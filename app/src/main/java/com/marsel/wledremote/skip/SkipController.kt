package com.marsel.wledremote.skip

import com.marsel.wledremote.data.RemoteRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Пропуск заставок целиком живёт на ТВ. Телефон показывает то, что прислал ТВ, и шлёт команды обратно.
 * Класс сохранён, чтобы экраны не менялись: те же ui, skipNow, cancel, undo, pickSeason.
 */
class SkipController(private val repository: RemoteRepository) {
    val ui: StateFlow<SkipUiState> = repository.skipState

    /** Раньше запускал цикл на телефоне. Теперь ничего не нужно, но вызовы остались. */
    fun start() {}

    fun skipNow() = repository.skipNow()
    fun cancel() = repository.skipCancel()
    fun undo() = repository.skipUndo()
    fun pickSeason(season: Int) = repository.skipPickSeason(season)
}
