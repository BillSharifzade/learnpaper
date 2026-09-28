#!/usr/bin/env python3
"""Tajik corpus concordance (Leipzig news/web/Wikipedia, ~564k sentences).
Usage: conc.py 'phrase' ['another phrase' ...] [-n N]
Whole-word, case-insensitive phrase match; a trailing * makes the LAST word a prefix ('палто*', 'қадам зад*').
Prints the number of matching sentences and the N (default 6) shortest examples.
Note: web text sometimes uses Russian letters (х for ҳ, к for қ, у for ӯ, и for ӣ) or ў for ӯ, so proper forms can be
undercounted; long inflected forms are naturally rare — check the stem or construction instead."""
import sqlite3, sys, os
args = sys.argv[1:]
n = 6
if "-n" in args:
    i = args.index("-n"); n = int(args[i + 1]); del args[i:i + 2]
if not args:
    sys.exit(__doc__)
con = sqlite3.connect(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cache", "tools", "tj_corpus.db"))
for q in args:
    prefix = q.endswith("*")
    body = q.rstrip("*").strip().replace('"', "")
    match = f'"{body}"' + ("*" if prefix else "")
    try:
        cnt = con.execute("SELECT count(*) FROM s WHERE s MATCH ?", (match,)).fetchone()[0]
        rows = con.execute("SELECT t FROM s WHERE s MATCH ? ORDER BY length(t) LIMIT ?", (match, n)).fetchall()
    except sqlite3.OperationalError as e:
        print(f"== {q}: query error {e}"); continue
    print(f"== {q}: {cnt} sentences")
    for (t,) in rows:
        print("   ", t[:220])
