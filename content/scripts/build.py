#!/usr/bin/env python3
"""Build the LearnPaper content pack.

Reads content/source/words.jsonl, validates it, generates Latin transliterations
for Russian and Tajik, fetches and rasterizes Fluent Emoji illustrations, and
writes content/build/words.json + content/build/images/*.png. By default the
result is also copied into the Android app's assets.

Usage:
  python3 content/scripts/build.py            # full build
  python3 content/scripts/build.py --check    # validate only, no network, no output
  python3 content/scripts/build.py --no-android
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import unicodedata
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "source" / "words.jsonl"
FOLDERS = ROOT / "source" / "fluent-emoji-folders.txt"
CACHE = ROOT / "cache"
BUILD = ROOT / "build"
ANDROID_ASSETS = ROOT.parent / "android" / "app" / "src" / "main" / "assets" / "content"
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


def build_image(word_id: str, spec: str, folders: set[str], check_only: bool) -> tuple[str | None, list[str]]:
    """Return (image file name or None, warnings)."""
    warnings: list[str] = []
    kind, _, name = spec.partition(":")
    out = BUILD / "images" / f"{word_id}.png"
    if kind == "fluent":
        if name not in folders:
            warnings.append(f"{word_id}: Fluent folder '{name}' not in folder list")
            return None, warnings
        if check_only:
            return out.name, warnings
        svg = fetch_fluent_svg(name)
        if svg is None:
            warnings.append(f"{word_id}: could not download Fluent SVG for '{name}'")
            return None, warnings
        if out.exists() and out.stat().st_mtime >= svg.stat().st_mtime:
            return out.name, warnings
        return (out.name if rasterize(svg, out) else None), warnings
    if kind == "noto":
        if check_only:
            return out.name, warnings
        code = name.lower().replace("u+", "").replace(" ", "_")
        if fetch(NOTO_PNG.format(code=code), out):
            return out.name, warnings
        warnings.append(f"{word_id}: could not download Noto PNG for '{name}'")
        return None, warnings
    if kind == "file":
        src = ROOT / "source" / "images" / name
        if not src.exists():
            warnings.append(f"{word_id}: image file '{name}' missing")
            return None, warnings
        if not check_only:
            out.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, out)
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
    args = ap.parse_args()

    folders = set(FOLDERS.read_text(encoding="utf-8").splitlines()) if FOLDERS.exists() else set()
    errors: list[str] = []
    warnings: list[str] = []
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
        for key in ("en", "ipa", "ru", "tj", "pos", "image"):
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
            warnings += missing + unmatched
            print(f"Tajik cross-check: {len(words) - len(missing) - len(unmatched)} matched, "
                  f"{len(unmatched)} gloss mismatch, {len(missing)} not in Wiktionary")

    for e in errors:
        print("ERROR", e)
    for w in warnings:
        print("WARN ", w)

    levels = {}
    for w in words:
        levels[w["level"]] = levels.get(w["level"], 0) + 1
    with_images = sum(1 for w in words if w["image"])
    print(f"{len(words)} words, levels {levels}, {with_images} with images, "
          f"{len(errors)} errors, {len(warnings)} warnings")

    if errors:
        return 1
    if args.check:
        return 0

    BUILD.mkdir(parents=True, exist_ok=True)
    pack = {"version": 1, "words": words}
    (BUILD / "words.json").write_text(json.dumps(pack, ensure_ascii=False, indent=1), encoding="utf-8")
    (BUILD / "report.txt").write_text("\n".join(errors + warnings) + "\n", encoding="utf-8")

    if not args.no_android:
        ANDROID_ASSETS.mkdir(parents=True, exist_ok=True)
        (ANDROID_ASSETS / "images").mkdir(exist_ok=True)
        shutil.copyfile(BUILD / "words.json", ANDROID_ASSETS / "words.json")
        wanted = {w["image"] for w in words if w["image"]}
        for old in (ANDROID_ASSETS / "images").glob("*.png"):
            if old.name not in wanted:
                old.unlink()
        for name in wanted:
            shutil.copyfile(BUILD / "images" / name, ANDROID_ASSETS / "images" / name)
        total = sum(f.stat().st_size for f in (ANDROID_ASSETS / "images").glob("*.png"))
        print(f"copied to {ANDROID_ASSETS} ({total // 1024} KB of images)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
