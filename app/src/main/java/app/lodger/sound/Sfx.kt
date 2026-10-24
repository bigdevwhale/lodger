package app.lodger.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The flat's sounds, synthesised once into small WAV files: knuckles on a door, a single thud, a floorboard
 * creaking, the soft tap of a button and the breath that carries a word out of the static.
 */
class Sfx(private val context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private var knock = 0
    private var thud = 0
    private var creak = 0
    private var tap = 0
    private var breath = 0
    @Volatile var on = true

    fun load() {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        fun file(name: String, pcm: () -> ShortArray) = File(dir, "$name.wav").also { if (!it.exists()) Wav.write(it, pcm()) }.absolutePath
        knock = pool.load(file("knock1") { Synth.knock(3) }, 1)
        thud = pool.load(file("thud1") { Synth.knock(1) }, 1)
        creak = pool.load(file("creak1") { Synth.creak() }, 1)
        tap = pool.load(file("tap1") { Synth.tap() }, 1)
        breath = pool.load(file("breath1") { Synth.breath() }, 1)
    }

    private fun play(id: Int, vol: Float = .9f) { if (on && id != 0) pool.play(id, vol, vol, 1, 0, 1f) }

    fun knock() = play(knock)
    fun thud() = play(thud, .8f)
    fun creak() = play(creak, .6f)
    fun tap() = play(tap, .4f)
    fun breath() = play(breath, .7f)
}

object Synth {
    const val RATE = 22050

    private fun pcm(seconds: Double, f: (Int, Double) -> Double): ShortArray {
        val n = (RATE * seconds).toInt()
        return ShortArray(n) { i -> (f(i, i / RATE.toDouble()) * 30000).toInt().coerceIn(-32767, 32767).toShort() }
    }

    /** A resonant band-pass for noise: wood, breath, a dry hinge. */
    class Band(f: Double, q: Double) {
        private val w = 2 * PI * f / RATE
        private val a = sin(w) / (2 * q)
        private val b0 = a / (1 + a); private val a1 = -2 * kotlin.math.cos(w) / (1 + a); private val a2 = (1 - a) / (1 + a)
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
        fun next(x: Double): Double { val y = b0 * x - b0 * x2 - a1 * y1 - a2 * y2; x2 = x1; x1 = x; y2 = y1; y1 = y; return y }
    }

    /** [n] knocks on a wooden door, a little uneven, the last one softer. */
    fun knock(n: Int): ShortArray {
        val r = Random(11)
        val gap = .34
        val lo = Band(95.0, 2.5); val mid = Band(420.0, 3.0)
        return pcm(gap * n + .3) { _, t ->
            val k = (t / gap).toInt()
            if (k >= n) return@pcm 0.0
            val local = t - k * gap - (if (k == 1) .02 else 0.0)
            if (local < 0) return@pcm 0.0
            val e = exp(-local / .035) * (if (k == n - 1 && n > 1) .7 else 1.0)
            val noise = r.nextDouble() * 2 - 1
            (sin(2 * PI * 82 * local) * .9 + lo.next(noise) * 2.4 + mid.next(noise) * 1.1) * e * .8
        }
    }

    /** A floorboard taking someone's weight: a slow, rough, rising squeak. */
    fun creak(): ShortArray {
        val r = Random(4)
        var phase = 0.0
        val band = Band(900.0, 6.0)
        return pcm(1.1) { _, t ->
            val f = 180 + 120 * t + 18 * sin(2 * PI * 7 * t)
            phase += f / RATE
            val pulse = if ((phase % 1.0) < .08) 1.0 else 0.0
            val env = sin(PI * t / 1.1).coerceAtLeast(0.0)
            band.next(pulse + (r.nextDouble() - .5) * .3) * env * 2.8
        }
    }

    fun tap(): ShortArray = pcm(.05) { _, t -> sin(2 * PI * 900 * t) * exp(-t * 120) * .4 }

    /** An exhale: noise swelling and fading through a low band. */
    fun breath(): ShortArray {
        val r = Random(9); val band = Band(700.0, .9)
        return pcm(1.4) { _, t -> band.next(r.nextDouble() * 2 - 1) * sin(PI * t / 1.4) * .9 }
    }
}

object Wav {
    fun write(f: File, pcm: ShortArray) {
        val data = ByteArrayOutputStream()
        for (s in pcm) { data.write(s.toInt() and 0xFF); data.write((s.toInt() shr 8) and 0xFF) }
        val bytes = data.toByteArray()
        DataOutputStream(f.outputStream().buffered()).use { o ->
            fun le32(v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF); o.write((v shr 16) and 0xFF); o.write((v shr 24) and 0xFF) }
            fun le16(v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF) }
            o.writeBytes("RIFF"); le32(36 + bytes.size); o.writeBytes("WAVE")
            o.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(Synth.RATE); le32(Synth.RATE * 2); le16(2); le16(16)
            o.writeBytes("data"); le32(bytes.size); o.write(bytes)
        }
    }
}
