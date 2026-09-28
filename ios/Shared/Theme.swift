import SwiftUI
import UIKit

// LearnPaper brand: warm "paper" surfaces, ink text, Iris violet for actions and Apricot as the
// friendly second colour (the two cards of the logo). Mint marks progress ("learned").
// Values mirror android/app/src/main/java/com/learnpaper/ui/theme/Theme.kt.

extension UIColor {
    /// Opaque sRGB colour from 0xRRGGBB.
    convenience init(rgb: UInt32, alpha: CGFloat = 1) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: alpha
        )
    }

    /// A colour that follows the light/dark appearance.
    convenience init(light: UIColor, dark: UIColor) {
        self.init(dynamicProvider: { traits in traits.userInterfaceStyle == .dark ? dark : light })
    }
}

extension Color {
    init(rgb: UInt32, opacity: Double = 1) {
        self.init(uiColor: UIColor(rgb: rgb, alpha: CGFloat(opacity)))
    }

    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor(light: UIColor(rgb: light), dark: UIColor(rgb: dark)))
    }

    init(light: UIColor, dark: UIColor) {
        self.init(uiColor: UIColor(light: light, dark: dark))
    }
}

/// Fixed brand colours (the same in light and dark mode).
enum Brand {
    static let iris = Color(rgb: 0x5A48E6)
    static let irisLight = Color(rgb: 0x7A6BFF)
    static let irisDeep = Color(rgb: 0x4A38D4)
    static let apricot = Color(rgb: 0xFF9A76)
    static let paper = Color(rgb: 0xFBF8F4)
    static let ink = Color(rgb: 0x1F1A2E)
    static let mint = Color(rgb: 0x2E9C74)
}

/// Semantic colours of the interface, light / dark (Material slots of the Android theme).
enum Theme {
    static let primary = Color(light: 0x5A48E6, dark: 0xB0A5FF)
    static let onPrimary = Color(light: 0xFFFFFF, dark: 0x1E1466)
    static let primaryContainer = Color(light: 0xE9E5FF, dark: 0x3A2DA6)
    static let onPrimaryContainer = Color(light: 0x22186B, dark: 0xE9E5FF)
    static let secondary = Color(light: 0xC4582F, dark: 0xFFB396)
    static let secondaryContainer = Color(light: 0xFFE3D6, dark: 0x6B2E17)
    static let onSecondaryContainer = Color(light: 0x4A1E0E, dark: 0xFFE3D6)
    static let tertiary = Color(light: 0x2E9C74, dark: 0x7FDDB7)

    static let background = Color(light: 0xFBF8F4, dark: 0x121018)
    static let onBackground = Color(light: 0x1F1A2E, dark: 0xF1ECF7)
    static let surface = background
    static let onSurface = onBackground
    static let surfaceVariant = Color(light: 0xF1ECE5, dark: 0x2A2634)
    static let onSurfaceVariant = Color(light: 0x6B6478, dark: 0xB9B1C7)
    static let surfaceContainerLowest = Color(light: 0xFFFFFF, dark: 0x0D0B12)
    static let surfaceContainerLow = Color(light: 0xFFFDFA, dark: 0x17141E)
    static let surfaceContainer = Color(light: 0xF6F2EC, dark: 0x1C1924)
    static let surfaceContainerHigh = Color(light: 0xF1ECE5, dark: 0x24202D)
    static let surfaceContainerHighest = Color(light: 0xEBE5DD, dark: 0x2D2937)
    static let inverseSurface = Color(light: 0x2E2A38, dark: 0xF1ECF7)
    static let inverseOnSurface = Color(light: 0xF5F1EC, dark: 0x2E2A38)
    static let outline = Color(light: 0xD5CDC3, dark: 0x4B4557)
    static let outlineVariant = Color(light: 0xE8E2DA, dark: 0x34303F)
    static let error = Color(light: 0xC8364E, dark: 0xFF8FA2)

    // Colours Material has no slot for.
    static let success = Color(light: 0x2E9C74, dark: 0x7FDDB7)
    static let successContainer = Color(light: 0xD5F3E6, dark: 0x11533C)
    static let onSuccessContainer = Color(light: 0x0D3B2A, dark: 0xD5F3E6)
    static let favorite = Color(light: 0xE5486A, dark: 0xFF7D98)
    static let streak = Color(light: 0xF07A3A, dark: 0xFFA36E)
    static let cardShadow = Color(light: UIColor(rgb: 0x1B1150, alpha: 0.2), dark: UIColor(rgb: 0x000000, alpha: 0.4))
    static let onLevel = Color(light: 0x1F1A2E, dark: 0xF1ECF7)

    private static let levelLight: [UInt32] = [0xD5F3E6, 0xD3EEF4, 0xDDE8FF, 0xE9E5FF, 0xFFE3D6, 0xFFDCE6]
    private static let levelDark: [UInt32] = [0x1C4A3A, 0x1B4550, 0x263A66, 0x362D78, 0x5E3020, 0x5C2438]

    /// Background tints for the CEFR level badges A1…C2.
    static let levels: [Color] = zip(levelLight, levelDark).map { Color(light: $0.0, dark: $0.1) }

    static func level(_ level: String) -> Color {
        let i = Levels.order.firstIndex(of: level) ?? 0
        return levels[min(max(i, 0), levels.count - 1)]
    }
}

/// Corner radii of the Android `BrandShapes`.
enum Radius {
    static let extraSmall: CGFloat = 8
    static let small: CGFloat = 12
    static let medium: CGFloat = 18
    static let large: CGFloat = 26
    static let extraLarge: CGFloat = 34
}

/// One step of the type scale: Onest for the brand voice, Inter for transcriptions.
struct TypeStyle {
    var size: CGFloat
    var line: CGFloat
    var weight: Int
    var tracking: CGFloat = 0
    var face: FontFace = .onest

    var font: Font { Font.brand(face, size: size, weight: weight) }

    /// Extra space between lines so the line height matches the Android style.
    var lineSpacing: CGFloat {
        max(0, line - Fonts.ui(face, size: size, weight: weight).lineHeight)
    }

    func resized(_ newSize: CGFloat) -> TypeStyle {
        var s = self
        s.line = line * newSize / size
        s.size = newSize
        return s
    }

    func weighted(_ newWeight: Int) -> TypeStyle {
        var s = self
        s.weight = newWeight
        return s
    }
}

/// The Android typography scale (Theme.kt `typography`).
enum TypeScale {
    static let displayLarge = TypeStyle(size: 54, line: 60, weight: 800, tracking: -1.2)
    static let displayMedium = TypeStyle(size: 44, line: 50, weight: 800, tracking: -0.9)
    static let displaySmall = TypeStyle(size: 36, line: 42, weight: 700, tracking: -0.6)
    static let headlineLarge = TypeStyle(size: 32, line: 38, weight: 700, tracking: -0.5)
    static let headlineMedium = TypeStyle(size: 28, line: 34, weight: 700, tracking: -0.4)
    static let headlineSmall = TypeStyle(size: 24, line: 30, weight: 700, tracking: -0.2)
    static let titleLarge = TypeStyle(size: 21, line: 27, weight: 700, tracking: -0.1)
    static let titleMedium = TypeStyle(size: 17, line: 23, weight: 600)
    static let titleSmall = TypeStyle(size: 15, line: 20, weight: 600)
    static let bodyLarge = TypeStyle(size: 16, line: 24, weight: 400)
    static let bodyMedium = TypeStyle(size: 14, line: 20, weight: 400)
    static let bodySmall = TypeStyle(size: 13, line: 18, weight: 400)
    static let labelLarge = TypeStyle(size: 15, line: 20, weight: 600)
    static let labelMedium = TypeStyle(size: 13, line: 18, weight: 500)
    static let labelSmall = TypeStyle(size: 11, line: 16, weight: 600, tracking: 0.4)

    /// Transcriptions (IPA and Latin transliteration): Inter, which carries every symbol they use.
    static let transcription = TypeStyle(size: 14, line: 20, weight: 400, face: .inter)
    /// Small uppercase label above a group of controls.
    static let overline = TypeStyle(size: 11, line: 16, weight: 600, tracking: 1.1)
}

extension View {
    /// Applies a step of the brand type scale (font, tracking and line height).
    func textStyle(_ style: TypeStyle) -> some View {
        font(style.font)
            .tracking(style.tracking)
            .lineSpacing(style.lineSpacing)
    }
}
