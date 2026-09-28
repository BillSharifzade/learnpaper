#!/usr/bin/env python3
"""Download and build the data the content tools need, into content/cache/tools/ (git-ignored).

  python3 content/tools/setup.py

- tj_corpus.db  Leipzig Tajik corpora (news 2011, web 2015, Wikipedia 2021; ~564k sentences) as an SQLite
                FTS5 index plus a word-form frequency table — used by conc.py, tjfreq.py, validate.py.
- ru_stress.db  OpenRussian word lists (github.com/Badestrand/russian-dictionary) with stress — stress.py.
- l10n/         GNOME's Tajik translations (.po) — the UI terminology glossary for l10n.py.
Everything else (dictionary lookups, Wiktionary) is fetched on demand and cached in web_cache.db.
Takes a few minutes and ~250 MB of disk.
"""
import csv
import io
import re
import sqlite3
import tarfile
import urllib.request
from collections import Counter
from pathlib import Path

DATA = Path(__file__).resolve().parents[1] / "cache" / "tools"
LEIPZIG = "https://downloads.wortschatz-leipzig.de/corpora/{}.tar.gz"
CORPORA = ["tgk_wikipedia_2021_100K", "tgk_newscrawl_2011_100K", "tgk-tj_web_2015_300K"]
OPENRUSSIAN = "https://raw.githubusercontent.com/Badestrand/russian-dictionary/master/{}.csv"
GNOME_PO = "https://gitlab.gnome.org/GNOME/{}/-/raw/main/po/tg.po"
GNOME_MODULES = ["gnome-control-center", "gnome-shell", "nautilus", "gtk", "gnome-calendar", "gnome-settings-daemon"]
TOKEN = re.compile(r"[а-яёғӣқӯҳҷ]+(?:-[а-яёғӣқӯҳҷ]+)*", re.I)


def get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "LearnPaper-content-tools/1.0"})
    with urllib.request.urlopen(req, timeout=300) as r:
        return r.read()


def build_corpus():
    out = DATA / "tj_corpus.db"
    if out.exists():
        print("tj_corpus.db exists, skipping")
        return
    sentences, seen = [], set()
    for name in CORPORA:
        print("downloading", name)
        with tarfile.open(fileobj=io.BytesIO(get(LEIPZIG.format(name))), mode="r:gz") as tar:
            for member in tar.getmembers():
                if member.name.endswith("-sentences.txt"):
                    for line in tar.extractfile(member).read().decode("utf-8", "replace").splitlines():
                        text = line.split("\t", 1)[-1].strip()
                        if text and text not in seen:
                            seen.add(text)
                            sentences.append(text)
    freq = Counter(w.lower() for s in sentences for w in TOKEN.findall(s))
    con = sqlite3.connect(out)
    con.execute("CREATE VIRTUAL TABLE s USING fts5(t, tokenize='unicode61 remove_diacritics 0')")
    con.executemany("INSERT INTO s(t) VALUES (?)", ((s,) for s in sentences))
    con.execute("CREATE TABLE freq(w TEXT PRIMARY KEY, n INTEGER) WITHOUT ROWID")
    con.executemany("INSERT INTO freq VALUES (?,?)", freq.items())
    con.commit()
    con.execute("INSERT INTO s(s) VALUES('optimize')")
    con.commit()
    con.close()
    print(f"tj_corpus.db: {len(sentences)} sentences, {len(freq)} word forms")


def build_stress():
    out = DATA / "ru_stress.db"
    if out.exists():
        print("ru_stress.db exists, skipping")
        return
    csv.field_size_limit(10**7)
    con = sqlite3.connect(out)
    con.execute("CREATE TABLE a(bare TEXT, accented TEXT, kind TEXT, en TEXT)")
    for kind in ("nouns", "verbs", "adjectives", "others"):
        print("downloading", kind)
        rows = csv.DictReader(io.StringIO(get(OPENRUSSIAN.format(kind)).decode("utf-8")), delimiter="\t")
        con.executemany("INSERT INTO a VALUES (?,?,?,?)", [
            (d["bare"].lower(), (d.get("accented") or "").replace("'", "́"),
             kind[:-1] if kind != "others" else "other", (d.get("translations_en") or "")[:80])
            for d in rows
        ])
    con.execute("CREATE INDEX ib ON a(bare)")
    con.commit()
    con.close()
    print("ru_stress.db built")


def fetch_l10n():
    d = DATA / "l10n"
    d.mkdir(exist_ok=True)
    for m in GNOME_MODULES:
        f = d / f"{m}_tg.po"
        if not f.exists():
            try:
                f.write_bytes(get(GNOME_PO.format(m)))
                print("fetched", f.name)
            except Exception as e:  # noqa: BLE001
                print("skip", m, e)


if __name__ == "__main__":
    DATA.mkdir(parents=True, exist_ok=True)
    build_corpus()
    build_stress()
    fetch_l10n()
    print("done:", DATA)
