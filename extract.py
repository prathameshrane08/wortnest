"""Stream kaikki German JSONL on stdin; write compact app data to argv[2] dir. argv[1] = freq list."""
import json, sys, re, os

freq = {}
for i, line in enumerate(open(sys.argv[1], encoding="utf-8")):
    w, c = line.rsplit(" ", 1)
    freq[w] = int(c)
out = sys.argv[2]

POS = {"noun", "verb", "adj", "adv", "prep", "conj", "pron", "intj", "num", "det", "particle", "phrase", "article", "postp"}
entries = {}   # key (word,pos) -> entry
verbs = {}
formidx = {}   # inflected form (lower) -> set of lemma

PERS = ["first-person singular", "second-person singular", "third-person singular",
        "first-person plural", "second-person plural", "third-person plural"]

def clean(g):
    g = re.sub(r"\s+", " ", g).strip()
    return g[:140]

def find(forms, need, avoid=(), nospace=False):
    for f in forms:
        t = set(f.get("tags", []))
        if nospace and " " in f["form"]:
            continue
        if f.get("source") == "conjugation" and need <= t and not (t & set(avoid)):
            return f["form"]
    return ""

for line in sys.stdin:
    try:
        d = json.loads(line)
    except Exception:
        continue
    pos = d.get("pos")
    if pos not in POS or d.get("lang_code", "de") != "de":
        continue
    word = d.get("word", "")
    if not word or " " in word and pos not in ("phrase",):
        continue
    glosses = []
    for s in d.get("senses", []):
        tags = set(s.get("tags", []))
        if tags & {"form-of", "alt-of", "misspelling"} or not s.get("glosses"):
            continue
        g = clean(s["glosses"][-1])
        qual = [t for t in s.get("tags", []) if t in ("colloquial", "informal", "figuratively", "dated", "archaic", "slang", "vulgar", "regional", "Austria", "Switzerland", "formal")]
        if qual:
            g = "(" + ", ".join(qual) + ") " + g
        if g not in glosses:
            glosses.append(g)
    forms = d.get("forms", [])
    lw = word.lower()
    score = freq.get(lw, 0)
    if pos == "verb":
        score += sum(freq.get(f["form"].lower(), 0) for f in forms if f.get("source") == "conjugation" and " " not in f["form"]) // 3
    if not glosses:
        continue
    if score == 0 and pos != "noun":
        continue
    gender, plural = "", ""
    if pos == "noun":
        for h in d.get("head_templates", []):
            if h.get("name") == "de-noun":
                a = h.get("args", {}).get("1", "")
                if a[:1] in "mfn" and a:
                    gender = a[0]
                elif a.startswith("p"):
                    gender = "p"
                break
        if not gender:
            stags = set(t for s in d.get("senses", []) for t in s.get("tags", []))
            for t, g in (("masculine", "m"), ("feminine", "f"), ("neuter", "n")):
                if t in stags:
                    gender = g
                    break
        for f in forms:
            if f.get("tags") == ["plural"]:
                plural = f["form"]
                break
        if score == 0 and not gender:
            continue
        # keep score-0 nouns only if they are common-ish compounds: gate later by size
    key = (word, pos)
    e = entries.get(key)
    if e:
        e["g"] += [x for x in glosses if x not in e["g"]]
        e["s"] = max(e["s"], score)
        if not e.get("ge") and gender:
            e["ge"] = gender
    else:
        e = {"w": word, "p": pos, "g": glosses, "s": score}
        if gender:
            e["ge"] = gender
        if plural and plural != "-":
            e["pl"] = plural
        entries[key] = e
    for f in forms:
        fw = f["form"]
        if " " in fw or fw == word:
            continue
        if fw.lower() in freq and pos in ("verb", "noun", "adj"):
            formidx.setdefault(fw.lower(), set()).add(word)
    if pos == "verb" and word not in verbs:
        tt = [f["form"] for f in forms if "table-tags" in f.get("tags", [])]
        aux = [f["form"] for f in forms if "auxiliary" in f.get("tags", [])]
        v = {
            "t": tt[0] if tt else "",
            "a": sorted(set(aux)) or ["haben"],
            "pp": find(forms, {"participle", "past"}),
            "p1": find(forms, {"participle", "present"}),
            "pr": [find(forms, set(p.split()) | {"indicative", "present"}) for p in PERS],
            "pt": [find(forms, set(p.split()) | {"indicative", "preterite"}) for p in PERS],
            "k1": [find(forms, set(p.split()) | {"subjunctive-i"}, ("rare",)) or find(forms, set(p.split()) | {"subjunctive-i"}) for p in PERS],
            "k2": [find(forms, set(p.split()) | {"subjunctive-ii"}, nospace=True) or find(forms, set(p.split()) | {"subjunctive-ii"}) for p in PERS],
            "im": [find(forms, {"imperative", "singular"}), find(forms, {"imperative", "plural"})],
        }
        if v["pr"][2] and v["pp"]:
            verbs[word] = v

# prune: nouns with score 0 limited
ents = sorted(entries.values(), key=lambda e: -e["s"])
scored = [e for e in ents if e["s"] > 0]
unscored = [e for e in ents if e["s"] == 0][:15000]
ents = scored + unscored
for e in ents:
    e["g"] = e["g"][:5]
keepw = {e["w"] for e in ents}
verbs = {k: v for k, v in verbs.items() if k in keepw}
rows = []
for e in ents:
    rows.append([e["w"], e["p"], e.get("ge", ""), e.get("pl", ""), e["g"], e["s"]])
fi = {k: sorted(v & keepw) for k, v in formidx.items() if v & keepw and k not in {e["w"].lower() for e in ents[:3000]}}
os.makedirs(out, exist_ok=True)
json.dump(rows, open(os.path.join(out, "dict.json"), "w"), ensure_ascii=False, separators=(",", ":"))
json.dump(verbs, open(os.path.join(out, "verbs.json"), "w"), ensure_ascii=False, separators=(",", ":"))
json.dump(fi, open(os.path.join(out, "forms.json"), "w"), ensure_ascii=False, separators=(",", ":"))
print(len(rows), len(verbs), len(fi), file=sys.stderr)
