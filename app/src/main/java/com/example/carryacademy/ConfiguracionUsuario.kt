package com.example.carryacademy

data class ConfiguracionUsuario(
    val id: Int = 1,
    val nivelInsistencia: String = "Normal",
    val notificacionesActivas: Boolean = true
)