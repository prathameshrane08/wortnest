import SwiftUI

struct DictionaryView: View {
    @EnvironmentObject var store: Store
    @State private var query = ""
    @State private var result = SearchResult()

    private let examples = ["Haus", "gehen", "schön", "Zeitung", "Mädchen", "bringen", "Freund", "trotzdem"]

    var body: some View {
        NavigationStack {
            List {
                if query.trimmingCharacters(in: .whitespaces).isEmpty {
                    Section("Try one of these") {
                        ForEach(examples.compactMap { store.entries(for: $0).first }) { EntryRow(entry: $0) }
                    }
                } else if result.isEmpty {
                    ContentUnavailableView(
                        "No match for “\(query)”",
                        systemImage: "magnifyingglass",
                        description: Text("Check the spelling, or try the base form (gehen instead of gegangen).")
                    )
                } else {
                    if !result.exact.isEmpty {
                        Section("German") { ForEach(result.exact) { EntryRow(entry: $0) } }
                    }
                    if !result.viaForm.isEmpty {
                        Section("“\(query)” is a form of \(result.lemmas.joined(separator: ", "))") {
                            ForEach(result.viaForm) { EntryRow(entry: $0) }
                        }
                    }
                    if !result.english.isEmpty {
                        Section("English → German") { ForEach(result.english) { EntryRow(entry: $0) } }
                    }
                    if !result.prefix.isEmpty {
                        Section("Starting with “\(query)”") { ForEach(result.prefix) { EntryRow(entry: $0) } }
                    }
                }
            }
            .navigationTitle("Wörterbuch")
            .searchable(text: $query, prompt: "German or English")
            .germanInput()
            .task(id: query) {
                try? await Task.sleep(for: .milliseconds(150))
                guard !Task.isCancelled else { return }
                result = store.search(query)
            }
            .appDestinations()
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
                    if entry.gender == "p" { Text("plural only").font(.subheadline).foregroundStyle(.secondary) }
                }
                if let v = store.verbs[entry.word], entry.pos == "verb" {
                    Text("\(v.pr[2]) · \(v.pt[2]) · \(Conjugation.mainAux(v) == "sein" ? "ist" : "hat") \(v.pp)")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(Array(entry.glosses.enumerated()), id: \.offset) { i, g in
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text("\(i + 1).").foregroundStyle(.secondary).monospacedDigit()
                            Text(g)
                        }
                    }
                }
                .card()
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
