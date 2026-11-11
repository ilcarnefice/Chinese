#!/usr/bin/env python3
"""Draws the mouth diagrams (mouth.json and its pictures, see SPEC.md) to PNG
files with the same rules as the app, so they can be judged without building it.

Usage: python3 tools/mouth/preview.py <assets dir> <out dir> [--only sheets,poses,plain,labels,blends,phone] [--poses a,b]
       ("plain" draws each pose without markers; it is only written when asked for)
"""
import json
import math
import os
import sys

import cv2
import numpy as np

POSES = [
    ("rest", "neutral start"), ("lips", "b p"), ("lipsNasal", "m"), ("lipTeeth", "f"),
    ("ridge", "d t"), ("ridgeNasal", "n, -n"), ("ridgeLateral", "l"),
    ("velar", "g k"), ("velarFric", "h"), ("velarNasal", "-ng"),
    ("palatal", "j q closure"), ("palatalFric", "x"),
    ("retroflex", "zh ch closure"), ("retroflexFric", "sh r"),
    ("dental", "z c closure"), ("dentalFric", "s"),
    ("a", "a"), ("o", "o"), ("e", "e"), ("eFront", "e of ie, ei"), ("schwa", "e of en, eng"),
    ("i", "i"), ("u", "u"), ("yu", "u-umlaut"), ("er", "er"),
]
# How each pose is usually shown: air, voiced.
TYPICAL = {
    "rest": ("none", False), "lips": ("none", False), "lipsNasal": ("nasal", True), "lipTeeth": ("oral", False),
    "ridge": ("none", False), "ridgeNasal": ("nasal", True), "ridgeLateral": ("oral", True),
    "velar": ("none", False), "velarFric": ("oral", False), "velarNasal": ("nasal", True),
    "palatal": ("none", False), "palatalFric": ("oral", False), "retroflex": ("none", False),
    "retroflexFric": ("oral", False), "dental": ("none", False), "dentalFric": ("oral", False),
    "a": ("oral", True), "o": ("oral", True), "e": ("oral", True), "eFront": ("oral", True),
    "schwa": ("oral", True), "i": ("oral", True), "u": ("oral", True), "yu": ("oral", True), "er": ("oral", True),
}
BLENDS = ("rest:lips lips:a rest:lipTeeth rest:ridge ridge:a ridgeNasal:i rest:velar velar:a rest:palatal "
          "palatal:palatalFric palatalFric:i rest:retroflex retroflex:retroflexFric rest:dental dental:dentalFric "
          "rest:a a:i a:u a:ridgeNasal a:velarNasal o:u e:er i:eFront u:a u:o yu:eFront i:velarNasal").split()


def rgb(text):
    v = int(text.lstrip("#"), 16)
    return (v & 255, (v >> 8) & 255, (v >> 16) & 255)      # BGR


class Art:
    def __init__(self, assets):
        self.assets = assets
        with open(os.path.join(assets, "mouth.json"), encoding="utf-8") as f:
            self.data = json.load(f)
        self.view = self.data["views"]["side"]
        self.bitmaps = {}
        self.layers = self.view["layers"]
        self.rest = {}
        for layer in self.layers:
            if layer["type"] == "image" and layer.get("cols"):
                x, y, w, h = layer["rect"]
                c, r = layer["cols"], layer["rows"]
                self.rest[layer["id"]] = np.array(
                    [(x + w * i / c, y + h * j / r) for j in range(r + 1) for i in range(c + 1)], dtype=np.float64)

    def bitmap(self, layer):
        key = (layer["src"], tuple(layer.get("crop", ())))
        if key not in self.bitmaps:
            im = cv2.imread(os.path.join(self.assets, layer["src"]), cv2.IMREAD_UNCHANGED)
            if im.shape[2] == 3:
                im = np.dstack([im, np.full(im.shape[:2], 255, np.uint8)])
            if layer.get("crop"):
                x, y, w, h = layer["crop"]
                im = im[y:y + h, x:x + w]
            self.bitmaps[key] = im.astype(np.float32)
        return self.bitmaps[key]

    def frame(self, pose):
        """Grid positions of every image layer in a pose."""
        mesh = self.data["poses"][pose].get("mesh", {})
        unit = self.data.get("meshUnit", 1.0)
        return {lid: rest + (np.array(mesh[lid], dtype=np.float64).reshape(-1, 2) * unit if lid in mesh else 0)
                for lid, rest in self.rest.items()}

    def blend(self, a, b, t):
        fa, fb = self.frame(a), self.frame(b)
        return {k: fa[k] + (fb[k] - fa[k]) * t for k in fa}


def draw_mesh(canvas, bitmap, cols, rows, points, scale):
    """Stretches `bitmap` over a grid whose corners are at `points` (view units), as Canvas.drawBitmapMesh does."""
    h, w = canvas.shape[:2]
    bh, bw = bitmap.shape[:2]
    layer = np.zeros((h, w, 4), np.float32)
    dst_all = points * scale
    for r in range(rows):
        for c in range(cols):
            sx0, sx1 = bw * c / cols, bw * (c + 1) / cols
            sy0, sy1 = bh * r / rows, bh * (r + 1) / rows
            if bitmap[int(sy0):int(math.ceil(sy1)), int(sx0):int(math.ceil(sx1)), 3].max() == 0:
                continue
            i = r * (cols + 1) + c
            corners_src = {i: (sx0, sy0), i + 1: (sx1, sy0), i + cols + 1: (sx0, sy1), i + cols + 2: (sx1, sy1)}
            for tri in ((i, i + cols + 1, i + cols + 2), (i, i + cols + 2, i + 1)):
                src = np.float32([corners_src[t] for t in tri])
                dst = np.float32([dst_all[t] for t in tri])
                x0 = max(int(math.floor(dst[:, 0].min())) - 1, 0)
                y0 = max(int(math.floor(dst[:, 1].min())) - 1, 0)
                x1 = min(int(math.ceil(dst[:, 0].max())) + 2, w)
                y1 = min(int(math.ceil(dst[:, 1].max())) + 2, h)
                if x1 <= x0 or y1 <= y0:
                    continue
                m = cv2.getAffineTransform(src, (dst - (x0, y0)).astype(np.float32))
                # sample at pixel centres, as a renderer does
                m[:, 2] += 0.5 - (m[:, 0] * 0.5 + m[:, 1] * 0.5)
                patch = cv2.warpAffine(bitmap, m, (x1 - x0, y1 - y0), flags=cv2.INTER_LINEAR,
                                       borderMode=cv2.BORDER_REPLICATE)
                mask = np.zeros((y1 - y0, x1 - x0), np.uint8)
                cv2.fillConvexPoly(mask, np.round((dst - (x0, y0)) * 16).astype(np.int32), 1, lineType=8, shift=4)
                region = layer[y0:y1, x0:x1]
                region[mask > 0] = patch[mask > 0]
    alpha = layer[:, :, 3:4] / 255.0
    canvas[:] = canvas * (1 - alpha) + layer[:, :, :3] * alpha


def draw_image(canvas, bitmap, rect, scale):
    x, y, w, h = [v * scale for v in rect]
    m = np.float32([[w / bitmap.shape[1], 0, x], [0, h / bitmap.shape[0], y]])
    patch = cv2.warpAffine(bitmap, m, (canvas.shape[1], canvas.shape[0]), flags=cv2.INTER_LINEAR)
    alpha = patch[:, :, 3:4] / 255.0
    canvas[:] = canvas * (1 - alpha) + patch[:, :, :3] * alpha


def smooth(points, per_segment=12):
    pts = [np.array(p, dtype=np.float64) for p in points]
    n = len(pts)
    out = []
    for i in range(n - 1):
        p0, p1, p2, p3 = pts[max(i - 1, 0)], pts[i], pts[i + 1], pts[min(i + 2, n - 1)]
        for k in range(per_segment):
            t = k / per_segment
            c1, c2 = p1 + (p2 - p0) / 6, p2 - (p3 - p1) / 6
            out.append((1 - t) ** 3 * p1 + 3 * (1 - t) ** 2 * t * c1 + 3 * (1 - t) * t * t * c2 + t ** 3 * p2)
    out.append(pts[-1])
    return np.array(out)


def line(img, pts, colour, width, scale):
    cv2.polylines(img, [np.round(np.array(pts) * scale * 16).astype(np.int32)], False, colour,
                  max(int(round(width * scale)), 1), cv2.LINE_AA, shift=4)


def dashed(img, pts, colour, width, scale, on=22.0, off=18.0):
    pts = np.array(pts)
    seg = np.linalg.norm(np.diff(pts, axis=0), axis=1)
    s = np.concatenate([[0], np.cumsum(seg)])
    start = 0.0
    while start < s[-1]:
        end = min(start + on, s[-1])
        idx = (s >= start) & (s <= end)
        part = [np.array([np.interp(start, s, pts[:, 0]), np.interp(start, s, pts[:, 1])])] + list(pts[idx]) + \
               [np.array([np.interp(end, s, pts[:, 0]), np.interp(end, s, pts[:, 1])])]
        line(img, part, colour, width, scale)
        start += on + off


def arrow(img, points, colour, scale):
    pts = smooth(points)
    white = np.full_like(img, 255)
    under = img.copy()
    line(under, pts, (255, 255, 255), 18, scale)
    img[:] = cv2.addWeighted(under, 0.55, img, 0.45, 0)
    dashed(img, pts, colour, 10, scale)
    tip = np.array(points[-1], float)
    d = tip - np.array(points[-2], float)
    u = d / (np.linalg.norm(d) or 1)
    base = tip - u * 34
    n = np.array([-u[1], u[0]])
    head = np.array([tip + u * 8, base + n * 19, base - n * 19])
    cv2.fillPoly(img, [np.round(head * scale * 16).astype(np.int32)], colour, cv2.LINE_AA, shift=4)
    cv2.polylines(img, [np.round(head * scale * 16).astype(np.int32)], True, (255, 255, 255),
                  max(int(round(3 * scale)), 1), cv2.LINE_AA, shift=4)
    del white


def arc(cx, cy, radius, a0, a1, steps=12):
    return [(cx + radius * math.cos(math.radians(a0 + (a1 - a0) * i / steps)),
             cy + radius * math.sin(math.radians(a0 + (a1 - a0) * i / steps))) for i in range(steps + 1)]


def circle(img, p, r, colour, scale, width=None):
    cv2.circle(img, (int(round(p[0] * scale * 16)), int(round(p[1] * scale * 16))), int(round(r * scale * 16)),
               colour, -1 if width is None else max(int(round(width * scale)), 1), cv2.LINE_AA, shift=4)


def render(art, frame, size, overlay=None, labels=False):
    """overlay: (pose, air, voiced) or None."""
    scale = size / art.view["width"]
    canvas = np.full((int(art.view["height"] * scale), size, 3), 255, np.float32)
    for layer in art.layers:
        if layer["type"] != "image":
            continue
        bitmap = art.bitmap(layer)
        if layer.get("cols"):
            draw_mesh(canvas, bitmap, layer["cols"], layer["rows"], frame[layer["id"]], scale)
        else:
            draw_image(canvas, bitmap, layer["rect"], scale)
    img = np.clip(canvas, 0, 255).astype(np.uint8)
    pal = art.data["palette"]
    if overlay:
        name, air, voiced = overlay
        pose = art.data["poses"][name]
        anchors = art.data["anchors"]
        if air in ("oral", "puff") and pose.get("airPath"):
            arrow(img, pose["airPath"], rgb(pal["air"]), scale)
        if air == "nasal":
            arrow(img, anchors["nasalPath"], rgb(pal["air"]), scale)
        if air == "puff":
            for r in (30, 54, 78):
                line(img, arc(anchors["mouthOut"][0], anchors["mouthOut"][1], r, -40, 40), rgb(pal["air"]), 10, scale)
        if voiced:
            x, y = anchors["larynx"]
            circle(img, (x, y), 8, rgb(pal["voice"]), scale)
            for r in (22, 38):
                line(img, arc(x, y, r, -40, 40, 8), rgb(pal["voice"]), 7, scale)
                line(img, arc(x, y, r, 140, 220, 8), rgb(pal["voice"]), 7, scale)
        for p in pose.get("gap", []):
            circle(img, p, 17, rgb(pal["contact"]), scale, 7)
        for p in pose.get("contact", []):
            circle(img, p, 17, rgb(pal["contact"]), scale)
            circle(img, p, 17, (255, 255, 255), scale, 5)
    if labels:
        ink = rgb(pal["label"])
        for white in (True, False):
            for label in art.view.get("labels", []):
                colour = (255, 255, 255) if white else ink
                line(img, [label.get("from", label["at"]), label["to"]], colour, 8 if white else 3.5, scale)
                circle(img, label["to"], 9.5 if white else 7, colour, scale)
        for label in art.view.get("labels", []):
            fs = 0.8 * scale
            (tw, th), _ = cv2.getTextSize(label["text"], cv2.FONT_HERSHEY_SIMPLEX, fs, 2)
            x = label["at"][0] * scale
            x -= {"middle": tw / 2, "end": tw}.get(label.get("align", "middle"), 0)
            org = (int(x), int(label["at"][1] * scale + th / 2))
            cv2.putText(img, label["text"], org, cv2.FONT_HERSHEY_SIMPLEX, fs, (255, 255, 255), 6, cv2.LINE_AA)
            cv2.putText(img, label["text"], org, cv2.FONT_HERSHEY_SIMPLEX, fs, ink, 2, cv2.LINE_AA)
    return img


def caption(img, text):
    out = cv2.copyMakeBorder(img, 34, 0, 0, 0, cv2.BORDER_CONSTANT, value=(255, 255, 255))
    cv2.putText(out, text, (8, 24), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (30, 30, 30), 2, cv2.LINE_AA)
    return out


def tile(images, columns):
    rows = []
    for i in range(0, len(images), columns):
        row = images[i:i + columns]
        while len(row) < columns:
            row.append(np.full_like(images[0], 255))
        rows.append(np.hstack(row))
    return np.vstack(rows)


def main():
    args = sys.argv[1:]
    only, chosen = None, None
    for flag in ("--only", "--poses"):
        if flag in args:
            i = args.index(flag)
            value = args[i + 1].split(",")
            del args[i:i + 2]
            if flag == "--only":
                only = value
            else:
                chosen = value
    assets, out = args[0], args[1]
    os.makedirs(out, exist_ok=True)
    art = Art(assets)
    want = lambda kind: only is None or kind in only
    poses = [(n, c) for n, c in POSES if n in art.data["poses"] and (chosen is None or n in chosen)]

    def shot(name, size):
        air, voiced = TYPICAL.get(name, ("none", False))
        return render(art, art.frame(name), size, (name, air, voiced))

    if want("sheets"):
        tiles = [caption(shot(n, 520), f"{n}  ({c})") for n, c in poses]
        for k in range(0, len(tiles), 6):
            cv2.imwrite(os.path.join(out, f"sheet-{k // 6 + 1}.png"), tile(tiles[k:k + 6], 3))
    if want("poses"):
        for n, _ in poses:
            cv2.imwrite(os.path.join(out, f"pose-{n}.png"), shot(n, 900))
    if only is not None and "plain" in only:
        for n, _ in poses:
            cv2.imwrite(os.path.join(out, f"plain-{n}.png"), render(art, art.frame(n), 900))
    if want("phone"):
        tiles = [caption(shot(n, 330), n) for n, _ in poses]
        cv2.imwrite(os.path.join(out, "phone.png"), tile(tiles, 5))
    if want("labels"):
        cv2.imwrite(os.path.join(out, "labels.png"), render(art, art.frame("rest"), 900, labels=True))
    if want("blends"):
        strips = []
        for pair in BLENDS:
            a, b = pair.split(":")
            if a not in art.data["poses"] or b not in art.data["poses"]:
                continue
            frames = [caption(render(art, art.blend(a, b, t), 300), f"{a} > {b}  {t:.2f}") for t in (0, 0.25, 0.5, 0.75, 1)]
            strips.append(np.hstack(frames))
        for k in range(0, len(strips), 5):
            cv2.imwrite(os.path.join(out, f"blend-{k // 5 + 1}.png"), np.vstack(strips[k:k + 5]))
    print("wrote previews to", out)


if __name__ == "__main__":
    main()
