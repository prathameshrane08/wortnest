import SwiftUI

@main
struct WortNestApp: App {
    @StateObject private var store = Store()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .tint(.orange)
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var store: Store
    @AppStorage("tab") private var tab = 0

    var body: some View {
        Group {
            if store.loaded {
                TabView(selection: $tab) {
                    DictionaryView()
                        .tabItem { Label("Wörterbuch", systemImage: "book.closed") }
                        .tag(0)
                    ArticleView()
                        .tabItem { Label("Der Die Das", systemImage: "circle.grid.3x3") }
                        .tag(1)
                    VerbsView()
                        .tabItem { Label("Verben", systemImage: "tablecells") }
                        .tag(2)
                    GrammarView()
                        .tabItem { Label("Grammatik", systemImage: "text.book.closed") }
                        .tag(4)
                    LearnView()
                        .tabItem { Label("Üben", systemImage: "star") }
                        .tag(3)
                }
            } else if let error = store.loadError {
                ContentUnavailableView("Dictionary unavailable", systemImage: "exclamationmark.triangle", description: Text(error))
            } else {
                VStack(spacing: 12) {
                    ProgressView()
                    Text("Loading 40,000 words…").foregroundStyle(.secondary)
                }
            }
        }
        .task { await store.load() }
    }
}
