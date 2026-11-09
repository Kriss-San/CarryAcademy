package com.example.carryacademy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BLINDAJE ANTI-REINICIO:
 * Android elimina todas las alarmas (AlarmManager) cuando el dispositivo se apaga.
 * Este receiver despierta al encender el celular, lee la base de datos
 * y reprograma automáticamente todas las tareas pendientes con el Motor de Escalada.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MotorNotificaciones.reprogramarTodos(context)
        }
    }
}