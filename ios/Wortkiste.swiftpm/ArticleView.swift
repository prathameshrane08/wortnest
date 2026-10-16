import SwiftUI

struct ArticleView: View {
    @EnvironmentObject var store: Store
    @State private var lookup = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    lookupSection
                    QuizCard()
                    RulesSection()
                }
                .padding()
            }
            .navigationTitle("Der Die Das")
        }
    }

    @ViewBuilder private var lookupSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("Which article? Type a noun: Kinderzimmer", text: $lookup)
                .textFieldStyle(.roundedBorder)
                .germanInput()
            if !lookup.trimmingCharacters(in: .whitespaces).isEmpty {
                if let hit = store.nounLookup(lookup) {
                    let e = hit.entry
                    let shown = hit.compound ?? e.word
                    let art = Gender.article(e.gender) ?? ""
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(art).foregroundStyle(Gender.color(e.gender))
                            Text(shown)
                            Spacer()
                            SpeakButton(text: "\(art) \(shown)")
                        }
                        .font(.system(size: 34, weight: .heavy))
                        .minimumScaleFactor(0.5)
                        .lineLimit(1)
                        if hit.compound != nil {
                            Text("Compound: takes the article of **\(art) \(e.word)**").font(.subheadline)
                        } else if !e.plural.isEmpty {
                            Text("plural: **die \(e.plural)**").font(.subheadline)
                        }
                        if let g = e.glosses.first { Text(g).font(.subheadline).foregroundStyle(.secondary) }
                        if let rule = Gender.rule(for: shown, gender: e.gender) {
                            Text(rule).font(.footnote)
                        }
                    }
                    .card()
                } else {
                    Text("No noun found for “\(lookup)”. Nouns are listed in the singular: try Haus, not Häuser.")
                        .font(.subheadline).foregroundStyle(.secondary).card()
                }
            }
        }
    }
}

struct QuizCard: View {
    @EnvironmentObject var store: Store
    @AppStorage("ddBest") private var best = 0
    @State private var level = 0
    @State private var current: Entry?
    @State private var chosen: String?
    @State private var streak = 0
    @State private var right = 0
    @State private var total = 0

    private let levels = ["Top 500", "500–2,000", "2,000+", "Mistakes"]

    private var pool: [Entry] {
        if level == 3 {
            return store.missedNouns.compactMap { w in store.entries(for: w).first { $0.isNoun } }
        }
        let ranges = [0..<500, 500..<2000, 2000..<6000]
        let ids = store.quizNouns
        let r = ranges[level].clamped(to: 0..<ids.count)
        return ids[r].map { store.entries[$0] }
    }

    var body: some View {
        VStack(spacing: 18) {
            VStack(alignment: .leading, spacing: 8) {
                Text("ARTICLE QUIZ").font(.caption.weight(.bold)).kerning(1).foregroundStyle(.secondary)
                Text("der, die oder das?").font(.title2.weight(.heavy))
                Picker("Difficulty", selection: $level) {
                    ForEach(levels.indices, id: \.self) { Text(levels[$0]).tag($0) }
                }
                .pickerStyle(.segmented)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if let e = current {
                VStack(spacing: 6) {
                    Text(e.word)
                        .font(.system(size: 44, weight: .heavy))
                        .foregroundStyle(chosen == nil ? .primary : Gender.color(e.gender))
                        .minimumScaleFactor(0.4)
                        .lineLimit(1)
                    Text(e.glosses.first ?? "").font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
                HStack(spacing: 10) {
                    ForEach(["m", "f", "n"], id: \.self) { g in
                        Button { answer(g) } label: {
                            Text(Gender.article(g)!)
                                .font(.title2.weight(.heavy))
                                .frame(maxWidth: .infinity, minHeight: 56)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Gender.color(g))
                        .background(Gender.color(g).opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(chosen != nil && g == e.gender ? Color.green : .clear, lineWidth: 3))
                        .opacity(chosen != nil && g != e.gender ? 0.4 : 1)
                    }
                }
                feedback(for: e).frame(minHeight: 44)
                if chosen != nil {
                    Button("Next word", action: next).buttonStyle(.borderedProminent)
                }
            } else {
                ContentUnavailableView("All clear", systemImage: "checkmark.circle", description: Text("No mistakes left to review. Pick another level."))
            }

            HStack(spacing: 28) {
                stat("\(streak)", "Streak")
                stat("\(best)", "Best")
                stat(total == 0 ? "–" : "\(Int((Double(right) / Double(total) * 100).rounded()))%", "Accuracy")
                stat("\(store.missedNouns.count)", "To review")
            }
        }
        .card()
        .onAppear { if current == nil { next() } }
        .onChange(of: level) { next() }
    }

    @ViewBuilder private func feedback(for e: Entry) -> some View {
        if let chosen {
            let ok = chosen == e.gender
            let art = Gender.article(e.gender) ?? ""
            VStack(spacing: 4) {
                Text(ok ? "Richtig!" : "Falsch.").font(.headline).foregroundStyle(ok ? .green : .red)
                Text("**\(art) \(e.word)**" + (e.plural.isEmpty ? "" : ", die \(e.plural)")).font(.subheadline)
                if let rule = Gender.rule(for: e.word, gender: e.gender) { Text(rule).font(.footnote).foregroundStyle(.secondary) }
            }
            .multilineTextAlignment(.center)
        } else {
            Text("Tap the article you think is right.").font(.footnote).foregroundStyle(.secondary)
        }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.title3.weight(.bold)).monospacedDigit()
            Text(label.uppercased()).font(.caption2).foregroundStyle(.secondary)
        }
    }

    private func answer(_ g: String) {
        guard let e = current, chosen == nil else { return }
        chosen = g
        total += 1
        let ok = g == e.gender
        if ok {
            right += 1; streak += 1; best = max(best, streak)
        } else {
            streak = 0
        }
        store.recordQuiz(e.word, correct: ok)
        Speaker.shared.say("\(Gender.article(e.gender) ?? "") \(e.word)")
        if ok {
            Task {
                try? await Task.sleep(for: .milliseconds(1100))
                if current?.id == e.id && chosen != nil { next() }
            }
        }
    }

    private func next() {
        let p = pool
        chosen = nil
        guard !p.isEmpty else { current = nil; return }
        var pick = p.randomElement()
        if p.count > 1 { while pick?.id == current?.id { pick = p.randomElement() } }
        current = pick
    }
}

struct RulesSection: View {
    private let columns: [(String, [String])] = [
        ("m", ["-ling  der Frühling", "-ismus  der Tourismus", "-or  der Motor", "-ig, -ich  der Honig", "-ist, -ant  der Polizist", "days, months, seasons", "weather: der Regen"]),
        ("f", ["-ung  die Zeitung", "-heit, -keit  die Freiheit", "-schaft  die Freundschaft", "-ion, -tät  die Nation", "-ie, -ei, -ik  die Bäckerei", "-in (people)  die Lehrerin", "-ur, -enz, -anz  die Natur"]),
        ("n", ["-chen, -lein  das Mädchen", "-ment  das Instrument", "-um  das Museum", "-tum  das Eigentum", "Ge- collectives: das Gebirge", "infinitives: das Essen", "metals, colours: das Gold"])
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("RULES OF THUMB").font(.caption.weight(.bold)).kerning(1).foregroundStyle(.secondary)
            Text("Endings that give the article away").font(.title3.weight(.heavy))
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: 10) { ForEach(columns, id: \.0) { column($0.0, $0.1) } }
                VStack(spacing: 10) { ForEach(columns, id: \.0) { column($0.0, $0.1) } }
            }
            Text("Compound nouns take the article of their last part: das Kind + das Zimmer = **das** Kinderzimmer.")
                .font(.footnote).foregroundStyle(.secondary)
        }
    }

    private func column(_ g: String, _ items: [String]) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(Gender.article(g)!).font(.headline).foregroundStyle(Gender.color(g))
            ForEach(items, id: \.self) { Text($0).font(.footnote) }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Gender.color(g).opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
    }
}
