package app.lodger.ui

import app.lodger.data.Fear
import app.lodger.domain.Room
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** The words of the flat, in both languages. */
class Words(val ru: Boolean) {
    fun t(r: String, e: String) = if (ru) r else e

    fun plural(n: Int, one: String, few: String, many: String): String {
        val a = abs(n) % 100; val b = a % 10
        return when { a in 11..19 -> many; b == 1 -> one; b in 2..4 -> few; else -> many }
    }

    fun nights(n: Int) = if (ru) "$n ${plural(n, "ночь", "ночи", "ночей")}" else if (n == 1) "1 night" else "$n nights"

    fun room(r: Room) = when (r) {
        Room.HALL -> t("Прихожая", "Hallway"); Room.KITCHEN -> t("Кухня", "Kitchen"); Room.ROOM -> t("Комната", "Room")
        Room.BATH -> t("Ванная", "Bathroom"); Room.BALCONY -> t("Балкон", "Balcony")
    }

    /** "Photograph the hallway": the room in the accusative. */
    fun shootRoom(r: Room) = when (r) {
        Room.HALL -> t("Сфотографируй прихожую", "Photograph the hallway"); Room.KITCHEN -> t("Сфотографируй кухню", "Photograph the kitchen")
        Room.ROOM -> t("Сфотографируй комнату", "Photograph the room"); Room.BATH -> t("Сфотографируй ванную", "Photograph the bathroom")
        Room.BALCONY -> t("Сфотографируй балкон", "Photograph the balcony")
    }

    fun fear(f: Fear) = when (f) {
        Fear.CANDLE -> t("Свеча", "Candle"); Fear.LANTERN -> t("Фонарь", "Lantern"); Fear.MOON -> t("Полнолуние", "Full moon")
    }

    fun fearHint(f: Fear) = when (f) {
        Fear.CANDLE -> t("Тихо: следы, записки, радио. Ни стуков, ни мерцания.", "Quiet: marks, notes, the radio. No knocks, no flicker.")
        Fear.LANTERN -> t("Иногда стучат. Фото подрагивают, как от лампы.", "The odd knock. Photos waver, as if the lamp were failing.")
        Fear.MOON -> t("На снимках можно на миг увидеть того, кто там стоит.", "On the photos you may glimpse, for a blink, whoever stands there.")
    }

    fun night(n: Int, of: Int) = t("Ночь $n из $of", "Night $n of $of")

    fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

    fun date(ms: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
        return if (ru) "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}" else "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${d.year}"
    }

    /** First-visit hints, one card per screen, written as a note left by the previous tenant. */
    fun coach(screen: String): List<String> = when (screen) {
        "flat" -> listOf(t("Квартира", "The flat"),
            t("Это твои комнаты на твоих фото. Каждый вечер, после шести, на них появляется что-то новое.", "These are your rooms, in your photos. Every evening after six, something new turns up in them."),
            t("Красная точка — в комнате что-то изменилось. Открой снимок и подожди: след проступает не сразу.", "A red dot means something has changed in that room. Open the photo and wait: a mark takes a moment to come through."),
            t("Комнату можно переснять в любой момент — следы перейдут на новый снимок.", "You can retake a room at any time; the marks move to the new photo."))
        "notes" -> listOf(t("Записки", "Notes"),
            t("Жилец пишет. Не каждую ночь, но часто. Записки приходят вечером, даже если написаны в три часа ночи.", "The lodger writes. Not every night, but often. Notes arrive in the evening, even if they were written at three in the morning."),
            t("В записках есть подсказки. Читай внимательно.", "There are hints in them. Read carefully."))
        "radio" -> listOf(t("Радио", "The radio"),
            t("Включи и крути шкалу. Где-то сквозь шум пробиваются слова.", "Switch it on and turn the dial. Somewhere under the hiss, words come through."),
            t("Когда шум стихает и проступают буквы — задержись на этой частоте. Слово запишется само.", "When the hiss thins and letters appear, hold the dial there. The word writes itself down."),
            t("Новые слова появляются в эфире не сразу — заглядывай через пару ночей.", "New words come on air only every few nights — check back."))
        "board" -> listOf(t("Доска", "The board"),
            t("Сюда прикалываются улики: фотографии, письма, справки. Красная нитка связывает найденное.", "Clues are pinned here: photos, letters, records. A red thread ties together what you've found."),
            t("Когда соберёшь все улики и все слова из радио, история сложится — и станет ясно, что жилец не успел сделать.", "Once you have every clue and every word from the radio, the story comes together, and you'll see what the lodger never got to finish."))
        "room" -> listOf(t("Снимок", "The photo"),
            t("Под снимком — что и когда здесь появилось. Самое новое сверху.", "Under the photo is what turned up here, and when. Newest first."),
            t("Если такой комнаты нет — так и отметь. Её следы уйдут в соседнюю.", "If your flat has no such room, mark it. Its marks will go to the room next door."))
        else -> emptyList()
    }

    companion object {
        private val MONTHS = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    }
}
