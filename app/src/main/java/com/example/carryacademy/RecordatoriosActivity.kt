package com.example.carryacademy

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RecordatoriosActivity : AppCompatActivity() {

    private lateinit var rvRecordatorios: RecyclerView
    private lateinit var fabAddRecordatorio: FloatingActionButton
    private lateinit var adapter: RecordatorioAdapter
    private lateinit var recordatorioDao: RecordatorioDao
    private lateinit var materiaDao: MateriaDao
    private lateinit var estadisticaDao: EstadisticaDao
    private val listaRecordatorios = mutableListOf<Recordatorio>()
    private var materiasMap: Map<Int, String> = emptyMap() // id -> nombre

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recordatorios)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_recordatorios)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recordatorioDao = RecordatorioDao(this)
        materiaDao = MateriaDao(this)
        estadisticaDao = EstadisticaDao(this)

        rvRecordatorios = findViewById(R.id.rv_recordatorios)
        rvRecordatorios.layoutManager = LinearLayoutManager(this)

        adapter = RecordatorioAdapter(
            listaRecordatorios,
            materiasMap,
            onCompletadoChanged = { recordatorio, completado ->
                recordatorioDao.actualizarCompletado(recordatorio.id, completado)
                if (completado) {
                    estadisticaDao.registrarComplecionHoy()
                    MotorNotificaciones.cancelar(this, recordatorio)
                } else {
                    MotorNotificaciones.programarInicial(this, recordatorio)
                }
            },
            onEliminar = { recordatorio -> confirmarEliminacion(recordatorio) }
        )
        rvRecordatorios.adapter = adapter

        fabAddRecordatorio = findViewById(R.id.fab_add_recordatorio)
        fabAddRecordatorio.setOnClickListener { mostrarDialogoNuevoRecordatorio() }
    }

    override fun onResume() {
        super.onResume()
        cargarDesdeBaseDeDatos()
    }

    private fun cargarDesdeBaseDeDatos() {
        // Cargar materias y construir el mapa id -> nombre
        val materias = materiaDao.obtenerTodas()
        materiasMap = materias.associate { it.id to it.nombre }

        // Actualizar el adapter con el nuevo mapa (recreamos el adapter para que refresque)
        adapter = RecordatorioAdapter(
            listaRecordatorios,
            materiasMap,
            onCompletadoChanged = { recordatorio, completado ->
                recordatorioDao.actualizarCompletado(recordatorio.id, completado)
                if (completado) {
                    estadisticaDao.registrarComplecionHoy()
                    MotorNotificaciones.cancelar(this, recordatorio)
                } else {
                    MotorNotificaciones.programarInicial(this, recordatorio)
                }
            },
            onEliminar = { recordatorio -> confirmarEliminacion(recordatorio) }
        )
        rvRecordatorios.adapter = adapter

        // Cargar recordatorios
        listaRecordatorios.clear()
        listaRecordatorios.addAll(recordatorioDao.obtenerTodos())
        adapter.notifyDataSetChanged()
    }

    private fun mostrarDialogoNuevoRecordatorio() {
        val vista = layoutInflater.inflate(R.layout.dialogo_nuevo_recordatorio, null)
        val etTitulo = vista.findViewById<EditText>(R.id.et_dialog_titulo)
        val btnFecha = vista.findViewById<Button>(R.id.btn_dialog_fecha)
        val tvFecha = vista.findViewById<TextView>(R.id.tv_dialog_fecha)
        val btnHora = vista.findViewById<Button>(R.id.btn_dialog_hora)
        val tvHora = vista.findViewById<TextView>(R.id.tv_dialog_hora)

        var fechaSeleccionada = ""
        var horaSel = 8
        var minSel = 0

        btnFecha.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    fechaSeleccionada = String.format("%02d/%02d/%d", day, month + 1, year)
                    tvFecha.text = "Fecha: $fechaSeleccionada"
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnHora.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    horaSel = hour
                    minSel = minute
                    tvHora.text = String.format("Hora límite: %02d:%02d", hour, minute)
                },
                horaSel, minSel, false
            ).show()
        }

        AlertDialog.Builder(this)
            .setTitle("Nuevo Recordatorio")
            .setView(vista)
            .setPositiveButton("Guardar") { _, _ ->
                val titulo = etTitulo.text.toString().trim()
                when {
                    titulo.isEmpty() ->
                        Toast.makeText(this, "Escribe un título", Toast.LENGTH_SHORT).show()
                    fechaSeleccionada.isEmpty() ->
                        Toast.makeText(this, "Selecciona una fecha límite", Toast.LENGTH_SHORT).show()
                    else -> {
                        val fechaCompleta = String.format("%s %02d:%02d", fechaSeleccionada, horaSel, minSel)
                        val millis = millisDeFecha(fechaCompleta)
                        val idNuevo = recordatorioDao.insertar(
                            Recordatorio(titulo = titulo, fecha = fechaCompleta, fechaMillis = millis)
                        )
                        if (idNuevo == -1L) {
                            Toast.makeText(this, "⚠️ Error al guardar en la BD", Toast.LENGTH_SHORT).show()
                        } else {
                            // Capturamos el valor devuelto por programarInicial (Long?)
                            val programada = MotorNotificaciones.programarInicial(
                                this,
                                Recordatorio(id = idNuevo.toInt(), titulo = titulo, fecha = fechaCompleta, fechaMillis = millis)
                            )
                            cargarDesdeBaseDeDatos()

                            // Toast de debug: muestra la hora exacta a la que se programó la alarma
                            if (programada != null) {
                                val formato = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                val horaProgramada = formato.format(Date(programada))
                                Toast.makeText(this, "⏰ Alarma programada para: $horaProgramada", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this, "⚠️ El motor NO programó alarma (fecha inválida)", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarEliminacion(recordatorio: Recordatorio) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar recordatorio")
            .setMessage("¿Seguro que quieres eliminar '${recordatorio.titulo}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                MotorNotificaciones.cancelar(this, recordatorio)
                recordatorioDao.eliminar(recordatorio.id)
                cargarDesdeBaseDeDatos()
                Toast.makeText(this, "Recordatorio eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun millisDeFecha(fecha: String): Long {
        val partes = fecha.split(" ")
        val fechaParte = partes[0].split("/")
        if (fechaParte.size != 3) return 0L
        var hora = 8
        var minuto = 0
        if (partes.size >= 2) {
            val hm = partes[1].split(":")
            hora = hm.getOrNull(0)?.toIntOrNull() ?: 8
            minuto = hm.getOrNull(1)?.toIntOrNull() ?: 0
        }
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, fechaParte[0].toIntOrNull() ?: return 0L)
        cal.set(Calendar.MONTH, (fechaParte[1].toIntOrNull() ?: 1) - 1)
        cal.set(Calendar.YEAR, fechaParte[2].toIntOrNull() ?: return 0L)
        cal.set(Calendar.HOUR_OF_DAY, hora)
        cal.set(Calendar.MINUTE, minuto)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}