"""Tag dict.json entries with their CEFR level (A1, A2, B1) from the Goethe-Institut exam word lists.

Usage (inside the wortnest folder, after extract.py and rescore.py; needs `pip install pypdf`):
  python3 levels.py A1_SD1_Wortliste_02.pdf Goethe-Zertifikat_A2_Wortliste.pdf Goethe-Zertifikat_B1_Wortliste.pdf

The lists are free downloads from goethe.de (Start Deutsch 1, Goethe-Zertifikat A2 and B1). Only the level of each
word is kept: rows of dict.json that appear in a list get a 7th column, 1 = A1, 2 = A2, 3 = B1.
A word in several lists gets the lowest level.
"""
import json, re, sys
from pypdf import PdfReader

rows = json.load(open("dict.json"))
by_word = {}
for i, r in enumerate(rows):
    by_word.setdefault(r[0], []).append(i)

NOUN = re.compile(r"^(der|die|das)\s+([A-ZÄÖÜ][\wäöüß-]*?)(?=[,\s(]|$)")
# Verbs, adjectives and small words start the line in lowercase and are followed by their forms, a comma,
# a note in brackets or the start of an example sentence; wrapped example lines end in punctuation instead.
OTHER = re.compile(r"^([a-zäöüß][a-zäöüß|]+)(?=,|\s*\(|\s*$|\s{2,}|\s+[A-ZÄÖÜ0-9])")

def headwords(pdf):
    text = "\n".join(p.extract_text() or "" for p in PdfReader(pdf).pages)
    nouns, other = set(), set()
    for line in text.splitlines():
        line = re.sub(r"\s+", " ", line.replace("\t", " ")).strip()
        if m := NOUN.match(line):
            nouns.add(m.group(2).rstrip("-"))
        elif m := OTHER.match(line):
            other.add(m.group(1).replace("|", ""))
    return nouns, other

level = {}
for lv, pdf in enumerate(sys.argv[1:4], start=1):
    nouns, other = headwords(pdf)
    hits = 0
    for w in nouns:
        for i in by_word.get(w, []):
            if rows[i][1] == "noun":
                level.setdefault(i, lv); hits += 1
    for w in other:
        ids = [i for i in by_word.get(w, []) if rows[i][1] != "noun"]
        if any(rows[i][1] == "verb" for i in ids):   # "wollen" is the verb, not the adjective (woollen)
            ids = [i for i in ids if rows[i][1] == "verb"]
        for i in ids:
            level.setdefault(i, lv); hits += 1
    print(f"{['A1', 'A2', 'B1'][lv - 1]}: {len(nouns)} nouns, {len(other)} other words, {hits} entries tagged", file=sys.stderr)

for i, r in enumerate(rows):
    del r[6:]
    if i in level:
        r.append(level[i])
json.dump(rows, open("dict.json", "w"), ensure_ascii=False, separators=(",", ":"))
counts = [sum(1 for v in level.values() if v == lv) for lv in (1, 2, 3)]
print("tagged A1/A2/B1:", counts, file=sys.stderr)
