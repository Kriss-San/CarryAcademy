package com.example.carryacademy

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

class MateriaDao(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    fun insertar(materia: Materia): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("nombre", materia.nombre)
            put("ultimoMensaje", materia.ultimoMensaje)
            put("hora", materia.hora)
            put("fechaCreacion", materia.fechaCreacion)
        }
        return db.insert(DatabaseHelper.TABLA_MATERIAS, null, values)
    }

    fun obtenerTodas(): List<Materia> {
        val lista = mutableListOf<Materia>()
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(DatabaseHelper.TABLA_MATERIAS, null, null, null, null, null, "id ASC")
        while (cursor.moveToNext()) {
            lista.add(
                Materia(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    nombre = cursor.getString(cursor.getColumnIndexOrThrow("nombre")),
                    ultimoMensaje = cursor.getString(cursor.getColumnIndexOrThrow("ultimoMensaje")),
                    hora = cursor.getString(cursor.getColumnIndexOrThrow("hora")),
                    fechaCreacion = cursor.getLong(cursor.getColumnIndexOrThrow("fechaCreacion"))
                )
            )
        }
        cursor.close()
        return lista
    }

    fun actualizarUltimoMensaje(id: Int, mensaje: String, hora: String): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("ultimoMensaje", mensaje)
            put("hora", hora)
        }
        return db.update(DatabaseHelper.TABLA_MATERIAS, values, "id = ?", arrayOf(id.toString()))
    }

    fun eliminar(id: Int): Int {
        val db = dbHelper.writableDatabase
        // Cascada manual: primero borramos los hijos (mensajes y recordatorios de la materia)
        db.delete(DatabaseHelper.TABLA_MENSAJES, "materiaId = ?", arrayOf(id.toString()))
        db.delete(DatabaseHelper.TABLA_RECORDATORIOS, "materiaId = ?", arrayOf(id.toString()))
        return db.delete(DatabaseHelper.TABLA_MATERIAS, "id = ?", arrayOf(id.toString()))
    }

    fun contar(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM ${DatabaseHelper.TABLA_MATERIAS}", null)
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        return count
    }
}