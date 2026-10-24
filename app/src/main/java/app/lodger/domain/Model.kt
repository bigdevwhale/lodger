package app.lodger.domain

/** The rooms of the flat, in the order they are shown. */
enum class Room(val key: String) {
    HALL("hall"), KITCHEN("kitchen"), ROOM("room"), BATH("bath"), BALCONY("balcony");

    companion object {
        fun of(k: String) = entries.firstOrNull { it.key == k }
    }
}

/** What a lodger leaves on a photo. */
enum class Mark(val key: String, val spot: Spot) {
    FOOTPRINTS("footprints", Spot.FLOOR), WET("wet", Spot.FLOOR), PAWS("paws", Spot.FLOOR), THREAD("thread", Spot.FLOOR),
    LETTER("letter", Spot.FLOOR), CHAIR("chair", Spot.FLOOR), CRUMBS("crumbs", Spot.TABLE), CUP("cup", Spot.TABLE),
    FIGURE("figure", Spot.DARK), EYES("eyes", Spot.DARK), DOOR("door", Spot.DARK), HAND("hand", Spot.GLASS),
    WRITING("writing", Spot.GLASS), FROST("frost", Spot.GLASS), KEYS("keys", Spot.WALL), LIGHT("light", Spot.HIGH), CAT("cat", Spot.TABLE);

    companion object {
        fun of(k: String) = entries.firstOrNull { it.key == k }
    }
}

/**
 * Where on a photo a mark can sit. Found from the picture itself: the darkest patch, the brightest
 * (a window, a mirror, a lamp), a quiet stretch of floor, wall or table.
 */
enum class Spot(val key: String) {
    FLOOR("floor"), TABLE("table"), WALL("wall"), GLASS("glass"), DARK("dark"), HIGH("high");

    companion object {
        fun of(k: String) = entries.firstOrNull { it.key == k }

        /** The story files name places as the prototype's drawn rooms had them. */
        fun named(at: String): Spot? = when (at) {
            "" -> null
            "wall", "vent" -> WALL
            "mirror", "mirrorfig", "window" -> GLASS
            "sill", "floor" -> FLOOR
            "rail", "table" -> TABLE
            "below", "corner", "door", "gap" -> DARK
            "sky" -> HIGH
            else -> of(at)
        }
    }
}

data class Trace(val night: Int, val room: Room, val mark: Mark, val text: String, val sign: String = "", val at: String = "") {
    val spot: Spot get() = Spot.named(at) ?: mark.spot
    /** A figure seen in a mirror or far below is smaller than one standing in the room. */
    val small: Boolean get() = at == "mirrorfig" || at == "below"
}

data class Note(val night: Int, val time: String, val text: String) {
    val minute: Int get() = time.split(':').let { (it.getOrNull(0)?.toIntOrNull() ?: 21) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
}

enum class ClueKind(val key: String) {
    PHOTO("photo"), LETTER("letter"), TICKET("ticket"), OBJECT("object"), DATE("date"), ADDRESS("address");

    companion object {
        fun of(k: String) = entries.firstOrNull { it.key == k } ?: OBJECT
    }
}

data class Clue(val night: Int, val kind: ClueKind, val title: String, val text: String)

data class Task(val title: String, val text: String, val askName: Boolean = false)

data class Lodger(
    val id: String,
    val kind: String,
    val name: String,
    val years: String,
    val role: String,
    val intro: String,
    val traces: List<Trace>,
    val notes: List<Note>,
    val words: List<String>,
    val freqs: List<Double>,
    val clues: List<Clue>,
    val truth: String,
    val task: Task,
    val farewell: String,
    val finale: String = "",
)

/** A room's photo: the file under the app's storage and the spots found in it (x, y as 0..1 of the frame). */
data class Photo(val file: String, val spots: Map<Spot, Pair<Float, Float>>, val at: Long)

/**
 * Everything about the flat that is kept: who lives here now and since when, what has been caught on
 * the radio, the photos, and the lodgers who have already gone.
 */
data class House(
    /** Index into [Engine.ORDER] of the current (or next) lodger; ORDER.size when all have gone. */
    val index: Int = 0,
    /** When the current lodger moved in; 0 before the first photos are taken. In the future while the flat stands empty. */
    val arrived: Long = 0,
    val words: Set<Int> = emptySet(),
    /** The story has been put together on the board. */
    val solved: Boolean = false,
    /** Lodgers who have left, with when. */
    val gone: Map<String, Long> = emptyMap(),
    /** The name the cat was given (and any other name a lodger asked for). */
    val names: Map<String, String> = emptyMap(),
    val photos: Map<Room, Photo> = emptyMap(),
    /** Rooms this flat doesn't have; their marks go to another room. */
    val absent: Set<Room> = emptySet(),
    /** First-visit hints already read, intros already seen, and "seen" marks for the newest things. */
    val seen: Set<String> = emptySet(),
    /** The last night whose note was rung as a notification. */
    val rung: Int = 0,
)
