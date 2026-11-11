# Mouth diagrams: data format and pose list

The app shows, for every Mandarin sound, where the lips, teeth, tongue and soft
palate go. The drawings are **data**, not code: one JSON file
(`app/src/main/assets/mouth.json`) holds the artwork and every mouth position
("pose"). The app draws it and animates smoothly from pose to pose.
`tools/mouth/preview.cjs` draws the same file to PNG with the same rules, so a
design can be judged without building the app.

Two views are drawn for each pose:

- **side**: a section through the middle of the head, face pointing LEFT. It
  shows lips, teeth, the ridge behind the upper teeth, hard palate, soft palate
  (velum) with uvula, nasal cavity, tongue, throat and voice box.
- **front**: the mouth seen from the front: lip shape, how far the mouth is
  open, visible teeth and tongue.

## File format

```json
{
  "version": 1,
  "palette": { "skin": "#FFD9C4", "tongue": "#FF7B8A", "...": "#RRGGBB" },
  "views": {
    "side":  { "width": 1000, "height": 800, "layers": [ ... ], "labels": [ ... ] },
    "front": { "width": 500,  "height": 400, "layers": [ ... ] }
  },
  "anchors": {
    "larynx":    [x, y],
    "mouthOut":  [x, y],
    "nasalPath": [[x, y], ...]
  },
  "poses": {
    "rest": {
      "points":  { "<shape layer id>": [[x, y], ...], ... },
      "contact": [[x, y], ...],
      "gap":     [[x, y], ...],
      "airPath": [[x, y], ...]
    },
    "lips": { "points": { ...only the layers that differ from rest... }, "contact": [[x, y]] }
  }
}
```

Coordinates are in the view's own units, origin top left, y pointing down. The
side view is always 1000 x 800 and the front view 500 x 400. Artwork may extend
past the edges of a view; both renderers clip it to the view.

### palette

Names to `#RRGGBB` colours. Layers refer to colours by palette name (a literal
`#RRGGBB` is also accepted). Four names are required because the renderer uses
them for the overlays: `contact`, `air`, `voice`, `label`.

### layers

Layers are drawn in the order listed. Layer ids are unique across both views.
Two types:

```json
{ "id": "head", "type": "path", "d": "M 120 80 C ... Z",
  "fill": "skin", "stroke": "outline", "strokeWidth": 6, "opacity": 1 }

{ "id": "tongue", "type": "shape", "closed": true, "smooth": true,
  "fill": "tongue", "stroke": "tongueEdge", "strokeWidth": 5, "opacity": 1, "dash": [12, 8] }
```

- `path`: artwork that never moves. `d` is SVG path data (commands
  `M L H V C S Q T A Z`, upper or lower case). Sub-paths fill with the non-zero
  rule, so a hole needs the opposite winding direction.
- `shape`: artwork that moves. Its outline is a list of points taken from the
  current pose. `closed` (default true) joins the last point to the first.
  `smooth` (default true) draws a curve through the points; `false` joins them
  with straight lines.
- `fill`, `stroke`: palette name, or omitted/null for none. `strokeWidth` in
  view units. `opacity` 0..1 applies to the whole layer. `dash` is optional.
  Strokes always have round caps and joins.

The smooth curve is a uniform Catmull-Rom spline: between points P1 and P2 (with
neighbours P0 and P3) it is the cubic Bezier with control points
`P1 + (P2 - P0) / 6` and `P2 - (P3 - P1) / 6`. For an open curve the end points
are repeated as their own neighbours. Space points fairly evenly; very uneven
spacing makes the curve bulge.

### poses

`rest` must give points for **every** shape layer. Any other pose lists only the
layers that differ; the rest fall back to `rest`. **A layer must have the same
number of points in every pose**, because the app moves point *i* of one pose to
point *i* of the next. Keep corresponding points on corresponding parts (point 0
is always the tongue tip, and so on) or the shape will twist in mid-animation.

Optional per pose, side view only:

- `contact`: where two parts touch and block the air. Drawn as a solid dot.
- `gap`: the narrowest point where air squeezes through. Drawn as a ring.
- `airPath`: the route of the breath through the mouth, from the throat to just
  in front of the lips. Drawn as a dashed arrow when the sound has air flowing
  through the mouth. Give it for every pose that can have oral airflow.

### anchors (side view)

- `larynx`: centre of the voice box. Vibration marks are drawn here when the
  sound is voiced.
- `mouthOut`: a point just in front of the lips. A burst is drawn here for the
  puff of air of p, t, k, q, ch, c.
- `nasalPath`: the route of the breath through the nose, from the throat up
  behind the soft palate, through the nasal cavity and out of the nostril.
  Drawn as a dashed arrow for m, n, ng.

### labels (side view, optional but wanted)

```json
{ "text": "ridge", "at": [330, 150], "to": [352, 318], "align": "middle" }
```

Names the parts on the `rest` pose: text centred (or `start` / `end` aligned) at
`at`, with a line to `to`. Wanted labels: nose cavity, upper lip, lower lip,
upper teeth, lower teeth, ridge, hard palate, soft palate, tongue tip, tongue
front, tongue back, throat, voice box.

## Overlays drawn by the renderer

A step of an animation is a pose plus two facts about the sound:
`air` (`none`, `oral`, `nasal` or `puff`) and `voiced` (true or false).

| Overlay | When | Drawn as |
| --- | --- | --- |
| contact dots, gap rings | always | palette `contact` |
| dashed arrow along `airPath` | air is `oral` or `puff` | palette `air` |
| burst at `mouthOut` | air is `puff` | palette `air` |
| dashed arrow along `nasalPath` | air is `nasal` | palette `air` |
| vibration marks at `larynx` | voiced | palette `voice` |

## The poses

Every pose below is required, with exactly these names. "Front" says what the
front view must show.

### Consonant positions

| Pose | Sounds | What it must show |
| --- | --- | --- |
| `rest` | neutral start | Lips barely apart, teeth slightly apart. Tongue relaxed: tip resting behind the lower front teeth, body gently domed in the middle of the mouth, touching nothing above. Soft palate raised against the back wall, closing off the nose. Front: relaxed lips, a thin dark slit. |
| `lips` | b, p | Both lips pressed firmly together. Teeth slightly apart behind them. Tongue low and relaxed. Soft palate raised. `contact` at the lips. Front: lips closed in a firm line. |
| `lipsNasal` | m | Exactly like `lips`, but the soft palate hangs down so the passage to the nose is open. Front: lips closed. |
| `lipTeeth` | f | Lower lip pulled up and slightly in, its inner edge touching the tips of the upper front teeth. Upper lip lifted a little. `gap` where lip meets teeth. Front: upper teeth visible, resting on the lower lip. |
| `ridge` | d, t | Tongue tip and its front edge pressed against the ridge just behind the upper front teeth (touching the back of the gum line). Tongue body at middle height. Jaw nearly closed. Soft palate raised. `contact` at the ridge. Front: lips slightly open, tongue tip may be seen behind the upper teeth. |
| `ridgeNasal` | n, final -n | Like `ridge`, with the soft palate lowered. |
| `ridgeLateral` | l | Tip on the ridge as in `ridge`, but the body of the tongue is lower and flatter because its sides are dropped to let air pass. `contact` at the ridge; `airPath` runs over the lowered tongue. |
| `velar` | g, k | Back of the tongue raised and pressed against the soft palate. Tongue tip down behind the lower teeth. Jaw slightly open. Soft palate raised. `contact` at the soft palate. Front: lips slightly open, neutral. |
| `velarFric` | h | Like `velar`, but a narrow slit remains between the back of the tongue and the soft palate. `gap` there. |
| `velarNasal` | final -ng | Back of the tongue against the soft palate, and the soft palate lowered onto it, so the mouth is blocked and the nose is open. `contact` there. |
| `palatal` | j, q (closure) | Tongue TIP DOWN, touching the back of the lower front teeth. The flat front of the tongue (not the tip) is raised and pressed over a wide area against the front of the hard palate, just behind the ridge. Lips spread, not rounded. Jaw nearly closed. `contact` on the front of the hard palate. Front: lips spread in a slight smile, teeth close together. |
| `palatalFric` | x, release of j and q | Same shape as `palatal` with a narrow channel between the tongue front and the hard palate. `gap` there. |
| `retroflex` | zh, ch (closure) | Tongue tip raised and curled slightly back, touching the roof of the mouth just BEHIND the ridge. The tongue is hollowed behind the tip (spoon shape) and its back is a little raised. Lips neutral, pushed forward a little. Jaw slightly open. `contact` behind the ridge. Front: lips slightly open and a little squared. |
| `retroflexFric` | sh, r, release of zh and ch, the vowel of zhi chi shi ri | Same curl as `retroflex`, tip close to the same spot without touching. `gap` there. |
| `dental` | z, c (closure) | Tongue tip and blade flat against the back of the upper front teeth, further forward than `ridge`. Teeth almost closed. Lips slightly spread. `contact` at the back of the upper teeth. Front: lips slightly spread, teeth nearly together. |
| `dentalFric` | s, release of z and c, the vowel of zi ci si | Same as `dental` with a narrow slit between the tongue tip and the back of the upper teeth. `gap` there. |

### Vowel positions

| Pose | Sound | What it must show |
| --- | --- | --- |
| `a` | a | Jaw dropped wide. Tongue low and flat, tip behind the lower teeth. Lips open, not rounded. Front: tall open mouth, tongue lying low. |
| `o` | o | Lips rounded into a medium circle. Jaw half open. Tongue pulled back, its back raised to middle height. Front: round opening. |
| `e` | e as in 饿 | Tongue as in `o` (pulled back, back raised to middle-high) but lips relaxed and unrounded. Jaw half open. Front: relaxed oval opening. |
| `eFront` | e in ie, üe, ei, ian | Tongue pushed forward, its front raised to middle height, tip behind the lower teeth. Lips spread. Jaw half open. Front: wide, medium-open mouth. |
| `schwa` | e in en, eng, un | Everything relaxed and central: tongue body in the middle of the mouth at middle height, lips neutral, jaw a little open. |
| `i` | i | Lips spread wide as in a smile. Jaw nearly closed. Front of the tongue raised high, close to the hard palate without touching; tip down behind the lower teeth. Front: wide flat opening, teeth close together. |
| `u` | u | Lips rounded tightly and pushed forward, leaving a small hole. Jaw nearly closed. Tongue pulled back with its back raised high towards the soft palate. Front: small round hole. |
| `yu` | ü | Tongue exactly as in `i` (front high), lips exactly as in `u` (tight, round, pushed forward). |
| `er` | er | Tongue body central at middle height, tip curled up and back without touching anything. Jaw half open. Lips neutral. |

Airflow: every vowel pose and every fricative pose (`lipTeeth`, `velarFric`,
`palatalFric`, `retroflexFric`, `dentalFric`), plus `ridgeLateral` and `rest`,
needs an `airPath`. Closure poses need a `contact` and no `airPath`.

## Where the artwork comes from

`build_mouth.py` writes the file. The fixed anatomy (head, palate, teeth, throat)
is traced by `trace_base.py` from "Midsagittal diagram unlabeled" by Wugapodes on
Wikimedia Commons, which is in the public domain (CC0); the traced outlines are in
`base_shapes.json`. The generator adds the parts that move: the jaw swings on a
hinge, lips and soft palate bend, and the tongue is a curve through twelve control
points set per pose.

## Checking a design

```
python3 tools/mouth/build_mouth.py app/src/main/assets/mouth.json   # regenerate
python3 tools/mouth/check_mouth.py <mouth.json>                      # structure
node tools/mouth/preview.cjs <mouth.json> <out dir>                  # pictures
```

The preview writes:

- `sheet-N.png`: all poses, six per sheet, side and front view with the usual overlays
- `pose-<name>.png`: one pose, large
- `phone.png`: every pose at the size it has on a phone
- `labels.png`: the rest pose with part names
- `indicators.png`: the air, puff, nasal and voice overlays
- `blend-N.png`: in-between frames for the transitions the app animates
