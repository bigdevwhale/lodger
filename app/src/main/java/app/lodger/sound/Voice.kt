package app.lodger.sound

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * The voice on the radio: the phone's own speech engine, slowed and lowered. If there is no engine,
 * or none for the language, the word simply appears without a voice.
 */
class Voice(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    private fun ensure() {
        if (tts != null) return
        tts = runCatching { TextToSpeech(context) { ready = it == TextToSpeech.SUCCESS } }.getOrNull()
    }

    fun warm() = ensure()

    fun say(word: String, ru: Boolean) {
        ensure()
        val t = tts ?: return
        if (!ready) return
        val loc = if (ru) Locale("ru", "RU") else Locale.UK
        if (t.isLanguageAvailable(loc) < TextToSpeech.LANG_AVAILABLE) return
        t.language = loc
        t.setPitch(.45f)
        t.setSpeechRate(.6f)
        t.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word")
    }
}
