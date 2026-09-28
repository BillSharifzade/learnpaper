#!/usr/bin/env python3
"""British (RP/UK) IPA from English Wiktionary. Usage: ipa.py WORD [WORD...]
Prints the {{IPA|en|...}} lines with their accent labels. Use the RP/UK one, without slashes, in the house style:
'həˈləʊ', 'ˈtaɪɡə(r)', 'ʌmˈbrelə' (Oxford-style symbols: e not ɛ, ə, ɜː, ɔː, əʊ, eə, ʊə; ˈ and ˌ before the syllable)."""
import os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import wiki_raw
for w in sys.argv[1:]:
    raw = wiki_raw("en.wiktionary.org", w) or ""
    m = re.search(r"==English==(.*?)(\n==[^=]|\Z)", raw, re.S)
    lines = re.findall(r"\{\{IPA\|en\|[^\n]*", m.group(1) if m else "")
    print(f"== {w}:")
    for l in lines[:6]:
        print("   ", l)
    if not lines:
        print("    (no IPA found)")
