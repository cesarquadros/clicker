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

/** Tela de configuração: intervalo, ativação do serviço e botão para exibir os controles. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var intervalInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this).apply { textSize = 16f }

        val intervalLabel = TextView(this).apply {
            text = "Intervalo entre cliques (ms, mínimo ${Prefs.MIN_INTERVAL_MS})"
            textSize = 14f
            setPadding(0, pad, 0, 0)
        }
        intervalInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(Prefs.getInterval(this@MainActivity).toString())
            gravity = Gravity.START
        }
        val saveBtn = Button(this).apply {
            text = "Salvar intervalo"
            setOnClickListener { saveInterval() }
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
            text = "Como usar: arraste o círculo vermelho até o ponto que deve ser clicado, " +
                "abra o jogo e toque em ▶ no painel flutuante. Toque em ⏸ para pausar e " +
                "arraste o painel pelo ícone ⠿."
            textSize = 13f
            setPadding(0, pad, 0, 0)
        }

        listOf(status, enableBtn, showBtn, intervalLabel, intervalInput, saveBtn, help)
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

    private fun saveInterval() {
        val ms = intervalInput.text.toString().toLongOrNull()
        if (ms == null || ms < Prefs.MIN_INTERVAL_MS) {
            toast("Informe um número a partir de ${Prefs.MIN_INTERVAL_MS} ms")
            return
        }
        Prefs.setInterval(this, ms)
        toast("Intervalo salvo: $ms ms")
    }

    private fun showControls() {
        val service = AutoClickService.instance
        if (service == null) {
            toast("Ative o serviço de acessibilidade primeiro")
            return
        }
        saveInterval()
        service.showOverlay()
        moveTaskToBack(true)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
