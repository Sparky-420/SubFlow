package com.subflow.app

import java.util.Locale

data class LanguageOption(
    val tag: String,
    val label: String
)

object SupportedLanguages {
    val options = listOf(
        LanguageOption("en", "English"),
        LanguageOption("es", "Español"),
        LanguageOption("fr", "Français"),
        LanguageOption("de", "Deutsch"),
        LanguageOption("it", "Italiano"),
        LanguageOption("pt", "Português"),
        LanguageOption("ru", "Русский"),
        LanguageOption("ja", "日本語"),
        LanguageOption("ko", "한국어")
    )

    fun labels(): List<String> = options.map { it.label }

    fun indexOfTag(tag: String): Int = options.indexOfFirst { it.tag == tag }.coerceAtLeast(0)

    fun tagAt(position: Int): String = options.getOrNull(position)?.tag ?: "en"

    fun labelForTag(tag: String): String = options.firstOrNull { it.tag == tag }?.label ?: tag

    fun localeForTag(tag: String): Locale = Locale.forLanguageTag(tag)
}
