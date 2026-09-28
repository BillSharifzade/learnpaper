import Foundation

/// Search over all three languages at once. Port of android/.../domain/WordSearch.kt.
///
/// Text is folded so that people can type without the special letters: case, stress marks and
/// diacritics are ignored, ё = е, and the Tajik letters ғ ӣ қ ӯ ҳ ҷ match their Russian look-alikes
/// (г и к у х ч). Latin transliterations are indexed too, so "salom" finds "салом".
struct WordSearch {
    private let entries: [(word: Word, keys: [String])]

    init(_ words: [Word]) {
        entries = words.map { w -> (word: Word, keys: [String]) in
            let keys = [w.en.text, w.ru.text, w.tj.text, w.ru.tr, w.tj.tr].map { WordSearch.fold($0) }.filter { !$0.isEmpty }
            return (word: w, keys: keys)
        }
    }

    /// Words matching `query`, best first: exact, then prefix, then word-prefix, then substring;
    /// ties by level, then by the length of the English word.
    func search(_ query: String, limit: Int = Int.max) -> [Word] {
        let q = WordSearch.fold(query).trimmingCharacters(in: .whitespaces)
        if q.isEmpty { return [] }
        var hits: [(word: Word, rank: Int, position: Int)] = []
        for (i, entry) in entries.enumerated() {
            if let r = WordSearch.rank(entry.keys, q) { hits.append((entry.word, r, i)) }
        }
        hits.sort { a, b in
            if a.rank != b.rank { return a.rank < b.rank }
            let la = WordSearch.levelIndex(a.word.level), lb = WordSearch.levelIndex(b.word.level)
            if la != lb { return la < lb }
            let na = a.word.en.text.count, nb = b.word.en.text.count
            if na != nb { return na < nb }
            return a.position < b.position
        }
        return hits.prefix(limit).map { $0.word }
    }

    private static func rank(_ keys: [String], _ q: String) -> Int? {
        var best: Int?
        for k in keys {
            let r: Int?
            if k == q {
                r = 0
            } else if k.hasPrefix(q) {
                r = 1
            } else if k.split(whereSeparator: { $0 == " " || $0 == "-" }).contains(where: { $0.hasPrefix(q) }) {
                r = 2
            } else if k.contains(q) {
                r = 3
            } else {
                r = nil
            }
            if let r, best == nil || r < best! { best = r }
        }
        return best
    }

    private static let tajikFold: [Character: Character] = [
        "ғ": "г", "ӣ": "и", "қ": "к", "ӯ": "у", "ҳ": "х", "ҷ": "ч", "ё": "е",
    ]

    static func fold(_ text: String) -> String {
        let mapped = String(text.lowercased().map { tajikFold[$0] ?? $0 })
        // Strip combining marks: the stress accent, and the macrons and acutes of the transliterations.
        var scalars = String.UnicodeScalarView()
        for scalar in mapped.decomposedStringWithCanonicalMapping.unicodeScalars
        where scalar.properties.generalCategory != .nonspacingMark {
            scalars.append(scalar)
        }
        return String(scalars)
            .replacingOccurrences(of: "ʼ", with: " ")
            .replacingOccurrences(of: "'", with: " ")
    }

    static func levelIndex(_ level: String) -> Int { Levels.order.firstIndex(of: level) ?? 99 }

    /// Library order: by level, then alphabetically in the headline language.
    static func sorted(_ words: [Word], headline: Lang) -> [Word] {
        words
            .map { (word: $0, level: levelIndex($0.level), key: fold($0.entry(headline).text)) }
            .sorted { a, b in a.level != b.level ? a.level < b.level : a.key < b.key }
            .map { $0.word }
    }
}
