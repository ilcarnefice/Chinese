#!/usr/bin/env python3
"""Checks a phrase-book file (app/src/main/assets/content/phrases_*.txt).

Format:
  # comment
  ## Topic title
  words separated by spaces, punctuation last | pinyin | English

Usage: python3 tools/check_phrases.py <file> [expected-phrase-count]
"""
import re
import sys

VOWELS = re.compile(r"[aeiouüāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]+")
CJK = re.compile(r"[一-鿿]")


def main():
    path = sys.argv[1]
    expected = int(sys.argv[2]) if len(sys.argv) > 2 else None
    errors, topics, count, seen = [], 0, 0, set()
    in_topic = False
    for n, raw in enumerate(open(path, encoding="utf-8"), start=1):
        line = raw.rstrip("\n")
        if not line.strip():
            continue
        if line.startswith("## "):
            topics += 1
            in_topic = True
            continue
        if line.startswith("#"):
            continue
        fields = [f.strip() for f in line.split("|")]
        if len(fields) != 3:
            errors.append(f"{n}: expected 3 fields: {line}")
            continue
        if not in_topic:
            errors.append(f"{n}: phrase before any '## topic' line")
        zh, py, en = fields
        tokens = zh.split(" ")
        if "" in tokens:
            errors.append(f"{n}: double space in Chinese: {zh}")
        if tokens[-1] not in ("。", "？", "！"):
            errors.append(f"{n}: must end with a separate 。？！ token: {zh}")
        words = [t for t in tokens if t not in "。，？！、：；"]
        for w in words:
            if not re.fullmatch(r"[一-鿿0-9A-Za-z]+", w):
                errors.append(f"{n}: odd token '{w}': {zh}")
        if CJK.search(py) or CJK.search(en):
            errors.append(f"{n}: Chinese characters in pinyin or English: {line}")
        hanzi = sum(len(CJK.findall(w)) for w in words)
        groups = len(VOWELS.findall(py.lower()))
        erhua = sum(1 for w in words if w.endswith("儿") and len(w) > 1)
        if hanzi and not (hanzi - erhua - 1 <= groups <= hanzi + 3):
            errors.append(f"{n}: pinyin has {groups} vowel groups for {hanzi} characters: {zh} / {py}")
        if len(py.split()) > hanzi + 2:
            errors.append(f"{n}: more pinyin words than characters: {zh} / {py}")
        if not py or not py[0].isupper() and not py[0].isdigit():
            errors.append(f"{n}: pinyin should start with a capital: {py}")
        if py and py[-1] not in ".?!":
            errors.append(f"{n}: pinyin should end with . ? or !: {py}")
        if not en:
            errors.append(f"{n}: missing English")
        key = "".join(tokens)
        if key in seen:
            errors.append(f"{n}: duplicate phrase: {zh}")
        seen.add(key)
        count += 1
    print(f"{path}: {topics} topics, {count} phrases, {len(errors)} problems")
    for e in errors[:60]:
        print("  " + e)
    if expected is not None and count != expected:
        print(f"  expected exactly {expected} phrases")
        sys.exit(1)
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
