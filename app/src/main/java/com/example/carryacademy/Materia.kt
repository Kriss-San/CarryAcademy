package com.example.carryacademy

data class Materia(
    val id: Int = 0,
    val nombre: String,
    val ultimoMensaje: String = "",
    val hora: String = "",
    val fechaCreacion: Long = System.currentTimeMillis()
)