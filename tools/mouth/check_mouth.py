#!/usr/bin/env python3
"""Checks the structure of a mouth-diagram data file (see SPEC.md).

Usage: python3 tools/mouth/check_mouth.py <mouth.json>
"""
import json
import os
import re
import sys

POSES = ["rest", "lips", "lipsNasal", "lipTeeth", "ridge", "ridgeNasal", "ridgeLateral", "velar", "velarFric",
         "velarNasal", "palatal", "palatalFric", "retroflex", "retroflexFric", "dental", "dentalFric",
         "a", "o", "e", "eFront", "schwa", "i", "u", "yu", "er"]
CLOSURES = ["lips", "lipsNasal", "ridge", "ridgeNasal", "velar", "velarNasal", "palatal", "retroflex", "dental"]
NARROWED = ["lipTeeth", "velarFric", "palatalFric", "retroflexFric", "dentalFric"]
NEEDS_AIR = NARROWED + ["rest", "ridgeLateral", "a", "o", "e", "eFront", "schwa", "i", "u", "yu", "er"]
SIZES = {"side": (1000, 800), "front": (500, 400)}
HEX = re.compile(r"^#[0-9A-Fa-f]{6}$")
PATH = re.compile(r"^[MLHVCSQTAZmlhvcsqtaz0-9eE\s,.\-+]+$")
MAX_BYTES = 400_000


def main():
    path = sys.argv[1]
    art = json.load(open(path, encoding="utf-8"))
    errors, warnings = [], []
    err = errors.append

    palette = art.get("palette", {})
    for name, value in palette.items():
        if not HEX.match(str(value)):
            err(f"palette.{name}: '{value}' is not #RRGGBB")
    for name in ("contact", "air", "voice", "label"):
        if name not in palette:
            err(f"palette is missing the required colour '{name}'")

    def colour_ok(where, value):
        if value is not None and value not in palette and not HEX.match(str(value)):
            err(f"{where}: unknown colour '{value}'")

    def point_ok(where, p, w, h):
        if not (isinstance(p, list) and len(p) == 2 and all(isinstance(v, (int, float)) for v in p)):
            err(f"{where}: {p!r} is not [x, y]")
        elif not (-250 <= p[0] <= w + 250 and -250 <= p[1] <= h + 250):
            err(f"{where}: {p} is far outside the {w} x {h} view")

    shapes = {}   # layer id -> (view, closed)
    ids = set()
    views = art.get("views", {})
    for vname, (w, h) in SIZES.items():
        view = views.get(vname)
        if not view:
            err(f"views.{vname} is missing")
            continue
        if (view.get("width"), view.get("height")) != (w, h):
            err(f"views.{vname} must be {w} x {h}")
        if not view.get("layers"):
            err(f"views.{vname} has no layers")
        for layer in view.get("layers", []):
            lid = layer.get("id")
            where = f"layer {vname}.{lid}"
            if not lid or lid in ids:
                err(f"{where}: missing or duplicate id")
            ids.add(lid)
            colour_ok(where + ".fill", layer.get("fill"))
            colour_ok(where + ".stroke", layer.get("stroke"))
            if not layer.get("fill") and not layer.get("stroke"):
                err(f"{where}: has neither fill nor stroke")
            if layer.get("stroke") and not layer.get("strokeWidth"):
                err(f"{where}: stroke without strokeWidth")
            if not 0 <= layer.get("opacity", 1) <= 1:
                err(f"{where}: opacity must be 0..1")
            if "dash" in layer and not (isinstance(layer["dash"], list) and len(layer["dash"]) == 2):
                err(f"{where}: dash must be [on, off]")
            if layer.get("type") == "path":
                if not isinstance(layer.get("d"), str) or not PATH.match(layer["d"]):
                    err(f"{where}: 'd' must be SVG path data using only M L H V C S Q T A Z and numbers")
            elif layer.get("type") == "shape":
                shapes[lid] = (vname, layer.get("closed", True))
            else:
                err(f"{where}: type must be 'path' or 'shape'")
        for i, label in enumerate(view.get("labels", [])):
            if not label.get("text"):
                err(f"views.{vname}.labels[{i}]: missing text")
            for key in ("at", "to"):
                point_ok(f"views.{vname}.labels[{i}].{key}", label.get(key), w, h)
            if label.get("align", "middle") not in ("start", "middle", "end"):
                err(f"views.{vname}.labels[{i}]: align must be start, middle or end")
    if "side" in views and len(views["side"].get("labels", [])) < 8:
        warnings.append("side view has fewer than 8 part labels")

    anchors = art.get("anchors", {})
    for key in ("larynx", "mouthOut"):
        point_ok(f"anchors.{key}", anchors.get(key), 1000, 800)
    nasal = anchors.get("nasalPath") or []
    if len(nasal) < 3:
        err("anchors.nasalPath needs at least 3 points")
    for i, p in enumerate(nasal):
        point_ok(f"anchors.nasalPath[{i}]", p, 1000, 800)

    poses = art.get("poses", {})
    for name in POSES:
        if name not in poses:
            err(f"pose '{name}' is missing")
    for name in poses:
        if name not in POSES:
            err(f"unknown pose '{name}'")
    rest = poses.get("rest", {}).get("points", {})
    for lid, (vname, closed) in shapes.items():
        pts = rest.get(lid)
        if not pts:
            err(f"rest pose has no points for shape layer '{lid}'")
        elif len(pts) < (3 if closed else 2):
            err(f"rest.{lid}: too few points")
        elif len(pts) > 40:
            warnings.append(f"rest.{lid} has {len(pts)} points; 40 or fewer keeps the file small")
    for name, pose in poses.items():
        for lid, pts in pose.get("points", {}).items():
            if lid not in shapes:
                err(f"pose {name}: '{lid}' is not a shape layer")
                continue
            w, h = SIZES[shapes[lid][0]]
            if lid in rest and len(pts) != len(rest[lid]):
                err(f"pose {name}.{lid}: {len(pts)} points, but rest has {len(rest[lid])}")
            for i, p in enumerate(pts):
                point_ok(f"pose {name}.{lid}[{i}]", p, w, h)
        for key in ("contact", "gap", "airPath"):
            for i, p in enumerate(pose.get(key) or []):
                point_ok(f"pose {name}.{key}[{i}]", p, 1000, 800)
        if pose.get("airPath") and len(pose["airPath"]) < 2:
            err(f"pose {name}.airPath needs at least 2 points")
    for name in CLOSURES:
        if name in poses and not poses[name].get("contact"):
            err(f"pose {name} is a closure and needs a 'contact' point")
    for name in NARROWED:
        if name in poses and not poses[name].get("gap"):
            err(f"pose {name} is a narrowing and needs a 'gap' point")
    for name in NEEDS_AIR:
        if name in poses and not poses[name].get("airPath"):
            err(f"pose {name} needs an 'airPath'")
    for name in POSES[1:]:
        if name in poses and not poses[name].get("points"):
            err(f"pose {name} moves nothing: it has no points of its own")

    size = os.path.getsize(path)
    if size > MAX_BYTES:
        err(f"file is {size} bytes; keep it under {MAX_BYTES}")
    for w in warnings:
        print("warning: " + w)
    for e in errors[:80]:
        print("error: " + e)
    print(f"{path}: {len(ids)} layers ({len(shapes)} moving), {len(poses)} poses, {size} bytes, "
          f"{len(errors)} errors, {len(warnings)} warnings")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
