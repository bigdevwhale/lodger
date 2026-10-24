package app.lodger.data

import android.graphics.Bitmap
import app.lodger.AppContainer
import app.lodger.BuildConfig
import app.lodger.domain.Engine
import app.lodger.domain.Room
import java.time.ZoneId
import app.lodger.ui.art.Rooms

/**
 * The demo flat: drawn rooms in place of photos and the first lodger ten nights in, with three words
 * already caught on the radio. Screenshot builds also mark every hint as read.
 */
object DemoSeed {
    const val NIGHT = 10

    suspend fun seedIfEmpty(c: AppContainer, shots: Boolean = BuildConfig.SHOTS) {
        if (c.store.now().photos.isNotEmpty()) return
        for (room in Room.entries) {
            val rel = c.newFile(room)
            c.file(rel).outputStream().use { Rooms.draw(room).compress(Bitmap.CompressFormat.JPEG, 90, it) }
            c.photo(room, rel)
        }
        val zone = ZoneId.systemDefault()
        // Moved in at seven in the evening, NIGHT − 1 evenings ago, so tonight is night NIGHT.
        val arrived = Engine.evening(System.currentTimeMillis(), zone).minusDays(NIGHT - 1L).atStartOfDay(zone).plusHours(19).toInstant().toEpochMilli()
        c.store.change { h ->
            h.copy(
                arrived = arrived,
                words = setOf(0, 1, 2),
                seen = h.seen + "welcome" + "intro:teacher" + (if (shots) setOf("coach:flat", "coach:notes", "coach:radio", "coach:board", "coach:room") else emptySet()),
                rung = NIGHT,
            )
        }
        c.store.settings { it.copy(fear = if (shots) Fear.CANDLE else Fear.LANTERN) }
    }
}
