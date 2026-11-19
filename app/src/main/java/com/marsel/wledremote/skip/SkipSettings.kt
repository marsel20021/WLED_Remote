package com.marsel.wledremote.skip

import android.content.Context
import com.marsel.wledremote.data.LocalPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Настройки автопропуска: одни на всё приложение, хранятся на телефоне. */
class SkipSettings(context: Context) {
    private val prefs = LocalPrefs(context)
    private val _enabled = MutableStateFlow(prefs.skipEnabled)
    val enabled: StateFlow<Boolean> = _enabled

    private val _recap = MutableStateFlow(prefs.skipRecap)
    val recap: StateFlow<Boolean> = _recap

    fun setRecap(value: Boolean) {
        prefs.skipRecap = value
        _recap.value = value
    }

    fun setEnabled(value: Boolean) {
        prefs.skipEnabled = value
        _enabled.value = value
    }
}
