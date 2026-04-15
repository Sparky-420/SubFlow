package com.subflow.app

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslationHelper(
    private val context: Context,
    sourceLanguageTag: String,
    targetLanguageTag: String
) {
    private val source = TranslateLanguage.fromLanguageTag(sourceLanguageTag) ?: TranslateLanguage.ENGLISH
    private val target = TranslateLanguage.fromLanguageTag(targetLanguageTag) ?: TranslateLanguage.SPANISH

    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(source)
        .setTargetLanguage(target)
        .build()

    private var translator: Translator = Translation.getClient(options)
    @Volatile
    private var isModelReady = false

    fun downloadIfNeeded(
        onReady: () -> Unit,
        onError: (String) -> Unit
    ) {
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                isModelReady = true
                onReady()
            }
            .addOnFailureListener { error ->
                isModelReady = false
                onError(error.localizedMessage ?: "No se pudo descargar el modelo de traducción")
            }
    }

    fun translate(
        text: String,
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        if (!isModelReady) {
            onResult(text)
            return
        }
        translator.translate(text)
            .addOnSuccessListener { onResult(it) }
            .addOnFailureListener { error ->
                onError?.invoke(error.localizedMessage ?: "Error al traducir")
                onResult(text)
            }
    }

    fun close() {
        translator.close()
    }
}
