#!/usr/bin/env python3
"""Builds app/src/main/assets/sentences/hsk{1..6}.tsv from the Tatoeba corpus.

Tatoeba (https://tatoeba.org) sentences are licensed CC BY 2.0 FR. Download
  cmn_sentences.tsv, eng_sentences.tsv and cmn-eng_links.tsv
from https://downloads.tatoeba.org/exports/per_language/ and unpack them.

Each Chinese sentence is converted to simplified characters, split into words
with the app's own vocabulary (longest match first) and kept only if every word
is an HSK word or a known name. Its level is the highest HSK level among its
words. Pinyin is produced per word from the app's word lists, with pypinyin
choosing between readings of single characters that have more than one.

Usage: python3 tools/build_sentences.py <tatoeba-dir> <assets-dir>
Requires: pip install pypinyin opencc-python-reimplemented
Output line format: words separated by spaces | pinyin | English
"""
import os
import re
import sys

from opencc import OpenCC
from pypinyin import Style, lazy_pinyin

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_wordlists import RUDE, mark  # noqa: E402

PER_LEVEL = {1: 2500, 2: 4000, 3: 5000, 4: 6000, 5: 6000, 6: 4500}
NAMES = {
    "汤姆": "Tāngmǔ", "玛丽": "Mǎlì", "约翰": "Yuēhàn", "玛丽亚": "Mǎlìyà", "杰克": "Jiékè",
    "日本": "Rìběn", "美国": "Měiguó", "英国": "Yīngguó", "法国": "Fǎguó", "德国": "Déguó",
    "上海": "Shànghǎi", "东京": "Dōngjīng", "伦敦": "Lúndūn", "巴黎": "Bālí", "纽约": "Niǔyuē",
    "波士顿": "Bōshìdùn", "澳大利亚": "Àodàlìyà", "加拿大": "Jiānádà", "英语": "Yīngyǔ",
    "法语": "Fǎyǔ", "日语": "Rìyǔ", "中文": "Zhōngwén", "英文": "Yīngwén", "德语": "Déyǔ",
    "欧洲": "Ōuzhōu", "亚洲": "Yàzhōu", "非洲": "Fēizhōu", "韩国": "Hánguó", "西班牙": "Xībānyá",
    "意大利": "Yìdàlì", "俄罗斯": "Éluósī", "印度": "Yìndù", "长城": "Chángchéng",
}
# Words that are not HSK entries on their own but are made of beginner pieces.
GLUE = {"这个": 1, "那个": 1, "这些": 1, "那些": 1, "这儿": 1, "那儿": 1, "这里": 1, "那里": 1,
        "哪里": 1, "他们": 1, "她们": 1, "你们": 1, "它们": 2, "什么样": 1, "这样": 2, "那样": 2,
        "这么": 2, "那么": 2, "有点儿": 1, "不是": 1, "没": 1, "们": 1, "今年": 1, "明年": 1,
        "去年": 2, "每天": 2, "有些": 1, "一些": 1, "一点": 1, "有点": 1, "不要": 2, "不能": 1,
        "不会": 1, "不用": 2, "怎样": 1, "多久": 2, "一样": 3, "一直": 3, "为了": 3, "自己": 3}
PUNCT = "。，？！、：；"
PARTICLES = {"的": "de5", "吗": "ma5", "呢": "ne5", "吧": "ba5", "们": "men5", "么": "me5", "啊": "a5"}


def load_words(assets):
    level, pinyin = {}, {}
    for n in range(1, 7):
        with open(os.path.join(assets, "words", f"hsk{n}.tsv"), encoding="utf-8") as f:
            for line in f:
                w, _, py, _ = line.rstrip("\n").split("\t")
                level.setdefault(w, n)
                pinyin.setdefault(w, py)
    readings = dict(pinyin)
    for name in ("extra.tsv", "chars.tsv"):
        with open(os.path.join(assets, "words", name), encoding="utf-8") as f:
            for line in f:
                w, _, py, _ = line.rstrip("\n").split("\t")
                readings.setdefault(w, py)
    return level, readings


def segment(text, vocab, longest):
    out, i = [], 0
    while i < len(text):
        if text[i] in PUNCT:
            out.append(text[i])
            i += 1
            continue
        for size in range(min(longest, len(text) - i), 0, -1):
            piece = text[i:i + size]
            if piece in vocab:
                out.append(piece)
                i += size
                break
        else:
            return None
    return out


def marked(numbered):
    out = ""
    for syl in numbered.split():
        m = mark(syl)
        if out and re.sub(r"\d", "", syl)[0] in "aeo":
            out += "'"
        out += m
    return out


def main():
    src, assets = sys.argv[1], sys.argv[2]
    level, readings = load_words(assets)
    vocab = set(level) | set(NAMES) | set(GLUE)
    longest = max(len(w) for w in vocab)
    t2s = OpenCC("t2s")

    english = {}
    wanted = set()
    links = {}
    with open(os.path.join(src, "cmn-eng_links.tsv"), encoding="utf-8") as f:
        for line in f:
            a, b = line.split()
            links.setdefault(a, []).append(b)
            wanted.add(b)
    with open(os.path.join(src, "eng_sentences.tsv"), encoding="utf-8") as f:
        for line in f:
            i, _, text = line.rstrip("\n").split("\t", 2)
            if i in wanted:
                english[i] = text

    by_level = {n: [] for n in range(1, 7)}
    seen = set()
    with open(os.path.join(src, "cmn_sentences.tsv"), encoding="utf-8") as f:
        for line in f:
            i, _, raw = line.rstrip("\n").split("\t", 2)
            text = t2s.convert(raw.strip()).replace(",", "，").replace("?", "？").replace("!", "！")
            text = re.sub(r"\s+", "", text)
            if not re.fullmatch(r"[一-鿿。，？！、：；]+", text) or text[-1] not in "。？！":
                continue
            if text in seen or not 3 <= len(text) <= 28:
                continue
            options = [english[j] for j in links.get(i, []) if j in english]
            options = [e for e in options if 3 <= len(e) <= 110 and "|" not in e and not RUDE.search(e)]
            if not options:
                continue
            tokens = segment(text, vocab, longest)
            if tokens is None:
                continue
            words = [t for t in tokens if t not in PUNCT]
            if not 2 <= len(words) <= 14:
                continue
            seen.add(text)
            lvl = max(level.get(w, GLUE.get(w, 1)) for w in words)

            contextual = lazy_pinyin(text, style=Style.TONE3, neutral_tone_with_five=True, errors=lambda x: list(x))
            pos, parts, first = 0, [], True
            for t in tokens:
                if t in PUNCT:
                    parts[-1] += {"。": ".", "，": ",", "？": "?", "！": "!", "、": ",", "：": ":", "；": ";"}[t]
                    pos += 1
                    continue
                if t in NAMES:
                    py = NAMES[t]
                elif len(t) == 1:
                    guess = contextual[pos] if re.fullmatch(r"[a-z]+[1-5]", contextual[pos]) else None
                    known = readings.get(t)
                    py = marked(PARTICLES.get(t) or guess or known)
                    if t == "得" and guess != "dei3":
                        py = "de"
                    if t == "了" and guess != "liao3":
                        py = "le"
                    if t == "着" and guess != "zhao2":
                        py = "zhe"
                    if t == "地" and guess in ("de5", "di5"):
                        py = "de"
                    if t == "不":
                        py = "bù"
                    if t == "一":
                        py = "yī"
                    if t == "个":
                        py = "ge"
                else:
                    known = readings.get(t)
                    py = marked(known) if known else marked(" ".join(contextual[pos:pos + len(t)]))
                    if t.endswith("儿") and not known:
                        py = marked(" ".join(contextual[pos:pos + len(t) - 1])) + "r"
                if first:
                    py = py[0].upper() + py[1:]
                    first = False
                parts.append(py)
                pos += len(t)
            eng = min(options, key=len)
            by_level[lvl].append((len(words), " ".join(tokens), " ".join(parts), eng))

    out_dir = os.path.join(assets, "sentences")
    os.makedirs(out_dir, exist_ok=True)
    for n, rows in by_level.items():
        # Shorter sentences first: they are easier and more of them fit.
        rows.sort(key=lambda r: (r[0], r[1]))
        # Tatoeba is full of sentences about "Tom"; keep only every fifth one.
        named = [r for r in rows if "汤姆" in r[1]]
        keep = set(id(r) for r in named[::5])
        rows = [r for r in rows if "汤姆" not in r[1] or id(r) in keep][:PER_LEVEL[n]]
        with open(os.path.join(out_dir, f"hsk{n}.tsv"), "w", encoding="utf-8") as f:
            for _, zh, py, en in rows:
                f.write(f"{zh}|{py}|{en}\n")
        print(f"HSK {n}: {len(rows)} sentences (of {len(by_level[n])} usable)")


if __name__ == "__main__":
    main()
