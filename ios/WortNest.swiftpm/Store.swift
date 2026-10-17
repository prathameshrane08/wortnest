import Foundation
import SwiftUI
import AVFoundation

// MARK: - Model

struct Entry: Identifiable, Hashable {
    let id: Int
    let word: String
    let pos: String
    let gender: String      // m, f, n, p (plural only) or empty
    let plural: String
    let glosses: [String]
    let score: Int

    var key: String { word + "|" + pos }
    var isNoun: Bool { pos == "noun" }
    var article: String? { isNoun ? Gender.article(gender) : nil }
    var posName: String { Entry.posNames[pos] ?? pos }

    static let posNames = [
        "noun": "noun", "verb": "verb", "adj": "adjective", "adv": "adverb", "prep": "preposition",
        "conj": "conjunction", "pron": "pronoun", "intj": "interjection", "num": "number",
        "det": "determiner", "particle": "particle", "phrase": "phrase", "article": "article", "postp": "postposition"
    ]
}

struct Verb: Codable {
    let t: String           // weak, strong, mixed, irregular, present (modal)
    let a: [String]         // auxiliaries
    let pp: String
    let p1: String
    let pr: [String]
    let pt: [String]
    let k1: [String]
    let k2: [String]
    let im: [String]
}

struct VerbRoute: Hashable { let infinitive: String }

enum Gender {
    static func article(_ g: String) -> String? {
        ["m": "der", "f": "die", "n": "das", "p": "die"][g]
    }

    static func color(_ g: String) -> Color {
        switch g {
        case "m": return .blue
        case "f": return .red
        case "n": return .green
        case "p": return .purple
        default: return .primary
        }
    }

    /// A hint from the noun's ending, phrased as a rule or as an exception to one.
    static func rule(for word: String, gender: String) -> String? {
        let l = word.lowercased()
        let rules: [(String, [String])] = [
            ("f", ["ung", "heit", "keit", "schaft", "ion", "tät", "ik", "ie", "ei", "enz", "anz", "ur"]),
            ("n", ["chen", "lein", "ment", "tum", "um"]),
            ("m", ["ling", "ismus", "or", "ist", "ant", "ig", "ich"])
        ]
        for (g, endings) in rules {
            for e in endings where l.hasSuffix(e) && l.count > e.count + 1 {
                let art = article(g) ?? ""
                return g == gender ? "Words ending in -\(e) are usually \(art)." : "Exception: most words ending in -\(e) are \(art)."
            }
        }
        return nil
    }
}

struct SearchResult {
    var exact: [Entry] = []
    var viaForm: [Entry] = []
    var lemmas: [String] = []
    var english: [Entry] = []
    var prefix: [Entry] = []
    var isEmpty: Bool { exact.isEmpty && viaForm.isEmpty && english.isEmpty && prefix.isEmpty }
}

// MARK: - Store

@MainActor
final class Store: ObservableObject {
    @Published private(set) var loaded = false
    @Published private(set) var loadError: String?
    @Published private(set) var saved: [String: Card] = [:]
    @Published private(set) var missedNouns: [String] = []

    private(set) var entries: [Entry] = []
    private(set) var verbs: [String: Verb] = [:]
    private(set) var examples: [String: [[String]]] = [:]   // "word|pos" -> [[German, English]]
    private(set) var quizNouns: [Int] = []
    private(set) var verbList: [String] = []
    private var forms: [String: [String]] = [:]
    private var byLower: [String: [Int]] = [:]
    private var lowerWords: [String] = []
    private var glossSegments: [[String]] = []
    private var firstSegments: [[String]] = []

    private let defaults = UserDefaults.standard

    init() {
        if let data = defaults.data(forKey: "saved"), let s = try? JSONDecoder().decode([String: Card].self, from: data) {
            saved = s
        }
        missedNouns = defaults.stringArray(forKey: "missedNouns") ?? []
    }

    func load() async {
        guard !loaded else { return }
        do {
            let built = try await Task.detached(priority: .userInitiated) { try Store.build() }.value
            entries = built.entries
            verbs = built.verbs
            examples = built.examples
            forms = built.forms
            byLower = built.byLower
            lowerWords = built.lowerWords
            glossSegments = built.glossSegments
            firstSegments = built.firstSegments
            quizNouns = built.quizNouns
            verbList = built.verbList
            loaded = true
        } catch {
            loadError = "The dictionary files could not be read: \(error.localizedDescription)"
        }
    }

    private struct Built {
        var entries: [Entry] = []
        var verbs: [String: Verb] = [:]
        var examples: [String: [[String]]] = [:]
        var forms: [String: [String]] = [:]
        var byLower: [String: [Int]] = [:]
        var lowerWords: [String] = []
        var glossSegments: [[String]] = []
        var firstSegments: [[String]] = []
        var quizNouns: [Int] = []
        var verbList: [String] = []
    }

    nonisolated private static func resource(_ name: String) throws -> Data {
        // App packages copy resources into the main app bundle.
        guard let url = Bundle.main.url(forResource: name, withExtension: "json") else {
            throw CocoaError(.fileNoSuchFile)
        }
        return try Data(contentsOf: url)
    }

    /// Splits a gloss list into comparable segments: lowercase, no parentheses, split on , and ;
    nonisolated private static func segments(_ s: String) -> [String] {
        var t = s.lowercased()
        while let open = t.firstIndex(of: "("), let close = t[open...].firstIndex(of: ")") {
            t.replaceSubrange(open...close, with: " ")
        }
        return t.split(whereSeparator: { $0 == "," || $0 == ";" || $0 == "|" })
            .map { seg in
                var x = seg.trimmingCharacters(in: .whitespaces)
                for p in ["to ", "a ", "an ", "the "] where x.hasPrefix(p) {
                    x.removeFirst(p.count)
                    break
                }
                return x
            }
            .filter { !$0.isEmpty }
    }

    nonisolated private static func build() throws -> Built {
        var b = Built()
        let raw = try JSONSerialization.jsonObject(with: resource("dict")) as? [[Any]] ?? []
        b.entries.reserveCapacity(raw.count)
        for (i, r) in raw.enumerated() where r.count >= 6 {
            let e = Entry(
                id: i,
                word: r[0] as? String ?? "",
                pos: r[1] as? String ?? "",
                gender: r[2] as? String ?? "",
                plural: r[3] as? String ?? "",
                glosses: r[4] as? [String] ?? [],
                score: (r[5] as? NSNumber)?.intValue ?? 0
            )
            b.entries.append(e)
        }
        b.verbs = try JSONDecoder().decode([String: Verb].self, from: resource("verbs"))
        b.forms = try JSONDecoder().decode([String: [String]].self, from: resource("forms"))
        b.examples = (try? JSONDecoder().decode([String: [[String]]].self, from: resource("examples"))) ?? [:]

        var seenNouns = Set<String>()
        for e in b.entries {
            let l = e.word.lowercased()
            b.lowerWords.append(l)
            b.byLower[l, default: []].append(e.id)
            b.glossSegments.append(segments(e.glosses.joined(separator: "|")))
            b.firstSegments.append(segments(e.glosses.first ?? ""))
            if e.isNoun, "mfn".contains(e.gender), !e.gender.isEmpty, e.score > 0,
               e.word.count >= 3, e.word.first?.isUppercase == true,
               e.word.allSatisfy({ $0.isLetter }), !seenNouns.contains(e.word) {
                seenNouns.insert(e.word)
                b.quizNouns.append(e.id)
            }
        }
        let verbScore: [String: Int] = Dictionary(
            b.entries.filter { $0.pos == "verb" }.map { ($0.word, $0.score) },
            uniquingKeysWith: max
        )
        b.verbList = b.verbs.keys
            .filter { $0.allSatisfy { $0.isLetter && $0.isLowercase } }
            .sorted { (verbScore[$0] ?? 0) > (verbScore[$1] ?? 0) }
        return b
    }

    // MARK: Lookup

    func entries(for word: String) -> [Entry] {
        (byLower[word.lowercased()] ?? []).map { entries[$0] }
    }

    func entry(word: String, pos: String) -> Entry? {
        entries(for: word).first { $0.word == word && $0.pos == pos }
    }

    func verbEntry(_ inf: String) -> Entry? { entry(word: inf, pos: "verb") }

    func search(_ query: String) -> SearchResult {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return SearchResult() }
        let lq = q.lowercased()
        var r = SearchResult()
        var seen = Set<Int>()
        for i in byLower[lq] ?? [] { r.exact.append(entries[i]); seen.insert(i) }
        r.lemmas = forms[lq] ?? []
        for lemma in r.lemmas {
            for i in byLower[lemma.lowercased()] ?? [] where !seen.contains(i) {
                r.viaForm.append(entries[i]); seen.insert(i)
            }
        }
        r.english = searchEnglish(lq)
        if lq.count >= 2 {
            for (i, w) in lowerWords.enumerated() where r.prefix.count < 15 {
                if !seen.contains(i), w.hasPrefix(lq), w != lq { r.prefix.append(entries[i]) }
            }
        }
        return r
    }

    func searchEnglish(_ query: String, limit: Int = 40) -> [Entry] {
        var lq = query.lowercased().trimmingCharacters(in: .whitespaces)
        if lq.hasPrefix("to ") { lq.removeFirst(3) }
        guard lq.count >= 2 else { return [] }
        var tiers: [[Int]] = [[], [], [], []]
        for (i, segs) in glossSegments.enumerated() {
            var best = 4
            for s in segs {
                guard s.contains(lq) else { continue }
                if s == lq { best = 0; break }
                if Store.startsWithWord(s, lq) { best = min(best, 2) } else if Store.containsWord(s, lq) { best = min(best, 3) }
            }
            if best == 0 && !firstSegments[i].contains(lq) { best = 1 }
            if best < 4 && (best < 3 || tiers[3].count < limit) { tiers[best].append(i) }
        }
        return tiers.joined().prefix(limit).map { entries[$0] }
    }

    nonisolated private static func startsWithWord(_ s: String, _ q: String) -> Bool {
        guard s.hasPrefix(q) else { return false }
        let rest = s.dropFirst(q.count)
        return rest.first.map { !$0.isLetter } ?? true
    }

    nonisolated private static func containsWord(_ s: String, _ q: String) -> Bool {
        var start = s.startIndex
        while let range = s.range(of: q, range: start..<s.endIndex) {
            let beforeOK = range.lowerBound == s.startIndex || !s[s.index(before: range.lowerBound)].isLetter
            let afterOK = range.upperBound == s.endIndex || !s[range.upperBound].isLetter
            if beforeOK && afterOK { return true }
            start = s.index(after: range.lowerBound)
        }
        return false
    }

    /// Finds the noun, or for a compound the longest known noun at its end (Kinderzimmer -> Zimmer).
    func nounLookup(_ word: String) -> (entry: Entry, compound: String?)? {
        let lw = word.trimmingCharacters(in: .whitespaces).lowercased()
        guard !lw.isEmpty else { return nil }
        let isNoun: (Entry) -> Bool = { $0.isNoun && !$0.gender.isEmpty }
        if let e = entries(for: lw).first(where: isNoun) { return (e, nil) }
        let chars = Array(lw)
        guard chars.count > 3 else { return nil }
        for s in 1..<(chars.count - 2) {
            let tail = String(chars[s...])
            if let e = entries(for: tail).first(where: { isNoun($0) && $0.gender != "p" }) {
                let shown = word.trimmingCharacters(in: .whitespaces)
                return (e, shown.prefix(1).uppercased() + shown.dropFirst())
            }
        }
        return nil
    }

    func findVerbs(_ query: String) -> [String] {
        let lq = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !lq.isEmpty else { return [] }
        var out: [String] = []
        func push(_ w: String) { if verbs[w] != nil && !out.contains(w) { out.append(w) } }
        if verbs[lq] != nil { push(lq) }
        (forms[lq] ?? []).forEach(push)
        let bits = lq.split(separator: " ").map(String.init)
        if bits.count == 2 {   // "fing an" -> anfangen
            (forms[bits[0]] ?? [bits[0]]).forEach { push(bits[1] + $0.lowercased()) }
        }
        if out.isEmpty || lq.hasPrefix("to ") {
            searchEnglish(lq).filter { $0.pos == "verb" }.forEach { push($0.word) }
        }
        if lq.count >= 2 {
            for w in verbList where out.count < 12 && w.hasPrefix(lq) { push(w) }
        }
        return Array(out.prefix(12))
    }

    // MARK: Saved words and quiz memory

    func isSaved(_ e: Entry) -> Bool { saved[e.key] != nil }

    func toggleSaved(_ e: Entry) {
        if saved[e.key] != nil { saved[e.key] = nil } else { saved[e.key] = Card() }
        persistSaved()
    }

    func addCommonNouns(_ n: Int = 20) {
        for i in quizNouns.prefix(n) where saved[entries[i].key] == nil {
            var c = Card()
            c.added = Date(timeIntervalSinceNow: Double(i) / 1000)   // keep the list order
            saved[entries[i].key] = c
        }
        persistSaved()
    }

    func grade(_ key: String, _ grade: Int) {
        guard let c = saved[key] else { return }
        if c.isNew { defaults.set(["day": Store.today, "n": newDoneToday + 1], forKey: "newToday") }
        saved[key] = Srs.next(c, grade: grade)
        persistSaved()
    }

    private static var today: String { Date().formatted(date: .numeric, time: .omitted) }

    var newDoneToday: Int {
        guard let t = defaults.dictionary(forKey: "newToday"), t["day"] as? String == Store.today else { return 0 }
        return t["n"] as? Int ?? 0
    }

    var newLeftToday: Int { max(0, Srs.newPerDay - newDoneToday) }

    /// What to study now: due learning and review cards first (oldest first), then today's share of new cards.
    func studyQueue(now: Date = Date()) -> [String] {
        let due = saved.filter { !$0.value.isNew && $0.value.due <= now }.sorted { $0.value.due < $1.value.due }.map(\.key)
        let fresh = saved.filter { $0.value.isNew }.sorted { $0.value.added < $1.value.added }.prefix(newLeftToday).map(\.key)
        if !due.isEmpty || !fresh.isEmpty { return due + fresh }
        // Rather than make you wait a few minutes, show a learning card early.
        return saved.filter { $0.value.isLearning && $0.value.due <= now + Srs.learnAhead }
            .sorted { $0.value.due < $1.value.due }.prefix(1).map(\.key)
    }

    struct Counts { var fresh = 0, learning = 0, review = 0, tomorrow = 0, known = 0 }

    func counts(now: Date = Date()) -> Counts {
        let v = saved.values
        let cal = Calendar.current
        let endOfTomorrow = cal.date(byAdding: .day, value: 2, to: cal.startOfDay(for: now)) ?? now
        return Counts(
            fresh: min(v.filter(\.isNew).count, newLeftToday),
            learning: v.filter { $0.isLearning && $0.due <= now + Srs.learnAhead }.count,
            review: v.filter { !$0.isNew && $0.step < 0 && $0.due <= now }.count,
            tomorrow: v.filter { !$0.isNew && $0.due > now && $0.due < endOfTomorrow }.count,
            known: v.filter { $0.step < 0 && $0.ivl >= 21 }.count
        )
    }

    func savedEntries() -> [Entry] {
        saved.keys.sorted().compactMap { key in
            let parts = key.split(separator: "|", maxSplits: 1).map(String.init)
            return parts.count == 2 ? entry(word: parts[0], pos: parts[1]) : nil
        }
    }

    private func persistSaved() {
        if let data = try? JSONEncoder().encode(saved) { defaults.set(data, forKey: "saved") }
    }

    func recordQuiz(_ word: String, correct: Bool) {
        if correct { missedNouns.removeAll { $0 == word } } else if !missedNouns.contains(word) { missedNouns.append(word) }
        defaults.set(missedNouns, forKey: "missedNouns")
    }
}

// MARK: - Conjugation

enum Conjugation {
    static let pronouns = ["ich", "du", "er/sie/es", "wir", "ihr", "sie/Sie"]
    static let aux: [String: [String: [String]]] = [
        "haben": ["pr": ["habe", "hast", "hat", "haben", "habt", "haben"],
                  "pt": ["hatte", "hattest", "hatte", "hatten", "hattet", "hatten"],
                  "k2": ["hätte", "hättest", "hätte", "hätten", "hättet", "hätten"]],
        "sein": ["pr": ["bin", "bist", "ist", "sind", "seid", "sind"],
                 "pt": ["war", "warst", "war", "waren", "wart", "waren"],
                 "k2": ["wäre", "wärest", "wäre", "wären", "wäret", "wären"]],
        "werden": ["pr": ["werde", "wirst", "wird", "werden", "werdet", "werden"],
                   "k2": ["würde", "würdest", "würde", "würden", "würdet", "würden"]]
    ]
    static let particles = ["zurück", "zusammen", "weiter", "heraus", "herein", "hinaus", "hinein", "vorbei", "entgegen",
                            "nach", "fort", "fest", "frei", "hoch", "heim", "weg", "auf", "aus", "bei", "ein", "mit",
                            "vor", "zu", "ab", "an", "um", "her", "hin", "los", "dar"]

    static func auxiliaries(_ v: Verb) -> [String] {
        let a = v.a.filter { $0 == "haben" || $0 == "sein" }
        return a.isEmpty ? ["haben"] : a
    }

    static func mainAux(_ v: Verb) -> String {
        let a = auxiliaries(v)
        return a.contains("sein") && !a.contains("haben") ? "sein" : a[0]
    }

    static func separablePrefix(_ v: Verb, _ inf: String) -> String? {
        guard let f = v.pr.first, let sp = f.lastIndex(of: " ") else { return nil }
        let p = String(f[f.index(after: sp)...])
        return inf.hasPrefix(p) && particles.contains(p) ? p : nil
    }

    /// The regular (weak) forms the verb would have, used to flag irregular ones.
    static func regular(_ inf: String, sep: String?) -> (pr: [String], pt: [String]) {
        let base = sep.map { String(inf.dropFirst($0.count)) } ?? inf
        var stem = base
        if base.hasSuffix("eln") || base.hasSuffix("ern") { stem = String(base.dropLast()) }
        else if base.hasSuffix("en") { stem = String(base.dropLast(2)) }
        else if base.hasSuffix("n") { stem = String(base.dropLast()) }
        let last = stem.last.map(String.init) ?? ""
        let beforeLast = stem.dropLast().last.map(String.init) ?? ""
        let needsE = ["d", "t"].contains(last) || (["m", "n"].contains(last) && !"aeioulrh".contains(beforeLast) && !beforeLast.isEmpty)
        let ins = needsE ? "e" : ""
        let sibilant = ["s", "ß", "x", "z"].contains(last)
        var pr = [stem + "e", stem + (sibilant ? "t" : ins + "st"), stem + ins + "t", base, stem + ins + "t", base]
        if base.hasSuffix("eln") { pr[0] = String(base.dropLast(3)) + "le" }
        let pt = ["te", "test", "te", "ten", "tet", "ten"].map { stem + ins + $0 }
        let add: (String) -> String = { s in sep.map { s + " " + $0 } ?? s }
        return (pr.map(add), pt.map(add))
    }

    struct Table: Identifiable {
        let title: String
        let rows: [(person: String, form: String, irregular: Bool)]
        let showEndings: Bool
        var id: String { title }
    }

    static func tables(_ v: Verb, _ inf: String) -> [Table] {
        let sep = separablePrefix(v, inf)
        let reg = regular(inf, sep: sep)
        let a = aux[mainAux(v)]!, w = aux["werden"]!
        let auxInf = mainAux(v)
        func rows(_ forms: [String], irregular: [Bool]? = nil, persons: [String] = pronouns) -> [(person: String, form: String, irregular: Bool)] {
            forms.enumerated().compactMap { i, f in
                f.isEmpty || f == "-" ? nil : (persons[i], f, irregular?[i] ?? false)
            }
        }
        var k2 = v.k2.contains(where: { !$0.isEmpty }) ? v.k2 : w["k2"]!.map { $0 + " " + inf }
        if let sep {   // main-clause word order: finge an, not anfinge
            k2 = k2.map { f in !f.contains(" ") && f.hasPrefix(sep) ? String(f.dropFirst(sep.count)) + " " + sep : f }
        }
        var t: [Table] = [
            Table(title: "Präsens", rows: rows(v.pr, irregular: zip(v.pr, reg.pr).map { !$0.isEmpty && $0 != $1 }), showEndings: true),
            Table(title: "Präteritum", rows: rows(v.pt, irregular: zip(v.pt, reg.pt).map { !$0.isEmpty && $0 != $1 }), showEndings: true),
            Table(title: "Perfekt", rows: rows(a["pr"]!.map { "\($0) \(v.pp)" }), showEndings: false),
            Table(title: "Plusquamperfekt", rows: rows(a["pt"]!.map { "\($0) \(v.pp)" }), showEndings: false),
            Table(title: "Futur I", rows: rows(w["pr"]!.map { "\($0) \(inf)" }), showEndings: false),
            Table(title: "Futur II", rows: rows(w["pr"]!.map { "\($0) \(v.pp) \(auxInf)" }), showEndings: false),
            Table(title: "Konjunktiv I", rows: rows(v.k1), showEndings: false),
            Table(title: "Konjunktiv II", rows: rows(k2), showEndings: false),
            Table(title: "Konjunktiv II Perfekt", rows: rows(a["k2"]!.map { "\($0) \(v.pp)" }), showEndings: false),
            Table(title: "würde + Infinitiv", rows: rows(w["k2"]!.map { "\($0) \(inf)" }), showEndings: false)
        ]
        if let du = v.im.first, !du.isEmpty, du != "-" {
            let sie = (v.pr.last ?? "").split(separator: " ", maxSplits: 1).map(String.init)
            let sieForm = sie.isEmpty ? "" : ([sie[0], "Sie"] + sie.dropFirst()).joined(separator: " ")
            t.append(Table(title: "Imperativ", rows: rows([du + "!", (v.im.count > 1 ? v.im[1] : "") + "!", sieForm + "!"], persons: ["du", "ihr", "Sie"]), showEndings: false))
        }
        t.append(Table(title: "Partizipien", rows: rows([v.p1, v.pp], persons: ["Partizip I", "Partizip II"]), showEndings: false))
        return t
    }

    static func kindLabel(_ v: Verb) -> String? {
        ["strong": "Strong (irregular)", "weak": "Weak (regular)", "mixed": "Mixed", "irregular": "Irregular", "present": "Modal verb"][v.t]
    }
}

// MARK: - Speech

final class Speaker {
    static let shared = Speaker()
    private let synth = AVSpeechSynthesizer()

    func say(_ text: String) {
        synth.stopSpeaking(at: .immediate)
        let u = AVSpeechUtterance(string: text)
        u.voice = AVSpeechSynthesisVoice(language: "de-DE")
        u.rate = 0.45
        synth.speak(u)
    }
}
