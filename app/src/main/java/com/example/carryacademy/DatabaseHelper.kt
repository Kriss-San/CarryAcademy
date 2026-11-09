package com.example.carryacademy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Base de datos local de CarryAcademy (SQLite nativo).
 * Versión 2: agrega ultimaFechaCompleto para la lógica de racha diaria.
 * Tnel ecuneta: las claves foráneas NO se fuerzan con PRAGMA porque existen
 * recordatorios globales (materiaId = 0). El borrado en cascada se
 * hace manualmente en MateriaDao.eliminar().
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "carryacademy.db"
        const val DATABASE_VERSION = 3
        const val TABLA_MATERIAS = "materias"
        const val TABLA_MENSAJES = "mensajes"
        const val TABLA_RECORDATORIOS = "recordatorios"
        const val TABLA_CONFIGURACION = "configuracion"
        const val TABLA_ESTADISTICAS = "estadisticas"

        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {

        db.execSQL("""
            CREATE TABLE $TABLA_MATERIAS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL,
                ultimoMensaje TEXT NOT NULL DEFAULT '',
                hora TEXT NOT NULL DEFAULT '',
                fechaCreacion INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLA_MENSAJES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                materiaId INTEGER NOT NULL,
                texto TEXT NOT NULL,
                esUsuario INTEGER NOT NULL DEFAULT 0,
                hora TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY (materiaId) REFERENCES $TABLA_MATERIAS(id) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLA_RECORDATORIOS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                materiaId INTEGER NOT NULL DEFAULT 0,
                titulo TEXT NOT NULL,
                fecha TEXT NOT NULL,
                completado INTEGER NOT NULL DEFAULT 0,
                creadoPorIA INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (materiaId) REFERENCES $TABLA_MATERIAS(id) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLA_CONFIGURACION (
                id INTEGER PRIMARY KEY,
                nivelInsistencia TEXT NOT NULL DEFAULT 'Normal',
                notificacionesActivas INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLA_ESTADISTICAS (
                id INTEGER PRIMARY KEY,
                diasRacha INTEGER NOT NULL DEFAULT 0,
                tareasCompletadasTotal INTEGER NOT NULL DEFAULT 0,
                tareasPendientesTotal INTEGER NOT NULL DEFAULT 0,
                ultimaFechaCompleto TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())

        db.execSQL("INSERT INTO $TABLA_CONFIGURACION (id, nivelInsistencia, notificacionesActivas) VALUES (1, 'Normal', 1)")
        db.execSQL("INSERT INTO $TABLA_ESTADISTICAS (id, diasRacha, tareasCompletadasTotal, tareasPendientesTotal, ultimaFechaCompleto) VALUES (1, 0, 0, 0, '')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLA_ESTADISTICAS ADD COLUMN ultimaFechaCompleto TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) {
            // Nueva columna: la fecha exacta en milisegundos para programar la alarma
            db.execSQL("ALTER TABLE $TABLA_RECORDATORIOS ADD COLUMN fechaMillis INTEGER NOT NULL DEFAULT 0")
        }
    }
}