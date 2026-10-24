package app.lodger.ui.house

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.core.os.LocaleListCompat
import app.lodger.BuildConfig
import app.lodger.container
import app.lodger.data.Fear
import app.lodger.domain.Engine
import app.lodger.domain.Phase
import app.lodger.ui.Home
import app.lodger.ui.Nav
import app.lodger.ui.Overlay
import app.lodger.ui.components.Btn
import app.lodger.ui.components.CommunityLinks
import app.lodger.ui.components.Gap
import app.lodger.ui.components.Glyph
import app.lodger.ui.components.Icon
import app.lodger.ui.components.Kind
import app.lodger.ui.components.Label
import app.lodger.ui.components.Page
import app.lodger.ui.components.panel
import app.lodger.ui.components.paper
import app.lodger.ui.notes.Scrap
import app.lodger.ui.theme.C
import app.lodger.ui.theme.T
import java.time.temporal.ChronoUnit

/** Between lodgers, and after the last one: the flat stands empty. */
@Composable
fun EmptyScreen(h: Home, nav: Nav) {
    Column(Modifier.fillMaxSize().background(C.shell).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("LODGER", style = T.Brand, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { nav.open(Overlay.Settings) }, contentAlignment = Alignment.Center) { Glyph(Icon.GEAR, 22.dp, C.dim) }
        }
        Column(Modifier.weight(1f).padding(28.dp), verticalArrangement = Arrangement.Center) {
            when (val p = h.phase) {
                is Phase.Empty -> {
                    val n = ChronoUnit.DAYS.between(Engine.evening(h.now, h.zone), Engine.firstEvening(p.until, h.zone)).toInt().coerceAtLeast(0)
                    Label(h.w.t("Квартира пуста", "The flat is empty"))
                    Text(h.w.t("Тихо. Слишком тихо.", "Quiet. Too quiet."), style = T.Display, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        if (n <= 0) h.w.t("Кто-то въезжает сегодня вечером.", "Someone moves in this evening.")
                        else h.w.t("Новый жилец въедет через ${h.w.nights(n)}.", "A new lodger moves in in ${h.w.nights(n)}."),
                        style = T.Italic.copy(color = C.dim), modifier = Modifier.padding(top = 12.dp),
                    )
                }
                else -> {
                    Label(h.w.t("Дом затих", "The house is still"))
                    Text(h.w.t("Все жильцы ушли.", "Every lodger has gone."), style = T.Display, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        h.w.t("Их истории записаны в домовой книге. Иногда по ночам всё ещё скрипит пол — но это просто старый дом.", "Their stories are written in the house register. The floor still creaks some nights, but that's just an old house."),
                        style = T.Italic.copy(color = C.dim), modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            Btn(h.w.t("Домовая книга", "House register"), Modifier.padding(top = 28.dp), Kind.GHOST) { nav.open(Overlay.Register) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPage(h: Home, nav: Nav) {
    val c = LocalContext.current.container
    val s = h.settings
    Page(h.w.t("Настройки", "Settings"), onClose = nav.close) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Label(h.w.t("Страх", "Fear"))
            for (f in Fear.entries) Choice(h.w.fear(f), h.w.fearHint(f), s.fear == f) { c.write { store.settings { it.copy(fear = f) } } }
            Gap(18.dp)
            Label(h.w.t("Записки по вечерам", "Notes in the evening"))
            Text(
                h.w.t("Записки, написанные ночью или днём, приходят в это время. Ночью телефон молчит.", "Notes written at night or by day arrive at this time. The phone stays silent at night."),
                style = T.Small, modifier = Modifier.padding(top = 4.dp),
            )
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in listOf(19 * 60, 20 * 60 + 30, 21 * 60 + 30, 22 * 60 + 30, -1)) {
                    Chip(if (m < 0) h.w.t("нет", "off") else h.w.clock(m), s.evening == m) { c.write { store.settings { it.copy(evening = m) } } }
                }
            }
            Gap(18.dp)
            Label(h.w.t("Звук", "Sound"))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(h.w.t("Звук", "Sound"), s.sound) { c.write { store.settings { it.copy(sound = !it.sound) } } }
                Chip(h.w.t("Голос в радио", "Voice on the radio"), s.voice) { c.write { store.settings { it.copy(voice = !it.voice) } } }
            }
            Gap(18.dp)
            Label(h.w.t("Язык", "Language"))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Русский", h.ru) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru")) }
                Chip("English", !h.ru) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
            }
            Gap(22.dp)
            Label(h.w.t("Сообщество", "Community"))
            CommunityLinks(h.w)
            Gap(18.dp)
            Btn(h.w.t("Домовая книга", "House register"), Modifier.fillMaxWidth(), Kind.GHOST) { nav.open(Overlay.Register) }
            if (h.absentRooms.isNotEmpty()) {
                Gap(10.dp)
                Btn(h.w.t("Вернуть комнаты: ", "Bring rooms back: ") + h.absentRooms.joinToString { h.w.room(it).lowercase() }, Modifier.fillMaxWidth(), Kind.GHOST) {
                    c.write { for (r in h.absentRooms) absent(r, false) }
                }
            }
            Gap(10.dp)
            Btn(h.w.t("Показать подсказки заново", "Show the hints again"), Modifier.fillMaxWidth(), Kind.GHOST) { c.write { unseen("coach:") } }
            if (BuildConfig.DEMO && h.lodger != null) {
                Gap(10.dp)
                // Demo builds only: jump to the last night with every word caught, to try the ending.
                Btn(h.w.t("Демо: последняя ночь", "Demo: last night"), Modifier.fillMaxWidth(), Kind.GHOST) {
                    c.write { lastNight() }; nav.close()
                }
            }
            if (h.phase is Phase.Empty) {
                Gap(10.dp)
                Btn(h.w.t("Впустить жильца сейчас", "Let the next lodger in now"), Modifier.fillMaxWidth(), Kind.GHOST) { c.write { callNext() }; nav.close() }
            }
            Text(
                h.w.t(
                    "Lodger работает без интернета. Фото комнат хранятся только в этом телефоне и никуда не отправляются.",
                    "Lodger works offline. Room photos are kept only on this phone and are never sent anywhere.",
                ) + "\n" + h.w.t("Версия ", "Version ") + BuildConfig.VERSION_NAME,
                style = T.Small, modifier = Modifier.padding(top = 22.dp, bottom = 28.dp),
            )
        }
    }
}

@Composable
private fun Choice(title: String, hint: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, if (on) C.amber else C.line, RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(14.dp)) {
        Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(if (on) C.amber else C.line))
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = T.Heading.copy(color = if (on) C.amberHi else C.text))
            Text(hint, style = T.Small, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) C.amber.copy(alpha = .18f) else C.panel)
            .border(1.dp, if (on) C.amber else C.line, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(text, style = T.Small.copy(color = if (on) C.amberHi else C.text)) }
}

/** The house register: everyone who has lived here and gone. */
@Composable
fun RegisterPage(h: Home, nav: Nav) {
    val gone = Engine.register(h.h, h.lodgers)
    Page(h.w.t("Домовая книга", "House register"), onClose = nav.close) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (gone.isEmpty()) Text(
                h.w.t("Пока здесь никого. Сюда записывают тех, кто съехал.", "Nobody yet. This is where those who have left are written down."),
                style = T.Italic.copy(color = C.dim), modifier = Modifier.padding(top = 24.dp),
            )
            for ((i, e) in gone.withIndex()) {
                val (l, at) = e
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).panel().clickable { nav.open(Overlay.Entry(l.id)) }.padding(14.dp)) {
                    Text("${i + 1}.", style = T.Stamp.copy(color = C.dim), modifier = Modifier.padding(top = 3.dp, end = 10.dp))
                    Column {
                        Text(l.name, style = T.Heading)
                        Text("${l.kind} · ${l.years}", style = T.Small)
                        Text(h.w.t("Дата выбытия: ", "Left ") + h.w.date(at), style = T.Stamp.copy(color = C.dim), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EntryPage(h: Home, id: String, nav: Nav) {
    val l = h.lodgers[id] ?: return
    Page(l.name, onClose = nav.close) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Column(Modifier.fillMaxWidth().paper()) {
                Text(l.kind.uppercase(), style = T.Stamp)
                Text(l.name, style = T.Title.copy(color = C.ink))
                Text("${l.years} · ${l.role}", style = T.Italic.copy(color = C.inkSoft), modifier = Modifier.padding(bottom = 10.dp))
                Text(l.truth, style = T.Body.copy(color = C.ink))
                Text(l.task.title.uppercase(), style = T.Stamp.copy(color = C.red), modifier = Modifier.padding(top = 14.dp))
                Text(l.farewell.replace("{name}", h.h.names[l.id].orEmpty()), style = T.Italic.copy(color = C.ink), modifier = Modifier.padding(top = 4.dp))
            }
            Gap(18.dp)
            Label(h.w.t("Записки", "Notes"))
            for (n in l.notes) Scrap(h, n, fresh = false)
            Gap(10.dp)
            Label(h.w.t("Улики", "Clues"))
            for (c in l.clues) Column(Modifier.padding(top = 10.dp).fillMaxWidth().panel().padding(14.dp)) {
                Text(c.title, style = T.Heading)
                Text(c.text, style = T.Body.copy(color = C.dim), modifier = Modifier.padding(top = 4.dp))
            }
            Text(l.words.joinToString(" "), style = T.Note.copy(color = C.paper), modifier = Modifier.padding(vertical = 20.dp))
        }
    }
}
