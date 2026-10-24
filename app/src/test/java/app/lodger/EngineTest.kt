package app.lodger

import app.lodger.data.Houses
import app.lodger.domain.Clue
import app.lodger.domain.ClueKind
import app.lodger.domain.Engine
import app.lodger.domain.House
import app.lodger.domain.Lodger
import app.lodger.domain.Lodgers
import app.lodger.domain.Look
import app.lodger.domain.Mark
import app.lodger.domain.Note
import app.lodger.domain.Phase
import app.lodger.domain.Photo
import app.lodger.domain.Room
import app.lodger.domain.Spot
import app.lodger.domain.Task
import app.lodger.domain.Trace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.hypot

class EngineTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private val lodger = Lodger(
        "teacher", "Ghost", "Vera", "1902–1971", "teacher", "intro",
        traces = listOf(Trace(1, Room.ROOM, Mark.CHAIR, "a chair"), Trace(2, Room.BALCONY, Mark.FIGURE, "someone"), Trace(4, Room.ROOM, Mark.HAND, "a hand", at = "wall")),
        notes = listOf(Note(1, "23:40", "one"), Note(2, "03:12", "two"), Note(5, "21:30", "five")),
        words = listOf("he", "never", "finished"), freqs = listOf(90.0, 95.0, 100.0),
        clues = listOf(Clue(3, ClueKind.PHOTO, "p", "p"), Clue(14, ClueKind.DATE, "d", "d")),
        truth = "t", task = Task("do", "it"), farewell = "bye",
    )
    private val lodgers = Lodgers(listOf(lodger))

    @Test fun nightsTurnAtSixInTheEvening() {
        val arrived = at(2026, 10, 24, 14) // moved in by day: tonight is night 1
        assertEquals(1, Engine.night(arrived, at(2026, 10, 24, 15), zone))
        assertEquals(1, Engine.night(arrived, at(2026, 10, 24, 23), zone))
        assertEquals(1, Engine.night(arrived, at(2026, 10, 25, 3), zone))
        assertEquals(1, Engine.night(arrived, at(2026, 10, 25, 17, 59), zone))
        assertEquals(2, Engine.night(arrived, at(2026, 10, 25, 18), zone))
        assertEquals(Engine.NIGHTS, Engine.night(arrived, at(2027, 1, 1, 12), zone))
    }

    @Test fun movingInAfterMidnightBelongsToThePreviousEvening() {
        val arrived = at(2026, 10, 25, 2)
        assertEquals(1, Engine.night(arrived, at(2026, 10, 25, 2, 30), zone))
        assertEquals(2, Engine.night(arrived, at(2026, 10, 25, 19), zone))
    }

    @Test fun wordsComeOnAirFromNightTwoToTwelve() {
        assertEquals(listOf(2, 7, 12), (0..2).map { Engine.wordNight(it, 3) })
        assertEquals(listOf(2, 4, 6, 8, 10, 12), (0..5).map { Engine.wordNight(it, 6) })
        assertEquals(listOf(0), Engine.open(lodger, 3))
    }

    @Test fun readyNeedsEveryClueAndEveryWord() {
        val h = House(words = setOf(0, 1, 2))
        assertFalse(Engine.ready(lodger, h, 13))
        assertTrue(Engine.ready(lodger, h, 14))
        assertFalse(Engine.ready(lodger, h.copy(words = setOf(0, 1)), 14))
    }

    @Test fun absentRoomsHandTheirMarksOn() {
        val h = House(absent = setOf(Room.BALCONY))
        assertEquals(Room.ROOM, Engine.home(Room.BALCONY, h))
        assertEquals(listOf("a chair", "someone"), Engine.marks(lodger, h, Room.ROOM, 2).map { it.text })
        assertEquals(Room.KITCHEN, Engine.home(Room.BALCONY, House(absent = setOf(Room.BALCONY, Room.ROOM))))
    }

    @Test fun marksOnTheSameSpotAreNudgedApart() {
        val photo = Photo("f", mapOf(Spot.FLOOR to (.5f to .86f)), 0)
        val two = listOf(Trace(1, Room.HALL, Mark.FOOTPRINTS, "a"), Trace(2, Room.HALL, Mark.PAWS, "b"))
        val (a, b) = Engine.place(two, photo).map { it.second }
        assertTrue(hypot(a.x - b.x, a.y - b.y) >= .16f)
        assertEquals(.5f, a.x, 1e-4f)
    }

    @Test fun phasesFollowTheHouse() {
        val now = at(2026, 10, 30, 20)
        assertEquals(Phase.Settling, Engine.phase(House(), lodgers, now, zone))
        assertEquals(Phase.Living(lodger, 7), Engine.phase(House(arrived = at(2026, 10, 24, 19)), lodgers, now, zone))
        assertEquals(Phase.Empty(now + 1), Engine.phase(House(arrived = now + 1), lodgers, now, zone))
        assertEquals(Phase.Quiet, Engine.phase(House(index = Engine.ORDER.size), lodgers, now, zone))
    }

    @Test fun settlingNeedsTwoPhotos() {
        val one = House(photos = mapOf(Room.HALL to Photo("a", emptyMap(), 0)))
        assertEquals(0L, Engine.settle(one, 5).arrived)
        assertEquals(5L, Engine.settle(one.copy(photos = one.photos + (Room.ROOM to Photo("b", emptyMap(), 0))), 5).arrived)
    }

    @Test fun leavingEmptiesTheFlatForThreeNights() {
        val now = at(2026, 11, 7, 21)
        val h = Engine.leave(House(arrived = at(2026, 10, 24, 19), words = setOf(0, 1, 2), solved = true, rung = 13), lodger, now, zone, null)
        assertEquals(1, h.index)
        assertEquals(at(2026, 11, 10, 18), h.arrived)
        assertTrue(h.words.isEmpty() && !h.solved && h.rung == 0)
        assertEquals(now, h.gone["teacher"])
        assertEquals("Barsik", Engine.leave(House(), lodger, now, zone, "  Barsik ").names["teacher"])
    }

    @Test fun notesRingInTheEveningOnly() {
        val arrived = at(2026, 10, 24, 19)
        // Written at 23:40: rings then. Written at 03:12: rings at the chosen evening hour instead.
        assertEquals(at(2026, 10, 24, 23, 40), Engine.noteTime(arrived, lodger.notes[0], 21 * 60 + 30, zone))
        assertEquals(at(2026, 10, 25, 21, 30), Engine.noteTime(arrived, lodger.notes[1], 21 * 60 + 30, zone))
        val h = House(arrived = arrived, rung = 1)
        assertEquals(2 to at(2026, 10, 25, 21, 30), Engine.nextNote(h, lodgers, at(2026, 10, 25, 12), 21 * 60 + 30, zone)?.let { it.first.night to it.second })
        assertEquals(5, Engine.nextNote(h, lodgers, at(2026, 10, 26, 12), 21 * 60 + 30, zone)?.first?.night)
        assertNull(Engine.nextNote(h, lodgers, at(2026, 10, 25, 12), -1, zone))
    }

    @Test fun theHouseSurvivesBeingWrittenDown() {
        val h = House(
            index = 3, arrived = 123, words = setOf(0, 4), solved = true, gone = mapOf("teacher" to 5L, "cat" to 9L),
            names = mapOf("cat" to "Мурзик"), absent = setOf(Room.BALCONY), seen = setOf("welcome", "coach:flat"), rung = 7,
            photos = mapOf(Room.HALL to Photo("rooms/hall-1.jpg", mapOf(Spot.DARK to (.25f to .5f), Spot.GLASS to (.7f to .2f)), 42)),
        )
        assertEquals(h, Houses.read(Houses.write(h)))
        assertEquals(House(), Houses.read(""))
    }

    @Test fun lookFindsTheDarkCornerAndTheWindow() {
        val w = 36; val h = 48
        // Mid-grey room, a bright window top-left, a dark doorway on the right.
        val lum = FloatArray(w * h) { i ->
            val x = i % w; val y = i / w
            when { x in 4..12 && y in 4..14 -> .95f; x in 26..31 && y in 18..44 -> .04f; else -> .45f }
        }
        val s = Look.spots(lum, w, h)
        val (dx, dy) = s.getValue(Spot.DARK)
        assertTrue("dark at $dx,$dy", dx > .7f && dx < .9f && dy > .4f)
        val (gx, gy) = s.getValue(Spot.GLASS)
        assertTrue("glass at $gx,$gy", gx < .4f && gy < .35f)
        assertNotNull(s[Spot.FLOOR]); assertTrue(s.getValue(Spot.FLOOR).second > .75f)
    }
}
