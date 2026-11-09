package com.example.carryacademy

import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar

class ConfiguracionActivity : AppCompatActivity() {

    private lateinit var configDao: ConfiguracionDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracion)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_configuracion)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        configDao = ConfiguracionDao(this)

        val rgNiveles = findViewById<RadioGroup>(R.id.rg_niveles)
        val switchNotif = findViewById<SwitchCompat>(R.id.switch_notificaciones)
        val btnGuardar = findViewById<Button>(R.id.btn_guardar_config)
        val tvActual = findViewById<TextView>(R.id.tv_config_actual)
        val tvDescripcion = findViewById<TextView>(R.id.tv_descripcion_nivel)

        val config = configDao.obtener()
        tvActual.text = "Nivel guardado: ${config.nivelInsistencia}"
        tvDescripcion.text = descripcionNivel(config.nivelInsistencia)
        when (config.nivelInsistencia) {
            "Tranquilo" -> rgNiveles.check(R.id.rb_tranquilo)
            "Normal" -> rgNiveles.check(R.id.rb_normal)
            "Estricto" -> rgNiveles.check(R.id.rb_estricto)
            "Toxico" -> rgNiveles.check(R.id.rb_toxico)
        }
        switchNotif.isChecked = config.notificacionesActivas

        // Al tocar cada nivel, se muestra su comportamiento exacto
        rgNiveles.setOnCheckedChangeListener { _, checkedId ->
            val nivel = when (checkedId) {
                R.id.rb_tranquilo -> "Tranquilo"
                R.id.rb_normal -> "Normal"
                R.id.rb_estricto -> "Estricto"
                R.id.rb_toxico -> "Toxico"
                else -> ""
            }
            if (nivel.isNotEmpty()) tvDescripcion.text = descripcionNivel(nivel)
        }

        btnGuardar.setOnClickListener {
            val nivel = when (rgNiveles.checkedRadioButtonId) {
                R.id.rb_tranquilo -> "Tranquilo"
                R.id.rb_normal -> "Normal"
                R.id.rb_estricto -> "Estricto"
                R.id.rb_toxico -> "Toxico"
                else -> ""
            }
            if (nivel.isEmpty()) {
                Toast.makeText(this, "Selecciona un nivel", Toast.LENGTH_SHORT).show()
            } else {
                configDao.guardar(nivel, switchNotif.isChecked)
                tvActual.text = "Nivel guardado: $nivel"
                Toast.makeText(this, "✅ Configuración guardada en la BD", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun descripcionNivel(nivel: String): String = when (nivel) {
        "Tranquilo" -> "😇 Tranquilo: 1 aviso al día con plazos lejanos y 2 el último día. Si una tarea se vence, solo 1 aviso suave y sin repeticiones."
        "Normal" -> "🙂 Normal: 2 avisos al día con plazos lejanos; 4 al día en los últimos 3 días; en las últimas 24 h, 4 avisos repartidos + 1 final 30 min antes. Si se vence: 1 aviso diario a las 6 p.m."
        "Estricto" -> "😤 Estricto: 3 a 5 avisos al día según urgencia, con aviso final 15 min antes del plazo. Si se vence: insiste cada 4 horas hasta que la completes."
        "Toxico" -> "🤬 Tóxico: hasta 8 avisos al día, aviso final 10 min antes y EN el plazo. Si se vence: te rostea cada 2 horas hasta que la completes. Elige este nivel solo si aceptas el trato."
        else -> "Selecciona un nivel para ver cómo se comportarán las notificaciones."
    }
}