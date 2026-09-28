#!/usr/bin/env python3
"""Build the LearnPaper content pack.

Reads content/source/words.jsonl, validates it, generates Latin transliterations
for Russian and Tajik, fetches and rasterizes Fluent Emoji illustrations, and
writes content/build/words.json + content/build/images/*.webp. By default the
result is also copied into the Android app's assets.

Images are lossless WebP (Pillow), one file per illustration shared by every word
that uses it; a word with "image": "none" has no illustration (the card shows a
drop-cap tile instead). Pillow is taken from content/.venv when the system Python
lacks it:  python3 -m venv content/.venv && content/.venv/bin/pip install pillow

Usage:
  python3 content/scripts/build.py            # full build
  python3 content/scripts/build.py --check    # validate only, no network, no output
  python3 content/scripts/build.py --no-android
  python3 content/scripts/build.py --pack b1=B1 --pack-version 2   # also publish a downloadable pack

A pack is a zip (words.json + images/) written to content/packs/ and listed in
content/packs/manifest.json, which the app fetches from this repository on GitHub.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import unicodedata
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VENV_PY = ROOT / ".venv" / "bin" / "python"

try:
    from PIL import Image  # noqa: F401
    HAVE_PIL = True
except ImportError:
    HAVE_PIL = False
    if VENV_PY.exists() and Path(sys.prefix).resolve() != (ROOT / ".venv").resolve() and not os.environ.get("LP_NO_VENV"):
        os.environ["LP_NO_VENV"] = "1"
        os.execv(str(VENV_PY), [str(VENV_PY), *sys.argv])
SOURCE = ROOT / "source" / "words.jsonl"
FOLDERS = ROOT / "source" / "fluent-emoji-folders.txt"
CACHE = ROOT / "cache"
BUILD = ROOT / "build"
ANDROID_ASSETS = ROOT.parent / "android" / "app" / "src" / "main" / "assets" / "content"
PACKS = ROOT / "packs"
PACK_NAMES = {"en": "{levels} words", "ru": "Слова {levels}", "tj": "Калимаҳои {levels}"}
KAIKKI_TG_URL = "https://kaikki.org/dictionary/Tajik/kaikki.org-dictionary-Tajik.jsonl"
FLUENT_RAW = "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets"
NOTO_PNG = "https://raw.githubusercontent.com/googlefonts/noto-emoji/main/png/512/emoji_u{code}.png"

LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2"]
LANGS = ("en", "ru", "tj")
COMBINING_ACUTE = "́"
IMAGE_SIZE = 512

# --- transliteration -----------------------------------------------------------

RU = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "yo", "ж": "zh",
    "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m", "н": "n", "о": "o",
    "п": "p", "р": "r", "с": "s", "т": "t", "у": "u", "ф": "f", "х": "kh", "ц": "ts",
    "ч": "ch", "ш": "sh", "щ": "shch", "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu",
    "я": "ya",
}
TJ = {
    "а": "a", "б": "b", "в": "v", "г": "g", "ғ": "gh", "д": "d", "е": "e", "ё": "yo",
    "ж": "zh", "з": "z", "и": "i", "ӣ": "ī", "й": "y", "к": "k", "қ": "q", "л": "l",
    "м": "m", "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u",
    "ӯ": "ū", "ф": "f", "х": "kh", "ҳ": "h", "ч": "ch", "ҷ": "j", "ш": "sh", "ъ": "ʼ",
    "э": "e", "ю": "yu", "я": "ya",
}
VOWELS_CYR = set("аеёиоуыэюяӣӯ")
ACUTE = {"a": "á", "e": "é", "i": "í", "o": "ó", "u": "ú", "y": "ý", "ī": "ī", "ū": "ū"}


def _translit(word: str, table: dict[str, str], ye_rule: bool) -> str:
    out: list[str] = []
    chars = list(word)
    i = 0
    prev = ""
    while i < len(chars):
        ch = chars[i]
        stressed = i + 1 < len(chars) and chars[i + 1] == COMBINING_ACUTE
        lower = ch.lower()
        if lower in table:
            lat = table[lower]
            if ye_rule and lower == "е" and (prev == "" or prev in VOWELS_CYR or prev in "ьъ"):
                lat = "ye"
            if lower == "ё":
                stressed = True
            if stressed and lat:
                # put the acute on the last vowel of the latin chunk
                for k in range(len(lat) - 1, -1, -1):
                    if lat[k] in ACUTE:
                        lat = lat[:k] + ACUTE[lat[k]] + lat[k + 1:]
                        break
            if ch.isupper() and lat:
                lat = lat[0].upper() + lat[1:]
            out.append(lat)
            prev = lower
        elif ch == COMBINING_ACUTE:
            pass
        else:
            out.append(ch)
            prev = ch if ch.isalpha() else ""
        i += 1
    return "".join(out)


def translit_ru(word: str) -> str:
    return _translit(word, RU, ye_rule=True)


def translit_tj(word: str) -> str:
    return _translit(word, TJ, ye_rule=True)


def strip_stress(text: str) -> str:
    return text.replace(COMBINING_ACUTE, "")


# --- images --------------------------------------------------------------------

def fluent_slugs(folder: str) -> list[str]:
    base = re.sub(r"[^a-z0-9 -]", "", folder.lower()).strip()
    with_hyphen = re.sub(r"\s+", "_", base)
    no_hyphen = re.sub(r"\s+", "_", base.replace("-", " "))
    return list(dict.fromkeys([with_hyphen, no_hyphen]))


def fetch(url: str, dest: Path) -> bool:
    if dest.exists() and dest.stat().st_size > 0:
        return True
    dest.parent.mkdir(parents=True, exist_ok=True)
    try:
        with urllib.request.urlopen(url, timeout=60) as r, open(dest, "wb") as f:
            shutil.copyfileobj(r, f)
        return True
    except Exception:
        if dest.exists():
            dest.unlink()
        return False


def fetch_fluent_svg(folder: str) -> Path | None:
    enc = urllib.request.quote(folder)
    for slug in fluent_slugs(folder):
        candidates = [
            (f"{FLUENT_RAW}/{enc}/Flat/{slug}_flat.svg", CACHE / "fluent" / f"{slug}_flat.svg"),
            (f"{FLUENT_RAW}/{enc}/Default/Flat/{slug}_flat_default.svg", CACHE / "fluent" / f"{slug}_flat_default.svg"),
        ]
        for url, dest in candidates:
            if fetch(url, dest):
                return dest
    return None


def rasterize(svg: Path, png: Path) -> bool:
    png.parent.mkdir(parents=True, exist_ok=True)
    try:
        subprocess.run(
            ["rsvg-convert", "-w", str(IMAGE_SIZE), "-h", str(IMAGE_SIZE), str(svg), "-o", str(png)],
            check=True, capture_output=True,
        )
        return True
    except (OSError, subprocess.CalledProcessError) as e:
        print(f"  rasterize failed for {svg.name}: {e}", file=sys.stderr)
        return False


def image_name(spec: str) -> str:
    """Shared file name for an image spec, so words that use the same picture share one file."""
    kind, _, name = spec.partition(":")
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-") or hashlib.sha1(name.encode()).hexdigest()[:10]
    ext = "webp" if HAVE_PIL else "png"
    return f"{kind[:1]}-{slug}.{ext}"


def to_webp(png: Path, out: Path) -> bool:
    try:
        from PIL import Image
        with Image.open(png) as im:
            im.convert("RGBA").save(out, "WEBP", lossless=True, method=4, quality=80)
        return True
    except Exception as e:  # noqa: BLE001
        print(f"  webp failed for {png.name}: {e}", file=sys.stderr)
        return False


def finish(raster: Path, out: Path) -> bool:
    """Write the final image from a PNG raster: WebP when Pillow is available, else the PNG itself."""
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.suffix == ".webp":
        return to_webp(raster, out)
    shutil.copyfile(raster, out)
    return True


def build_image(word_id: str, spec: str, folders: set[str], check_only: bool) -> tuple[str | None, list[str]]:
    """Return (image file name or None, warnings)."""
    warnings: list[str] = []
    if spec == "none":
        return None, warnings
    kind, _, name = spec.partition(":")
    out = BUILD / "images" / image_name(spec)
    raster = CACHE / "raster" / (out.stem + ".png")
    if kind == "fluent":
        if name not in folders:
            warnings.append(f"{word_id}: Fluent folder '{name}' not in folder list")
            return None, warnings
        if check_only:
            return out.name, warnings
        if out.exists():
            return out.name, warnings
        svg = fetch_fluent_svg(name)
        if svg is None:
            warnings.append(f"{word_id}: could not download Fluent SVG for '{name}'")
            return None, warnings
        if not (raster.exists() and raster.stat().st_mtime >= svg.stat().st_mtime) and not rasterize(svg, raster):
            return None, warnings
        return (out.name if finish(raster, out) else None), warnings
    if kind == "noto":
        if check_only:
            return out.name, warnings
        if out.exists():
            return out.name, warnings
        code = name.lower().replace("u+", "").replace(" ", "_")
        if fetch(NOTO_PNG.format(code=code), raster):
            return (out.name if finish(raster, out) else None), warnings
        warnings.append(f"{word_id}: could not download Noto PNG for '{name}'")
        return None, warnings
    if kind == "file":
        src = ROOT / "source" / "images" / name
        if not src.exists():
            warnings.append(f"{word_id}: image file '{name}' missing")
            return None, warnings
        if not check_only and not out.exists():
            finish(src, out)
        return out.name, warnings
    warnings.append(f"{word_id}: unknown image spec '{spec}'")
    return None, warnings


# --- Tajik cross-check against Wiktionary (kaikki.org) -------------------------

def load_kaikki_tajik(allow_download: bool) -> dict[str, set[str]] | None:
    path = CACHE / "kaikki-tajik.jsonl"
    if not path.exists():
        if not allow_download or not fetch(KAIKKI_TG_URL, path):
            return None
    glosses: dict[str, set[str]] = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            try:
                d = json.loads(line)
            except json.JSONDecodeError:
                continue
            w = d.get("word")
            if not w:
                continue
            bag = glosses.setdefault(w.lower(), set())
            for s in d.get("senses", []):
                for g in s.get("glosses", []) or []:
                    bag.add(g.lower())
    return glosses


# --- main ------------------------------------------------------------------------

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="validate only; no network, no output files")
    ap.add_argument("--no-android", action="store_true", help="do not copy the pack into the Android assets")
    ap.add_argument("--no-kaikki", action="store_true", help="skip the Wiktionary cross-check")
    ap.add_argument("--pack", action="append", default=[], metavar="ID=LEVELS",
                    help="write content/packs/<ID>-v<N>.zip with the words of LEVELS (comma-separated) and update the manifest")
    ap.add_argument("--pack-version", type=int, default=1, help="version number for packs written with --pack")
    args = ap.parse_args()

    folders = set(FOLDERS.read_text(encoding="utf-8").splitlines()) if FOLDERS.exists() else set()
    errors: list[str] = []
    warnings: list[str] = []
    notes: list[str] = []
    words: list[dict] = []
    seen: set[str] = set()

    for n, line in enumerate(SOURCE.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            d = json.loads(line)
        except json.JSONDecodeError as e:
            errors.append(f"line {n}: invalid JSON ({e})")
            continue
        wid = d.get("id", "")
        if not re.fullmatch(r"[a-z0-9-]+", wid):
            errors.append(f"line {n}: bad id '{wid}'")
        if wid in seen:
            errors.append(f"line {n}: duplicate id '{wid}'")
        seen.add(wid)
        if d.get("level") not in LEVELS:
            errors.append(f"{wid}: bad level '{d.get('level')}'")
        for key in ("en", "ipa", "ru", "tj", "pos", "image"):  # image may be "none"
            if not str(d.get(key, "")).strip():
                errors.append(f"{wid}: missing '{key}'")
        ex = d.get("ex") or {}
        for lang in LANGS:
            if not str(ex.get(lang, "")).strip():
                errors.append(f"{wid}: missing example '{lang}'")
            elif len(ex[lang]) > 90:
                warnings.append(f"{wid}: example '{lang}' is long ({len(ex[lang])} chars)")
        if re.search(r"[A-Za-z]", d.get("ru", "")) or re.search(r"[A-Za-z]", d.get("tj", "")):
            errors.append(f"{wid}: Latin letters inside ru/tj text")
        if not any(ch in d.get("ipa", "") for ch in "ˈˌ") and len(d.get("ipa", "")) > 8:
            warnings.append(f"{wid}: IPA has no stress mark")

        image, w = build_image(wid, d["image"], folders, args.check)
        warnings += w

        words.append({
            "id": wid,
            "level": d["level"],
            "pos": d.get("pos", ""),
            "tags": d.get("tags", []),
            "en": {"text": d["en"], "tr": d["ipa"]},
            "ru": {"text": strip_stress(d["ru"]), "tr": translit_ru(d["ru"])},
            "tj": {"text": d["tj"], "tr": translit_tj(d["tj"])},
            "example": {lang: ex.get(lang, "") for lang in LANGS},
            "image": image,
        })

    if not args.no_kaikki:
        glosses = load_kaikki_tajik(allow_download=not args.check)
        if glosses is None:
            warnings.append("kaikki Tajik dump not available; skipped cross-check")
        else:
            missing, unmatched = [], []
            for w in words:
                tj = w["tj"]["text"].lower()
                en = w["en"]["text"].lower()
                bag = glosses.get(tj)
                if bag is None:
                    missing.append(f"{w['id']}: '{tj}' not in Wiktionary")
                elif not any(en in g for g in bag):
                    unmatched.append(f"{w['id']}: '{tj}' glosses do not mention '{en}' ({'; '.join(sorted(bag))[:80]})")
            # Wiktionary covers only a few thousand Tajik words, so these are notes for the report, not warnings.
            notes = missing + unmatched
            print(f"Tajik cross-check: {len(words) - len(missing) - len(unmatched)} matched, "
                  f"{len(unmatched)} gloss mismatch, {len(missing)} not in Wiktionary (details in build/report.txt)")

    for e in errors:
        print("ERROR", e)
    for w in warnings:
        print("WARN ", w)

    levels = {}
    for w in words:
        levels[w["level"]] = levels.get(w["level"], 0) + 1
    levels = {lv: levels[lv] for lv in LEVELS if lv in levels}
    with_images = sum(1 for w in words if w["image"])
    print(f"{len(words)} words, levels {levels}, {with_images} with images, "
          f"{len(errors)} errors, {len(warnings)} warnings")

    if errors:
        return 1
    if args.check:
        return 0

    BUILD.mkdir(parents=True, exist_ok=True)
    wanted_build = {w["image"] for w in words if w["image"]}
    for old in (BUILD / "images").glob("*"):
        if old.name not in wanted_build:
            old.unlink()
    pack = {"version": 1, "words": words}
    (BUILD / "words.json").write_text(json.dumps(pack, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    (BUILD / "report.txt").write_text("\n".join(errors + warnings + notes) + "\n", encoding="utf-8")

    if not args.no_android:
        ANDROID_ASSETS.mkdir(parents=True, exist_ok=True)
        (ANDROID_ASSETS / "images").mkdir(exist_ok=True)
        shutil.copyfile(BUILD / "words.json", ANDROID_ASSETS / "words.json")
        wanted = {w["image"] for w in words if w["image"]}
        for old in (ANDROID_ASSETS / "images").iterdir():
            if old.name not in wanted:
                old.unlink()
        for name in wanted:
            dst = ANDROID_ASSETS / "images" / name
            if not dst.exists() or dst.stat().st_size != (BUILD / "images" / name).stat().st_size:
                shutil.copyfile(BUILD / "images" / name, dst)
        total = sum(f.stat().st_size for f in (ANDROID_ASSETS / "images").iterdir())
        print(f"copied to {ANDROID_ASSETS} ({len(wanted)} images, {total // 1024} KB)")

    for spec in args.pack:
        write_pack(spec, args.pack_version, words)
    return 0


def write_pack(spec: str, version: int, words: list[dict]) -> None:
    """Zip the words of the given levels into content/packs/ and list the pack in manifest.json."""
    pack_id, _, level_spec = spec.partition("=")
    levels = [lv for lv in level_spec.split(",") if lv]
    if not re.fullmatch(r"[a-z0-9-]+", pack_id) or not levels:
        sys.exit(f"bad --pack spec '{spec}', expected ID=LEVEL[,LEVEL]")
    chosen = [w for w in words if w["level"] in levels]
    PACKS.mkdir(parents=True, exist_ok=True)
    for old in PACKS.glob(f"{pack_id}-v*.zip"):
        old.unlink()
    name = f"{pack_id}-v{version}.zip"
    with zipfile.ZipFile(PACKS / name, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("words.json", json.dumps({"version": 1, "words": chosen}, ensure_ascii=False))
        for w in chosen:
            if w["image"]:
                z.write(BUILD / "images" / w["image"], f"images/{w['image']}")
    manifest_path = PACKS / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {"packs": []}
    levels_text = ", ".join(levels)
    entry = {
        "id": pack_id,
        "name": {lang: tpl.format(levels=levels_text) for lang, tpl in PACK_NAMES.items()},
        "levels": levels,
        "version": version,
        "words": len(chosen),
        "url": name,
        "bytes": (PACKS / name).stat().st_size,
    }
    manifest["packs"] = [p for p in manifest["packs"] if p["id"] != pack_id] + [entry]
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"pack {name}: {len(chosen)} words, {entry['bytes'] // 1024} KB; manifest lists {len(manifest['packs'])} pack(s)")


if __name__ == "__main__":
    sys.exit(main())
