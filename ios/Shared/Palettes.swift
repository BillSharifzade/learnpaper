import UIKit

/// A pastel background with matching text colours. Same values as the Android `Palettes`.
struct Palette: Identifiable, Equatable {
    let id: String
    let nameKey: String
    let bg: UIColor
    let text: UIColor
    let muted: UIColor
    let tile: UIColor
    let accent: UIColor

    init(_ id: String, _ bg: UInt32, _ text: UInt32, _ muted: UInt32, _ tile: UInt32, _ accent: UInt32) {
        self.id = id
        nameKey = "palette.\(id)"
        self.bg = UIColor(rgb: bg)
        self.text = UIColor(rgb: text)
        self.muted = UIColor(rgb: muted)
        self.tile = UIColor(rgb: tile)
        self.accent = UIColor(rgb: accent)
    }

    static func == (a: Palette, b: Palette) -> Bool { a.id == b.id }
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
    ]

    static func byId(_ id: String) -> Palette { all.first { $0.id == id } ?? all[0] }

    /// The palette for a given change: fixed, or rotating through `all` by `index`.
    static func forSettings(_ settings: Settings, index: Int) -> Palette {
        settings.rotatePalette ? all[((index % all.count) + all.count) % all.count] : byId(settings.paletteId)
    }
}

extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}
