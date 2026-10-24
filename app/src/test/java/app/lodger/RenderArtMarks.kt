package app.lodger

import android.graphics.Bitmap
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import app.lodger.data.Photos
import app.lodger.domain.Engine
import app.lodger.domain.Look
import app.lodger.domain.Mark
import app.lodger.domain.Photo
import app.lodger.domain.Room
import app.lodger.domain.Trace
import app.lodger.ui.art.Ink
import app.lodger.ui.art.Rooms
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Every kind of mark on a photo, one PNG each (run with `-Prender`). The drawn rooms are always used;
 * `-Pphoto=<file>` adds a real photo to try the marks against.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderArtMarks {
    private val out = File(System.getProperty("render.out") ?: "build/art", "marks").apply { mkdirs() }

    private fun photo(b: Bitmap): Photo {
        val (l, w, h) = Photos.lum(b)
        return Photo("", Look.spots(l, w, h), 0)
    }

    private fun sheet(name: String, b: Bitmap, marks: List<Trace>) {
        val app = ApplicationProvider.getApplicationContext<LodgerApp>()
        val hand = runCatching { ResourcesCompat.getFont(app, R.font.hand) }.getOrNull()
        val p = photo(b)
        val (lum, gw, gh) = Photos.lum(b)
        val layers = Engine.place(marks, p).withIndex().flatMap { (i, tp) ->
            val (t, pos) = tp
            val l = lum[((pos.y * gh).toInt().coerceIn(0, gh - 1)) * gw + (pos.x * gw).toInt().coerceIn(0, gw - 1)]
            Ink.layers(t, pos, b.width, b.height, i * 31 + t.night, l, hand)
        }
        File(out, "$name.png").outputStream().use { Ink.composite(b, layers).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun eachMark() {
        val real = System.getProperty("lodger.photo").orEmpty().takeIf { it.isNotEmpty() && File(it).exists() }
            ?.let { Photos.load(File(it), 1280) }
        val drawn = Rooms.draw(Room.ROOM)
        for (m in Mark.entries) {
            val t = Trace(1, Room.ROOM, m, "", sign = if (m == Mark.WRITING) "ИЩИ" else "", at = if (m == Mark.HAND) "" else "")
            sheet("drawn-${m.key}", drawn, listOf(t))
            real?.let { sheet("photo-${m.key}", it, listOf(t)) }
        }
        real?.let { sheet("photo-wall-hand", it, listOf(Trace(1, Room.ROOM, Mark.HAND, "", at = "wall"))) }
    }

    @Test fun storyRooms() {
        val app = ApplicationProvider.getApplicationContext<LodgerApp>()
        for (l in app.container.lodgers(true).all) for (r in Room.entries) {
            val ms = l.traces.filter { it.room == r }
            if (ms.isNotEmpty()) sheet("${l.id}-${r.key}", Rooms.draw(r), ms)
        }
    }

}
