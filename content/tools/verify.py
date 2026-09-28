#!/usr/bin/env python3
"""Automated verification of batch entries against dictionaries, Wiktionary and the corpus.

Usage: verify.py BATCH.jsonl [BATCH2.jsonl ...]
Writes verify/<batch>.json with, per entry: Tajik confirmation sources, dictionary snippets, IPA check,
Russian stress check and rare Tajik tokens in the example. Everything is cached (web_cache.db), so reruns
are cheap. Prints a summary per batch."""
import json, os, re, sqlite3, sys, threading, unicodedata
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import Sahifa, wiki_raw  # noqa: E402

TOOLS = Path(__file__).resolve().parent
DATA = TOOLS.parent / "cache" / "tools"
OUT = DATA / "verify"
OUT.mkdir(exist_ok=True)
ACUTE = "́"
LOOSE = str.maketrans("ӣӯҳқҷғйў", "иухкчгиу")
L = "а-яёғӣқӯҳҷйў"
corpus = sqlite3.connect(DATA / "tj_corpus.db", check_same_thread=False)
_db_lock = threading.Lock()  # one connection per database, shared by the worker threads
stress = sqlite3.connect(DATA / "ru_stress.db", check_same_thread=False)


def norm(s, loose=False):
    s = s.lower().replace(ACUTE, "").replace("ё", "е")
    return s.translate(LOOSE) if loose else s.replace("й", "ӣ")


def has(text, term, loose=False):
    if not text or not term:
        return False
    t, q = norm(text, loose), norm(term, loose)
    return re.search(rf"(?<![{L}]){re.escape(q)}(?![{L}])", t) is not None


def en_wikt(en):
    raw = wiki_raw("en.wiktionary.org", en) or ""
    m = re.search(r"==English==(.*?)(\n==[^=]|\Z)", raw, re.S)
    body = m.group(1) if m else ""
    ipa = re.findall(r"\{\{IPA\|en\|([^}]*)\}\}", body)
    tj = []
    for line in raw.splitlines():
        if line.lstrip("*: ").startswith("Tajik:"):
            tj += re.findall(r"\{\{t\+?\|tg\|([^|}]+)", line)
    return ipa, tj


def ru_wikt(ru):
    raw = wiki_raw("ru.wiktionary.org", ru) or ""
    out = []
    for m in re.finditer(r"^\|tg=(.*)$", raw, re.M):
        out += [w.strip() for w in re.findall(r"\[\[([^\]|#]+)", m.group(1))]
        out += re.findall(r"\{\{t\+?\|tg\|([^|}]+)", m.group(1))
    return [w for w in out if w and "<!--" not in w]


CONSONANTS = set("pbtdkgfvθðszʃʒhmnŋlrjwx")


def ipa_skeleton(s):
    """Consonants only: robust to notation differences (ɪ/ə vowels, ̯ ◌͡◌ ̩, ɹ/r, stress and length marks)."""
    s = re.sub(r"\([^)]*\)", "", s)
    s = s.replace("ɡ", "g").replace("ɹ", "r").replace("ʍ", "w").replace("ɫ", "l").replace("ʧ", "tʃ").replace("ʤ", "dʒ")
    return "".join(c for c in s if c in CONSONANTS)


def ipa_check(ours, lines):
    """True if our IPA has the same consonants as one of Wiktionary's RP/UK transcriptions (None: no data)."""
    cands = []
    for l in lines:
        parts = l.split("|")
        accent = " ".join(p for p in parts if p.startswith("a=")).lower()
        if accent and not any(k in accent for k in ("rp", "uk", "gb", "received", "british")):
            continue
        cands += [p for p in parts if p.startswith("/") and p.endswith("/")]
    if not cands:
        return None, []
    o = ipa_skeleton(ours)
    return any(ipa_skeleton(c) == o for c in cands), cands


def rare_tokens(sentence):
    out = []
    for t in re.findall(rf"[{L}]+", sentence.lower()):
        with _db_lock:
            r = corpus.execute("SELECT n FROM freq WHERE w=?", (t,)).fetchone()
        if not r or r[0] < 2:
            out.append(t)
    return out


def check(entry, tj_ru, ru_tj):
    tj = entry["tj"].strip()
    ru = entry["ru"].replace(ACUTE, "").strip()
    en = entry["en"].strip()
    parts = [p for p in re.split(r"[\s-]+", tj) if p]
    d_tjru = tj_ru.lookup(tj) or ""
    parts_tjru = {p: (tj_ru.lookup(p) or "") for p in parts} if len(parts) > 1 else {}
    d_rutj = ru_tj.lookup(ru) or ""
    ipa_lines, en_tj = en_wikt(en)
    rw = ru_wikt(ru)
    src = set()
    if has(d_rutj, tj) or (len(parts) > 1 and all(has(d_rutj, p) for p in parts)):
        src.add("ru→tj")
    elif has(d_rutj, tj, True):
        src.add("ru→tj~")
    ru_alts = [x.strip() for x in re.split(r"[,;/]", ru) if x.strip()]
    if any(has(d_tjru, x) for x in ru_alts) or any(has(v, x) for v in parts_tjru.values() for x in ru_alts):
        src.add("tj→ru")
    if any(norm(tj) == norm(x) for x in en_tj):
        src.add("enwikt")
    if any(norm(tj) == norm(x) for x in rw):
        src.add("ruwikt")
    with _db_lock:
        freq = [corpus.execute("SELECT n FROM freq WHERE w=?", (p.lower(),)).fetchone() for p in parts]
    ipa_ok, ipa_c = ipa_check(entry["ipa"], ipa_lines)
    st = []
    for tok in entry["ru"].split():
        bare = tok.replace(ACUTE, "").lower()
        with _db_lock:
            rows = [r[0] for r in stress.execute("SELECT accented FROM a WHERE bare=?", (bare,)).fetchall()]
        if rows and "ё" not in bare and tok.lower() not in {r.lower() for r in rows}:
            st.append(f"{tok} vs {sorted(set(rows))}")
    return {
        "id": entry["id"], "level": entry["level"], "pos": entry["pos"], "en": en, "ipa": entry["ipa"], "ru": entry["ru"], "tj": tj,
        "ex": entry["ex"], "image": entry["image"],
        "sources": sorted(src),
        "tj_freq": [f[0] if f else 0 for f in freq],
        "dict_tj_ru": d_tjru[:300], "dict_ru_tj": d_rutj[:300],
        "dict_parts": {k: v[:160] for k, v in parts_tjru.items()},
        "enwikt_tj": en_tj[:8], "ruwikt_tj": rw[:8],
        "ipa_ok": ipa_ok, "ipa_wikt": ipa_c[:3],
        "ru_stress": st,
        "rare_ex_tokens": rare_tokens(entry["ex"]["tj"]),
    }


def main():
    for path in sys.argv[1:]:
        entries = [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
        name = Path(path).stem
        out = OUT / f"{name}.json"
        done = {}
        if out.exists():
            done = {r["id"]: r for r in json.load(open(out, encoding="utf-8"))}
        todo = [e for e in entries if e["id"] not in done or done[e["id"]]["tj"] != e["tj"].strip()]
        results = dict(done)

        def work(chunk):
            tj_ru, ru_tj = Sahifa("tj"), Sahifa("ru")
            return [check(e, tj_ru, ru_tj) for e in chunk]

        chunks = [todo[i::3] for i in range(3)]
        with ThreadPoolExecutor(3) as pool:
            for rs in pool.map(work, chunks):
                for r in rs:
                    results[r["id"]] = r
        ordered = [results[e["id"]] for e in entries if e["id"] in results]
        json.dump(ordered, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        weak = sum(1 for r in ordered if len(r["sources"]) <= 1)
        ipa_bad = sum(1 for r in ordered if r["ipa_ok"] is False)
        print(f"{name}: {len(ordered)} entries, {weak} with ≤1 Tajik source, {ipa_bad} IPA mismatches, "
              f"{sum(1 for r in ordered if r['ru_stress'])} stress diffs, {sum(1 for r in ordered if r['rare_ex_tokens'])} with rare example tokens", flush=True)


if __name__ == "__main__":
    main()
