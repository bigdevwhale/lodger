package app.lodger.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Where the flat stands right now. */
sealed interface Phase {
    /** No lodger yet: the rooms still have to be photographed. */
    data object Settling : Phase
    data class Living(val lodger: Lodger, val night: Int) : Phase
    /** A lodger has gone; the next moves in at [until]. */
    data class Empty(val until: Long) : Phase
    /** Every lodger has gone. */
    data object Quiet : Phase
}

data class Pos(val x: Float, val y: Float)

/** The rules of the house: how nights pass, what a night shows, when a story is ready, where marks land. */
object Engine {
    /** The lodgers, in the order they move in. */
    val ORDER = listOf("teacher", "domovoy", "hide", "mirror", "postman", "kikimora", "whisperer", "upstairs", "hostess", "cat")
    const val NIGHTS = 14
    /** Nights the flat stands empty between lodgers. */
    const val EMPTY_NIGHTS = 3
    /** The night turns at six in the evening. */
    const val EVENING = 18
    /** Photos needed before anyone moves in. */
    const val MIN_PHOTOS = 2

    fun phase(h: House, lodgers: Lodgers, now: Long, zone: ZoneId): Phase {
        if (h.index >= ORDER.size) return Phase.Quiet
        if (h.arrived == 0L) return Phase.Settling
        if (now < h.arrived) return Phase.Empty(h.arrived)
        val l = lodgers[ORDER[h.index]] ?: return Phase.Quiet
        return Phase.Living(l, night(h.arrived, now, zone))
    }

    /** The evening a moment belongs to: from 18:00 until 17:59 the next day. */
    fun evening(t: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(t).atZone(zone).minusHours(EVENING.toLong()).toLocalDate()

    /** The evening an arrival counts from: moving in by day means tonight is the first night. */
    fun firstEvening(arrived: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(arrived).atZone(zone).minusHours(5).toLocalDate()

    fun night(arrived: Long, now: Long, zone: ZoneId): Int {
        val d = ChronoUnit.DAYS.between(firstEvening(arrived, zone), evening(now, zone)).toInt()
        return (d + 1).coerceIn(1, NIGHTS)
    }

    fun traces(l: Lodger, night: Int) = l.traces.filter { it.night <= night }
    fun notes(l: Lodger, night: Int) = l.notes.filter { it.night <= night }
    fun clues(l: Lodger, night: Int) = l.clues.filter { it.night <= night }

    /** The night a word first comes through the static: spread over nights 2…12. */
    fun wordNight(k: Int, n: Int): Int = 2 + (k * 10.0 / maxOf(1, n - 1)).roundToInt()

    fun open(l: Lodger, night: Int): List<Int> = l.words.indices.filter { wordNight(it, l.words.size) <= night }

    /** All the clues are on the board and all the words caught: the story can be put together. */
    fun ready(l: Lodger, h: House, night: Int): Boolean =
        l.clues.all { it.night <= night } && h.words.containsAll(l.words.indices.toList())

    /** A room the flat doesn't have hands its marks to the nearest one it does. */
    fun home(r: Room, h: House): Room {
        if (r !in h.absent) return r
        val next = when (r) {
            Room.HALL -> listOf(Room.ROOM, Room.KITCHEN, Room.BATH, Room.BALCONY)
            Room.KITCHEN -> listOf(Room.ROOM, Room.HALL, Room.BATH, Room.BALCONY)
            Room.ROOM -> listOf(Room.HALL, Room.KITCHEN, Room.BALCONY, Room.BATH)
            Room.BATH -> listOf(Room.HALL, Room.KITCHEN, Room.ROOM, Room.BALCONY)
            Room.BALCONY -> listOf(Room.ROOM, Room.KITCHEN, Room.HALL, Room.BATH)
        }
        return next.firstOrNull { it !in h.absent } ?: r
    }

    /** The marks showing in [r] by [night], wherever the story first put them. */
    fun marks(l: Lodger, h: House, r: Room, night: Int) = traces(l, night).filter { home(it.room, h) == r }

    /** Places marks on a photo, nudging a mark aside when its spot is already taken. */
    fun place(marks: List<Trace>, photo: Photo?): List<Pair<Trace, Pos>> {
        val used = mutableListOf<Pos>()
        return marks.map { t ->
            val (bx, by) = photo?.spots?.get(t.spot) ?: Look.fallback(t.spot)
            var p = Pos(bx, by)
            var k = 0
            while (used.any { hypot(it.x - p.x, it.y - p.y) < .16f } && k < 8) {
                k++
                val side = if (k % 2 == 1) 1 else -1
                p = Pos((bx + side * .2f * ((k + 1) / 2)).coerceIn(.1f, .9f), (by + .05f * (k / 2)).coerceIn(.08f, .94f))
            }
            used += p
            t to p
        }
    }

    /** Is there anything new tonight on a tab? */
    fun fresh(l: Lodger, h: House, night: Int): Map<String, Boolean> = mapOf(
        "flat" to l.traces.any { it.night == night },
        "notes" to l.notes.any { it.night == night },
        "radio" to open(l, night).any { it !in h.words },
        "board" to l.clues.any { it.night == night },
    )

    // ---- the evening note ----

    /**
     * When a note reaches the phone: at its own time if that falls in the evening (19:00–23:59), otherwise
     * at the chosen evening hour. A lodger writing at three in the morning still only knocks in the evening.
     */
    fun noteTime(arrived: Long, n: Note, eveningMinute: Int, zone: ZoneId): Long {
        val day = firstEvening(arrived, zone).plusDays((n.night - 1).toLong())
        val m = if (n.minute in 19 * 60 until 24 * 60) n.minute else eveningMinute
        return day.atStartOfDay(zone).toInstant().toEpochMilli() + m * 60_000L
    }

    /** The next note to ring, and when; none if every coming one is in the past. */
    fun nextNote(h: House, lodgers: Lodgers, now: Long, eveningMinute: Int, zone: ZoneId): Pair<Note, Long>? {
        if (eveningMinute < 0 || h.index >= ORDER.size || h.arrived == 0L) return null
        val l = lodgers[ORDER[h.index]] ?: return null
        return l.notes.filter { it.night > h.rung }
            .map { it to maxOf(noteTime(h.arrived, it, eveningMinute, zone), h.arrived) }
            .firstOrNull { it.second > now - 60_000L }
    }

    // ---- moving in and out ----

    /** The first lodger moves in once enough rooms are photographed. */
    fun settle(h: House, now: Long): House =
        if (h.arrived == 0L && h.index < ORDER.size && h.photos.size >= MIN_PHOTOS) h.copy(arrived = now, rung = 0) else h

    /** The task is done: the lodger goes, and the flat stands empty for a few nights. */
    fun leave(h: House, l: Lodger, now: Long, zone: ZoneId, name: String? = null): House {
        val next = evening(now, zone).plusDays(EMPTY_NIGHTS.toLong()).atStartOfDay(zone).plusHours(EVENING.toLong()).toInstant().toEpochMilli()
        return h.copy(
            index = h.index + 1,
            arrived = if (h.index + 1 < ORDER.size) next else h.arrived,
            words = emptySet(),
            solved = false,
            gone = h.gone + (l.id to now),
            names = if (name.isNullOrBlank()) h.names else h.names + (l.id to name.trim().take(24)),
            rung = 0,
        )
    }

    /** The flat's lodgers who have gone, in the order they left. */
    fun register(h: House, lodgers: Lodgers): List<Pair<Lodger, Long>> =
        h.gone.entries.sortedBy { it.value }.mapNotNull { (id, at) -> lodgers[id]?.let { it to at } }
}
