"""Clean examples.json for beginners: drop sentences that are hard to learn from and correct the English.

Usage: python3 tidy_examples.py, then python3 examples.py --fill-only, then python3 tidy_examples.py again

Wiktionary's example sentences include poetry, old spellings, dialect, rude jokes and notes for linguists,
and some translations have typos or are deliberately old-fashioned. Rules below remove those; FIX corrects
or rewrites individual translations and DROP removes single sentences; both come from example_fixes.tsv.
"""
import json, re

OLD_DE = r"\b(daß|muß|mußt|mußte|läßt|laß|Thor|Thür|Thier\w*|Theil\w*|Thräne\w*|Thal|Noth|Muth|Rath|thun|thut|seyn|sey|bey\w*|umb|giebt|gieng|Brod|todt|woll'n)\b|niß\b"
OLD_EN = r"\b(thou|thee|thy|thine|hast|hath|doth|dost|spake|ye|thu|yseen|wert|art thou)\b"
RUDE = r"(fuck|shit|bitch|cunt|dick\b|asshole|arsehole|whore|slut|cock\b|wank|piss|bastard|tits\b|boobs|gevögelt|ficken|fick\b|Fotze|Wichser|Hure|Nutte|Scheiß|Arsch|Neger|Schwuchtel|Kanake|Spast|Mohr|nigg|abspritz|goon|\bcum\b|porn|Nudes|Titten|Penis|Vagina)"
NOTE_EN = r"(Literally|literally:|Stress on|≈|\[|\bi\.e\.|\be\.g\.|\(place names\)|\bLit\.)"
DAYS = "Monday Tuesday Wednesday Thursday Friday Saturday Sunday January February April June July August September October November December".split()

FIX, DROP = {}, set()
for line in open("example_fixes.tsv", encoding="utf-8"):
    if line.startswith("#") or "\t" not in line:
        continue
    a, b = line.rstrip("\n").split("\t")
    if a == "-":
        DROP.add(b)
    else:
        FIX[a] = b

def bad(de, en):
    if de in DROP:
        return True
    if de in FIX:
        return False
    if "/" in de or "/" in en or "(" in de or "[" in de:
        return True                                    # verse, alternatives, editorial notes
    if re.search(r"[A-Za-zäöüß]['’](?!s\b)", de):      # dialect elisions like is’, hab', ’ne (but keep geht's)
        return True
    if re.search(r"['’][a-zäöü]", de) and not re.search(r"['’]s\b", de):
        return True
    if re.search(OLD_DE, de) or re.search(OLD_EN, en, re.I) or re.search(RUDE, de + " " + en, re.I):
        return True
    if re.search(NOTE_EN, en):
        return True
    if de[:1] in "„“\"»«" and len(de) > 60:            # quotations from books
        return True
    words = re.findall(r"[A-Za-z]+", en)
    if len(words) >= 4 and sum(w[0].isupper() for w in words) / len(words) > 0.6:
        return True                                    # a book title instead of a translation
    if len(de) > 40 and len(en) < len(de) / 3:
        return True                                    # the same, or a translation that leaves most out
    return False

def tidy(de, en):
    en = FIX.get(de, en)
    if de not in FIX:
        en = re.sub(r"\s*\([^()]*\)", "", en)          # notes like (formal), (said to a child)
        en = re.sub(r"[,;:]\s*$", "", en)
        en = re.sub(r"(?<!\.)([.!?])\s+\.$|([!?])\.$", r"\1\2", en)
        en = re.sub(r"(?<!\.)\.\.$", ".", en)
    en = re.sub(r"\s+", " ", en).strip()
    for d in DAYS:
        en = re.sub(rf"\b{d.lower()}\b", d, en)
    end = de.rstrip("“”\"»« ")[-1:]
    if end in ".!?":                                   # a full German sentence gets a full English sentence
        en = en[:1].upper() + en[1:]
        if en.rstrip("\"”’)")[-1:] not in ".!?…":
            en += end
    return en

ex = json.load(open("examples.json"))
allde = {de for v in ex.values() for de, _ in v}
for de in sorted((set(FIX) | DROP) - allde):
    print("not found:", de)
out, dropped = {}, 0
for k, v in ex.items():
    seen, keep = set(), []
    for de, en in v:
        if de.lower() in seen or bad(de, en):
            dropped += 1
            continue
        seen.add(de.lower())
        keep.append([de, tidy(de, en)])
    if keep:
        out[k] = keep
json.dump(out, open("examples.json", "w"), ensure_ascii=False, separators=(",", ":"))
print(len(out), "entries,", sum(len(v) for v in out.values()), "sentences,", dropped, "dropped")
