package app.lodger.ui.flat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lodger.container
import app.lodger.data.Fear
import app.lodger.domain.Engine
import app.lodger.domain.Room
import app.lodger.ui.Home
import app.lodger.ui.Nav
import app.lodger.ui.Overlay
import app.lodger.ui.components.Gap
import app.lodger.ui.components.Glyph
import app.lodger.ui.components.Icon
import app.lodger.ui.components.Label
import app.lodger.ui.components.PhotoView
import app.lodger.ui.components.panel
import app.lodger.ui.components.rememberCamera
import app.lodger.ui.components.tap
import app.lodger.ui.theme.C
import app.lodger.ui.theme.T

/** A room's marks first seen tonight are developed slowly; after that they are simply there. */
fun devKey(h: Home, r: Room) = "dev:${h.lodger?.id}:${r.key}:${h.night}"

@Composable
fun FlatScreen(h: Home, nav: Nav) {
    val c = LocalContext.current.container
    val l = h.lodger ?: return
    val camera = rememberCamera { room, rel -> c.write { photo(room, rel) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        val last = Engine.traces(l, h.night).maxByOrNull { it.night }
        Column(Modifier.fillMaxWidth().panel().tap { last?.let { nav.open(Overlay.RoomView(Engine.home(it.room, h.h))) } }.padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (last != null) {
                val room = Engine.home(last.room, h.h)
                val hidden = last.night == h.night && devKey(h, room) !in h.h.seen
                Label(h.w.t("Ночь ${last.night} · ", "Night ${last.night} · ") + h.w.room(room))
                Text(
                    if (hidden) h.w.t("Там что-то не так. Открой снимок.", "Something is wrong in there. Open the photo.") else last.text,
                    style = T.Italic, modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Label(h.w.night(h.night, Engine.NIGHTS))
                Text(h.w.t("Пока тихо. Слишком тихо для новой квартиры.", "Quiet so far. Too quiet for a new flat."), style = T.Italic, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Gap(18.dp)
        Label(h.w.t("Комнаты", "Rooms"), Modifier.padding(bottom = 10.dp))
        val rooms = h.rooms
        for (row in rooms.chunked(2)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (r in row) Tile(h, r, Modifier.weight(1f), onOpen = { nav.open(Overlay.RoomView(r)) }, onShoot = { camera.shoot(r) })
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Tile(h: Home, r: Room, modifier: Modifier, onOpen: () -> Unit, onShoot: () -> Unit) {
    val photo = h.h.photos[r]
    val marks = h.marks(r)
    val fresh = marks.any { it.first.night == h.night } && devKey(h, r) !in h.h.seen
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(4 / 3f).clip(RoundedCornerShape(3.dp)).border(3.dp, C.paper, RoundedCornerShape(3.dp)).padding(3.dp)
                .tap(if (photo == null) onShoot else onOpen),
        ) {
            if (photo != null) {
                // Tonight's marks stay hidden on the tile until the photo has been opened.
                val shown = if (fresh) marks.filter { it.first.night < h.night } else marks
                PhotoView(photo.file, shown, Modifier.fillMaxSize(), flicker = h.settings.fear != Fear.CANDLE && !h.still, max = 480)
            } else {
                Column(Modifier.fillMaxSize().background(C.panel), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph(Icon.CAMERA, 28.dp, C.dim)
                    Text(h.w.t("Снять", "Shoot"), style = T.Small, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (fresh && photo != null) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(9.dp).clip(CircleShape).background(C.red))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(h.w.room(r), style = T.Body.copy(fontSize = T.Small.fontSize * 1.05f), modifier = Modifier.weight(1f))
            Text(
                when {
                    photo == null -> h.w.t("нет фото", "no photo")
                    fresh -> h.w.t("что-то изменилось", "something changed")
                    marks.isEmpty() -> h.w.t("пусто", "nothing")
                    else -> h.w.t("следов: ${marks.size}", "marks: ${marks.size}")
                },
                style = T.Small.copy(color = if (fresh) C.red else C.dim),
            )
        }
    }
}
