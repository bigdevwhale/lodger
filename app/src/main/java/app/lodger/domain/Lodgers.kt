package app.lodger.domain

import org.json.JSONArray
import org.json.JSONObject

/** The stories, one file per language: assets/lodgers/<lang>.json. */
class Lodgers(val all: List<Lodger>) {
    private val byId = all.associateBy { it.id }
    operator fun get(id: String) = byId[id]

    companion object {
        fun file(ru: Boolean) = "lodgers/${if (ru) "ru" else "en"}.json"

        fun parse(text: String): Lodgers {
            val a = JSONArray(text)
            return Lodgers((0 until a.length()).map { lodger(a.getJSONObject(it)) })
        }

        private fun <T> JSONArray.list(f: (JSONObject) -> T) = (0 until length()).map { f(getJSONObject(it)) }

        private fun lodger(o: JSONObject): Lodger {
            val task = o.getJSONObject("task")
            val words = o.getJSONArray("words")
            val freqs = o.getJSONArray("freqs")
            return Lodger(
                id = o.getString("id"),
                kind = o.getString("kind"),
                name = o.getString("name"),
                years = o.getString("years"),
                role = o.getString("role"),
                intro = o.getString("intro"),
                traces = o.getJSONArray("traces").list { t ->
                    Trace(
                        t.getInt("night"), Room.of(t.getString("room")) ?: error("room ${t.getString("room")}"),
                        Mark.of(t.getString("type")) ?: error("mark ${t.getString("type")}"), t.getString("text"), t.optString("mark"), t.optString("at"),
                    )
                },
                notes = o.getJSONArray("notes").list { Note(it.getInt("night"), it.getString("time"), it.getString("text")) },
                words = (0 until words.length()).map { words.getString(it) },
                freqs = (0 until freqs.length()).map { freqs.getDouble(it) },
                clues = o.getJSONArray("clues").list { Clue(it.getInt("night"), ClueKind.of(it.getString("kind")), it.getString("title"), it.getString("text")) },
                truth = o.getString("truth"),
                task = Task(task.getString("title"), task.getString("text"), task.optBoolean("askName")),
                farewell = o.getString("farewell"),
                finale = o.optString("finale"),
            )
        }
    }
}
