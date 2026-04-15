package com.subflow.app

import android.content.Context

data class AppSettings(
    val sourceLanguageTag: String = "en",
    val targetLanguageTag: String = "es",
    val translationEnabled: Boolean = true,
    val fontSizeSp: Int = 22,
    val bubbleOpacityPercent: Int = 82,
    val bubblePosX: Int = 0,
    val bubblePosY: Int = 160
)

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("subflow_prefs", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        sourceLanguageTag = prefs.getString(KEY_SOURCE_LANG, "en") ?: "en",
        targetLanguageTag = prefs.getString(KEY_TARGET_LANG, "es") ?: "es",
        translationEnabled = prefs.getBoolean(KEY_TRANSLATION_ENABLED, true),
        fontSizeSp = prefs.getInt(KEY_FONT_SIZE, 22),
        bubbleOpacityPercent = prefs.getInt(KEY_BUBBLE_OPACITY, 82),
        bubblePosX = prefs.getInt(KEY_BUBBLE_POS_X, 0),
        bubblePosY = prefs.getInt(KEY_BUBBLE_POS_Y, 160)
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_SOURCE_LANG, settings.sourceLanguageTag)
            .putString(KEY_TARGET_LANG, settings.targetLanguageTag)
            .putBoolean(KEY_TRANSLATION_ENABLED, settings.translationEnabled)
            .putInt(KEY_FONT_SIZE, settings.fontSizeSp)
            .putInt(KEY_BUBBLE_OPACITY, settings.bubbleOpacityPercent)
            .putInt(KEY_BUBBLE_POS_X, settings.bubblePosX)
            .putInt(KEY_BUBBLE_POS_Y, settings.bubblePosY)
            .apply()
    }

    companion object {
        private const val KEY_SOURCE_LANG = "source_language"
        private const val KEY_TARGET_LANG = "target_language"
        private const val KEY_TRANSLATION_ENABLED = "translation_enabled"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_BUBBLE_OPACITY = "bubble_opacity"
        private const val KEY_BUBBLE_POS_X = "bubble_pos_x"
        private const val KEY_BUBBLE_POS_Y = "bubble_pos_y"
    }
}
