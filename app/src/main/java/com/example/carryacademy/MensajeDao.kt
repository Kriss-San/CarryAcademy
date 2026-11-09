package com.example.carryacademy

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

class MensajeDao(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    fun insertar(mensaje: Mensaje): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("materiaId", mensaje.materiaId)
            put("texto", mensaje.texto)
            put("esUsuario", if (mensaje.esUsuario) 1 else 0)
            put("hora", mensaje.hora)
            put("timestamp", mensaje.timestamp)
        }
        return db.insert(DatabaseHelper.TABLA_MENSAJES, null, values)
    }

    fun obtenerPorMateria(materiaId: Int): List<Mensaje> {
        val lista = mutableListOf<Mensaje>()
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            DatabaseHelper.TABLA_MENSAJES,
            null,
            "materiaId = ?",
            arrayOf(materiaId.toString()),
            null, null,
            "timestamp ASC"
        )
        while (cursor.moveToNext()) {
            lista.add(
                Mensaje(
                    texto = cursor.getString(cursor.getColumnIndexOrThrow("texto")),
                    esUsuario = cursor.getInt(cursor.getColumnIndexOrThrow("esUsuario")) == 1,
                    hora = cursor.getString(cursor.getColumnIndexOrThrow("hora")),
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    materiaId = cursor.getInt(cursor.getColumnIndexOrThrow("materiaId")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                )
            )
        }
        cursor.close()
        return lista
    }
}