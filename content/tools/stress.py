#!/usr/bin/env python3
"""Russian stress lookup (OpenRussian dataset). Usage: stress.py WORD [WORD...]
Prints the accented dictionary form(s) with a combining acute after the stressed vowel, the word class and an English
gloss. Stress marks already in the input are ignored. Words with ё need no mark (ё is always stressed)."""
import sqlite3, sys, os
con = sqlite3.connect(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cache", "tools", "ru_stress.db"))
for w in sys.argv[1:]:
    for part in w.replace("́", "").lower().split():
        rows = con.execute("SELECT accented, kind, en FROM a WHERE bare=?", (part,)).fetchall()
        if not rows:
            print(f"{part}: (not in dataset)")
        for acc, kind, en in rows:
            print(f"{part}: {acc}  [{kind}] {en}")
