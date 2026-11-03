import SwiftUI

struct VerbsView: View {
    @EnvironmentObject var store: Store
    @State private var query = DebugLaunch.string("demoVerbQuery") ?? ""
    @State private var results: [String] = []
    @State private var path = NavigationPath(DebugLaunch.string("demoVerb").map { [VerbRoute(infinitive: $0)] } ?? [])

    private let common = ["sein", "haben", "werden", "können", "gehen", "kommen", "sehen", "geben", "nehmen", "anfangen", "bringen", "wissen"]

    var body: some View {
        NavigationStack(path: $path) {
            List {
                if query.trimmingCharacters(in: .whitespaces).isEmpty {
                    Section {
                        NavigationLink { DrillView() } label: {
                            Label("Type the right form", systemImage: "keyboard")
                        }
                    }
                    Section("Common verbs") {
                        ForEach(common.filter { store.verbs[$0] != nil }, id: \.self) { verbRow($0) }
                    }
                } else if results.isEmpty {
                    ContentUnavailableView.search(text: query)
                } else {
                    ForEach(results, id: \.self) { verbRow($0) }
                }
            }
            .navigationTitle("Verbs")
            .listStyle(.plain)
            .pinnedSearch(text: $query, prompt: "gehen, fing an, to bring")
            .germanInput()
            .task(id: query) {
                try? await Task.sleep(for: .milliseconds(150))
                guard !Task.isCancelled else { return }
                results = store.findVerbs(query)
            }
            .appDestinations()
        }
    }

    private func verbRow(_ inf: String) -> some View {
        NavigationLink(value: VerbRoute(infinitive: inf)) {
            VStack(alignment: .leading, spacing: 2) {
                Text(inf).font(.body.weight(.semibold))
                if let e = store.verbEntry(inf) {
                    Text(e.glosses.prefix(2).joined(separator: "; ")).font(.subheadline).foregroundStyle(Palette.muted).lineLimit(1)
                }
            }
        }
    }
}

struct VerbDetailView: View {
    @EnvironmentObject var store: Store
    let infinitive: String

    var body: some View {
        if let v = store.verbs[infinitive] {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header(v)
                    principalParts(v)
                    HStack(spacing: 16) {
                        Text("Orange").foregroundColor(Palette.irregular).bold() + Text(" = differs from the regular pattern")
                    }
                    .font(.footnote).foregroundStyle(Palette.muted)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 230), spacing: 12)], spacing: 12) {
                        ForEach(Conjugation.tables(v, infinitive)) { TenseTable(table: $0) }
                    }
                }
                .padding()
            }
            .navigationTitle(infinitive)
            .inlineTitle()
        } else {
            ContentUnavailableView("No conjugation for “\(infinitive)”", systemImage: "tablecells")
        }
    }

    private func header(_ v: Verb) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(infinitive).font(.largeTitle.weight(.heavy)).minimumScaleFactor(0.5).lineLimit(1)
                Spacer()
                SpeakButton(text: "\(infinitive), \(v.pt[2]), \(v.pp)")
                if let e = store.verbEntry(infinitive) { SaveButton(entry: e) }
            }
            HStack(spacing: 6) {
                if let kind = Conjugation.kindLabel(v) { badge(kind, highlight: v.t != "weak") }
                badge("Perfect tense with " + Conjugation.auxiliaries(v).joined(separator: " or "))
                if let sep = Conjugation.separablePrefix(v, infinitive) { badge("Separable: \(sep)-") }
            }
            if let e = store.verbEntry(infinitive) {
                Text(e.glosses.prefix(3).joined(separator: "; "))
            }
        }
    }

    private func badge(_ text: String, highlight: Bool = false) -> some View {
        Text(text)
            .font(.caption.weight(.bold))
            .padding(.horizontal, 8).padding(.vertical, 3)
            .foregroundStyle(highlight ? Palette.irregular : Palette.muted)
            .background(highlight ? Palette.irregular.opacity(0.15) : Palette.surfaceHigh, in: RoundedRectangle(cornerRadius: 6))
    }

    private func principalParts(_ v: Verb) -> some View {
        let parts = [
            ("Infinitive", infinitive),
            ("Present, er", v.pr[2]),
            ("Past, er", v.pt[2]),
            ("Past participle", (Conjugation.mainAux(v) == "sein" ? "ist " : "hat ") + v.pp)
        ]
        return LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: 8)], spacing: 8) {
            ForEach(parts, id: \.0) { label, value in
                VStack(alignment: .leading, spacing: 2) {
                    Text(label).font(.caption).foregroundStyle(Palette.muted)
                    Text(value).font(.headline)
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Palette.surface, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}

struct TenseTable: View {
    let table: Conjugation.Table

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            (Text(table.title).font(.subheadline.weight(.bold))
                + Text(table.de.isEmpty ? "" : "  " + table.de).font(.footnote).foregroundColor(Palette.muted))
            Grid(alignment: .leading, horizontalSpacing: 10, verticalSpacing: 3) {
                ForEach(Array(table.rows.enumerated()), id: \.offset) { _, row in
                    GridRow {
                        Text(row.person).foregroundStyle(Palette.muted).font(.subheadline)
                        formText(row.form, irregular: row.irregular)
                    }
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .outlined(radius: 16)
    }

    /// Bolds the personal ending of the conjugated word; irregular forms turn orange.
    private func formText(_ form: String, irregular: Bool) -> Text {
        let color: Color = irregular ? Palette.irregular : Palette.text
        guard table.showEndings else { return Text(form).foregroundColor(color) }
        let parts = form.split(separator: " ", maxSplits: 1).map(String.init)
        let word = parts[0]
        let rest = parts.count > 1 ? " " + parts[1] : ""
        let endings = ["test", "est", "ten", "tet", "st", "et", "en", "te", "t", "e", "n"]
        if let end = endings.first(where: { word.hasSuffix($0) && word.count > $0.count + 1 }) {
            return Text(String(word.dropLast(end.count))).foregroundColor(color)
                + Text(end).bold().foregroundColor(color)
                + Text(rest).foregroundColor(color)
        }
        return Text(form).foregroundColor(color)
    }
}

struct DrillView: View {
    @EnvironmentObject var store: Store
    @State private var tense = "pr"
    @State private var verb = ""
    @State private var person = 0
    @State private var answerText = ""
    @State private var expected = ""
    @State private var checked: Bool?
    @State private var right = 0
    @State private var total = 0
    @FocusState private var focused: Bool

    var body: some View {
        Form {
            Picker("Tense", selection: $tense) {
                Text("Present").tag("pr")
                Text("Past").tag("pt")
                Text("Perfect").tag("pf")
            }
            .pickerStyle(.segmented)

            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text(verb).font(.title2.weight(.heavy))
                    if let e = store.verbEntry(verb) {
                        Text(e.glosses.first ?? "").font(.subheadline).foregroundStyle(Palette.muted)
                    }
                }
                HStack {
                    Text(Conjugation.pronouns[person]).font(.title3.weight(.semibold))
                    TextField("your answer", text: $answerText)
                        .germanInput()
                        .focused($focused)
                        .onSubmit(check)
                        .font(.title3)
                }
            } header: {
                Text((["pr": "Present", "pt": "Simple past", "pf": "Perfect"][tense] ?? "") + " · \(levelRange(store.level)) verbs")
            } footer: {
                Text(total == 0 ? "Type the form and press return." : "\(right) of \(total) right this session")
            }

            if let checked {
                Section {
                    Label {
                        Text(checked ? "Correct! " : "Not quite. ") + Text("\(Conjugation.pronouns[person]) **\(expected)**")
                    } icon: {
                        Image(systemName: checked ? "checkmark.circle.fill" : "xmark.circle.fill")
                            .foregroundStyle(checked ? .green : .red)
                    }
                    Button("Next verb", action: next)
                }
            } else {
                Section {
                    Button("Check", action: check).disabled(answerText.trimmingCharacters(in: .whitespaces).isEmpty)
                    Button("Skip", action: next)
                }
            }
        }
        .navigationTitle("Type the right form")
        .onAppear { if verb.isEmpty { next() } }
        .onChange(of: tense) { next() }
    }

    private func next() {
        var pool = upTo(store.levelVerbs, store.level)
        if pool.isEmpty { pool = Array(store.verbList.prefix(300)) }
        for _ in 0..<20 {
            guard let inf = pool.randomElement(), let v = store.verbs[inf] else { continue }
            let p = Int.random(in: 0..<6)
            let form: String
            switch tense {
            case "pt": form = v.pt[p]
            case "pf": form = Conjugation.aux[Conjugation.mainAux(v)]!["pr"]![p] + " " + v.pp
            default: form = v.pr[p]
            }
            guard !form.isEmpty else { continue }
            verb = inf; person = p; expected = form
            break
        }
        answerText = ""
        checked = nil
        focused = true
    }

    private func check() {
        if checked != nil { next(); return }
        let given = answerText.trimmingCharacters(in: .whitespaces).lowercased()
            .split(separator: " ").joined(separator: " ")
        guard !given.isEmpty else { return }
        let ok = given == expected.lowercased()
        checked = ok
        total += 1
        if ok { right += 1 }
    }
}
