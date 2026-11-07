package nl.contrastlezer.ocr

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Leest herkende tekst voor met de tekst-naar-spraak-engine van Android in het
 * Nederlands. Alles gebeurt op het toestel; er wordt niets verstuurd.
 *
 * De engine start op de achtergrond. Tekst die al wordt aangeboden voordat de
 * engine klaar is, wordt onthouden en daarna alsnog voorgelezen.
 */
class SpeechReader(context: Context) : AutoCloseable {

    private val _speaking = MutableStateFlow(false)

    /** True zolang er wordt voorgelezen. */
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    /** Wordt aangeroepen als voorlezen niet kan of mislukt. */
    var onError: (() -> Unit)? = null

    private lateinit var tts: TextToSpeech
    private var ready = false
    private var languageAvailable = false
    private var pendingText: String? = null
    private var lastUtteranceId: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = true
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.forLanguageTag("nl-NL"))
                languageAvailable = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                tts.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _speaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == lastUtteranceId) _speaking.value = false
                        }

                        override fun onError(utteranceId: String?) {
                            fail()
                        }
                    },
                )
                pendingText?.let { text ->
                    pendingText = null
                    speak(text)
                }
            } else {
                pendingText = null
                fail()
            }
        }
    }

    /** Leest [text] voor en onderbreekt wat er nog werd voorgelezen. */
    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pendingText = text
            return
        }
        if (!languageAvailable) {
            fail()
            return
        }
        val parts = SpeechTextSplitter.split(text, TextToSpeech.getMaxSpeechInputLength())
        if (parts.isEmpty()) return

        lastUtteranceId = utteranceId(parts.lastIndex)
        _speaking.value = true
        parts.forEachIndexed { index, part ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(part, mode, null, utteranceId(index))
        }
    }

    /** Stopt met voorlezen. */
    fun stop() {
        pendingText = null
        lastUtteranceId = null
        if (::tts.isInitialized) tts.stop()
        _speaking.value = false
    }

    override fun close() {
        stop()
        if (::tts.isInitialized) tts.shutdown()
    }

    private fun fail() {
        _speaking.value = false
        onError?.invoke()
    }

    private fun utteranceId(index: Int) = "contrastlezer-$index"
}
