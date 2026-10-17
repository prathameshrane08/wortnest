package com.prathameshrane.wortnest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// ---------- Model ----------

data class Entry(
    val id: Int,
    val word: String,
    val pos: String,
    val gender: String,       // m, f, n, p (plural only) or empty
    val plural: String,
    val glosses: List<String>,
    val score: Int,
) {
    val key: String get() = "$word|$pos"
    val isNoun: Boolean get() = pos == "noun"
    val article: String? get() = if (isNoun) Gender.article(gender) else null
    val posName: String get() = POS_NAMES[pos] ?: pos
    val spoken: String get() = listOfNotNull(article, word).joinToString(" ")
}

val POS_NAMES = mapOf(
    "noun" to "noun", "verb" to "verb", "adj" to "adjective", "adv" to "adverb", "prep" to "preposition",
    "conj" to "conjunction", "pron" to "pronoun", "intj" to "interjection", "num" to "number",
    "det" to "determiner", "particle" to "particle", "phrase" to "phrase", "article" to "article",
    "postp" to "postposition",
)

data class Verb(
    val t: String,            // weak, strong, mixed, irregular, present (modal)
    val a: List<String>,      // auxiliaries
    val pp: String,
    val p1: String,
    val pr: List<String>,
    val pt: List<String>,
    val k1: List<String>,
    val k2: List<String>,
    val im: List<String>,
)

data class SearchResult(
    val exact: List<Entry> = emptyList(),
    val viaForm: List<Entry> = emptyList(),
    val lemmas: List<String> = emptyList(),
    val english: List<Entry> = emptyList(),
    val prefix: List<Entry> = emptyList(),
) {
    val isEmpty: Boolean get() = exact.isEmpty() && viaForm.isEmpty() && english.isEmpty() && prefix.isEmpty()
}

object Gender {
    fun article(g: String): String? = when (g) {
        "m" -> "der"
        "f" -> "die"
        "n" -> "das"
        "p" -> "die"
        else -> null
    }

    private val rules = listOf(
        "f" to listOf("ung", "heit", "keit", "schaft", "ion", "tät", "ik", "ie", "ei", "enz", "anz", "ur"),
        "n" to listOf("chen", "lein", "ment", "tum", "um"),
        "m" to listOf("ling", "ismus", "or", "ist", "ant", "ig", "ich"),
    )

    /** A hint from the noun's ending, phrased as a rule or as an exception to one. */
    fun rule(word: String, gender: String): String? {
        val l = word.lowercase()
        for ((g, endings) in rules) {
            for (e in endings) {
                if (l.endsWith(e) && l.length > e.length + 1) {
                    val art = article(g)
                    return if (g == gender) "Words ending in -$e are usually $art."
                    else "Exception: most words ending in -$e are $art."
                }
            }
        }
        return null
    }
}

// ---------- Repository ----------

class Repository private constructor(
    val entries: List<Entry>,
    val verbs: Map<String, Verb>,
    private val forms: Map<String, List<String>>,
    /** "word|pos" to example sentences, each [German, English]. */
    val examples: Map<String, List<List<String>>>,
) {
    private val byLower = HashMap<String, MutableList<Int>>()
    private val lowerWords = ArrayList<String>(entries.size)

    /** Gloss pieces per entry for English search: all glosses, and the first gloss alone. Built on first use. */
    private val glossIndex: Pair<List<List<String>>, List<List<String>>> by lazy {
        val all = ArrayList<List<String>>(entries.size)
        val first = ArrayList<List<String>>(entries.size)
        for (e in entries) {
            val perGloss = e.glosses.map { segments(it) }
            all.add(perGloss.flatten())
            first.add(perGloss.firstOrNull().orEmpty())
        }
        all to first
    }

    /** Builds the English index ahead of the first search; call off the main thread. */
    fun warmUp() { glossIndex }
    val quizNouns = ArrayList<Int>()
    val verbList: List<String>

    init {
        val seenNouns = HashSet<String>()
        for (e in entries) {
            val l = e.word.lowercase()
            lowerWords.add(l)
            byLower.getOrPut(l) { mutableListOf() }.add(e.id)
            if (e.isNoun && (e.gender == "m" || e.gender == "f" || e.gender == "n") && e.score > 0 && e.word.length >= 3 &&
                e.word[0].isUpperCase() && e.word.all { it.isLetter() } && seenNouns.add(e.word)
            ) quizNouns.add(e.id)
        }
        val verbScore = HashMap<String, Int>()
        for (e in entries) if (e.pos == "verb") verbScore[e.word] = maxOf(verbScore[e.word] ?: 0, e.score)
        verbList = verbs.keys.filter { w -> w.all { it.isLetter() && it.isLowerCase() } }
            .sortedByDescending { verbScore[it] ?: 0 }
    }

    companion object {
        fun load(context: Context): Repository {
            fun read(name: String) = context.assets.open(name).bufferedReader().use { it.readText() }

            val raw = JSONArray(read("dict.json"))
            val entries = ArrayList<Entry>(raw.length())
            for (i in 0 until raw.length()) {
                val r = raw.getJSONArray(i)
                entries.add(
                    Entry(
                        id = i, word = r.getString(0), pos = r.getString(1), gender = r.getString(2),
                        plural = r.getString(3), glosses = r.getJSONArray(4).strings(), score = r.optInt(5),
                    )
                )
            }
            val vj = JSONObject(read("verbs.json"))
            val verbs = HashMap<String, Verb>(vj.length())
            for (k in vj.keys()) {
                val o = vj.getJSONObject(k)
                verbs[k] = Verb(
                    t = o.optString("t"), a = o.getJSONArray("a").strings(), pp = o.optString("pp"),
                    p1 = o.optString("p1"), pr = o.getJSONArray("pr").strings(), pt = o.getJSONArray("pt").strings(),
                    k1 = o.getJSONArray("k1").strings(), k2 = o.getJSONArray("k2").strings(),
                    im = o.getJSONArray("im").strings(),
                )
            }
            val fj = JSONObject(read("forms.json"))
            val forms = HashMap<String, List<String>>(fj.length())
            for (k in fj.keys()) forms[k] = fj.getJSONArray(k).strings()
            val examples = HashMap<String, List<List<String>>>()
            runCatching {
                val ej = JSONObject(read("examples.json"))
                for (k in ej.keys()) {
                    val a = ej.getJSONArray(k)
                    examples[k] = List(a.length()) { a.getJSONArray(it).strings() }
                }
            }
            return Repository(entries, verbs, forms, examples)
        }

        private fun JSONArray.strings(): List<String> = List(length()) { optString(it) }
        private val parens = Regex("\\([^)]*\\)")

        /** Lowercase gloss pieces without parentheses, split on , ; and |, with "to"/"a"/"the" removed. */
        private fun segments(s: String): List<String> {
            val noParens = if ('(' in s) s.lowercase().replace(parens, " ") else s.lowercase()
            return noParens.split(',', ';', '|').map { seg ->
                var x = seg.trim()
                for (p in listOf("to ", "a ", "an ", "the ")) if (x.startsWith(p)) { x = x.removePrefix(p); break }
                x
            }.filter { it.isNotEmpty() }
        }

        private fun startsWithWord(s: String, q: String): Boolean =
            s.startsWith(q) && (s.length == q.length || !s[q.length].isLetter())

        private fun containsWord(s: String, q: String): Boolean {
            var from = 0
            while (true) {
                val i = s.indexOf(q, from)
                if (i < 0) return false
                val beforeOk = i == 0 || !s[i - 1].isLetter()
                val afterOk = i + q.length == s.length || !s[i + q.length].isLetter()
                if (beforeOk && afterOk) return true
                from = i + 1
            }
        }
    }

    fun entriesFor(word: String): List<Entry> = byLower[word.lowercase()].orEmpty().map { entries[it] }
    fun entry(word: String, pos: String): Entry? = entriesFor(word).firstOrNull { it.word == word && it.pos == pos }
    fun verbEntry(inf: String): Entry? = entry(inf, "verb")

    fun search(query: String): SearchResult {
        val lq = query.trim().lowercase()
        if (lq.isEmpty()) return SearchResult()
        val seen = HashSet<Int>()
        val exact = byLower[lq].orEmpty().map { seen.add(it); entries[it] }
        val lemmas = forms[lq].orEmpty()
        val via = ArrayList<Entry>()
        for (lemma in lemmas) for (i in byLower[lemma.lowercase()].orEmpty()) if (seen.add(i)) via.add(entries[i])
        val prefix = ArrayList<Entry>()
        if (lq.length >= 2) {
            for (i in lowerWords.indices) {
                if (prefix.size >= 15) break
                val w = lowerWords[i]
                if (w != lq && w.startsWith(lq) && i !in seen) prefix.add(entries[i])
            }
        }
        return SearchResult(exact, via, lemmas, searchEnglish(lq), prefix)
    }

    fun searchEnglish(query: String, limit: Int = 40): List<Entry> {
        val lq = query.trim().lowercase().removePrefix("to ").trim()
        if (lq.length < 2) return emptyList()
        val (glossSegments, firstSegments) = glossIndex
        val tiers = List(4) { ArrayList<Int>() }
        for (i in glossSegments.indices) {
            var best = 4
            for (s in glossSegments[i]) {
                if (!s.contains(lq)) continue
                if (s == lq) { best = 0; break }
                if (startsWithWord(s, lq)) best = minOf(best, 2)
                else if (containsWord(s, lq)) best = minOf(best, 3)
            }
            if (best == 0 && lq !in firstSegments[i]) best = 1
            if (best < 4 && (best < 3 || tiers[3].size < limit)) tiers[best].add(i)
        }
        return tiers.flatten().take(limit).map { entries[it] }
    }

    /** The noun, or for a compound the longest known noun at its end (Kinderzimmer -> Zimmer). */
    fun nounLookup(word: String): Pair<Entry, String?>? {
        val shown = word.trim()
        val lw = shown.lowercase()
        if (lw.isEmpty()) return null
        entriesFor(lw).firstOrNull { it.isNoun && it.gender.isNotEmpty() }?.let { return it to null }
        for (s in 1 until lw.length - 2) {
            val hit = entriesFor(lw.substring(s)).firstOrNull { it.isNoun && it.gender.isNotEmpty() && it.gender != "p" }
            if (hit != null) return hit to shown.replaceFirstChar { it.uppercase() }
        }
        return null
    }

    fun findVerbs(query: String): List<String> {
        val lq = query.trim().lowercase()
        if (lq.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        fun push(w: String) { if (verbs.containsKey(w) && w !in out) out.add(w) }
        push(lq)
        forms[lq].orEmpty().forEach(::push)
        val bits = lq.split(Regex("\\s+"))
        if (bits.size == 2) (forms[bits[0]] ?: listOf(bits[0])).forEach { push(bits[1] + it.lowercase()) }   // fing an -> anfangen
        if (out.isEmpty() || lq.startsWith("to ")) searchEnglish(lq).filter { it.pos == "verb" }.forEach { push(it.word) }
        if (lq.length >= 2) for (w in verbList) { if (out.size >= 12) break; if (w.startsWith(lq)) push(w) }
        return out.take(12)
    }
}

// ---------- Conjugation ----------

object Conjugation {
    val pronouns = listOf("ich", "du", "er/sie/es", "wir", "ihr", "sie/Sie")
    private val haben = mapOf(
        "pr" to listOf("habe", "hast", "hat", "haben", "habt", "haben"),
        "pt" to listOf("hatte", "hattest", "hatte", "hatten", "hattet", "hatten"),
        "k2" to listOf("hätte", "hättest", "hätte", "hätten", "hättet", "hätten"),
    )
    private val sein = mapOf(
        "pr" to listOf("bin", "bist", "ist", "sind", "seid", "sind"),
        "pt" to listOf("war", "warst", "war", "waren", "wart", "waren"),
        "k2" to listOf("wäre", "wärest", "wäre", "wären", "wäret", "wären"),
    )
    private val werdenPr = listOf("werde", "wirst", "wird", "werden", "werdet", "werden")
    private val wuerde = listOf("würde", "würdest", "würde", "würden", "würdet", "würden")
    private val particles = listOf(
        "zurück", "zusammen", "weiter", "heraus", "herein", "hinaus", "hinein", "vorbei", "entgegen", "nach",
        "fort", "fest", "frei", "hoch", "heim", "weg", "auf", "aus", "bei", "ein", "mit", "vor", "zu", "ab",
        "an", "um", "her", "hin", "los", "dar",
    )

    fun auxiliaries(v: Verb): List<String> = v.a.filter { it == "haben" || it == "sein" }.ifEmpty { listOf("haben") }
    fun mainAux(v: Verb): String = auxiliaries(v).let { if ("sein" in it && "haben" !in it) "sein" else it[0] }
    fun auxPresent(v: Verb): List<String> = (if (mainAux(v) == "sein") sein else haben).getValue("pr")

    fun separablePrefix(v: Verb, inf: String): String? {
        val f = v.pr.firstOrNull() ?: return null
        val sp = f.lastIndexOf(' ')
        if (sp < 0) return null
        val p = f.substring(sp + 1)
        return if (inf.startsWith(p) && p in particles) p else null
    }

    /** The regular (weak) forms the verb would have, used to flag irregular ones. */
    private fun regular(inf: String, sep: String?): Pair<List<String>, List<String>> {
        val base = if (sep != null) inf.removePrefix(sep) else inf
        val stem = when {
            base.endsWith("eln") || base.endsWith("ern") -> base.dropLast(1)
            base.endsWith("en") -> base.dropLast(2)
            base.endsWith("n") -> base.dropLast(1)
            else -> base
        }
        val last = stem.lastOrNull()
        val beforeLast = stem.dropLast(1).lastOrNull()
        val needsE = last == 'd' || last == 't' ||
            ((last == 'm' || last == 'n') && beforeLast != null && beforeLast !in "aeioulrh")
        val ins = if (needsE) "e" else ""
        val sibilant = last in listOf('s', 'ß', 'x', 'z')
        val pr = mutableListOf(stem + "e", stem + (if (sibilant) "t" else ins + "st"), stem + ins + "t", base, stem + ins + "t", base)
        if (base.endsWith("eln")) pr[0] = base.dropLast(3) + "le"
        val pt = listOf("te", "test", "te", "ten", "tet", "ten").map { stem + ins + it }
        val add = { s: String -> if (sep != null) "$s $sep" else s }
        return pr.map(add) to pt.map(add)
    }

    data class Row(val person: String, val form: String, val irregular: Boolean)
    data class Table(val title: String, val rows: List<Row>, val showEndings: Boolean)

    fun tables(v: Verb, inf: String): List<Table> {
        val sep = separablePrefix(v, inf)
        val (regPr, regPt) = regular(inf, sep)
        val aux = if (mainAux(v) == "sein") sein else haben
        val auxInf = mainAux(v)
        fun rows(forms: List<String>, irregular: List<Boolean>? = null, persons: List<String> = pronouns) =
            forms.mapIndexedNotNull { i, f ->
                if (f.isEmpty() || f == "-" || i >= persons.size) null else Row(persons[i], f, irregular?.getOrNull(i) ?: false)
            }
        var k2 = if (v.k2.any { it.isNotEmpty() }) v.k2 else wuerde.map { "$it $inf" }
        if (sep != null) k2 = k2.map { f -> if (' ' !in f && f.startsWith(sep)) f.removePrefix(sep) + " " + sep else f }   // finge an, not anfinge
        val t = mutableListOf(
            Table("Präsens", rows(v.pr, v.pr.zip(regPr) { a, b -> a.isNotEmpty() && a != b }), true),
            Table("Präteritum", rows(v.pt, v.pt.zip(regPt) { a, b -> a.isNotEmpty() && a != b }), true),
            Table("Perfekt", rows(aux.getValue("pr").map { "$it ${v.pp}" }), false),
            Table("Plusquamperfekt", rows(aux.getValue("pt").map { "$it ${v.pp}" }), false),
            Table("Futur I", rows(werdenPr.map { "$it $inf" }), false),
            Table("Futur II", rows(werdenPr.map { "$it ${v.pp} $auxInf" }), false),
            Table("Konjunktiv I", rows(v.k1), false),
            Table("Konjunktiv II", rows(k2), false),
            Table("Konjunktiv II Perfekt", rows(aux.getValue("k2").map { "$it ${v.pp}" }), false),
            Table("würde + Infinitiv", rows(wuerde.map { "$it $inf" }), false),
        )
        val du = v.im.firstOrNull().orEmpty()
        if (du.isNotEmpty() && du != "-") {
            val sie = v.pr.lastOrNull().orEmpty().split(" ", limit = 2)
            val sieForm = (listOf(sie[0], "Sie") + sie.drop(1)).joinToString(" ")
            t.add(Table("Imperativ", rows(listOf("$du!", "${v.im.getOrElse(1) { "" }}!", "$sieForm!"), persons = listOf("du", "ihr", "Sie")), false))
        }
        t.add(Table("Partizipien", rows(listOf(v.p1, v.pp), persons = listOf("Partizip I", "Partizip II")), false))
        return t
    }

    fun kindLabel(v: Verb): String? = mapOf(
        "strong" to "Strong (irregular)", "weak" to "Weak (regular)", "mixed" to "Mixed",
        "irregular" to "Irregular", "present" to "Modal verb",
    )[v.t]
}
