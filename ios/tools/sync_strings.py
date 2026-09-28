#!/usr/bin/env python3
"""Builds ios/Shared/Resources/Localizable.xcstrings from the Android strings.xml files (the source of
truth for shared interface texts) plus the iOS-only texts below. Keys are the Android resource names."""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "android/app/src/main/res"
OUT = ROOT / "ios/Shared/Resources/Localizable.xcstrings"
LANGS = {"en": "values", "ru": "values-ru", "tg": "values-tg"}

# Android keys the iOS app uses.
SHARED = """
app_tagline nav_today nav_words nav_settings
onb_welcome_title onb_welcome_body onb_app_language onb_start onb_next onb_back
onb_languages_title onb_languages_body onb_level_title onb_level_body onb_look_title onb_look_body
onb_schedule_title onb_schedule_body onb_ready_title
label_learning label_show_translations lang_en lang_ru lang_tj
level_a1 level_a2 level_b1 level_b2 level_c1 level_c2 words_count
label_palette palette_rotate label_layout layout_lock layout_lock_desc layout_home layout_home_desc
layout_compact layout_compact_desc layout_note
palette_peach palette_mint palette_lavender palette_sky palette_sand palette_rose palette_sage
palette_butter palette_lilac palette_powder palette_midnight palette_pine
label_interval interval_minutes interval_hours interval_daily label_quiet quiet_desc quiet_from quiet_to
today_next_change today_every today_quiet_now home_no_word action_next_word action_favorite
action_unfavorite action_learned action_unlearned action_speak action_show_on_wallpaper
stat_streak stat_seen stat_learned stat_due msg_no_words
words_title words_search_hint words_filter_all words_filter_history words_filter_favorites
words_filter_learned words_empty_search words_empty_history words_empty_favorites words_empty_learned
words_clear_search detail_example time_just_now time_minutes_ago time_hours_ago time_yesterday
settings_app_language settings_learning settings_levels settings_show_transcriptions
settings_show_transcriptions_desc settings_show_examples settings_show_examples_desc settings_appearance
settings_wallpaper settings_schedule settings_notifications notif_daily notif_daily_desc notif_at
settings_about settings_about_body settings_privacy settings_version settings_licenses settings_reset
settings_reset_confirm dialog_cancel dialog_reset dialog_ok dialog_close time_picker_title
pos_noun pos_verb pos_adjective pos_adverb pos_interjection pos_phrase pos_pronoun pos_preposition
pos_numeral pos_conjunction widget_name widget_description
""".split()

# iOS-only texts (Apple does not let apps set the wallpaper; see docs/DESIGN.md §3 and §12).
# Tajik: "тасвири экран" for wallpaper, "экрани қулф" for lock screen, as in the Android strings.
IOS = {
    "ios_how_title": {
        "en": "How it works on iPhone",
        "ru": "Как это работает на iPhone",
        "tg": "Дар iPhone чӣ тавр кор мекунад",
    },
    "ios_how_body": {
        "en": "iOS does not let apps change the wallpaper by themselves. You get two ways instead.",
        "ru": "iOS не позволяет приложениям самим менять обои. Вместо этого есть два способа.",
        "tg": "iOS ба барномаҳо иҷозат намедиҳад, ки тасвири экранро худашон иваз кунанд. Ба ҷои ин ду роҳ ҳаст.",
    },
    "ios_widget_title": {
        "en": "Widget — works by itself",
        "ru": "Виджет — работает сам",
        "tg": "Виджет — худ аз худ кор мекунад",
    },
    "ios_widget_body": {
        "en": "Add the LearnPaper widget to your Home Screen or Lock Screen. It shows the current card and changes on schedule with no setup.",
        "ru": "Добавьте виджет LearnPaper на главный экран или экран блокировки. Он показывает текущую карточку и меняется по расписанию без настройки.",
        "tg": "Виджети LearnPaper-ро ба экрани асосӣ ё экрани қулф илова кунед. Он корти ҷориро нишон медиҳад ва бе ягон танзим аз рӯи ҷадвал иваз мешавад.",
    },
    "ios_shortcuts_title": {
        "en": "Real wallpaper via Shortcuts",
        "ru": "Настоящие обои через «Команды»",
        "tg": "Тасвири экрани воқеӣ тавассути «Команды» (Shortcuts)",
    },
    "ios_shortcuts_body": {
        "en": "A short setup in the Shortcuts app puts the card on your actual wallpaper at the times you choose.",
        "ru": "Короткая настройка в приложении «Команды» ставит карточку на настоящие обои в выбранное время.",
        "tg": "Танзими кӯтоҳ дар барномаи «Команды» (Shortcuts) кортро дар вақтҳои интихобкардаи шумо ба тасвири экрани воқеӣ мегузорад.",
    },
    "ios_guide_open": {
        "en": "Wallpaper setup guide",
        "ru": "Как настроить обои",
        "tg": "Дастури танзими тасвири экран",
    },
    "ios_guide_intro": {
        "en": "Once set up, your wallpaper changes to the current card at the times you pick. It takes about five minutes.",
        "ru": "После настройки обои будут меняться на текущую карточку в выбранное время. Это займёт около пяти минут.",
        "tg": "Пас аз танзим тасвири экран дар вақтҳои интихобкардаи шумо ба корти ҷорӣ иваз мешавад. Ин тақрибан панҷ дақиқа вақт мегирад.",
    },
    "ios_guide_step1": {
        "en": "Open the Shortcuts app and tap + to create a new shortcut.",
        "ru": "Откройте приложение «Команды» и нажмите +, чтобы создать новую команду.",
        "tg": "Барномаи «Команды» (Shortcuts)-ро кушоед ва барои сохтани фармони нав тугмаи «+»-ро пахш кунед.",
    },
    "ios_guide_step2": {
        "en": "Add the action “Get LearnPaper card” (search for LearnPaper).",
        "ru": "Добавьте действие «Get LearnPaper card» (найдите LearnPaper в поиске).",
        "tg": "Амали «Get LearnPaper card»-ро илова кунед (дар ҷустуҷӯ LearnPaper-ро нависед).",
    },
    "ios_guide_step3": {
        "en": "Add the action “Set Wallpaper” (on some iOS versions “Set Wallpaper Photo”), choose Lock Screen and Home Screen, and turn off “Show Preview”.",
        "ru": "Добавьте действие «Установить обои» (в некоторых версиях iOS — «Установить фото обоев»), выберите экран блокировки и главный экран и выключите «Показывать превью».",
        "tg": "Амали «Установить обои» (Set Wallpaper)-ро илова кунед (дар баъзе версияҳои iOS «Установить фото обоев»), экрани қулф ва экрани асосиро интихоб кунед ва «Показывать превью» (Show Preview)-ро хомӯш кунед.",
    },
    "ios_guide_step4": {
        "en": "Name the shortcut “LearnPaper wallpaper” and run it once to check.",
        "ru": "Назовите команду «LearnPaper wallpaper» и запустите её один раз для проверки.",
        "tg": "Фармонро «LearnPaper wallpaper» ном гузоред ва барои санҷиш як бор иҷро кунед.",
    },
    "ios_guide_step5": {
        "en": "Go to Automation, tap +, choose Time of Day, pick a time, select Run Immediately and choose the shortcut.",
        "ru": "Перейдите в «Автоматизация», нажмите +, выберите «Время суток», задайте время, включите «Запускать сразу» и выберите команду.",
        "tg": "Ба «Автоматизация» (Automation) гузаред, «+»-ро пахш кунед, «Время суток» (Time of Day)-ро интихоб кунед, вақтро муайян кунед, «Запускать сразу» (Run Immediately)-ро фаъол кунед ва фармонро интихоб кунед.",
    },
    "ios_guide_step6": {
        "en": "Repeat step 5 for every hour you want a new card, e.g. 8:00, 9:00 … 22:00. iOS has no “every hour” trigger, so each time is its own automation.",
        "ru": "Повторите шаг 5 для каждого часа, когда нужна новая карточка, например 8:00, 9:00 … 22:00. В iOS нет триггера «каждый час», поэтому каждое время — отдельная автоматизация.",
        "tg": "Қадами 5-ро барои ҳар соате, ки корти нав мехоҳед, такрор кунед, масалан 8:00, 9:00 … 22:00. Дар iOS триггери «ҳар соат» нест, бинобар ин ҳар вақт автоматизатсияи алоҳида аст.",
    },
    "ios_guide_tip": {
        "en": "Your wallpaper must be a normal photo wallpaper (not Photo Shuffle). Fewer steps: use “When charger connected” or “When Focus changes” triggers instead of times.",
        "ru": "Обои должны быть обычным фото (не «Перемешивание фото»). Меньше шагов: вместо времени используйте триггеры «При подключении зарядки» или «При смене фокусирования».",
        "tg": "Тасвири экран бояд акси оддӣ бошад (на «Перемешивание фото» / Photo Shuffle). Барои қадамҳои камтар ба ҷои вақт триггерҳои «При подключении зарядки» ё «При смене фокусирования»-ро истифода баред.",
    },
    "ios_guide_open_shortcuts": {
        "en": "Open Shortcuts",
        "ru": "Открыть «Команды»",
        "tg": "Кушодани «Команды»",
    },
    "ios_schedule_note": {
        "en": "The widget follows this schedule by itself. For the wallpaper, create a Shortcuts automation for each time you want a change.",
        "ru": "Виджет сам следует этому расписанию. Для обоев создайте в «Командах» автоматизацию на каждое время смены.",
        "tg": "Виджет ин ҷадвалро худаш риоя мекунад. Барои тасвири экран дар «Команды» (Shortcuts) барои ҳар вақти иваз як автоматизатсия созед.",
    },
    "ios_msg_applied": {
        "en": "Card updated",
        "ru": "Карточка обновлена",
        "tg": "Корт нав шуд",
    },
    "ios_msg_shown": {
        "en": "Now the current card",
        "ru": "Теперь это текущая карточка",
        "tg": "Акнун ин корти ҷорӣ аст",
    },
    "ios_show_note": {
        "en": "Widgets show it right away; the wallpaper changes the next time your shortcut runs.",
        "ru": "Виджеты покажут его сразу, а обои сменятся при следующем запуске вашей команды.",
        "tg": "Виджетҳо онро фавран нишон медиҳанд, тасвири экран бошад дафъаи навбатии иҷрои фармони шумо иваз мешавад.",
    },
    "ios_setup_title": {
        "en": "Put it on your wallpaper",
        "ru": "Поставьте карточку на обои",
        "tg": "Кортро ба тасвири экран гузоред",
    },
    "ios_setup_body": {
        "en": "On iPhone only you can change the wallpaper, so it takes a short one-time setup in Shortcuts. The widget works right away.",
        "ru": "На iPhone обои может менять только владелец, поэтому нужна короткая разовая настройка в «Командах». Виджет работает сразу.",
        "tg": "Дар iPhone тасвири экранро танҳо худи соҳиби он иваз карда метавонад, бинобар ин як танзими кӯтоҳи яккарата дар «Команды» (Shortcuts) лозим аст. Виджет фавран кор мекунад.",
    },
    "ios_setup_action": {
        "en": "Show me how",
        "ru": "Показать, как",
        "tg": "Дастурро дидан",
    },
    "ios_onb_ready_body": {
        "en": "Your first word is ready. Widgets change by themselves; your wallpaper follows once Shortcuts is set up.",
        "ru": "Первое слово готово. Виджеты меняются сами, а обои — после настройки «Команд».",
        "tg": "Калимаи аввалин тайёр аст. Виджетҳо худ аз худ иваз мешаванд, тасвири экран бошад — пас аз танзими «Команды» (Shortcuts).",
    },
    "ios_onb_finish": {
        "en": "Start learning",
        "ru": "Начать учиться",
        "tg": "Омӯзишро оғоз кардан",
    },
}


def android_text(raw: str) -> str:
    text = raw.strip()
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
        text = text[1:-1]
    text = re.sub(r"\\(['\"@?])", r"\1", text).replace("\\n", "\n").replace("\\t", "\t")
    # Android positional placeholders to iOS ones (every shared string has at most one argument).
    text = re.sub(r"%1\$s", "%@", text)
    text = re.sub(r"%(?:1\$)?d", "%lld", text)
    if re.search(r"%[0-9]+\$", text):
        sys.exit(f"unsupported placeholder in: {text}")
    return text


def load(folder: str):
    root = ET.parse(RES / folder / "strings.xml").getroot()
    strings, plurals = {}, {}
    for e in root:
        name = e.get("name")
        if e.tag == "string":
            strings[name] = android_text("".join(e.itertext()))
        elif e.tag == "plurals":
            plurals[name] = {item.get("quantity"): android_text("".join(item.itertext())) for item in e}
    return strings, plurals


def unit(value: str):
    return {"stringUnit": {"state": "translated", "value": value}}


def main():
    data = {lang: load(folder) for lang, folder in LANGS.items()}
    out = {}
    missing = []
    for key in SHARED:
        locs = {}
        for lang in LANGS:
            strings, plurals = data[lang]
            if key in plurals:
                forms = plurals[key]
                if "other" not in forms:
                    missing.append(f"{lang}:{key} (no 'other')")
                locs[lang] = {"variations": {"plural": {q: unit(v) for q, v in forms.items()}}}
            elif key in strings:
                locs[lang] = unit(strings[key])
            else:
                missing.append(f"{lang}:{key}")
        out[key] = {"extractionState": "manual", "localizations": locs}
    for key, texts in IOS.items():
        out[key] = {"extractionState": "manual", "localizations": {lang: unit(texts[lang]) for lang in LANGS}}
    if missing:
        sys.exit("missing: " + ", ".join(missing))
    catalog = {"sourceLanguage": "en", "strings": out, "version": "1.0"}
    OUT.write_text(json.dumps(catalog, ensure_ascii=False, indent=2, sort_keys=True, separators=(",", " : ")) + "\n", encoding="utf-8")
    print(f"{len(out)} keys written to {OUT}")


if __name__ == "__main__":
    main()
