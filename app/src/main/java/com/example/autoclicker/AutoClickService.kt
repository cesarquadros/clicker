package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Serviço de acessibilidade que:
 *  1. desenha overlays: um painel de controle e vários alvos numerados que você
 *     arrasta até os pontos a serem clicados;
 *  2. executa toques repetidos no centro de cada alvo via dispatchGesture(),
 *     cada alvo com o seu próprio intervalo (ver [Prefs.intervalFor]).
 */
class AutoClickService : AccessibilityService() {

    private class Target(val view: TextView, val params: WindowManager.LayoutParams)

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var panel: View? = null
    private var playButton: TextView? = null
    private val targets = mutableListOf<Target>()

    private var running = false

    /** Um timer por alvo (índice -> runnable que agenda o próximo toque). */
    private val timers = mutableMapOf<Int, Runnable>()

    /** Fila de toques: o Android só aceita um gesto por vez, então enfileiramos. */
    private val tapQueue = ArrayDeque<Int>()
    private var dispatching = false

    /** Cliques disparados na sessão atual (para o limite de cliques). */
    private var clickCount = 0L

    /** Motivo de uma parada automática pendente; aplicada quando o toque em curso terminar. */
    private var pendingStopReason: String? = null

    private val timeLimitRunnable = Runnable { requestStop("Tempo limite atingido") }

    // ---------------------------------------------------------------- ciclo de vida

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        stopClicking()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hideOverlay()
        instance = null
        return super.onUnbind(intent)
    }

    // ---------------------------------------------------------------- overlay

    fun isOverlayVisible() = panel != null

    fun showOverlay() {
        if (panel != null || !::wm.isInitialized) return
        // Restaura os alvos salvos; sem nada salvo, começa com um alvo no centro.
        val saved = Prefs.getTargetPositions(this)
        if (saved.isEmpty()) {
            addTarget()
        } else {
            saved.take(MAX_TARGETS).forEach { addTarget(it) }
        }
        showPanel()
    }

    private fun savePositions() {
        Prefs.setTargetPositions(this, targets.map { it.params.x to it.params.y })
    }

    private fun savePanelPosition() {
        val p = panel ?: return
        val lp = p.layoutParams as? WindowManager.LayoutParams ?: return
        Prefs.setPanelPosition(this, lp.x, lp.y)
    }

    fun hideOverlay() {
        stopClicking()
        panel?.let { runCatching { wm.removeView(it) } }
        targets.forEach { t -> runCatching { wm.removeView(t.view) } }
        panel = null
        playButton = null
        targets.clear()
    }

    private fun overlayParams(w: Int, h: Int, x: Int, y: Int) =
        WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

    /** Adiciona um alvo; [saved] é a posição (x, y) guardada, ou null para a posição padrão. */
    private fun addTarget(saved: Pair<Int, Int>? = null) {
        if (targets.size >= MAX_TARGETS) {
            toast("Máximo de $MAX_TARGETS alvos")
            return
        }
        val index = targets.size
        val size = dp(52)
        val color = TARGET_COLORS[index % TARGET_COLORS.size]

        val view = TextView(this).apply {
            text = (index + 1).toString()
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)))
                setStroke(dp(3), color)
            }
        }
        val dm = resources.displayMetrics
        // Mantém o alvo dentro da tela, caso a resolução/orientação tenha mudado.
        val x = saved?.first?.coerceIn(0, (dm.widthPixels - size).coerceAtLeast(0))
            ?: (dm.widthPixels / 2 - size / 2)
        val y = saved?.second?.coerceIn(0, (dm.heightPixels - size).coerceAtLeast(0))
            ?: (dm.heightPixels / 3 + index * dp(64))
        val params = overlayParams(size, size, x, y)
        attachDrag(view, params, onDragEnd = { savePositions() })
        wm.addView(view, params)
        targets.add(Target(view, params))
    }

    private fun removeLastTarget() {
        if (running) {
            toast("Pause antes de remover alvos")
            return
        }
        if (targets.size <= 1) return
        val last = targets.removeAt(targets.lastIndex)
        runCatching { wm.removeView(last.view) }
        savePositions()
    }

    private fun showPanel() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(Color.argb(230, 33, 33, 33))
            }
        }

        fun button(label: String, size: Int = 22) = TextView(this).apply {
            text = label
            textSize = size.toFloat()
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            minWidth = dp(44)
            minHeight = dp(44)
        }

        val handle = button("⠿")
        val play = button("▶")
        val add = button("+")
        val remove = button("−")
        val close = button("✕", 18)
        playButton = play

        listOf(handle, play, add, remove, close).forEach { bar.addView(it) }

        val dm = resources.displayMetrics
        val savedPanel = Prefs.getPanelPosition(this)
        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            savedPanel?.first?.coerceIn(0, (dm.widthPixels - dp(44)).coerceAtLeast(0)) ?: dp(12),
            savedPanel?.second?.coerceIn(0, (dm.heightPixels - dp(44)).coerceAtLeast(0)) ?: dp(120)
        )
        attachDrag(handle, params, moved = bar, onDragEnd = { savePanelPosition() })
        play.setOnClickListener { if (running) stopClicking() else startClicking() }
        add.setOnClickListener {
            if (running) {
                toast("Pause antes de adicionar alvos")
            } else {
                addTarget()
                savePositions()
            }
        }
        remove.setOnClickListener { removeLastTarget() }
        close.setOnClickListener { hideOverlay() }

        wm.addView(bar, params)
        panel = bar
    }

    /**
     * Permite arrastar a janela [moved] (por padrão, o próprio [handle]) ao
     * arrastar o dedo sobre [handle].
     */
    private fun attachDrag(
        handle: View,
        params: WindowManager.LayoutParams,
        moved: View = handle,
        onDragEnd: (() -> Unit)? = null
    ) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        val slop = dp(6)
        var dragged = false

        handle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = ev.rawX
                    touchY = ev.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - touchX).toInt()
                    val dy = (ev.rawY - touchY).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) dragged = true
                    if (dragged) {
                        params.x = startX + dx
                        params.y = startY + dy
                        runCatching { wm.updateViewLayout(moved, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) onDragEnd?.invoke()
                    true
                }
                else -> false
            }
        }
    }

    // ---------------------------------------------------------------- cliques

    private fun setTouchable(t: Target, touchable: Boolean) {
        t.params.flags = if (touchable) {
            t.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            t.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        runCatching { wm.updateViewLayout(t.view, t.params) }
    }

    private fun startClicking() {
        if (targets.isEmpty()) return
        running = true
        dispatching = false
        clickCount = 0L
        pendingStopReason = null
        tapQueue.clear()
        val maxMinutes = Prefs.getMaxMinutes(this)
        if (maxMinutes > 0) handler.postDelayed(timeLimitRunnable, maxMinutes * 60_000L)
        // Deixa o toque "atravessar" os alvos, senão eles interceptariam os cliques injetados.
        targets.forEach { setTouchable(it, false) }
        playButton?.text = "⏸"
        targets.indices.forEach { scheduleTarget(it) }
    }

    private fun stopClicking() {
        running = false
        pendingStopReason = null
        handler.removeCallbacks(timeLimitRunnable)
        timers.values.forEach { handler.removeCallbacks(it) }
        timers.clear()
        tapQueue.clear()
        targets.forEach { setTouchable(it, true) }
        playButton?.text = "▶"
    }

    /** Dispara o 1º toque do alvo já e repete conforme o intervalo configurado para ele. */
    private fun scheduleTarget(index: Int) {
        val timer = object : Runnable {
            override fun run() {
                if (!running) return
                enqueueTap(index)
                handler.postDelayed(this, Prefs.intervalFor(this@AutoClickService, index))
            }
        }
        timers[index] = timer
        handler.post(timer)
    }

    private fun enqueueTap(index: Int) {
        if (pendingStopReason != null) return
        if (index !in tapQueue) tapQueue.addLast(index)
        drainQueue()
    }

    private fun drainQueue() {
        if (dispatching || tapQueue.isEmpty()) return
        val index = tapQueue.removeFirst()
        val target = targets.getOrNull(index)
        if (target == null) {
            drainQueue()
            return
        }

        val loc = IntArray(2)
        target.view.getLocationOnScreen(loc)
        val x = loc[0] + target.view.width / 2f
        val y = loc[1] + target.view.height / 2f

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 20)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatching = true
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onTapFinished()
            override fun onCancelled(gestureDescription: GestureDescription?) = onTapFinished()
        }, handler)
        if (!accepted) {
            dispatching = false
            return
        }

        clickCount++
        val maxClicks = Prefs.getMaxClicks(this)
        if (maxClicks > 0 && clickCount >= maxClicks) {
            requestStop("Limite de $maxClicks cliques atingido")
        }
    }

    private fun onTapFinished() {
        dispatching = false
        val reason = pendingStopReason
        if (reason != null) {
            stopWithMessage(reason)
            return
        }
        if (running) drainQueue()
    }

    // ---------------------------------------------------------------- parada automática

    /** Para a sessão; se um toque estiver em curso, espera ele terminar para não cair no alvo. */
    private fun requestStop(reason: String) {
        if (!running) return
        if (dispatching) {
            pendingStopReason = reason
        } else {
            stopWithMessage(reason)
        }
    }

    private fun stopWithMessage(reason: String) {
        val total = clickCount
        stopClicking()
        toast("$reason ($total cliques)")
    }

    // ---------------------------------------------------------------- utilidades

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_TARGETS = 8

        private val TARGET_COLORS = intArrayOf(
            Color.rgb(255, 59, 48),   // vermelho
            Color.rgb(255, 149, 0),   // laranja
            Color.rgb(10, 132, 255),  // azul
            Color.rgb(52, 199, 89),   // verde
            Color.rgb(175, 82, 222),  // roxo
            Color.rgb(0, 199, 190),   // turquesa
            Color.rgb(255, 45, 85),   // rosa
            Color.rgb(142, 142, 147)  // cinza
        )

        /** Instância ativa enquanto o serviço estiver habilitado nas configurações. */
        @Volatile
        var instance: AutoClickService? = null
            private set
    }
}
