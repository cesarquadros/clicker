package com.example.autoclicker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Tela de configuração: intervalos por alvo, ativação do serviço e botão para exibir os controles. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var intervalsInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this).apply { textSize = 16f }

        val intervalsLabel = TextView(this).apply {
            text = "Intervalo de cada alvo, em ms, separados por vírgula " +
                "(mínimo ${Prefs.MIN_INTERVAL_MS}). Ex.: 100,30000 = alvo 1 a cada 100 ms " +
                "e alvo 2 a cada 30 s. Alvos além da lista usam o último valor."
            textSize = 14f
            setPadding(0, pad, 0, 0)
        }
        intervalsInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789, ")
            setText(Prefs.getIntervalsText(this@MainActivity))
            gravity = Gravity.START
        }
        val saveBtn = Button(this).apply {
            text = "Salvar intervalos"
            setOnClickListener { saveIntervals() }
        }

        val enableBtn = Button(this).apply {
            text = "1. Ativar serviço de acessibilidade"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val showBtn = Button(this).apply {
            text = "2. Mostrar controles na tela"
            setOnClickListener { showControls() }
        }

        val help = TextView(this).apply {
            text = "Como usar: arraste os círculos numerados até os pontos que devem ser " +
                "clicados (use + e − no painel para adicionar ou remover alvos), abra o jogo e " +
                "toque em ▶. Toque em ⏸ para pausar e arraste o painel pelo ícone ⠿."
            textSize = 13f
            setPadding(0, pad, 0, 0)
        }

        listOf(status, enableBtn, showBtn, intervalsLabel, intervalsInput, saveBtn, help)
            .forEach { root.addView(it) }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        status.text = if (AutoClickService.instance != null)
            "Serviço: ATIVO ✓" else "Serviço: desativado"
    }

    /** Valida e salva. Retorna false (com aviso) se o texto for inválido. */
    private fun saveIntervals(): Boolean {
        val text = intervalsInput.text.toString()
        if (Prefs.parseIntervals(text) == null) {
            toast("Use números separados por vírgula, cada um a partir de ${Prefs.MIN_INTERVAL_MS} ms")
            return false
        }
        Prefs.setIntervalsText(this, text)
        toast("Intervalos salvos")
        return true
    }

    private fun showControls() {
        val service = AutoClickService.instance
        if (service == null) {
            toast("Ative o serviço de acessibilidade primeiro")
            return
        }
        if (!saveIntervals()) return
        service.showOverlay()
        moveTaskToBack(true)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
