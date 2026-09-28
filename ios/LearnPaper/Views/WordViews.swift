import SwiftUI

// Word presentation shared by Today and the Words library; port of android/.../ui/components/WordDetail.kt.

/// Headword with transcription and a listen button.
struct Headword: View {
    @EnvironmentObject private var model: AppModel
    let word: Word
    let settings: Settings
    var big = true

    var body: some View {
        let headline = word.entry(settings.headline)
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(headline.text)
                    .textStyle(big ? TypeScale.displaySmall : TypeScale.headlineMedium)
                    .foregroundStyle(Theme.onSurface)
                    .minimumScaleFactor(0.6)
                    .fixedSize(horizontal: false, vertical: true)
                if !headline.tr.isEmpty {
                    Text(L10n.transcription(settings.headline, headline.tr))
                        .textStyle(TypeScale.transcription.resized(16))
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if model.canSpeak(settings.headline) {
                CircleIconButton(
                    systemImage: "speaker.wave.2.fill",
                    label: L10n.t("action_speak"),
                    container: Theme.primaryContainer,
                    content: Theme.onPrimaryContainer,
                    size: 48,
                    iconSize: 19
                ) {
                    model.speak(headline.text, lang: settings.headline)
                }
            }
        }
    }
}

/// "RU  слово  [slóvo]  🔊" rows for every translation language.
struct TranslationRows: View {
    @EnvironmentObject private var model: AppModel
    let word: Word
    let settings: Settings

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(settings.translations) { lang in
                let entry = word.entry(lang)
                HStack(alignment: .center, spacing: 12) {
                    LangTag(lang: lang)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(entry.text)
                            .textStyle(TypeScale.titleLarge)
                            .foregroundStyle(Theme.onSurface)
                        if settings.showTranscriptions, !entry.tr.isEmpty {
                            Text(L10n.transcription(lang, entry.tr))
                                .textStyle(TypeScale.transcription)
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    if model.canSpeak(lang) {
                        Button {
                            model.speak(entry.text, lang: lang)
                        } label: {
                            Image(systemName: "speaker.wave.2")
                                .font(.system(size: 18, weight: .medium))
                                .foregroundStyle(Theme.onSurfaceVariant)
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(PressableStyle(scale: 0.9))
                        .accessibilityLabel(L10n.t("action_speak"))
                    }
                }
            }
        }
    }
}

/// The example sentence in the learned language, then its translations.
struct ExampleBlock: View {
    let word: Word
    let settings: Settings

    var body: some View {
        let main = word.example.of(settings.headline)
        if !main.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                Overline(text: L10n.t("detail_example"))
                Text(main)
                    .textStyle(TypeScale.bodyLarge.resized(17))
                    .foregroundStyle(Theme.onSurface)
                    .fixedSize(horizontal: false, vertical: true)
                ForEach(settings.translations) { lang in
                    let text = word.example.of(lang)
                    if !text.isEmpty {
                        HStack(alignment: .top, spacing: 10) {
                            LangTag(lang: lang)
                                .padding(.top, 2)
                            Text(text)
                                .textStyle(TypeScale.bodyMedium)
                                .foregroundStyle(Theme.onSurfaceVariant)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(.top, 8)
                    }
                }
            }
        }
    }
}

/// Favourite (the heart bounces) and "I know it" toggles.
struct WordToggles: View {
    let favorite: Bool
    let learned: Bool
    let onFavorite: () -> Void
    let onLearned: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            CircleIconButton(
                systemImage: favorite ? "heart.fill" : "heart",
                label: L10n.t(favorite ? "action_unfavorite" : "action_favorite"),
                container: favorite ? Theme.favorite.opacity(0.16) : Theme.surfaceContainerHigh,
                content: favorite ? Theme.favorite : Theme.onSurface,
                action: onFavorite
            )
            .symbolEffect(.bounce, value: favorite)
            .sensoryFeedback(.impact(weight: .medium), trigger: favorite)
            CircleIconButton(
                systemImage: learned ? "checkmark.circle.fill" : "checkmark.circle",
                label: L10n.t(learned ? "action_unlearned" : "action_learned"),
                container: learned ? Theme.successContainer : Theme.surfaceContainerHigh,
                content: learned ? Theme.success : Theme.onSurface,
                action: onLearned
            )
            .symbolEffect(.bounce, value: learned)
            .sensoryFeedback(.success, trigger: learned) { _, isLearned in isLearned }
        }
    }
}

/// Everything about one word, as shown in the sheet of the Words library.
struct WordDetailSheet: View {
    @EnvironmentObject private var model: AppModel
    let word: Word

    var body: some View {
        let settings = model.settings
        let favorite = model.progress.favorites.contains(word.id)
        let learned = model.progress.learned.contains(word.id)
        let isCurrent = model.progress.currentId == word.id
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 14) {
                    WordThumb(word: word, size: 64, headline: settings.headline)
                    HStack(spacing: 8) {
                        LevelBadge(level: word.level)
                        Text(PosNames.localized(word.pos))
                            .textStyle(TypeScale.labelMedium)
                            .foregroundStyle(Theme.onSurfaceVariant)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    WordToggles(
                        favorite: favorite,
                        learned: learned,
                        onFavorite: { model.toggleFavorite(word.id) },
                        onLearned: { model.toggleLearned(word.id) }
                    )
                }
                Headword(word: word, settings: settings)
                    .padding(.top, 18)
                if !settings.translations.isEmpty {
                    TranslationRows(word: word, settings: settings)
                        .padding(.top, 18)
                }
                Rectangle()
                    .fill(Theme.outlineVariant)
                    .frame(height: 1)
                    .padding(.vertical, 16)
                ExampleBlock(word: word, settings: settings)
                Button {
                    model.showWord(word.id)
                } label: {
                    Label(
                        L10n.t(isCurrent ? "ios_msg_shown" : "action_show_on_wallpaper"),
                        systemImage: isCurrent ? "checkmark" : "photo.on.rectangle.angled"
                    )
                }
                .buttonStyle(PrimaryButtonStyle(height: 54))
                .disabled(isCurrent)
                .padding(.top, 24)
                Text(L10n.t("ios_show_note"))
                    .textStyle(TypeScale.bodySmall)
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 10)
            }
            .padding(.horizontal, 24)
            .padding(.top, 28)
            .padding(.bottom, 16)
        }
        .background(Theme.surfaceContainerLowest)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(Radius.extraLarge)
    }
}
