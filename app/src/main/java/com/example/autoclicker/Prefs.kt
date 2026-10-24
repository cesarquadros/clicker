package com.example.autoclicker

import android.content.Context

/** Configurações salvas do app. */
object Prefs {
    private const val FILE = "autoclicker_prefs"
    private const val KEY_INTERVALS = "intervals_ms"
    private const val KEY_INTERVAL_LEGACY = "interval_ms"
    private const val KEY_MAX_CLICKS = "max_clicks"
    private const val KEY_MAX_MINUTES = "max_minutes"

    const val DEFAULT_INTERVAL_MS = 100L
    const val MIN_INTERVAL_MS = 20L

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Texto dos intervalos, ex.: "100,30000" (1º alvo = 100 ms, 2º alvo = 30 s). */
    fun getIntervalsText(ctx: Context): String =
        prefs(ctx).getString(KEY_INTERVALS, null)
            ?: prefs(ctx).getLong(KEY_INTERVAL_LEGACY, DEFAULT_INTERVAL_MS).toString()

    fun setIntervalsText(ctx: Context, text: String) {
        prefs(ctx).edit().putString(KEY_INTERVALS, text.trim()).apply()
    }

    /** Converte "100, 30000" em [100, 30000]. Retorna null se algum valor for inválido. */
    fun parseIntervals(text: String): List<Long>? {
        val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val values = parts.map { it.toLongOrNull() ?: return null }
        if (values.any { it < MIN_INTERVAL_MS }) return null
        return values
    }

    fun getIntervals(ctx: Context): List<Long> =
        parseIntervals(getIntervalsText(ctx)) ?: listOf(DEFAULT_INTERVAL_MS)

    /** Limite de cliques por sessão (0 = sem limite). */
    fun getMaxClicks(ctx: Context): Long = prefs(ctx).getLong(KEY_MAX_CLICKS, 0L)

    /** Limite de tempo por sessão, em minutos (0 = sem limite). */
    fun getMaxMinutes(ctx: Context): Long = prefs(ctx).getLong(KEY_MAX_MINUTES, 0L)

    fun setLimits(ctx: Context, maxClicks: Long, maxMinutes: Long) {
        prefs(ctx).edit()
            .putLong(KEY_MAX_CLICKS, maxClicks.coerceAtLeast(0L))
            .putLong(KEY_MAX_MINUTES, maxMinutes.coerceAtLeast(0L))
            .apply()
    }

    /** Intervalo do alvo de posição [index]; alvos além da lista usam o último valor. */
    fun intervalFor(ctx: Context, index: Int): Long {
        val list = getIntervals(ctx)
        return list[index.coerceIn(0, list.lastIndex)]
    }
}
