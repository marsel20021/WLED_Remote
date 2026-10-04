package com.marsel.wledremote.data

import android.content.Context

/**
 * Хранит на телефоне только то, что должно переживать перезапуск приложения -
 * сейчас это IP ТВ, чтобы не вводить его заново при каждом запуске.
 */
class LocalPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("wled_remote_prefs", Context.MODE_PRIVATE)

    var savedTvIp: String
        get() = prefs.getString(KEY_TV_IP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TV_IP, value).apply()

    companion object {
        private const val KEY_TV_IP = "tv_ip"
    }
}