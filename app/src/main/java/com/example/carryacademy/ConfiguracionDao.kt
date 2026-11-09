package com.example.carryacademy

import android.content.ContentValues
import android.content.Context

class ConfiguracionDao(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    // READ: lee la fila única de configuración
    fun obtener(): ConfiguracionUsuario {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLA_CONFIGURACION,
            null, "id = ?", arrayOf("1"), null, null, null
        )
        var config = ConfiguracionUsuario()
        if (cursor.moveToFirst()) {
            config = ConfiguracionUsuario(
                id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                nivelInsistencia = cursor.getString(cursor.getColumnIndexOrThrow("nivelInsistencia")),
                notificacionesActivas = cursor.getInt(cursor.getColumnIndexOrThrow("notificacionesActivas")) == 1
            )
        }
        cursor.close()
        return config
    }

    // UPDATE: guarda el nivel y el switch en la BD
    fun guardar(nivel: String, notificaciones: Boolean) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", 1)
            put("nivelInsistencia", nivel)
            put("notificacionesActivas", if (notificaciones) 1 else 0)
        }
        val filas = db.update(DatabaseHelper.TABLA_CONFIGURACION, values, "id = ?", arrayOf("1"))
        if (filas == 0) db.insert(DatabaseHelper.TABLA_CONFIGURACION, null, values)
    }
}