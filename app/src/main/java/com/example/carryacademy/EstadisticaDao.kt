package com.example.carryacademy

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EstadisticaDao(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    // READ: lee la fila única de estadísticas
    fun obtener(): EstadisticaGlobal {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLA_ESTADISTICAS,
            null, "id = ?", arrayOf("1"), null, null, null
        )
        var e = EstadisticaGlobal()
        if (cursor.moveToFirst()) {
            e = EstadisticaGlobal(
                id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                diasRacha = cursor.getInt(cursor.getColumnIndexOrThrow("diasRacha")),
                tareasCompletadasTotal = cursor.getInt(cursor.getColumnIndexOrThrow("tareasCompletadasTotal")),
                tareasPendientesTotal = cursor.getInt(cursor.getColumnIndexOrThrow("tareasPendientesTotal")),
                ultimaFechaCompleto = cursor.getString(cursor.getColumnIndexOrThrow("ultimaFechaCompleto"))
            )
        }
        cursor.close()
        return e
    }

    /**
     * Lógica de racha diaria:
     * - Si ya completaste algo HOY: no suma doble.
     * - Si tu última compleción fue AYER: racha +1.
     * - Si hubo un hueco de 2 o más días: la racha se reinicia en 1.
     */
    fun registrarComplecionHoy() {
        val actual = obtener()
        val hoy = fechaActual()
        if (actual.ultimaFechaCompleto == hoy) return

        val nuevaRacha = if (actual.ultimaFechaCompleto == fechaAyer()) actual.diasRacha + 1 else 1

        val db = dbHelper.writableDatabase
        db.execSQL(
            "UPDATE ${DatabaseHelper.TABLA_ESTADISTICAS} SET diasRacha = ?, ultimaFechaCompleto = ? WHERE id = 1",
            arrayOf(nuevaRacha.toString(), hoy)
        )
    }

    // Contadores en vivo calculados desde las otras tablas
    fun contarCompletadas(): Int = contar("SELECT COUNT(*) FROM ${DatabaseHelper.TABLA_RECORDATORIOS} WHERE completado = 1")
    fun contarPendientes(): Int = contar("SELECT COUNT(*) FROM ${DatabaseHelper.TABLA_RECORDATORIOS} WHERE completado = 0")
    fun contarMensajes(): Int = contar("SELECT COUNT(*) FROM ${DatabaseHelper.TABLA_MENSAJES}")
    fun contarMaterias(): Int = contar("SELECT COUNT(*) FROM ${DatabaseHelper.TABLA_MATERIAS}")

    private fun contar(sql: String): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(sql, null)
        var n = 0
        if (cursor.moveToFirst()) n = cursor.getInt(0)
        cursor.close()
        return n
    }

    private fun fechaActual(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)

    private fun fechaAyer(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }
}