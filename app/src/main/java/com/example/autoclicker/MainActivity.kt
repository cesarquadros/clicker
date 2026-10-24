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
    private lateinit var maxClicksInput: EditText
    private lateinit var maxMinutesInput: EditText

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

        val limitsLabel = TextView(this).apply {
            text = "Parar automaticamente (0 = sem limite). Vale o que acontecer primeiro."
            textSize = 14f
            setPadding(0, pad, 0, 0)
        }
        maxClicksInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Parar após quantos cliques"
            val v = Prefs.getMaxClicks(this@MainActivity)
            if (v > 0) setText(v.toString())
        }
        maxMinutesInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Parar após quantos minutos"
            val v = Prefs.getMaxMinutes(this@MainActivity)
            if (v > 0) setText(v.toString())
        }

        val saveBtn = Button(this).apply {
            text = "Salvar configurações"
            setOnClickListener { saveSettings() }
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

        listOf(
            status, enableBtn, showBtn,
            intervalsLabel, intervalsInput,
            limitsLabel, maxClicksInput, maxMinutesInput,
            saveBtn, help
        ).forEach { root.addView(it) }
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
    private fun saveSettings(): Boolean {
        val text = intervalsInput.text.toString()
        if (Prefs.parseIntervals(text) == null) {
            toast("Use números separados por vírgula, cada um a partir de ${Prefs.MIN_INTERVAL_MS} ms")
            return false
        }
        // Campo vazio = 0 = sem limite.
        val maxClicks = maxClicksInput.text.toString().trim().ifEmpty { "0" }.toLongOrNull()
        val maxMinutes = maxMinutesInput.text.toString().trim().ifEmpty { "0" }.toLongOrNull()
        if (maxClicks == null || maxMinutes == null) {
            toast("Os limites devem ser números inteiros")
            return false
        }
        Prefs.setIntervalsText(this, text)
        Prefs.setLimits(this, maxClicks, maxMinutes)
        toast("Configurações salvas")
        return true
    }

    private fun showControls() {
        val service = AutoClickService.instance
        if (service == null) {
            toast("Ative o serviço de acessibilidade primeiro")
            return
        }
        if (!saveSettings()) return
        service.showOverlay()
        moveTaskToBack(true)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
