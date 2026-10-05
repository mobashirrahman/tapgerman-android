#!/usr/bin/env python3
"""
Render the Play Store feature graphic (1024x500 PNG) with plain stdlib Python.

Same technique as render_icon.py: no Pillow on this machine, so the PNG is
assembled by hand (scanline fill + zlib, no text rasterisation available).

Design: the brand's indigo-to-violet diagonal gradient with the product mark —
three text lines sharing a left edge, the middle one taller, wider and electric
— centred, sized to survive Play's cropping on different surfaces. No type:
there is no font rasteriser here, so the wordmark ("TapGerman – German Reader")
is overlaid by whoever owns the listing, or left off; the graphic is valid
without it.
"""

import os
import struct
import zlib

W, H = 1024, 500
SS = 2  # supersampling factor

# Brand gradient stops from drawable/ic_launcher_background.xml.
STOPS = [(0.0, (0x2B, 0x1A, 0x8C)), (0.55, (0x5B, 0x4B, 0xCB)), (1.0, (0x8A, 0x5C, 0xF0))]
PALE = (0xF2, 0xF0, 0xFF)
ACCENT = (0xC9, 0xF5, 0x3C)

# Bars in a 108-unit icon space, echoing ic_launcher_foreground.xml: two pale
# lines with ragged right ends, one taller/wider electric line between them.
BARS = [
    (36.0, 31.0, 24.5, 9.5, 4.5, PALE),
    (39.0, 45.5, 35.0, 15.5, 7.5, ACCENT),
    (36.0, 65.5, 28.5, 9.5, 4.5, PALE),
]

OUT = os.path.join(os.path.dirname(__file__), "..", "play-assets",
                   "feature-graphic-1024x500.png")


def write_png(path, width, height, rgb_rows):
    raw = b"".join(b"\x00" + row for row in rgb_rows)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def lerp3(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def sample_gradient(t):
    t = max(0.0, min(1.0, t))
    for (t0, c0), (t1, c1) in zip(STOPS, STOPS[1:]):
        if t <= t1 or t1 >= 1.0:
            span = (t1 - t0) or 1.0
            return lerp3(c0, c1, (t - t0) / span)
    return STOPS[-1][1]


def inside_rrect(px, py, x, y, w, h, r):
    if px < x or px > x + w or py < y or py > y + h:
        return False
    cx = min(max(px, x + r), x + w - r)
    cy = min(max(py, y + r), y + h - r)
    dx, dy = px - cx, py - cy
    return dx * dx + dy * dy <= r * r


def main():
    # Icon space (108x108) mapped so the mark is ~300px tall and centred.
    scale = 300.0 / 108.0
    ox = (W - 108.0 * scale) / 2.0
    oy = (H - 108.0 * scale) / 2.0

    rows = []
    for y in range(H):
        row = bytearray()
        for x in range(W):
            r = g = b = 0
            for sy in range(SS):
                for sx in range(SS):
                    px = x + (sx + 0.5) / SS
                    py = y + (sy + 0.5) / SS
                    t = (px / W + py / H) / 2.0
                    col = sample_gradient(t)
                    ix, iy = (px - ox) / scale, (py - oy) / scale
                    for bx, by, bw, bh, br, fill in BARS:
                        if inside_rrect(ix, iy, bx, by, bw, bh, br):
                            col = fill
                            break
                    r += col[0]
                    g += col[1]
                    b += col[2]
            n = SS * SS
            row += bytes((r // n, g // n, b // n))
        rows.append(bytes(row))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    write_png(OUT, W, H, rows)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
