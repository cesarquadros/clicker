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
    private const val KEY_SWIPE_MS = "swipe_ms"

    const val DEFAULT_INTERVAL_MS = 100L
    const val MIN_INTERVAL_MS = 20L

    const val DEFAULT_SWIPE_MS = 300L
    const val MIN_SWIPE_MS = 50L
    const val MAX_SWIPE_MS = 5000L

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

    /** Alvo salvo: toque (x, y) ou deslize (x, y) até (x2, y2). */
    data class SavedTarget(
        val swipe: Boolean,
        val x: Int,
        val y: Int,
        val x2: Int = 0,
        val y2: Int = 0
    )

    /**
     * Alvos salvos, na ordem. Formato: "T,x,y" (toque) e "S,x,y,x2,y2" (deslize),
     * separados por ";".
     */
    fun getSavedTargets(ctx: Context): List<SavedTarget> {
        val raw = prefs(ctx).getString(KEY_TARGETS, "") ?: ""
        return raw.split(';').mapNotNull { item ->
            val p = item.split(',')
            val n = p.drop(1).map { it.toIntOrNull() }
            when {
                p.size == 3 && p[0] == "T" && n.none { it == null } ->
                    SavedTarget(false, n[0]!!, n[1]!!)
                p.size == 5 && p[0] == "S" && n.none { it == null } ->
                    SavedTarget(true, n[0]!!, n[1]!!, n[2]!!, n[3]!!)
                else -> null
            }
        }
    }

    fun setSavedTargets(ctx: Context, targets: List<SavedTarget>) {
        val raw = targets.joinToString(";") {
            if (it.swipe) "S,${it.x},${it.y},${it.x2},${it.y2}" else "T,${it.x},${it.y}"
        }
        prefs(ctx).edit().putString(KEY_TARGETS, raw).apply()
    }

    /** Duração do deslize, em ms. */
    fun getSwipeDuration(ctx: Context): Long =
        prefs(ctx).getLong(KEY_SWIPE_MS, DEFAULT_SWIPE_MS).coerceIn(MIN_SWIPE_MS, MAX_SWIPE_MS)

    fun setSwipeDuration(ctx: Context, ms: Long) {
        prefs(ctx).edit().putLong(KEY_SWIPE_MS, ms.coerceIn(MIN_SWIPE_MS, MAX_SWIPE_MS)).apply()
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
