package com.example.carryacademy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object ProgramadorNotificaciones {

    fun programarEn(context: Context, recordatorio: Recordatorio, millis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RecordatorioReceiver::class.java).apply {
            putExtra("TITULO", recordatorio.titulo)
            putExtra("ID", recordatorio.id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            recordatorio.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 16: usar setAlarmClock() que sobrevive a Doze y muestra en el reloj del sistema
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val info = AlarmManager.AlarmClockInfo(millis, pending)
            alarmManager.setAlarmClock(info, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    fun cancelar(context: Context, recordatorio: Recordatorio) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RecordatorioReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            recordatorio.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }
}