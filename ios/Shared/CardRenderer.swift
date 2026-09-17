import UIKit

/// Draws a word card into an image of the given pixel size. Port of the Android `CardRenderer`:
/// all dimensions are expressed for a 1080 px wide canvas and scaled by `width / 1080`; the
/// content is a vertical stack of blocks centred inside a band that depends on the layout preset,
/// shrunk step by step if it does not fit.
final class CardRenderer {
    static let shared = CardRenderer()
    private let margin: CGFloat = 96

    func render(word: Word, settings: Settings, palette: Palette, width: CGFloat, height: CGFloat) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let size = CGSize(width: width, height: height)
        return UIGraphicsImageRenderer(size: size, format: format).image { rc in
            let ctx = rc.cgContext
            let s = width / 1080
            drawBackground(ctx, palette: palette, w: width, h: height, s: s)

            let (bandTop, bandBottom): (CGFloat, CGFloat)
            switch settings.layout {
            case .lock: (bandTop, bandBottom) = (0.30, 0.90)
            case .home: (bandTop, bandBottom) = (0.16, 0.88)
            case .compact: (bandTop, bandBottom) = (0.58, 0.92)
            }
            let top = bandTop * height
            let bottom = bandBottom * height
            let maxWidth = width - 2 * margin * s
            let image = settings.layout == .compact ? nil : ContentStore.shared.image(for: word)

            var scale: CGFloat = 1
            var blocks = buildBlocks(word: word, settings: settings, palette: palette, image: image, s: s, maxWidth: maxWidth)
            var total = blocks.reduce(0) { $0 + $1.height }
            while total > bottom - top && scale > 0.5 {
                scale *= 0.92
                blocks = buildBlocks(word: word, settings: settings, palette: palette, image: image, s: s * scale, maxWidth: maxWidth)
                total = blocks.reduce(0) { $0 + $1.height }
            }

            var y = top + ((bottom - top) - total) / 2
            let cx = width / 2
            for block in blocks {
                block.draw(cx: cx, y: y, ctx: ctx)
                y += block.height
            }
        }
    }

    private func drawBackground(_ ctx: CGContext, palette: Palette, w: CGFloat, h: CGFloat, s: CGFloat) {
        ctx.setFillColor(palette.bg.cgColor)
        ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
        // Soft blobs, like the blurred circles on Android.
        for (center, radius) in [(CGPoint(x: w * 0.88, y: h * 0.10), 300 * s), (CGPoint(x: w * 0.10, y: h * 0.96), 340 * s)] {
            let colors = [palette.tile.cgColor, palette.tile.withAlphaComponent(0).cgColor] as CFArray
            guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0.35, 1]) else { continue }
            ctx.drawRadialGradient(gradient, startCenter: center, startRadius: 0, endCenter: center, endRadius: radius * 1.5, options: [])
        }
    }

    private func buildBlocks(word: Word, settings: Settings, palette: Palette, image: UIImage?, s: CGFloat, maxWidth: CGFloat) -> [Block] {
        var blocks: [Block] = []
        let headline = word.entry(settings.headline)
        let translations = settings.translations
        let compact = settings.layout == .compact

        if let image {
            blocks.append(ImageTile(image: image, size: 360 * s, radius: 64 * s, tileColor: palette.tile))
            blocks.append(Spacer(56 * s))
        }

        let headlineSize = fitSize(headline.text, start: 88 * s, weight: 700, maxWidth: maxWidth, minSize: 40 * s)
        blocks.append(TextLine(headline.text, attrs(headlineSize, 700, palette.text)))
        if settings.showTranscriptions, !headline.tr.isEmpty {
            blocks.append(Spacer(10 * s))
            blocks.append(TextLine(Self.formatTr(settings.headline, headline.tr), attrs(34 * s, 500, palette.muted)))
        }
        blocks.append(Spacer(20 * s))
        let tag = [word.level, PosNames.localized(word.pos)].filter { !$0.isEmpty }.joined(separator: "  ·  ").uppercased()
        blocks.append(Tag(tag, attrs(22 * s, 600, palette.muted, kern: 0.12 * 22 * s), bg: palette.tile, s: s))

        if !translations.isEmpty {
            blocks.append(Spacer((compact ? 44 : 56) * s))
            for (i, lang) in translations.enumerated() {
                if i > 0 { blocks.append(Spacer(22 * s)) }
                let e = word.entry(lang)
                blocks.append(WordLine(
                    label: lang.label,
                    labelAttrs: attrs(22 * s, 700, palette.muted, kern: 0.1 * 22 * s),
                    labelBg: palette.tile,
                    text: e.text,
                    textAttrs: attrs(fitSize(e.text, start: 46 * s, weight: 600, maxWidth: maxWidth * 0.7, minSize: 28 * s), 600, palette.text),
                    tr: settings.showTranscriptions && !e.tr.isEmpty ? Self.formatTr(lang, e.tr) : nil,
                    trAttrs: attrs(30 * s, 500, palette.muted),
                    gap: 18 * s,
                    maxWidth: maxWidth,
                    s: s
                ))
            }
        }

        if settings.showExamples, !compact {
            let main = word.example.of(settings.headline)
            if !main.isEmpty {
                blocks.append(Spacer(48 * s))
                blocks.append(Divider(width: 300 * s, thickness: 2 * s, color: palette.muted.withAlphaComponent(0.35)))
                blocks.append(Spacer(40 * s))
                blocks.append(Paragraph(main, attrs(34 * s, 500, palette.text), width: maxWidth, lineSpacing: 1.2))
                for lang in translations {
                    let t = word.example.of(lang)
                    if t.isEmpty { continue }
                    blocks.append(Spacer(16 * s))
                    blocks.append(Paragraph(t, attrs(28 * s, 400, palette.muted), width: maxWidth, lineSpacing: 1.2))
                }
            }
        }
        return blocks
    }

    private func attrs(_ size: CGFloat, _ weight: Int, _ color: UIColor, kern: CGFloat = 0) -> [NSAttributedString.Key: Any] {
        var a: [NSAttributedString.Key: Any] = [.font: Fonts.inter(size: size, weight: weight), .foregroundColor: color]
        if kern != 0 { a[.kern] = kern }
        return a
    }

    private func fitSize(_ text: String, start: CGFloat, weight: Int, maxWidth: CGFloat, minSize: CGFloat) -> CGFloat {
        var size = start
        while size > minSize, (text as NSString).size(withAttributes: [.font: Fonts.inter(size: size, weight: weight)]).width > maxWidth {
            size *= 0.94
        }
        return size
    }

    static func formatTr(_ lang: Lang, _ tr: String) -> String { lang == .en ? "/\(tr)/" : "[\(tr)]" }

    // MARK: - blocks

    private struct Metrics {
        let ascent: CGFloat
        let descent: CGFloat
        var height: CGFloat { ascent + descent }
        init(_ attrs: [NSAttributedString.Key: Any]) {
            let font = (attrs[.font] as? UIFont) ?? UIFont.systemFont(ofSize: 16)
            ascent = font.ascender
            descent = -font.descender
        }
    }

    private static func measure(_ text: String, _ attrs: [NSAttributedString.Key: Any]) -> CGFloat {
        (text as NSString).size(withAttributes: attrs).width
    }

    private static func drawText(_ text: String, _ attrs: [NSAttributedString.Key: Any], x: CGFloat, baseline: CGFloat, ascent: CGFloat) {
        (text as NSString).draw(at: CGPoint(x: x, y: baseline - ascent), withAttributes: attrs)
    }

    private struct Spacer: Block {
        let height: CGFloat
        init(_ h: CGFloat) { height = h }
        func draw(cx: CGFloat, y: CGFloat, ctx: CGContext) {}
    }

    private struct TextLine: Block {
        let text: String
        let attrs: [NSAttributedString.Key: Any]
        let m: Metrics
        let height: CGFloat
        init(_ text: String, _ attrs: [NSAttributedString.Key: Any]) {
            self.text = text
            self.attrs = attrs
            m = Metrics(attrs)
            height = m.height
        }
        func draw(cx: CGFloat, y: CGFloat, ctx: CGContext) {
            CardRenderer.drawText(text, attrs, x: cx - CardRenderer.measure(text, attrs) / 2, baseline: y + m.ascent, ascent: m.ascent)
        }
    }

    /// Small uppercase pill, e.g. "A1 · NOUN".
    private struct Tag: Block {
        let text: String
        let attrs: [NSAttributedString.Key: Any]
        let bg: UIColor
        let padX: CGFloat
        let padY: CGFloat
        let m: Metrics
        let height: CGFloat
        init(_ text: String, _ attrs: [NSAttributedString.Key: Any], bg: UIColor, s: CGFloat) {
            self.text = text
            self.attrs = attrs
            self.bg = bg
            padX = 22 * s
            padY = 10 * s
            m = Metrics(attrs)
            height = m.height + 2 * padY
        }
        func draw(cx: CGFloat, y: CGFloat, ctx: CGContext) {
            let textW = CardRenderer.measure(text, attrs)
            let w = textW + 2 * padX
            ctx.setFillColor(bg.cgColor)
            ctx.addPath(UIBezierPath(roundedRect: CGRect(x: cx - w / 2, y: y, width: w, height: height), cornerRadius: height / 2).cgPath)
            ctx.fillPath()
            CardRenderer.drawText(text, attrs, x: cx - textW / 2, baseline: y + padY + m.ascent, ascent: m.ascent)
        }
    }

    /// "[RU] зонт [zont]" on one line, with the transcription wrapping below when it does not fit.
    private struct WordLine: Block {
        let label: String
        let labelAttrs: [NSAttributedString.Key: Any]
        let labelBg: UIColor
        let text: String
        let textAttrs: [NSAttributedString.Key: Any]
        let tr: String?
        let trAttrs: [NSAttributedString.Key: Any]
        let gap: CGFloat
        let labelPadX: CGFloat
        let labelPadY: CGFloat
        let labelM: Metrics
        let labelH: CGFloat
        let labelW: CGFloat
        let textW: CGFloat
        let trW: CGFloat
        let oneLine: Bool
        let ascent: CGFloat
        let descent: CGFloat
        let line1H: CGFloat
        let line2Gap: CGFloat
        let line2H: CGFloat
        let trM: Metrics
        let height: CGFloat

        init(label: String, labelAttrs: [NSAttributedString.Key: Any], labelBg: UIColor, text: String, textAttrs: [NSAttributedString.Key: Any],
             tr: String?, trAttrs: [NSAttributedString.Key: Any], gap: CGFloat, maxWidth: CGFloat, s: CGFloat) {
            self.label = label
            self.labelAttrs = labelAttrs
            self.labelBg = labelBg
            self.text = text
            self.textAttrs = textAttrs
            self.tr = tr
            self.trAttrs = trAttrs
            self.gap = gap
            labelPadX = 14 * s
            labelPadY = 8 * s
            labelM = Metrics(labelAttrs)
            labelH = labelM.height + 2 * labelPadY
            labelW = CardRenderer.measure(label, labelAttrs) + 2 * labelPadX
            textW = CardRenderer.measure(text, textAttrs)
            trW = tr.map { CardRenderer.measure($0, trAttrs) } ?? 0
            oneLine = tr == nil || labelW + gap + textW + gap + trW <= maxWidth
            let textM = Metrics(textAttrs)
            trM = Metrics(trAttrs)
            ascent = max(textM.ascent, oneLine && tr != nil ? trM.ascent : 0)
            descent = max(textM.descent, oneLine && tr != nil ? trM.descent : 0)
            line1H = max(ascent + descent, labelH)
            line2Gap = 6 * s
            line2H = (!oneLine && tr != nil) ? trM.height + line2Gap : 0
            height = line1H + line2H
        }

        func draw(cx: CGFloat, y: CGFloat, ctx: CGContext) {
            let total = labelW + gap + textW + ((oneLine && tr != nil) ? gap + trW : 0)
            var x = cx - total / 2
            let pillTop = y + (line1H - labelH) / 2
            ctx.setFillColor(labelBg.cgColor)
            ctx.addPath(UIBezierPath(roundedRect: CGRect(x: x, y: pillTop, width: labelW, height: labelH), cornerRadius: labelH / 2).cgPath)
            ctx.fillPath()
            CardRenderer.drawText(label, labelAttrs, x: x + labelPadX, baseline: pillTop + labelPadY + labelM.ascent, ascent: labelM.ascent)
            x += labelW + gap
            let baseline = y + (line1H - (ascent + descent)) / 2 + ascent
            CardRenderer.drawText(text, textAttrs, x: x, baseline: baseline, ascent: Metrics(textAttrs).ascent)
            if let tr {
                if oneLine {
                    CardRenderer.drawText(tr, trAttrs, x: x + textW + gap, baseline: baseline, ascent: trM.ascent)
                } else {
                    CardRenderer.drawText(tr, trAttrs, x: cx - trW / 2, baseline: y + line1H + line2Gap + trM.ascent, ascent: trM.ascent)
                }
            }
        }
    }

    private struct Paragraph: Block {
        let attributed: NSAttributedString
        let width: CGFloat
        let height: CGFloat
        init(_ text: String, _ attrs: [NSAttributedString.Key: Any], width: CGFloat, lineSpacing: CGFloat) {
            let style = NSMutableParagraphStyle()
            style.alignment = .center
            style.lineHeightMultiple = lineSpacing
            style.lineBreakMode = .byTruncatingTail
            var a = attrs
            a[.paragraphStyle] = style
            attributed = NSAttributedString(string: text, attributes: a)
            self.width = width
            let font = (attrs[.font] as? UIFont) ?? UIFont.systemFont(ofSize: 16)
            let maxH = ceil(font.lineHeight * lineSpacing * 3)
            let rect = attributed.boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
            height = min(ceil(rect.height), maxH)
        }
        func draw(cx: CGFloat, y: CGFloat, ctx: CGContext) {
            attributed.draw(with: CGRect(x: cx - width / 2, y: y, width: width, height: height), options: [.usesLineFragmentOrigin, .usesFontLeading, .truncatesLastVisibleLine], context: nil)
        }
    }

    private struct Divider: Block {
        let width: CGFloat
        let height: CGFloat
        let color: UIColor
        init(width: CGFloat, thickness: CGFloat, color: UIColor) {
            self.width = width
            height = thickness
            self.color = color
        }
        func draw(cx: CGFloat, y: CGFloat, ctx: CGContext) {
            ctx.setFillColor(color.cgColor)
            ctx.fill(CGRect(x: cx - width / 2, y: y, width: width, height: height))
        }
    }

    private struct ImageTile: Block {
        let image: UIImage
        let size: CGFloat
        let radius: CGFloat
        let tileColor: UIColor
        var height: CGFloat { size }
        func draw(cx: CGFloat, y: CGFloat, ctx: CGContext) {
            ctx.setFillColor(tileColor.cgColor)
            ctx.addPath(UIBezierPath(roundedRect: CGRect(x: cx - size / 2, y: y, width: size, height: size), cornerRadius: radius).cgPath)
            ctx.fillPath()
            let inner = size * 0.64
            image.draw(in: CGRect(x: cx - inner / 2, y: y + (size - inner) / 2, width: inner, height: inner))
        }
    }
}

private protocol Block {
    var height: CGFloat { get }
    func draw(cx: CGFloat, y: CGFloat, ctx: CGContext)
}

/// Part-of-speech keys from the content, localised through the string catalog.
enum PosNames {
    static let known = ["noun", "verb", "adjective", "adverb", "interjection", "phrase", "pronoun", "preposition", "numeral", "conjunction"]

    static func localized(_ pos: String) -> String {
        known.contains(pos) ? L10n.t("pos.\(pos)") : pos
    }
}
