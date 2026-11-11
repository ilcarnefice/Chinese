#!/usr/bin/env python3
"""Checks a mouth-diagram data file against tools/mouth/SPEC.md.

Usage: python3 tools/mouth/check_mouth.py <assets dir or mouth.json>
Prints every problem found and exits with status 1 if there are any.
"""
import json
import os
import re
import sys

POSES = ("rest lips lipsNasal lipTeeth ridge ridgeNasal ridgeLateral velar velarFric velarNasal palatal palatalFric "
         "retroflex retroflexFric dental dentalFric a o e eFront schwa i u yu er").split()
CLOSURES = "lips lipsNasal ridge ridgeNasal ridgeLateral velar velarNasal palatal retroflex dental".split()
NARROWINGS = "lipTeeth velarFric palatalFric retroflexFric dentalFric".split()
NEEDS_AIR = "rest lipTeeth ridgeLateral velarFric palatalFric retroflexFric dentalFric a o e eFront schwa i u yu er".split()
NASAL = {"rest", "lipsNasal", "ridgeNasal", "velarNasal"}
HEX = re.compile(r"#[0-9A-Fa-f]{6}$")


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return int.from_bytes(head[16:20], "big"), int.from_bytes(head[20:24], "big")


def check(path):
    if os.path.isdir(path):
        path = os.path.join(path, "mouth.json")
    assets = os.path.dirname(path)
    art = json.load(open(path, encoding="utf-8"))
    problems = []
    palette = art.get("palette", {})
    for name in ("contact", "air", "voice", "label"):
        if name not in palette:
            problems.append(f"palette lacks {name}")
    for name, value in palette.items():
        if not HEX.match(value):
            problems.append(f"palette.{name} is not #RRGGBB")
    if not art.get("source"):
        problems.append("no source: the picture's credit is missing")
    if art.get("meshUnit", 1) <= 0:
        problems.append("meshUnit must be positive")

    side = art.get("views", {}).get("side")
    if side is None:
        return problems + ["no side view"]
    grids = {}
    seen = set()
    for layer in side["layers"]:
        lid = layer.get("id")
        if lid in seen:
            problems.append(f"layer id {lid} is used twice")
        seen.add(lid)
        if layer.get("type") != "image":
            continue
        src = layer.get("src")
        file = os.path.join(assets, src or "")
        size = png_size(file) if src and os.path.isfile(file) else None
        if size is None:
            problems.append(f"layer {lid}: picture {src} is missing or not a PNG")
        rect = layer.get("rect")
        if not rect or len(rect) != 4:
            problems.append(f"layer {lid}: needs rect [x, y, width, height]")
        crop = layer.get("crop")
        if crop and size and (len(crop) != 4 or crop[0] + crop[2] > size[0] or crop[1] + crop[3] > size[1]):
            problems.append(f"layer {lid}: crop does not fit inside {src}")
        cols, rows = layer.get("cols", 0), layer.get("rows", 0)
        if (cols > 0) != (rows > 0):
            problems.append(f"layer {lid}: a grid needs both cols and rows")
        if cols > 0 and rows > 0:
            grids[lid] = (cols + 1) * (rows + 1) * 2
    if len(side.get("labels", [])) < 8:
        problems.append("the side view should name at least 8 parts")

    poses = art.get("poses", {})
    for name in POSES:
        if name not in poses:
            problems.append(f"pose {name} is missing")
    if poses.get("rest", {}).get("mesh"):
        problems.append("rest must be the picture untouched (no grid movement)")
    width, height = side["width"], side["height"]
    for name, pose in poses.items():
        for lid, moved in pose.get("mesh", {}).items():
            if lid not in grids:
                problems.append(f"pose {name} moves unknown picture {lid}")
            elif len(moved) != grids[lid]:
                problems.append(f"pose {name}.{lid} has {len(moved)} numbers, its grid needs {grids[lid]}")
            elif any(not isinstance(v, int) for v in moved):
                problems.append(f"pose {name}.{lid} must hold whole numbers")
        for key in ("contact", "gap", "airPath"):
            for p in pose.get(key) or []:
                if len(p) != 2 or not (-60 <= p[0] <= width + 60 and -60 <= p[1] <= height + 60):
                    problems.append(f"pose {name}.{key} has a point outside the view: {p}")
        if name in CLOSURES and not pose.get("contact"):
            problems.append(f"pose {name} is a closure and needs a contact marker")
        if name in NARROWINGS and not pose.get("gap"):
            problems.append(f"pose {name} is a narrowing and needs a gap marker")
        if name in NEEDS_AIR and len(pose.get("airPath") or []) < 2:
            problems.append(f"pose {name} needs an airPath")
        lifted = "softPalate" in pose.get("mesh", {})
        if "softPalate" in grids and name in POSES and lifted == (name in NASAL):
            problems.append(f"pose {name}: the soft palate must be {'hanging' if name in NASAL else 'lifted'}")
    anchors = art.get("anchors", {})
    for key in ("larynx", "mouthOut"):
        if len(anchors.get(key, [])) != 2:
            problems.append(f"anchors.{key} must be [x, y]")
    if len(anchors.get("nasalPath", [])) < 2:
        problems.append("anchors.nasalPath is too short")
    return problems


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    found = check(sys.argv[1])
    for line in found:
        print(line)
    print(f"{len(found)} problems")
    sys.exit(1 if found else 0)
