#!/usr/bin/env python3
"""Tajik UI glossary from GNOME's professional Tajik translations (gnome-control-center, gnome-shell, nautilus,
gtk, clocks, calendar, software, settings-daemon). Usage: l10n.py 'english term' ['term2' ...] [-n N]
Case-insensitive substring search in the English source strings; prints matching English -> Tajik pairs,
shortest first. Use it to see how established Tajik software words UI concepts (settings, notifications, lock
screen, wallpaper, download, reset, language...)."""
import glob, os, re, sys
args = sys.argv[1:]
n = 12
if "-n" in args:
    i = args.index("-n"); n = int(args[i + 1]); del args[i:i + 2]
D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cache", "tools", "l10n")
pairs = set()
for f in glob.glob(os.path.join(D, "*_tg.po")):
    s = open(f, encoding="utf-8", errors="replace").read()
    for a, b in re.findall(r'msgid "((?:[^"\\]|\\.)*)"\s*\nmsgstr "((?:[^"\\]|\\.)*)"', s):
        if a and b:
            pairs.add((a.replace("_", ""), b.replace("_", "")))
for q in args:
    hits = sorted((p for p in pairs if q.lower() in p[0].lower()), key=lambda p: len(p[0]))[:n]
    print(f"== {q}: {len(hits)} shown")
    for a, b in hits:
        print(f"    {a}  ->  {b}")
