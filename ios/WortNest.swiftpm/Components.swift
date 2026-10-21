import SwiftUI

/// The coloured der / die / das tag.
struct ArticleChip: View {
    let gender: String
    var large = false

    var body: some View {
        if let art = Gender.article(gender) {
            Text(art)
                .font(large ? .headline : .caption.weight(.bold))
                .foregroundStyle(Gender.color(gender))
                .padding(.horizontal, large ? 10 : 6)
                .padding(.vertical, large ? 4 : 1)
                .background(Gender.color(gender).opacity(0.15), in: RoundedRectangle(cornerRadius: 5))
        }
    }
}

struct POSLabel: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.caption2.weight(.bold))
            .kerning(0.6)
            .foregroundStyle(.secondary)
    }
}

/// One line in a result list: German on the left, English underneath.
struct EntryRow: View {
    let entry: Entry

    var body: some View {
        NavigationLink(value: entry) {
            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    if entry.isNoun { ArticleChip(gender: entry.gender) }
                    Text(entry.word)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(entry.isNoun && !entry.gender.isEmpty ? Gender.color(entry.gender) : .primary)
                    if entry.isNoun, !entry.plural.isEmpty {
                        Text("pl. \(entry.plural)").font(.caption).foregroundStyle(.secondary)
                    }
                    if !entry.isNoun { POSLabel(text: entry.posName) }
                    LevelBadge(level: entry.level)
                }
                Text(entry.glosses.prefix(3).joined(separator: "; "))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            .padding(.vertical, 2)
        }
    }
}

struct SpeakButton: View {
    let text: String
    var body: some View {
        Button { Speaker.shared.say(text) } label: {
            Image(systemName: "speaker.wave.2")
        }
        .buttonStyle(.bordered)
        .accessibilityLabel("Pronounce")
    }
}

struct SaveButton: View {
    @EnvironmentObject var store: Store
    let entry: Entry
    var body: some View {
        let on = store.isSaved(entry)
        Button { store.toggleSaved(entry) } label: {
            Image(systemName: on ? "star.fill" : "star")
        }
        .buttonStyle(.bordered)
        .tint(on ? .orange : .secondary)
        .accessibilityLabel(on ? "Remove from flashcards" : "Add to flashcards")
    }
}

/// Test-only launch options, e.g. `-demoQuery ging`. Compiled out of release builds.
enum DebugLaunch {
    static func string(_ key: String) -> String? {
        #if DEBUG
        return UserDefaults.standard.string(forKey: key)
        #else
        return nil
        #endif
    }
}

extension View {
    /// A search field that stays visible above the list instead of hiding until you pull down.
    @ViewBuilder func pinnedSearch(text: Binding<String>, prompt: String) -> some View {
        #if os(iOS)
        self.searchable(text: text, placement: .navigationBarDrawer(displayMode: .always), prompt: prompt)
        #else
        self.searchable(text: text, prompt: prompt)
        #endif
    }

    /// German input: no auto-capitalisation or autocorrect on iPhone.
    @ViewBuilder func germanInput() -> some View {
        #if os(iOS)
        self.textInputAutocapitalization(.never).autocorrectionDisabled()
        #else
        self.autocorrectionDisabled()
        #endif
    }

    @ViewBuilder func inlineTitle() -> some View {
        #if os(iOS)
        self.navigationBarTitleDisplayMode(.inline)
        #else
        self
        #endif
    }

    func card() -> some View {
        self
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 14))
    }
}

/// Destinations shared by every navigation stack in the app.
struct AppDestinations: ViewModifier {
    func body(content: Content) -> some View {
        content
            .navigationDestination(for: Entry.self) { EntryView(entry: $0) }
            .navigationDestination(for: VerbRoute.self) { VerbDetailView(infinitive: $0.infinitive) }
            .navigationDestination(for: GrammarTopic.self) { GrammarTopicView(topic: $0) }
    }
}

extension View {
    func appDestinations() -> some View { modifier(AppDestinations()) }
}
