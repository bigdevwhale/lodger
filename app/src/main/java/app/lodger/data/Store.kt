package app.lodger.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lodger.domain.House
import app.lodger.domain.Photo
import app.lodger.domain.Room
import app.lodger.domain.Spot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("house")

/** How frightening the flat may be. */
enum class Fear { CANDLE, LANTERN, MOON }

data class Settings(
    val sound: Boolean = true,
    val fear: Fear = Fear.LANTERN,
    /** Minute of the day the lodger's notes arrive when they were written at an hour nobody should be woken; −1 = no notifications. */
    val evening: Int = 21 * 60 + 30,
    /** The voice that speaks the words caught on the radio. */
    val voice: Boolean = true,
)

/** The house and the settings, kept as plain preferences. */
class Store(context: Context) {
    private val store = context.applicationContext.dataStore

    val house: Flow<House> = store.data.map { Houses.read(it[HOUSE].orEmpty()) }
    val settings: Flow<Settings> = store.data.map { it.settings() }

    private fun Preferences.settings() = Settings(
        sound = this[SOUND] ?: true,
        fear = Fear.entries.getOrElse(this[FEAR] ?: 1) { Fear.LANTERN },
        evening = this[EVENING] ?: Settings().evening,
        voice = this[VOICE] ?: true,
    )

    suspend fun now(): House = house.first()
    suspend fun settingsNow(): Settings = settings.first()

    suspend fun change(f: (House) -> House): House {
        var out = House()
        store.edit { p -> out = f(Houses.read(p[HOUSE].orEmpty())); p[HOUSE] = Houses.write(out) }
        return out
    }

    suspend fun settings(f: (Settings) -> Settings) {
        store.edit { p ->
            val s = f(p.settings())
            p[SOUND] = s.sound; p[FEAR] = s.fear.ordinal; p[EVENING] = s.evening; p[VOICE] = s.voice
        }
    }

    private companion object {
        val HOUSE = stringPreferencesKey("house")
        val SOUND = booleanPreferencesKey("sound")
        val FEAR = intPreferencesKey("fear")
        val EVENING = intPreferencesKey("evening")
        val VOICE = booleanPreferencesKey("voice")
    }
}

/**
 * The house as one line of text: fields split by the ASCII record separator, list items by the unit
 * separator, which no name or file contains.
 */
object Houses {
    private const val RS = '\u001e'
    private const val US = '\u001f'

    fun write(h: House): String = listOf(
        "1",
        h.index.toString(),
        h.arrived.toString(),
        h.words.joinToString(","),
        if (h.solved) "1" else "0",
        h.gone.entries.joinToString(US.toString()) { "${it.key}=${it.value}" },
        h.names.entries.joinToString(US.toString()) { "${it.key}=${clean(it.value)}" },
        h.photos.entries.joinToString(US.toString()) { (r, p) ->
            "${r.key}=${p.file}|${p.at}|" + p.spots.entries.joinToString(";") { "${it.key.key}:${it.value.first}:${it.value.second}" }
        },
        h.absent.joinToString(",") { it.key },
        h.seen.joinToString(US.toString()),
        h.rung.toString(),
    ).joinToString(RS.toString())

    fun read(s: String): House {
        if (s.isEmpty()) return House()
        val f = s.split(RS)
        fun at(i: Int) = f.getOrElse(i) { "" }
        fun pairs(v: String) = v.split(US).filter { '=' in it }.map { it.substringBefore('=') to it.substringAfter('=') }
        return House(
            index = at(1).toIntOrNull() ?: 0,
            arrived = at(2).toLongOrNull() ?: 0,
            words = at(3).split(',').mapNotNull { it.toIntOrNull() }.toSet(),
            solved = at(4) == "1",
            gone = pairs(at(5)).mapNotNull { (k, v) -> v.toLongOrNull()?.let { k to it } }.toMap(),
            names = pairs(at(6)).toMap(),
            photos = pairs(at(7)).mapNotNull { (k, v) ->
                val r = Room.of(k) ?: return@mapNotNull null
                val c = v.split('|')
                val spots = c.getOrElse(2) { "" }.split(';').mapNotNull { e ->
                    val p = e.split(':')
                    val sp = Spot.of(p.getOrElse(0) { "" }) ?: return@mapNotNull null
                    val x = p.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
                    val y = p.getOrNull(2)?.toFloatOrNull() ?: return@mapNotNull null
                    sp to (x to y)
                }.toMap()
                r to Photo(c[0], spots, c.getOrNull(1)?.toLongOrNull() ?: 0)
            }.toMap(),
            absent = at(8).split(',').mapNotNull { Room.of(it) }.toSet(),
            seen = at(9).split(US).filter { it.isNotEmpty() }.toSet(),
            rung = at(10).toIntOrNull() ?: 0,
        )
    }

    private fun clean(v: String) = v.replace(RS, ' ').replace(US, ' ').replace('=', ' ')
}
