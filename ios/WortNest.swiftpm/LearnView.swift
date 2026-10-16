import SwiftUI

struct LearnView: View {
    @EnvironmentObject var store: Store
    @State private var currentKey: String?
    @State private var flipped = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Star any word in the dictionary to add it here. Cards you know move up a box and come back less often.")
                        .foregroundStyle(.secondary)
                    flashcardArea
                    savedList
                }
                .padding()
            }
            .navigationTitle("Üben")
            .appDestinations()
        }
    }

    @ViewBuilder private var flashcardArea: some View {
        if store.saved.isEmpty {
            VStack(spacing: 12) {
                Text("No saved words yet").font(.headline)
                Text("Tap the star on any dictionary entry, or start with 20 everyday nouns.")
                    .foregroundStyle(.secondary).multilineTextAlignment(.center)
                Button("Add 20 common nouns") { store.addCommonNouns() }.buttonStyle(.borderedProminent)
            }
            .frame(maxWidth: .infinity)
            .card()
        } else {
            let due = store.dueKeys
            if due.isEmpty {
                VStack(spacing: 12) {
                    Text("All caught up").font(.headline)
                    Text("No cards are due right now. Come back later or add more words.").foregroundStyle(.secondary)
                    boxes
                }
                .frame(maxWidth: .infinity)
                .card()
            } else if let key = currentKey, due.contains(key), let e = entry(for: key) {
                VStack(spacing: 14) {
                    Button { flip(e) } label: { face(e, dueCount: due.count) }
                        .buttonStyle(.plain)
                    if flipped {
                        HStack {
                            Button("Again") { grade(key, false) }.buttonStyle(.bordered)
                            Button("I knew it") { grade(key, true) }.buttonStyle(.borderedProminent)
                        }
                    }
                    boxes
                }
            } else {
                Color.clear.frame(height: 1).onAppear {
                    currentKey = due.randomElement()
                    flipped = false
                }
            }
        }
    }

    private func face(_ e: Entry, dueCount: Int) -> some View {
        VStack(spacing: 10) {
            Text("\(e.posName.uppercased()) · \(dueCount) DUE").font(.caption.weight(.bold)).foregroundStyle(.secondary)
            if flipped {
                Text([e.article, e.word].compactMap { $0 }.joined(separator: " "))
                    .font(.largeTitle.weight(.heavy))
                    .foregroundStyle(e.isNoun && !e.gender.isEmpty ? Gender.color(e.gender) : .primary)
                if e.isNoun, !e.plural.isEmpty { Text("die \(e.plural)").foregroundStyle(.secondary) }
                Text(e.glosses.prefix(2).joined(separator: "; ")).multilineTextAlignment(.center)
            } else {
                Text(e.word).font(.largeTitle.weight(.heavy))
                Text("Tap to reveal").font(.footnote).foregroundStyle(.secondary)
            }
        }
        .minimumScaleFactor(0.5)
        .frame(maxWidth: .infinity, minHeight: 200)
        .padding()
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 16))
        .contentShape(Rectangle())
    }

    private var boxes: some View {
        let values = Array(store.saved.values)
        return HStack(spacing: 8) {
            ForEach(1...5, id: \.self) { b in
                VStack(spacing: 2) {
                    Text("\(values.filter { $0.box == b }.count)")
                        .font(.headline).monospacedDigit()
                        .frame(width: 44, height: 34)
                        .background(.background.tertiary, in: RoundedRectangle(cornerRadius: 7))
                    Text("Box \(b)").font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder private var savedList: some View {
        let list = store.savedEntries()
        VStack(alignment: .leading, spacing: 8) {
            Text("Saved words" + (list.isEmpty ? "" : " (\(list.count))")).font(.headline)
            if list.isEmpty {
                Text("Nothing saved yet.").font(.subheadline).foregroundStyle(.secondary)
            } else {
                VStack(spacing: 0) {
                    ForEach(list) { e in
                        EntryRow(entry: e)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Divider()
                    }
                }
                .background(.background.secondary, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private func entry(for key: String) -> Entry? {
        let parts = key.split(separator: "|", maxSplits: 1).map(String.init)
        return parts.count == 2 ? store.entry(word: parts[0], pos: parts[1]) : nil
    }

    private func flip(_ e: Entry) {
        flipped.toggle()
        if flipped { Speaker.shared.say([e.article, e.word].compactMap { $0 }.joined(separator: " ")) }
    }

    private func grade(_ key: String, _ knew: Bool) {
        store.grade(key, knew: knew)
        currentKey = store.dueKeys.randomElement()
        flipped = false
    }
}
