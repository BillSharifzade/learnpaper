#!/usr/bin/env python3
"""Search the Fluent Emoji folder names (the only valid values for image: "fluent:<name>").
Usage: emoji.py KEYWORD [KEYWORD...]   (case-insensitive substring match; prints exact folder names)"""
import sys
names = open(__import__("os").path.join(__import__("os").path.dirname(__import__("os").path.abspath(__file__)), "..", "source", "fluent-emoji-folders.txt"), encoding="utf-8").read().splitlines()
for k in sys.argv[1:]:
    hits = [n for n in names if k.lower() in n.lower()]
    print(f"== {k}: " + (" | ".join(hits[:40]) if hits else "(none)"))
