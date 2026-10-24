package app.lodger.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The radio's hiss, made live: noise through a band that wanders, with a mains hum underneath.
 * [signal] (0..1) is how close the dial is to a voice: the hiss thins and a low murmur rises.
 */
class Static {
    @Volatile var signal = 0f
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "static").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread = null
    }

    private fun loop() {
        val rate = Synth.RATE
        val size = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(2048)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(size * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return
        val r = Random(System.nanoTime())
        var band = Synth.Band(1800.0, .6)
        val murmur = Synth.Band(320.0, 4.0)
        val buf = ShortArray(512)
        var gain = 0.0
        var t = 0.0
        track.play()
        while (running) {
            if (r.nextInt(6) == 0) band = Synth.Band(700.0 + r.nextDouble() * 2600, .6)
            val s = signal.toDouble()
            for (i in buf.indices) {
                gain += ((.32 * (1 - .8 * s) + .04) - gain) * .002
                val n = r.nextDouble() * 2 - 1
                // A voice somewhere under the hiss: a slow throat-band warble.
                val voice = murmur.next(n) * 3.0 * s * (.6 + .4 * sin(2 * PI * 4.5 * t))
                val hum = sin(2 * PI * 50 * t) * .03
                buf[i] = ((band.next(n) * 1.8 * gain + voice + hum) * 26000).toInt().coerceIn(-32767, 32767).toShort()
                t += 1.0 / rate
            }
            track.write(buf, 0, buf.size)
        }
        runCatching { track.stop() }
        track.release()
    }
}
