#!/usr/bin/env python3
"""Smoke test against a booted emulator or device.

Clears app data, installs the debug APK, walks through onboarding with uiautomator
(including the system live-wallpaper picker), and checks that our wallpaper service is
the active wallpaper. Screenshots land in --out.

  cd android && ./gradlew :app:assembleDebug && python3 tools/smoke.py

Needs adb on PATH and an English-locale device. For a lock-screen screenshot on the
emulator (which has no lock by default): `adb shell locksettings set-pin 1234`, sleep/wake,
screenshot, then `adb shell locksettings clear --old 1234`.
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


def node_center(exact_text: str):
    """Centre of the first node whose text or content-desc equals exact_text."""
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    try:
        root = ET.fromstring(adb("shell", "cat", "/sdcard/ui.xml").stdout)
    except ET.ParseError:
        return None
    for n in root.iter("node"):
        if exact_text in ((n.get("text") or ""), (n.get("content-desc") or "")):
            b = list(map(int, re.findall(r"\d+", n.get("bounds"))))
            return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
    return None


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


def wallpaper_ids():
    dump = adb("shell", "dumpsys", "wallpaper").stdout.decode(errors="replace")
    return re.findall(r"User 0: id=(\d+): mWhich=(\d+)", dump)


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
    print("install:", adb("install", "-r", args.apk).stdout.decode().strip())
    adb("shell", "pm", "clear", PKG)
    before = wallpaper_ids()
    adb("logcat", "-c")
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(4)
    screenshot(out, "01_welcome")

    ok = tap("Get started")
    for i in range(4):
        ok &= tap("Next", wait=1.5)
    screenshot(out, "02_ready")
    ok &= tap("Set my wallpaper", wait=6)
    screenshot(out, "03_picker")
    # Live mode (the default) hands over to the system picker.
    ok &= tap("Set wallpaper", wait=3)
    for choice in ("Home screen and lock screen", "Home and lock screen", "Home screen"):
        if node_center(choice):
            tap(choice, wait=4)
            break
    screenshot(out, "03_home")
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(2.5)
    screenshot(out, "04_launcher")

    after = wallpaper_ids()
    changed = before != after and live_active()
    crashes = [l for l in adb("logcat", "-d", "-v", "brief").stdout.decode(errors="replace").splitlines()
               if "AndroidRuntime" in l and "FATAL" in l]
    print(f"wallpaper ids before={before} after={after} live={live_active()} changed={changed}")
    print("crashes:", crashes or "none")
    print(f"screenshots in {out}")
    return 0 if ok and changed and not crashes else 1


if __name__ == "__main__":
    sys.exit(main())
