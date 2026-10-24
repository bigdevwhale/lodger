package app.lodger.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.lodger.BuildConfig
import app.lodger.container
import app.lodger.data.Fear
import app.lodger.data.Settings
import app.lodger.domain.Engine
import app.lodger.domain.House
import app.lodger.domain.Lodger
import app.lodger.domain.Lodgers
import app.lodger.domain.Phase
import app.lodger.domain.Room
import app.lodger.ru
import app.lodger.ui.board.BoardScreen
import app.lodger.ui.board.TruthPage
import app.lodger.ui.components.Btn
import app.lodger.ui.components.Glyph
import app.lodger.ui.components.Icon
import app.lodger.ui.components.Kind
import app.lodger.ui.components.Sheet
import app.lodger.ui.components.paper
import app.lodger.ui.flat.FlatScreen
import app.lodger.ui.flat.RoomPage
import app.lodger.ui.flat.SetupScreen
import app.lodger.ui.house.EmptyScreen
import app.lodger.ui.house.EntryPage
import app.lodger.ui.house.RegisterPage
import app.lodger.ui.house.SettingsPage
import app.lodger.ui.notes.NotesScreen
import app.lodger.ui.radio.RadioScreen
import app.lodger.ui.story.Line
import app.lodger.ui.story.Sequence
import app.lodger.ui.theme.C
import app.lodger.ui.theme.T
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/** Everything a screen reads, at one moment. */
class Home(val h: House, val settings: Settings, val w: Words, val lodgers: Lodgers, val now: Long, val still: Boolean = BuildConfig.SHOTS) {
    val ru get() = w.ru
    val zone: ZoneId = ZoneId.systemDefault()
    val phase = Engine.phase(h, lodgers, now, zone)
    private val living = phase as? Phase.Living
    val lodger: Lodger? = living?.lodger
    val night: Int = living?.night ?: 0

    fun marks(r: Room) = lodger?.let { Engine.place(Engine.marks(it, h, r, night), h.photos[r]) }.orEmpty()
    /** Rooms the flat has, in order. */
    val rooms get() = Room.entries.filter { it !in h.absent }
    val absentRooms get() = Room.entries.filter { it in h.absent }
}

enum class Tab(val key: String) { FLAT("flat"), NOTES("notes"), RADIO("radio"), BOARD("board") }

sealed interface Overlay {
    data class RoomView(val room: Room) : Overlay
    data object Truth : Overlay
    data object Settings : Overlay
    data object Register : Overlay
    data class Entry(val id: String) : Overlay
}

/** What the screens can ask the root to do. */
class Nav(
    val tab: (Tab) -> Unit,
    val open: (Overlay) -> Unit,
    val close: () -> Unit,
    val farewell: (Lodger, String?) -> Unit,
)

@Composable
fun LodgerRoot(house: House, settings: Settings, toNotes: Int) {
    val ctx = LocalContext.current
    val c = ctx.container
    val words = remember(ctx.ru) { Words(ctx.ru) }
    val lodgers = remember(ctx.ru) { c.lodgers(ctx.ru) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    LaunchedEffect(settings.sound) { c.sfx.on = settings.sound }
    val home = Home(house, settings, words, lodgers, now)

    var tab by remember { mutableStateOf(Tab.FLAT) }
    LaunchedEffect(toNotes) { if (toNotes > 0) tab = Tab.NOTES }
    val overlays = remember { mutableStateListOf<Overlay>() }
    var farewell by remember { mutableStateOf<Pair<Lodger, String?>?>(null) }
    val nav = Nav(
        tab = { overlays.clear(); tab = it },
        open = { overlays.add(it) },
        close = { if (overlays.isNotEmpty()) overlays.removeAt(overlays.lastIndex) },
        farewell = { l, name -> overlays.clear(); farewell = l to name },
    )
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // The house shakes a little when someone knocks.
    val shake = remember { Animatable(0f) }
    val phase = home.phase
    LaunchedEffect(phase is Phase.Living, settings.fear) {
        if (phase !is Phase.Living || settings.fear == Fear.CANDLE || home.still) return@LaunchedEffect
        val hour = LocalTime.now().hour
        if (hour in 6 until Engine.EVENING) return@LaunchedEffect
        delay(45_000L + Random.nextLong(105_000L))
        c.sfx.knock()
        for (x in listOf(6f, -5f, 4f, -2f, 0f)) shake.animateTo(x, tween(50))
    }

    Box(Modifier.fillMaxSize().background(C.shell).offset { IntOffset(shake.value.dp.roundToPx(), 0) }) {
        when {
            "welcome" !in house.seen -> Sequence(
                listOf(
                    Line(words.t("Новая квартира", "A new flat"), label = true),
                    Line(words.t("Коробки ещё не разобраны. Пахнет чужим.", "The boxes aren't unpacked yet. It smells of someone else.")),
                    Line(words.t("Говорят, в старых домах у каждой квартиры есть жилец, который не съехал.", "They say every flat in an old house has a lodger who never moved out.")),
                    Line(words.t("Сфотографируй свои комнаты — как есть, вечером, при своём свете.", "Photograph your rooms — as they are, in the evening, in your own light.")),
                ),
                words.t("Взять телефон", "Take the phone"),
            ) { c.write { seen("welcome") } }
            phase == Phase.Settling -> SetupScreen(home) { c.write { moveIn() } }
            phase is Phase.Living && "intro:${phase.lodger.id}" !in house.seen -> Sequence(
                listOf(
                    Line(words.t("Ночь 1", "Night 1"), label = true),
                    Line(phase.lodger.intro),
                    Line(words.t("В квартире кто-то есть.", "Someone is in the flat.")),
                ),
                words.t("Остаться", "Stay"),
            ) {
                c.write { seen("intro:${phase.lodger.id}") }
                if (Build.VERSION.SDK_INT >= 33 && settings.evening >= 0) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            phase is Phase.Living -> Main(home, tab, nav, overlays.lastOrNull())
            phase is Phase.Empty -> EmptyScreen(home, nav)
            else -> EmptyScreen(home, nav)
        }
        when (val o = overlays.lastOrNull()) {
            Overlay.Settings -> SettingsPage(home, nav)
            Overlay.Register -> RegisterPage(home, nav)
            is Overlay.Entry -> EntryPage(home, o.id, nav)
            else -> Unit
        }
        farewell?.let { (l, name) ->
            Sequence(
                listOf(Line(l.farewell.replace("{name}", name ?: "")), Line(l.finale.ifEmpty { words.t("Квартира пуста.", "The flat is empty.") }, label = true)),
                words.t("Дальше", "Go on"),
            ) { farewell = null }
        }
    }
}

@Composable
private fun Main(h: Home, tab: Tab, nav: Nav, overlay: Overlay?) {
    val c = LocalContext.current.container
    val l = h.lodger ?: return
    val fresh = Engine.fresh(l, h.h, h.night)
    fun key(t: Tab) = "tab:${t.key}:${l.id}:${h.night}"
    LaunchedEffect(tab, h.night, l.id) { c.seen(key(tab)) }
    BackToFlat(overlay == null && tab != Tab.FLAT, nav)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("LODGER", style = T.Brand)
            Column(Modifier.weight(1f).padding(end = 8.dp), horizontalAlignment = Alignment.End) {
                Text(if (h.h.solved) "${l.kind} · ${l.name}" else h.w.t("Жилец: неизвестен", "Lodger: unknown"), style = T.Small.copy(color = C.text), maxLines = 1)
                Text(h.w.night(h.night, Engine.NIGHTS), style = T.Small)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { nav.open(Overlay.Settings) }, contentAlignment = Alignment.Center) { Glyph(Icon.GEAR, 22.dp, C.dim) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.FLAT -> FlatScreen(h, nav)
                Tab.NOTES -> NotesScreen(h)
                Tab.RADIO -> RadioScreen(h)
                Tab.BOARD -> BoardScreen(h, nav)
            }
        }
        Row(Modifier.fillMaxWidth().background(C.nav).navigationBarsPadding()) {
            for (t in Tab.entries) {
                val sel = t == tab
                val col = if (sel) C.amber else C.dim
                Column(
                    Modifier.weight(1f).clickable { c.sfx.tap(); nav.tab(t) }.padding(top = 10.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Glyph(when (t) { Tab.FLAT -> Icon.HOUSE; Tab.NOTES -> Icon.NOTE; Tab.RADIO -> Icon.RADIO; Tab.BOARD -> Icon.BOARD }, 24.dp, col)
                        if (!sel && fresh[t.key] == true && key(t) !in h.h.seen) Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-2).dp).size(8.dp).clip(CircleShape).background(C.red))
                    }
                    Text(
                        when (t) { Tab.FLAT -> h.w.t("Квартира", "Flat"); Tab.NOTES -> h.w.t("Записки", "Notes"); Tab.RADIO -> h.w.t("Радио", "Radio"); Tab.BOARD -> h.w.t("Доска", "Board") },
                        style = T.Small.copy(color = col), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
    when (overlay) {
        is Overlay.RoomView -> RoomPage(h, overlay.room, nav)
        Overlay.Truth -> TruthPage(h, nav)
        else -> Unit
    }
    val coachKey = when (overlay) { is Overlay.RoomView -> "room"; null -> tab.key; else -> null }
    Coach(h, coachKey)
}

/** The previous tenants explain a screen the first time it opens, in a note left behind. */
@Composable
fun Coach(h: Home, screen: String?) {
    val c = LocalContext.current.container
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(screen, h.h.seen) {
        shown = null
        if (screen != null && "coach:$screen" !in h.h.seen) { delay(600); shown = screen }
    }
    val lines = shown?.let { h.w.coach(it) }.orEmpty()
    fun done() { shown?.let { k -> c.write { seen("coach:$k") } }; shown = null }
    Sheet(visible = lines.isNotEmpty(), onClose = ::done) {
        Text(h.w.t("Записка от прежних жильцов", "A note from the previous tenants").uppercase(), style = T.Label)
        if (lines.isNotEmpty()) {
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(2.dp)).paper()) {
                Text(lines[0], style = T.Note.copy(fontSize = T.Note.fontSize * 1.1f))
                for (l in lines.drop(1)) Text(l, style = T.Body.copy(color = C.ink), modifier = Modifier.padding(top = 8.dp))
            }
        }
        Btn(h.w.t("Понятно", "Got it"), Modifier.fillMaxWidth().padding(top = 16.dp), Kind.AMBER) { done() }
    }
}

/** A modest back handler for tab screens: back goes to the flat first. */
@Composable
fun BackToFlat(enabled: Boolean, nav: Nav) = BackHandler(enabled) { nav.tab(Tab.FLAT) }
