# Mouth diagrams: the picture, the data format and the pose list

The app shows, for every Mandarin sound, where the lips, teeth, tongue and soft
palate go. The diagrams are **one real textbook picture that moves**: a side
view of a head cut down the middle, from OpenStax *Anatomy & Physiology*
(CC BY 3.0; see `source/README.md`). Nothing is drawn by hand. `build_mouth.py`
takes the picture apart and writes

- `app/src/main/assets/mouth/*.png`: the head, and the pieces that move (lower
  jaw, tongue, soft palate), cut out of the picture;
- `app/src/main/assets/mouth.json`: where the pieces sit and how each one is
  bent in every mouth position ("pose"), plus the markers drawn on top.

The app draws the file and moves smoothly from pose to pose. `preview.py` draws
the same file to PNG with the same rules, so a change can be judged without
building the app.

**Kept simple on purpose.** What matters for speaking is the mouth: lips, teeth,
tongue and the roof of the mouth. So the app shows a close view of the mouth
(the `mouth` window), the two tonsils are painted out, and everything that is
not mouth or skin (nose cavity, spine, voice box) is washed out. On the picture
there is only a dot where two parts touch, a ring at a narrow gap and one short
arrow where the breath comes out. What the voice is doing is said in words.

## File format

```json
{
  "version": 2,
  "source": "credit for the picture",
  "meshUnit": 0.25,
  "windows": { "mouth": [x, y, width, height], "names": [x, y, width, height] },
  "palette": { "contact": "#FFB703", "air": "#1C7ED6", "voice": "#7C4DFF", "label": "#1F2333" },
  "views": { "side": { "width": 1000, "height": 1000, "layers": [ ... ], "labels": [ ... ] } },
  "anchors": { "larynx": [x, y], "mouthOut": [x, y], "nasalPath": [[x, y], ...] },
  "poses": {
    "rest": {},
    "a": { "mesh": { "jaw": [dx, dy, dx, dy, ...], "tongue": [...] }, "airPath": [[x, y], ...] },
    "ridge": { "mesh": { ... }, "contact": [[x, y]] }
  }
}
```

Coordinates are in view units, origin top left, y pointing down. The view is
1000 x 1000. The head faces right: nose and lips on the right, throat on the left.

### layers

Layers are drawn in the order listed. Every layer of the shipped file is an
`image` layer:

```json
{ "id": "head",   "type": "image", "src": "mouth/head.png", "rect": [0, 0, 1000, 1000], "cols": 0, "rows": 0 }
{ "id": "tongue", "type": "image", "src": "mouth/tongue.png", "rect": [149, 376, 512, 460], "cols": 32, "rows": 29 }
{ "id": "upperLip", "type": "image", "src": "mouth/head.png", "crop": [626, 330, 120, 162], "rect": [...], "cols": 8, "rows": 10 }
```

- `src`: a PNG inside the assets folder. `crop` (optional) uses only part of it:
  x, y, width, height in the PNG's own pixels.
- `rect`: where the picture sits in the view when nothing has moved.
- `cols`, `rows`: a grid of that many cells is laid over the picture. A pose can
  move the grid's corners, and the picture is stretched over the moved grid
  (`Canvas.drawBitmapMesh` on Android: each cell is two triangles, split from its
  top-left to its bottom-right corner). `0` means the layer never moves.

The layers, bottom to top:

| Layer | What it is |
| --- | --- |
| `head` | The whole picture with the moving parts taken out and what is behind them painted in: the dark inside of the mouth, the wall of the throat, open air in front of the chin. |
| `upperLip` | The patch of `head` around the upper lip, so that the lip can lift, round and spread. |
| `softPalate` | The soft palate with its tip. It hangs in the picture; it lifts to shut off the nose. |
| `jaw` | Everything that swings with the lower jaw: lower lip, chin, lower teeth, jaw bone, the root of the tongue. |
| `tongue` | The free part of the tongue. It fades out into the `jaw` piece where the tongue is rooted. |

The format also still accepts line-art layers (`path` with SVG path data,
`shape` with per-pose outline points in `poses.<name>.points`); the shipped file
has none.

### poses

- `mesh`: per moving layer, how far each grid corner has moved from its place in
  `rect`: `dx, dy` for every corner, row by row from the top left,
  `(cols + 1) * (rows + 1) * 2` whole numbers in units of `meshUnit` view units.
  A layer that is not listed has not moved.
- `rest` has no `mesh` at all: **the relaxed mouth is the picture, untouched**.
- `contact`: where two parts touch and block the air. Drawn as a solid dot.
- `gap`: the narrowest point where air squeezes through. Drawn as a ring.
- `airPath`: the route of the breath through the mouth, from the voice box to
  just in front of the lips. Only its last stretch, out between the lips, is
  drawn, as a short arrow, when air flows through the mouth. Needed by every
  pose that can have breath flowing through the mouth.

The app moves from one pose to the next by sliding every grid corner in a
straight line, so a pose must not fold a grid over itself. `build_mouth.py`
warns when a visible triangle of a grid is turned inside out or squashed flat.

### windows

The part of the view the app shows. `mouth` is the normal close view, from the
tip of the nose to the chin; `names` is the same with room on the right for the
names of the parts. Markers and names keep the same size on screen whatever
the window.

### anchors

- `larynx`: the voice box (kept for reference; nothing is drawn there).
- `mouthOut`: a point just in front of the lips. Arcs are drawn here for the
  puff of air of p, t, k, q, ch, c.
- `nasalPath`: the route of the breath through the nose. Only its last stretch,
  out of the nostril, is drawn, as a short arrow, for m, n, ng.

### labels

```json
{ "text": "ridge", "at": [809, 413], "from": [802, 413], "to": [660, 431], "align": "start" }
```

Names of the parts, shown on the relaxed mouth: text at `at` (`start`, `middle`
or `end` aligned), with a pointer from `from` to `to`.

## Markers drawn by the renderer

A step of an animation is a pose plus two facts about the sound:
`air` (`none`, `oral`, `nasal` or `puff`) and `voiced` (true or false).

| Marker | When | Colour |
| --- | --- | --- |
| solid dot at each `contact`, ring at each `gap` | always | palette `contact` |
| short arrow out between the lips | air is `oral` or `puff` | palette `air` |
| arcs at `mouthOut` | air is `puff` | palette `air` |
| short arrow out of the nostril | air is `nasal` | palette `air` |

The voice is not drawn. The app writes "Voice on" or "No voice" under the picture
(in palette `voice`), next to what the breath is doing.

## The poses

Every pose below is required, with exactly these names.

### Consonant positions

| Pose | Sounds | What it shows |
| --- | --- | --- |
| `rest` | start of every animation | The picture as published: lips together, teeth a little apart, tongue relaxed, soft palate hanging so that we breathe through the nose. |
| `lips` | b, p | Lips together, soft palate lifted. `contact` at the lips. |
| `lipsNasal` | m | Lips together, soft palate hanging: the same as `rest`. `contact` at the lips. |
| `lipTeeth` | f | Lower lip tucked up under the edge of the upper front teeth. `gap` there. |
| `ridge` | d, t | Tongue tip pressed on the ridge just behind the upper front teeth. `contact` there. |
| `ridgeNasal` | n, final -n | As `ridge`, soft palate hanging. |
| `ridgeLateral` | l | Tip on the ridge, the body of the tongue lower so that air passes its sides. |
| `velar` | g, k | Back of the tongue pressed against the lifted soft palate, tip down. `contact` there. |
| `velarFric` | h | As `velar` with a narrow slit. `gap` there. |
| `velarNasal` | final -ng | Back of the tongue against the hanging soft palate: mouth blocked, nose open. |
| `palatal` | j, q (closure) | Tip down behind the lower teeth; the flat front of the tongue pressed on the hard palate; lips spread. |
| `palatalFric` | x, release of j and q | As `palatal` with a narrow channel. `gap` there. |
| `retroflex` | zh, ch (closure) | Tongue tip turned up, touching the roof of the mouth just behind the ridge; hollow behind the tip. |
| `retroflexFric` | sh, r, release of zh and ch | Same turn of the tip, close to the same spot without touching. `gap` there. |
| `dental` | z, c (closure) | Tongue tip against the back of the upper front teeth, teeth nearly together. |
| `dentalFric` | s, release of z and c | As `dental` with a narrow slit. `gap` there. |

### Vowel positions

| Pose | Sound | What it shows |
| --- | --- | --- |
| `a` | a | Jaw dropped wide, tongue low and flat. |
| `o` | o | Lips rounded and pushed forward, jaw half open, tongue pulled back. |
| `e` | e as in 饿 | Tongue as in `o`, lips relaxed. |
| `eFront` | e in ie, üe, ei, ian | Front of the tongue raised to middle height, lips spread. |
| `schwa` | e in en, eng, un | Everything relaxed and central, jaw a little open. |
| `i` | i | Front of the tongue high, close to the hard palate; lips spread; jaw nearly closed. |
| `u` | u | Lips rounded and pushed forward; back of the tongue high. |
| `yu` | ü | Tongue as in `i`, lips as in `u`. |
| `er` | er | Tongue tip turned up and back without touching anything. |

The soft palate hangs (nose open) in `rest`, `lipsNasal`, `ridgeNasal` and
`velarNasal`, and is lifted (nose shut) in every other pose.

## Changing the diagrams

Poses are the `POSES` table in `build_mouth.py`: a jaw angle, lip movements,
whether the soft palate is lifted, and where the handles on the tongue go.

```
python3 tools/mouth/build_mouth.py                      # rewrite the assets
python3 tools/mouth/check_mouth.py app/src/main/assets  # structure
python3 tools/mouth/preview.py app/src/main/assets out  # pictures
```

The preview writes `sheet-N.png` (all poses with their usual markers),
`pose-<name>.png` (one pose, large), `phone.png` (every pose at phone size),
`labels.png` (the relaxed mouth with part names) and `blend-N.png` (in-between
frames of the movements the app animates), all in the `mouth` window unless
`--window all` is given. Needs Python 3 with `numpy` and
`opencv-python`.
