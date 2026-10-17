"""Stream kaikki German JSONL on stdin; write examples.json with short example sentences for dict.json words.

Usage: curl -s https://kaikki.org/dictionary/German/kaikki.org-dictionary-German.jsonl | python3 examples.py
(run inside the wortnest folder, after extract.py and rescore.py; add --fill-only to redo only the borrowing step)

Output: {"word|pos": [["German sentence", "English translation"], ...]}, at most MAX per entry.
Only sentences with an English translation are kept, short made-up examples before literary quotations.
"""
import json, sys, re

MAX = 3
MAXLEN = 120

rows = json.load(open("dict.json"))
want = {(r[0], r[1]) for r in rows}
found = {}   # (word,pos) -> list of (rank, de, en)

def tidy(s):
    return re.sub(r"\s+", " ", s or "").strip()

def junk(de, en):
    """Some quotations carry the book reference instead of the sentence ("Georg Büchner, Dantons Tod ..., p. 50"),
    and some still have Wiktionary's placeholder instead of a translation."""
    return bool(re.search(r"\bpp?\.\s?\d|\bin: ", de)) or "add an English translation" in en

for line in ([] if "--fill-only" in sys.argv else sys.stdin):
    try:
        d = json.loads(line)
    except Exception:
        continue
    key = (d.get("word", ""), d.get("pos"))
    if key not in want or d.get("lang_code", "de") != "de":
        continue
    got = found.setdefault(key, [])
    for sense_no, s in enumerate(d.get("senses", [])):
        if set(s.get("tags", [])) & {"form-of", "alt-of", "misspelling", "archaic", "obsolete"}:
            continue
        for ex in s.get("examples", []):
            de = tidy(ex.get("text"))
            en = tidy(ex.get("english") or ex.get("translation"))
            if not de or not en or len(de) > MAXLEN or len(en) > MAXLEN + 20:
                continue
            if "\n" in de or de.startswith(("(", "[")) or re.search(r"\d{4}", de) or junk(de, en):
                continue
            quote = ex.get("type") == "quotation" or bool(ex.get("ref"))
            rank = (quote, sense_no, len(de))
            if all(de != g[1] for g in got):
                got.append((rank, de, en))

def fill(out):
    """Words with fewer than two sentences of their own borrow short ones that use the word from other entries
    (a verb's sentence that uses a noun also shows up under that noun)."""
    verbs = json.load(open("verbs.json"))
    out = {k: [x for x in v if not junk(*x)] for k, v in out.items()}
    out = {k: v for k, v in out.items() if v}
    pool = sorted({(de, en) for v in out.values() for de, en in v}, key=lambda x: len(x[0]))
    index = {}
    for n, (de, _) in enumerate(pool):
        for tok in set(re.findall(r"[A-Za-zÄÖÜäöüß]+", de)):
            index.setdefault(tok, []).append(n)
    for r in rows:
        w, p = r[0], r[1]
        if p not in ("noun", "verb", "adj", "adv") or len(w) < 3:
            continue
        have = out.get(f"{w}|{p}", [])
        if len(have) >= 2:
            continue
        forms = {w}
        if p == "noun" and r[3]:
            forms.add(r[3])
        if p == "verb" and w in verbs:
            v = verbs[w]
            forms |= {f for f in v["pr"] + v["pt"] + [v["pp"]] if f and " " not in f}
        hits = sorted({n for f in forms for n in index.get(f, [])})
        extra = [list(pool[n]) for n in hits if list(pool[n]) not in have][:2 - len(have)]
        if extra:
            out[f"{w}|{p}"] = have + extra
    return out

if "--fill-only" in sys.argv:   # redo the borrowing step on an existing examples.json without the download
    out = fill(json.load(open("examples.json")))
else:
    out = {}
    for (w, p), got in found.items():
        if not got:
            continue
        got.sort()
        out[f"{w}|{p}"] = [[de, en] for _, de, en in got[:MAX]]
    out = fill(out)
json.dump(out, open("examples.json", "w"), ensure_ascii=False, separators=(",", ":"))
print(len(out), "entries with examples,", sum(len(v) for v in out.values()), "sentences", file=sys.stderr)
