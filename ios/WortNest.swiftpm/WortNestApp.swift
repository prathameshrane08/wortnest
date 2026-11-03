import SwiftUI

@main
struct WortNestApp: App {
    @StateObject private var store = Store()
    @AppStorage("dark") private var dark = false   // white by default; the moon button switches

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .tint(Palette.accent)
                .preferredColorScheme(dark ? .dark : .light)
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var store: Store
    @State private var tourOpen = false
    @State private var levelOpen = false

    var body: some View {
        Group {
            if store.loaded {
                TabView(selection: $store.tab) {
                    DictionaryView(openTour: { tourOpen = true }, openLevel: { levelOpen = true })
                        .tabItem { Label("Dictionary", systemImage: "book.closed") }
                        .tag(0)
                    ArticleView()
                        .tabItem { Label("Articles", systemImage: "circle.grid.3x3") }
                        .tag(1)
                    VerbsView()
                        .tabItem { Label("Verbs", systemImage: "tablecells") }
                        .tag(2)
                    GrammarView()
                        .tabItem { Label("Grammar", systemImage: "text.book.closed") }
                        .tag(3)
                    LearnView(openLevel: { levelOpen = true })
                        .tabItem { Label("Practice", systemImage: "star") }
                        .tag(4)
                }
                .sheet(isPresented: $tourOpen) {
                    TourView { added in
                        tourOpen = false
                        if added { store.tab = 4 }
                    }
                    .interactiveDismissDisabled()
                }
                .sheet(isPresented: $levelOpen) {
                    LevelView { goLearn in
                        levelOpen = false
                        if goLearn { store.tab = 4 }
                    }
                }
                .onAppear {
                    if let t = DebugLaunch.string("demoTab").flatMap(Int.init) { store.tab = t }
                    if DebugLaunch.string("demoLevelTest") != nil { levelOpen = true }
                    if !store.toured && DebugLaunch.string("demoNoTour") == nil { tourOpen = true }
                }
            } else if let error = store.loadError {
                ContentUnavailableView("Dictionary unavailable", systemImage: "exclamationmark.triangle", description: Text(error))
            } else {
                VStack(spacing: 12) {
                    ProgressView()
                    Text("Loading 40,000 words…").foregroundStyle(Palette.muted)
                }
            }
        }
        .task { await store.load() }
    }
}
