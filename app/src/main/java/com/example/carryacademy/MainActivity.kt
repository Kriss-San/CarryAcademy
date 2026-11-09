package com.example.carryacademy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var rvMaterias: RecyclerView
    private lateinit var fabAddMateria: FloatingActionButton
    private lateinit var btnRecordatorios: Button
    private lateinit var btnConfig: Button
    private lateinit var btnStats: Button
    private lateinit var adapter: MateriaAdapter
    private lateinit var materiaDao: MateriaDao
    private val listaMaterias = mutableListOf<Materia>()

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        materiaDao = MateriaDao(this)

        rvMaterias = findViewById(R.id.rv_materias)
        fabAddMateria = findViewById(R.id.fab_add_materia)
        btnRecordatorios = findViewById(R.id.btn_recordatorios)
        btnConfig = findViewById(R.id.btn_config)
        btnStats = findViewById(R.id.btn_stats)

        rvMaterias.layoutManager = LinearLayoutManager(this)

        adapter = MateriaAdapter(
            listaMaterias,
            onItemClick = { materia ->
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("MATERIA_ID", materia.id)
                    putExtra("MATERIA_NOMBRE", materia.nombre)
                }
                startActivity(intent)
            },
            onLongClick = { materia -> confirmarEliminacion(materia) }
        )
        rvMaterias.adapter = adapter

        fabAddMateria.setOnClickListener { mostrarDialogoNuevaMateria() }
        btnRecordatorios.setOnClickListener { startActivity(Intent(this, RecordatoriosActivity::class.java)) }
        btnConfig.setOnClickListener { startActivity(Intent(this, ConfiguracionActivity::class.java)) }
        btnStats.setOnClickListener { startActivity(Intent(this, EstadisticasActivity::class.java)) }

        // SOLICITAR PERMISOS DE NOTIFICACIONES (Android 13+)
        solicitarPermisosNotificaciones()
    }

    private fun solicitarPermisosNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ Notificaciones activadas", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠️ Sin permiso de notificaciones, las alarmas no sonarán", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cargarDesdeBaseDeDatos()
    }

    private fun cargarDesdeBaseDeDatos() {
        listaMaterias.clear()
        listaMaterias.addAll(materiaDao.obtenerTodas())
        adapter.notifyDataSetChanged()
    }

    private fun mostrarDialogoNuevaMateria() {
        val editText = android.widget.EditText(this).apply {
            hint = "Nombre de la materia"
            setPadding(40, 40, 40, 40)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Nueva Materia")
            .setView(editText)
            .setPositiveButton("Crear") { _, _ ->
                val nombre = editText.text.toString().trim()
                if (nombre.isNotEmpty()) {
                    materiaDao.insertar(Materia(nombre = nombre))
                    cargarDesdeBaseDeDatos()
                    Toast.makeText(this, "✅ Materia creada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Escribe un nombre", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarEliminacion(materia: Materia) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Eliminar materia")
            .setMessage("¿Seguro que quieres eliminar '${materia.nombre}'? Se borrará todo el chat.")
            .setPositiveButton("Eliminar") { _, _ ->
                materiaDao.eliminar(materia.id)
                cargarDesdeBaseDeDatos()
                Toast.makeText(this, "Materia eliminada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}