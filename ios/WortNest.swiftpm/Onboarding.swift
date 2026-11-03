import SwiftUI

private let testCount = 12
private let testPass = 10

/// One question of the move-up test: the meaning of a German word (de), the German for a meaning (en), or a noun's article (art).
private struct TestQ {
    let id: Int
    let kind: String
    let opts: [Int]
}

/// Your level: take the twelve-question test to move up, or set the level by hand.
/// onClose(true) means "go to Practice", after adding the new level's first words.
struct LevelView: View {
    @EnvironmentObject var store: Store
    let onClose: (Bool) -> Void
    @State private var view = "home"
    @State private var items: [TestQ] = []
    @State private var n = 0
    @State private var right = 0
    @State private var pick: String?     // the chosen article, or the chosen word's id as text
    @State private var target = 0

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    switch view {
                    case "home": home
                    case "result": result
                    default: question
                    }
                }
                .padding(20)
            }
            .background(Palette.background)
            .navigationTitle(view == "home" ? "Your level" : "\(Level.names[target]) test")
            .inlineTitle()
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { onClose(false) } } }
            .onAppear { if DebugLaunch.string("demoLevelTest") == "test" && view == "home" { start() } }
        }
    }

    private func start() {
        let l = store.level + 1
        let pool = store.levelWords[l].prefix(600).filter { !store.entries[$0].glosses.isEmpty }
        let nouns = store.pickUnique(pool.filter { let e = store.entries[$0]; return e.isNoun && ["m", "f", "n"].contains(e.gender) }, 4)
        let words = store.pickUnique(pool.filter { !nouns.contains($0) }, testCount - nouns.count)
        items = (nouns.map { TestQ(id: $0, kind: "art", opts: []) }
                 + words.enumerated().map { k, i in TestQ(id: i, kind: k % 2 == 0 ? "de" : "en", opts: store.options(i, pool: Array(pool), showEnglish: k % 2 == 0)) })
            .shuffled()
        target = l; n = 0; right = 0; pick = nil; view = "test"
    }

    @ViewBuilder private var home: some View {
        let l = store.level
        HStack(spacing: 14) {
            Text(Level.names[l]).font(.title2.weight(.heavy)).foregroundStyle(Level.color(l))
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(Level.color(l).opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 2) {
                Text("You're at \(Level.names[l])").font(.title2.weight(.heavy))
                Text("The word of the day, quizzes and flashcards use \(levelRange(l)) words.").font(.subheadline).foregroundStyle(Palette.muted)
            }
        }
        if l < 3 {
            VStack(alignment: .leading, spacing: 6) {
                Text("Ready for \(Level.names[l + 1])?").font(.headline)
                Text("\(testCount) quick questions on \(Level.names[l + 1]) words. Get \(testPass) right to move up.").font(.subheadline)
                Button("Take the \(Level.names[l + 1]) test", action: start).buttonStyle(PrimaryButton()).padding(.top, 4)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.accentSoft, in: RoundedRectangle(cornerRadius: 16))
        } else {
            Text("B1 is the highest level here. Keep practicing with the quizzes and the grammar topics.").foregroundStyle(Palette.muted)
        }
        Text("Or choose your level yourself").font(.subheadline.weight(.semibold)).foregroundStyle(Palette.muted)
        ForEach(1...3, id: \.self) { k in
            LevelChoice(level: k, line: k == 1 ? "A1 words" : "A1 to \(Level.names[k]) words", on: k == l) { store.chooseLevel(k) }
        }
    }

    @ViewBuilder private var question: some View {
        let q = items[n]
        let e = store.entries[q.id]
        let answer = q.kind == "art" ? e.gender : "\(e.id)"
        let done = pick != nil
        QuizProgress(n: n, total: testCount)
        VStack(spacing: 6) {
            switch q.kind {
            case "art":
                Text("der, die or das?").font(.footnote).foregroundStyle(Palette.muted)
                Text(e.word).font(.system(size: 34, weight: .heavy)).foregroundStyle(done ? Gender.color(e.gender) : Palette.text)
                    .lineLimit(1).minimumScaleFactor(0.4)
                Text(e.shortGloss).font(.subheadline).foregroundStyle(Palette.muted)
            case "de":
                Text("Pick the meaning").font(.footnote).foregroundStyle(Palette.muted)
                Text(e.spoken).font(.system(size: 32, weight: .heavy)).foregroundStyle(e.color).lineLimit(1).minimumScaleFactor(0.4)
            default:
                Text("Pick the German · \(e.posName)").font(.footnote).foregroundStyle(Palette.muted)
                Text(e.shortGloss).font(.title2.weight(.bold))
            }
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity).padding(.vertical, 22).padding(.horizontal, 16)
        .outlined(radius: 20, fill: Palette.surface)
        if q.kind == "art" {
            ArticleButtons(answer: e.gender, chosen: pick) { choose($0, answer) }
        } else {
            VStack(spacing: 8) {
                ForEach(Array(q.opts.enumerated()), id: \.element) { k, j in
                    let o = store.entries[j]
                    OptionButton(number: k + 1, label: q.kind == "de" ? o.shortGloss : o.spoken, color: q.kind == "de" ? Palette.text : o.color,
                                 state: !done ? nil : j == e.id ? "right" : "\(j)" == pick ? "wrong" : "dim") { choose("\(j)", answer) }
                }
            }
        }
        if done {
            Button(n + 1 < testCount ? "Next" : "See result") {
                n += 1; pick = nil
                if n >= testCount {
                    view = "result"
                    if right >= testPass { store.chooseLevel(target) }
                }
            }
            .buttonStyle(PrimaryButton())
            .frame(maxWidth: .infinity)
        }
    }

    private func choose(_ p: String, _ answer: String) {
        guard pick == nil else { return }
        pick = p
        if p == answer { right += 1 }
    }

    private var result: some View {
        let pass = right >= testPass
        return VStack(spacing: 10) {
            HStack(alignment: .lastTextBaseline, spacing: 2) {
                Text("\(right)").font(.system(size: 52, weight: .heavy))
                Text(" / \(testCount)").font(.title3).foregroundStyle(Palette.muted)
            }
            if pass {
                Text("Welcome to \(Level.names[target])! 🎉").font(.title2.weight(.heavy))
                Text("The word of the day, quizzes and flashcards now include \(Level.names[target]) words.").foregroundStyle(Palette.muted)
                Button("Add 20 \(Level.names[target]) words to flashcards") { store.addLevel(target); onClose(true) }.buttonStyle(PrimaryButton())
                Button("Done") { onClose(false) }.buttonStyle(SecondaryButton())
            } else {
                Text("Not quite yet").font(.title2.weight(.heavy))
                Text("You need \(testPass) right to move up. Keep practicing and try again whenever you like.").foregroundStyle(Palette.muted)
                Button("Try again", action: start).buttonStyle(PrimaryButton())
                Button("Close") { onClose(false) }.buttonStyle(SecondaryButton())
            }
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
    }
}

/// A level you can pick: its badge, its name and one line about it.
struct LevelChoice: View {
    let level: Int
    let line: String
    let on: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                LevelBadge(level: level)
                VStack(alignment: .leading, spacing: 1) {
                    Text(Level.titles[level]).font(.body.weight(.bold)).foregroundStyle(Palette.text)
                    Text(line).font(.footnote).foregroundStyle(Palette.muted)
                }
                Spacer()
            }
            .padding(12)
            .background(on ? Palette.accentSoft : Palette.background, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(on ? Palette.accent : Palette.line, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Walkthrough

private let levelStep = 1

/// A short walkthrough of the five sections, shown on first launch and from the Tour button.
/// The second step asks the learner's level, which sets the words everywhere and can add the first 20.
struct TourView: View {
    @EnvironmentObject var store: Store
    let onClose: (Bool) -> Void
    @State private var step = 0
    @State private var level = 1
    @State private var addWords = true
    @State private var sawLevel = false

    private let steps = [
        ("Willkommen! A little German every day", "Five tools in one place. This walkthrough shows each one in under a minute."),
        ("Where are you starting?", "Your level picks the word of the day and the words in quizzes and flashcards. Lower levels are included, so A2 also has the A1 words. A short test moves you up later."),
        ("Look up any word, both ways", "Type German or English. Forms like ging or Häuser find their base word, with the meaning, examples and pronunciation."),
        ("Blue, red, green", "Every noun shows its article in colour: blue for der, red for die, green for das. The Articles tab quizzes you until the colours stick."),
        ("Every verb, every tense", "The Verbs tab conjugates about 8,900 verbs. Highlighted forms break the regular pattern, so you know which ones to learn by heart."),
        ("Practice four ways", "Practice has flashcards for your starred words, plus Choose, Write and Match quizzes, German to English or the other way. Swipe a flashcard right if you knew it."),
    ]

    var body: some View {
        let last = step == steps.count - 1
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    HStack(spacing: 6) {
                        ForEach(steps.indices, id: \.self) { i in
                            Capsule().fill(i == step ? Palette.accent : Palette.line).frame(width: i == step ? 22 : 7, height: 7)
                        }
                    }
                    Spacer()
                    if !last { Button("Skip", action: finish).foregroundStyle(Palette.muted) }
                }
                VStack(alignment: .leading, spacing: 10) { demo }
                    .frame(maxWidth: .infinity, minHeight: 190)
                    .padding(16)
                    .outlined(radius: 20, fill: Palette.surface)
                Text(steps[step].0).font(.title.weight(.heavy)).kerning(-0.5)
                Text(steps[step].1).foregroundStyle(Palette.muted)
                HStack {
                    if step > 0 { Button("Back") { step -= 1 }.buttonStyle(SecondaryButton()) }
                    Spacer()
                    Button(last ? "Start learning" : "Next") {
                        if last { finish() } else { step += 1 }
                        if step == levelStep { sawLevel = true }
                    }
                    .buttonStyle(PrimaryButton())
                }
            }
            .padding(20)
        }
        .background(Palette.background)
        .onAppear {
            level = store.level; addWords = store.saved.isEmpty
            if let s = DebugLaunch.string("demoTourStep").flatMap(Int.init) { step = s }
        }
    }

    /// Skip and Start learning both keep the level once it has been shown.
    private func finish() {
        let add = sawLevel && addWords && store.saved.isEmpty
        if sawLevel { store.chooseLevel(level) }
        if add { store.addLevel(level) }
        store.finishTour()
        onClose(add)
    }

    @ViewBuilder private var demo: some View {
        switch step {
        case 0:
            HStack {
                ForEach([("book.closed", "Dictionary"), ("circle.grid.3x3", "Articles"), ("tablecells", "Verbs"),
                         ("text.book.closed", "Grammar"), ("star", "Practice")], id: \.1) { icon, label in
                    VStack(spacing: 6) {
                        Image(systemName: icon).foregroundStyle(Palette.accent)
                            .frame(width: 44, height: 44).outlined(radius: 14)
                        Text(label).font(.caption2.weight(.semibold)).foregroundStyle(Palette.muted).lineLimit(1).minimumScaleFactor(0.6)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        case levelStep:
            ForEach([(1, "I'm just starting out"), (2, "I know the basics"), (3, "I can hold a conversation")], id: \.0) { l, line in
                LevelChoice(level: l, line: line, on: level == l) { level = l }
            }
            if store.saved.isEmpty {
                Toggle("Add my first 20 words to the flashcards", isOn: $addWords).font(.subheadline)
            }
        case 2:
            SearchDemo()
        case 3:
            ForEach([("m", "Tisch"), ("f", "Lampe"), ("n", "Buch")], id: \.0) { g, w in
                HStack(spacing: 10) {
                    ArticleChip(gender: g, large: true)
                    Text(w).font(.title2.weight(.heavy)).foregroundStyle(Gender.color(g))
                }
            }
        case 4:
            VStack(alignment: .leading, spacing: 4) {
                (Text("fahren").bold() + Text("  Präsens").font(.footnote).foregroundColor(Palette.muted))
                ForEach([("ich", "fahre", false), ("du", "fährst", true), ("er/sie/es", "fährt", true), ("wir", "fahren", false)], id: \.0) { p, f, odd in
                    HStack {
                        Text(p).foregroundStyle(Palette.muted).frame(width: 90, alignment: .leading)
                        Text(f).fontWeight(odd ? .bold : .regular).foregroundStyle(odd ? Palette.irregular : Palette.text)
                    }
                }
            }
            .padding(14).frame(maxWidth: .infinity, alignment: .leading).outlined()
        default:
            VStack(spacing: 2) {
                Text("das Haus").font(.title.weight(.heavy)).foregroundStyle(Gender.color("n"))
                Text("house, home").font(.subheadline).foregroundStyle(Palette.muted)
            }
            .padding(16).frame(maxWidth: .infinity).outlined(radius: 18)
            HStack(spacing: 6) {
                ForEach(Array(Srs.gradeNames.enumerated()), id: \.offset) { i, name in
                    Text(name).font(.footnote.weight(.semibold)).foregroundStyle(i == 2 ? .white : Palette.text)
                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                        .background(i == 2 ? Palette.accent : Palette.background, in: RoundedRectangle(cornerRadius: 11))
                        .overlay(RoundedRectangle(cornerRadius: 11).stroke(i == 2 ? Palette.accent : Palette.line, lineWidth: 1))
                }
            }
        }
    }
}

/// Types ging, to run and Häuser into a pretend search box and shows the word each one finds.
private struct SearchDemo: View {
    @EnvironmentObject var store: Store
    @State private var d = 0
    @State private var typed = ""
    private let demos = [("ging", "gehen"), ("to run", "laufen"), ("Häuser", "Haus")]

    var body: some View {
        let (q, w) = demos[d]
        let e = typed == q ? store.entries(for: w).first { $0.word == w } : nil
        VStack(alignment: .leading, spacing: 10) {
            Text(typed + "|").font(.title3)
                .padding(.horizontal, 14).padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Palette.background, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.accent, lineWidth: 1.5))
            ZStack(alignment: .topLeading) {
                Color.clear.frame(height: 64)
                if let e {
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            if e.isNoun { ArticleChip(gender: e.gender) }
                            Text(e.word).bold().foregroundStyle(e.color)
                            LevelBadge(level: e.level)
                        }
                        Text(e.glosses.prefix(2).joined(separator: "; ")).font(.subheadline).foregroundStyle(Palette.muted).lineLimit(2)
                    }
                    .padding(12).frame(maxWidth: .infinity, alignment: .leading).outlined(radius: 14)
                }
            }
        }
        .task {
            while !Task.isCancelled {
                let q = demos[d].0
                for k in 0...q.count {
                    typed = String(q.prefix(k))
                    try? await Task.sleep(for: .milliseconds(140))
                }
                try? await Task.sleep(for: .milliseconds(1700))
                d = (d + 1) % demos.count
            }
        }
    }
}
