package app.lodger.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lodger.domain.Clue
import app.lodger.domain.ClueKind
import app.lodger.domain.Engine
import app.lodger.domain.Pos
import app.lodger.domain.Room
import app.lodger.ui.art.Ink
import app.lodger.ui.art.Rooms
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.lodger.ui.Home
import app.lodger.ui.Nav
import app.lodger.ui.Overlay
import app.lodger.ui.components.Btn
import app.lodger.ui.components.Kind
import app.lodger.ui.components.Label
import app.lodger.ui.components.Sheet
import app.lodger.ui.components.paper
import app.lodger.ui.components.tap
import app.lodger.ui.theme.C
import app.lodger.ui.theme.Hand
import app.lodger.ui.theme.Mono
import app.lodger.ui.theme.T
import kotlin.random.Random

/** Where the five clues hang, on a board 350 units wide (x, y, tilt). */
private val SLOTS = listOf(Triple(12f, 14f, -4f), Triple(176f, 30f, 3f), Triple(26f, 190f, 2f), Triple(188f, 210f, -3f), Triple(100f, 366f, 1f))
private const val CARD = 142f

@Composable
fun BoardScreen(h: Home, nav: Nav) {
    val l = h.lodger ?: return
    var open by remember { mutableStateOf<Clue?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Label(h.w.t("Доска", "Board"), Modifier.padding(bottom = 12.dp))
        BoxWithConstraints(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(C.corkEdge).padding(8.dp).background(C.cork)
                .drawBehind {
                    val r = Random(5)
                    repeat(900) { drawCircle(Color(0x22000000), 1.2f, Offset(r.nextFloat() * size.width, r.nextFloat() * size.height)) }
                },
        ) {
            val s = maxWidth.value / 350f
            fun dp(v: Float): Dp = (v * s).dp
            Box(Modifier.fillMaxWidth().height(dp(520f))) {
                val found = l.clues.withIndex().filter { it.value.night <= h.night }
                // Red thread from pin to pin, sagging a little.
                Canvas(Modifier.fillMaxSize()) {
                    val k = size.width / 350f
                    for (i in 1 until found.size) {
                        val (x1, y1) = SLOTS[found[i - 1].index].let { (it.first + CARD / 2) * k to it.second * k }
                        val (x2, y2) = SLOTS[found[i].index].let { (it.first + CARD / 2) * k to it.second * k }
                        drawPath(Path().apply { moveTo(x1, y1); quadraticTo((x1 + x2) / 2, maxOf(y1, y2) + 40 * k, x2, y2) }, C.thread, style = Stroke(1.8f * k))
                    }
                }
                for ((i, clue) in l.clues.withIndex()) {
                    val (x, y, tilt) = SLOTS.getOrElse(i) { SLOTS.last() }
                    if (clue.night <= h.night) {
                        Box(Modifier.offset(dp(x), dp(y)).width(dp(CARD)).rotate(tilt).tap { open = clue }) { Card(clue, s) }
                    } else {
                        Box(
                            Modifier.offset(dp(x), dp(y)).size(dp(CARD), dp(108f)).border(2.dp, Color(0x55000000), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text("?", style = T.Title.copy(color = Color(0x66000000))) }
                    }
                }
            }
        }
        val nc = Engine.clues(l, h.night).size
        val nw = h.h.words.size
        val ready = Engine.ready(l, h.h, h.night)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                h.w.t("Улики $nc/${l.clues.size} · Слова $nw/${l.words.size}", "Clues $nc/${l.clues.size} · Words $nw/${l.words.size}"),
                style = T.Small, modifier = Modifier.weight(1f),
            )
            Btn(if (h.h.solved) h.w.t("Перечитать", "Read again") else h.w.t("Сложить историю", "Piece it together"), kind = Kind.AMBER, enabled = ready || h.h.solved) {
                nav.open(Overlay.Truth)
            }
        }
        if (!ready && !h.h.solved) Text(
            when {
                nc < l.clues.size -> h.w.t("Не все улики найдены. Они появляются сами, ночь за ночью.", "Not every clue is here yet. They turn up by themselves, night after night.")
                else -> h.w.t("Не хватает слов из радио.", "Some words from the radio are missing.")
            },
            style = T.Small, modifier = Modifier.padding(top = 8.dp),
        )
    }
    Sheet(visible = open != null, onClose = { open = null }) {
        open?.let { c ->
            Label(kindName(h, c.kind))
            Column(Modifier.padding(top = 10.dp).fillMaxWidth().paper()) {
                Text(c.title, style = T.Title.copy(color = C.ink))
                Text(c.text, style = if (c.kind == ClueKind.LETTER) T.Note.copy(fontSize = 24.sp) else T.Body.copy(color = C.ink), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun kindName(h: Home, k: ClueKind) = when (k) {
    ClueKind.PHOTO -> h.w.t("Фотография", "Photograph"); ClueKind.LETTER -> h.w.t("Письмо", "Letter"); ClueKind.TICKET -> h.w.t("Бумага", "Paper")
    ClueKind.OBJECT -> h.w.t("Предмет", "Object"); ClueKind.DATE -> h.w.t("Документ", "Record"); ClueKind.ADDRESS -> h.w.t("Справка", "Enquiry")
}

/** A clue as it hangs on the board: a print, a letter, a ticket, a tagged object, a typed card. */
@Composable
private fun Card(c: Clue, s: Float) {
    val small = (12 * s).coerceAtLeast(10f).sp
    Box {
        Column(
            Modifier.fillMaxWidth().shadow(4.dp).background(
                when (c.kind) {
                    ClueKind.PHOTO -> Color(0xFFEFE7D5); ClueKind.LETTER -> C.paper; ClueKind.TICKET -> Color(0xFFE6C7B0)
                    ClueKind.OBJECT -> Color(0xFFC9A874); ClueKind.DATE, ClueKind.ADDRESS -> Color(0xFFF1EAD8)
                },
            ).padding((8 * s).dp),
        ) {
            when (c.kind) {
                ClueKind.PHOTO -> {
                    Print(c.title.hashCode(), Modifier.fillMaxWidth().height((84 * s).dp))
                    Text(c.title, style = T.Note.copy(fontSize = (17 * s).sp, color = C.ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                ClueKind.LETTER -> {
                    Text(c.title, style = T.Small.copy(fontWeight = FontWeight.Bold, color = Color(0xFF23324A), fontSize = small))
                    Text(c.text, style = T.Note.copy(fontFamily = Hand, fontSize = (16 * s).sp, lineHeight = (17 * s).sp, color = Color(0xFF23324A)), maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
                ClueKind.DATE, ClueKind.ADDRESS -> {
                    Text(c.title, style = T.Small.copy(fontWeight = FontWeight.Bold, color = C.ink, fontSize = small))
                    Text(c.text, style = T.Stamp.copy(color = C.ink, fontFamily = Mono, fontSize = (10.5f * s).sp, lineHeight = (13 * s).sp), maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
                else -> {
                    Text(c.title, style = T.Small.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2B1D10), fontSize = small))
                    Text(c.text, style = T.Small.copy(color = Color(0xFF2B1D10), fontSize = (11.5f * s).sp, lineHeight = (15 * s).sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // The pin.
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-5).dp).size(12.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFE46A54), Color(0xFF7A1F14)), center = Offset(12f, 12f))),
        )
    }
}

/** An old print: one of the drawn rooms, sepia, with someone standing in it. */
@Composable
private fun Print(seed: Int, modifier: Modifier) {
    val img = remember(seed) {
        val k = Math.floorMod(seed, 15)
        val b = Rooms.draw(Room.entries[k % Room.entries.size], 360)
        Ink.composite(b, Ink.figure(b.width, b.height, Pos(.3f + (k % 3) * .2f, .5f), .55f)).asImageBitmap()
    }
    Image(img, null, modifier, contentScale = ContentScale.Crop, colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply {
        setToSaturation(0f)
        timesAssign(ColorMatrix(floatArrayOf(1.25f, 0f, 0f, 0f, 18f, 0f, 1.05f, 0f, 0f, 10f, 0f, 0f, .8f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    }))
}
