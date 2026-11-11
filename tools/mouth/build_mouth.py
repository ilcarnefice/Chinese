#!/usr/bin/env python3
"""Builds the mouth diagrams from one real textbook figure.

The picture is "Anatomy of Nose-Pharynx-Mouth-Larynx" from OpenStax College,
Anatomy & Physiology (CC BY 3.0), kept unchanged in tools/mouth/source/. This
script does not draw anatomy; it takes that picture apart and moves the pieces:

  1. removes the printed pointer lines and crops the head;
  2. cuts out the parts that move when we speak: the tongue, the lower jaw
     (with lower lip and teeth) and the soft palate, and fills in what is
     behind them;
  3. for every mouth position ("pose") works out where each piece goes: the jaw
     swings on its hinge, the lips shift, the soft palate lifts, and the tongue
     is bent by a dozen handles on its surface;
  4. writes the pieces as PNG files and the poses as mouth.json. A pose stores,
     for each piece, how far every corner of a grid laid over it has moved; the
     app (and preview.py) draws the piece stretched over that grid.

Usage: python3 tools/mouth/build_mouth.py [assets dir] [--debug dir]
Then:  python3 tools/mouth/check_mouth.py <assets dir>/mouth.json
       python3 tools/mouth/preview.py <assets dir> <out dir>
"""
import json
import math
import os
import sys

import cv2
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(HERE, "source", "2303_Anatomy_of_Nose-Pharynx-Mouth-Larynx.jpg")

X0, Y0, SIZE = 615, 258, 900        # the part of the figure we use, in its pixels
VIEW = 1000.0                       # the view is VIEW x VIEW units
K = VIEW / SIZE                     # view units per picture pixel
MESH_UNIT = 0.25                    # grid movements are stored as whole numbers of this many view units
CAVITY = (53, 70, 203)              # the dark red of the inside of the mouth (BGR)
WHITE = (255, 255, 255)

PALETTE = {"air": "#1C7ED6", "contact": "#FFB703", "voice": "#7C4DFF", "label": "#1F2333"}


def smoothstep(a, b, x):
    t = np.clip((np.asarray(x, dtype=np.float64) - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------ the picture

def remove_lines(im):
    """Paints out the black pointer lines. Each line pixel becomes a blend of two
    clean pixels on opposite sides of the line. Of the directions that cross the
    line, the one whose two ends look most alike is used, so an edge or a stripe
    that the line cut through is joined up again instead of being smeared. The
    choice is made for a small neighbourhood at a time, so that the joined-up
    stripes run straight."""
    h, w = im.shape[:2]
    b, g, r = [im[:, :, i].astype(np.int32) for i in range(3)]
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    core = ((mx < 150) & (mx - mn < 60)).astype(np.uint8)
    mask = cv2.dilate(core, np.ones((3, 3), np.uint8)) > 0
    dirs = [(1, 0), (0, 1), (1, 1), (1, -1), (2, 1), (1, 2), (2, -1), (1, -2), (3, 1), (1, 3), (3, -1), (1, -3),
            (3, 2), (2, 3), (3, -2), (2, -3), (4, 1), (4, -1), (1, 4), (1, -4), (5, 1), (5, -1), (5, 2), (5, -2),
            (6, 1), (6, -1), (1, 5), (1, -5)]
    src = im.astype(np.float32)
    look = cv2.GaussianBlur(src, (0, 0), 1.0)          # compare slightly blurred colours: less fooled by noise
    ys, xs = np.nonzero(mask)
    n = len(ys)
    reach = 46.0
    ends = []                                          # per direction: steps and end points on both sides
    cost = np.full((n, len(dirs)), 1e9, np.float32)
    for d, (dx, dy) in enumerate(dirs):
        step = math.hypot(dx, dy)
        sides = []
        for sign in (1, -1):
            k_found = np.zeros(n, np.int32)
            ex = np.zeros(n, np.int32)
            ey = np.zeros(n, np.int32)
            for k in range(1, int(reach / step) + 1):
                xx = xs + sign * dx * k
                yy = ys + sign * dy * k
                inside = (xx >= 0) & (xx < w) & (yy >= 0) & (yy < h)
                clear = np.zeros(n, bool)
                clear[inside] = ~mask[yy[inside], xx[inside]]
                new = clear & (k_found == 0)
                k_found[new] = k
                ex[new] = xx[new]
                ey[new] = yy[new]
            sides.append((k_found, ex, ey))
        (k1, x1, y1), (k2, x2, y2) = sides
        ok = (k1 > 0) & (k2 > 0)
        diff = np.abs(look[y1, x1] - look[y2, x2]).sum(axis=1)
        cost[ok, d] = diff[ok] + 1.2 * (k1[ok] + k2[ok]) * step
        ends.append((k1, x1, y1, k2, x2, y2, ok))
    # agree on a direction with the neighbours
    weight = cv2.blur(mask.astype(np.float32), (9, 9))[ys, xs]
    smooth = np.empty_like(cost)
    for d in range(len(dirs)):
        dense = np.zeros((h, w), np.float32)
        dense[ys, xs] = np.minimum(cost[:, d], 2000.0)
        smooth[:, d] = cv2.blur(dense, (9, 9))[ys, xs] / np.maximum(weight, 1e-6)
    smooth[cost >= 1e9] = 1e9
    best = np.argmin(smooth, axis=1)
    out = src.copy()
    for d in range(len(dirs)):
        k1, x1, y1, k2, x2, y2, ok = ends[d]
        pick = (best == d) & ok
        t = (k1[pick] / np.maximum(k1[pick] + k2[pick], 1)).astype(np.float32)[:, None]
        out[ys[pick], xs[pick]] = src[y1[pick], x1[pick]] * (1 - t) + src[y2[pick], x2[pick]] * t
    # soften the repaired strip a little, so that small kinks in it do not catch the eye
    strip = cv2.GaussianBlur(cv2.dilate(mask.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(np.float32), (0, 0), 0.8)
    out = out * (1 - strip[:, :, None]) + cv2.GaussianBlur(out, (0, 0), 1.1) * strip[:, :, None]
    return np.clip(out, 0, 255).astype(np.uint8)


def load_picture():
    full = cv2.imread(SOURCE)
    if full is None:
        sys.exit("cannot read " + SOURCE)
    pad = 16
    part = full[Y0 - pad:Y0 + SIZE + pad, X0 - pad:X0 + SIZE + pad]
    clean = remove_lines(part)
    return remove_tonsils(clean[pad:pad + SIZE, pad:pad + SIZE].copy())


def remove_tonsils(pic):
    """Paints out the two yellow tonsils. They play no part in speaking and only
    distract: the one at the back of the mouth becomes throat wall and dark
    mouth, continued up from just below it; the one on the back of the tongue
    becomes tongue."""
    b, g, r = [pic[:, :, i].astype(np.int32) for i in range(3)]
    yellow = ((r - g < 62) & (r > 222) & (b < 190)).astype(np.uint8)
    out = pic.copy()
    round_ = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (11, 11))
    back = np.zeros_like(yellow)
    back[392:522, 92:162] = yellow[392:522, 92:162]
    back = cv2.dilate(cv2.morphologyEx(back, cv2.MORPH_CLOSE, round_), round_)
    for y, x in zip(*np.nonzero(back)):
        out[y, x] = pic[532, x]
    on_tongue = np.zeros_like(yellow)
    on_tongue[468:585, 138:210] = yellow[468:585, 138:210]
    on_tongue = cv2.dilate(cv2.morphologyEx(on_tongue, cv2.MORPH_CLOSE, round_), round_)
    out = cv2.inpaint(out, on_tongue, 7, cv2.INPAINT_TELEA)
    soft = cv2.GaussianBlur(out, (0, 0), 0.8)
    edge = cv2.dilate(back, np.ones((3, 3), np.uint8)) > 0
    out[edge] = soft[edge]
    return out


def fill_holes(mask):
    flood = mask.copy()
    h, w = mask.shape
    cv2.floodFill(flood, np.zeros((h + 2, w + 2), np.uint8), (0, 0), 2)
    out = mask.copy()
    out[flood != 2] = 1
    return out


def tongue_mask(pic):
    b, g, r = [pic[:, :, i].astype(np.int32) for i in range(3)]
    m = ((r - g > 88) & (r - g < 121) & (r > 190) & (b < 125)).astype(np.uint8)
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
    _, labels = cv2.connectedComponents(m, connectivity=4)
    m = (labels == labels[542, 385]).astype(np.uint8)
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))
    return fill_holes(m)


VELUM_AREA = [(300, 256), (245, 254), (205, 259), (165, 271), (133, 288), (106, 310), (89, 344), (81, 382),
              (81, 416), (90, 440), (110, 449), (128, 444), (141, 426), (145, 398), (153, 374), (168, 353),
              (187, 338), (216, 326), (246, 318), (300, 312)]


def velum_mask(pic):
    b, g, r = [pic[:, :, i].astype(np.int32) for i in range(3)]
    area = np.zeros(pic.shape[:2], np.uint8)
    cv2.fillPoly(area, [np.array(VELUM_AREA, np.int32)], 1)
    m = ((area > 0) & (r - g < 118) & (r - g > 60) & (r > 200)).astype(np.uint8)
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7)))
    count, labels, stats, _ = cv2.connectedComponentsWithStats(m, connectivity=8)
    m = (labels == 1 + int(np.argmax(stats[1:, 4]))).astype(np.uint8)
    m = fill_holes(m)
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    count, labels, stats, _ = cv2.connectedComponentsWithStats(m, connectivity=8)
    m = (labels == 1 + int(np.argmax(stats[1:, 4]))).astype(np.uint8)
    return fill_holes(m)


THROAT_EDGE_Y = [286, 300, 318, 340, 365, 395, 425, 470]     # behind the soft palate: where the throat wall
THROAT_EDGE_X = [175, 135, 114, 104, 99, 98, 100, 103]       # (left) meets the dark inside of the mouth (right)


def paint_behind_velum(pic, hole):
    """What is seen where the soft palate hung once it has lifted: the wall of the
    throat on the left, continued up from below, and the dark mouth on the right."""
    hole = hole.astype(np.uint8)
    out = pic.copy()
    h, w = hole.shape
    for x in range(w):
        rows = np.nonzero(hole[:, x])[0]
        if len(rows) == 0:
            continue
        below = pic[470, min(x, 96)]      # the throat wall, taken from under the soft palate's tip
        for y in rows:
            out[y, x] = below if x < np.interp(y, THROAT_EDGE_Y, THROAT_EDGE_X) else CAVITY
    soft = cv2.GaussianBlur(out, (0, 0), 0.8)
    out[hole > 0] = soft[hole > 0]
    return out


# What matters for speaking is the mouth: lips, teeth, tongue and the roof of the mouth. Everything else in the
# picture (nose cavity, spine, voice box) is kept, for bearings, but washed out.
FOCUS_LEFT, FOCUS_TOP = 85, 256
FOCUS_FLOOR_X = [85, 150, 165, 200, 250, 300, 330, 560, 600, 900]
FOCUS_FLOOR_Y = [585, 585, 600, 650, 700, 722, 742, 742, 760, 760]
WASH = 0.62          # how far the rest is faded towards white


def focus_weight(pic, tongue, velum):
    """1 where the picture keeps its full colour (mouth and skin), 0 where it is washed out."""
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    b, g, r = [pic[:, :, i].astype(np.int32) for i in range(3)]
    skin = ((abs(r - 247) < 16) & (abs(g - 213) < 22) & (abs(b - 176) < 28)).astype(np.uint8)
    skin = cv2.morphologyEx(skin, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    count, labels, stats, _ = cv2.connectedComponentsWithStats(skin, connectivity=8)
    big = np.isin(labels, [i for i in range(1, count) if stats[i, 4] > 1500])
    floor = np.interp(xs, FOCUS_FLOOR_X, FOCUS_FLOOR_Y)
    mouth = (ys >= FOCUS_TOP) & (xs >= FOCUS_LEFT) & (ys <= floor)
    keep = mouth | cv2.dilate(big.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool) | (tongue > 0) | (velum > 0)
    return cv2.GaussianBlur(keep.astype(np.float32), (0, 0), 12)


def wash(image, weight):
    faded = image.astype(np.float32) * (1 - WASH) + 255.0 * WASH
    w = weight[:, :, None]
    return np.clip(image.astype(np.float32) * w + faded * (1 - w), 0, 255).astype(np.uint8)


def background_mask(pic):
    """The white paper outside the head, found by flooding in from the right edge."""
    white = (pic.min(axis=2) > 236).astype(np.uint8)
    _, labels = cv2.connectedComponents(white, connectivity=4)
    return labels == labels[600, SIZE - 2]


# ------------------------------------------------------------------ curves and warps

def spline(points, per_segment=24):
    """Dense points along an open Catmull-Rom curve through `points`."""
    pts = [np.array(p, dtype=np.float64) for p in points]
    n = len(pts)
    out = []
    for i in range(n - 1):
        p0, p1, p2, p3 = pts[max(i - 1, 0)], pts[i], pts[i + 1], pts[min(i + 2, n - 1)]
        for k in range(per_segment):
            t = k / per_segment
            out.append(0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t
                              + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    out.append(pts[-1])
    return np.array(out)


def thin_plate(src, values):
    """A smooth function that takes the given `values` at the points `src`."""
    src = np.asarray(src, dtype=np.float64) / 100.0
    values = np.asarray(values, dtype=np.float64)
    n = len(src)

    def kernel(d):
        return np.where(d > 0, d * d * np.log(d + 1e-12), 0.0)

    a = np.zeros((n + 3, n + 3))
    a[:n, :n] = kernel(np.linalg.norm(src[:, None] - src[None], axis=2))
    a[:n, n] = 1
    a[:n, n + 1:] = src
    a[n, :n] = 1
    a[n + 1:, :n] = src.T
    rhs = np.zeros((n + 3, values.shape[1]))
    rhs[:n] = values
    w = np.linalg.solve(a, rhs)

    def f(p):
        p = np.asarray(p, dtype=np.float64) / 100.0
        u = kernel(np.linalg.norm(p[:, None] - src[None], axis=2))
        return u @ w[:n] + w[n] + p @ w[n + 1:]

    return f


def sample(field, p):
    """Bilinear look-up of a scalar picture `field` at float positions p (n x 2)."""
    h, w = field.shape
    x = np.clip(p[:, 0], 0, w - 1.001)
    y = np.clip(p[:, 1], 0, h - 1.001)
    x0 = np.floor(x).astype(int)
    y0 = np.floor(y).astype(int)
    fx, fy = x - x0, y - y0
    return (field[y0, x0] * (1 - fx) * (1 - fy) + field[y0, x0 + 1] * fx * (1 - fy)
            + field[y0 + 1, x0] * (1 - fx) * fy + field[y0 + 1, x0 + 1] * fx * fy)


# ------------------------------------------------------------------ the lower jaw and the lips

HINGE = np.array([65.0, 232.0])     # the jaw swings about this point (just in front of the ear)
JAW_BACK, JAW_FRONT = 330.0, 540.0  # the jaw's pull fades out between these x positions

# Top edge of the jaw piece: over the tip of the lower front tooth, then between the lips where they touch.
CUT = [(330, 486), (612, 486), (660, 475), (679, 472), (900, 472)]
LIP_LINE = np.array([677.0, 474.0])  # where the lips touch: the red inside of the mouth ends here

UPPER_LIP = np.array([688.0, 461.0])     # the middle of each lip's free edge: the lip handles pull here
LOWER_LIP = np.array([686.0, 485.0])


def jaw_weight(p):
    return smoothstep(JAW_BACK, JAW_FRONT, p[:, 0])


def swing(p, degrees):
    a = math.radians(degrees)
    r = p - HINGE
    return HINGE + np.stack([r[:, 0] * math.cos(a) - r[:, 1] * math.sin(a),
                             r[:, 0] * math.sin(a) + r[:, 1] * math.cos(a)], axis=1)


def jaw_shift(p, degrees):
    """How far each point moves when the jaw opens by `degrees` (negative closes it)."""
    return jaw_weight(p)[:, None] * (swing(p, degrees) - p)


def upper_lip_weight(p):
    d2 = ((p - UPPER_LIP) ** 2).sum(axis=1)
    return np.exp(-d2 / (2 * 38.0 ** 2)) * smoothstep(634, 654, p[:, 0])


def lower_lip_weight(p, centre=LOWER_LIP, spread=(37.0, 37.0)):
    d = p - np.asarray(centre, dtype=np.float64)
    return np.exp(-(d[:, 0] / spread[0]) ** 2 / 2 - (d[:, 1] / spread[1]) ** 2 / 2) * smoothstep(604, 636, p[:, 0])


# The edge of the dark mouth opening between the lips: it runs down from where the lips touch, along the
# path the lower lip takes as the jaw opens.
MOUTH_EDGE = np.array([swing(LIP_LINE[None], a)[0] for a in np.linspace(0, 14, 60)])


def mouth_edge_weight(p):
    """How much of the lower lip's sideways movement the edge of the mouth opening follows."""
    edge_x = np.interp(p[:, 1], MOUTH_EDGE[:, 1], MOUTH_EDGE[:, 0], left=LIP_LINE[0])
    return (np.exp(-((p[:, 0] - edge_x) / 30.0) ** 2 / 2) * smoothstep(468, 482, p[:, 1])
            * (1 - smoothstep(560, 610, p[:, 1])))


def cut_y(x):
    xs = [c[0] for c in CUT]
    ys = [c[1] for c in CUT]
    return np.interp(x, xs, ys)


# ------------------------------------------------------------------ the soft palate

VELUM_REST = [(290, 285), (262, 286), (232, 289), (202, 297), (173, 311), (150, 329), (132, 352), (120, 378),
              (114, 402), (112, 426)]
VELUM_UP = [(290, 285), (262, 286), (232, 284), (200, 281), (168, 280), (137, 283), (108, 290), (85, 302),
            (69, 322), (63, 347)]
VELUM_LINE = spline(VELUM_REST)
VELUM_LINE_UP = spline(VELUM_UP)


def frame_of(line):
    t = np.gradient(line, axis=0)
    t /= np.linalg.norm(t, axis=1)[:, None]
    return t, np.stack([-t[:, 1], t[:, 0]], axis=1)


def along(line):
    return np.concatenate([[0.0], np.cumsum(np.linalg.norm(np.diff(line, axis=0), axis=1))])


VELUM_S = along(VELUM_LINE)


def velum_place(p):
    """For each point: index of the nearest point of the soft palate's middle line,
    and its offsets across and along that line."""
    d = np.linalg.norm(p[:, None] - VELUM_LINE[None], axis=2)
    i = np.argmin(d, axis=1)
    t, n = frame_of(VELUM_LINE)
    rel = p - VELUM_LINE[i]
    return i, (rel * n[i]).sum(axis=1), (rel * t[i]).sum(axis=1)


def velum_shift(p, amount):
    """How far each point of the soft palate moves when it is lifted by `amount` (0..1)."""
    if amount == 0:
        return np.zeros_like(p)
    i, across, extra = velum_place(p)
    line = VELUM_LINE + (VELUM_LINE_UP - VELUM_LINE) * amount
    t, n = frame_of(line)
    target = line[i] + across[:, None] * n[i] + extra[:, None] * t[i]
    return smoothstep(15, 55, VELUM_S[i])[:, None] * (target - p)


# ------------------------------------------------------------------ the tongue

# Handles on the surface of the tongue, from the root round the top to the tip and under it.
HANDLES = {
    "root": (152, 530), "back": (160, 440), "backtop": (212, 382), "dorsum": (290, 353), "mid": (370, 354),
    "front": (450, 376), "blade": (515, 410), "pretip": (560, 446), "tip": (588, 496), "under": (570, 515),
    "under2": (538, 545),
}
SURFACE = ["back", "backtop", "dorsum", "mid", "front", "blade", "pretip", "tip"]
# Where the tongue is rooted (to the jaw in front, to the throat behind): these never move by themselves.
PINS = [(548, 588), (555, 630), (557, 680), (165, 585), (195, 640), (250, 700)]
FREE_BELOW = 585       # the tongue's outline above this line is its free surface
MOVE_FROM, MOVE_TO = 15.0, 150.0     # how deep under the surface the bending reaches
SHOW_TO = 190.0                      # the tongue piece fades out between MOVE_TO and SHOW_TO
ERASE_TO = 135.0                     # the tongue is painted out of the pieces below it down to this depth


# Extra handles inside the tip; only used when a pose turns the tip as a block.
INNER = {"tipTop": (577, 470), "tipCore": (561, 489), "bladeCore": (536, 468)}
TIP_BLOCK = ["pretip", "tipTop", "tip", "under", "tipCore"]


class Tongue:
    def __init__(self, mask):
        self.mask = mask
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
        outline = max(contours, key=len).reshape(-1, 2)
        free = outline[outline[:, 1] < FREE_BELOW]
        seed = np.ones(mask.shape, np.uint8)
        seed[free[:, 1], free[:, 0]] = 0
        self.depth = cv2.distanceTransform(seed, cv2.DIST_L2, 5)
        self.handles = {}
        for name, (x, y) in HANDLES.items():
            j = np.argmin(((outline - (x, y)) ** 2).sum(axis=1))
            self.handles[name] = outline[j].astype(np.float64)
        for name, p in INNER.items():
            self.handles[name] = np.array(p, dtype=np.float64)

    def shift(self, p, jaw, local):
        """How far each point of the tongue moves. `local` gives, per handle, its own
        movement on top of being carried by the jaw. Surface handles that are not
        named stay put; inner handles that are not named are left free."""
        names = list(HANDLES) + [n for n in INNER if n in local]
        points = np.array([self.handles[n] for n in names] + [np.array(q, float) for q in PINS])
        values = np.zeros_like(points)
        for k, name in enumerate(names):
            values[k] = local.get(name, (0.0, 0.0))
        bend = thin_plate(points, values)(p)
        reach = 1.0 - smoothstep(MOVE_FROM, MOVE_TO, sample(self.depth, p))
        return jaw_shift(p, jaw) + reach[:, None] * bend


# ------------------------------------------------------------------ the poses

def pose(jaw=0.0, velum=1.0, ulip=(0, 0), llip=(0, 0), tongue=None, to=None, turn=None, contact=(), gap=(),
         air="auto", via=None, llip_at=None, llip_spread=(37.0, 37.0)):
    """One mouth position.
    jaw: degrees open (+) or closed (-) from the picture. velum: 1 lifted (nose shut), 0 hanging (nose open).
    ulip, llip: movement of the upper and lower lip (the lower lip on top of the jaw's movement).
    tongue: per handle, movement on top of the jaw's. to: per handle, the exact place it must reach.
    turn: (degrees, pivot, where the tip ends up): swings the whole tip of the tongue as one block.
    contact / gap: marker positions. air: "auto" for a breath route through the mouth, None for none.
    via: the breath route over the tongue and out between the teeth, in place of the automatic one.
    llip_at: the point of the lower lip that llip pulls on, when it is not the middle of the lip's edge.
    llip_spread: how far around that point the pull reaches, across and down."""
    return dict(jaw=jaw, velum=velum, ulip=ulip, llip=llip, tongue=tongue or {}, to=to or {}, turn=turn,
                contact=list(contact), gap=list(gap), air=air, via=via, llip_at=llip_at or tuple(LOWER_LIP),
                llip_spread=llip_spread)


RIDGE_TONGUE = dict(turn=(-46, (530, 468), (586, 383)), to={"blade": (504, 373), "under2": (549, 486)},
                    tongue={"front": (0, -4)})
PALATAL_TONGUE = {"dorsum": (292, 335), "mid": (372, 315), "front": (450, 311), "blade": (512, 337),
                  "pretip": (562, 408)}
# zh ch sh r: the tip turns up to the roof a little way behind the ridge, where plain dark mouth shows around it
RETROFLEX_TONGUE = dict(turn=(-96, (530, 468), (528, 338)),
                        to={"blade": (452, 398), "front": (408, 398), "mid": (356, 374), "under2": (546, 466)})
RETROFLEX_BREATH = [(215, 340), (300, 332), (375, 338), (440, 350), (490, 346), (526, 346), (556, 362), (580, 400),
                    (598, 442), (608, 470)]
DENTAL_TONGUE = dict(turn=(-22, (530, 468), (602, 424)), to={"blade": (512, 390), "front": (450, 367),
                                                             "under2": (549, 502)})
VELAR_TONGUE = {"back": (158, 400), "backtop": (208, 305), "dorsum": (285, 316), "mid": (370, 356),
                "front": (450, 388), "blade": (514, 424), "pretip": (557, 458), "tip": (584, 498)}
HIGH_FRONT = {"dorsum": (292, 337), "mid": (372, 320), "front": (452, 329), "blade": (514, 362),
              "pretip": (562, 416)}
HIGH_BACK = {"back": (156, 406), "backtop": (205, 327), "dorsum": (285, 331), "mid": (368, 362),
             "front": (446, 392), "blade": (510, 426), "pretip": (554, 458), "tip": (582, 500)}


def shifted(points, dx, dy, names=None):
    return {k: (v[0] + dx, v[1] + dy) if (names is None or k in names) else v for k, v in points.items()}


OPEN_LIPS = dict(ulip=(0, -5), llip=(0, 10))
RIDGE_LIPS = dict(ulip=(0, -6), llip=(0, 14))
SPREAD_LIPS = dict(ulip=(-3, -7), llip=(-4, 25))
ROUND_LIPS = dict(ulip=(25, -3), llip=(27, 13))

POSES = {
    "rest": pose(velum=0, air="auto"),
    "lips": pose(contact=[(679, 472)], air=None),
    "lipsNasal": pose(velum=0, contact=[(679, 472)], air=None),
    "lipTeeth": pose(jaw=-0.6, ulip=(2, -12), llip=(-35, -20), llip_at=(660, 478), llip_spread=(34.0, 22.0),
                     gap=[(621, 451)]),
    "ridge": pose(jaw=-0.3, **RIDGE_LIPS, **RIDGE_TONGUE, contact=[(587, 380)], air=None),
    "ridgeNasal": pose(jaw=-0.3, velum=0, **RIDGE_LIPS, **RIDGE_TONGUE, contact=[(587, 380)], air=None),
    # l: the tip is on the ridge but the body of the tongue is low, and the breath passes beside the tongue
    "ridgeLateral": pose(jaw=0.0, **RIDGE_LIPS, turn=RIDGE_TONGUE["turn"],
                         to={"blade": (503, 398), "under2": (549, 486)},
                         tongue={"front": (0, 24), "mid": (0, 24), "dorsum": (0, 14), "backtop": (0, 6)},
                         contact=[(587, 380)],
                         via=[(215, 340), (300, 338), (385, 350), (450, 384), (505, 428), (560, 458), (604, 470)]),
    "velar": pose(jaw=0.5, **OPEN_LIPS, to=VELAR_TONGUE, contact=[(222, 309)], air=None),
    "velarFric": pose(jaw=0.5, **OPEN_LIPS, to=shifted(VELAR_TONGUE, 0, 12, ["backtop", "dorsum"]),
                      gap=[(222, 313)]),
    "velarNasal": pose(jaw=0.5, velum=0, **OPEN_LIPS,
                       to={"back": (156, 392), "backtop": (200, 322), "dorsum": (286, 332), "mid": (370, 358),
                           "front": (450, 386), "blade": (514, 420), "pretip": (558, 454)},
                       contact=[(197, 330)], air=None),
    "palatal": pose(jaw=-1.2, **SPREAD_LIPS, to=PALATAL_TONGUE, contact=[(478, 322)], air=None),
    "palatalFric": pose(jaw=-1.2, **SPREAD_LIPS, to=shifted(PALATAL_TONGUE, 0, 10, ["mid", "front", "blade"]),
                        gap=[(478, 326)]),
    "retroflex": pose(jaw=0.5, ulip=(5, -5), llip=(6, 10), **RETROFLEX_TONGUE, contact=[(527, 341)], air=None),
    "retroflexFric": pose(jaw=0.5, ulip=(5, -5), llip=(6, 10), turn=(-96, (530, 468), (527, 351)),
                          to=RETROFLEX_TONGUE["to"], gap=[(526, 346)], via=RETROFLEX_BREATH),
    "dental": pose(jaw=-1.5, **SPREAD_LIPS, **DENTAL_TONGUE, contact=[(604, 419)], air=None),
    "dentalFric": pose(jaw=-1.5, **SPREAD_LIPS, turn=(-22, (530, 468), (588, 434)), to=DENTAL_TONGUE["to"],
                       gap=[(601, 428)]),
    "a": pose(jaw=7.0, ulip=(0, -5),
              tongue={"back": (-10, 8), "backtop": (-8, 16), "dorsum": (0, 18), "mid": (0, 14), "front": (0, 6),
                      "blade": (0, 2)}),
    "o": pose(jaw=3.2, ulip=(17, 5), llip=(19, -13),
              tongue={"back": (-10, -8), "backtop": (-10, -22), "dorsum": (-8, -14), "mid": (-6, 4),
                      "front": (-6, 12), "blade": (-6, 12), "pretip": (-6, 6), "tip": (-8, 2)}),
    "e": pose(jaw=2.6, ulip=(0, -5),
              tongue={"back": (-8, -10), "backtop": (-8, -30), "dorsum": (-6, -22), "mid": (-4, -2),
                      "front": (-4, 10), "blade": (-4, 10), "pretip": (-4, 4), "tip": (-6, 2)}),
    "eFront": pose(jaw=2.0, ulip=(-3, -6), llip=(-4, 3),
                   tongue={"dorsum": (0, -6), "mid": (2, -18), "front": (4, -22), "blade": (4, -18),
                           "pretip": (2, -8)}),
    "schwa": pose(jaw=2.0, ulip=(0, -4)),
    "i": pose(jaw=-1.2, **SPREAD_LIPS, to=HIGH_FRONT, tongue={"back": (8, 4)}),
    "u": pose(jaw=-0.3, **ROUND_LIPS, to=HIGH_BACK),
    "yu": pose(jaw=-1.0, ulip=(25, -3), llip=(27, 22), to=HIGH_FRONT, tongue={"back": (8, 4)}),
    "er": pose(jaw=3.0, ulip=(0, -4), turn=(-80, (530, 468), (546, 404)),
               to={"blade": (474, 432), "front": (426, 412), "mid": (362, 376), "under2": (556, 505)},
               via=[(215, 346), (300, 340), (375, 348), (430, 362), (480, 378), (522, 386), (560, 394), (586, 438),
                    (603, 482)]),
}

LARYNX = (192, 830)
MOUTH_OUT = (742, 474)
# What the app shows of the picture (x, y, width, height in picture pixels, 5 wide to 4 high):
WINDOWS = {
    "mouth": (70, 172, 790, 632),       # nose tip to chin: lips, teeth, tongue and the roof of the mouth
    "names": (70, 172, 830, 664),       # the same with room on the right for the names of the parts
}
THROAT = [(192, 830), (152, 776), (108, 690), (88, 600), (84, 520)]
NASAL = THROAT + [(74, 440), (62, 370), (70, 300), (100, 240), (170, 222), (300, 222), (440, 226), (570, 226),
                  (660, 232), (725, 252), (756, 280), (784, 318)]

# The roof of the mouth: y of the palate's underside at each x (soft palate hanging / lifted, then hard palate).
ROOF_X = [130, 150, 170, 190, 210, 230, 250, 300, 350, 400, 450, 500, 550, 575, 600]
ROOF_HANGING = [440, 368, 351, 334, 323, 314, 307, 301, 302, 305, 314, 330, 356, 372, 392]
ROOF_LIFTED = [304, 303, 303, 303, 304, 305, 306, 301, 302, 305, 314, 330, 356, 372, 392]
UPPER_TOOTH = np.array([616.0, 447.0])
LOWER_TOOTH = np.array([603.0, 490.0])
UPPER_LIP_EDGE = np.array([672.0, 470.0])
LOWER_LIP_EDGE = np.array([672.0, 476.0])

LABELS = [
    # text, where the text sits, where its pointer starts, where it points (picture pixels), alignment
    ("hard palate", (352, 262), (372, 276), (392, 298), "middle"),
    ("soft palate", (150, 262), (158, 276), (178, 302), "middle"),
    ("ridge", (722, 372), (716, 372), (594, 388), "start"),
    ("upper teeth", (722, 416), (716, 416), (620, 432), "start"),
    ("lips", (722, 472), (716, 472), (704, 472), "start"),
    ("lower teeth", (722, 528), (716, 528), (607, 512), "start"),
    ("tongue tip", (500, 528), (540, 514), (583, 496), "middle"),
    ("tongue front", (452, 440), (460, 426), (478, 394), "middle"),
    ("tongue back", (264, 440), (256, 426), (238, 378), "middle"),
]


def roof(x, velum):
    hanging = np.interp(x, ROOF_X, ROOF_HANGING)
    lifted = np.interp(x, ROOF_X, ROOF_LIFTED)
    return hanging + (lifted - hanging) * velum


# ------------------------------------------------------------------ building

def grid(rect, cols, rows):
    x, y, w, h = rect
    xs = np.linspace(x, x + w, cols + 1)
    ys = np.linspace(y, y + h, rows + 1)
    return np.array([(gx, gy) for gy in ys for gx in xs], dtype=np.float64)


def folds(points, moved, cols, rows, alpha):
    """Number of visible grid triangles a warp has turned inside out or squashed nearly flat."""
    bad = 0
    for r in range(rows):
        for c in range(cols):
            i = r * (cols + 1) + c
            x0, y0 = points[i]
            x1, y1 = points[i + cols + 2]
            if alpha[int(y0):int(math.ceil(y1)), int(x0):int(math.ceil(x1))].max() < 0.5:
                continue
            for tri in ((i, i + cols + 1, i + cols + 2), (i, i + cols + 2, i + 1)):
                def area(pts):
                    a, b, d = [pts[t] for t in tri]
                    return (b[0] - a[0]) * (d[1] - a[1]) - (b[1] - a[1]) * (d[0] - a[0])
                if area(moved) / area(points) < 0.08:
                    bad += 1
    return bad


def bounds(alpha, pad=4):
    ys, xs = np.nonzero(alpha > 0)
    x0, x1 = max(xs.min() - pad, 0), min(xs.max() + pad + 1, SIZE)
    y0, y1 = max(ys.min() - pad, 0), min(ys.max() + pad + 1, SIZE)
    return int(x0), int(y0), int(x1 - x0), int(y1 - y0)


def piece(pic, alpha, rect):
    x, y, w, h = rect
    out = np.dstack([pic[y:y + h, x:x + w], np.clip(alpha[y:y + h, x:x + w] * 255, 0, 255).astype(np.uint8)])
    return out


def soften(mask, sigma=0.7):
    return cv2.GaussianBlur(mask.astype(np.float32), (0, 0), sigma)


def build(out_dir, debug_dir=None):
    pic = load_picture()
    tongue = Tongue(tongue_mask(pic))
    velum = velum_mask(pic)
    paper = background_mask(pic)
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    everywhere = np.stack([xs.ravel(), ys.ravel()], axis=1).astype(np.float64)

    # --- soft palate piece: fades in over its first few pixels, where it does not move
    box = bounds(velum, 6)
    vx, vy, vw, vh = box
    sub = np.stack([xs[vy:vy + vh, vx:vx + vw].ravel(), ys[vy:vy + vh, vx:vx + vw].ravel()], axis=1).astype(float)
    index, _, _ = velum_place(sub)
    s_map = np.zeros((SIZE, SIZE))
    s_map[vy:vy + vh, vx:vx + vw] = VELUM_S[index].reshape(vh, vw)
    velum_alpha = soften(velum) * smoothstep(0, 18, s_map)
    velum_rect = bounds(velum_alpha > 0.01, 6)

    # --- tongue piece
    tongue_alpha = soften(tongue.mask) * (1.0 - smoothstep(MOVE_TO, SHOW_TO, tongue.depth))
    tongue_rect = bounds(tongue_alpha > 0.01, 6)

    # --- lower jaw piece: everything under the cut, except the tongue's top layer and the paper beside the lips
    under_cut = ys > cut_y(xs)
    jaw_rect = (int(JAW_BACK), 440, 760 - int(JAW_BACK), SIZE - 440)
    erased_tongue = (tongue.mask > 0) & (tongue.depth < ERASE_TO)
    jaw_mask = under_cut & ~erased_tongue
    jaw_mask &= ~(paper & (ys < 530))
    jaw_alpha = soften(jaw_mask)
    # the tongue's own outline is hidden by the tongue piece, so keep that edge hard and fully covered
    jaw_alpha[(tongue.mask > 0) & (tongue.depth >= ERASE_TO)] = 1.0

    # --- the head with the moving parts taken out and what is behind them painted in
    head = pic.copy()
    hole = ((velum > 0) & (s_map > 46)).astype(np.uint8)
    hole = cv2.dilate(hole, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))
    hole[(s_map <= 40) & (velum > 0)] = 0
    head = paint_behind_velum(head, hole)
    # along its free surface the tongue's soft edge is painted out too, so that no outline is left behind
    wide = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9))
    free_part = (erased_tongue & (ys < FREE_BELOW - 4)).astype(np.uint8)
    head[erased_tongue | ((cv2.dilate(free_part, wide) > 0) & (ys < FREE_BELOW))] = CAVITY
    # under the cut the lower jaw can drop away: behind it is the dark mouth, and open air in front of the lips
    arc_x = np.interp(ys, MOUTH_EDGE[:, 1], MOUTH_EDGE[:, 0], left=LIP_LINE[0])
    band = under_cut & (ys < 665) & (xs > 500) & ~((tongue.mask > 0) & (tongue.depth >= ERASE_TO))
    head[band & (xs <= arc_x)] = CAVITY
    head[band & (xs > arc_x)] = WHITE

    weight = focus_weight(pic, tongue.mask, velum)
    head = wash(head, weight)
    lip_crop = (616, 330, 130, 290)       # the upper lip, and under it the edge of the mouth opening

    layers = [
        dict(id="head", src="mouth/head.png", rect=(0, 0, SIZE, SIZE), cols=0, rows=0),
        dict(id="upperLip", src="mouth/head.png", crop=lip_crop, rect=lip_crop, cols=9, rows=20),
        dict(id="softPalate", src="mouth/velum.png", rect=velum_rect, cols=14, rows=13),
        dict(id="jaw", src="mouth/jaw.png", rect=jaw_rect, cols=28, rows=30),
        dict(id="tongue", src="mouth/tongue.png", rect=tongue_rect, cols=32, rows=29),
    ]
    os.makedirs(os.path.join(out_dir, "mouth"), exist_ok=True)
    cv2.imwrite(os.path.join(out_dir, "mouth", "head.png"), head)
    cv2.imwrite(os.path.join(out_dir, "mouth", "velum.png"), piece(pic, velum_alpha, velum_rect))
    cv2.imwrite(os.path.join(out_dir, "mouth", "jaw.png"), piece(wash(pic, weight), jaw_alpha, jaw_rect))
    cv2.imwrite(os.path.join(out_dir, "mouth", "tongue.png"), piece(pic, tongue_alpha, tongue_rect))
    if debug_dir:
        os.makedirs(debug_dir, exist_ok=True)
        cv2.imwrite(os.path.join(debug_dir, "picture.png"), pic)
        marks = pic.copy()
        for name, p in tongue.handles.items():
            cv2.circle(marks, (int(p[0]), int(p[1])), 4, (0, 255, 0), -1)
        for p in PINS:
            cv2.circle(marks, p, 4, (255, 0, 0), -1)
        cv2.polylines(marks, [VELUM_LINE.astype(np.int32)], False, (0, 255, 255), 1)
        cv2.polylines(marks, [VELUM_LINE_UP.astype(np.int32)], False, (255, 255, 0), 1)
        cv2.imwrite(os.path.join(debug_dir, "marks.png"), marks)

    grids = {l["id"]: grid(l["rect"], l["cols"], l["rows"]) for l in layers if l["cols"]}
    alphas = {"upperLip": np.ones((SIZE, SIZE)), "softPalate": velum_alpha, "jaw": jaw_alpha, "tongue": tongue_alpha}
    poses = {}
    report = []
    for name, spec in POSES.items():
        local = dict(spec["tongue"])
        for handle, target in spec["to"].items():
            rest = tongue.handles[handle][None]
            carried = rest + jaw_shift(rest, spec["jaw"])
            local[handle] = tuple((np.array(target, float) - carried[0]).tolist())
        if spec["turn"]:
            degrees, pivot, tip_to = spec["turn"]
            a = math.radians(degrees)
            pivot = np.array(pivot, float)
            pivot = pivot + jaw_shift(pivot[None], spec["jaw"])[0]
            turned = {}
            for handle in TIP_BLOCK:
                rest = tongue.handles[handle]
                carried = rest + jaw_shift(rest[None], spec["jaw"])[0]
                r = carried - pivot
                turned[handle] = (carried, pivot + np.array([r[0] * math.cos(a) - r[1] * math.sin(a),
                                                            r[0] * math.sin(a) + r[1] * math.cos(a)]))
            slide = np.array(tip_to, float) - turned["tip"][1]
            for handle, (carried, there) in turned.items():
                local[handle] = tuple((there + slide - carried).tolist())
        ulip = np.array(spec["ulip"], float)
        llip = np.array(spec["llip"], float)

        def lower(p):
            return (jaw_shift(p, spec["jaw"])
                    + lower_lip_weight(p, spec["llip_at"], spec["llip_spread"])[:, None] * llip)

        moves = {
            "upperLip": (upper_lip_weight(grids["upperLip"])[:, None] * ulip
                         + mouth_edge_weight(grids["upperLip"])[:, None] * np.array([llip[0], 0.0])),
            "softPalate": velum_shift(grids["softPalate"], spec["velum"]),
            "jaw": lower(grids["jaw"]),
            "tongue": tongue.shift(grids["tongue"], spec["jaw"], local),
        }
        mesh = {}
        for layer in layers:
            lid = layer["id"]
            if lid not in moves or not np.abs(moves[lid]).max() > 0.05:
                continue
            bad = folds(grids[lid], grids[lid] + moves[lid], layer["cols"], layer["rows"], alphas[lid])
            if bad:
                report.append(f"{name}.{lid}: {bad} folded triangles")
            mesh[lid] = [int(round(float(v) * K / MESH_UNIT)) for v in moves[lid].ravel()]

        entry = {"mesh": mesh}
        if spec["contact"]:
            entry["contact"] = [[round(x * K, 1), round(y * K, 1)] for x, y in spec["contact"]]
        if spec["gap"]:
            entry["gap"] = [[round(x * K, 1), round(y * K, 1)] for x, y in spec["gap"]]
        if spec["air"] == "auto":
            where = {n: tongue.handles[n] + jaw_shift(tongue.handles[n][None], spec["jaw"])[0]
                     + np.array(local.get(n, (0, 0)), float) for n in SURFACE}
            if debug_dir is not None:
                pass
            route = list(THROAT)
            back = where["back"]
            route.append(((back[0] + 42) / 2, back[1] + 10))
            if spec["via"]:
                route += list(spec["via"])
            else:
                for n in SURFACE[1:-1]:
                    x, y = where[n]
                    route.append((x, (y + roof(x, spec["velum"])) / 2))
                lower_tooth = LOWER_TOOTH + jaw_shift(LOWER_TOOTH[None], spec["jaw"])[0]
                route.append(tuple((UPPER_TOOTH + lower_tooth) / 2 + (0, 2)))
            top = UPPER_LIP_EDGE + upper_lip_weight(UPPER_LIP_EDGE[None])[0] * ulip
            bottom = LOWER_LIP_EDGE + lower(LOWER_LIP_EDGE[None])[0]
            between = (top + bottom) / 2
            route.append((between[0] + 4, between[1]))
            route.append((between[0] + 66, between[1] + 2))
            spaced = [route[0]]
            for point in route[1:-2]:
                if math.hypot(point[0] - spaced[-1][0], point[1] - spaced[-1][1]) >= 30:
                    spaced.append(point)
            route = spaced + route[-2:]
            entry["airPath"] = [[round(x * K, 1), round(y * K, 1)] for x, y in route]
        poses[name] = entry

    def view_rect(r):
        return [round(v * K, 2) for v in r]

    art = {
        "version": 2,
        "meshUnit": MESH_UNIT,
        "windows": {name: [round(v * K, 1) for v in rect] for name, rect in WINDOWS.items()},
        "source": "OpenStax College, Anatomy & Physiology, figure 'Anatomy of Nose-Pharynx-Mouth-Larynx' (CC BY 3.0)",
        "palette": PALETTE,
        "views": {"side": {
            "width": VIEW, "height": VIEW,
            "layers": [dict({"id": l["id"], "type": "image", "src": l["src"], "rect": view_rect(l["rect"]),
                             "cols": l["cols"], "rows": l["rows"]},
                            **({"crop": list(l["crop"])} if "crop" in l else {})) for l in layers],
            "labels": [{"text": t, "at": [round(a[0] * K, 1), round(a[1] * K, 1)],
                        "from": [round(f[0] * K, 1), round(f[1] * K, 1)],
                        "to": [round(b[0] * K, 1), round(b[1] * K, 1)], "align": al} for t, a, f, b, al in LABELS],
        }},
        "anchors": {
            "larynx": [round(LARYNX[0] * K, 1), round(LARYNX[1] * K, 1)],
            "mouthOut": [round(MOUTH_OUT[0] * K, 1), round(MOUTH_OUT[1] * K, 1)],
            "nasalPath": [[round(x * K, 1), round(y * K, 1)] for x, y in NASAL],
        },
        "poses": poses,
    }
    path = os.path.join(out_dir, "mouth.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(art, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    print(f"wrote {path} ({os.path.getsize(path)} bytes), {len(poses)} poses")
    for line in report:
        print("WARNING", line)
    return report


if __name__ == "__main__":
    args = [a for a in sys.argv[1:]]
    debug = None
    if "--debug" in args:
        i = args.index("--debug")
        debug = args[i + 1]
        del args[i:i + 2]
    target = args[0] if args else os.path.join(HERE, "..", "..", "app", "src", "main", "assets")
    build(target, debug)
