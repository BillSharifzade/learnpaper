"""Shared helpers for the LearnPaper content tools: polite, cached, rate-limited web lookups."""
import fcntl, http.cookiejar, os, re, sqlite3, threading, time, urllib.parse, urllib.request
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
DATA = Path(__file__).resolve().parents[1] / "cache" / "tools"
DATA.mkdir(parents=True, exist_ok=True)
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36"
WIKI_UA = "LearnPaper-content-check/1.0 (vocabulary app; https://github.com/BillSharifzade/learnpaper)"
_cache = sqlite3.connect(DATA / "web_cache.db", timeout=60, check_same_thread=False)
_lock = threading.Lock()
_cache.execute("PRAGMA journal_mode=WAL")
_cache.execute("CREATE TABLE IF NOT EXISTS c(k TEXT PRIMARY KEY, v TEXT)")


def cached(key, fn):
    with _lock:
        row = _cache.execute("SELECT v FROM c WHERE k=?", (key,)).fetchone()
    if row is not None:
        return row[0]
    v = fn()
    if v is not None:
        with _lock:
            _cache.execute("INSERT OR REPLACE INTO c VALUES (?,?)", (key, v))
            _cache.commit()
    return v


def throttle(name, interval):
    """Cross-process rate limit: at most one call per `interval` seconds for `name`."""
    lock = DATA / f".{name}.lock"
    with open(lock, "a+") as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        f.seek(0)
        last = float(f.read() or 0)
        wait = last + interval - time.time()
        if wait > 0:
            time.sleep(wait)
        f.seek(0); f.truncate(); f.write(str(time.time())); f.flush()
        fcntl.flock(f, fcntl.LOCK_UN)


class Sahifa:
    PAGES = {"tj": "tadzhiksko_russkij", "ru": "russko_tadzhikskij", "en": "anglo_tadzhikskij", "tjen": "tadzhiksko_anglijskij"}

    def __init__(self, direction):
        self.direction = direction
        self.url = f"https://sahifa.tj/{self.PAGES[direction]}.aspx"
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.op.addheaders = [("User-Agent", UA)]
        self.fields = None

    def _init(self):
        throttle("sahifa", 0.8)
        html = self.op.open(self.url, timeout=30).read().decode()
        self.fields = dict(re.findall(r'name="(__[A-Z]+)"[^>]*value="([^"]*)"', html))
        btn = re.search(r'name="ctl00\$ContentPlaceHolder1\$Button2"[^>]*value="([^"]*)"', html)
        self.btn = btn.group(1) if btn else "Перевод"

    def _fetch(self, term):
        err = ""
        for attempt in range(3):
            try:
                if self.fields is None:
                    self._init()
                f = dict(self.fields)
                f["ctl00$ContentPlaceHolder1$tbAuto"] = term
                f["ctl00$ContentPlaceHolder1$Button2"] = self.btn
                throttle("sahifa", 0.8)
                html = self.op.open(self.url, urllib.parse.urlencode(f).encode(), timeout=30).read().decode()
                new = dict(re.findall(r'name="(__[A-Z]+)"[^>]*value="([^"]*)"', html))
                if new:
                    self.fields = new
                m = re.search(r'id="(?:ctl00_)?ContentPlaceHolder1_Label1"[^>]*>(.*?)</span>', html, re.S)
                if not m:
                    return ""
                txt = re.sub(r"<br\s*/?>", "\n", m.group(1))
                txt = re.sub(r"<[^>]+>", "", txt)
                return re.sub(r"[ \t]+", " ", txt).strip()
            except Exception as e:  # noqa: BLE001
                err = str(e)
                self.fields = None
                time.sleep(2 + attempt * 4)
        return None  # not cached, may retry later

    def lookup(self, term):
        term = term.strip()
        return cached(f"sahifa:{self.direction}:{term}", lambda: self._fetch(term))


def wiki_raw(host, title):
    def fetch():
        url = f"https://{host}/w/index.php?title={urllib.parse.quote(title)}&action=raw"
        req = urllib.request.Request(url, headers={"User-Agent": WIKI_UA})
        for attempt in range(3):
            try:
                throttle("wiki", 0.35)
                return urllib.request.urlopen(req, timeout=30).read().decode()
            except urllib.error.HTTPError as e:
                if e.code == 404:
                    return ""
                time.sleep(2 + attempt * 4)
            except Exception:  # noqa: BLE001
                time.sleep(2 + attempt * 4)
        return None
    return cached(f"wiki:{host}:{title}", fetch)
