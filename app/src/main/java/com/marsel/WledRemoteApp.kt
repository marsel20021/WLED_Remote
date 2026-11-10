package com.marsel.wledremote

import android.app.Application
import com.marsel.wledremote.data.NetworkRemoteRepository
import com.marsel.wledremote.data.PosterRepository
import com.marsel.wledremote.data.RemoteRepository

/**
 * Единая точка хранения RemoteRepository - и MainActivity, и фоновый PlaybackService
 * должны видеть одно и то же состояние подключения/воспроизведения, а не две
 * независимые копии с разными вебсокет-соединениями.
 */
class WledRemoteApp : Application() {
    val repository: RemoteRepository by lazy { NetworkRemoteRepository(this) }

    /** Поиск постеров для фона на экране пульта (кэш лежит на диске и общий с плашкой в уведомлениях). */
    val posters: PosterRepository by lazy { PosterRepository(this) }
}
