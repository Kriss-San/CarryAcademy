package com.example.carryacademy

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

class RecordatorioDao(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    fun insertar(recordatorio: Recordatorio): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("materiaId", recordatorio.materiaId)
            put("titulo", recordatorio.titulo)
            put("fecha", recordatorio.fecha)
            put("completado", if (recordatorio.completado) 1 else 0)
            put("creadoPorIA", if (recordatorio.creadoPorIA) 1 else 0)
            put("fechaMillis", recordatorio.fechaMillis)
        }
        return db.insert(DatabaseHelper.TABLA_RECORDATORIOS, null, values)
    }

    fun actualizarCompletado(id: Int, completado: Boolean): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("completado", if (completado) 1 else 0)
        }
        return db.update(DatabaseHelper.TABLA_RECORDATORIOS, values, "id = ?", arrayOf(id.toString()))
    }

    fun eliminar(id: Int): Int {
        val db = dbHelper.writableDatabase
        return db.delete(DatabaseHelper.TABLA_RECORDATORIOS, "id = ?", arrayOf(id.toString()))
    }

    fun obtenerTodos(): List<Recordatorio> {
        val lista = mutableListOf<Recordatorio>()
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            DatabaseHelper.TABLA_RECORDATORIOS,
            null, null, null, null, null,
            "id DESC"
        )
        while (cursor.moveToNext()) {
            lista.add(
                Recordatorio(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    materiaId = cursor.getInt(cursor.getColumnIndexOrThrow("materiaId")),
                    titulo = cursor.getString(cursor.getColumnIndexOrThrow("titulo")),
                    fecha = cursor.getString(cursor.getColumnIndexOrThrow("fecha")),
                    completado = cursor.getInt(cursor.getColumnIndexOrThrow("completado")) == 1,
                    creadoPorIA = cursor.getInt(cursor.getColumnIndexOrThrow("creadoPorIA")) == 1,
                    fechaMillis = cursor.getLong(cursor.getColumnIndexOrThrow("fechaMillis"))
                )
            )
        }
        cursor.close()
        return lista
    }
    fun obtenerPorId(id: Int): Recordatorio? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLA_RECORDATORIOS,
            null, "id = ?", arrayOf(id.toString()), null, null, null
        )
        var r: Recordatorio? = null
        if (cursor.moveToFirst()) {
            r = Recordatorio(
                id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                materiaId = cursor.getInt(cursor.getColumnIndexOrThrow("materiaId")),
                titulo = cursor.getString(cursor.getColumnIndexOrThrow("titulo")),
                fecha = cursor.getString(cursor.getColumnIndexOrThrow("fecha")),
                completado = cursor.getInt(cursor.getColumnIndexOrThrow("completado")) == 1,
                creadoPorIA = cursor.getInt(cursor.getColumnIndexOrThrow("creadoPorIA")) == 1,
                fechaMillis = cursor.getLong(cursor.getColumnIndexOrThrow("fechaMillis"))
            )
        }
        cursor.close()
        return r
    }
    /**
     * Obtiene solo las tareas NO completadas de una materia específica.
     * Sirve para darle contexto real a la IA.
     */
    fun obtenerPendientesPorMateria(materiaId: Int): List<Recordatorio> {
        val lista = mutableListOf<Recordatorio>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLA_RECORDATORIOS,
            null,
            "materiaId = ? AND completado = 0",
            arrayOf(materiaId.toString()),
            null, null,
            "fechaMillis ASC" // Ordenadas por urgencia (la que vence antes primero)
        )
        while (cursor.moveToNext()) {
            lista.add(
                Recordatorio(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    materiaId = cursor.getInt(cursor.getColumnIndexOrThrow("materiaId")),
                    titulo = cursor.getString(cursor.getColumnIndexOrThrow("titulo")),
                    fecha = cursor.getString(cursor.getColumnIndexOrThrow("fecha")),
                    completado = false,
                    creadoPorIA = cursor.getInt(cursor.getColumnIndexOrThrow("creadoPorIA")) == 1,
                    fechaMillis = cursor.getLong(cursor.getColumnIndexOrThrow("fechaMillis"))
                )
            )
        }
        cursor.close()
        return lista
    }
}