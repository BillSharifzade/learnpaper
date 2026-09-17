package com.learnpaper.domain

import com.learnpaper.content.Example
import com.learnpaper.content.LangEntry
import com.learnpaper.content.Word

internal const val DAY = 86_400_000L

internal fun word(id: String, level: String = "A1") = Word(
    id = id,
    level = level,
    pos = "noun",
    en = LangEntry(id, id),
    ru = LangEntry("ру-$id", id),
    tj = LangEntry("тҷ-$id", id),
    example = Example(en = "Example $id.", ru = "Пример $id.", tj = "Мисол $id."),
)

internal fun words(n: Int, level: String = "A1") = (1..n).map { word("$level-$it".lowercase(), level) }
