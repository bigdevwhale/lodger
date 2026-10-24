package app.lodger.ui.radio

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lodger.container
import app.lodger.domain.Engine
import app.lodger.ui.Home
import app.lodger.ui.components.Glyph
import app.lodger.ui.components.Icon
import app.lodger.ui.components.Label
import app.lodger.ui.theme.C
import app.lodger.ui.theme.Hand
import app.lodger.ui.theme.Mono
import app.lodger.ui.theme.T
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

private const val LO = 87.5
private const val HI = 108.0
private const val GLYPHS = "░▒▓·:;/\\|—~"

/** Letters of [w] come through the hiss more surely the stronger the [signal]. */
fun scramble(w: String, signal: Float, r: Random = Random): String =
    w.map { ch -> if (r.nextFloat() < signal.toDouble().pow(1.5).toFloat()) ch else GLYPHS[r.nextInt(GLYPHS.length)] }.joinToString("")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioScreen(h: Home) {
    val ctx = LocalContext.current
    val c = ctx.container
    val l = h.lodger ?: return
    var powered by remember { mutableStateOf(h.still) }
    var freq by rememberSaveable { mutableFloatStateOf(if (h.still) l.freqs.getOrElse(1) { 95.0 }.toFloat() else 95f) }
    var shown by remember { mutableStateOf("") }
    var caught by remember { mutableStateOf(false) }
    var signal by remember { mutableFloatStateOf(0f) }
    val open = Engine.open(l, h.night)
    val words by rememberUpdatedState(h.h.words)

    DisposableEffect(powered, h.settings.sound) {
        if (powered && h.settings.sound && !h.still) c.static.start() else c.static.stop()
        onDispose { c.static.stop() }
    }
    LaunchedEffect(powered, l.id, h.night) {
        if (!powered) { shown = ""; signal = 0f; return@LaunchedEffect }
        c.voice.warm()
        var lock = 0
        while (true) {
            val best = open.minByOrNull { abs(freq - l.freqs[it]) }
            val d = best?.let { abs(freq - l.freqs[it]).toFloat() } ?: 99f
            signal = (1f - d / .6f).coerceIn(0f, 1f)
            c.static.signal = signal
            caught = false
            when {
                best == null || signal <= 0f -> { shown = if (Random.nextFloat() < .25f) scramble("········", 0f).take(3 + Random.nextInt(6)) else ""; lock = 0 }
                best in words && d < .15f -> { shown = l.words[best]; caught = true; lock = 0 }
                else -> {
                    shown = if (h.still) l.words[best] else scramble(l.words[best], signal)
                    if (d < .12f) {
                        lock += 90
                        if (lock > 1100) {
                            lock = 0
                            c.write { word(best) }
                            c.sfx.breath()
                            if (h.settings.voice && h.settings.sound) c.voice.say(l.words[best], h.ru)
                            runCatching { ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(80, 160)) }
                        }
                    } else lock = 0
                }
            }
            delay(90)
        }
    }

    fun tune(f: Float) { freq = ((f.coerceIn(LO.toFloat(), HI.toFloat()) * 20).roundToInt() / 20f) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Label(h.w.t("Радио", "Radio"), Modifier.padding(bottom = 12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(C.wood, C.woodDeep))).border(1.dp, Color(0xFF4C3826), RoundedCornerShape(14.dp)).padding(16.dp),
        ) {
            // The screen: frequency, signal bars and whatever comes through.
            Box(Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0D0B07)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text("%.1f".format(freq) + if (h.ru) " МГц" else " MHz", style = T.Dial)
                Row(Modifier.align(Alignment.TopEnd).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    for (i in 0 until 5) Box(Modifier.size(5.dp, (6 + i * 4).dp).background(if (powered && signal > i / 5f) C.amber else Color(0xFF3A2C18)))
                }
                Text(
                    shown, maxLines = 1,
                    style = T.Dial.copy(fontSize = 24.sp, color = if (caught) C.amberHi else Color(0xFFCFC3A4), fontFamily = Mono),
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
            Dial(freq, Modifier.padding(top = 14.dp).fillMaxWidth().height(64.dp), onTune = ::tune)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Knob(Icon.MINUS) { tune(freq - .1f) }
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(if (powered) C.amber.copy(alpha = .15f) else Color.Transparent)
                        .border(1.dp, if (powered) C.amber else Color(0xFF6B5038), RoundedCornerShape(20.dp))
                        .clickable { powered = !powered; c.sfx.tap() }.padding(horizontal = 20.dp, vertical = 10.dp),
                ) { Text(if (powered) h.w.t("ВЫКЛЮЧИТЬ", "SWITCH OFF") else h.w.t("ВКЛЮЧИТЬ", "SWITCH ON"), style = T.Label.copy(color = if (powered) C.amberHi else C.paper2)) }
                Knob(Icon.PLUS) { tune(freq + .1f) }
            }
        }
        // The phrase, word by word as they are caught.
        FlowRow(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.Center) {
            for ((k, w) in l.words.withIndex()) {
                Text(
                    if (k in h.h.words) w else "· · ·",
                    style = T.Note.copy(fontFamily = Hand, fontSize = 30.sp, color = if (k in h.h.words) C.paper else C.faint),
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
        val waiting = open.filter { it !in h.h.words }
        val next = l.words.indices.map { Engine.wordNight(it, l.words.size) }.filter { it > h.night }.minOrNull()
        Text(
            when {
                h.h.words.size == l.words.size -> h.w.t("Все слова пойманы. Эфир молчит.", "Every word is caught. The air is silent.")
                waiting.isNotEmpty() -> h.w.t("Крути шкалу. Когда сквозь шум проступят буквы — задержись. В эфире сейчас слов: ${waiting.size}.", "Turn the dial. When letters come through the hiss, hold it there. Words on air now: ${waiting.size}.")
                next != null -> h.w.t("Сейчас в эфире только шум. Следующее слово — через ${h.w.nights(next - h.night)}.", "Only hiss on air now. The next word comes in ${h.w.nights(next - h.night)}.")
                else -> ""
            },
            style = T.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 8.dp, end = 8.dp),
        )
    }
}

@Composable
private fun Knob(icon: Icon, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF6B5038), Color(0xFF2A1D13)), center = Offset(40f, 36f)))
            .border(1.dp, Color(0xFF120C08), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, 18.dp, Color(0xFFD9C39A)) }
}

/** The tuning scale: drag or tap anywhere to move the needle. */
@Composable
private fun Dial(freq: Float, modifier: Modifier, onTune: (Float) -> Unit) {
    val measurer = rememberTextMeasurer()
    val tuneNow by rememberUpdatedState(onTune)
    val f by rememberUpdatedState(freq)
    Canvas(
        modifier.clip(RoundedCornerShape(4.dp))
            .pointerInput(Unit) { detectTapGestures { tuneNow((LO + it.x / size.width * (HI - LO)).toFloat()) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { _, dx -> tuneNow(f + (dx / size.width * (HI - LO)).toFloat()) } },
    ) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFE8D6A8), Color(0xFFCDB57C))))
        val ink = Color(0xFF4A3418)
        fun x(v: Double) = ((v - LO) / (HI - LO) * size.width).toFloat()
        var v = 88.0
        while (v <= 108.0) {
            val big = v % 2.0 == 0.0
            drawLine(ink, Offset(x(v), 0f), Offset(x(v), size.height * (if (big) .38f else .2f)), if (big) 2.4f else 1.3f)
            if (v % 4.0 == 0.0) {
                val layout = measurer.measure(v.toInt().toString(), androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 12.sp, color = ink))
                val lx = (x(v) - layout.size.width / 2f).coerceIn(2f, size.width - layout.size.width - 2f)
                drawText(layout, topLeft = Offset(lx, size.height * .5f))
            }
            v += .5
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x557A5A2A)), radius = size.width * .7f))
        val nx = x(freq.toDouble())
        drawLine(C.red, Offset(nx, 0f), Offset(nx, size.height), 5f)
    }
}
