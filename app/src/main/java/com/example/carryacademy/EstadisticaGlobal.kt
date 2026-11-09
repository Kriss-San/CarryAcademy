package com.example.carryacademy

data class EstadisticaGlobal(
    val id: Int = 1,
    val diasRacha: Int = 0,
    val tareasCompletadasTotal: Int = 0,
    val tareasPendientesTotal: Int = 0,
    val ultimaFechaCompleto: String = ""
)