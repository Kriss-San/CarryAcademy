package com.example.carryacademy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class RecordatorioReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("ID", 0)
        val recordatorio = RecordatorioDao(context).obtenerPorId(id) ?: return
        if (recordatorio.completado) return

        val nivel = ConfiguracionDao(context).obtener().nivelInsistencia
        val restante = recordatorio.fechaMillis - System.currentTimeMillis()
        val texto = construirTexto(nivel, recordatorio.titulo, restante)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                "canal_recordatorios",
                "Recordatorios CarryAcademy",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(canal)
        }

        val notificacion = NotificationCompat.Builder(context, "canal_recordatorios")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("CarryAcademy")
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setAutoCancel(true)
            .build()

        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notificacion)

        // ENCADENAMIENTO: programar el siguiente aviso del motor
        MotorNotificaciones.reprogramarSiguiente(context, recordatorio)
    }

    // El texto escala según la urgencia y el modo
    private fun construirTexto(nivel: String, titulo: String, restante: Long): String {
        val horas = restante / 3_600_000L
        val dias = horas / 24
        val minutosPasado = -restante / 60_000L
        return when {
            // ACABA DE VENCER: sonó en el momento exacto del plazo (o en los 10 min siguientes)
            restante <= 0 && minutosPasado <= 10 -> when (nivel) {
                "Toxico" -> "🤬 ES AHORA: el plazo de '$titulo' se cumple EN ESTE MOMENTO. Cierra todo lo demás y entrégala YA."
                "Estricto" -> "⏰ EL PLAZO DE '$titulo' SE CUMPLE AHORA MISMO. Entrégala ya."
                "Normal" -> "📌 El plazo de '$titulo' es AHORA. ¡A entregar!"
                else -> "🌤️ Ya es la hora límite de '$titulo'. Cuando puedas."
            }
            // VENCIDA HACE RATO: cadena de insistencia
            restante <= 0 -> when (nivel) {
                "Toxico" -> "🤬 El plazo de '$titulo' YA PASÓ y sigues sin completarla. Te lo recordaré cada 2 horas hasta que la completes."
                "Estricto" -> "⏰ PLAZO VENCIDO: '$titulo'. Complétala ahora mismo."
                "Normal" -> "📌 '$titulo' está vencida. No la dejes acumular."
                else -> "🌤️ '$titulo' quedó vencida. Cuando puedas."
            }
            horas < 24 -> when (nivel) {
                "Toxico" -> "🤬 '$titulo' muere en $horas h. Ábrela YA o acepta las consecuencias."
                "Estricto" -> "⏰ Quedan $horas h para '$titulo'. Sin excusas."
                "Normal" -> "📌 Quedan $horas h para '$titulo'."
                else -> "🌤️ Recordatorio suave: '$titulo' ($horas h restantes)."
            }
            else -> when (nivel) {
                "Toxico" -> "🤬 '$titulo' es en $dias días y aún no la empiezas. Te estoy vigilando."
                "Estricto" -> "⏰ '$titulo' es en $dias días. Planifica hoy, no 'algún día'."
                "Normal" -> "📌 No olvides: '$titulo' es en $dias días."
                else -> "🌤️ Cuando puedas: '$titulo' es en $dias días."
            }
        }
    }
}