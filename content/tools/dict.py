#!/usr/bin/env python3
"""Tajik dictionary lookup on sahifa.tj (Rahimi–Uspenskaya Tajik–Russian, Russian–Tajik, and small EN/TJ ones).
Usage: dict.py tj|ru|en|tjen WORD [WORD...]
  tj = Tajik->Russian, ru = Russian->Tajik, en = English->Tajik, tjen = Tajik->English
Headwords are dictionary forms (infinitives: 'шустан'); '~' in an entry stands for the headword; the digitized
text sometimes has й where ӣ is meant. Results are cached; the site is rate-limited to ~1 request/s across all users."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import Sahifa
if len(sys.argv) < 3 or sys.argv[1] not in Sahifa.PAGES:
    sys.exit(__doc__)
s = Sahifa(sys.argv[1])
for w in sys.argv[2:]:
    r = s.lookup(w)
    print(f"== {w}: {r if r else '(not found)' if r == '' else '(network error, try later)'}")
