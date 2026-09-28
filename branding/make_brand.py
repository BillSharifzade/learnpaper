#!/usr/bin/env python3
"""Generate every LearnPaper brand asset from one geometry.

The mark is two stacked flashcards; the front card carries a bold "ā" — the macron is how dictionaries mark
pronunciation and what distinguishes the Tajik letters Ӣ and Ӯ. Colours and the Onest glyphs are defined once
here and written out as:

  branding/logo.svg, mark.svg, wordmark.svg, lockup.svg      vector masters
  branding/png/*.png                                          store / social exports (rsvg-convert)
  android/.../res/drawable/ic_launcher_{background,foreground,monochrome}.xml, ic_notification.xml, ic_brand_mark.xml
  ios/LearnPaper/Assets.xcassets/AppIcon.appiconset           1024 px icon, light / dark / tinted

Usage (from the repo root; needs fontTools and rsvg-convert):
  python3 branding/make_brand.py
"""
from __future__ import annotations

import json
import math
import subprocess
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "branding"
RES = ROOT / "android/app/src/main/res"
IOS_ICON = ROOT / "ios/LearnPaper/Assets.xcassets/AppIcon.appiconset"
FONT = ROOT / "android/app/src/main/assets/fonts/Onest-Variable.ttf"

# --- palette -------------------------------------------------------------------------------------------------
IRIS_LIGHT = "#7A6BFF"   # gradient start
IRIS = "#5A48E6"         # brand primary
IRIS_DEEP = "#4A38D4"    # gradient end, glyph ink
APRICOT = "#FF9A76"      # back card
PAPER = "#FFFBF5"        # front card
INK = "#1F1A2E"          # wordmark / text
SHADOW = "#1B1150"

# --- geometry (1024 x 1024 design space) -----------------------------------------------------------------------
BACK = dict(cx=560, cy=470, w=360, h=450, r=64, rot=12)
FRONT = dict(cx=492, cy=540, w=380, h=470, r=68, rot=-7)
GLYPH_H = 250  # height of "ā" on the front card


def onest(weight: int) -> TTFont:
    return instancer.instantiateVariableFont(TTFont(FONT), {"wght": weight})


def glyph_path(font: TTFont, ch: str, transform) -> str:
    gs = font.getGlyphSet()
    name = font.getBestCmap()[ord(ch)]
    pen = SVGPathPen(gs, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
    gs[name].draw(TransformPen(pen, transform))
    return pen.getCommands()


def glyph_bounds(font: TTFont, ch: str):
    from fontTools.pens.boundsPen import BoundsPen
    gs = font.getGlyphSet()
    b = BoundsPen(gs)
    gs[font.getBestCmap()[ord(ch)]].draw(b)
    return b.bounds


def centred_glyph(font, ch, cx, cy, height, rot=0.0):
    """Path for `ch` scaled to `height`, centred on (cx, cy), rotated by `rot` degrees about that centre."""
    x0, y0, x1, y1 = glyph_bounds(font, ch)
    s = height / (y1 - y0)
    gx, gy = (x0 + x1) / 2, (y0 + y1) / 2
    a = math.radians(rot)
    ca, sa = math.cos(a), math.sin(a)
    # font units (y up) -> scale, flip y, rotate, translate
    xx, xy = s * ca, s * sa
    yx, yy = s * sa, -s * ca
    dx = cx - (xx * gx + yx * gy)
    dy = cy - (xy * gx + yy * gy)
    return glyph_path(font, ch, (xx, xy, yx, yy, dx, dy))


def rounded_rect(cx, cy, w, h, r, rot, k=1.0, ox=0.0, oy=0.0):
    """Rounded rectangle as an absolute path, rotated about its centre, then scaled by k and offset."""
    a = math.radians(rot)
    ca, sa = math.cos(a), math.sin(a)

    def p(x, y):
        x, y = x - cx, y - cy
        return (ox + k * (cx + x * ca - y * sa), oy + k * (cy + x * sa + y * ca))

    x0, y0, x1, y1 = cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2
    c = 0.5523 * r  # cubic approximation of a quarter circle
    pts = [
        ("M", [p(x0 + r, y0)]), ("L", [p(x1 - r, y0)]), ("C", [p(x1 - r + c, y0), p(x1, y0 + r - c), p(x1, y0 + r)]),
        ("L", [p(x1, y1 - r)]), ("C", [p(x1, y1 - r + c), p(x1 - r + c, y1), p(x1 - r, y1)]),
        ("L", [p(x0 + r, y1)]), ("C", [p(x0 + r - c, y1), p(x0, y1 - r + c), p(x0, y1 - r)]),
        ("L", [p(x0, y0 + r)]), ("C", [p(x0, y0 + r - c), p(x0 + r - c, y0), p(x0 + r, y0)]),
    ]
    f = lambda v: f"{v:.2f}".rstrip("0").rstrip(".")
    return "".join(cmd + " ".join(f"{f(x)},{f(y)}" for x, y in xs) for cmd, xs in pts) + "Z"


def scale_path(path: str, k: float, ox: float, oy: float) -> str:
    """Scale an absolute path made of M/L/Q/C/Z commands (as produced here) by k and offset it."""
    import re
    out = []
    for cmd, args in re.findall(r"([MLQCZ])([^MLQCZ]*)", path):
        nums = [float(v) for v in re.findall(r"-?\d+(?:\.\d+)?(?:e-?\d+)?", args)]
        pairs = [(ox + k * nums[i], oy + k * nums[i + 1]) for i in range(0, len(nums), 2)]
        f = lambda v: f"{v:.2f}".rstrip("0").rstrip(".")
        out.append(cmd + " ".join(f"{f(x)},{f(y)}" for x, y in pairs))
    return "".join(out)


# --- SVG masters -----------------------------------------------------------------------------------------------

def svg_defs():
    return f'''<defs>
  <linearGradient id="iris" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{IRIS_LIGHT}"/><stop offset="1" stop-color="{IRIS_DEEP}"/></linearGradient>
  <linearGradient id="irisDark" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#2A2250"/><stop offset="1" stop-color="#15112B"/></linearGradient>
  <filter id="shadow" x="-30%" y="-30%" width="160%" height="160%"><feDropShadow dx="0" dy="18" stdDeviation="22" flood-color="{SHADOW}" flood-opacity="0.28"/></filter>
</defs>'''


def mark_svg_body(font, front=PAPER, back=APRICOT, ink=IRIS_DEEP, shadow=True):
    back_p = rounded_rect(**BACK)
    front_p = rounded_rect(**FRONT)
    g = centred_glyph(font, "ā", FRONT["cx"], FRONT["cy"], GLYPH_H, FRONT["rot"])
    filt = ' filter="url(#shadow)"' if shadow else ""
    return f'<path d="{back_p}" fill="{back}"/><g{filt}><path d="{front_p}" fill="{front}"/><path d="{g}" fill="{ink}"/></g>'


def write_svgs(font800):
    body = mark_svg_body(font800)
    (BRAND / "logo.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">{svg_defs()}'
        f'<rect width="1024" height="1024" rx="224" fill="url(#iris)"/>{body}</svg>\n')
    (BRAND / "logo-square.svg").write_text(  # full-bleed, for iOS / stores that apply their own mask
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">{svg_defs()}'
        f'<rect width="1024" height="1024" fill="url(#iris)"/>{body}</svg>\n')
    (BRAND / "logo-square-dark.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">{svg_defs()}'
        f'<rect width="1024" height="1024" fill="url(#irisDark)"/>{mark_svg_body(font800, front="#F4F0FF", back="#FF9A76", ink=IRIS)}</svg>\n')
    (BRAND / "mark.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="240 180 560 680">{svg_defs()}{body}</svg>\n')

    # wordmark: "Learn" in ink + "Paper" in iris, Onest 800, tracked slightly tight
    gs = font800.getGlyphSet()
    cmap = font800.getBestCmap()
    size = 200.0
    s = size / 1000
    x = 0.0
    parts = []
    for i, ch in enumerate("LearnPaper"):
        name = cmap[ord(ch)]
        pen = SVGPathPen(gs, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
        gs[name].draw(TransformPen(pen, (s, 0, 0, -s, x, 160)))
        parts.append((pen.getCommands(), INK if i < 5 else IRIS))
        x += gs[name].width * s - 3
    width = math.ceil(x) + 4
    paths = "".join(f'<path d="{d}" fill="{c}"/>' for d, c in parts)
    (BRAND / "wordmark.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} 210">{paths}</svg>\n')
    # lockup: mark (scaled) + wordmark
    k = 0.62  # wordmark scale inside the lockup: cap height ~ 45% of the mark
    lock_w = math.ceil(282 + k * width + 8)
    (BRAND / "lockup.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {lock_w} 240">{svg_defs()}'
        f'<g transform="scale(0.234)"><rect width="1024" height="1024" rx="224" fill="url(#iris)"/>{body}</g>'
        f'<g transform="translate(282 {120 - k * 105:.1f}) scale({k})">{paths}</g></svg>\n')
    return width


def text_path(font: TTFont, text: str, size: float, x: float, baseline: float, tracking: float = 0.0) -> tuple[str, float]:
    """Outline of `text` in `font` at `size` px, starting at (x, baseline); returns (path, advance)."""
    gs = font.getGlyphSet()
    cmap = font.getBestCmap()
    s = size / font["head"].unitsPerEm
    out = []
    for ch in text:
        name = cmap.get(ord(ch))
        if name is None:
            continue
        pen = SVGPathPen(gs, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
        gs[name].draw(TransformPen(pen, (s, 0, 0, -s, x, baseline)))
        out.append(pen.getCommands())
        x += gs[name].width * s + tracking
    return "".join(out), x


def write_feature_graphic(font800, font600):
    """Google Play feature graphic, 1024 x 500: gradient, the two cards, wordmark and the Tajik tagline."""
    body = mark_svg_body(font800)
    word, end = text_path(font800, "LearnPaper", 92, 452, 250, -2)
    tag1, _ = text_path(font600, "Ҳар соат калимаи нав", 36, 456, 316)
    tag2, _ = text_path(font600, "дар экрани шумо", 36, 456, 362)
    tag = tag1 + tag2
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 500">{svg_defs()}'
           f'<rect width="1024" height="500" fill="url(#iris)"/>'
           f'<circle cx="930" cy="70" r="190" fill="#FFFFFF" fill-opacity="0.07"/>'
           f'<circle cx="120" cy="480" r="220" fill="#FFFFFF" fill-opacity="0.06"/>'
           f'<g transform="translate(40 30) scale(0.42)">{body}</g>'
           f'<path d="{word}" fill="#FFFFFF"/><path d="{tag}" fill="#FFFFFF" fill-opacity="0.88"/></svg>\n')
    (BRAND / "feature-graphic.svg").write_text(svg)
    subprocess.run(["rsvg-convert", "-w", "1024", "-h", "500", str(BRAND / "feature-graphic.svg"),
                    "-o", str(BRAND / "png" / "feature-graphic.png")], check=True)


def export_pngs():
    out = BRAND / "png"
    out.mkdir(exist_ok=True)
    jobs = [
        ("logo.svg", "icon-512.png", 512, 512),
        ("logo.svg", "icon-192.png", 192, 192),
        ("logo-square.svg", "icon-1024-square.png", 1024, 1024),
        ("logo-square-dark.svg", "icon-1024-square-dark.png", 1024, 1024),
        ("lockup.svg", "lockup.png", None, 240),
        ("wordmark.svg", "wordmark.png", None, 210),
    ]
    for src, dst, w, h in jobs:
        cmd = ["rsvg-convert", str(BRAND / src), "-o", str(out / dst)]
        if w: cmd[1:1] = ["-w", str(w)]
        if h: cmd[1:1] = ["-h", str(h)]
        subprocess.run(cmd, check=True)


# --- Android vector drawables ------------------------------------------------------------------------------------

VD_HEAD = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by branding/make_brand.py; edit the script, not this file. -->\n'


def vector(size_dp, viewport, body, extra_ns=""):
    return (f'{VD_HEAD}<vector xmlns:android="http://schemas.android.com/apk/res/android"{extra_ns}\n'
            f'    android:width="{size_dp}dp"\n    android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="{viewport}"\n    android:viewportHeight="{viewport}">\n{body}</vector>\n')


def adaptive_transform(k=1.18):
    """Map the 1024 design (whose rounded square = the 72dp visible area) into the 108dp adaptive canvas,
    enlarging the mark by k around the design centre so it fills the icon like other launcher icons."""
    base = 72 / 1024
    kk = base * k
    ox = 54 - 512 * kk
    oy = 54 - 512 * kk
    return kk, ox, oy


def write_android(font800):
    RES.joinpath("drawable").mkdir(parents=True, exist_ok=True)
    k, ox, oy = adaptive_transform()
    back = scale_path(rounded_rect(**BACK), k, ox, oy)
    front = scale_path(rounded_rect(**FRONT), k, ox, oy)
    shadow = scale_path(rounded_rect(**{**FRONT, "cy": FRONT["cy"] + 16}), k, ox, oy)
    glyph = scale_path(centred_glyph(font800, "ā", FRONT["cx"], FRONT["cy"], GLYPH_H, FRONT["rot"]), k, ox, oy)

    bg = (f'    <path android:pathData="M0,0h108v108h-108z">\n'
          f'        <aapt:attr name="android:fillColor">\n'
          f'            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="108" android:endY="108">\n'
          f'                <item android:offset="0" android:color="{IRIS_LIGHT}" />\n'
          f'                <item android:offset="1" android:color="{IRIS_DEEP}" />\n'
          f'            </gradient>\n        </aapt:attr>\n    </path>\n')
    (RES / "drawable/ic_launcher_background.xml").write_text(
        vector(108, 108, bg, extra_ns='\n    xmlns:aapt="http://schemas.android.com/aapt"'))

    fg = (f'    <path android:fillColor="{APRICOT}" android:pathData="{back}" />\n'
          f'    <path android:fillColor="{SHADOW}" android:fillAlpha="0.22" android:pathData="{shadow}" />\n'
          f'    <path android:fillColor="{PAPER}" android:pathData="{front}" />\n'
          f'    <path android:fillColor="{IRIS_DEEP}" android:pathData="{glyph}" />\n')
    (RES / "drawable/ic_launcher_foreground.xml").write_text(vector(108, 108, fg))

    # themed icon: the system tints it; alpha carries the shape. Front card with the glyph cut out.
    mono = (f'    <path android:fillColor="#FFFFFFFF" android:fillAlpha="0.5" android:pathData="{back}" />\n'
            f'    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{front}{glyph}" />\n')
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(vector(108, 108, mono))

    # notification icon: 24dp, white silhouette of the front card with the glyph cut out
    nk2 = 24 / 560
    nox2, noy2 = 12 - FRONT["cx"] * nk2, 12 - FRONT["cy"] * nk2
    nfront = scale_path(rounded_rect(**FRONT), nk2, nox2, noy2)
    nglyph = scale_path(centred_glyph(font800, "ā", FRONT["cx"], FRONT["cy"], GLYPH_H * 1.12, FRONT["rot"]), nk2, nox2, noy2)
    notif = f'    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{nfront}{nglyph}" />\n'
    (RES / "drawable/ic_notification.xml").write_text(vector(24, 24, notif))

    # in-app brand mark (no background), for onboarding / about
    bk = 96 / 680
    bx, by = -240 * bk, -180 * bk
    mark = (f'    <path android:fillColor="{APRICOT}" android:pathData="{scale_path(rounded_rect(**BACK), bk, bx, by)}" />\n'
            f'    <path android:fillColor="{SHADOW}" android:fillAlpha="0.16" android:pathData="{scale_path(rounded_rect(**{**FRONT, "cy": FRONT["cy"] + 14}), bk, bx, by)}" />\n'
            f'    <path android:fillColor="{PAPER}" android:pathData="{scale_path(rounded_rect(**FRONT), bk, bx, by)}" />\n'
            f'    <path android:fillColor="{IRIS_DEEP}" android:pathData="{scale_path(centred_glyph(font800, "ā", FRONT["cx"], FRONT["cy"], GLYPH_H, FRONT["rot"]), bk, bx, by)}" />\n')
    (RES / "drawable/ic_brand_mark.xml").write_text(vector(96, 96, mark))

    (RES / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@drawable/ic_launcher_background" />\n'
                '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
                '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n</adaptive-icon>\n')
    (RES / "mipmap-anydpi-v26/ic_launcher.xml").write_text(adaptive)


def write_ios():
    IOS_ICON.mkdir(parents=True, exist_ok=True)
    for src, dst in (("logo-square.svg", "icon-1024.png"), ("logo-square-dark.svg", "icon-1024-dark.png")):
        subprocess.run(["rsvg-convert", "-w", "1024", "-h", "1024", "-b", "#5A48E6" if "dark" not in src else "#15112B",
                        str(BRAND / src), "-o", str(IOS_ICON / dst)], check=True)
    contents = {
        "images": [
            {"filename": "icon-1024.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"},
            {"appearances": [{"appearance": "luminosity", "value": "dark"}], "filename": "icon-1024-dark.png",
             "idiom": "universal", "platform": "ios", "size": "1024x1024"},
        ],
        "info": {"author": "xcode", "version": 1},
    }
    (IOS_ICON / "Contents.json").write_text(json.dumps(contents, indent=2) + "\n")
    (IOS_ICON.parent / "Contents.json").write_text(json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2) + "\n")


def main():
    font800 = onest(800)
    write_svgs(font800)
    export_pngs()
    write_feature_graphic(font800, onest(600))
    write_android(font800)
    write_ios()
    print("brand assets written")


if __name__ == "__main__":
    main()
