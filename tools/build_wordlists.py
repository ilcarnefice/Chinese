#!/usr/bin/env python3
"""Builds app/src/main/assets/words/hsk{1..6}.tsv and chars.tsv from open data.

Input: an unpacked copy of the `chinese-lexicon` npm package (ISC), which bundles
  - statistics/hsk.js    : the HSK 2.0 word lists, levels 1-6
  - dictionary/cedict.js : CC-CEDICT (CC BY-SA 4.0, https://cc-cedict.org)
  - statistics/pinyinFrequency.js : reading frequencies from the Unihan database

The files are only parsed as text; nothing from the package is executed.

Usage: python3 tools/build_wordlists.py <path-to-chinese-lexicon/package> <out-dir>

Output line format (tab separated):
  simplified  traditional  numbered-pinyin  definition; definition; ...
"""
import json
import os
import re
import sys

EXTRA_WORDS = 6000

TONES = {"a": "āáǎà", "e": "ēéěè", "i": "īíǐì", "o": "ōóǒò", "u": "ūúǔù", "ü": "ǖǘǚǜ"}

# Readings picked by hand where the automatic choice is not the one HSK teaches.
OVERRIDES = {
    "了": "le5", "的": "de5", "得": "de5", "地": "de5", "着": "zhe5", "还": "hai2",
    "长": "chang2", "都": "dou1", "行": "xing2", "为": "wei4", "只": "zhi3",
    "种": "zhong3", "觉得": "jue2 de5", "便宜": "pian2 yi5", "教": "jiao1",
    "差": "cha4", "倒": "dao4", "当": "dang1", "重": "zhong4", "省": "sheng3",
    "数": "shu4", "朝": "chao2", "系": "xi4", "空": "kong1", "干": "gan4",
    "假": "jia3", "要": "yao4", "和": "he2", "过": "guo4", "会": "hui4",
    "发": "fa1", "少": "shao3", "好": "hao3", "乐": "le4", "藏": "cang2",
}


def mark(syl):
    """ni3 -> nǐ (used only to look up reading frequencies)."""
    syl = syl.lower().replace("u:", "ü").replace("v", "ü")
    m = re.match(r"^([a-zü]+)([1-5])$", syl)
    if not m:
        return syl
    body, tone = m.group(1), int(m.group(2))
    if tone == 5:
        return body
    for v in ("a", "e"):
        if v in body:
            return body.replace(v, TONES[v][tone - 1], 1)
    if "ou" in body:
        return body.replace("o", TONES["o"][tone - 1], 1)
    for i in range(len(body) - 1, -1, -1):
        if body[i] in TONES:
            return body[:i] + TONES[body[i]][tone - 1] + body[i + 1:]
    return body


def load_hsk(pkg):
    text = open(os.path.join(pkg, "statistics", "hsk.js"), encoding="utf-8").read()
    start = text.index("[", text.index("let hsk"))
    depth, end = 0, None
    for i in range(start, len(text)):
        if text[i] == "[":
            depth += 1
        elif text[i] == "]":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    return json.loads(text[start:end])


def load_cedict(pkg):
    entries = {}
    line_re = re.compile(r"^(\S+) (\S+) \[([^\]]+)\] /(.+)/\s*$")
    with open(os.path.join(pkg, "dictionary", "cedict.js"), encoding="utf-8") as f:
        for line in f:
            m = line_re.match(line)
            if not m:
                continue
            trad, simp, pinyin, defs = m.groups()
            entries.setdefault(simp, []).append((trad, pinyin, defs.split("/")))
    return entries


def load_freq(pkg):
    text = open(os.path.join(pkg, "statistics", "pinyinFrequency.js"), encoding="utf-8").read()
    return {k: int(v) for k, v in re.findall(r'"([^"]+)": "(\d+)"', text)}


def is_reference(d):
    return bool(re.match(r"^(variant of|old variant of|see |surname |erhua variant|"
                         r"Japanese variant|archaic variant|used in |also written|also pr\.)", d))


RUDE = re.compile(r"vulgar|\(slang\)|fuck|prostitut|penis|vagina|whore|bitch|bastard|shit|\bsex\b|"
                  r"\bass\b|asshole|damn|genital|slut|\bporn|erotic|masturbat|testicle|breasts", re.I)


def clean_defs(defs):
    out = []
    for d in defs:
        if d.startswith("CL:") or is_reference(d) or RUDE.search(d):
            continue
        d = re.sub(r"\s*\(Tw\)", "", d)
        # 場|场[chang2] -> 场
        d = re.sub(r"[^\s|\[\]]+\|([^\s|\[\]]+)\[[^\]]*\]", r"\1", d)
        d = re.sub(r"\[[a-zA-Z0-9: ]+\]", "", d).strip()
        if d and d not in out and "\t" not in d:
            out.append(d)
    return out[:5]


def load_glosses():
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "glosses.tsv")
    glosses = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            word, pinyin, gloss = line.rstrip("\n").split("\t")
            glosses[word] = (None if pinyin == "-" else pinyin, gloss)
    return glosses


def is_old(defs):
    return any("(old)" in d or "ancient" in d or "archaic" in d for d in defs)


def choose(word, cands, freq, forced=None):
    forced = forced or OVERRIDES.get(word)
    if forced:
        exact = [c for c in cands if c[1].replace("u:", "v") == forced]
        exact.sort(key=lambda c: (is_old(c[2]), -len(clean_defs(c[2]))))
        if exact:
            return exact[0]
    modern = [c for c in cands if not is_old(c[2])]
    cands = modern or cands
    usable = [c for c in cands if clean_defs(c[2])] or cands
    lower = [c for c in usable if not c[1][0].isupper()] or usable

    def score(c):
        trad, pinyin, defs = c
        f = 0
        if len(word) == 1:
            f = freq.get(trad + mark(pinyin), freq.get(word + mark(pinyin), 0))
        return (f, len(clean_defs(defs)))

    best = max(lower, key=score)
    if score(best)[0] == 0:
        best = lower[0]
    return best


def main():
    pkg, out = sys.argv[1], sys.argv[2]
    hsk, cedict, freq = load_hsk(pkg), load_cedict(pkg), load_freq(pkg)
    glosses = load_glosses()

    def to_trad(word):
        return "".join(choose(ch, cedict[ch], freq)[0] if ch in cedict else ch for ch in word)
    os.makedirs(out, exist_ok=True)
    for i, words in enumerate(hsk, start=1):
        rows, missing = [], []
        for w in words:
            cands = cedict.get(w)
            forced, gloss = glosses.get(w, (None, None))
            if cands:
                trad, pinyin, defs = choose(w, cands, freq, forced)
                defs = clean_defs(defs)
            else:
                trad, pinyin, defs = to_trad(w), forced, []
            if gloss:
                first = [g.strip() for g in gloss.split(";")]
                defs = (first + [d for d in defs if d not in first])[:5]
                pinyin = forced or pinyin
            if not defs or not pinyin:
                missing.append(w)
                continue
            pinyin = pinyin.lower().replace("u:", "v")
            rows.append("\t".join([w, trad, pinyin, "; ".join(defs)]))
        with open(os.path.join(out, f"hsk{i}.tsv"), "w", encoding="utf-8") as f:
            f.write("\n".join(rows) + "\n")
        print(f"HSK {i}: {len(rows)} words, skipped {len(missing)}: {' '.join(missing)}")

    # Single characters that appear inside HSK words but are not HSK words themselves,
    # so every word can be explained character by character.
    known = {w for words in hsk for w in words}
    chars = []
    for words in hsk:
        for w in words:
            for ch in w:
                if ch not in known and ch not in chars and ch in cedict:
                    chars.append(ch)
    rows = []
    for ch in chars:
        forced, gloss = glosses.get(ch, (None, None))
        trad, pinyin, defs = choose(ch, cedict[ch], freq, forced)
        defs = clean_defs(defs)
        if defs:
            rows.append("\t".join([ch, trad, pinyin.lower().replace("u:", "v"), "; ".join(defs)]))
    with open(os.path.join(out, "chars.tsv"), "w", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")
    print(f"Extra characters: {len(rows)}")

    # Common everyday words beyond the HSK lists, ranked by how often they occur in
    # film subtitles (SUBTLEX-CH), so the word map covers more than 10,000 words.
    text = open(os.path.join(pkg, "statistics", "movieWordFrequency.js"), encoding="utf-8").read()
    ranked = [line.split("\t")[0] for line in text[text.index("`") + 1:].splitlines() if "\t" in line]
    rows = []
    for w in ranked:
        if len(rows) >= EXTRA_WORDS:
            break
        if w in known or not 2 <= len(w) <= 4 or w not in cedict:
            continue
        if not all("\u4e00" <= ch <= "\u9fff" for ch in w):
            continue
        cands = [c for c in cedict[w] if not c[1][0].isupper()]
        if not cands:
            continue  # proper nouns only
        trad, pinyin, defs = choose(w, cands, freq)
        defs = clean_defs(defs)
        if defs and len(pinyin.split()) == len(w) and not any(d.startswith("abbr.") for d in defs[:1]):
            rows.append("\t".join([w, trad, pinyin.lower().replace("u:", "v"), "; ".join(defs)]))
    with open(os.path.join(out, "extra.tsv"), "w", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")
    print(f"Words beyond HSK: {len(rows)}")


if __name__ == "__main__":
    main()
