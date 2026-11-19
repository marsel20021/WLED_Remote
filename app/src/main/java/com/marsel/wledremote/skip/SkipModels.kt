package com.marsel.wledremote.skip

import org.json.JSONObject

/** Что пропускаем. Совпадает с названиями, которые присылает ТВ. */
enum class SkipKind { INTRO, RECAP, CREDITS }

/**
 * Что показать на экране. Обычно идёт отсчёт: progress 0..1, secondsLeft - сколько секунд осталось.
 * manual = true: отсчёта нет, просто кнопка, пока человек внутри отрезка.
 */
data class SkipOffer(val kind: SkipKind, val progress: Float, val secondsLeft: Int, val manual: Boolean = false)

/** Выбор сезона для плашки. selected = null: сезон ещё не выбран. */
data class SeasonChoice(val seasons: List<Int>, val selected: Int?)

/** После прыжка: можно вернуться назад. */
data class SkipUndo(val kind: SkipKind)

/** Всё, что нужно нарисовать на экране пульта. Считает и присылает ТВ, телефон только показывает. */
data class SkipUiState(
    val offer: SkipOffer? = null,
    val seasonChoice: SeasonChoice? = null,
    val undo: SkipUndo? = null
)

/** Разбор сообщения {"type":"skipState",...} от ТВ. Без Android, чтобы проверить обычными тестами. */
object SkipWire {
    const val TYPE = "skipState"

    fun parse(json: JSONObject): SkipUiState {
        val offer = json.optJSONObject("offer")?.let { o ->
            val kind = kindOf(o.optString("kind")) ?: return@let null
            SkipOffer(kind, o.optDouble("progress", 0.0).toFloat(), o.optInt("secondsLeft", 0), o.optBoolean("manual", false))
        }
        val undo = json.optJSONObject("undo")?.let { o -> kindOf(o.optString("kind"))?.let { SkipUndo(it) } }
        val choice = json.optJSONObject("seasonChoice")?.let { o ->
            val arr = o.optJSONArray("seasons") ?: return@let null
            val seasons = (0 until arr.length()).map { arr.optInt(it, 0) }.filter { it >= 1 }
            if (seasons.isEmpty()) null else SeasonChoice(seasons, if (o.isNull("selected")) null else o.optInt("selected"))
        }
        return SkipUiState(offer, choice, undo)
    }

    private fun kindOf(name: String): SkipKind? = SkipKind.values().firstOrNull { it.name == name }
}
