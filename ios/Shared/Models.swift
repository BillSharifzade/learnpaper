import Foundation

/// The three languages of the app. `code` is the key used in the content pack and `label` the short
/// tag drawn on the card. Tajik is "TJ" everywhere the user can see it; the system locale
/// identifier stays the ISO code `tg`.
enum Lang: String, Codable, CaseIterable, Identifiable {
    case en, ru, tj

    var id: String { rawValue }
    var code: String { rawValue }
    var label: String { rawValue.uppercased() }
    /// Locale used for speech and for picking the UI translation.
    var localeIdentifier: String {
        switch self {
        case .en: return "en-GB"
        case .ru: return "ru-RU"
        case .tj: return "tg-TJ"
        }
    }
}

/// A word in one language: display text plus transcription (IPA for English, Latin transliteration otherwise).
struct LangEntry: Codable, Hashable {
    var text: String
    var tr: String = ""

    enum CodingKeys: String, CodingKey { case text, tr }

    init(text: String, tr: String = "") {
        self.text = text
        self.tr = tr
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        text = try c.decode(String.self, forKey: .text)
        tr = try c.decodeIfPresent(String.self, forKey: .tr) ?? ""
    }
}

struct Example: Codable, Hashable {
    var en: String = ""
    var ru: String = ""
    var tj: String = ""

    func of(_ lang: Lang) -> String {
        switch lang {
        case .en: return en
        case .ru: return ru
        case .tj: return tj
        }
    }
}

struct Word: Codable, Hashable, Identifiable {
    var id: String
    var level: String
    var pos: String = ""
    var tags: [String] = []
    var en: LangEntry
    var ru: LangEntry
    var tj: LangEntry
    var example: Example = Example()
    var image: String?

    func entry(_ lang: Lang) -> LangEntry {
        switch lang {
        case .en: return en
        case .ru: return ru
        case .tj: return tj
        }
    }

    enum CodingKeys: String, CodingKey { case id, level, pos, tags, en, ru, tj, example, image }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        level = try c.decode(String.self, forKey: .level)
        pos = try c.decodeIfPresent(String.self, forKey: .pos) ?? ""
        tags = try c.decodeIfPresent([String].self, forKey: .tags) ?? []
        en = try c.decode(LangEntry.self, forKey: .en)
        ru = try c.decode(LangEntry.self, forKey: .ru)
        tj = try c.decode(LangEntry.self, forKey: .tj)
        example = try c.decodeIfPresent(Example.self, forKey: .example) ?? Example()
        image = try c.decodeIfPresent(String.self, forKey: .image)
    }

    init(id: String, level: String, pos: String = "", tags: [String] = [], en: LangEntry, ru: LangEntry, tj: LangEntry, example: Example = Example(), image: String? = nil) {
        self.id = id
        self.level = level
        self.pos = pos
        self.tags = tags
        self.en = en
        self.ru = ru
        self.tj = tj
        self.example = example
        self.image = image
    }
}

struct ContentPack: Codable {
    var version: Int = 1
    var words: [Word] = []
}

enum Levels {
    static let order = ["A1", "A2", "B1", "B2", "C1", "C2"]

    static func sorted(_ levels: some Collection<String>) -> [String] {
        Array(Set(levels)).sorted { (order.firstIndex(of: $0) ?? .max) < (order.firstIndex(of: $1) ?? .max) }
    }
}
