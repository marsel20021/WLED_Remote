package com.marsel.wledremote

import android.app.Application
import com.marsel.wledremote.data.NetworkRemoteRepository
import com.marsel.wledremote.data.PosterRepository
import com.marsel.wledremote.data.RemoteRepository
import com.marsel.wledremote.skip.SharedPrefsStore
import com.marsel.wledremote.skip.SkipCache
import com.marsel.wledremote.skip.SkipController
import com.marsel.wledremote.skip.SkipSettings

/**
 * Единая точка хранения RemoteRepository - и MainActivity, и фоновый PlaybackService
 * должны видеть одно и то же состояние подключения/воспроизведения, а не две
 * независимые копии с разными вебсокет-соединениями.
 */
class WledRemoteApp : Application() {
    val repository: RemoteRepository by lazy { NetworkRemoteRepository(this) }

    /** Поиск постеров для экранов приложения. Кэш лежит на диске и общий с плашкой в уведомлениях. */
    val posters: PosterRepository by lazy { PosterRepository(this) }

    /** Галочка автопропуска заставок и титров. */
    val skipSettings: SkipSettings by lazy { SkipSettings(this) }

    /**
     * Автопропуск заставок и титров. Живёт здесь, а не в экране, чтобы работал и при закрытом пульте.
     * Запускается при первом обращении (из MainActivity или из сервиса).
     */
    val skip: SkipController by lazy {
        SkipController(repository, skipSettings.enabled, SkipCache(SharedPrefsStore(this))).also { it.start() }
    }
}
