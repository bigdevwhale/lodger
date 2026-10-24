package app.lodger.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lodger.ui.theme.C
import app.lodger.ui.theme.T

enum class Kind { AMBER, GHOST, RED }

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, kind: Kind = Kind.AMBER, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    val (bg, fg) = when {
        !enabled -> C.line to C.faint
        kind == Kind.AMBER -> C.amber to C.night
        kind == Kind.RED -> C.red to C.paper
        else -> Color.Transparent to C.text
    }
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .then(if (kind == Kind.GHOST && enabled) Modifier.border(1.dp, C.dim, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 46.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.Button.copy(color = fg), textAlign = TextAlign.Center) }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) = Text(text.uppercase(), style = T.Label, modifier = modifier)

/** A dark card with a hairline edge. */
fun Modifier.panel() = this.clip(RoundedCornerShape(6.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(6.dp))

/** A scrap of paper. */
fun Modifier.paper(bg: Color = C.paper) = this.background(bg).padding(horizontal = 16.dp, vertical = 14.dp)

/** Taps without the ripple: for photos and paper. */
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier = this.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)

/** A sheet that rises from the bottom over a dark scrim; back or a tap on the scrim closes it. */
@Composable
fun Sheet(visible: Boolean, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (visible) BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(C.scrim).tap(onClose))
        }
        AnimatedVisibility(visible, modifier = Modifier.align(Alignment.BottomCenter), enter = slideInVertically(tween(260)) { it }, exit = slideOutVertically(tween(220)) { it }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(C.panel).tap {}
                    .navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                content = content,
            )
        }
    }
}

/** A full screen laid over the tabs, with a close cross. */
@Composable
fun Page(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(C.shell).tap {}.statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(title, Modifier.weight(1f))
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClose), contentAlignment = Alignment.Center) { Glyph(Icon.CLOSE, 22.dp, C.dim) }
        }
        Box(modifier.weight(1f).fillMaxWidth().navigationBarsPadding(), content = content)
    }
}

@Composable
fun Gap(h: Dp) = Box(Modifier.size(1.dp, h))

@Composable
fun Center(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start, content = content)

enum class Icon { HOUSE, NOTE, RADIO, BOARD, GEAR, CAMERA, CLOSE, BOOK, DOOR, PLUS, MINUS }

/** Line icons drawn in code, all on a 24-unit grid. */
@Composable
fun Glyph(icon: Icon, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { glyph(icon, color) }
}

fun DrawScope.glyph(icon: Icon, color: Color) {
    val u = size.minDimension / 24f
    val st = Stroke(1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(vararg xy: Float, close: Boolean = false) = Path().apply {
        moveTo(xy[0] * u, xy[1] * u)
        for (i in 2 until xy.size step 2) lineTo(xy[i] * u, xy[i + 1] * u)
        if (close) close()
    }
    fun line(vararg xy: Float) = drawPath(p(*xy), color, style = st)
    fun circle(x: Float, y: Float, r: Float, fill: Boolean = false) =
        if (fill) drawCircle(color, r * u, Offset(x * u, y * u)) else drawCircle(color, r * u, Offset(x * u, y * u), style = st)
    fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 0f) =
        drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u), style = st)
    when (icon) {
        Icon.HOUSE -> { drawPath(p(3f, 11f, 12f, 4f, 21f, 11f, 21f, 20f, 3f, 20f, close = true), color, style = st); line(10f, 20f, 10f, 15f, 14f, 15f, 14f, 20f) }
        Icon.NOTE -> { drawPath(p(6f, 3f, 15f, 3f, 18f, 6f, 18f, 21f, 6f, 21f, close = true), color, style = st); line(9f, 10f, 15f, 10f); line(9f, 14f, 15f, 14f); line(9f, 18f, 12f, 18f) }
        Icon.RADIO -> { rect(3f, 8f, 18f, 12f, 2f); line(7f, 8f, 17f, 3f); circle(16f, 14f, 2.5f); line(6f, 12f, 11f, 12f); line(6f, 15f, 11f, 15f) }
        Icon.BOARD -> { rect(3f, 4f, 18f, 16f); line(7f, 8f, 12f, 14f, 17f, 10f); circle(7f, 8f, 1f, true); circle(12f, 14f, 1f, true); circle(17f, 10f, 1f, true) }
        Icon.GEAR -> { circle(12f, 12f, 3f); for (k in 0 until 8) { val a = k * Math.PI / 4; line(12f + 6f * kotlin.math.cos(a).toFloat(), 12f + 6f * kotlin.math.sin(a).toFloat(), 12f + 8.5f * kotlin.math.cos(a).toFloat(), 12f + 8.5f * kotlin.math.sin(a).toFloat()) }; circle(12f, 12f, 6f) }
        Icon.CAMERA -> { drawPath(p(3f, 8f, 7f, 8f, 9f, 5f, 15f, 5f, 17f, 8f, 21f, 8f, 21f, 19f, 3f, 19f, close = true), color, style = st); circle(12f, 13f, 3.5f) }
        Icon.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Icon.BOOK -> { drawPath(p(4f, 4f, 11f, 5f, 11f, 20f, 4f, 19f, close = true), color, style = st); drawPath(p(20f, 4f, 13f, 5f, 13f, 20f, 20f, 19f, close = true), color, style = st) }
        Icon.DOOR -> { drawPath(p(6f, 21f, 6f, 3f, 18f, 3f, 18f, 21f), color, style = st); drawPath(p(8f, 4f, 16f, 5.5f, 16f, 20f, 8f, 21f, close = true), color, style = st); circle(14f, 12.5f, .9f, true) }
        Icon.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        Icon.MINUS -> line(5f, 12f, 19f, 12f)
    }
}
