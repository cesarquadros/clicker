package com.example.autoclicker

import android.content.Context

/** Configurações salvas do app. */
object Prefs {
    private const val FILE = "autoclicker_prefs"
    private const val KEY_INTERVAL = "interval_ms"

    const val DEFAULT_INTERVAL_MS = 100L
    const val MIN_INTERVAL_MS = 20L

    fun getInterval(ctx: Context): Long =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getLong(KEY_INTERVAL, DEFAULT_INTERVAL_MS)
            .coerceAtLeast(MIN_INTERVAL_MS)

    fun setInterval(ctx: Context, ms: Long) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_INTERVAL, ms.coerceAtLeast(MIN_INTERVAL_MS))
            .apply()
    }
}
