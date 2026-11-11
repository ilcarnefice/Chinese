#!/usr/bin/env python3
"""A deliberately plain example generator for the mouth-diagram format (see SPEC.md).

It shows how to build every pose from a few numbers and write a valid file. The
drawing itself is crude; real designs replace all of it.

Usage: python3 tools/mouth/starter_build_mouth.py <out.json>
"""
import json
import sys

PALETTE = {
    "background": "#EEF1F8", "skin": "#FFD9C4", "outline": "#8A5A4A", "cavity": "#B5475A",
    "tongue": "#FF7B8A", "tongueEdge": "#D9546A", "lip": "#E5576B", "tooth": "#FFFFFF", "toothEdge": "#C9CEDB",
    "contact": "#FFB703", "air": "#339AF0", "voice": "#845EF7", "label": "#1F2333",
}

# name: tongue tip (x, y), blade y, middle y, back y, jaw drop, lip gap, lip rounding (0 spread, 1 neutral, 2 round),
#       velum down (0/1), and the contact or gap point if any.
POSES = {
    "rest": dict(tip=(300, 470), blade=450, mid=440, back=470, jaw=20, lips=8, round=1),
    "lips": dict(tip=(300, 480), blade=460, mid=450, back=475, jaw=14, lips=0, round=1, contact=(170, 432)),
    "lipsNasal": dict(tip=(300, 480), blade=460, mid=450, back=475, jaw=14, lips=0, round=1, velum=1, contact=(170, 432)),
    "lipTeeth": dict(tip=(300, 480), blade=460, mid=450, back=475, jaw=16, lips=4, round=1, tuck=1, gap=(215, 428)),
    "ridge": dict(tip=(290, 345), blade=385, mid=430, back=475, jaw=18, lips=10, round=1, contact=(285, 338)),
    "ridgeNasal": dict(tip=(290, 345), blade=385, mid=430, back=475, jaw=18, lips=10, round=1, velum=1, contact=(285, 338)),
    "ridgeLateral": dict(tip=(290, 345), blade=400, mid=460, back=490, jaw=24, lips=12, round=1, contact=(285, 338)),
    "velar": dict(tip=(300, 500), blade=470, mid=380, back=305, jaw=30, lips=12, round=1, contact=(640, 300)),
    "velarFric": dict(tip=(300, 500), blade=470, mid=390, back=330, jaw=32, lips=12, round=1, gap=(640, 305)),
    "velarNasal": dict(tip=(300, 500), blade=470, mid=380, back=315, jaw=28, lips=12, round=1, velum=1, contact=(640, 318)),
    "palatal": dict(tip=(262, 475), blade=300, mid=305, back=430, jaw=14, lips=8, round=0, contact=(400, 285)),
    "palatalFric": dict(tip=(262, 475), blade=318, mid=322, back=435, jaw=16, lips=8, round=0, gap=(400, 290)),
    "retroflex": dict(tip=(385, 285), blade=350, mid=450, back=440, jaw=28, lips=12, round=1.3, contact=(380, 275)),
    "retroflexFric": dict(tip=(385, 305), blade=365, mid=455, back=445, jaw=30, lips=12, round=1.3, gap=(380, 285)),
    "dental": dict(tip=(250, 392), blade=405, mid=440, back=475, jaw=10, lips=8, round=0.4, contact=(245, 388)),
    "dentalFric": dict(tip=(254, 402), blade=412, mid=442, back=475, jaw=12, lips=8, round=0.4, gap=(246, 394)),
    "a": dict(tip=(300, 560), blade=550, mid=545, back=530, jaw=95, lips=40, round=1),
    "o": dict(tip=(330, 520), blade=500, mid=450, back=400, jaw=55, lips=22, round=2),
    "e": dict(tip=(330, 510), blade=490, mid=440, back=385, jaw=50, lips=20, round=1),
    "eFront": dict(tip=(270, 500), blade=400, mid=400, back=460, jaw=48, lips=20, round=0),
    "schwa": dict(tip=(305, 490), blade=460, mid=440, back=455, jaw=40, lips=16, round=1),
    "i": dict(tip=(262, 475), blade=325, mid=330, back=440, jaw=14, lips=10, round=0),
    "u": dict(tip=(340, 510), blade=490, mid=420, back=335, jaw=22, lips=8, round=2),
    "yu": dict(tip=(262, 475), blade=325, mid=330, back=440, jaw=14, lips=8, round=2),
    "er": dict(tip=(395, 330), blade=385, mid=450, back=440, jaw=45, lips=18, round=1),
}


def tongue(p):
    d = p["jaw"] * 0.4
    tx, ty = p["tip"]
    return [[tx, ty + d], [(tx + 400) / 2, (ty + p["blade"]) / 2 - 12 + d], [400, p["blade"] + d],
            [500, p["mid"] + d], [600, p["back"] - 12 + d], [690, p["back"] + 10 + d], [735, 560 + d],
            [720, 690], [600, 700], [470, 640 + p["jaw"]], [380, 590 + p["jaw"]], [tx + 30, ty + 60 + d]]


def lips(p):
    push = max(0.0, p["round"] - 1) * 22
    tuck = p.get("tuck", 0)
    upper = [[150 - push, 395], [185 - push, 380], [228, 392], [222, 420], [185 - push, 428], [152 - push, 420]]
    y = 428 + p["lips"] + p["jaw"] * 0.8 - tuck * 14
    x = -push + tuck * 34
    lower = [[152 + x, y + 6], [186 + x, y], [226 + x * 0.3, y + 10], [228, y + 36], [188 + x, y + 46], [150 + x, y + 34]]
    return upper, lower


def velum(p):
    if p.get("velum"):
        return [[640, 262], [700, 268], [748, 300], [760, 372], [742, 392], [722, 366], [690, 322], [640, 302]]
    return [[640, 262], [700, 262], [762, 280], [800, 330], [790, 352], [760, 330], [700, 304], [640, 300]]


def lower_teeth(p):
    y = 448 + p["jaw"]
    return [[236, y], [262, y], [262, y + 70], [236, y + 70]]


def front(p):
    """Front view: opening, lips and teeth from the same numbers."""
    r = p["round"]
    half_w = 150 - max(0.0, r - 1) * 95 + max(0.0, 1 - r) * 25
    opening = p["lips"] * 1.2 + p["jaw"] * 0.9
    cx, cy = 250, 200
    top, bottom = cy - opening / 2, cy + opening / 2
    hole = [[cx - half_w, cy], [cx - half_w * 0.5, top], [cx, top - 2], [cx + half_w * 0.5, top], [cx + half_w, cy],
            [cx + half_w * 0.5, bottom], [cx, bottom + 2], [cx - half_w * 0.5, bottom]]
    upper = [[cx - half_w - 22, cy], [cx - half_w * 0.5, top - 34], [cx, top - 26], [cx + half_w * 0.5, top - 34],
             [cx + half_w + 22, cy], [cx + half_w * 0.5, top], [cx, top - 2], [cx - half_w * 0.5, top]]
    lower = [[cx - half_w - 22, cy], [cx - half_w * 0.5, bottom], [cx, bottom + 2], [cx + half_w * 0.5, bottom],
             [cx + half_w + 22, cy], [cx + half_w * 0.5, bottom + 40], [cx, bottom + 48], [cx - half_w * 0.5, bottom + 40]]
    tw = half_w * 0.7
    teeth = [[cx - tw, top], [cx + tw, top], [cx + tw, top + min(opening * 0.45, 26)], [cx - tw, top + min(opening * 0.45, 26)]]
    return hole, upper, lower, teeth


def air_path(p):
    d = p["jaw"] * 0.4
    return [[790, 640], [770, 470], [640, p["back"] - 40 + d], [500, p["mid"] - 45 + d], [380, p["blade"] - 40 + d],
            [250, 432 + p["lips"] / 2], [110, 436]]


def pose_points(p):
    upper, lower = lips(p)
    hole, f_upper, f_lower, f_teeth = front(p)
    return {"tongue": tongue(p), "upperLip": upper, "lowerLip": lower, "velum": velum(p), "lowerTeeth": lower_teeth(p),
            "frontHole": hole, "frontUpperLip": f_upper, "frontLowerLip": f_lower, "frontTeeth": f_teeth}


def main():
    side_layers = [
        {"id": "bg", "type": "path", "d": "M0 0H1000V800H0Z", "fill": "background"},
        {"id": "head", "type": "path", "fill": "skin", "stroke": "outline", "strokeWidth": 6,
         "d": "M330 0 C230 90 215 190 200 250 C150 300 105 330 100 350 C120 372 150 372 185 372 L185 480 "
              "C165 560 190 640 240 690 C350 760 500 770 620 760 L620 800 L1000 800 L1000 0 Z"},
        {"id": "cavity", "type": "path", "fill": "cavity",
         "d": "M205 432 L250 392 C280 345 320 320 360 300 C480 240 560 250 640 262 C720 262 780 290 800 340 "
              "L830 340 L830 800 L700 800 C690 720 560 700 480 680 C340 640 270 590 240 520 L205 470 Z"},
        {"id": "velum", "type": "shape", "fill": "skin", "stroke": "outline", "strokeWidth": 5},
        {"id": "tongue", "type": "shape", "fill": "tongue", "stroke": "tongueEdge", "strokeWidth": 5},
        {"id": "upperTeeth", "type": "path", "fill": "tooth", "stroke": "toothEdge", "strokeWidth": 4,
         "d": "M232 372 L260 372 L256 440 L238 440 Z"},
        {"id": "lowerTeeth", "type": "shape", "smooth": False, "fill": "tooth", "stroke": "toothEdge", "strokeWidth": 4},
        {"id": "upperLip", "type": "shape", "fill": "lip"},
        {"id": "lowerLip", "type": "shape", "fill": "lip"},
    ]
    front_layers = [
        {"id": "frontBg", "type": "path", "d": "M0 0H500V400H0Z", "fill": "skin"},
        {"id": "frontHole", "type": "shape", "fill": "cavity"},
        {"id": "frontTeeth", "type": "shape", "smooth": False, "fill": "tooth"},
        {"id": "frontUpperLip", "type": "shape", "fill": "lip"},
        {"id": "frontLowerLip", "type": "shape", "fill": "lip"},
    ]
    poses = {}
    for name, p in POSES.items():
        pose = {"points": pose_points(p)}
        if "contact" in p:
            pose["contact"] = [list(p["contact"])]
        if "contact" not in p or name == "ridgeLateral":
            pose["airPath"] = air_path(p)
        if "gap" in p:
            pose["gap"] = [list(p["gap"])]
        poses[name] = pose
    art = {
        "version": 1,
        "palette": PALETTE,
        "views": {
            "side": {"width": 1000, "height": 800, "layers": side_layers, "labels": [
                {"text": "upper lip", "at": [90, 300], "to": [180, 400], "align": "middle"},
                {"text": "ridge", "at": [330, 200], "to": [300, 345], "align": "middle"},
                {"text": "hard palate", "at": [520, 180], "to": [480, 262], "align": "middle"},
                {"text": "soft palate", "at": [780, 200], "to": [720, 280], "align": "middle"},
                {"text": "tongue tip", "at": [120, 560], "to": [300, 478], "align": "middle"},
                {"text": "tongue back", "at": [600, 560], "to": [620, 480], "align": "middle"},
                {"text": "upper teeth", "at": [110, 230], "to": [246, 400], "align": "middle"},
                {"text": "voice box", "at": [900, 720], "to": [780, 740], "align": "middle"},
            ]},
            "front": {"width": 500, "height": 400, "layers": front_layers},
        },
        "anchors": {"larynx": [770, 740], "mouthOut": [120, 436],
                    "nasalPath": [[790, 560], [800, 380], [740, 240], [520, 190], [300, 250], [150, 345], [70, 380]]},
        "poses": poses,
    }
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(art, f, ensure_ascii=False, separators=(",", ":"))
    print(f"wrote {sys.argv[1]}")


if __name__ == "__main__":
    main()
