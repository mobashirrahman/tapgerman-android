#!/usr/bin/env python3
"""
Render the LingoDeck icon vectors to PNG so the mark can actually be looked at.

The icon ships as three VectorDrawables and there is no way to see a VectorDrawable without
launching Android Studio. This re-implements the same geometry in plain Python — the shapes are
only rounded rectangles and a linear gradient, so a scanline fill with 4x supersampling is exact
enough to judge proportion, spacing and contrast at 512px, at 192px, and at the 48dp size a
launcher actually draws.

The geometry is read out of the vector files, not retyped, so if the vectors change this changes
with them. Run:  python3 tools/render_icon.py
"""
import math
import os
import re
import struct
import zlib

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable")
SS = 4  # supersampling factor

# A neutral mid-grey to composite against. The mark's page plate is white, so rendering on white
# would hide it entirely and make the preview worse than useless.
CHECKER = (128, 128, 128)

# Half the adaptive-icon mask: 72/2. Anything outside this is not guaranteed to be visible.
MASK_R = 36.0


# --- PNG ---------------------------------------------------------------------------

def write_png(path, width, height, rgb_rows):
    """rgb_rows: list of `height` bytes objects, each width*3 bytes."""
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


# --- Vector parsing ---------------------------------------------------------------

def parse_vector(name):
    """Pull the <path> elements out of a VectorDrawable, with fills and their gradients."""
    text = open(os.path.join(RES, name)).read()
    paths = []
    for m in re.finditer(r"<path\b(.*?)(?:/>|>(.*?)</path>)", text, re.S):
        attrs, inner = m.group(1) or "", m.group(2) or ""
        d = re.search(r'android:pathData="([^"]+)"', attrs)
        if not d:
            continue
        color = re.search(r'android:fillColor="(#[0-9A-Fa-f]{6,8})"', attrs)
        even_odd = 'evenOdd' in attrs
        stops = []
        grad = re.search(r'android:type="(\w+)"', inner)
        for item in re.finditer(r'android:color="(#[0-9A-Fa-f]{6,8})"\s*/>\s*(?:</item>|)', inner):
            pass
        for item in re.finditer(r'android:offset="([\d.]+)"\s*android:color="(#[0-9A-Fa-f]{6,8})"', inner):
            stops.append((float(item.group(1)), hex_to_rgb(item.group(2))))
        if not stops:
            for item in re.finditer(r'android:color="(#[0-9A-Fa-f]{6,8})"', inner):
                stops.append((len(stops) / 2.0, hex_to_rgb(item.group(1))))
        paths.append({
            "d": d.group(1),
            "color": hex_to_rgb(color.group(1)) if color else None,
            "gradient": stops if grad else None,
            "even_odd": even_odd,
        })
    return paths


def hex_to_rgb(h):
    h = h.lstrip("#")
    if len(h) == 8:
        h = h[2:]  # drop alpha; every fill in the icon is opaque
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# --- Path handling ----------------------------------------------------------------

def rrect_subpaths(d):
    """
    Every path in the icon is a rounded rectangle written as the same arc sequence, so parse it
    into (x, y, w, h, r) rather than writing a general SVG path engine.
    """
    out = []
    # Every rounded rect in these vectors is the same arc sequence:
    #   M x,y  h w-2r  a r,r 0 0 1 r,r  v h-2r  a r,r 0 0 1 -r,r  h -(w-2r)  a ...  v -(h-2r)  a ...
    # so the width is the `h` argument and the height is the `v` argument plus both corners.
    for m in re.finditer(
        r"M([\d.-]+),([\d.-]+)\s*h([\d.-]+)\s*a([\d.-]+),([\d.-]+)\s+0\s+0\s+1\s+([\d.-]+),([\d.-]+)\s*v([\d.-]+)\s*a",
        d,
    ):
        # The `M` lands at the start of the top edge, *after* the top-left corner arc, so the
        # rect's left edge is M.x - r. Missing that offset shifts every shape right by its own
        # radius, which is most of a bar's width and made the bars overhang the plate.
        mx, y = float(m.group(1)), float(m.group(2))
        r = float(m.group(4))
        w = float(m.group(3)) + 2 * r
        h = float(m.group(8)) + 2 * r
        out.append((mx - r, y, w, h, r))
    if out:
        return out
    # Plain rectangle, no corners: the background layer is written this way.
    for m in re.finditer(r"M([\d.-]+),([\d.-]+)\s*h([\d.-]+)\s*v([\d.-]+)", d):
        out.append((float(m.group(1)), float(m.group(2)),
                    float(m.group(3)), float(m.group(4)), 0.0))
    return out


def inside_rrect(px, py, x, y, w, h, r):
    if r <= 0:
        return x <= px <= x + w and y <= py <= y + h
    if px < x or px > x + w or py < y or py > y + h:
        return False
    # Corner discs.
    cx = min(max(px, x + r), x + w - r)
    cy = min(max(py, y + r), y + h - r)
    dx, dy = px - cx, py - cy
    return dx * dx + dy * dy <= r * r


def lerp3(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def sample_gradient(stops, t):
    t = max(0.0, min(1.0, t))
    for i in range(len(stops) - 1):
        o0, c0 = stops[i]
        o1, c1 = stops[i + 1]
        if o0 <= t <= o1:
            span = (o1 - o0) or 1.0
            return lerp3(c0, c1, (t - o0) / span)
    return stops[-1][1]


# --- Rendering --------------------------------------------------------------------

def render(size, background_path, foreground_path, mask=None, out=None):
    """Render at `size` px. `mask` is one of 'circle', 'squircle', 'full'."""
    bg = parse_vector(background_path) if background_path else []
    fg = parse_vector(foreground_path) if foreground_path else []

    W = H = size
    scale = size / 108.0  # the vectors are authored on a 108 viewport
    rows = []

    for py in range(H):
        row = bytearray(W * 3)
        sy = (py + 0.5) / scale
        for px in range(W):
            sx = (px + 0.5) / scale
            rgb = CHECKER

            # Mask, sampled with supersampling so the circle and squircle are smooth.
            if mask in ("circle", "squircle"):
                cov = 0
                for oy in range(SS):
                    for ox in range(SS):
                        fx = sx + (ox + 0.5) / SS / scale - 54.0
                        fy = sy + (oy + 0.5) / SS / scale - 54.0
                        # Android's adaptive-icon mask is the centre 72x72 of the 108x108
                        # canvas, not the whole canvas. Masking at radius 54 here would be far
                        # more generous than any launcher is and would hide a mark that gets
                        # its corners clipped in the real world.
                        if mask == "circle":
                            inside = fx * fx + fy * fy <= MASK_R * MASK_R
                        else:
                            inside = squircle_inside(fx, fy, MASK_R)
                        cov += 1 if inside else 0
                alpha = cov / (SS * SS)
                if alpha == 0:
                    row[px * 3:px * 3 + 3] = bytes(rgb)
                    continue
            else:
                alpha = 1.0

            for layer in bg + fg:
                t = (sx / 108.0 + sy / 108.0) / 2.0
                color = sample_gradient(layer["gradient"], t) if layer["gradient"] else layer["color"]
                if color is None:
                    continue
                rects = rrect_subpaths(layer["d"])
                if not rects:
                    continue
                if layer["even_odd"]:
                    hits = sum(1 for (x, y, w, h, r) in rects
                               if inside_rrect(sx, sy, x, y, w, h, r))
                    if hits % 2 == 1:
                        rgb = color
                else:
                    for (x, y, w, h, r) in rects:
                        if inside_rrect(sx, sy, x, y, w, h, r):
                            rgb = color
                            break

            v = int(round(rgb[0] * alpha + 255 * (1 - alpha)))
            v2 = int(round(rgb[1] * alpha + 255 * (1 - alpha)))
            v3 = int(round(rgb[2] * alpha + 255 * (1 - alpha)))
            row[px * 3:px * 3 + 3] = bytes((v, v2, v3))
        rows.append(bytes(row))

    write_png(out, W, H, rows)
    return out


def squircle_inside(x, y, r):
    """Superellipse, which is what Android's squircle mask approximates."""
    return abs(x / r) ** 4 + abs(y / r) ** 4 <= 1.0


if __name__ == "__main__":
    out = os.path.join(os.path.dirname(__file__), "..", "build", "icon-preview")
    os.makedirs(out, exist_ok=True)
    jobs = [
        (512, "full", "icon-512-full.png"),
        (512, "squircle", "icon-512-squircle.png"),
        (512, "circle", "icon-512-circle.png"),
        (192, "squircle", "icon-192-squircle.png"),
        (96, "squircle", "icon-96-squircle.png"),
        (48, "squircle", "icon-48-squircle.png"),
    ]
    for size, mask, name in jobs:
        path = render(
            size,
            "ic_launcher_background.xml",
            "ic_launcher_foreground.xml",
            mask=mask,
            out=os.path.join(out, name),
        )
        print("wrote", os.path.relpath(path))
