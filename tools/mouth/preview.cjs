#!/usr/bin/env node
/*
 * Draws a mouth-diagram data file (see SPEC.md) to PNG images, using the same
 * drawing rules as the app, so a design can be judged without building the app.
 *
 * Usage: node tools/mouth/preview.cjs <mouth.json> <out dir> [--blind <map.json>] [--only a,b]
 *   --only   comma list of: sheets, poses, phone, labels, indicators, blends, blind
 *   --blind  JSON object {poseName: code}; writes blind/<code>.png without captions
 */
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const POSES = [
  ['rest', 'neutral start'], ['lips', 'b p'], ['lipsNasal', 'm'], ['lipTeeth', 'f'],
  ['ridge', 'd t'], ['ridgeNasal', 'n, -n'], ['ridgeLateral', 'l'],
  ['velar', 'g k'], ['velarFric', 'h'], ['velarNasal', '-ng'],
  ['palatal', 'j q closure'], ['palatalFric', 'x'],
  ['retroflex', 'zh ch closure'], ['retroflexFric', 'sh r'],
  ['dental', 'z c closure'], ['dentalFric', 's'],
  ['a', 'a'], ['o', 'o'], ['e', 'e'], ['eFront', 'e of ie, ei'], ['schwa', 'e of en, eng'],
  ['i', 'i'], ['u', 'u'], ['yu', 'ü'], ['er', 'er'],
];

/** How each pose is usually shown: [air, voiced]. */
const TYPICAL = {
  rest: ['none', false], lips: ['none', false], lipsNasal: ['nasal', true], lipTeeth: ['oral', false],
  ridge: ['none', false], ridgeNasal: ['nasal', true], ridgeLateral: ['oral', true],
  velar: ['none', false], velarFric: ['oral', false], velarNasal: ['nasal', true],
  palatal: ['none', false], palatalFric: ['oral', false], retroflex: ['none', false],
  retroflexFric: ['oral', false], dental: ['none', false], dentalFric: ['oral', false],
  a: ['oral', true], o: ['oral', true], e: ['oral', true], eFront: ['oral', true], schwa: ['oral', true],
  i: ['oral', true], u: ['oral', true], yu: ['oral', true], er: ['oral', true],
};

/** Transitions the app animates; each is drawn as five in-between frames. */
const BLENDS = ('rest:lips lips:a rest:lipsNasal lipsNasal:a rest:lipTeeth lipTeeth:a rest:ridge ridge:a ' +
  'rest:ridgeNasal ridgeNasal:i rest:ridgeLateral ridgeLateral:a rest:velar velar:e velar:a rest:velarFric ' +
  'velarFric:a rest:palatal palatal:palatalFric palatalFric:i rest:retroflex retroflex:retroflexFric ' +
  'rest:retroflexFric rest:dental dental:dentalFric rest:dentalFric rest:a a:i a:u a:ridgeNasal a:velarNasal ' +
  'rest:o o:u rest:e e:er rest:schwa schwa:ridgeNasal schwa:velarNasal schwa:er rest:i i:a i:eFront eFront:i ' +
  'eFront:ridgeNasal i:ridgeNasal i:velarNasal i:o rest:u u:a u:o u:eFront u:schwa u:velarNasal rest:yu ' +
  'yu:eFront yu:ridgeNasal yu:velarNasal rest:eFront rest:er').split(' ').map(s => s.split(':'));

const r1 = n => Math.round(n * 10) / 10;
const colour = (art, c) => (!c ? 'none' : (art.palette[c] || c));

/** Uniform Catmull-Rom spline through the points, as cubic Beziers. */
function smoothPath(pts, closed) {
  const n = pts.length;
  if (n < 2) return '';
  const at = i => (closed ? pts[((i % n) + n) % n] : pts[Math.max(0, Math.min(n - 1, i))]);
  let d = `M${r1(pts[0][0])} ${r1(pts[0][1])}`;
  const segments = closed ? n : n - 1;
  for (let i = 0; i < segments; i++) {
    const p0 = at(i - 1), p1 = at(i), p2 = at(i + 1), p3 = at(i + 2);
    d += ` C${r1(p1[0] + (p2[0] - p0[0]) / 6)} ${r1(p1[1] + (p2[1] - p0[1]) / 6)}` +
      ` ${r1(p2[0] - (p3[0] - p1[0]) / 6)} ${r1(p2[1] - (p3[1] - p1[1]) / 6)} ${r1(p2[0])} ${r1(p2[1])}`;
  }
  return closed ? d + ' Z' : d;
}

function polyPath(pts, closed) {
  if (pts.length < 2) return '';
  return 'M' + pts.map(p => `${r1(p[0])} ${r1(p[1])}`).join(' L') + (closed ? ' Z' : '');
}

/** Points of a shape layer in a pose, falling back to the rest pose. */
function pointsOf(art, pose, id) {
  const p = art.poses[pose];
  return (p && p.points && p.points[id]) || art.poses.rest.points[id];
}

/** Points of every shape layer, blended from pose a to pose b at t (0..1). */
function blended(art, a, b, t) {
  const out = {};
  for (const view of Object.values(art.views)) {
    for (const layer of view.layers) {
      if (layer.type !== 'shape') continue;
      const pa = pointsOf(art, a, layer.id), pb = pointsOf(art, b, layer.id);
      out[layer.id] = pa.map((p, i) => [p[0] + (pb[i][0] - p[0]) * t, p[1] + (pb[i][1] - p[1]) * t]);
    }
  }
  return out;
}

function layerSvg(art, layer, points) {
  const d = layer.type === 'path' ? layer.d
    : (layer.smooth === false ? polyPath(points[layer.id], layer.closed !== false)
      : smoothPath(points[layer.id], layer.closed !== false));
  const dash = layer.dash ? ` stroke-dasharray="${layer.dash.join(' ')}"` : '';
  return `<path d="${d}" fill="${colour(art, layer.fill)}" stroke="${colour(art, layer.stroke)}"` +
    ` stroke-width="${layer.stroke ? (layer.strokeWidth || 0) : 0}" opacity="${layer.opacity == null ? 1 : layer.opacity}"` +
    ` stroke-linecap="round" stroke-linejoin="round"${dash}/>`;
}

function arcPoints(cx, cy, r, from, to, steps) {
  const out = [];
  for (let i = 0; i <= steps; i++) {
    const a = (from + (to - from) * i / steps) * Math.PI / 180;
    out.push([cx + r * Math.cos(a), cy + r * Math.sin(a)]);
  }
  return out;
}

/** A dashed arrow along a route of the breath. */
function arrowSvg(art, pts) {
  if (!pts || pts.length < 2) return '';
  const air = colour(art, 'air');
  const tip = pts[pts.length - 1], prev = pts[pts.length - 2];
  const len = Math.hypot(tip[0] - prev[0], tip[1] - prev[1]) || 1;
  const ux = (tip[0] - prev[0]) / len, uy = (tip[1] - prev[1]) / len;
  const base = [tip[0] - ux * 34, tip[1] - uy * 34];
  const head = [[tip[0] + ux * 8, tip[1] + uy * 8], [base[0] - uy * 19, base[1] + ux * 19], [base[0] + uy * 19, base[1] - ux * 19]];
  return `<path d="${smoothPath(pts, false)}" fill="none" stroke="#FFFFFF" stroke-width="18" stroke-linecap="round" opacity="0.55"/>` +
    `<path d="${smoothPath(pts, false)}" fill="none" stroke="${air}" stroke-width="10" stroke-linecap="round" stroke-dasharray="22 18"/>` +
    `<path d="${polyPath(head, true)}" fill="${air}" stroke="#FFFFFF" stroke-width="3" stroke-linejoin="round"/>`;
}

/** Contact dots, gap rings, breath arrows, puff and voice marks for the side view. */
function overlaySvg(art, pose, air, voiced) {
  const p = art.poses[pose] || {};
  let s = '';
  if (air === 'oral' || air === 'puff') s += arrowSvg(art, p.airPath);
  if (air === 'nasal') s += arrowSvg(art, art.anchors.nasalPath);
  if (air === 'puff') {
    const [x, y] = art.anchors.mouthOut;
    for (const r of [30, 54, 78]) {
      s += `<path d="${polyPath(arcPoints(x, y, r, 140, 220, 12), false)}" fill="none" stroke="${colour(art, 'air')}"` +
        ` stroke-width="10" stroke-linecap="round" stroke-linejoin="round"/>`;
    }
  }
  if (voiced) {
    const [x, y] = art.anchors.larynx;
    const voice = colour(art, 'voice');
    s += `<circle cx="${x}" cy="${y}" r="8" fill="${voice}"/>`;
    for (const r of [22, 38]) {
      for (const [from, to] of [[-40, 40], [140, 220]]) {
        s += `<path d="${polyPath(arcPoints(x, y, r, from, to, 8), false)}" fill="none" stroke="${voice}"` +
          ` stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>`;
      }
    }
  }
  for (const [x, y] of p.gap || []) {
    s += `<circle cx="${x}" cy="${y}" r="17" fill="#FFFFFF" fill-opacity="0.35" stroke="${colour(art, 'contact')}" stroke-width="7"/>`;
  }
  for (const [x, y] of p.contact || []) {
    s += `<circle cx="${x}" cy="${y}" r="17" fill="${colour(art, 'contact')}" stroke="#FFFFFF" stroke-width="5"/>`;
  }
  return s;
}

function labelsSvg(art, view) {
  const ink = colour(art, 'label');
  let s = '';
  for (const l of view.labels || []) {
    const from = l.from || l.at;
    s += `<line x1="${from[0]}" y1="${from[1]}" x2="${l.to[0]}" y2="${l.to[1]}" stroke="${ink}" stroke-width="3" stroke-linecap="round"/>` +
      `<circle cx="${l.to[0]}" cy="${l.to[1]}" r="6" fill="${ink}"/>`;
  }
  for (const l of view.labels || []) {
    s += `<text x="${l.at[0]}" y="${l.at[1]}" font-family="Roboto, Arial, sans-serif" font-size="30" font-weight="600"` +
      ` text-anchor="${l.align || 'middle'}" dominant-baseline="middle" fill="${ink}" stroke="#FFFFFF" stroke-width="7"` +
      ` paint-order="stroke" stroke-linejoin="round">${l.text}</text>`;
  }
  return s;
}

/** One view as an <svg> element of the given pixel width. */
function viewSvg(art, name, points, pixelWidth, extra) {
  const view = art.views[name];
  const h = Math.round(pixelWidth * view.height / view.width);
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${pixelWidth}" height="${h}" viewBox="0 0 ${view.width} ${view.height}">` +
    view.layers.map(l => layerSvg(art, l, points)).join('') + (extra || '') + '</svg>';
}

/** Side view with overlays and the front view beside it. */
function poseHtml(art, pose, sideWidth, opts) {
  const o = opts || {};
  const [air, voiced] = o.flags || TYPICAL[pose] || ['none', false];
  const points = blended(art, pose, pose, 0);
  const extra = overlaySvg(art, pose, air, voiced) + (o.labels ? labelsSvg(art, art.views.side) : '');
  const front = o.noFront ? '' : viewSvg(art, 'front', points, Math.round(sideWidth * 0.42));
  return `<div class="pose"><div class="views">${viewSvg(art, 'side', points, sideWidth, extra)}${front}</div>` +
    (o.caption ? `<div class="cap">${o.caption}</div>` : '') + '</div>';
}

const CSS = `body{margin:0;background:#fff;font-family:Roboto,Arial,sans-serif;color:#1f2333}
#root{display:inline-block;padding:12px}
.grid{display:grid;gap:14px}.pose{display:flex;flex-direction:column;align-items:center}
.views{display:flex;align-items:center;gap:8px}.views svg{border-radius:14px}
.cap{font-size:22px;font-weight:700;margin-top:4px}.cap small{font-weight:400;color:#5b6275}
.strip{display:flex;gap:6px;align-items:center}.strip .t{font-size:18px;font-weight:700;width:190px}`;

async function shoot(page, html, file) {
  await page.setContent(`<html><head><style>${CSS}</style></head><body><div id="root">${html}</div></body></html>`);
  await page.locator('#root').screenshot({ path: file });
}

async function main() {
  const args = process.argv.slice(2);
  if (args.length < 2) {
    console.error('usage: preview.cjs <mouth.json> <out dir> [--blind map.json] [--only a,b]');
    process.exit(2);
  }
  const art = JSON.parse(fs.readFileSync(args[0], 'utf8'));
  const out = args[1];
  const blindAt = args.indexOf('--blind');
  const onlyAt = args.indexOf('--only');
  const only = onlyAt >= 0 ? args[onlyAt + 1].split(',') : null;
  const want = k => !only || only.includes(k);
  fs.mkdirSync(out, { recursive: true });
  const missing = POSES.map(p => p[0]).filter(n => !art.poses[n]);
  if (missing.length) console.error('missing poses (drawn as rest): ' + missing.join(', '));
  const names = POSES.map(p => p[0]).filter(n => art.poses[n]);
  const usedFor = Object.fromEntries(POSES);

  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 2000, height: 1600 }, deviceScaleFactor: 1 });
  const written = [];

  if (want('sheets')) {
    for (let i = 0; i < names.length; i += 6) {
      const cells = names.slice(i, i + 6).map(n => poseHtml(art, n, 560, { caption: `${n} <small>(${usedFor[n]})</small>` }));
      const file = path.join(out, `sheet-${i / 6 + 1}.png`);
      await shoot(page, `<div class="grid" style="grid-template-columns:repeat(2,auto)">${cells.join('')}</div>`, file);
      written.push(file);
    }
  }
  if (want('poses')) {
    for (const n of names) {
      const file = path.join(out, `pose-${n}.png`);
      await shoot(page, poseHtml(art, n, 1000, { caption: `${n} <small>(${usedFor[n]})</small>` }), file);
      written.push(file);
    }
  }
  if (want('phone')) {
    const cells = names.map(n => poseHtml(art, n, 300, { caption: `<span style="font-size:15px">${n}</span>` }));
    const file = path.join(out, 'phone.png');
    await shoot(page, `<div class="grid" style="grid-template-columns:repeat(4,auto)">${cells.join('')}</div>`, file);
    written.push(file);
  }
  if (want('labels')) {
    const file = path.join(out, 'labels.png');
    await shoot(page, poseHtml(art, 'rest', 1100, { labels: true, flags: ['none', false], noFront: true }), file);
    written.push(file);
  }
  if (want('indicators')) {
    const combos = [['lips', 'none', false, 'b: blocked, no voice'], ['a', 'puff', false, 'p t k: puff of air'],
      ['a', 'oral', true, 'vowel: voiced, air through the mouth'], ['lipsNasal', 'nasal', true, 'm: air through the nose'],
      ['ridgeNasal', 'nasal', true, 'n'], ['velarNasal', 'nasal', true, 'ng'],
      ['dentalFric', 'oral', false, 's: hiss, no voice'], ['retroflexFric', 'oral', true, 'r: voiced']];
    const cells = combos.filter(c => art.poses[c[0]]).map(c => poseHtml(art, c[0], 520,
      { flags: [c[1], c[2]], caption: `${c[0]} <small>${c[3]}</small>`, noFront: true }));
    const file = path.join(out, 'indicators.png');
    await shoot(page, `<div class="grid" style="grid-template-columns:repeat(2,auto)">${cells.join('')}</div>`, file);
    written.push(file);
  }
  if (want('blends')) {
    const usable = BLENDS.filter(([a, b]) => art.poses[a] && art.poses[b]);
    for (let i = 0; i < usable.length; i += 6) {
      const rows = usable.slice(i, i + 6).map(([a, b]) => {
        const frames = [0, 0.25, 0.5, 0.75, 1].map(t => viewSvg(art, 'side', blended(art, a, b, t), 300));
        return `<div class="strip"><div class="t">${a} → ${b}</div>${frames.join('')}</div>`;
      });
      const file = path.join(out, `blend-${i / 6 + 1}.png`);
      await shoot(page, `<div class="grid">${rows.join('')}</div>`, file);
      written.push(file);
    }
  }
  if (blindAt >= 0 && want('blind')) {
    const map = JSON.parse(fs.readFileSync(args[blindAt + 1], 'utf8'));
    fs.mkdirSync(path.join(out, 'blind'), { recursive: true });
    for (const n of names) {
      if (!map[n]) continue;
      await shoot(page, poseHtml(art, n, 900), path.join(out, 'blind', `${map[n]}.png`));
    }
    written.push(path.join(out, 'blind') + '/*.png');
  }
  await browser.close();
  console.log(`wrote ${written.length} files to ${out}`);
}

main().catch(e => { console.error(e); process.exit(1); });
