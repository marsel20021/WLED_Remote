package com.marsel.wledremote.data

import android.content.Context

/**
 * Хранит на телефоне только то, что должно переживать перезапуск приложения -
 * IP ТВ, чтобы не вводить его заново при каждом запуске, и галочку автопропуска.
 */
class LocalPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("wled_remote_prefs", Context.MODE_PRIVATE)

    var savedTvIp: String
        get() = prefs.getString(KEY_TV_IP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TV_IP, value).apply()

    /** Автопропуск заставок и титров. По умолчанию включён. */
    var skipEnabled: Boolean
        get() = prefs.getBoolean(KEY_SKIP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_ENABLED, value).apply()

    /** Пропускать отрезки, у которых всего одна отметка в базе. По умолчанию включено. */
    var skipUncertain: Boolean
        get() = prefs.getBoolean(KEY_SKIP_UNCERTAIN, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_UNCERTAIN, value).apply()

    /** Пропускать пересказ ("в предыдущих сериях"). По умолчанию включено. */
    var skipRecap: Boolean
        get() = prefs.getBoolean(KEY_SKIP_RECAP, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_RECAP, value).apply()

    companion object {
        private const val KEY_TV_IP = "tv_ip"
        private const val KEY_SKIP_ENABLED = "skip_enabled"
        private const val KEY_SKIP_UNCERTAIN = "skip_uncertain"
        private const val KEY_SKIP_RECAP = "skip_recap"
    }
}