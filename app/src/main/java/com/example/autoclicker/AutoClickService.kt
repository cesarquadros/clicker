package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
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
import kotlin.math.abs

/**
 * Serviço de acessibilidade que:
 *  1. desenha dois overlays: um painel de controle (mover / iniciar / fechar)
 *     e um alvo circular que você arrasta até o ponto a ser clicado;
 *  2. executa toques repetidos no centro do alvo via dispatchGesture().
 */
class AutoClickService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var playButton: TextView? = null

    private var target: View? = null
    private var targetParams: WindowManager.LayoutParams? = null

    private var running = false

    private val clickLoop = object : Runnable {
        override fun run() {
            if (!running) return
            performTap()
            handler.postDelayed(this, Prefs.getInterval(this@AutoClickService))
        }
    }

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
        if (panel != null) return
        showTarget()
        showPanel()
    }

    fun hideOverlay() {
        stopClicking()
        panel?.let { runCatching { wm.removeView(it) } }
        target?.let { runCatching { wm.removeView(it) } }
        panel = null
        target = null
        panelParams = null
        targetParams = null
        playButton = null
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

    private fun showTarget() {
        val size = dp(56)
        val view = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(70, 255, 59, 48))
                setStroke(dp(3), Color.argb(230, 255, 59, 48))
            }
        }
        val dm = resources.displayMetrics
        val params = overlayParams(size, size, dm.widthPixels / 2 - size / 2, dm.heightPixels / 2 - size / 2)
        attachDrag(view, params, onClick = null)
        wm.addView(view, params)
        target = view
        targetParams = params
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
        val close = button("✕", 18)
        playButton = play

        bar.addView(handle)
        bar.addView(play)
        bar.addView(close)

        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            dp(12), dp(120)
        )
        attachDrag(handle, params, onClick = null, moved = bar)
        play.setOnClickListener { if (running) stopClicking() else startClicking() }
        close.setOnClickListener { hideOverlay() }

        wm.addView(bar, params)
        panel = bar
        panelParams = params
    }

    /**
     * Permite arrastar a janela tocando em [handle].
     * [moved] é a view cuja janela deve se mover (por padrão, o próprio handle).
     */
    private fun attachDrag(
        handle: View,
        params: WindowManager.LayoutParams,
        onClick: (() -> Unit)?,
        moved: View = handle
    ) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false
        val slop = dp(6)

        handle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = ev.rawX; touchY = ev.rawY
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
                    if (!dragged) onClick?.invoke()
                    true
                }
                else -> false
            }
        }
    }

    // ---------------------------------------------------------------- cliques

    private fun startClicking() {
        val t = target ?: return
        val tp = targetParams ?: return
        running = true
        // Deixa o toque "atravessar" o alvo, senão ele interceptaria os cliques injetados.
        tp.flags = tp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { wm.updateViewLayout(t, tp) }
        playButton?.text = "⏸"
        handler.post(clickLoop)
    }

    private fun stopClicking() {
        running = false
        handler.removeCallbacks(clickLoop)
        val t = target
        val tp = targetParams
        if (t != null && tp != null) {
            tp.flags = tp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { wm.updateViewLayout(t, tp) }
        }
        playButton?.text = "▶"
    }

    private fun performTap() {
        val t = target ?: return
        val loc = IntArray(2)
        t.getLocationOnScreen(loc)
        val x = loc[0] + t.width / 2f
        val y = loc[1] + t.height / 2f

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 20)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        /** Instância ativa enquanto o serviço estiver habilitado nas configurações. */
        @Volatile
        var instance: AutoClickService? = null
            private set
    }
}
