package app.lodger.ui.flat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import app.lodger.domain.Engine
import app.lodger.domain.Room
import app.lodger.ui.Home
import app.lodger.ui.components.Btn
import app.lodger.ui.components.Gap
import app.lodger.ui.components.Glyph
import app.lodger.ui.components.Icon
import app.lodger.ui.components.Kind
import app.lodger.ui.components.Label
import app.lodger.ui.components.PhotoView
import app.lodger.ui.components.rememberCamera
import app.lodger.ui.components.tap
import app.lodger.ui.theme.C
import app.lodger.ui.theme.T

/** Before anyone moves in: the rooms are photographed, and any the flat doesn't have are crossed out. */
@Composable
fun SetupScreen(h: Home, onDone: () -> Unit) {
    val c = LocalContext.current.container
    val camera = rememberCamera { room, rel -> c.write { photo(room, rel) } }
    val count = h.h.photos.size
    Column(Modifier.fillMaxSize().background(C.shell).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Label(h.w.t("Перед заселением", "Before anyone moves in"))
            Text(h.w.t("Сфотографируй комнаты", "Photograph your rooms"), style = T.Display, modifier = Modifier.padding(top = 6.dp))
            Text(
                h.w.t(
                    "Так, как они выглядят вечером: при твоём свете, с твоими вещами. Здесь будут появляться следы. Хватит двух комнат, но чем больше — тем теснее.",
                    "As they look in the evening: in your own light, with your own things. This is where the marks will appear. Two rooms are enough, but the more, the closer it gets.",
                ),
                style = T.Body.copy(color = C.dim), modifier = Modifier.padding(top = 10.dp),
            )
            Gap(18.dp)
            for (r in Room.entries) {
                val photo = h.h.photos[r]
                val absent = r in h.h.absent
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(96.dp, 72.dp).clip(RoundedCornerShape(3.dp)).border(2.dp, if (photo != null) C.paper else C.line, RoundedCornerShape(3.dp))
                            .padding(2.dp).tap { if (!absent) camera.shoot(r) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (photo != null) PhotoView(photo.file, emptyList(), Modifier.fillMaxSize(), max = 320)
                        else Glyph(if (absent) Icon.CLOSE else Icon.CAMERA, 24.dp, C.faint)
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(h.w.room(r), style = T.Heading.copy(color = if (absent) C.faint else C.text))
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (!absent) Text(
                                if (photo == null) h.w.t("Снять", "Shoot") else h.w.t("Переснять", "Retake"),
                                style = T.Small.copy(color = C.amber), modifier = Modifier.tap { camera.shoot(r) }.padding(vertical = 6.dp),
                            )
                            if (!absent) Text(h.w.t("Из галереи", "Gallery"), style = T.Small, modifier = Modifier.tap { camera.pick(r) }.padding(vertical = 6.dp))
                            Text(
                                if (absent) h.w.t("Вернуть", "Bring back") else h.w.t("Нет такой", "Don't have one"),
                                style = T.Small, modifier = Modifier.tap { c.write { absent(r, !absent) } }.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            Text(h.w.t("Фото остаются только в этом телефоне.", "Photos never leave this phone."), style = T.Small, modifier = Modifier.padding(top = 12.dp))
        }
        Btn(
            if (count >= Engine.MIN_PHOTOS) h.w.t("Готово. Пусть заселяется", "Done. Let them move in") else h.w.t("Нужно ещё ${Engine.MIN_PHOTOS - count} фото", if (Engine.MIN_PHOTOS - count == 1) "One more photo needed" else "${Engine.MIN_PHOTOS - count} more photos needed"),
            Modifier.fillMaxWidth().padding(16.dp), Kind.AMBER, enabled = count >= Engine.MIN_PHOTOS, onClick = onDone,
        )
    }
}
