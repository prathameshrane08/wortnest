import SwiftUI

// MARK: - Levels

/// Every list from A1 up to the level, so an A2 learner also practices the A1 words.
func upTo<T>(_ lists: [[T]], _ level: Int) -> [T] {
    (1...max(1, min(3, level))).flatMap { lists[$0] }
}

func levelRange(_ level: Int) -> String { level > 1 ? "A1–\(Level.names[level])" : "A1" }

// MARK: - Words in quizzes

private func stripBrackets(_ s: String) -> String {
    s.replacingOccurrences(of: #"\s*\([^)]*\)"#, with: "", options: .regularExpression)
}

extension Entry {
    /// The first meaning without its notes in brackets, short enough for a button.
    var shortGloss: String {
        let first = glosses.first ?? ""
        var g = stripBrackets(first).components(separatedBy: ";")[0]
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: " ,", with: ",")
            .trimmingCharacters(in: .whitespaces)
        if g.isEmpty { g = first }
        if g.count > 44 {
            g = String(g.prefix(42)).replacingOccurrences(of: #"[ ,]+\S*$"#, with: "", options: .regularExpression) + "…"
        }
        return g
    }

    /// For the small Match tiles: only the first meaning when the gloss is long.
    var tileGloss: String {
        let g = shortGloss
        return g.count > 22 ? g.components(separatedBy: ", ")[0] : g
    }

    /// The colour a word is shown in: its gender colour for nouns, plain text otherwise.
    var color: Color { isNoun && !gender.isEmpty ? Gender.color(gender) : Palette.text }
}

extension Store {
    /// n random words with no repeated word or meaning, so every question has one right answer.
    func pickUnique(_ pool: [Int], _ n: Int) -> [Int] {
        var out: [Int] = [], words = Set<String>(), glosses = Set<String>()
        for i in pool.shuffled() {
            let e = entries[i]
            guard words.insert(e.word.lowercased()).inserted, glosses.insert(e.shortGloss.lowercased()).inserted else { continue }
            out.append(i)
            if out.count == n { break }
        }
        return out
    }

    /// The right answer plus three others, the same part of speech where possible.
    func options(_ i: Int, pool: [Int], showEnglish: Bool) -> [Int] {
        let e = entries[i]
        let from = pool.count < 12 ? pool + upTo(levelWords, 3) : pool
        func key(_ j: Int) -> String { (showEnglish ? entries[j].shortGloss : entries[j].word).lowercased() }
        let others = from.shuffled().prefix(4000).filter { $0 != i }
        let ordered = others.filter { entries[$0].pos == e.pos } + others.filter { entries[$0].pos != e.pos }
        var out = [i], seen: Set<String> = [key(i)]
        for j in ordered where out.count < 4 {
            if seen.insert(key(j)).inserted { out.append(j) }
        }
        return out.shuffled()
    }

    /// The words a quiz round draws from: your saved words, or every word up to your level.
    func quizPool(saved useSaved: Bool) -> [Int] {
        if useSaved {
            let s = savedEntries().filter { !$0.glosses.isEmpty }.map(\.id)
            if s.count >= 4 { return s }
        }
        return upTo(levelWords, level).filter { !entries[$0].glosses.isEmpty }
    }

    /// The same noun, verb or adjective for everyone at a level on a given day (the website picks it the same way).
    /// The pool holds every word up to the level, so B1 learners still meet A1 and A2 words.
    func wordOfDay() -> Entry? {
        var seen = Set<String>()
        let pool = entries.filter {
            (1...level).contains($0.level) && ($0.pos == "verb" || $0.pos == "adj" || ($0.isNoun && ["m", "f", "n"].contains($0.gender)))
                && seen.insert($0.word).inserted
        }
        guard !pool.isEmpty else { return nil }
        let day = Int(floor(Date().timeIntervalSince1970 / 86_400 + Double(TimeZone.current.secondsFromGMT()) / 86_400))
        return pool[(day * 7919) % pool.count]
    }
}

struct Checked {
    let ok: Bool
    var note = ""
}

private func fold(_ s: String) -> String {
    s.lowercased()
        .replacingOccurrences(of: #"[.!?;:"]"#, with: "", options: .regularExpression)
        .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        .trimmingCharacters(in: .whitespaces)
}

private func foldUmlauts(_ s: String) -> String {
    fold(s).replacingOccurrences(of: "ä", with: "ae").replacingOccurrences(of: "ö", with: "oe")
        .replacingOccurrences(of: "ü", with: "ue").replacingOccurrences(of: "ß", with: "ss")
}

private func foldEnglish(_ s: String) -> String {
    fold(s.replacingOccurrences(of: #"\([^)]*\)"#, with: " ", options: .regularExpression))
        .replacingOccurrences(of: ",", with: "")
        .replacingOccurrences(of: #"^(to|a|an|the) "#, with: "", options: .regularExpression)
        .trimmingCharacters(in: .whitespaces)
}

/// Edit distance, but only exact for 0 and 1: anything further apart is reported as 2.
private func lev(_ a: String, _ b: String) -> Int {
    let a = Array(a), b = Array(b)
    if abs(a.count - b.count) > 1 { return 2 }
    if a.isEmpty || b.isEmpty { return max(a.count, b.count) }
    var prev = Array(0...b.count)
    for x in 1...a.count {
        var cur = [x] + Array(repeating: 0, count: b.count)
        for y in 1...b.count {
            cur[y] = min(prev[y] + 1, cur[y - 1] + 1, prev[y - 1] + (a[x - 1] == b[y - 1] ? 0 : 1))
        }
        prev = cur
    }
    return prev[b.count]
}

/// German answers: ignore case, allow a missing umlaut or one typo, but a wrong article is wrong.
func checkGerman(_ given: String, _ e: Entry) -> Checked {
    var g = fold(given), art = ""
    if e.isNoun, let m = g.range(of: #"^(der|die|das) "#, options: .regularExpression) {
        art = g[m].trimmingCharacters(in: .whitespaces)
        g = String(g[m.upperBound...])
    }
    let w = fold(e.word)
    let exact = g == w
    let close = !exact && (foldUmlauts(g) == foldUmlauts(w) || (w.count >= 5 && lev(g, w) <= 1))
    if !exact && !close { return Checked(ok: false) }
    if !art.isEmpty, let right = e.article, art != right {
        return Checked(ok: false, note: "Right word, wrong article: it's \(right) \(e.word).")
    }
    return Checked(ok: true, note: close ? "Watch the spelling: \(e.word)." : "")
}

/// English answers: any meaning of the word counts, with or without "to" or "a".
@MainActor func checkEnglish(_ given: String, _ e: Entry, _ store: Store) -> Checked {
    let g = foldEnglish(given)
    guard !g.isEmpty else { return Checked(ok: false) }
    var answers = Set<String>()
    for x in store.entries(for: e.word) where x.word == e.word {
        for sense in x.glosses {
            let plain = sense.replacingOccurrences(of: #"\([^)]*\)"#, with: " ", options: .regularExpression)
            for p in plain.components(separatedBy: CharacterSet(charactersIn: ";,")) {
                let f = foldEnglish(p)
                if !f.isEmpty { answers.insert(f) }
            }
        }
    }
    if answers.contains(g) { return Checked(ok: true) }
    if let near = answers.first(where: { $0.count >= 5 && lev($0, g) <= 1 }) {
        return Checked(ok: true, note: "Watch the spelling: \(near).")
    }
    return Checked(ok: false)
}

/// Notes in brackets make the meaning hard to read in feedback; leave them out there.
func plainGlosses(_ e: Entry, _ n: Int = 2) -> String {
    e.glosses.prefix(n).map(stripBrackets).joined(separator: "; ")
}

// MARK: - Shared pieces

/// A flashcard you can tap to flip and drag sideways: right for "knew it", left for "not yet".
struct SwipeCard<Content: View>: View {
    let leftLabel: String
    let rightLabel: String
    let onTap: () -> Void
    let onSwipe: (Bool) -> Void
    @ViewBuilder let content: Content
    @State private var offset: CGFloat = 0

    var body: some View {
        GeometryReader { geo in
            let w = max(geo.size.width, 1)
            let lean = offset / w
            ZStack(alignment: lean > 0 ? .topTrailing : .topLeading) {
                VStack(spacing: 10) { content }
                    .multilineTextAlignment(.center)
                    .padding(20)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Palette.background, in: RoundedRectangle(cornerRadius: 24))
                    .overlay(RoundedRectangle(cornerRadius: 24).stroke(
                        lean > 0.12 ? Palette.ok : lean < -0.12 ? Palette.bad : Palette.line,
                        lineWidth: abs(lean) > 0.12 ? 2 : 1))
                if abs(lean) > 0.12 {
                    Text(lean > 0 ? rightLabel : leftLabel)
                        .font(.footnote.weight(.heavy)).foregroundStyle(.white)
                        .padding(.horizontal, 10).padding(.vertical, 3)
                        .background(lean > 0 ? Palette.ok : Palette.bad, in: RoundedRectangle(cornerRadius: 8))
                        .padding(14)
                }
            }
            .contentShape(Rectangle())
            .offset(x: offset)
            .rotationEffect(.degrees(Double(offset / 40)))
            .opacity(1 - Double(min(max(abs(lean) - 0.6, 0), 0.4)) * 2.5)
            .onTapGesture(perform: onTap)
            .gesture(
                DragGesture(minimumDistance: 12)
                    .onChanged { offset = $0.translation.width }
                    .onEnded { _ in
                        if abs(offset) > w * 0.28 {
                            let right = offset > 0
                            withAnimation(.easeIn(duration: 0.17)) { offset = (right ? 1 : -1) * w * 1.3 }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                                onSwipe(right)
                                offset = 0
                            }
                        } else {
                            withAnimation(.spring) { offset = 0 }
                        }
                    }
            )
        }
        .frame(minHeight: 250)
    }
}

/// An answer in Choose and in the level test. state: nil before answering, then "right", "wrong" or "dim".
struct OptionButton: View {
    let number: Int
    let label: String
    let color: Color
    let state: String?
    let action: () -> Void

    var body: some View {
        let line = state == "right" ? Palette.ok : state == "wrong" ? Palette.bad : Palette.line
        Button(action: action) {
            HStack(spacing: 12) {
                Text("\(number)").font(.caption2.weight(.bold)).foregroundStyle(Palette.muted)
                    .frame(width: 22, height: 22)
                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(line, lineWidth: 1))
                Text(label).font(.body.weight(.semibold)).foregroundStyle(color).multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .frame(minHeight: 52)
            .background(state == "right" ? Palette.ok.opacity(0.12) : state == "wrong" ? Palette.bad.opacity(0.12) : Palette.background,
                        in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(line, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
        .disabled(state != nil)
        .opacity(state == "dim" ? 0.5 : 1)
    }
}

/// The thin bar and "3 / 10" above a quiz question.
struct QuizProgress: View {
    let n: Int
    let total: Int
    var body: some View {
        HStack(spacing: 12) {
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(Palette.surfaceHigh)
                    Capsule().fill(Palette.accent).frame(width: g.size.width * CGFloat(n) / CGFloat(max(total, 1)))
                }
            }
            .frame(height: 6)
            Text("\(n + 1) / \(total)").font(.footnote).foregroundStyle(Palette.muted).monospacedDigit()
        }
    }
}

/// ä ö ü ß buttons for people without a German keyboard.
struct UmlautRow: View {
    let insert: (String) -> Void
    var body: some View {
        HStack(spacing: 6) {
            ForEach(["ä", "ö", "ü", "ß"], id: \.self) { c in
                Button { insert(c) } label: {
                    Text(c).font(.body.weight(.bold)).foregroundStyle(Palette.text)
                        .frame(width: 40, height: 34)
                        .outlined(radius: 9)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// The big der / die / das buttons of the article quiz and the level test.
struct ArticleButtons: View {
    let answer: String
    let chosen: String?
    let pick: (String) -> Void
    var body: some View {
        HStack(spacing: 10) {
            ForEach(["m", "f", "n"], id: \.self) { g in
                let c = Gender.color(g)
                let isRight = chosen != nil && g == answer
                Button { pick(g) } label: {
                    Text(Gender.article(g)!)
                        .font(.title2.weight(.heavy))
                        .foregroundStyle(isRight ? .white : c)
                        .frame(maxWidth: .infinity, minHeight: 56)
                        .background(isRight ? c : c.opacity(0.12), in: RoundedRectangle(cornerRadius: 16))
                }
                .buttonStyle(.plain)
                .disabled(chosen != nil)
                .opacity(chosen != nil && !isRight ? 0.35 : 1)
            }
        }
    }
}
