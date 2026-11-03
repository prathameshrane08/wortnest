import SwiftUI

/* Practice has four ways to practise, like Quizlet: flashcards (spaced repetition on your saved words),
   choose from four, write the answer, and a timed matching game. The last three use your level's
   words or your saved words, German to English or the other way round. */

private let modes = [("cards", "Flashcards", "rectangle.on.rectangle"), ("mc", "Choose", "list.bullet.rectangle"),
                     ("type", "Write", "pencil"), ("match", "Match", "square.grid.2x2")]
private let modeText = [
    "cards": "Star words in the dictionary to add them here. Words you know come back less often; words you miss come back soon.",
    "mc": "Pick the right answer out of four. Ten questions a round.",
    "type": "Write the answer yourself. Ten questions a round.",
    "match": "Tap a German word, then its meaning. Clear the board as fast as you can.",
]

struct LearnView: View {
    @EnvironmentObject var store: Store
    let openLevel: () -> Void

    var body: some View {
        let mode = store.practiceMode
        let canSaved = store.saved.count >= 4
        let useSaved = canSaved && store.practiceSrc == "saved"
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(modeText[mode] ?? "").foregroundStyle(Palette.muted)
                    HStack(spacing: 8) {
                        ForEach(modes, id: \.0) { key, label, icon in modeTile(key, label, icon, on: mode == key) }
                    }
                    if mode != "match" {
                        Button { store.setPractice(dir: store.practiceDir == "de" ? "en" : "de") } label: {
                            Label(store.practiceDir == "de" ? "German → English" : "English → German", systemImage: "arrow.left.arrow.right")
                                .font(.body.weight(.semibold))
                        }
                    }
                    if mode != "cards" {
                        Segments(labels: ["\(levelRange(store.level)) words", "My saved words"], selected: useSaved ? 1 : 0) {
                            if $0 == 0 || canSaved { store.setPractice(src: $0 == 1 ? "saved" : "level") }
                        }
                        if !canSaved {
                            Text("Save at least 4 words to practise only those.").font(.footnote).foregroundStyle(Palette.muted)
                        }
                    }
                    switch mode {
                    case "cards": Flashcards()
                    case "match": MatchGame(useSaved: useSaved).id("\(useSaved)\(store.level)")
                    default: QuizRound(kind: mode, de: store.practiceDir == "de", useSaved: useSaved).id("\(mode)\(store.practiceDir)\(useSaved)\(store.level)")
                    }
                    if mode == "cards" { savedList }
                }
                .padding(16)
            }
            .background(Palette.background)
            .navigationTitle("Practice")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: openLevel) {
                        HStack(spacing: 6) {
                            LevelBadge(level: store.level)
                            Text(store.level < 3 ? "Take the \(Level.names[store.level + 1]) test" : "Your level")
                                .font(.footnote.weight(.semibold))
                        }
                    }
                }
            }
            .onAppear { if DebugLaunch.string("demoSeed") != nil && store.saved.isEmpty { store.addLevel(1) } }
            .appDestinations()
        }
    }

    private func modeTile(_ key: String, _ label: String, _ icon: String, on: Bool) -> some View {
        Button { store.setPractice(mode: key) } label: {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.title3)
                Text(label).font(.footnote.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.6)
            }
            .foregroundStyle(on ? Palette.accent : Palette.muted)
            .frame(maxWidth: .infinity).padding(.vertical, 12).padding(.horizontal, 2)
            .background(on ? Palette.accentSoft : Palette.background, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(on ? Palette.accent : Palette.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var savedList: some View {
        let list = store.savedEntries()
        VStack(alignment: .leading, spacing: 8) {
            Text("Saved words" + (list.isEmpty ? "" : " (\(list.count))")).font(.headline)
            if list.isEmpty {
                Text("Nothing saved yet.").font(.subheadline).foregroundStyle(Palette.muted)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(list.enumerated()), id: \.element.id) { k, e in
                        if k > 0 { Divider() }
                        EntryRow(entry: e).padding(.horizontal, 14).padding(.vertical, 10)
                    }
                }
                .outlined()
            }
        }
    }
}

// MARK: - Flashcards

private struct Flashcards: View {
    @EnvironmentObject var store: Store
    @State private var currentKey: String?
    @State private var flipped = DebugLaunch.string("demoFlip") != nil

    var body: some View {
        if store.saved.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("No saved words yet").font(.headline)
                Text("Tap the star on any dictionary entry, or start with the 20 most useful words of a level.").foregroundStyle(Palette.muted)
                AddLevelRow()
            }
            .card()
        } else {
            let queue = store.studyQueue()
            let key = currentKey.flatMap { queue.contains($0) ? $0 : nil } ?? queue.first
            if let key, let e = entry(key), let card = store.saved[key] {
                VStack(spacing: 14) {
                    SwipeCard(leftLabel: "Again", rightLabel: "Good", onTap: { flip(e) }, onSwipe: { grade(key, $0 ? 2 : 0) }) {
                        face(e, card: card)
                    }
                    .id(key)
                    if flipped {
                        HStack(spacing: 8) {
                            ForEach(0..<4, id: \.self) { g in
                                Button { grade(key, g) } label: {
                                    Text(Srs.gradeNames[g]).lineLimit(1).minimumScaleFactor(0.7).frame(maxWidth: .infinity)
                                }
                                // Good is the button you'll press most, so it stands out.
                                .modifier(GradeStyle(grade: g))
                            }
                        }
                    } else {
                        Button { flip(e) } label: { Text("Show answer").frame(maxWidth: .infinity) }.buttonStyle(PrimaryButton())
                    }
                    Text("Swipe right for Good, left for Again.").font(.footnote).foregroundStyle(Palette.muted)
                    Stats()
                }
                .onChange(of: key) { flipped = false }
            } else {
                VStack(alignment: .leading, spacing: 10) {
                    Text("All done for now").font(.headline)
                    Text(store.hasNewCards
                         ? "You've had today's \(Srs.newPerDay) new words. More arrive tomorrow."
                         : "No cards are due. Try Choose, Write or Match meanwhile, or star more words.")
                        .foregroundStyle(Palette.muted)
                }
                .card()
                Stats()
            }
        }
    }

    @ViewBuilder private func face(_ e: Entry, card: Card) -> some View {
        HStack(spacing: 6) {
            Text(e.posName).italic().font(.footnote).foregroundStyle(Palette.muted)
            if !flipped && (card.isNew || card.isLearning) {
                Text(card.isNew ? "new" : "learning")
                    .font(.caption.weight(.semibold)).foregroundStyle(Palette.accentText)
                    .padding(.horizontal, 6).padding(.vertical, 1)
                    .background(Palette.accentSoft, in: RoundedRectangle(cornerRadius: 6))
            }
        }
        if flipped {
            if let a = e.article { Text(a).font(.title3.weight(.bold)).foregroundStyle(e.color) }
            Text(e.word).font(.system(size: 36, weight: .heavy)).kerning(-1).foregroundStyle(e.color).lineLimit(1).minimumScaleFactor(0.4)
            if e.isNoun, !e.plural.isEmpty { Text("die \(e.plural)").foregroundStyle(Palette.muted) }
            Text(e.glosses.prefix(2).joined(separator: "; "))
            if let ex = store.examples[e.key]?.first, ex.count == 2 {
                Divider()
                Text(ex[0]).italic()
                Text(ex[1]).font(.footnote).foregroundStyle(Palette.muted)
            }
        } else if store.practiceDir == "de" {
            Text(e.word).font(.system(size: 36, weight: .heavy)).kerning(-1).foregroundStyle(Palette.text).lineLimit(1).minimumScaleFactor(0.4)
            Text("Tap to reveal").font(.footnote).foregroundStyle(Palette.muted)
        } else {
            Text(e.glosses.prefix(2).joined(separator: "; ")).font(.title2.weight(.bold)).foregroundStyle(Palette.text)
            Text("Say it in German, then tap").font(.footnote).foregroundStyle(Palette.muted)
        }
    }

    private func entry(_ key: String) -> Entry? {
        let parts = key.split(separator: "|", maxSplits: 1).map(String.init)
        return parts.count == 2 ? store.entry(word: parts[0], pos: parts[1]) : nil
    }

    private func flip(_ e: Entry) {
        flipped.toggle()
        if flipped { Speaker.shared.say(e.spoken) }
    }

    private func grade(_ key: String, _ g: Int) {
        store.grade(key, g)
        currentKey = store.studyQueue().first
        flipped = false
    }
}

private struct GradeStyle: ViewModifier {
    let grade: Int
    func body(content: Content) -> some View {
        if grade == 2 { content.buttonStyle(PrimaryButton()) }
        else { content.buttonStyle(SecondaryButton(color: grade == 0 ? Palette.bad : Palette.text)) }
    }
}

private struct Stats: View {
    @EnvironmentObject var store: Store
    var body: some View {
        let c = store.counts()
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                stat(c.fresh, "New", Gender.color("m"))
                stat(c.learning, "Learning", Gender.color("f"))
                stat(c.review, "To review", Gender.color("n"))
            }
            Text("\(c.known) of \(store.saved.count) words known well · \(c.tomorrow) due by tomorrow")
                .font(.caption).foregroundStyle(Palette.muted)
            AddLevelRow().padding(.top, 6)
        }
        .frame(maxWidth: .infinity)
    }

    private func stat(_ n: Int, _ label: String, _ color: Color) -> some View {
        VStack(spacing: 3) {
            Text("\(n)").font(.headline).monospacedDigit().foregroundStyle(color)
                .frame(width: 84, height: 40)
                .background(Palette.surface, in: RoundedRectangle(cornerRadius: 12))
            Text(label).font(.caption2).foregroundStyle(Palette.muted)
        }
    }
}

/// Adds the next 20 words of a level, for levels up to your own.
private struct AddLevelRow: View {
    @EnvironmentObject var store: Store
    var body: some View {
        VStack(spacing: 6) {
            Text("Add 20 words from").font(.caption).foregroundStyle(Palette.muted)
            HStack(spacing: 8) {
                ForEach(1...store.level, id: \.self) { l in
                    let left = store.wordsLeft(l)
                    Button { store.addLevel(l) } label: {
                        VStack(spacing: 0) {
                            Text(Level.names[l]).font(.subheadline.weight(.bold))
                            Text("\(left) left").font(.caption2).foregroundStyle(Palette.muted)
                        }
                        .frame(minWidth: 64)
                    }
                    .buttonStyle(.bordered)
                    .disabled(left == 0)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Choose and Write

private struct QuizRound: View {
    @EnvironmentObject var store: Store
    let kind: String
    let de: Bool
    let useSaved: Bool
    @State private var pool: [Int] = []
    @State private var items: [Int] = []
    @State private var n = 0
    @State private var right = 0
    @State private var missed: [Int] = []
    @State private var pick: Int?        // nil until answered; -1 for a wrong written answer
    @State private var note = ""
    @State private var opts: [Int] = []
    @State private var typed = ""
    @State private var started = false
    @FocusState private var focused: Bool

    var body: some View {
        Group {
            if !started {
                Color.clear.frame(height: 1).onAppear(perform: newRound)
            } else if items.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("No words to practise").font(.headline)
                    Text("Star a few words in the dictionary first.").foregroundStyle(Palette.muted)
                }
                .card()
            } else if n >= items.count {
                result
            } else {
                question(store.entries[items[n]])
            }
        }
    }

    private func newRound() {
        pool = store.quizPool(saved: useSaved)
        items = store.pickUnique(pool, 10)
        n = 0; right = 0; missed = []; pick = nil; note = ""; typed = ""
        started = true
        prepare()
        // Test-only: answer the first question so the feedback can be checked without tapping.
        if let demo = DebugLaunch.string("demoAnswer"), let first = items.first {
            let e = store.entries[first]
            if kind == "mc" { answer(e, demo == "right" ? e.id : opts.first { $0 != e.id } ?? -1) }
            else { typed = demo == "right" ? (de ? e.shortGloss : e.spoken) : "xyz"; submit(e) }
        }
    }

    private func prepare() {
        opts = kind == "mc" && n < items.count ? store.options(items[n], pool: pool, showEnglish: de) : []
    }

    @ViewBuilder private func question(_ e: Entry) -> some View {
        let answered = pick != nil
        VStack(spacing: 14) {
            QuizProgress(n: n, total: items.count)
            VStack(spacing: 8) {
                Text(de ? (kind == "mc" ? "Pick the meaning" : "Write the meaning in English")
                     : (kind == "mc" ? "Pick the German" : "Write it in German") + " · \(e.posName)")
                    .font(.footnote).foregroundStyle(Palette.muted)
                if de {
                    if let a = e.article { Text(a).font(.headline).foregroundStyle(e.color) }
                    Text(e.word).font(.system(size: 34, weight: .heavy)).foregroundStyle(e.color).lineLimit(1).minimumScaleFactor(0.4)
                    Button { Speaker.shared.say(e.spoken) } label: {
                        Image(systemName: "speaker.wave.2.fill").frame(width: 40, height: 40).background(Palette.accentSoft, in: Circle())
                    }
                    .accessibilityLabel("Pronounce")
                } else {
                    Text(e.shortGloss).font(.title2.weight(.bold)).multilineTextAlignment(.center)
                }
            }
            .frame(maxWidth: .infinity).padding(.vertical, 20).padding(.horizontal, 12)
            .outlined(radius: 20, fill: Palette.surface)

            if kind == "mc" {
                VStack(spacing: 8) {
                    ForEach(Array(opts.enumerated()), id: \.element) { k, j in
                        let o = store.entries[j]
                        OptionButton(number: k + 1, label: de ? o.shortGloss : o.spoken, color: de ? Palette.text : o.color,
                                     state: !answered ? nil : j == e.id ? "right" : j == pick ? "wrong" : "dim") { answer(e, j) }
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    TextField(de ? "Meaning in English" : "German word", text: $typed)
                        .germanInput().focused($focused).submitLabel(.done)
                        .onSubmit { submit(e) }
                        .padding(14).outlined(radius: 12)
                        .disabled(answered)
                    if !de && !answered { UmlautRow { typed += $0 } }
                    HStack(spacing: 8) {
                        Button(answered ? "Next" : "Check") { submit(e) }.buttonStyle(PrimaryButton())
                        if !answered { Button("I don't know") { answer(e, -1, "The answer:") }.buttonStyle(SecondaryButton()) }
                    }
                }
            }
            if answered {
                let ok = pick == e.id
                VStack(spacing: 4) {
                    Text(ok ? "Correct!" : note.isEmpty ? "Not quite." : note).font(.headline).foregroundStyle(ok ? Palette.ok : Palette.bad)
                    Text(e.spoken).font(.headline).foregroundStyle(e.color)
                    Text(plainGlosses(e)).foregroundStyle(Palette.muted)
                    if ok && !note.isEmpty { Text(note).font(.footnote).foregroundStyle(Palette.muted) }
                    if kind == "mc" { Button("Next", action: next).buttonStyle(PrimaryButton()).padding(.top, 6) }
                }
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            }
        }
    }

    private func answer(_ e: Entry, _ p: Int, _ n2: String = "") {
        guard pick == nil else { return }
        pick = p; note = n2
        if p == e.id { right += 1 } else { missed.append(e.id) }
        Speaker.shared.say(e.spoken)
        // A right choice moves on by itself.
        if kind == "mc" && p == e.id {
            let at = n
            DispatchQueue.main.asyncAfter(deadline: .now() + 1) { if n == at && pick != nil { next() } }
        }
    }

    private func submit(_ e: Entry) {
        if pick != nil { next(); return }
        guard !typed.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        let c = de ? checkEnglish(typed, e, store) : checkGerman(typed, e)
        answer(e, c.ok ? e.id : -1, c.note)
    }

    private func next() {
        n += 1; pick = nil; note = ""; typed = ""
        prepare()
        if kind == "type" { focused = true }
    }

    private var result: some View {
        let total = items.count
        let unsaved = missed.map { store.entries[$0] }.filter { !store.isSaved($0) }
        return VStack(spacing: 10) {
            HStack(alignment: .lastTextBaseline, spacing: 2) {
                Text("\(right)").font(.system(size: 52, weight: .heavy))
                Text(" / \(total)").font(.title3).foregroundStyle(Palette.muted)
            }
            Text(right == total ? "Perfect round."
                 : Double(right) >= Double(total) * 0.8 ? "Very good."
                 : right * 2 >= total ? "Good start. The missed words are below."
                 : "These need more practice. Add them to your flashcards.")
                .foregroundStyle(Palette.muted).multilineTextAlignment(.center)
            ForEach(missed, id: \.self) { i in
                let e = store.entries[i]
                HStack(alignment: .top) {
                    Text(e.spoken).bold().foregroundStyle(e.color)
                    Spacer()
                    Text(e.shortGloss).foregroundStyle(Palette.muted).multilineTextAlignment(.trailing)
                }
            }
            Button("New round", action: newRound).buttonStyle(PrimaryButton())
            if !unsaved.isEmpty {
                Button(unsaved.count == 1 ? "Add it to flashcards" : "Add these \(unsaved.count) to flashcards") {
                    unsaved.forEach { store.toggleSaved($0) }
                }
                .buttonStyle(SecondaryButton())
            }
        }
        .frame(maxWidth: .infinity)
        .card()
    }
}

// MARK: - Match

private struct MatchGame: View {
    @EnvironmentObject var store: Store
    let useSaved: Bool
    @State private var tiles: [(id: Int, de: Bool)] = []
    @State private var gone = Set<Int>()
    @State private var sel: Int?
    @State private var wrong = Set<Int>()
    @State private var start: Date?
    @State private var penalty = 0
    @State private var time = 0.0
    @State private var best = false

    private var done: Bool { !tiles.isEmpty && gone.count == tiles.count }

    var body: some View {
        Group {
            if tiles.isEmpty {
                Color.clear.frame(height: 1).onAppear(perform: newBoard)
            } else if tiles.count < 8 {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Not enough words").font(.headline)
                    Text("Star a few more words first.").foregroundStyle(Palette.muted)
                }
                .card()
            } else if done {
                VStack(spacing: 10) {
                    HStack(alignment: .lastTextBaseline, spacing: 2) {
                        Text(String(format: "%.1f", time)).font(.system(size: 52, weight: .heavy))
                        Text(" s").font(.title3).foregroundStyle(Palette.muted)
                    }
                    Text((best ? "New best time!" : String(format: "Your best is %.1f s.", store.matchBest))
                         + (penalty > 0 ? " Includes \(penalty) s for wrong pairs." : ""))
                        .foregroundStyle(Palette.muted).multilineTextAlignment(.center)
                    Button("Play again", action: newBoard).buttonStyle(PrimaryButton())
                }
                .frame(maxWidth: .infinity)
                .card()
            } else {
                board
            }
        }
    }

    private var board: some View {
        VStack(spacing: 8) {
            HStack(alignment: .lastTextBaseline) {
                TimelineView(.periodic(from: .now, by: 0.1)) { ctx in
                    let t = start.map { ctx.date.timeIntervalSince($0) + Double(penalty) } ?? 0
                    Text(String(format: "%.1f s", t)).font(.title2.weight(.heavy)).monospacedDigit()
                }
                Spacer()
                Text((store.matchBest > 0 ? String(format: "Best %.1f s · ", store.matchBest) : "") + "wrong pair +1 s")
                    .font(.caption).foregroundStyle(Palette.muted)
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
                ForEach(tiles.indices, id: \.self) { k in tile(k) }
            }
        }
    }

    private func tile(_ k: Int) -> some View {
        let t = tiles[k]
        let e = store.entries[t.id]
        let isGone = gone.contains(k)
        let line = wrong.contains(k) ? Palette.bad : sel == k ? Palette.accent : Palette.line
        return Button { tap(k) } label: {
            Text(t.de ? e.spoken : e.tileGloss)
                .font(t.de ? .subheadline.weight(.bold) : .footnote.weight(.medium))
                .foregroundStyle(t.de ? e.color : Palette.text)
                .multilineTextAlignment(.center).lineLimit(4).minimumScaleFactor(0.7)
                .padding(6)
                .frame(maxWidth: .infinity, minHeight: 86, maxHeight: 86)
                .background(sel == k ? Palette.accentSoft : wrong.contains(k) ? Palette.bad.opacity(0.12) : Palette.background,
                            in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(line, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
        .disabled(isGone)
        .opacity(isGone ? 0 : 1)
    }

    private func newBoard() {
        let picks = store.pickUnique(store.quizPool(saved: useSaved), 6)
        tiles = picks.flatMap { [(id: $0, de: true), (id: $0, de: false)] }.shuffled()
        gone = []; sel = nil; wrong = []; start = nil; penalty = 0; time = 0; best = false
    }

    private func tap(_ k: Int) {
        if start == nil { start = Date() }
        guard let s = sel, s != k else { sel = sel == k ? nil : k; return }
        sel = nil
        if tiles[s].id == tiles[k].id && tiles[s].de != tiles[k].de {
            gone.formUnion([s, k])
            if done {
                time = Date().timeIntervalSince(start ?? Date()) + Double(penalty)
                best = store.recordMatch(time)
            }
        } else {
            penalty += 1
            wrong = [s, k]
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { wrong = [] }
        }
    }
}
