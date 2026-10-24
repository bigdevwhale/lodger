package app.lodger

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.lodger.data.DemoSeed
import app.lodger.domain.Room
import app.lodger.ui.Home
import app.lodger.ui.Nav
import app.lodger.ui.Words
import app.lodger.ui.board.BoardScreen
import app.lodger.ui.board.TruthPage
import app.lodger.ui.flat.FlatScreen
import app.lodger.ui.flat.RoomPage
import app.lodger.ui.flat.SetupScreen
import app.lodger.ui.house.EmptyScreen
import app.lodger.ui.house.SettingsPage
import app.lodger.ui.notes.NotesScreen
import app.lodger.ui.radio.RadioScreen
import app.lodger.ui.story.Line
import app.lodger.ui.story.Sequence
import app.lodger.ui.theme.C
import app.lodger.ui.theme.LodgerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Every main screen over the demo flat, drawn to PNGs (run with `-Prender`), in both languages. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreensBase {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    abstract val lang: String
    private val ru get() = lang == "ru"
    private val out by lazy { File(System.getProperty("render.out") ?: "build/art", "screens-$lang").apply { mkdirs() } }
    private val nav = Nav({}, {}, {}, { _, _ -> })

    private fun home(change: (app.lodger.domain.House) -> app.lodger.domain.House = { it }): Home = runBlocking {
        val c = ApplicationProvider.getApplicationContext<LodgerApp>().container
        DemoSeed.seedIfEmpty(c, shots = true)
        val h = c.store.change(change)
        Home(h, c.store.settingsNow(), Words(ru), c.lodgers(ru), System.currentTimeMillis(), still = true)
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { LodgerTheme { Box(Modifier.fillMaxSize().background(C.shell)) { content() } } }
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        // Photos load off the main thread.
        Thread.sleep(600)
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            File(out, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun words() = Words(ru)

    @Test fun welcome() = shot("0-welcome") {
        Sequence(listOf(Line(words().t("Новая квартира", "A new flat"), true), Line(words().t("Говорят, в старых домах у каждой квартиры есть жилец, который не съехал.", "They say every flat in an old house has a lodger who never moved out."))), words().t("Взять телефон", "Take the phone"), still = true) {}
    }
    @Test fun setup() = home { it.copy(arrived = 0, photos = it.photos - Room.BATH - Room.BALCONY, absent = setOf(Room.BALCONY)) }.let { h -> shot("1-setup") { SetupScreen(h) {} } }
    @Test fun flat() = home { it.copy(seen = it.seen + "dev:teacher:room:${DemoSeed.NIGHT}") }.let { h -> shot("2-flat") { FlatScreen(h, nav) } }
    @Test fun room() = home { it.copy(seen = it.seen + "dev:teacher:room:${DemoSeed.NIGHT}") }.let { h -> shot("3-room") { RoomPage(h, Room.ROOM, nav) } }
    @Test fun hall() = home().let { h -> shot("3-hall") { RoomPage(h, Room.HALL, nav) } }
    @Test fun notes() = home().let { h -> shot("4-notes") { NotesScreen(h) } }
    @Test fun radio() = home().let { h -> shot("5-radio") { RadioScreen(h) } }
    @Test fun board() = home().let { h -> shot("6-board") { BoardScreen(h, nav) } }
    @Test fun truth() = home { it.copy(words = setOf(0, 1, 2, 3, 4, 5), solved = true, arrived = it.arrived - 4 * 86_400_000L) }.let { h -> shot("7-truth") { TruthPage(h, nav) } }
    @Test fun empty() = home { it.copy(index = 1, arrived = System.currentTimeMillis() + 2 * 86_400_000L, gone = mapOf("teacher" to System.currentTimeMillis())) }.let { h -> shot("8-empty") { EmptyScreen(h, nav) } }
    @Test fun settings() = home().let { h -> shot("9-settings") { SettingsPage(h, nav) } }

}

@Config(sdk = [34], qualifiers = "en-w411dp-h891dp-xxhdpi")
class RenderArtScreensEn : ScreensBase() { override val lang = "en" }

@Config(sdk = [34], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class RenderArtScreensRu : ScreensBase() { override val lang = "ru" }
