
package com.marsel.wledremote

import android.app.Application
import com.marsel.wledremote.data.NetworkRemoteRepository
import com.marsel.wledremote.data.RemoteRepository

/**
 * Единая точка хранения RemoteRepository - и MainActivity, и фоновый PlaybackService
 * должны видеть одно и то же состояние подключения/воспроизведения, а не две
 * независимые копии с разными вебсокет-соединениями.
 */
class WledRemoteApp : Application() {
    val repository: RemoteRepository by lazy { NetworkRemoteRepository(this) }
}
