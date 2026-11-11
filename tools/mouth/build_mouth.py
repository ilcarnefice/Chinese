#!/usr/bin/env python3
"""Builds app/src/main/assets/mouth.json: the mouth diagrams for every Mandarin sound.

The fixed anatomy (head, palate, teeth, throat) is traced from a public-domain
midsagittal diagram (see trace_base.py and base_shapes.json). On top of it this
script models the parts that move: the lower jaw swings on a hinge, the lips and
the soft palate bend, and the tongue is a curve through a dozen control points.
Each pose below is a jaw angle, lip and soft-palate settings and tongue points.

Usage: python3 tools/mouth/build_mouth.py app/src/main/assets/mouth.json
Check:  python3 tools/mouth/check_mouth.py <file>; node tools/mouth/preview.cjs <file> <dir>
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = json.load(open(os.path.join(HERE, "base_shapes.json"), encoding="utf-8"))
JAW, PAL, HEAD, EPI = BASE["jaw"], BASE["palate"], BASE["head"], BASE["epiglottis"]

PALETTE = {
    "air": "#2F8FEB", "contact": "#FFB703", "voice": "#7C4DFF", "label": "#1F2333",
    "background": "#EFF4FB", "skin": "#FBD5C2", "skinShade": "#F3BFA8", "outline": "#A8705C",
    "tongue": "#F47B8E", "tongueEdge": "#C94F66", "lip": "#E8667A", "velum": "#EFA3B3",
    "tooth": "#FFFFFF", "toothEdge": "#AEB6C6", "bone": "#FFF1E6", "mouthDark": "#7A2A3A",
}

# ---------------------------------------------------------------- geometry helpers

HINGE = (700.0, 300.0)      # the jaw swings about this point
TRACED_JAW = 5.3            # degrees the jaw is open in the traced diagram


def lerp(a, b, t):
    return a + (b - a) * t


def rotate(p, degrees, centre):
    a = math.radians(degrees)
    dx, dy = p[0] - centre[0], p[1] - centre[1]
    return (centre[0] + dx * math.cos(a) - dy * math.sin(a), centre[1] + dx * math.sin(a) + dy * math.cos(a))


def jaw_move(p, jaw, weight=1.0):
    """Where a point of the traced lower jaw is when the jaw is open by `jaw` degrees."""
    return rotate(p, (TRACED_JAW - jaw) * weight, HINGE)


def spline(points, per_segment=14):
    """Dense points along an open Catmull-Rom curve through `points`."""
    n = len(points)
    out = []
    for i in range(n - 1):
        p0, p1, p2, p3 = points[max(i - 1, 0)], points[i], points[i + 1], points[min(i + 2, n - 1)]
        for k in range(per_segment):
            t = k / per_segment
            t2, t3 = t * t, t * t * t
            out.append(tuple(
                0.5 * ((2 * p1[d]) + (-p0[d] + p2[d]) * t + (2 * p0[d] - 5 * p1[d] + 4 * p2[d] - p3[d]) * t2
                       + (-p0[d] + 3 * p1[d] - 3 * p2[d] + p3[d]) * t3) for d in (0, 1)))
    out.append(tuple(points[-1]))
    return out


def resample(points, count):
    """`count` points evenly spaced along a polyline, end points included."""
    lengths = [0.0]
    for a, b in zip(points, points[1:]):
        lengths.append(lengths[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    total = lengths[-1]
    out, j = [], 0
    for i in range(count):
        target = total * i / (count - 1)
        while j < len(points) - 2 and lengths[j + 1] < target:
            j += 1
        span = lengths[j + 1] - lengths[j] or 1.0
        t = (target - lengths[j]) / span
        out.append((lerp(points[j][0], points[j + 1][0], t), lerp(points[j][1], points[j + 1][1], t)))
    return out


def r1(points):
    return [[round(x, 1), round(y, 1)] for x, y in points]


def path_d(points, closed=True):
    """SVG path through points as a smooth closed curve (same spline the renderers use)."""
    n = len(points)
    at = lambda i: points[i % n]
    d = "M%.1f %.1f" % tuple(points[0])
    for i in range(n if closed else n - 1):
        p0, p1, p2, p3 = at(i - 1), at(i), at(i + 1), at(i + 2)
        d += " C%.1f %.1f %.1f %.1f %.1f %.1f" % (
            p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6,
            p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6, p2[0], p2[1])
    return d + (" Z" if closed else "")


def weights(table, count):
    """Per-index weights from a sparse {index: weight} table, linear in between."""
    keys = sorted(table)
    out = [0.0] * count
    for a, b in zip(keys, keys[1:]):
        for i in range(a, b + 1):
            out[i] = lerp(table[a], table[b], (i - a) / (b - a))
    return out


# ---------------------------------------------------------------- the moving parts

# Lower jaw outline: the traced contour without the tongue (points 69..115 are the
# tongue's surface). It runs: floor of the mouth, lower tooth, lip, chin, neck, voice
# box wall, epiglottis, and closes under the tongue.
JAW_ORDER = list(range(116, 147)) + list(range(0, 69))
JAW_WEIGHT = [0.0] * 147
for i in list(range(116, 147)) + list(range(0, 31)):
    JAW_WEIGHT[i] = 1.0
for i in range(31, 45):
    JAW_WEIGHT[i] = max(0.0, 1.0 - (i - 30) / 12.0)       # the neck stretches, its base stays
for i in range(48, 61):
    JAW_WEIGHT[i] = 0.15                                   # the voice box hardly moves
for i in range(61, 69):
    JAW_WEIGHT[i] = lerp(0.15, 0.5, (i - 60) / 8.0)        # epiglottis and the tongue's root
LOWER_LIP = weights({132: 0, 136: 0.6, 140: 0.95, 141: 1.0, 146: 1.0}, 147)
for i, w in enumerate(weights({0: 1.0, 2: 0.95, 4: 0.8, 8: 0.2, 10: 0.0}, 147)[:11]):
    LOWER_LIP[i] = w
UPPER_LIP = weights({0: 0.1, 3: 0.3, 6: 0.55, 9: 0.8, 12: 0.95, 15: 1.0, 21: 1.0, 24: 0.85, 27: 0.4, 29: 0.0, 90: 0.0}, 91)
VELUM = weights({0: 0, 57: 0, 61: 1.0, 74: 1.0, 78: 0, 90: 0}, 91)
VELUM_PIVOT = (522.0, 186.0)
VELUM_UP, VELUM_OPEN = -12.0, 9.0     # degrees: raised against the throat wall / hanging open

# Tongue control points in the traced jaw's frame: floor attachment, underside,
# tip (under and top), blade, front, middle, back (two), root (two), vallecula.
TONGUE_REST = {
    "floor": (291, 458), "under": (284, 428), "tipUnder": (246, 380), "tip": (241, 351), "blade": (293, 312),
    "front": (373, 289), "mid": (442, 287), "back": (514, 304), "back2": (554, 342), "root": (574, 395),
    "root2": (582, 480), "vallecula": (563, 543),
}
TONGUE_ORDER = list(TONGUE_REST)
TONGUE_WEIGHT = {"back2": 0.95, "root": 0.85, "root2": 0.7, "vallecula": 0.5}
TONGUE_INSIDE = [(528, 574), (410, 560), (322, 510)]      # the tongue's lower edge inside the jaw


JAW_HIDDEN = [(545, 566), (470, 556), (400, 540), (340, 505), (305, 472)]   # closes the outline under the tongue


def jaw_body(pose):
    jaw, (ldx, ldy) = pose["jaw"], pose["lowerLip"]
    out = []
    for i in JAW_ORDER:
        x, y = jaw_move(JAW[i], jaw, JAW_WEIGHT[i])
        out.append((x + ldx * LOWER_LIP[i], y + ldy * LOWER_LIP[i]))
    return out + [jaw_move(p, jaw, 0.8) for p in JAW_HIDDEN]


def band(points, depth):
    """A band along an outline: the points themselves and a copy pulled towards their centre."""
    cx = sum(p[0] for p in points) / len(points)
    cy = sum(p[1] for p in points) / len(points)
    inner = [(lerp(x, cx, depth), lerp(y, cy, depth)) for x, y in points]
    return points + inner[::-1]


def lower_tooth(pose):
    """The lower incisor's crown: the traced tooth above the gum line."""
    pts = [JAW[i] for i in range(121, 132)]
    crown = [p for p in pts if p[1] < 404]
    crown = [(244.0, 404.0)] + crown + [(215.5, 404.0)]
    return [jaw_move(p, pose["jaw"]) for p in crown]


def lower_lip_tint(body):
    index = {i: n for n, i in enumerate(JAW_ORDER)}
    return band([body[index[i]] for i in [139, 140, 141, 142, 143, 144, 145, 146, 0, 1, 2, 3, 4, 5, 6, 7]], 0.82)


def palate(pose):
    (udx, udy), velum = pose["upperLip"], pose["velum"]
    out = []
    for i, p in enumerate(PAL):
        x, y = rotate(p, velum * VELUM[i], VELUM_PIVOT)
        out.append((x + udx * UPPER_LIP[i], y + udy * UPPER_LIP[i]))
    return out


def velum_shape(pal):
    return [pal[i] for i in range(57, 79)]


def upper_lip_tint(pal):
    return band([pal[i] for i in range(10, 27)], 0.82)


def tongue_controls(pose):
    """Tongue control points in view coordinates for a pose."""
    spec = dict(TONGUE_REST)
    absolute = {}
    for name, value in pose.get("tongue", {}).items():
        if len(value) == 3:           # ("abs", x, y): a point fixed against the palate or teeth
            absolute[name] = (value[1], value[2])
        else:
            spec[name] = value
    out = []
    for name in TONGUE_ORDER:
        if name in absolute:
            out.append(absolute[name])
        else:
            out.append(jaw_move(spec[name], pose["jaw"], TONGUE_WEIGHT.get(name, 1.0)))
    return out


def tongue(pose):
    surface = resample(spline(tongue_controls(pose)), 24)
    inside = [jaw_move(p, pose["jaw"]) for p in TONGUE_INSIDE]
    return surface + inside


# Fixed upper anatomy used to place markers and breath routes.
UPPER_TOOTH = [PAL[i] for i in range(29, 43)]
ROOF = [PAL[i] for i in range(33, 58)]          # behind the upper tooth, ridge, hard palate


def roof_line(pal):
    """The roof of the mouth from the upper tooth to the uvula, for this pose's soft palate."""
    return ROOF + [pal[i] for i in range(58, 67)]


def nearest(a_points, b_points):
    """The closest pair between two point sets: (distance, point on a, point on b)."""
    best = None
    for a in a_points:
        for b in b_points:
            d = math.hypot(a[0] - b[0], a[1] - b[1])
            if best is None or d < best[0]:
                best = (d, a, b)
    return best


def dense(points, step=4.0):
    out = []
    for a, b in zip(points, points[1:]):
        n = max(1, int(math.hypot(b[0] - a[0], b[1] - a[1]) / step))
        out += [(lerp(a[0], b[0], k / n), lerp(a[1], b[1], k / n)) for k in range(n)]
    return out + [points[-1]]


def air_path(pose, body, pal, surface):
    """Route of the breath: up the throat, between tongue and roof, out between the lips."""
    roof = dense(roof_line(pal))
    top = dense(surface[3:])                       # the tongue's upper surface, tip to root
    route = [(592.0, 700.0), (598.0, 630.0), (604.0, 560.0)]
    for x in (585, 540, 490, 440, 390, 340, 295, 258):
        above = [p for p in roof if abs(p[0] - x) < 9]
        below = [p for p in top if abs(p[0] - x) < 9]
        if not above or not below:
            continue
        ya, yb = max(p[1] for p in above), min(p[1] for p in below)
        route.append((float(x), (ya + yb) / 2 if yb > ya else ya + 2))
    index = {i: n for n, i in enumerate(JAW_ORDER)}
    lower_tip = body[index[126]]
    upper_tip = PAL[31]
    route.append(((lower_tip[0] + upper_tip[0]) / 2 + 2, (lower_tip[1] + upper_tip[1]) / 2))
    lip_low, lip_up = body[index[144]], pal[21]
    y = (lip_low[1] + lip_up[1]) / 2
    route.append(((lip_low[0] + lip_up[0]) / 2 - 6, y))
    route.append((92.0, y + 2))
    return route


# ---------------------------------------------------------------- front view

FRONT_SCALE = 1.28


def front(pose):
    f = pose["front"]
    cx, cy = 250.0, 214.0
    w, h = f["w"] * FRONT_SCALE, f["h"] * FRONT_SCALE          # half width of the mouth, height of the opening
    tu, tl = f.get("upper_lip", 30.0) * FRONT_SCALE, f.get("lower_lip", 38.0) * FRONT_SCALE     # lip thickness
    rnd = f.get("round", 0.0)
    top, bottom = cy - h / 2, cy + h / 2
    k = 0.55 + 0.22 * rnd                       # rounder lips bulge more evenly
    lips = [
        (cx - w - 7, cy), (cx - w * 0.62, top - tu * 0.6), (cx - w * 0.24, top - tu), (cx, top - tu * (0.72 + 0.22 * rnd)),
        (cx + w * 0.24, top - tu), (cx + w * 0.62, top - tu * 0.6), (cx + w + 7, cy),
        (cx + w * k, bottom + tl * 0.78), (cx + w * 0.2, bottom + tl), (cx - w * 0.2, bottom + tl), (cx - w * k, bottom + tl * 0.78),
    ]
    hole = [
        (cx - w + 4, cy), (cx - w * 0.55, top + h * 0.08), (cx, top), (cx + w * 0.55, top + h * 0.08), (cx + w - 4, cy),
        (cx + w * 0.55, bottom - h * 0.08), (cx, bottom), (cx - w * 0.55, bottom - h * 0.08),
    ]
    centre = [(cx, cy)]
    # Teeth: a band under the upper lip and one above the lower lip, as far as they show.
    up = min(f.get("teethUp", 0.0) * FRONT_SCALE, h * 0.7)
    tw = w * 0.74
    if h < 8 or up <= 0:
        upper_teeth = centre * 10
    else:
        upper_teeth = [(cx - tw, top + h * 0.06), (cx - tw * 0.5, top + 1), (cx, top), (cx + tw * 0.5, top + 1), (cx + tw, top + h * 0.06),
                       (cx + tw * 0.96, top + up * 0.8), (cx + tw * 0.5, top + up), (cx, top + up), (cx - tw * 0.5, top + up), (cx - tw * 0.96, top + up * 0.8)]
        if f.get("bite"):                       # f: the upper teeth come down onto the lower lip
            drop = f["bite"] * FRONT_SCALE
            upper_teeth = [(x, y + (drop if n >= 5 else 0)) for n, (x, y) in enumerate(upper_teeth)]
    low = min(f.get("teethLow", 0.0) * FRONT_SCALE, h * 0.5)
    lw = w * 0.6
    if h < 8 or low <= 0:
        lower_teeth = centre * 10
    else:
        lower_teeth = [(cx - lw, bottom - low * 0.8), (cx - lw * 0.5, bottom - low), (cx, bottom - low), (cx + lw * 0.5, bottom - low), (cx + lw, bottom - low * 0.8),
                       (cx + lw * 0.9, bottom - h * 0.04), (cx + lw * 0.5, bottom - 1), (cx, bottom), (cx - lw * 0.5, bottom - 1), (cx - lw * 0.9, bottom - h * 0.04)]
    # Tongue: seen low in the opening; `tongue_show` is how much of the opening's height it fills.
    t = f.get("tongue_show", 0.0)
    if h < 8 or t <= 0:
        tongue_shape = centre * 8
    else:
        ty = bottom - h * t
        tw2 = w * 0.6
        tongue_shape = [(cx - tw2, bottom - h * 0.12), (cx - tw2 * 0.6, ty + h * 0.03), (cx, ty), (cx + tw2 * 0.6, ty + h * 0.03),
                        (cx + tw2, bottom - h * 0.12), (cx + tw2 * 0.5, bottom - 1), (cx, bottom), (cx - tw2 * 0.5, bottom - 1)]
    return {"frontLips": lips, "frontHole": hole, "frontTongue": tongue_shape, "frontUpperTeeth": upper_teeth, "frontLowerTeeth": lower_teeth}


FRONT_STATIC = [
    {"id": "frontBg", "type": "path", "d": "M0 0H500V400H0Z", "fill": "skin"},
    # nostrils, the groove under the nose, and the shadow under the lower lip
    {"id": "frontNose", "type": "path", "fill": "skinShade",
     "d": "M196 34 C206 20 228 22 232 38 C234 50 216 56 204 50 C196 46 192 40 196 34 Z "
          "M268 38 C272 22 294 20 304 34 C308 40 304 46 296 50 C284 56 266 50 268 38 Z"},
    {"id": "frontGroove", "type": "path", "stroke": "skinShade", "strokeWidth": 5, "d": "M238 66 C234 92 236 116 240 136 M262 66 C266 92 264 116 260 136"},
    {"id": "frontChin", "type": "path", "stroke": "skinShade", "strokeWidth": 6, "d": "M170 372 C206 392 294 392 330 372"},
]

# ---------------------------------------------------------------- the poses

UP, OPEN = VELUM_UP, VELUM_OPEN
UPPER_LIP_BOTTOM = 321.0


def P(jaw, kind, velum=UP, tongue=None, gap=None, lip=(0, 0), upper=(0, 0), mark=None, air=False, **front_view):
    """One pose. `gap` is the space wanted between the lips; `lip` shifts the lower lip sideways and
    `upper` moves the upper lip. `kind` is contact, gap or air; `mark` is where the contact or gap is."""
    natural = 9.07 * jaw - 17.1                 # lip gap the jaw angle alone would give
    dy = 0.0 if gap is None else gap - natural + upper[1]
    return {"jaw": jaw, "kind": kind, "velum": velum, "tongue": tongue or {}, "lowerLip": (lip[0], dy + lip[1]),
            "upperLip": upper, "mark": mark, "air": air or kind in ("air", "gap"), "front": front_view}


def A(x, y):
    return ("abs", x, y)


SPREAD = dict(upper=(3, -1), lip=(3, 0))
ROUND = dict(upper=(-15, 4), lip=(-17, 0))
F_SPREAD = dict(w=132, h=20, upper_lip=24, lower_lip=30, teethUp=11, teethLow=9)
F_ROUND = dict(w=42, h=18, upper_lip=36, lower_lip=42, round=1.0)

RIDGE_TONGUE = {"tip": A(243, 253), "tipUnder": A(244, 287), "blade": A(288, 242), "front": A(360, 240),
                "mid": A(440, 262), "back": A(512, 288)}
VELAR_TONGUE = {"tip": (247, 372), "tipUnder": (250, 392), "blade": (302, 354), "front": (380, 328), "mid": A(466, 262),
                "back": A(546, 207), "back2": A(592, 262)}
PALATAL_TONGUE = {"blade": A(286, 234), "front": A(345, 209), "mid": A(430, 222), "back": A(510, 264)}
RETRO_TONGUE = {"tip": A(298, 229), "tipUnder": A(282, 268), "blade": A(332, 264), "front": A(392, 288),
                "mid": A(456, 280), "back": A(522, 270)}
DENTAL_TONGUE = {"tip": A(234, 291), "tipUnder": A(238, 320), "blade": A(270, 252), "front": A(350, 250),
                 "mid": A(440, 264), "back": A(512, 288)}
HIGH_FRONT = {"blade": A(291, 252), "front": A(350, 228), "mid": A(430, 233), "back": A(506, 270)}
HIGH_BACK = {"tip": (258, 374), "tipUnder": (262, 392), "blade": (308, 350), "front": A(382, 294), "mid": A(462, 242),
             "back": A(536, 228), "back2": A(588, 264)}
MID_BACK = {"tip": (258, 376), "tipUnder": (262, 394), "blade": (312, 346), "front": (388, 312), "mid": (458, 286),
            "back": (528, 290), "back2": (564, 334)}

POSES = {
    "rest": P(1.4, "air", gap=3, w=104, h=4),
    "lips": P(1.0, "contact", gap=-3, upper=(0, 1), mark=(158, 322), w=104, h=0),
    "lipsNasal": P(1.0, "contact", velum=OPEN, gap=-3, upper=(0, 1), mark=(158, 322), w=104, h=0),
    "lipTeeth": P(2.2, "gap", gap=-4, lip=(25, 0), upper=(-2, -6), mark=(203, 319), w=104, h=24, teethUp=20, bite=10),
    "ridge": P(1.8, "contact", tongue=RIDGE_TONGUE, gap=9, mark=(243, 250), w=100, h=16, teethUp=8, tongue_show=0.5),
    "ridgeNasal": P(1.8, "contact", velum=OPEN, tongue=RIDGE_TONGUE, gap=9, mark=(243, 250), w=100, h=16, teethUp=8, tongue_show=0.5),
    "ridgeLateral": P(2.6, "contact", air=True, gap=12, mark=(243, 250), w=100, h=20, teethUp=8, tongue_show=0.5,
                      tongue=dict(RIDGE_TONGUE, front=A(360, 264), mid=A(440, 292), back=A(512, 308))),
    "velar": P(2.6, "contact", tongue=VELAR_TONGUE, gap=12, mark=(552, 205), w=96, h=22, teethUp=6),
    "velarFric": P(2.8, "gap", tongue=dict(VELAR_TONGUE, mid=A(462, 262), back=A(544, 224), back2=A(590, 274)), gap=12,
                   mark=(552, 213), w=96, h=24, teethUp=6),
    "velarNasal": P(2.4, "contact", velum=13.0, tongue=dict(VELAR_TONGUE, mid=A(462, 262), back=A(540, 226), back2=A(572, 268)),
                    gap=12, mark=(548, 221), w=96, h=22, teethUp=6),
    "palatal": P(1.2, "contact", tongue=PALATAL_TONGUE, gap=7, mark=(318, 215), **SPREAD, **F_SPREAD),
    "palatalFric": P(1.5, "gap", tongue=dict(PALATAL_TONGUE, blade=A(288, 243), front=A(345, 219)), gap=8, mark=(320, 216),
                     **SPREAD, **F_SPREAD),
    "retroflex": P(2.6, "contact", tongue=RETRO_TONGUE, gap=12, lip=(-7, 0), upper=(-6, 1), mark=(297, 223),
                   w=74, h=26, upper_lip=32, lower_lip=38, round=0.4, teethUp=6),
    "retroflexFric": P(2.8, "gap", tongue=dict(RETRO_TONGUE, tip=A(299, 242), tipUnder=A(283, 274)), gap=12, lip=(-7, 0),
                       upper=(-6, 1), mark=(298, 231), w=74, h=26, upper_lip=32, lower_lip=38, round=0.4, teethUp=6),
    "dental": P(0.8, "contact", tongue=DENTAL_TONGUE, gap=7, mark=(229, 294), **SPREAD, **F_SPREAD),
    "dentalFric": P(1.0, "gap", tongue=dict(DENTAL_TONGUE, tip=A(240, 298), tipUnder=A(243, 324)), gap=7, mark=(232, 298),
                    **SPREAD, **F_SPREAD),
    "a": P(12.0, "air", gap=74, w=104, h=128, teethUp=16, teethLow=12, tongue_show=0.34,
           tongue={"tip": (243, 357), "tipUnder": (246, 380), "blade": (300, 332), "front": (375, 320), "mid": (445, 320),
                   "back": (512, 332), "back2": (551, 364), "root": (568, 412)}),
    "o": P(6.8, "air", tongue=MID_BACK, gap=30, upper=(-11, 3), lip=(-13, 0), w=62, h=58, upper_lip=34, lower_lip=40, round=1.0,
           tongue_show=0.2),
    "e": P(6.0, "air", tongue=dict(MID_BACK, mid=(458, 280), back=(528, 282)), gap=26, w=100, h=44, teethUp=8, tongue_show=0.25),
    "eFront": P(6.0, "air", gap=26, **SPREAD, w=120, h=46, upper_lip=26, lower_lip=32, teethUp=10, tongue_show=0.4,
                tongue={"blade": (290, 300), "front": (365, 272), "mid": (440, 282), "back": (512, 314)}),
    "schwa": P(4.2, "air", gap=18, w=98, h=32, teethUp=8, tongue_show=0.3),
    "i": P(1.6, "air", tongue=HIGH_FRONT, gap=8, **SPREAD, **F_SPREAD),
    "u": P(2.2, "air", tongue=HIGH_BACK, gap=8, **ROUND, **F_ROUND),
    "yu": P(1.8, "air", tongue=HIGH_FRONT, gap=8, **ROUND, **F_ROUND),
    "er": P(6.0, "air", gap=26, w=98, h=44, teethUp=8, tongue_show=0.45,
            tongue={"tip": A(312, 258), "tipUnder": A(293, 290), "blade": A(348, 280), "front": A(398, 292),
                    "mid": A(456, 288), "back": A(516, 302)}),
}


LABELS = [
    {"text": "nose cavity", "at": [330, 82], "to": [330, 128], "align": "middle"},
    {"text": "hard palate", "at": [420, 244], "to": [400, 204], "align": "middle"},
    {"text": "soft palate", "at": [770, 150], "to": [578, 198], "align": "start"},
    {"text": "ridge", "at": [300, 286], "to": [256, 240], "align": "start"},
    {"text": "upper teeth", "at": [8, 232], "from": [150, 240], "to": [214, 292], "align": "start"},
    {"text": "upper lip", "at": [8, 290], "from": [112, 294], "to": [138, 300], "align": "start"},
    {"text": "lower lip", "at": [8, 380], "from": [118, 372], "to": [156, 344], "align": "start"},
    {"text": "lower teeth", "at": [8, 440], "from": [150, 432], "to": [224, 350], "align": "start"},
    {"text": "tongue tip", "at": [70, 640], "from": [150, 620], "to": [250, 338], "align": "start"},
    {"text": "tongue front", "at": [330, 690], "to": [345, 296], "align": "middle"},
    {"text": "tongue back", "at": [470, 730], "to": [500, 300], "align": "middle"},
    {"text": "throat", "at": [800, 430], "to": [612, 430], "align": "start"},
    {"text": "voice box", "at": [800, 716], "to": [600, 716], "align": "start"},
]


def build_pose(name, pose):
    body = jaw_body(pose)
    pal = palate(pose)
    tng = tongue(pose)
    points = {
        "palate": r1(pal), "velum": r1(velum_shape(pal)), "upperLipTint": r1(upper_lip_tint(pal)),
        "jaw": r1(body), "lowerLipTint": r1(lower_lip_tint(body)), "lowerTooth": r1(lower_tooth(pose)),
        "tongue": r1(tng),
    }
    points.update({k: r1(v) for k, v in front(pose).items()})
    out = {"points": points}
    if pose["air"]:
        route = air_path(pose, body, pal, tng[:24])
        if name == "ridgeLateral":
            # The tip blocks the middle, so the breath slips past the sides of the tongue:
            # drawn running down along the tongue instead of over its tip.
            top = dense(tng[3:24])
            side = []
            for x, drop in ((440, 14), (390, 26), (340, 38), (295, 50)):
                near = [p[1] for p in top if abs(p[0] - x) < 9]
                if near:
                    side.append((float(x), min(near) + drop))
            route = [p for p in route if p[0] > 460] + side + [(250.0, 322.0)] + [p for p in route if p[0] < 200]
        out["airPath"] = r1(route)
    if pose["mark"]:
        out["contact" if pose["kind"] == "contact" else "gap"] = r1([pose["mark"]])
    return out


def main():
    side_layers = [
        {"id": "bg", "type": "path", "d": "M0 0H1000V800H0Z", "fill": "background"},
        {"id": "head", "type": "path", "d": path_d(HEAD), "fill": "skin", "stroke": "outline", "strokeWidth": 5},
        {"id": "epiglottis", "type": "path", "d": path_d(EPI), "fill": "skin", "stroke": "outline", "strokeWidth": 5},
        {"id": "palate", "type": "shape", "fill": "skin", "stroke": "outline", "strokeWidth": 5},
        {"id": "velum", "type": "shape", "fill": "velum", "stroke": "outline", "strokeWidth": 5},
        {"id": "upperLipTint", "type": "shape", "fill": "lip", "opacity": 0.85},
        {"id": "upperTooth", "type": "path", "d": path_d(UPPER_TOOTH), "fill": "tooth", "stroke": "toothEdge", "strokeWidth": 4},
        {"id": "jaw", "type": "shape", "fill": "skin", "stroke": "outline", "strokeWidth": 5},
        {"id": "tongue", "type": "shape", "fill": "tongue", "stroke": "tongueEdge", "strokeWidth": 5},
        {"id": "lowerTooth", "type": "shape", "fill": "tooth", "stroke": "toothEdge", "strokeWidth": 4},
        {"id": "lowerLipTint", "type": "shape", "fill": "lip", "opacity": 0.85},
    ]
    front_layers = FRONT_STATIC + [
        {"id": "frontLips", "type": "shape", "fill": "lip", "stroke": "tongueEdge", "strokeWidth": 4},
        {"id": "frontHole", "type": "shape", "fill": "mouthDark"},
        {"id": "frontTongue", "type": "shape", "fill": "tongue"},
        {"id": "frontLowerTeeth", "type": "shape", "fill": "tooth"},
        {"id": "frontUpperTeeth", "type": "shape", "fill": "tooth"},
    ]
    poses = {name: build_pose(name, pose) for name, pose in POSES.items()}
    art = {
        "version": 1,
        "palette": PALETTE,
        "views": {
            "side": {"width": 1000, "height": 800, "layers": side_layers, "labels": LABELS},
            "front": {"width": 500, "height": 400, "layers": front_layers},
        },
        "anchors": {"larynx": [592, 716], "mouthOut": [96, 338],
                    "nasalPath": [[600, 420], [612, 300], [590, 170], [480, 112], [300, 104], [176, 138], [98, 214]]},
        "poses": poses,
    }
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(art, f, ensure_ascii=False, separators=(",", ":"))
    print("wrote %s (%d poses, %d bytes)" % (sys.argv[1], len(poses), os.path.getsize(sys.argv[1])))


if __name__ == "__main__":
    main()
