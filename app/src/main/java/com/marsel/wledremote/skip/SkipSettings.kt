package com.marsel.wledremote.skip

import android.content.Context
import com.marsel.wledremote.data.LocalPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Галочка "Автопропуск": одна на всё приложение, хранится на телефоне. */
class SkipSettings(context: Context) {
    private val prefs = LocalPrefs(context)
    private val _enabled = MutableStateFlow(prefs.skipEnabled)
    val enabled: StateFlow<Boolean> = _enabled

    fun setEnabled(value: Boolean) {
        prefs.skipEnabled = value
        _enabled.value = value
    }
}
