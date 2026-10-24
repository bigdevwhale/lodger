package app.lodger.ui.story

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import app.lodger.BuildConfig
import app.lodger.ui.components.Btn
import app.lodger.ui.components.Kind
import app.lodger.ui.components.tap
import app.lodger.ui.theme.C
import app.lodger.ui.theme.T
import kotlinx.coroutines.delay

/** One line of a black-screen passage; [label] lines are small capitals, like a heading. */
data class Line(val text: String, val label: Boolean = false)

/**
 * A passage on black, line after line, then one button. Used when someone moves in and when they leave.
 * Back does nothing: the passage is short, and it is better read.
 */
@Composable
fun Sequence(lines: List<Line>, button: String, still: Boolean = BuildConfig.SHOTS, onDone: () -> Unit) {
    BackHandler {}
    val fades = remember(lines) { List(lines.size + 1) { Animatable(if (still) 1f else 0f) } }
    LaunchedEffect(lines) {
        if (still) return@LaunchedEffect
        delay(400)
        for (f in fades) { f.animateTo(1f, tween(1400)); delay(500) }
    }
    Column(
        Modifier.fillMaxSize().background(C.night).tap {}.systemBarsPadding().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        for ((i, l) in lines.withIndex()) {
            Text(if (l.label) l.text.uppercase() else l.text, style = if (l.label) T.Label else T.Title.copy(lineHeight = T.Title.lineHeight), modifier = Modifier.alpha(fades[i].value))
        }
        val last = fades.last()
        Btn(button, Modifier.padding(top = 12.dp).alpha(last.value), Kind.AMBER, enabled = last.value > .5f, onClick = onDone)
    }
}
