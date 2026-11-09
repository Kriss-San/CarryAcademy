package com.example.carryacademy

data class Mensaje(
    val texto: String,
    val esUsuario: Boolean,
    val hora: String,
    val id: Int = 0,
    val materiaId: Int = 0,        // ¿A qué chat pertenece este mensaje?
    val timestamp: Long = System.currentTimeMillis()
)