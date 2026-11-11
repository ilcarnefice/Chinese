#!/usr/bin/env python3
"""Traces the tissue outlines of a midsagittal head diagram into base_shapes.json.

Source image: "Midsagittal diagram unlabeled.svg" by Wugapodes, Wikimedia Commons,
released into the public domain (CC0):
https://commons.wikimedia.org/wiki/File:Midsagittal_diagram_unlabeled.svg
Render it to PNG at 3840 pixels wide and pass the file to this script.

The four tissue regions (head, lower jaw with tongue, palate with upper lip, and
the back wall of the voice box) are cropped to the mouth area and scaled to the
1000 x 800 side view used by the mouth diagrams.

Usage: python3 tools/mouth/trace_base.py <diagram.png> tools/mouth/base_shapes.json
Requires: pip install opencv-python-headless numpy
"""
import json
import sys

import cv2
import numpy as np

# Window of the 3840-pixel-wide render that becomes the 1000 x 800 view.
X0, Y0, WIDTH = 40, 2150, 3125


def main():
    src, out = sys.argv[1], sys.argv[2]
    im = cv2.imread(src, cv2.IMREAD_UNCHANGED)
    alpha = im[:, :, 3].astype(np.float32) / 255.0
    flat = (im[:, :, 0].astype(np.float32) * alpha + 255 * (1 - alpha)).astype(np.uint8)
    mask = (flat < 235).astype(np.uint8) * 255
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    big = sorted([c for c in contours if cv2.contourArea(c) > 3000], key=lambda c: -cv2.contourArea(c))
    scale = np.float32(1000.0 / WIDTH)
    shapes = {}
    for name, c in zip(["head", "jaw", "palate", "epiglottis"], big):
        pts = ((c[:, 0, :].astype(np.float32) - np.array([X0, Y0], dtype=np.float32)) * scale).astype(np.float32)
        approx = cv2.approxPolyDP(pts.reshape(-1, 1, 2), 0.45, True)[:, 0, :]
        shapes[name] = [[round(float(x), 1), round(float(y), 1)] for x, y in approx]
        print(name, len(approx), "points")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(shapes, f, separators=(",", ":"))


if __name__ == "__main__":
    main()
