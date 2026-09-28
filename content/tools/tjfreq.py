#!/usr/bin/env python3
"""Tajik word-form frequencies in the corpus (~9.5M tokens). Usage: tjfreq.py WORD [WORD...]
A trailing * sums all forms starting with the prefix (e.g. 'палто*')."""
import sqlite3, sys, os
con = sqlite3.connect(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cache", "tools", "tj_corpus.db"))
for w in sys.argv[1:]:
    q = w.lower()
    if q.endswith("*"):
        n = con.execute("SELECT coalesce(sum(n),0), count(*) FROM freq WHERE w >= ? AND w < ?", (q[:-1], q[:-1] + "￿")).fetchone()
        print(f"{w}: {n[0]} tokens in {n[1]} forms")
    else:
        r = con.execute("SELECT n FROM freq WHERE w=?", (q,)).fetchone()
        print(f"{w}: {r[0] if r else 0}")
