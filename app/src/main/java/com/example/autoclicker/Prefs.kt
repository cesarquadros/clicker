package com.example.autoclicker

import android.content.Context

/** Configurações salvas do app. */
object Prefs {
    private const val FILE = "autoclicker_prefs"
    private const val KEY_INTERVALS = "intervals_ms"
    private const val KEY_INTERVAL_LEGACY = "interval_ms"
    private const val KEY_MAX_CLICKS = "max_clicks"
    private const val KEY_MAX_MINUTES = "max_minutes"
    private const val KEY_TARGETS = "target_positions"
    private const val KEY_PANEL = "panel_position"

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

    // ------------------------------------------------------------ posições salvas

    /**
     * Posições (x, y) dos alvos, na ordem. Formato salvo: "T,x,y;T,x,y".
     * O prefixo "T" (toque) deixa espaço para outros tipos de alvo no futuro.
     */
    fun getTargetPositions(ctx: Context): List<Pair<Int, Int>> {
        val raw = prefs(ctx).getString(KEY_TARGETS, "") ?: ""
        return raw.split(';').mapNotNull { item ->
            val p = item.split(',')
            if (p.size == 3 && p[0] == "T") {
                val x = p[1].toIntOrNull()
                val y = p[2].toIntOrNull()
                if (x != null && y != null) x to y else null
            } else {
                null
            }
        }
    }

    fun setTargetPositions(ctx: Context, positions: List<Pair<Int, Int>>) {
        val raw = positions.joinToString(";") { "T,${it.first},${it.second}" }
        prefs(ctx).edit().putString(KEY_TARGETS, raw).apply()
    }

    fun getPanelPosition(ctx: Context): Pair<Int, Int>? {
        val p = (prefs(ctx).getString(KEY_PANEL, null) ?: return null).split(',')
        if (p.size != 2) return null
        val x = p[0].toIntOrNull() ?: return null
        val y = p[1].toIntOrNull() ?: return null
        return x to y
    }

    fun setPanelPosition(ctx: Context, x: Int, y: Int) {
        prefs(ctx).edit().putString(KEY_PANEL, "$x,$y").apply()
    }

    /** Esquece as posições salvas (alvos e painel). */
    fun clearPositions(ctx: Context) {
        prefs(ctx).edit().remove(KEY_TARGETS).remove(KEY_PANEL).apply()
    }

    /** Intervalo do alvo de posição [index]; alvos além da lista usam o último valor. */
    fun intervalFor(ctx: Context, index: Int): Long {
        val list = getIntervals(ctx)
        return list[index.coerceIn(0, list.lastIndex)]
    }
}
