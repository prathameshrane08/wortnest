"""Re-rank verbs in dict.json by the median frequency of their own conjugated forms.

Usage: python3 rescore.py freq.txt   (run inside the wortnest folder, after extract.py)
"""
import json, sys, statistics

freq = {}
for line in open(sys.argv[1], encoding="utf-8"):
    w, c = line.rsplit(" ", 1)
    freq[w] = int(c)
rows = json.load(open("dict.json"))
verbs = json.load(open("verbs.json"))

nouns = {r[0].lower() for r in rows if r[1] == "noun"}

def vscore(inf, v):
    f = lambda w: freq.get(w.lower(), 0)
    own = lambda w: 0 if w.lower() in nouns else f(w)   # ignore homographs of nouns (weihnachten ~ Weihnachten)
    if any(" " in x for x in v["pr"] + v["pt"] if x):     # separable: only the one-word forms are unambiguous
        return (own(inf) + own(v["pp"])) // 2
    forms = [x for x in v["pr"] + v["pt"] if x and x != "-"]
    if len(forms) < 10:                                   # impersonal or defective verb: rank conservatively
        return min(own(inf), own(v["pp"]))
    return int(statistics.median([f(x) for x in forms] + [own(v["pp"]), own(inf)]))

for r in rows:
    if r[1] == "verb":
        r[5] = vscore(r[0], verbs[r[0]]) if r[0] in verbs else (0 if r[0].lower() in nouns else freq.get(r[0].lower(), 0))
for r in rows:
    if r[1] == "verb" and r[0] in ("passiert", "jungen"):   # frequency-list noise
        r[5] = 0
# Nouns: the frequency list is lowercase, so "Aber" would inherit "aber". Score every noun from
# the list again, then zero it when it shares its spelling with a function word, a modifier or a
# verb form. Homographs of verbs (das Essen) and interjections (die Entschuldigung) stay in.
FUNCTION = {"pron", "det", "article", "conj", "adv", "adj", "num", "particle", "prep", "postp"}
clash = {r[0].lower() for r in rows if r[1] in FUNCTION} - {"zeit"}
clash |= {x.split()[0].lower() for v in verbs.values() for x in v["pr"] + v["pt"] if x}
clash |= {x.split()[0].lower() for v in verbs.values() for x in v["k1"] + v["k2"] if x}
adjs = {r[0] for r in rows if r[1] == "adj"}
clash |= {f for f, lemmas in json.load(open("forms.json")).items() if any(l in adjs for l in lemmas)}
clash |= {"ach", "o", "mom", "dad", "amen", "nein", "ja", "des", "dem", "den", "der", "die", "das",
          "ein", "eine", "einem", "einen", "einer", "eines"}
for r in rows:
    if r[1] == "noun":
        lw = r[0].lower()
        r[5] = 0 if (lw in clash or len(r[0]) < 3) else freq.get(lw, 0)
rows.sort(key=lambda r: -r[5])
json.dump(rows, open("dict.json", "w"), ensure_ascii=False, separators=(",", ":"))
top = [r[0] for r in rows if r[1] == "verb"][:25]
print(" ".join(top))
