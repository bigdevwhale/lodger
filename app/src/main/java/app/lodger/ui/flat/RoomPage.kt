package app.lodger.ui.flat

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lodger.container
import app.lodger.data.Fear
import app.lodger.domain.Look
import app.lodger.domain.Pos
import app.lodger.domain.Room
import app.lodger.domain.Spot
import app.lodger.ui.Home
import app.lodger.ui.Nav
import app.lodger.ui.components.Btn
import app.lodger.ui.components.Gap
import app.lodger.ui.components.Kind
import app.lodger.ui.components.Label
import app.lodger.ui.components.Page
import app.lodger.ui.components.PhotoView
import app.lodger.ui.components.rememberCamera
import app.lodger.ui.components.rememberPhoto
import app.lodger.ui.theme.C
import app.lodger.ui.theme.T
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

@Composable
fun RoomPage(h: Home, r: Room, nav: Nav) {
    val ctx = LocalContext.current
    val c = ctx.container
    val camera = rememberCamera { room, rel -> c.write { photo(room, rel) } }
    val photo = h.h.photos[r]
    val marks = h.marks(r)
    val key = devKey(h, r)
    // Remembered once per opening, so the marks don't stop developing when the "seen" mark is written.
    val develop = remember(key) { if (key in h.h.seen) emptySet() else marks.map { it.first }.filter { it.night == h.night }.toSet() }
    var revealed by remember(key) { mutableStateOf(develop.isEmpty()) }
    LaunchedEffect(key, photo?.file) { if (photo != null && develop.isNotEmpty()) { delay(3500); revealed = true; c.seen(key) } }
    val bmp by rememberPhoto(photo?.file, 1280)
    val flash = if (h.settings.fear == Fear.MOON && !h.still && photo != null)
        remember(photo.file) { (photo.spots[Spot.DARK] ?: Look.fallback(Spot.DARK)).let { Pos(it.first, it.second) } } else null

    Page(h.w.room(r), onClose = nav.close) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (photo != null) {
                val b = bmp
                val ratio = if (b != null) b.width / b.height.toFloat() else 4 / 3f
                PhotoView(
                    photo.file, marks,
                    Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(3.dp)).border(4.dp, C.paper, RoundedCornerShape(3.dp)).padding(4.dp),
                    develop = develop, flicker = h.settings.fear != Fear.CANDLE && !h.still, flash = flash,
                    onFlash = {
                        c.sfx.thud()
                        runCatching { ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(60, 200)) }
                    },
                )
                val at = Instant.ofEpochMilli(photo.at).atZone(ZoneId.systemDefault())
                Text(
                    h.w.t("СНЯТО ", "TAKEN ") + h.w.date(photo.at).uppercase() + " · " + h.w.clock(at.hour * 60 + at.minute) + " · " + h.w.night(h.night, 14).uppercase(),
                    style = T.Stamp.copy(color = C.dim), modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(4 / 3f).border(1.dp, C.line, RoundedCornerShape(3.dp)))
                Text(h.w.shootRoom(r), style = T.Italic, modifier = Modifier.padding(top = 10.dp))
            }
            Gap(10.dp)
            val list = marks.map { it.first }.sortedByDescending { it.night }
            if (list.isEmpty()) Text(h.w.t("На снимке ничего необычного. Пока.", "Nothing unusual in the photo. Yet."), style = T.Italic, modifier = Modifier.padding(vertical = 10.dp))
            for (t in list) {
                Box(Modifier.fillMaxWidth().height(1.dp).border(1.dp, C.line))
                Column(Modifier.padding(vertical = 10.dp)) {
                    Label(h.w.t("Ночь ${t.night}", "Night ${t.night}"))
                    Text(
                        if (t in develop && !revealed) h.w.t("…что-то проявляется.", "…something is coming through.") else t.text,
                        style = T.Italic, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Gap(14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(if (photo == null) h.w.t("Сфотографировать", "Take a photo") else h.w.t("Переснять", "Retake"), Modifier.weight(1f), Kind.AMBER) { camera.shoot(r) }
                Btn(h.w.t("Из галереи", "From gallery"), Modifier.weight(1f), Kind.GHOST) { camera.pick(r) }
            }
            val others = h.h.photos.keys.count { it != r }
            if (others >= 1) {
                Btn(h.w.t("В моей квартире такой нет", "My flat has no such room"), Modifier.fillMaxWidth().padding(top = 10.dp), Kind.GHOST) {
                    c.write { absent(r, true) }; nav.close()
                }
            }
            Text(
                h.w.t("Фото остаются только в этом телефоне.", "Photos never leave this phone."),
                style = T.Small, modifier = Modifier.padding(top = 14.dp, bottom = 24.dp),
            )
        }
    }
}
