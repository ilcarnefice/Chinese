#!/usr/bin/env python3
"""Builds app/src/main/assets/strokes.tsv: stroke order for every character taught.

Input: an unpacked copy of the `hanzi-writer-data` npm package, whose stroke data
comes from the Make Me a Hanzi project and the Arphic fonts (Arphic Public License,
see licenses/ARPHICPL.TXT). Only each stroke's centre line is kept, which is enough
to animate the stroke order and to check a traced stroke.

Usage: python3 tools/build_strokes.py <hanzi-writer-data/package> <assets-dir>
Output line: character <TAB> stroke;stroke;...   stroke = "x,y x,y ..." on a
1024 x 1024 grid with the origin at the top left.
"""
import json
import os
import sys


def main():
    pkg, assets = sys.argv[1], sys.argv[2]
    chars = []
    for name in ["hsk%d.tsv" % n for n in range(1, 7)] + ["chars.tsv"]:
        with open(os.path.join(assets, "words", name), encoding="utf-8") as f:
            for line in f:
                for ch in line.split("\t")[0]:
                    if ch not in chars:
                        chars.append(ch)
    rows, missing = [], []
    for ch in chars:
        path = os.path.join(pkg, ch + ".json")
        if not os.path.exists(path):
            missing.append(ch)
            continue
        medians = json.load(open(path, encoding="utf-8"))["medians"]
        strokes = [" ".join("%d,%d" % (x, 900 - y) for x, y in m) for m in medians]
        rows.append(ch + "\t" + ";".join(strokes))
    with open(os.path.join(assets, "strokes.tsv"), "w", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")
    print(f"{len(rows)} characters, missing {len(missing)}: {''.join(missing)}")


if __name__ == "__main__":
    main()
