import SwiftUI

struct ArticleView: View {
    @EnvironmentObject var store: Store
    @State private var lookup = DebugLaunch.string("demoArticle") ?? ""

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
            .background(Palette.background)
            .navigationTitle("Articles")
        }
    }

    @ViewBuilder private var lookupSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SearchBox(text: $lookup, prompt: "Type a noun, e.g. Kinderzimmer")
            UmlautRow { lookup += $0 }
            if !lookup.trimmingCharacters(in: .whitespaces).isEmpty {
                if let hit = store.nounLookup(lookup) {
                    let e = hit.entry
                    let shown = hit.compound ?? e.word
                    let art = Gender.article(e.gender) ?? ""
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .center) {
                            (Text(art).foregroundStyle(Gender.color(e.gender)) + Text(" " + shown))
                                .font(.system(size: 34, weight: .heavy))
                                .minimumScaleFactor(0.5)
                                .lineLimit(1)
                            Spacer()
                            SpeakButton(text: "\(art) \(shown)")
                        }
                        if hit.compound != nil {
                            Text("Compound: takes the article of **\(art) \(e.word)**").font(.subheadline)
                        } else if !e.plural.isEmpty {
                            Text("plural: **die \(e.plural)**").font(.subheadline)
                        }
                        if let g = e.glosses.first { Text(g).font(.subheadline).foregroundStyle(Palette.muted) }
                        if let rule = Gender.rule(for: shown, gender: e.gender) {
                            Text(rule).font(.footnote)
                        }
                    }
                    .card()
                } else {
                    Text("No noun found for “\(lookup)”. Nouns are listed in the singular: try Haus, not Häuser.")
                        .font(.subheadline).foregroundStyle(Palette.muted).card()
                }
            }
        }
    }
}

struct QuizCard: View {
    @EnvironmentObject var store: Store
    @State private var cards = false        // flashcards instead of the three buttons
    @State private var missedOnly = false
    @State private var flipped = false
    @State private var known = 0
    @State private var current: Entry?
    @State private var chosen: String?
    @State private var streak = 0
    @State private var right = 0
    @State private var total = 0

    private var pool: [Entry] {
        missedOnly
            ? store.missedNouns.compactMap { w in store.entries(for: w).first { $0.isNoun } }
            : upTo(store.levelNouns, store.level).map { store.entries[$0] }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("der, die or das?").font(.title2.weight(.heavy))
            Text("Every German noun is der, die or das. Pick the right one for each word.")
                .font(.subheadline).foregroundStyle(Palette.muted)
            Segments(labels: ["Quiz", "Flashcards"], selected: cards ? 1 : 0) { cards = $0 == 1; next() }
            Segments(labels: ["\(levelRange(store.level)) nouns", "My mistakes"], selected: missedOnly ? 1 : 0) { missedOnly = $0 == 1; next() }

            if cards {
                nounCards
            } else if let e = current {
                VStack(spacing: 12) {
                    Text(e.word)
                        .font(.system(size: 40, weight: .heavy))
                        .foregroundStyle(chosen == nil ? Palette.text : Gender.color(e.gender))
                        .lineLimit(1).minimumScaleFactor(0.4)
                    Text(e.glosses.first ?? "").font(.subheadline).foregroundStyle(Palette.muted).multilineTextAlignment(.center)
                    ArticleButtons(answer: e.gender, chosen: chosen, pick: answer)
                    feedback(for: e).frame(minHeight: 44)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
            } else {
                allClear
            }

            HStack(spacing: 8) {
                stat("\(streak)", "Streak")
                stat("\(store.bestStreak)", "Best")
                stat(total == 0 ? "–" : "\(Int((Double(right) / Double(total) * 100).rounded()))%", "Accuracy")
                stat("\(store.missedNouns.count)", "To review")
            }
        }
        .padding(16)
        .outlined(radius: 24)
        .onAppear { if current == nil { next() } }
        .onChange(of: store.level) { next() }
    }

    private var allClear: some View {
        VStack(spacing: 6) {
            Text("All clear").font(.title3.weight(.bold))
            Text("No mistakes left to review. Switch back to your level's nouns.").font(.subheadline).foregroundStyle(Palette.muted)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 20)
        .multilineTextAlignment(.center)
    }

    /// Flashcard mode: see the noun, think of its article, flip, then say (or swipe) whether you knew it.
    @ViewBuilder private var nounCards: some View {
        if let e = current {
            VStack(spacing: 12) {
                SwipeCard(leftLabel: "Not yet", rightLabel: "Knew it", onTap: flip, onSwipe: knew) {
                    if flipped {
                        Text("noun").font(.footnote).foregroundStyle(Palette.muted)
                        Text(e.article ?? "").font(.title2.weight(.bold)).foregroundStyle(Gender.color(e.gender))
                        Text(e.word).font(.system(size: 38, weight: .heavy)).foregroundStyle(Gender.color(e.gender))
                            .lineLimit(1).minimumScaleFactor(0.4)
                        if !e.plural.isEmpty { Text("die \(e.plural)").foregroundStyle(Palette.muted) }
                        Text(e.glosses.first ?? "")
                        if let rule = Gender.rule(for: e.word, gender: e.gender) { Text(rule).font(.footnote).foregroundStyle(Palette.muted) }
                    } else {
                        Text("der, die or das?").font(.footnote).foregroundStyle(Palette.muted)
                        Text(e.word).font(.system(size: 38, weight: .heavy)).foregroundStyle(Palette.text)
                            .lineLimit(1).minimumScaleFactor(0.4)
                        Text(e.glosses.first ?? "").foregroundStyle(Palette.muted)
                        Text("Tap to see the article").font(.footnote).foregroundStyle(Palette.muted)
                    }
                }
                .id(e.id)
                HStack(spacing: 10) {
                    Button { knew(false) } label: { Text("Not yet").frame(maxWidth: .infinity) }
                        .buttonStyle(SecondaryButton(color: Palette.bad))
                    Button { knew(true) } label: { Text("Knew it").frame(maxWidth: .infinity) }
                        .buttonStyle(PrimaryButton())
                }
                Text("Swipe right if you knew it, left if not · \(known) known · \(store.missedNouns.count) to review")
                    .font(.footnote).foregroundStyle(Palette.muted).multilineTextAlignment(.center)
            }
        } else {
            allClear
        }
    }

    private func flip() {
        flipped.toggle()
        if flipped, let e = current { Speaker.shared.say(e.spoken) }
    }

    private func knew(_ ok: Bool) {
        guard let e = current else { return }
        store.recordQuiz(e.word, correct: ok)
        if ok { known += 1 }
        next()
    }

    @ViewBuilder private func feedback(for e: Entry) -> some View {
        if let chosen {
            let ok = chosen == e.gender
            VStack(spacing: 4) {
                Text(ok ? "Correct!" : "Not quite.").font(.headline).foregroundStyle(ok ? Palette.ok : Palette.bad)
                Text("**\(e.spoken)**" + (e.plural.isEmpty ? "" : ", die \(e.plural)")).font(.subheadline)
                if let rule = Gender.rule(for: e.word, gender: e.gender) { Text(rule).font(.footnote).foregroundStyle(Palette.muted) }
                if !ok { Button("Next word", action: next).buttonStyle(PrimaryButton()).padding(.top, 6) }
            }
            .multilineTextAlignment(.center)
        } else {
            Text("Tap the article you think is right.").font(.footnote).foregroundStyle(Palette.muted)
        }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.title3.weight(.bold)).monospacedDigit()
            Text(label).font(.caption2).foregroundStyle(Palette.muted).lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 10)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 12))
    }

    private func answer(_ g: String) {
        guard let e = current, chosen == nil else { return }
        chosen = g
        total += 1
        let ok = g == e.gender
        if ok { right += 1; streak += 1 } else { streak = 0 }
        store.recordQuiz(e.word, correct: ok, streak: streak)
        Speaker.shared.say(e.spoken)
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
        flipped = false
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
            Text("Endings that give the article away").font(.title3.weight(.heavy))
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: 10) { ForEach(columns, id: \.0) { column($0.0, $0.1) } }
                VStack(spacing: 10) { ForEach(columns, id: \.0) { column($0.0, $0.1) } }
            }
            Text("Compound nouns take the article of their last part: das Kind + das Zimmer = **das** Kinderzimmer.")
                .font(.footnote).foregroundStyle(Palette.muted)
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
