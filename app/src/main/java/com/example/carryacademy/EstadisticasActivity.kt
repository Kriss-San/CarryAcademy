package com.example.carryacademy

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class EstadisticasActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_estadisticas)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_estadisticas)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    // Se recalcula cada vez que entras a la pantalla
    override fun onResume() {
        super.onResume()
        cargarEstadisticas()
    }

    private fun cargarEstadisticas() {
        val estadisticaDao = EstadisticaDao(this)
        val estadistica = estadisticaDao.obtener()

        val completadas = estadisticaDao.contarCompletadas()
        val pendientes = estadisticaDao.contarPendientes()
        val total = completadas + pendientes
        val efectividad = if (total > 0) (completadas * 100) / total else 0

        findViewById<TextView>(R.id.tv_racha).text =
            "${estadistica.diasRacha} ${if (estadistica.diasRacha == 1) "día" else "días"}"
        findViewById<TextView>(R.id.tv_tareas_completadas).text = completadas.toString()
        findViewById<TextView>(R.id.tv_tareas_pendientes).text = pendientes.toString()
        findViewById<TextView>(R.id.tv_porcentaje).text = "$efectividad%"
        findViewById<TextView>(R.id.tv_materias).text = estadisticaDao.contarMaterias().toString()
        findViewById<TextView>(R.id.tv_mensajes).text = estadisticaDao.contarMensajes().toString()
    }
}