package app.lodger

import androidx.test.core.app.ApplicationProvider
import app.lodger.domain.Engine
import app.lodger.domain.Lodgers
import app.lodger.domain.Mark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Both story files read, agree with each other, and fit what the screens can show. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentTest {
    private fun read(ru: Boolean) = ApplicationProvider.getApplicationContext<LodgerApp>().container.lodgers(ru)

    @Test fun storiesAreWholeAndAgree() {
        val ru = read(true); val en = read(false)
        assertEquals(Engine.ORDER, ru.all.map { it.id })
        assertEquals(Engine.ORDER, en.all.map { it.id })
        for ((r, e) in ru.all.zip(en.all)) check(r.id, r, e)
    }

    private fun check(id: String, r: app.lodger.domain.Lodger, e: app.lodger.domain.Lodger) {
        fun ok(c: Boolean, what: String) = assertTrue("$id: $what", c)
        for (l in listOf(r, e)) {
            ok(l.clues.size == 5, "five clues (the board has five pins)")
            ok(l.clues.all { it.night in 1..Engine.NIGHTS } && l.clues.maxOf { it.night } == Engine.NIGHTS, "last clue on the last night")
            ok(l.words.size == l.freqs.size && l.words.size in 3..6, "a word per frequency")
            ok(l.freqs.all { it in 88.0..107.5 }, "frequencies on the dial")
            ok(l.freqs.sorted().zipWithNext().all { (a, b) -> b - a >= 1.5 }, "frequencies apart")
            ok(l.words.indices.all { Engine.wordNight(it, l.words.size) <= Engine.NIGHTS }, "words in time")
            ok(l.traces.all { it.night in 1..Engine.NIGHTS && it.text.isNotBlank() }, "marks")
            ok(l.traces.filter { it.mark == Mark.WRITING }.all { it.sign.isNotBlank() && it.sign.length <= 10 }, "writing is short")
            ok(l.notes.map { it.night }.distinct().size == l.notes.size, "one note a night")
            ok(l.notes.all { Regex("\\d\\d:\\d\\d").matches(it.time) }, "note times")
            ok(!l.task.askName || "{name}" in l.farewell, "the asked name is used")
            ok(listOf(l.kind, l.name, l.truth, l.task.title, l.task.text, l.farewell, l.intro).all { it.isNotBlank() }, "texts")
        }
        assertEquals("$id traces", r.traces.map { Triple(it.night, it.room, it.mark) }, e.traces.map { Triple(it.night, it.room, it.mark) })
        assertEquals("$id notes", r.notes.map { it.night to it.time }, e.notes.map { it.night to it.time })
        assertEquals("$id clues", r.clues.map { it.night to it.kind }, e.clues.map { it.night to it.kind })
        assertEquals("$id freqs", r.freqs, e.freqs)
        assertEquals("$id askName", r.task.askName, e.task.askName)
        assertTrue("$id: no Cyrillic in English", Regex("[А-Яа-яЁё]").find(Lodgers.file(false) + e.toString()) == null)
    }
}
