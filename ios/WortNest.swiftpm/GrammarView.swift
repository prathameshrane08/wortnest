import SwiftUI

/// One topic of the A1–B1 grammar guide (grammar.json, shared with the web and Android apps).
struct GrammarTopic: Codable, Identifiable, Hashable {
    let id: String
    let level: Int
    let title: String
    let de: String
    let summary: String
    let blocks: [GrammarBlock]
    let verbs: [String]?

    static func == (a: GrammarTopic, b: GrammarTopic) -> Bool { a.id == b.id }
    func hash(into h: inout Hasher) { h.combine(id) }
}

/// A paragraph ("p"), a tip ("tip"), example sentences ("ex") or a table ("table").
struct GrammarBlock: Codable {
    let t: String
    var text: String?
    var examples: [[String]]?
    var head: [String]?
    var rows: [[String]]?

    private enum Keys: String, CodingKey { case t, x, head, rows }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        t = try c.decode(String.self, forKey: .t)
        head = try c.decodeIfPresent([String].self, forKey: .head)
        rows = try c.decodeIfPresent([[String]].self, forKey: .rows)
        if t == "ex" { examples = try c.decodeIfPresent([[String]].self, forKey: .x) } else { text = try c.decodeIfPresent(String.self, forKey: .x) }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: Keys.self)
        try c.encode(t, forKey: .t)
        try c.encodeIfPresent(head, forKey: .head)
        try c.encodeIfPresent(rows, forKey: .rows)
        if let examples { try c.encode(examples, forKey: .x) } else { try c.encodeIfPresent(text, forKey: .x) }
    }
}

enum Level {
    static let names = ["", "A1", "A2", "B1"]
    static let titles = ["", "Beginner", "Elementary", "Intermediate"]

    static func color(_ level: Int) -> Color {
        switch level {
        case 1: return .green
        case 2: return .blue
        case 3: return .purple
        default: return .secondary
        }
    }
}

/// The small A1 / A2 / B1 tag.
struct LevelBadge: View {
    let level: Int
    var body: some View {
        if level > 0 {
            Text(Level.names[level])
                .font(.caption2.weight(.heavy))
                .foregroundStyle(Level.color(level))
                .padding(.horizontal, 5).padding(.vertical, 1)
                .background(Level.color(level).opacity(0.15), in: RoundedRectangle(cornerRadius: 4))
                .accessibilityLabel("Level \(Level.names[level])")
        }
    }
}

/// Text with **bold** parts.
func richText(_ s: String) -> Text {
    Text((try? AttributedString(markdown: s)) ?? AttributedString(s))
}

struct GrammarView: View {
    @EnvironmentObject var store: Store
    @State private var path = NavigationPath()

    var body: some View {
        NavigationStack(path: $path) {
            List {
                Section {
                    Text("The topics German courses cover from A1 to B1, each with tables, examples and the mistakes learners make most.")
                        .foregroundStyle(.secondary)
                }
                ForEach(1...3, id: \.self) { level in
                    Section {
                        ForEach(store.grammar.filter { $0.level == level }) { t in
                            NavigationLink(value: t) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(t.title).font(.body.weight(.semibold))
                                    Text(t.summary).font(.subheadline).foregroundStyle(.secondary)
                                }
                                .padding(.vertical, 2)
                            }
                        }
                    } header: {
                        HStack(spacing: 6) {
                            LevelBadge(level: level)
                            Text(Level.titles[level])
                        }
                    }
                }
            }
            .navigationTitle("Grammatik")
            .appDestinations()
            .onAppear {
                if path.isEmpty, let id = DebugLaunch.string("demoTopic"), let t = store.grammar.first(where: { $0.id == id }) { path.append(t) }
            }
        }
    }
}

struct GrammarTopicView: View {
    @EnvironmentObject var store: Store
    let topic: GrammarTopic

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 6) {
                    LevelBadge(level: topic.level)
                    Text(topic.de.uppercased()).font(.caption.weight(.bold)).foregroundStyle(.secondary)
                }
                Text(topic.title).font(.title.weight(.heavy))
                ForEach(Array(topic.blocks.enumerated()), id: \.offset) { _, b in block(b) }
                if let verbs = topic.verbs?.filter({ store.verbs[$0] != nil }), !verbs.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("PRACTISE IN THE CONJUGATOR").font(.caption.weight(.bold)).foregroundStyle(.secondary)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack {
                                ForEach(verbs, id: \.self) { v in
                                    NavigationLink(value: VerbRoute(infinitive: v)) { Text(v) }.buttonStyle(.bordered)
                                }
                            }
                        }
                    }
                }
                neighbours
            }
            .padding()
        }
        .inlineTitle()
    }

    @ViewBuilder private func block(_ b: GrammarBlock) -> some View {
        switch b.t {
        case "tip":
            VStack(alignment: .leading, spacing: 4) {
                Text("WATCH OUT").font(.caption.weight(.bold)).foregroundStyle(.orange)
                richText(b.text ?? "")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 10))
        case "ex":
            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array((b.examples ?? []).enumerated()), id: \.offset) { _, e in
                    if e.count == 2 {
                        HStack(alignment: .top, spacing: 10) {
                            SpeakButton(text: e[0].replacingOccurrences(of: "**", with: ""))
                            VStack(alignment: .leading, spacing: 2) {
                                richText(e[0]).fontWeight(.semibold)
                                richText(e[1]).font(.subheadline).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .card()
        case "table":
            ScrollView(.horizontal, showsIndicators: false) {
                Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 8) {
                    GridRow {
                        ForEach(Array((b.head ?? []).enumerated()), id: \.offset) { _, h in
                            richText(h).font(.caption.weight(.bold)).foregroundStyle(.secondary)
                        }
                    }
                    Divider()
                    ForEach(Array((b.rows ?? []).enumerated()), id: \.offset) { _, row in
                        GridRow {
                            ForEach(Array(row.enumerated()), id: \.offset) { k, cell in
                                richText(cell)
                                    .font(k == 0 ? .subheadline : .body)
                                    .foregroundStyle(k == 0 ? .secondary : .primary)
                                    .fixedSize()
                            }
                        }
                    }
                }
                .padding(12)
            }
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 12))
        default:
            richText(b.text ?? "").lineSpacing(3)
        }
    }

    @ViewBuilder private var neighbours: some View {
        let all = store.grammar
        if let i = all.firstIndex(of: topic) {
            HStack {
                if i > 0 {
                    NavigationLink(value: all[i - 1]) { Label(all[i - 1].title, systemImage: "chevron.left") }
                }
                Spacer()
                if i + 1 < all.count {
                    NavigationLink(value: all[i + 1]) {
                        HStack(spacing: 4) { Text(all[i + 1].title); Image(systemName: "chevron.right") }
                    }
                }
            }
            .font(.subheadline)
            .padding(.top, 8)
        }
    }
}
