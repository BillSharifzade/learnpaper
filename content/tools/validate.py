#!/usr/bin/env python3
"""Validate a LearnPaper batch file (JSONL). Usage: validate.py FILE [--quiet]
Checks schema, allowed values, Russian stress marks, Tajik orthography, example lengths, headword presence in
examples, Russian stress against the OpenRussian dataset, Tajik corpus attestation, and id clashes with the
app's words and with the other batch files."""
import glob, json, os, re, sqlite3, sys, unicodedata
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parents[1]
DATA = TOOLS.parent / "cache" / "tools"
BATCHES = Path(os.environ.get("LP_BATCHES", TOOLS.parent / "cache" / "batches"))
LEVELS = {"A1", "A2", "B1", "B2", "C1", "C2"}
POS = set("noun verb adjective adverb numeral phrase pronoun preposition conjunction interjection".split())
TAGS = set("""people family body health food home clothes objects animals nature weather places transport travel time
numbers work money shopping school science technology communication society arts music sport feelings character mind
actions qualities colors basics""".split())
KEYS = ["id", "level", "pos", "tags", "image", "en", "ipa", "ru", "tj", "ex"]
LIMIT = {"A1": 50, "A2": 50, "B1": 70, "B2": 70, "C1": 85, "C2": 85}
ACUTE = "́"
RU_VOWELS = "аеёиоуыэюя"
IPA_OK = set("abdefghijklmnoprstuvwxzæðŋɑɒɔəɜɡɪʃʊʌʒθːˈˌ()ʔ .-'ʤʧ")

folders = set((ROOT / "content/source/fluent-emoji-folders.txt").read_text(encoding="utf-8").splitlines())
existing = [json.loads(l) for l in (ROOT / "content/source/words.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
existing_ids = {w["id"] for w in existing}
existing_en = {(w["en"].lower(), w["pos"]) for w in existing}
stress_db = sqlite3.connect(DATA / "ru_stress.db")
corpus_db = sqlite3.connect(DATA / "tj_corpus.db")


def other_batch_ids(me):
    ids = {}
    for f in glob.glob(str(BATCHES / "*.jsonl")):
        if os.path.abspath(f) == os.path.abspath(me):
            continue
        for l in open(f, encoding="utf-8"):
            try:
                d = json.loads(l)
                ids[d["id"]] = os.path.basename(f)
            except Exception:
                pass
    return ids


def stem_in(head, sentence, lang):
    s = sentence.lower().replace(ACUTE, "").replace("ё", "е")
    for tok in re.findall(r"[\w'-]+", head.lower().replace(ACUTE, "").replace("ё", "е")):
        if len(tok) <= 3:
            continue
        k = max(3, len(tok) - (3 if lang != "en" else 2))
        if tok[:k] in s:
            return True
    toks = [t for t in re.findall(r"[\w'-]+", head.lower()) if len(t) > 3]
    return not toks  # short words: can't judge


def ru_word_errors(word):
    errs = []
    for tok in re.split(r"[\s-]+", word):
        if not tok:
            continue
        bare = tok.replace(ACUTE, "")
        vowels = sum(1 for c in bare.lower() if c in RU_VOWELS)
        marks = tok.count(ACUTE)
        if marks and tok.index(ACUTE) > 0 and tok[tok.index(ACUTE) - 1].lower() not in RU_VOWELS:
            errs.append(f"stress mark after a consonant in '{tok}'")
        if vowels >= 2 and "ё" not in bare.lower() and marks != 1:
            errs.append(f"'{tok}' needs exactly one stress mark (has {marks})")
        if vowels <= 1 and marks:
            errs.append(f"one-syllable '{tok}' should have no stress mark")
    return errs


def ru_dataset_warn(word):
    out = []
    for tok in word.split():
        bare = tok.replace(ACUTE, "").lower()
        rows = [r[0] for r in stress_db.execute("SELECT accented FROM a WHERE bare=?", (bare,)).fetchall()]
        if rows and "ё" not in bare and tok.lower() not in {r.lower() for r in rows}:
            out.append(f"stress '{tok}' differs from dataset {sorted(set(rows))}")
    return out


def main():
    global existing_ids, existing_en
    path = sys.argv[1]
    quiet = "--quiet" in sys.argv
    if Path(path).resolve() == (ROOT / "content/source/words.jsonl").resolve():
        existing_ids, existing_en = set(), set()   # validating the app's own list: only in-file duplicates count
    others = other_batch_ids(path)
    errors, warns, seen, n = [], [], set(), 0
    for i, line in enumerate(open(path, encoding="utf-8"), 1):
        if not line.strip():
            continue
        n += 1
        try:
            d = json.loads(line)
        except json.JSONDecodeError as e:
            errors.append(f"line {i}: invalid JSON ({e})"); continue
        wid = d.get("id", f"line{i}")
        E = lambda m: errors.append(f"{wid}: {m}")
        W = lambda m: warns.append(f"{wid}: {m}")
        if list(d.keys()) != KEYS:
            E(f"keys must be exactly {KEYS} in that order (got {list(d.keys())})")
        if not re.fullmatch(r"[a-z0-9-]+", str(wid)):
            E("bad id")
        if wid in seen:
            E("duplicate id in this file")
        seen.add(wid)
        if wid in existing_ids:
            E("id already exists in the app (choose another id or skip the word)")
        if wid in others:
            E(f"id also used in {others[wid]}")
        if (str(d.get("en", "")).lower(), d.get("pos")) in existing_en:
            W("this English word with this part of speech is already in the app — skip unless a different sense")
        if d.get("level") not in LEVELS: E(f"bad level {d.get('level')}")
        if d.get("pos") not in POS: E(f"bad pos {d.get('pos')}")
        tags = d.get("tags")
        if not isinstance(tags, list) or not 1 <= len(tags) <= 2 or any(t not in TAGS for t in tags):
            E(f"tags must be 1-2 of the allowed list (got {tags})")
        img = d.get("image", "")
        if img != "none" and not (img.startswith("fluent:") and img[7:] in folders):
            E(f"image must be 'none' or fluent:<exact folder name> (got '{img}')")
        for k in ("en", "ipa", "ru", "tj"):
            if not str(d.get(k, "")).strip():
                E(f"missing {k}")
        ipa = str(d.get("ipa", ""))
        bad = {c for c in ipa if c not in IPA_OK}
        if "/" in ipa: E("ipa without slashes")
        if bad: W(f"unusual IPA symbols {''.join(sorted(bad))} (house style: e not ɛ, ɡ, ː)")
        if len(re.findall(r"[aeiouæɑɒɔəɜɪʊʌ]+", ipa)) >= 2 and not re.search("[ˈˌ]", ipa):
            E("ipa needs a stress mark ˈ")
        ru, tj = str(d.get("ru", "")), str(d.get("tj", ""))
        if re.search(r"[A-Za-z]", ru + tj): E("Latin letters in ru/tj")
        if re.search(r"[ғӣқӯҳҷ]", ru.lower()): E("Tajik letters in the Russian word")
        for m in ru_word_errors(ru): E(f"ru {m}")
        for m in ru_dataset_warn(ru): W(f"ru {m}")
        if re.search(r"[ьцщы]", tj.lower()): E("Tajik word with ь/ц/щ/ы (use reformed spelling: палто, мотосикл)")
        for tok in re.findall(r"[а-яёғӣқӯҳҷ]+", tj.lower()):
            r = corpus_db.execute("SELECT n FROM freq WHERE w=?", (tok,)).fetchone()
            if not r:
                W(f"tj '{tok}' has 0 corpus hits — make sure it is real, standard Tajik (dict.py / conc.py)")
        ex = d.get("ex") or {}
        if not isinstance(ex, dict) or list(ex.keys()) != ["en", "ru", "tj"]:
            E("ex must be {en, ru, tj} in that order"); continue
        lim = LIMIT.get(d.get("level"), 90)
        for lang in ("en", "ru", "tj"):
            s = str(ex.get(lang, ""))
            if not s.strip(): E(f"missing example {lang}"); continue
            if len(s) > 90: E(f"example {lang} too long ({len(s)} > 90)")
            elif len(s) > lim + 10: W(f"example {lang} is long for {d.get('level')} ({len(s)})")
            head = {"en": d.get("en", ""), "ru": ru, "tj": tj}[lang]
            if not stem_in(head, s, lang): W(f"example {lang} may not contain the headword '{head.replace(ACUTE, '')}'")
        if ACUTE in ex.get("ru", ""): E("no stress marks in the Russian example")
        if re.search(r"[A-Za-z]", ex.get("ru", "") + ex.get("tj", "")): E("Latin letters in ru/tj example")
        if re.search(r"[ьцщы]", ex.get("tj", "").lower()): E("Tajik example with ь/ц/щ/ы")
    print(f"{path}: {n} entries, {len(errors)} errors, {len(warns)} warnings")
    for e in errors: print("ERROR", e)
    if not quiet:
        for w in warns: print("WARN ", w)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
