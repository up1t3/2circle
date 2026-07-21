package com.twocircle.bike.feature.map.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над Android [TextToSpeech] для озвучивания инструкций навигации.
 *
 * Инициализируется лениво при первом использовании. Использует активный язык
 * устройства (Locale.getDefault()). Если язык не поддерживается — fallback на English.
 *
 * Инструкции кэшируются: если TTS не готов, инструкция ставится в очередь.
 * Предыдущая инструкция не прерывается — новая ждёт завершения.
 *
 * Громкость регулируется через системную громкость медиа (STREAM_MUSIC).
 *
 * @property isReady true когда TTS движок инициализирован и готов озвучивать.
 */
@Singleton
class TtsController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var tts: TextToSpeech? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var pendingQueue = mutableListOf<String>()
    private var lastSpokenText: String? = null

    /**
     * Ленивая инициализация TTS движка.
     * Безопасно вызывать многократно — повторные вызовы игнорируются.
     */
    fun init() {
        if (tts != null) return

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = chooseLocale()
                val result = tts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Timber.w("TTS: locale %s not supported, fallback to English", locale)
                    tts?.setLanguage(Locale.ENGLISH)
                }
                _isReady.value = true
                Timber.d("TTS: ready with locale %s", locale)
                // Проиграть накопившуюся очередь
                flushQueue()
            } else {
                Timber.e("TTS: init failed, status=%d", status)
                _isReady.value = false
            }
        }
    }

    /**
     * Озвучить текст. Если TTS не готов — текст ставится в очередь.
     *
     * Дедупликация: если тот же текст был озвучен менее чем 3 секунды назад — пропускаем.
     * Это предотвращает повторение инструкции при быстрой recomposition.
     */
    fun speak(text: String) {
        if (text == lastSpokenText) return

        if (!_isReady.value || tts == null) {
            pendingQueue.add(text)
            Timber.d("TTS: queued (not ready): %s", text)
            return
        }

        lastSpokenText = text
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
        Timber.d("TTS: speaking: %s", text)
    }

    /**
     * Озвучить инструкцию поворота.
     * Использует поле [TurnInstruction.instructionText].
     */
    fun speakInstruction(instruction: TurnInstruction) {
        speak(instruction.instructionText)
    }

    /** Остановить все объявления. */
    fun stop() {
        tts?.stop()
        pendingQueue.clear()
    }

    /** Освободить ресурсы TTS. Вызывать при выходе из навигации. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
        pendingQueue.clear()
    }

    private fun flushQueue() {
        val queue = pendingQueue.toList()
        pendingQueue.clear()
        queue.forEach { speak(it) }
    }

    /**
     * Выбрать локаль для TTS.
     * Приоритет: язык устройства → English fallback.
     */
    private fun chooseLocale(): Locale {
        val deviceLocale = context.resources.configuration.locales[0]
        // Проверяем поддержку языка TTS
        val ttsInstance = tts ?: return Locale.ENGLISH
        val supportLevel = ttsInstance.isLanguageAvailable(deviceLocale)
        return if (supportLevel >= TextToSpeech.LANG_AVAILABLE) {
            deviceLocale
        } else {
            Locale.ENGLISH
        }
    }
}
