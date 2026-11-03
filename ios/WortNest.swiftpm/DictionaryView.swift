import SwiftUI

/// The first tab: a greeting, the word of the day and your progress, and the search box for both languages.
struct DictionaryView: View {
    @EnvironmentObject var store: Store
    let openTour: () -> Void
    let openLevel: () -> Void
    @State private var query = DebugLaunch.string("demoQuery") ?? ""
    @State private var result = SearchResult()
    @State private var path = NavigationPath()
    @AppStorage("dark") private var dark = false

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    brand
                    if query.trimmingCharacters(in: .whitespaces).isEmpty { greeting }
                    SearchBox(text: $query, prompt: "Haus, ging, to run…")
                    HStack {
                        UmlautRow { query += $0 }
                        Spacer()
                        Text("Searches both directions at once").font(.caption).foregroundStyle(Palette.muted)
                    }
                    if query.trimmingCharacters(in: .whitespaces).isEmpty {
                        HomeContent(openLevel: openLevel) { query = $0 }
                    } else if result.isEmpty {
                        Text("No match for “\(query)”. Check the spelling, or try the base form (gehen instead of gegangen).")
                            .foregroundStyle(Palette.muted).padding(.top, 8)
                    } else {
                        results
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(Palette.background)
            .toolbar(.hidden, for: .navigationBar)
            .task(id: query) {
                try? await Task.sleep(for: .milliseconds(150))
                guard !Task.isCancelled else { return }
                result = store.search(query)
            }
            .appDestinations()
            .onAppear {
                if path.isEmpty, let w = DebugLaunch.string("demoEntry"), let e = store.entries(for: w).first { path.append(e) }
            }
        }
    }

    /// The app icon in small (a box holding der, die and das), the name, the theme switch and the walkthrough.
    private var brand: some View {
        HStack(spacing: 9) {
            AppMark().frame(width: 28, height: 28)
            (Text("Deutsch ") + Text("Täglich").foregroundColor(Palette.accent))
                .font(.title3.weight(.heavy)).kerning(-0.5)
            Spacer()
            Button { dark.toggle() } label: {
                Image(systemName: dark ? "sun.max" : "moon")
                    .font(.subheadline.weight(.semibold))
                    .frame(width: 34, height: 34)
                    .background(Palette.accentSoft, in: Circle())
                    .foregroundStyle(Palette.accentText)
            }
            .accessibilityLabel(dark ? "Switch to light mode" : "Switch to dark mode")
            Button(action: openTour) {
                Label("Tour", systemImage: "questionmark.circle")
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(Palette.accentSoft, in: Capsule())
                    .foregroundStyle(Palette.accentText)
            }
        }
        .padding(.top, 8)
    }

    /// "Guten Morgen", its English, and what is waiting today; each item opens the place to do it.
    private var greeting: some View {
        let h = Calendar.current.component(.hour, from: Date())
        let n = store.counts()
        let due = n.fresh + n.learning + n.review
        let missed = store.missedNouns.count
        let nextTopic = store.grammar.first { !store.topicsSeen.contains($0.id) }
        let any = due > 0 || missed > 0 || nextTopic != nil
        func plural(_ k: Int, _ w: String) -> String { "\(k) \(w)" + (k == 1 ? "" : "s") }
        return VStack(alignment: .leading, spacing: 2) {
            Text((h < 11 ? "Guten Morgen" : h < 18 ? "Guten Tag" : "Guten Abend") + " 👋")
                .font(.system(size: 30, weight: .heavy)).kerning(-0.8)
            Text((h < 11 ? "Good morning. " : h < 18 ? "Good afternoon. " : "Good evening. ")
                 + (any ? "For today:" : "All caught up for today."))
                .foregroundStyle(Palette.muted)
            if any {
                FlowLayout(spacing: 8) {
                    if due > 0 { todoChip("\(plural(due, "flashcard")) to practise", Palette.accent) { store.tab = 4 } }
                    if missed > 0 { todoChip("\(plural(missed, "noun")) you got wrong", Gender.color("n")) { store.tab = 1 } }
                    if let t = nextTopic { todoChip("Grammar: \(t.title)", Palette.amber) { path.append(t) } }
                }
                .padding(.top, 10)
            }
        }
        .padding(.top, 6)
    }

    private func todoChip(_ label: String, _ dot: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Circle().fill(dot).frame(width: 8, height: 8)
                Text(label).font(.subheadline.weight(.medium)).foregroundStyle(Palette.text).lineLimit(1)
            }
            .padding(.horizontal, 12).padding(.vertical, 7)
            .overlay(Capsule().stroke(Palette.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var results: some View {
        if !result.exact.isEmpty { ResultSection(title: "German", entries: result.exact) }
        if !result.viaForm.isEmpty {
            ResultSection(title: "“\(query)” is a form of \(result.lemmas.joined(separator: ", "))", entries: result.viaForm)
        }
        if !result.english.isEmpty { ResultSection(title: "English → German", entries: result.english) }
        if !result.prefix.isEmpty { ResultSection(title: "Starting with “\(query)”", entries: result.prefix) }
    }
}

/// A heading and its rows, in a bordered box.
struct ResultSection: View {
    let title: String
    let entries: [Entry]
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(Palette.muted).padding(.top, 8)
            VStack(spacing: 0) {
                ForEach(Array(entries.enumerated()), id: \.element.id) { k, e in
                    if k > 0 { Divider() }
                    EntryRow(entry: e).padding(.horizontal, 14).padding(.vertical, 10)
                }
            }
            .outlined()
        }
    }
}

/// A search field that looks the same on every tab.
struct SearchBox: View {
    @Binding var text: String
    let prompt: String
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundStyle(Palette.muted)
            TextField(prompt, text: $text).germanInput().submitLabel(.search)
            if !text.isEmpty {
                Button { text = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(Palette.muted) }
                    .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 13)
        .background(Palette.surfaceHigh, in: RoundedRectangle(cornerRadius: 14))
    }
}

/// The app icon drawn small: a box holding three bars in the der, die and das colours.
struct AppMark: View {
    var body: some View {
        Canvas { ctx, size in
            let u = size.width / 32
            for (i, g) in ["m", "f", "n"].enumerated() {
                ctx.fill(Path(roundedRect: CGRect(x: 9 * u, y: CGFloat(5 + 7 * i) * u, width: 14 * u, height: 4.4 * u), cornerRadius: 1.4 * u),
                         with: .color(Gender.color(g)))
            }
            var box = Path()
            box.move(to: CGPoint(x: 5 * u, y: 9 * u)); box.addLine(to: CGPoint(x: 5 * u, y: 24 * u))
            box.addQuadCurve(to: CGPoint(x: 8 * u, y: 27 * u), control: CGPoint(x: 5 * u, y: 27 * u))
            box.addLine(to: CGPoint(x: 24 * u, y: 27 * u))
            box.addQuadCurve(to: CGPoint(x: 27 * u, y: 24 * u), control: CGPoint(x: 27 * u, y: 27 * u))
            box.addLine(to: CGPoint(x: 27 * u, y: 9 * u))
            ctx.stroke(box, with: .color(Palette.accent), style: StrokeStyle(lineWidth: 2.6 * u, lineCap: .round))
        }
    }
}

/// Below the search box when it's empty: the word of the day, four progress tiles and words to try.
struct HomeContent: View {
    @EnvironmentObject var store: Store
    let openLevel: () -> Void
    let onTry: (String) -> Void

    var body: some View {
        let n = store.counts()
        let total = store.saved.count
        let read = store.topicsSeen.filter { id in store.grammar.contains { $0.id == id } }.count
        let next = store.grammar.first { !store.topicsSeen.contains($0.id) }
        let words = upTo(store.levelWords, store.level)
        let inLevel = words.filter { store.saved[store.entries[$0].key] != nil }.count
        VStack(alignment: .leading, spacing: 14) {
            if let e = store.wordOfDay() { WordOfDayCard(entry: e) }
            Grid(horizontalSpacing: 12, verticalSpacing: 12) {
                GridRow {
                    Tile(number: "\(n.fresh + n.learning + n.review)", title: "Cards due",
                         line: total == 0 ? "tap to start" : "\(n.known) of \(total) known well",
                         progress: frac(n.known, total), color: Palette.accent) { store.tab = 4 }
                    Tile(number: "\(store.missedNouns.count)", title: "Nouns to review", line: "best streak \(store.bestStreak)",
                         progress: frac(store.bestStreak, 50), color: Gender.color("n")) { store.tab = 1 }
                }
                GridRow {
                    Tile(number: "\(read)/\(store.grammar.count)", title: "Grammar topics", line: next.map { "next: \($0.title)" } ?? "all read",
                         progress: frac(read, store.grammar.count), color: Palette.amber) { store.tab = 3 }
                    Tile(number: Level.names[store.level], title: "Your level",
                         line: store.level < 3 ? "\(Level.names[store.level + 1]) test when you're ready" : "\(inLevel) of \(words.count) words saved",
                         progress: frac(inLevel, words.count), color: Palette.pink, action: openLevel)
                }
            }
            Text("Or look something up").font(.headline).padding(.top, 6)
            FlowLayout(spacing: 8) {
                ForEach(["Haus", "gehen", "schön", "Zeitung", "Mädchen", "bringen", "Freund", "trotzdem"], id: \.self) { w in
                    Button { onTry(w) } label: {
                        Text(w).font(.body.weight(.medium)).foregroundStyle(Palette.text)
                            .padding(.horizontal, 14).padding(.vertical, 7)
                            .overlay(Capsule().stroke(Palette.line, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.top, 4)
    }
}

private func frac(_ a: Int, _ b: Int) -> Double { b == 0 ? 0 : min(1, Double(a) / Double(b)) }

/// The dark card at the top of the home screen.
struct WordOfDayCard: View {
    @EnvironmentObject var store: Store
    let entry: Entry

    var body: some View {
        let e = entry
        let gendered = e.isNoun && !e.gender.isEmpty
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Word of the day").font(.subheadline.weight(.semibold)).foregroundStyle(Palette.heroLabel)
                Spacer()
                Text(Date().formatted(.dateTime.weekday(.wide).day().month(.wide).locale(Locale(identifier: "en_GB"))))
                    .font(.subheadline).foregroundStyle(Palette.heroMuted)
            }
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 0) {
                    if gendered { Text(Gender.article(e.gender)!).font(.title3.weight(.semibold)).foregroundStyle(Gender.colorOnDark(e.gender)) }
                    Text(e.word).font(.system(size: 46, weight: .heavy)).kerning(-1.5).foregroundStyle(.white)
                        .lineLimit(1).minimumScaleFactor(0.4)
                }
                .padding(.top, 6)
                Spacer()
                heroButton("speaker.wave.2.fill", size: 48, label: "Pronounce") { Speaker.shared.say(e.spoken) }
            }
            HStack(spacing: 10) {
                Text(e.posName).italic().font(.subheadline).foregroundStyle(Palette.heroMuted)
                if e.level > 0 {
                    Text(Level.names[e.level]).font(.caption2.weight(.bold)).foregroundStyle(.white)
                        .padding(.horizontal, 6).padding(.vertical, 1)
                        .background(.white.opacity(0.1), in: RoundedRectangle(cornerRadius: 6))
                }
                if gendered && !e.plural.isEmpty { Text("plural: die \(e.plural)").font(.subheadline).foregroundStyle(Palette.heroMuted) }
            }
            Text(e.glosses.prefix(2).joined(separator: "; ")).font(.body).foregroundStyle(Palette.heroMuted).padding(.top, 4)
            if let ex = store.examples[e.key]?.first(where: { $0.count == 2 }) {
                Rectangle().fill(.white.opacity(0.12)).frame(height: 1).padding(.vertical, 8)
                HStack(alignment: .top, spacing: 12) {
                    heroButton("speaker.wave.2.fill", size: 36, label: "Read the sentence aloud") { Speaker.shared.say(ex[0]) }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(ex[0]).fontWeight(.semibold).foregroundStyle(.white)
                        Text(ex[1]).font(.subheadline).foregroundStyle(Palette.heroMuted)
                    }
                }
            }
            HStack(spacing: 8) {
                let on = store.isSaved(e)
                Button { store.toggleSaved(e) } label: {
                    Label(on ? "Saved" : "Add to flashcards", systemImage: on ? "star.fill" : "star")
                        .lineLimit(1).minimumScaleFactor(0.7)
                }
                .buttonStyle(HeroButtonStyle())
                NavigationLink(value: e) { Text("Full entry").lineLimit(1) }
                    .buttonStyle(HeroButtonStyle())
            }
            .padding(.top, 10)
        }
        .environment(\.colorScheme, .dark)
        .padding(20)
        .background(Palette.hero, in: RoundedRectangle(cornerRadius: 24))
    }

    private func heroButton(_ icon: String, size: CGFloat, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(size > 40 ? .title3 : .footnote).foregroundStyle(.white)
                .frame(width: size, height: size)
                .background(.white.opacity(0.1), in: Circle())
        }
        .accessibilityLabel(label)
    }
}

private struct HeroButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold)).foregroundStyle(.white)
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(.white.opacity(configuration.isPressed ? 0.18 : 0.1), in: RoundedRectangle(cornerRadius: 12))
    }
}

/// A number, what it counts, one line of detail and a progress bar.
private struct Tile: View {
    let number: String
    let title: String
    let line: String
    let progress: Double
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 1) {
                Text(number).font(.system(size: 26, weight: .heavy)).kerning(-0.6).foregroundStyle(Palette.text)
                Text(title).font(.subheadline.weight(.medium)).foregroundStyle(Palette.muted)
                Text(line).font(.caption).foregroundStyle(Palette.muted.opacity(0.8)).lineLimit(1)
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Palette.surfaceHigh)
                        Capsule().fill(color).frame(width: g.size.width * max(progress, 0.03))
                    }
                }
                .frame(height: 6).padding(.top, 11)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .outlined()
        }
        .buttonStyle(.plain)
    }
}

/// Lays out chips left to right and wraps them onto new lines.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, row: CGFloat = 0, widest: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > width { x = 0; y += row + spacing; row = 0 }
            x += s.width + spacing; row = max(row, s.height); widest = max(widest, x - spacing)
        }
        return CGSize(width: proposal.width ?? widest, height: y + row)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, row: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > bounds.minX && x + s.width > bounds.maxX { x = bounds.minX; y += row + spacing; row = 0 }
            v.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(s))
            x += s.width + spacing; row = max(row, s.height)
        }
    }
}

struct EntryView: View {
    @EnvironmentObject var store: Store
    let entry: Entry

    private var spoken: String { [entry.article, entry.word].compactMap { $0 }.joined(separator: " ") }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .center, spacing: 10) {
                    if entry.isNoun { ArticleChip(gender: entry.gender, large: true) }
                    Text(entry.word)
                        .font(.largeTitle.weight(.heavy))
                        .foregroundStyle(entry.isNoun && !entry.gender.isEmpty ? Gender.color(entry.gender) : .primary)
                        .minimumScaleFactor(0.5)
                        .lineLimit(2)
                    Spacer()
                    SpeakButton(text: spoken)
                    SaveButton(entry: entry)
                }
                HStack(spacing: 12) {
                    POSLabel(text: entry.posName)
                    if entry.isNoun, !entry.plural.isEmpty {
                        Text("plural: **die \(entry.plural)**").font(.subheadline)
                    }
                    if entry.gender == "p" { Text("plural only").font(.subheadline).foregroundStyle(Palette.muted) }
                    if entry.level > 0 {
                        HStack(spacing: 4) {
                            LevelBadge(level: entry.level)
                            Text("\(Level.names[entry.level]) word").font(.subheadline).foregroundStyle(Palette.muted)
                        }
                    }
                }
                if let v = store.verbs[entry.word], entry.pos == "verb" {
                    Text("\(v.pr[2]) · \(v.pt[2]) · \(Conjugation.mainAux(v) == "sein" ? "ist" : "hat") \(v.pp)")
                        .font(.subheadline).foregroundStyle(Palette.muted)
                }
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(Array(entry.glosses.enumerated()), id: \.offset) { i, g in
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text("\(i + 1).").foregroundStyle(Palette.muted).monospacedDigit()
                            Text(g)
                        }
                    }
                }
                .card()
                if let examples = store.examples[entry.key], !examples.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Examples").font(.subheadline.weight(.semibold)).foregroundStyle(Palette.muted)
                        ForEach(Array(examples.enumerated()), id: \.offset) { _, ex in
                            if ex.count == 2 {
                                HStack(alignment: .top, spacing: 10) {
                                    SpeakButton(text: ex[0])
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(ex[0]).fontWeight(.semibold)
                                        Text(ex[1]).font(.subheadline).foregroundStyle(Palette.muted)
                                    }
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .card()
                }
                if entry.pos == "verb", store.verbs[entry.word] != nil {
                    NavigationLink(value: VerbRoute(infinitive: entry.word)) {
                        Label("Full conjugation", systemImage: "tablecells")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            .padding()
        }
        .inlineTitle()
    }
}
