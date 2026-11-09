package com.example.carryacademy

data class Recordatorio(
    val id: Int = 0,
    val titulo: String,
    val fecha: String,
    var completado: Boolean = false,
    val materiaId: Int = 0,
    val creadoPorIA: Boolean = false,
    val fechaMillis: Long = 0L   // Cuándo debe sonar la alarma (epoch millis)
)