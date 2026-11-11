#!/usr/bin/env python3
"""Builds app/src/main/assets/tips.tsv: for each character, what its parts are and
a short note on how it came to look the way it does. These are the "tricks" for
remembering a character: 好 is a woman 女 with her child 子; in 妈 the 女 gives the
meaning and the 马 gives the sound.

The notes come from the character-origin data of the chinese-lexicon package by
Peter Olson (ISC licence, made for Dong Chinese). That data is a JavaScript file
of calls such as  semsem("好", "女", "子", 3, "good", "It is good ...");  this
script reads the calls as text with a small parser of its own and never runs them.

Usage: python3 tools/build_tips.py <chinese-lexicon package dir> [assets dir]
Output columns (tab-separated): character, meaning, parts (part:role ...), note.
Roles: meaning, sound, picture, simplified, unknown.
"""
import os
import re
import sys

O = "◎"   # the data's placeholder for a part that is not a character

RADICAL_FORMS = {
    "龰": "止", "夂": "止", "灬": "火", "⺗": "心", "亻": "人", "彳": "行", "辶": "辵", "⺼": "肉", "讠": "言",
    "氵": "水", "冫": "冰", "扌": "手", "钅": "金", "釒": "金", "忄": "心", "犭": "犬", "纟": "糸", "糹": "糸",
    "冄": "冉", "衤": "衣", "飠": "食", "饣": "食", "𤣩": "玉", "丬": "爿", "镸": "長", "礻": "示", "阝": "阜",
    "彐": "帚", "刂": "刀", "⻏": "邑", "攵": "攴", "勾": "句", "艹": "艸", "⺮": "竹", "巛": "川", "冖": "宀",
    "耂": "老", "虍": "虎", "勹": "包",
}


# ------------------------------------------------------------------ reading the calls

class Reader:
    """Reads the small part of JavaScript the data uses: calls, strings, numbers,
    lists, {key: value} maps, + and a few names."""

    def __init__(self, text, names):
        self.t = text
        self.i = 0
        self.names = names

    def skip(self):
        t = self.t
        while self.i < len(t):
            if t[self.i].isspace():
                self.i += 1
            elif t.startswith("//", self.i):
                self.i = t.find("\n", self.i) if "\n" in t[self.i:] else len(t)
            else:
                break

    def peek(self):
        self.skip()
        return self.t[self.i] if self.i < len(self.t) else ""

    def take(self, ch):
        if self.peek() != ch:
            raise ValueError(f"expected {ch} at {self.i}: {self.t[self.i:self.i + 30]!r}")
        self.i += 1

    def word(self):
        self.skip()
        m = re.compile(r"[^\s()\[\]{},:;+\"'`]+").match(self.t, self.i)
        if not m:
            raise ValueError(f"expected a name at {self.i}: {self.t[self.i:self.i + 30]!r}")
        self.i = m.end()
        return m.group(0)

    def string(self):
        quote = self.t[self.i]
        self.i += 1
        out = []
        t = self.t
        while t[self.i] != quote:
            c = t[self.i]
            if c == "\\":
                nxt = t[self.i + 1]
                out.append({"n": "\n", "t": "\t"}.get(nxt, nxt))
                self.i += 2
            elif quote == "`" and t.startswith("${", self.i):
                end = t.index("}", self.i)
                inner = Reader(t[self.i + 2:end], self.names)
                out.append(str(inner.expression() or ""))
                self.i = end + 1
            else:
                out.append(c)
                self.i += 1
        self.i += 1
        return "".join(out)

    def expression(self):
        value = self.term()
        while self.peek() == "+":
            self.i += 1
            right = self.term()
            value = (value or "") + (right or "") if isinstance(value, str) or isinstance(right, str) else value + right
        return value

    def term(self):
        c = self.peek()
        if c in "\"'`":
            return self.string()
        if c == "[":
            self.i += 1
            items = []
            while self.peek() != "]":
                items.append(self.expression())
                if self.peek() == ",":
                    self.i += 1
            self.i += 1
            return items
        if c == "{":
            self.i += 1
            pairs = {}
            while self.peek() != "}":
                key = self.string() if self.peek() in "\"'`" else self.word()
                self.take(":")
                pairs[key] = self.expression()
                if self.peek() == ",":
                    self.i += 1
            self.i += 1
            return pairs
        if c == "(":
            self.i += 1
            value = self.expression()
            self.take(")")
            return value
        name = self.word()
        if re.fullmatch(r"-?\d+(\.\d+)?", name):
            return float(name) if "." in name else int(name)
        if self.peek() == "(":
            self.i += 1
            args = []
            while self.peek() != ")":
                args.append(self.expression())
                if self.peek() == ",":
                    self.i += 1
            self.i += 1
            return self.names.call(name, args)
        return self.names.value(name)


# ------------------------------------------------------------------ what the calls mean

class Origins:
    def __init__(self):
        self.entries = {}
        self.variables = {"true": True, "false": False, "null": None, "undefined": None, "O": O}

    def value(self, name):
        if name in self.variables:
            return self.variables[name]
        raise ValueError("unknown name " + name)

    def call(self, name, args):
        fn = getattr(self, "f_" + name, None)
        if fn is None:
            raise ValueError("unknown call " + name)
        return fn(*args)

    # notes
    def f_shiftMeaning(self, new, old=None):
        if old:
            return f'Based on the original meaning "{old}". ' + self.f_shiftMeaning(new)
        return f'The meaning has shifted over time and is now "{new}". '

    def f_obsoleteMeaning(self, old):
        return f'Built on the original meaning "{old}", which has nothing to do with the modern meaning. '

    def f_obsoleteSound(self, char, sound):
        return f"{char} once sounded like {sound}; the two have drifted apart since. "

    def f_alsoMeaning(self, char):
        return f"{char} also hints at the meaning. "

    def f_unknownComonentNote(self):
        return "What this part is for is unclear. "

    def f_simplified(self, trad):
        return f"Simplified form of {trad}. "

    def f_soundLoan(self, new, old, new_char):
        return f'The meaning "{new}" was borrowed for its sound. The original meaning "{old}" is now written {new_char}.'

    def f_simplifyMerge(self, simp, trad, meaning):
        return f'{simp} is also used for "{meaning}", which the traditional script writes {trad}.'

    def f_cursive(self, simp, trad):
        return f"{simp} comes from the handwritten (cursive) form of {trad}."

    def f_shorthand(self, component, trad):
        return f"Shorthand for the {component} part of the traditional character {trad}."

    def f_radicalNote(self, radical):
        return f"{radical} is the squeezed form of {RADICAL_FORMS.get(radical, radical)}. "

    # parts
    def part(self, kind, char, notes=None):
        return {"type": kind, "char": char, "notes": notes or ""}

    def f_meaningComponent(self, char, fragment=None, notes=None):
        return self.part("meaning", char, notes)

    def f_soundComponent(self, char, fragment=None, notes=None):
        return self.part("sound", char, notes)

    def f_simplifiedComponent(self, char, fragment=None, notes=None):
        return self.part("simplified", char, notes)

    def f_iconComponent(self, char, fragment=None, notes=None):
        return self.part("picture", char, notes)

    def f_unknownComponent(self, char, fragment=None, notes=None):
        return self.part("unknown", char, (self.f_unknownComonentNote() if char != O else "") + (notes or ""))

    # entries
    def f_addEtymology(self, char, definition=None, notes=None, components=None, images=None):
        self.entries[char] = {"definition": definition or "", "notes": notes or "",
                              "components": [dict(c) for c in (components or [])]}

    def f_icon(self, char, definition=None, notes=None, img=None, components=None):
        self.f_addEtymology(char, definition, notes, components)

    def f_semsem(self, char, left, right, strokes=None, definition=None, notes=None, images=None):
        self.f_addEtymology(char, definition, notes, [self.part("meaning", left), self.part("meaning", right)])

    def f_semphon(self, char, left, right, strokes=None, definition=None, notes=None, obsolete=None, both=None,
                  reversed_=None, images=None):
        text = f"{left} gives the meaning and {right} gives the sound. " + (notes or "")
        sound_note = (self.f_obsoleteSound(char, right) if obsolete else "") + (self.f_alsoMeaning(right) if both else "")
        self.f_addEtymology(char, definition, text + sound_note,
                            [self.part("meaning", left), self.part("sound", right)])

    def f_phonsem(self, char, left, right, strokes=None, definition=None, notes=None, obsolete=None, both=None, images=None):
        self.f_semphon(char, right, left, strokes, definition, notes, obsolete, both, True, images)

    def f_simp(self, simp, trad, fragments=None, replacements=None, changed=None, is_cursive=None, merge_char=None,
               merge_meaning=None):
        base = self.entries.get(trad)
        if base is None:
            raise ValueError("no entry for " + trad)
        notes = [("" if is_cursive else self.f_simplified(trad)), base["notes"],
                 (self.f_cursive(simp, trad) if is_cursive else ""),
                 (self.f_simplifyMerge(simp, merge_char, merge_meaning) if merge_char else "")]
        entry = {"definition": base["definition"], "notes": " ".join(n.strip() for n in notes if n and n.strip()),
                 "components": [dict(c) for c in base["components"]]}
        for i, fragment in enumerate(fragments or []):
            if i >= len(entry["components"]):
                break
            part = entry["components"][i]
            if replacements and part["char"] in replacements:
                part["char"] = replacements[part["char"]]
            if changed and part["char"] in changed:
                part["char"] = changed[part["char"]]
                part["type"] = "simplified"
            if isinstance(fragment, list) and len(fragment) == 2 and fragment[0] == fragment[1]:
                part["type"] = "deleted"
        entry["components"] = [p for p in entry["components"] if p["type"] != "deleted"]
        self.entries[simp] = entry


def read_origins(package_dir):
    path = os.path.join(package_dir, "etymology", "index.js")
    text = open(path, encoding="utf-8").read()
    end = text.find("let componentDict")
    text = text[:end if end > 0 else len(text)]
    origins = Origins()
    reader = Reader(text, origins)
    statement = re.compile(r"(?m)^(let\s+(\w+)\s*=|(?:addEtymology|icon|semsem|semphon|phonsem|simp)\()")
    failed = 0
    position = 0
    while True:
        m = statement.search(text, position)
        if not m:
            break
        try:
            if m.group(2):
                reader.i = m.end()
                origins.variables[m.group(2)] = reader.expression()
            else:
                reader.i = m.start()
                reader.expression()
            position = max(reader.i, m.end())
        except (ValueError, IndexError, TypeError):
            failed += 1
            position = m.end()
    return origins.entries, failed


def tidy(note):
    note = re.sub(r"\s+", " ", note or "").strip()
    return note.replace(" .", ".")


def app_characters(assets):
    chars = set()
    words = os.path.join(assets, "words")
    files = [os.path.join(words, f) for f in sorted(os.listdir(words))]
    for path in files:
        with open(path, encoding="utf-8") as f:
            for line in f:
                for ch in line.split("\t")[0]:
                    if "一" <= ch <= "鿿":
                        chars.add(ch)
    return chars


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    here = os.path.dirname(os.path.abspath(__file__))
    assets = sys.argv[2] if len(sys.argv) > 2 else os.path.join(here, "..", "app", "src", "main", "assets")
    entries, failed = read_origins(sys.argv[1])
    wanted = app_characters(assets)
    lines = []
    for char in sorted(wanted):
        entry = entries.get(char)
        if not entry:
            continue
        parts = [f"{p['char']}:{p['type']}" for p in entry["components"] if p["char"] and p["char"] != O
                 and "\t" not in p["char"] and " " not in p["char"]]
        note = tidy(entry["notes"])
        if not note and not parts:
            continue
        lines.append("\t".join([char, tidy(entry["definition"]), " ".join(parts), note]))
    out = os.path.join(assets, "tips.tsv")
    with open(out, "w", encoding="utf-8") as f:
        f.write("# How each character is built and how to remember it. Columns: character, meaning, parts\n"
                "# (part:role, roles: meaning sound picture simplified unknown), note.\n"
                "# From the character-origin data of chinese-lexicon by Peter Olson (ISC licence); made by tools/build_tips.py.\n")
        f.write("\n".join(lines) + "\n")
    print(f"read {len(entries)} origins ({failed} statements skipped); wrote {len(lines)} of {len(wanted)} "
          f"app characters to {out} ({os.path.getsize(out)} bytes)")


if __name__ == "__main__":
    main()
