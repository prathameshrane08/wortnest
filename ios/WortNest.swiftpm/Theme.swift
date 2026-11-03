import SwiftUI

// Clean and calm: white space, one indigo accent, slate text. The same palette as the website and Android.
extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }

    /// One colour for light mode, another for dark.
    static func adaptive(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(Color(hex: dark)) : UIColor(Color(hex: light)) })
    }
}

enum Palette {
    static let accent = Color.adaptive(0x6366F1, 0x818CF8)
    static let accentSoft = Color.adaptive(0xEEF2FF, 0x1E1F4A)
    static let accentText = Color.adaptive(0x4F46E5, 0xA5B4FC)
    static let background = Color.adaptive(0xFFFFFF, 0x0B1120)
    static let surface = Color.adaptive(0xF8FAFC, 0x111A2E)
    static let surfaceHigh = Color.adaptive(0xF1F5F9, 0x1A2438)
    static let text = Color.adaptive(0x0F172A, 0xF1F5F9)
    static let muted = Color.adaptive(0x64748B, 0x94A3B8)
    static let line = Color.adaptive(0xE2E8F0, 0x243049)
    static let ok = Color.adaptive(0x059669, 0x34D399)
    static let bad = Color.adaptive(0xE11D48, 0xFB7185)
    static let irregular = Color.adaptive(0xC2410C, 0xFDBA74)
    static let amber = Color(hex: 0xF59E0B)
    static let pink = Color(hex: 0xEC4899)

    /// The word-of-the-day card stays dark in both themes.
    static let hero = Color.adaptive(0x0F172A, 0x1E1B4B)
    static let heroMuted = Color(hex: 0xCBD5E1)
    static let heroLabel = Color(hex: 0xA5B4FC)
}

/// A rounded box with a hairline border, the basic card of the app.
struct Outlined: ViewModifier {
    var radius: CGFloat = 16
    var fill: Color = Palette.background
    func body(content: Content) -> some View {
        content
            .background(fill, in: RoundedRectangle(cornerRadius: radius))
            .overlay(RoundedRectangle(cornerRadius: radius).stroke(Palette.line, lineWidth: 1))
    }
}

extension View {
    func outlined(radius: CGFloat = 16, fill: Color = Palette.background) -> some View { modifier(Outlined(radius: radius, fill: fill)) }
}

/// A row of options where one is chosen, like a segmented control but with labels that shrink instead of being cut off.
struct Segments: View {
    let labels: [String]
    let selected: Int
    let onSelect: (Int) -> Void

    var body: some View {
        HStack(spacing: 2) {
            ForEach(labels.indices, id: \.self) { i in
                Button { onSelect(i) } label: {
                    Text(labels[i])
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1).minimumScaleFactor(0.6)
                        .foregroundStyle(i == selected ? Palette.text : Palette.muted)
                        .padding(.horizontal, 12).padding(.vertical, 7)
                        .background(i == selected ? Palette.background : .clear, in: RoundedRectangle(cornerRadius: 9))
                        .shadow(color: i == selected ? .black.opacity(0.06) : .clear, radius: 2, y: 1)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(Palette.surfaceHigh, in: RoundedRectangle(cornerRadius: 12))
    }
}

/// The filled indigo button used for the main action on a screen.
struct PrimaryButton: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 18).padding(.vertical, 12)
            .background(Palette.accent.opacity(configuration.isPressed ? 0.8 : 1), in: RoundedRectangle(cornerRadius: 12))
    }
}

/// The outlined button for the other choices next to a primary one.
struct SecondaryButton: ButtonStyle {
    var color: Color = Palette.text
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 18).padding(.vertical, 12)
            .background(configuration.isPressed ? Palette.surfaceHigh : Palette.background, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.line, lineWidth: 1))
    }
}
