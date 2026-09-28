import UIKit

/// A card palette: background, text colours, tile and accent. Same values as the Android `Palettes`
/// (ten pastels and two dark ones); dark palettes carry light text colours.
struct Palette: Identifiable, Equatable {
    let id: String
    let nameKey: String
    let bgHex: UInt32
    let textHex: UInt32
    let mutedHex: UInt32
    let tileHex: UInt32
    let accentHex: UInt32

    init(_ id: String, _ bg: UInt32, _ text: UInt32, _ muted: UInt32, _ tile: UInt32, _ accent: UInt32) {
        self.id = id
        nameKey = "palette_\(id)"
        bgHex = bg
        textHex = text
        mutedHex = muted
        tileHex = tile
        accentHex = accent
    }

    var bg: UIColor { UIColor(rgb: bgHex) }
    var text: UIColor { UIColor(rgb: textHex) }
    var muted: UIColor { UIColor(rgb: mutedHex) }
    var tile: UIColor { UIColor(rgb: tileHex) }
    var accent: UIColor { UIColor(rgb: accentHex) }

    /// Dark backgrounds get light text (Midnight, Pine).
    var isDark: Bool {
        let r = Double((bgHex >> 16) & 0xFF)
        let g = Double((bgHex >> 8) & 0xFF)
        let b = Double(bgHex & 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b < 128
    }

    static func == (a: Palette, b: Palette) -> Bool { a.id == b.id }

    /// Linear blend of two opaque colours; t = 0 gives `a`.
    static func mix(_ a: UInt32, _ b: UInt32, _ t: CGFloat) -> UIColor {
        func channel(_ c: UInt32, _ shift: UInt32) -> CGFloat { CGFloat((c >> shift) & 0xFF) / 255 }
        func lerp(_ x: CGFloat, _ y: CGFloat) -> CGFloat { min(1, max(0, x + (y - x) * t)) }
        return UIColor(
            red: lerp(channel(a, 16), channel(b, 16)),
            green: lerp(channel(a, 8), channel(b, 8)),
            blue: lerp(channel(a, 0), channel(b, 0)),
            alpha: 1
        )
    }
}

enum Palettes {
    static let all: [Palette] = [
        Palette("peach", 0xFFE1CF, 0x4A2A1C, 0x8C5A45, 0xFFCDB2, 0xE8845C),
        Palette("mint", 0xD6F2E6, 0x173E31, 0x4F7D6C, 0xBDE8D5, 0x4CAF8A),
        Palette("lavender", 0xE6E0F8, 0x2E2452, 0x6B5E96, 0xD5CCF2, 0x8B76D6),
        Palette("sky", 0xD8ECFA, 0x163A57, 0x4D7797, 0xC2E0F6, 0x5AA5DE),
        Palette("sand", 0xF4E8D0, 0x4A3A1D, 0x8A7450, 0xEBD9B4, 0xC9A35C),
        Palette("rose", 0xFADCE3, 0x4E1F2E, 0x93586B, 0xF5C7D2, 0xDD6E8B),
        Palette("sage", 0xE1EBD9, 0x2C3A22, 0x647557, 0xD0E0C2, 0x7FA36A),
        Palette("butter", 0xFFF1C2, 0x4A3E12, 0x8C7A3A, 0xFFE79E, 0xE3B939),
        Palette("lilac", 0xF1DDF3, 0x43244A, 0x855A8E, 0xE8C9EB, 0xB972C2),
        Palette("powder", 0xE3E8EF, 0x23303F, 0x5A6B80, 0xD3DAE5, 0x6F87A6),
        Palette("midnight", 0x17142A, 0xF2EEFF, 0xA9A2C9, 0x27224A, 0xA99CFF),
        Palette("pine", 0x10231C, 0xE9F5EE, 0x9DBCAC, 0x1B3A2E, 0x7FDDB7),
    ]

    static func byId(_ id: String) -> Palette { all.first { $0.id == id } ?? all[0] }

    /// The palette for a given change: fixed, or rotating through `all` by `index`.
    static func forSettings(_ settings: Settings, index: Int) -> Palette {
        settings.rotatePalette ? all[((index % all.count) + all.count) % all.count] : byId(settings.paletteId)
    }
}
