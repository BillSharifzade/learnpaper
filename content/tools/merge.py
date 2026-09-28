#!/usr/bin/env python3
"""Merge reviewed draft files into content/source/words.jsonl.

Usage: merge.py DRAFT.jsonl [DRAFT2.jsonl ...] [--dry-run]

Existing words keep their order; new words are appended grouped by level (A1…C2) in draft order. Legacy
tags are normalised (adjectives → qualities, verbs → actions). Refuses to merge when an id is duplicated
or when the same English word + part of speech would appear twice. Writes one JSON object per line with
the house key order and prints a per-level summary."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORDS = ROOT / "content/source/words.jsonl"
LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2"]
KEYS = ["id", "level", "pos", "tags", "image", "en", "ipa", "ru", "tj", "ex"]
TAG_MAP = {"adjectives": "qualities", "verbs": "actions"}


def normalise(d: dict) -> dict:
    tags = []
    for t in d.get("tags", []):
        t = TAG_MAP.get(t, t)
        if t not in tags:
            tags.append(t)
    d = {**d, "tags": tags[:2]}
    return {k: d[k] for k in KEYS}


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry = "--dry-run" in sys.argv
    existing = [json.loads(l) for l in WORDS.read_text(encoding="utf-8").splitlines() if l.strip()]
    new = []
    for f in args:
        new += [json.loads(l) for l in Path(f).read_text(encoding="utf-8").splitlines() if l.strip()]
    ids, pairs, problems = set(), set(), []
    for d in existing + new:
        if d["id"] in ids:
            problems.append(f"duplicate id {d['id']}")
        ids.add(d["id"])
        key = (d["en"].lower(), d["pos"])
        if key in pairs:
            problems.append(f"duplicate word {d['en']} ({d['pos']}) — id {d['id']}")
        pairs.add(key)
    if problems:
        print("\n".join(problems[:50]))
        print(f"{len(problems)} problem(s); nothing written")
        return 1
    new.sort(key=lambda d: LEVELS.index(d["level"]))  # stable: keeps draft order within a level
    merged = [normalise(d) for d in existing + new]
    counts = {lv: sum(1 for d in merged if d["level"] == lv) for lv in LEVELS}
    print(f"{len(existing)} existing + {len(new)} new = {len(merged)} words; by level {counts}")
    if not dry:
        WORDS.write_text("".join(json.dumps(d, ensure_ascii=False) + "\n" for d in merged), encoding="utf-8")
        print("written", WORDS)
    return 0


if __name__ == "__main__":
    sys.exit(main())
