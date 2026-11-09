package com.example.carryacademy

import android.content.Context
import java.util.Calendar

/**
 * MOTOR DE ESCALADA PROGRESIVA (encadenado).
 * Mantiene UNA sola alarma activa por tarea. Cuando suena, el receiver
 * reprograma el siguiente aviso según el modo de insistencia y el tiempo restante.
 * Regla de descanso: ninguna notificación entre 00:00 y 07:00.
 */
object MotorNotificaciones {

    // Slots horarios por día cuando faltan más de 3 días (L)
    private val SLOTS_L = mapOf(
        "Tranquilo" to listOf(9),
        "Normal" to listOf(9, 18),
        "Estricto" to listOf(9, 14, 20),
        "Toxico" to listOf(9, 15, 21)
    )

    // Slots horarios por día entre 1 y 3 días (M)
    private val SLOTS_M = mapOf(
        "Tranquilo" to listOf(9, 18),
        "Normal" to listOf(9, 13, 18, 20),
        "Estricto" to listOf(9, 12, 16, 19, 22),
        "Toxico" to listOf(9, 11, 13, 15, 17, 19, 21, 23)
    )

    // Offsets ANTES del deadline en las últimas 24 h (C)
    private val OFFSETS_C_HORAS = mapOf(
        "Tranquilo" to listOf(6.0, 2.0),
        "Normal" to listOf(12.0, 6.0, 3.0, 0.5),
        "Estricto" to listOf(18.0, 12.0, 6.0, 3.0, 1.0, 0.25),
        "Toxico" to listOf(12.0, 10.0, 8.0, 6.0, 4.0, 2.0, 0.17, 0.0)
    )

    // Slots diarios cuando la tarea ya está VENCIDA (V)
    private val SLOTS_V = mapOf(
        "Tranquilo" to listOf<Int>(),
        "Normal" to listOf(18),
        "Estricto" to listOf(7, 11, 15, 19, 23),
        "Toxico" to listOf(7, 9, 11, 13, 15, 17, 19, 21, 23)
    )

    fun calcularSiguiente(now: Long, deadline: Long, nivel: String): Long? {
        val restante = deadline - now

        // V: vencida
        if (restante <= 0) {
            val slots = SLOTS_V[nivel] ?: return null
            return proximoSlotDiario(now, slots)
        }
        // L: más de 3 días
        if (restante > 72 * 3600_000L) {
            return proximoSlotDiario(now, SLOTS_L[nivel] ?: listOf(9, 18))
        }
        // M: entre 1 y 3 días
        if (restante > 24 * 3600_000L) {
            return proximoSlotDiario(now, SLOTS_M[nivel] ?: listOf(9, 18))
        }
        // C: últimas 24 h (offsets fijos desde el deadline)
        val offsets = OFFSETS_C_HORAS[nivel] ?: listOf(3.0, 0.5)
        for (off in offsets) {
            val t = aplicarDescanso(deadline - (off * 3600_000L).toLong())
            if (t > now) return t
        }
        // Si ya pasaron todos los offsets pero el deadline sigue futuro: avisar EN el deadline
        return if (deadline > now) deadline else null
    }

    private fun proximoSlotDiario(now: Long, slots: List<Int>): Long? {
        if (slots.isEmpty()) return null
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        for (h in slots) {
            val c = cal.clone() as Calendar
            c.set(Calendar.HOUR_OF_DAY, h)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis > now) return c.timeInMillis
        }
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.add(Calendar.DAY_OF_YEAR, 1)
        c.set(Calendar.HOUR_OF_DAY, slots.first())
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    // Regla de descanso: nada entre 00:00 y 07:00 -> se mueve a las 07:00
    private fun aplicarDescanso(t: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = t
        if (c.get(Calendar.HOUR_OF_DAY) < 7) {
            c.set(Calendar.HOUR_OF_DAY, 7)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    // Se llama al crear la tarea (manual o por IA) y al reconstruir todo tras un reinicio
    // AHORA DEVUELVE Long? para que la Activity pueda mostrar en un Toast cuándo se programó
    fun programarInicial(context: Context, recordatorio: Recordatorio): Long? {
        // Guardia: tarea sin fecha válida no se programa (evita spam instantáneo)
        if (recordatorio.fechaMillis <= 0L) {
            android.util.Log.w("MOTOR_DEBUG", "Tarea sin fecha válida, se omite: ${recordatorio.titulo}")
            return null
        }
        val nivel = ConfiguracionDao(context).obtener().nivelInsistencia
        val now = System.currentTimeMillis()
        val siguiente = if (recordatorio.fechaMillis <= now) {
            primeraHoraVencida(now)
        } else {
            calcularSiguiente(now, recordatorio.fechaMillis, nivel) ?: recordatorio.fechaMillis
        }

        // LOG DE DIAGNÓSTICO:
        android.util.Log.d("MOTOR_DEBUG", "Tarea: ${recordatorio.titulo} | Deadline: ${recordatorio.fechaMillis} | Ahora: $now | Siguiente alarma: $siguiente | Nivel: $nivel")

        ProgramadorNotificaciones.programarEn(context, recordatorio, siguiente)
        return siguiente
    }

    // Tarea vencida: aviso casi inmediato, PERO si es madrugada espera a las 07:00
    private fun primeraHoraVencida(now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        return if (cal.get(Calendar.HOUR_OF_DAY) < 7) {
            cal.set(Calendar.HOUR_OF_DAY, 7)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } else {
            now + 2_000L
        }
    }

    // BLINDAJE: reprograma TODAS las tareas pendientes (lo llama el BootReceiver)
    fun reprogramarTodos(context: Context) {
        val pendientes = RecordatorioDao(context).obtenerTodos().filter { !it.completado }
        for (recordatorio in pendientes) {
            programarInicial(context, recordatorio)
        }
    }

    // Lo llama el receiver después de cada notificación
    fun reprogramarSiguiente(context: Context, recordatorio: Recordatorio) {
        val nivel = ConfiguracionDao(context).obtener().nivelInsistencia
        val siguiente = calcularSiguiente(System.currentTimeMillis(), recordatorio.fechaMillis, nivel)
        if (siguiente != null) {
            ProgramadorNotificaciones.programarEn(context, recordatorio, siguiente)
        }
        // Si es null, la cadena termina (ej: Tranquilo vencida = sin spam)
    }

    fun cancelar(context: Context, recordatorio: Recordatorio) {
        ProgramadorNotificaciones.cancelar(context, recordatorio)
    }
}