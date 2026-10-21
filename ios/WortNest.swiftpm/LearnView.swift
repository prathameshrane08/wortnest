import SwiftUI

struct LearnView: View {
    @EnvironmentObject var store: Store
    @State private var currentKey: String?
    @State private var flipped = DebugLaunch.string("demoFlip") != nil

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Star any word in the dictionary to add it here. Words you know come back less and less often; words you miss come back soon.")
                        .foregroundStyle(.secondary)
                    flashcardArea
                    savedList
                }
                .padding()
            }
            .navigationTitle("Üben")
            .onAppear { if DebugLaunch.string("demoSeed") != nil && store.saved.isEmpty { store.addLevel(1) } }
            .appDestinations()
        }
    }

    @ViewBuilder private var flashcardArea: some View {
        if store.saved.isEmpty {
            VStack(spacing: 12) {
                Text("No saved words yet").font(.headline)
                Text("Tap the star on any dictionary entry, or start with the 20 most useful words of a level.")
                    .foregroundStyle(.secondary).multilineTextAlignment(.center)
                addLevelRow
            }
            .frame(maxWidth: .infinity)
            .card()
        } else {
            let queue = store.studyQueue()
            if queue.isEmpty {
                VStack(spacing: 12) {
                    Text("All done for now").font(.headline)
                    Text(store.saved.values.contains(where: \.isNew)
                         ? "You've had today's \(Srs.newPerDay) new words. More arrive tomorrow."
                         : "No cards are due. Come back later, or star more words.")
                        .foregroundStyle(.secondary).multilineTextAlignment(.center)
                    stats
                }
                .frame(maxWidth: .infinity)
                .card()
            } else if let key = currentKey, queue.contains(key), let e = entry(for: key), let card = store.saved[key] {
                VStack(spacing: 14) {
                    Button { flip(e) } label: { face(e, card: card) }
                        .buttonStyle(.plain)
                    if flipped {
                        HStack(spacing: 8) {
                            ForEach(0..<4, id: \.self) { g in gradeButton(key, card: card, grade: g) }
                        }
                    } else {
                        Button("Show answer") { flip(e) }.buttonStyle(.borderedProminent)
                    }
                    stats
                }
            } else {
                Color.clear.frame(height: 1).onAppear {
                    currentKey = queue.first
                    flipped = DebugLaunch.string("demoFlip") != nil
                }
            }
        }
    }

    private func gradeButton(_ key: String, card: Card, grade g: Int) -> some View {
        Button { grade(key, g) } label: {
            VStack(spacing: 2) {
                Text(Srs.gradeNames[g]).font(.subheadline.weight(.bold))
                Text(Srs.label(card, grade: g)).font(.caption).opacity(0.8)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 4)
        }
        .tint(g == 0 ? .red : g == 2 ? .accentColor : .secondary)
        .modifier(GradeStyle(prominent: g == 2))
    }

    private func face(_ e: Entry, card: Card) -> some View {
        VStack(spacing: 10) {
            HStack(spacing: 6) {
                Text(e.posName.uppercased()).font(.caption.weight(.bold)).foregroundStyle(.secondary)
                if !flipped && (card.isNew || card.isLearning) {
                    Text(card.isNew ? "NEW" : "LEARNING")
                        .font(.caption2.weight(.bold))
                        .padding(.horizontal, 6).padding(.vertical, 1)
                        .background(.background.tertiary, in: RoundedRectangle(cornerRadius: 4))
                        .foregroundStyle(card.isNew ? Color.blue : Color.red)
                }
            }
            if flipped {
                Text([e.article, e.word].compactMap { $0 }.joined(separator: " "))
                    .font(.largeTitle.weight(.heavy))
                    .foregroundStyle(e.isNoun && !e.gender.isEmpty ? Gender.color(e.gender) : .primary)
                if e.isNoun, !e.plural.isEmpty { Text("die \(e.plural)").foregroundStyle(.secondary) }
                Text(e.glosses.prefix(2).joined(separator: "; ")).multilineTextAlignment(.center)
                if let ex = store.examples[e.key]?.first, ex.count == 2 {
                    Divider()
                    Text(ex[0]).italic().multilineTextAlignment(.center)
                    Text(ex[1]).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
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

    private var stats: some View {
        let c = store.counts()
        return VStack(spacing: 8) {
            HStack(spacing: 8) {
                stat(c.fresh, "New", .blue)
                stat(c.learning, "Learning", .red)
                stat(c.review, "To review", .green)
            }
            Text("\(c.known) of \(store.saved.count) words known well · \(c.tomorrow) due by tomorrow")
                .font(.caption).foregroundStyle(.secondary)
            addLevelRow.padding(.top, 6)
        }
        .frame(maxWidth: .infinity)
    }

    private var addLevelRow: some View {
        VStack(spacing: 6) {
            Text("Add 20 words from").font(.caption).foregroundStyle(.secondary)
            HStack(spacing: 8) {
                ForEach(1...3, id: \.self) { l in
                    let left = store.wordsLeft(l)
                    Button { store.addLevel(l) } label: {
                        VStack(spacing: 0) {
                            Text(Level.names[l]).font(.subheadline.weight(.bold))
                            Text("\(left) left").font(.caption2).foregroundStyle(.secondary)
                        }
                        .frame(minWidth: 64)
                    }
                    .buttonStyle(.bordered)
                    .disabled(left == 0)
                }
            }
        }
    }

    private func stat(_ n: Int, _ label: String, _ color: Color) -> some View {
        VStack(spacing: 3) {
            Text("\(n)")
                .font(.headline).monospacedDigit().foregroundStyle(color)
                .frame(width: 84, height: 36)
                .background(.background.tertiary, in: RoundedRectangle(cornerRadius: 7))
            Text(label).font(.caption2).foregroundStyle(.secondary)
        }
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
                            .buttonStyle(.plain)   // keep the row's own colours instead of the accent tint
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

    private func grade(_ key: String, _ g: Int) {
        store.grade(key, g)
        currentKey = store.studyQueue().first
        flipped = false
    }
}

/// Good is the button you'll press most, so it stands out.
private struct GradeStyle: ViewModifier {
    let prominent: Bool
    func body(content: Content) -> some View {
        if prominent { content.buttonStyle(.borderedProminent) } else { content.buttonStyle(.bordered) }
    }
}
