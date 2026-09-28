#!/usr/bin/env python3
"""Find existing entries (app words + every batch file) whose English, Russian or Tajik headword matches.
Usage: find.py WORD [WORD...]   (case/stress-insensitive; exact headword match, or substring with a trailing *)
Use it before writing an entry to avoid giving two different English words the same Russian+Tajik pair."""
import glob, json, os, sys
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "source", "words.jsonl")
# Optional drafts not yet merged into words.jsonl (e.g. while several people or agents write entries).
B = os.environ.get("LP_BATCHES", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cache", "batches"))
def norm(s): return s.lower().replace("́", "").replace("ё", "е").strip()
entries = []
for f in [ROOT] + sorted(glob.glob(os.path.join(B, "*.jsonl"))):
    src = "app" if f == ROOT else os.path.basename(f)
    for l in open(f, encoding="utf-8"):
        try:
            d = json.loads(l); entries.append((src, d))
        except Exception:
            pass
for q in sys.argv[1:]:
    sub = q.endswith("*"); qq = norm(q.rstrip("*"))
    hits = [(s, d) for s, d in entries if any((qq in norm(d[k])) if sub else norm(d[k]) == qq for k in ("en", "ru", "tj"))]
    print(f"== {q}: {len(hits)}")
    for s, d in hits[:12]:
        print(f"    [{s}] {d['id']} ({d['level']}): {d['en']} | {d['ru']} | {d['tj']}")
