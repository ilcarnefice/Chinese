#!/usr/bin/env python3
"""Checks a course outline (app/src/main/assets/content/outline_hsk<N>.txt).

An outline groups a level's words into themed units and lessons:

  ## Unit title              starts a unit
  你 好 我 是 不             one vocabulary lesson: the words it teaches, in order
  > Saying no: 不            a grammar lesson: the exact title of a formula in grammar.txt

Usage: python3 tools/check_outline.py <level> <outline file> [--words <file>] [--grammar all|none|any]
  --words    a TSV whose first column lists the words this outline must cover exactly
             (default: every word of the level)
  --grammar  all: every formula of the level must appear once; none: no grammar lines
             allowed; any (default): those that appear must exist and not repeat
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")


def level_words(level):
    with open(os.path.join(ASSETS, "words", f"hsk{level}.tsv"), encoding="utf-8") as f:
        return [line.split("\t")[0] for line in f if line.strip()]


def grammar_titles(level):
    titles = []
    with open(os.path.join(ASSETS, "content", "grammar.txt"), encoding="utf-8") as f:
        for line in f:
            parts = [p.strip() for p in line.lstrip("#").split("|")]
            if line.startswith("#") and len(parts) in (3, 4) and parts[0].isdigit() and int(parts[0]) == level:
                titles.append(parts[1])
    return titles


def main():
    args = sys.argv[1:]
    level, path = int(args[0]), args[1]
    words_file = args[args.index("--words") + 1] if "--words" in args else None
    grammar_mode = args[args.index("--grammar") + 1] if "--grammar" in args else "any"
    if words_file:
        expected = [line.split("\t")[0] for line in open(words_file, encoding="utf-8") if line.strip()]
    else:
        expected = level_words(level)
    known_grammar = grammar_titles(level)

    errors = []
    units = []          # (title, [lesson word lists], [grammar titles])
    seen_words, seen_grammar, titles = {}, set(), set()
    for n, raw in enumerate(open(path, encoding="utf-8"), start=1):
        line = raw.strip()
        if not line or (line.startswith("#") and not line.startswith("##")):
            continue
        if line.startswith("## "):
            title = line[3:].strip()
            if not title or len(title) > 28:
                errors.append(f"line {n}: unit title must be 1 to 28 characters: '{title}'")
            if re.search(r"[一-鿿]", title):
                errors.append(f"line {n}: unit title must be English: '{title}'")
            if title.lower() in titles:
                errors.append(f"line {n}: duplicate unit title '{title}'")
            titles.add(title.lower())
            units.append((title, [], []))
            continue
        if not units:
            errors.append(f"line {n}: content before the first '## unit' line")
            continue
        if line.startswith(">"):
            g = line[1:].strip()
            if g not in known_grammar:
                errors.append(f"line {n}: no HSK {level} formula is titled '{g}'")
            elif g in seen_grammar:
                errors.append(f"line {n}: formula '{g}' appears twice")
            seen_grammar.add(g)
            if grammar_mode == "none":
                errors.append(f"line {n}: this outline must not contain grammar lines")
            if not units[-1][1]:
                errors.append(f"line {n}: a unit must start with a vocabulary lesson, not grammar")
            units[-1][2].append(g)
            continue
        lesson = line.split()
        if not 3 <= len(lesson) <= 6:
            errors.append(f"line {n}: a lesson has 3 to 6 words (5 is normal), this one has {len(lesson)}")
        for w in lesson:
            if w in seen_words:
                errors.append(f"line {n}: '{w}' was already taught on line {seen_words[w]}")
            seen_words[w] = n
        units[-1][1].append(lesson)

    expected_set = set(expected)
    missing = [w for w in expected if w not in seen_words]
    extra = [w for w in seen_words if w not in expected_set]
    if missing:
        errors.append(f"{len(missing)} words are not taught: {' '.join(missing[:60])}")
    if extra:
        errors.append(f"{len(extra)} words do not belong here: {' '.join(extra[:60])}")
    if grammar_mode == "all":
        left = [g for g in known_grammar if g not in seen_grammar]
        if left:
            errors.append(f"{len(left)} formulas are not placed: {' | '.join(left)}")
    for title, lessons, grammar in units:
        count = sum(len(l) for l in lessons)
        if not 2 <= len(lessons) <= 4:
            errors.append(f"unit '{title}': {len(lessons)} vocabulary lessons (2 to 4 allowed)")
        if not 9 <= count <= 22:
            errors.append(f"unit '{title}': {count} words (9 to 22 allowed)")
        if len(grammar) > 3:
            errors.append(f"unit '{title}': {len(grammar)} grammar lessons (at most 3)")
    lessons = sum(len(u[1]) for u in units)
    five = sum(1 for u in units for l in u[1] if len(l) == 5)
    if lessons and five < lessons * 0.85:
        errors.append(f"only {five} of {lessons} lessons have exactly 5 words; at least 85% must")

    for e in errors[:60]:
        print("error: " + e)
    print(f"{path}: {len(units)} units, {lessons} vocabulary lessons, {len(seen_words)} words, "
          f"{len(seen_grammar)} grammar lessons, {len(errors)} errors")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
