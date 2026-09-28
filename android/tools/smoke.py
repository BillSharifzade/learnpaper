#!/usr/bin/env python3
"""Smoke test against a booted emulator or device.

Installs the APK on a clean slate, checks that the interface starts in Tajik and switches
to Russian/English and back, walks through onboarding (including the system live-wallpaper
picker), searches the word library, puts a word on the wallpaper, and checks that our
wallpaper service is active and nothing crashed. Screenshots land in --out.

  cd android && ./gradlew :app:assembleDebug && python3 tools/smoke.py
  python3 tools/smoke.py --apk app/build/outputs/apk/release/app-release.apk   # the signed release

Needs adb on PATH; the system picker is driven with its English labels (emulator default).
For a lock-screen screenshot on the emulator (which has no lock by default):
`adb shell locksettings set-pin 1234`, sleep/wake, screenshot, then
`adb shell locksettings clear --old 1234`.
"""
import argparse
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.learnpaper"


def adb(*args, timeout=120):
    return subprocess.run(["adb", *args], capture_output=True, timeout=timeout)


def screenshot(out: pathlib.Path, name: str):
    (out / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p").stdout)
    print(f"  shot {name}")


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    try:
        root = ET.fromstring(adb("shell", "cat", "/sdcard/ui.xml").stdout)
    except ET.ParseError:
        return []
    return list(root.iter("node"))


def node_center(exact_text: str):
    """Centre of the first node whose text or content-desc equals exact_text."""
    for n in nodes():
        if exact_text in ((n.get("text") or ""), (n.get("content-desc") or "")):
            b = list(map(int, re.findall(r"\d+", n.get("bounds"))))
            return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
    return None


def visible(exact_text: str, retries: int = 6) -> bool:
    for _ in range(retries):
        if node_center(exact_text):
            return True
        time.sleep(1)
    return False


def visible_prefix(prefix: str, retries: int = 6) -> bool:
    for _ in range(retries):
        if any((n.get("text") or "").startswith(prefix) for n in nodes()):
            return True
        time.sleep(1)
    return False


def tap(exact_text: str, wait: float = 1.5, retries: int = 8) -> bool:
    for _ in range(retries):
        p = node_center(exact_text)
        if p:
            adb("shell", "input", "tap", str(p[0]), str(p[1]))
            time.sleep(wait)
            print(f"  tap '{exact_text}'")
            return True
        time.sleep(1)
    print(f"  NOT FOUND '{exact_text}'")
    return False


def check(label: str, ok: bool) -> bool:
    print(f"  {'ok  ' if ok else 'FAIL'} {label}")
    return ok


def live_active() -> bool:
    dump = adb("shell", "dumpsys", "wallpaper").stdout.decode(errors="replace")
    return f"{PKG}/{PKG}.wallpaper.LiveCardWallpaper" in dump


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", default="app/build/outputs/apk/debug/app-debug.apk")
    ap.add_argument("--out", default="/tmp/learnpaper-smoke")
    args = ap.parse_args()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    if b"device" not in adb("devices").stdout.split(b"\n", 1)[1]:
        print("no device connected")
        return 1
    adb("uninstall", PKG)
    print("install:", adb("install", args.apk).stdout.decode().strip())
    adb("logcat", "-c")
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(4)
    screenshot(out, "01_welcome")

    ok = check("interface starts in Tajik", visible("Оғоз кардан"))
    ok &= tap("English") and check("switches to English", visible("Get started"))
    ok &= tap("Русский") and check("switches to Russian", visible("Начать"))
    ok &= tap("Тоҷикӣ") and check("switches back to Tajik", visible("Оғоз кардан"))

    ok &= tap("Оғоз кардан")
    for i in range(4):
        ok &= tap("Идома", wait=1.2)
    screenshot(out, "02_ready")
    ok &= tap("Ба экран гузоштан", wait=5)
    screenshot(out, "03_picker")
    # Live mode (the default) hands over to the system picker.
    ok &= tap("Set wallpaper", wait=3)
    for choice in ("Home screen and lock screen", "Home and lock screen", "Home screen"):
        if node_center(choice):
            tap(choice, wait=4)
            break
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(2.5)
    screenshot(out, "04_today")
    ok &= check("today screen shows the schedule", visible_prefix("Калимаи навбатӣ соати"))

    ok &= tap("Калимаҳо")
    ok &= tap("Ҷустуҷӯ ба ҳар забон", wait=0.8)
    adb("shell", "input", "text", "salom")
    time.sleep(1.5)
    screenshot(out, "05_search")
    ok &= check("latin 'salom' finds салом", visible("hello"))
    ok &= tap("hello")
    screenshot(out, "06_detail")
    ok &= tap("Дар экран нишон додан", wait=2)
    ok &= check("word is shown on the wallpaper", visible("Акнун дар экрани шумо"))
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    ok &= tap("Танзимот")
    screenshot(out, "07_settings")

    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(2.5)
    screenshot(out, "08_launcher")

    live = live_active()
    crashes = [l for l in adb("logcat", "-d", "-b", "crash").stdout.decode(errors="replace").splitlines() if "FATAL" in l]
    print(f"live wallpaper active: {live}")
    print("crashes:", crashes or "none")
    print(f"screenshots in {out}")
    return 0 if ok and live and not crashes else 1


if __name__ == "__main__":
    sys.exit(main())
