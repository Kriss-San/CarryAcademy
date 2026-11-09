package com.example.carryacademy

import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ArchivoAdjunto(
    val bytes: ByteArray,
    val mimeType: String
)

// Mensaje del pasado para la memoria conversacional
data class MensajeHistorial(
    val esUsuario: Boolean,
    val texto: String
)

class GeminiClient {

    companion object {
        private const val MODELO = "gemini-flash-latest"
        private const val URL_BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
        private const val MAX_REINTENTOS = 4
    }

    fun generar(
        promptUsuario: String,
        instruccionSistema: String,
        historial: List<MensajeHistorial> = emptyList(),
        onExito: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val cuerpo = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply { put(JSONObject().apply { put("text", instruccionSistema) }) })
            })
            put("contents", construirContents(historial, promptUsuario, null))
        }
        ejecutarEnHilo(cuerpo, onExito, onError)
    }

    fun generarConArchivos(
        promptUsuario: String,
        instruccionSistema: String,
        archivos: List<ArchivoAdjunto>,
        historial: List<MensajeHistorial> = emptyList(),
        onExito: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val cuerpo = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply { put(JSONObject().apply { put("text", instruccionSistema) }) })
            })
            put("contents", construirContents(historial, promptUsuario, archivos))
        }
        ejecutarEnHilo(cuerpo, onExito, onError)
    }

    // Construye la conversación: historial (roles user/model) + mensaje actual (+ archivos si hay)
    private fun construirContents(
        historial: List<MensajeHistorial>,
        promptActual: String,
        archivos: List<ArchivoAdjunto>?
    ): JSONArray = JSONArray().apply {
        for (msg in historial) {
            put(JSONObject().apply {
                put("role", if (msg.esUsuario) "user" else "model")
                put("parts", JSONArray().apply { put(JSONObject().apply { put("text", msg.texto) }) })
            })
        }
        put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", promptActual) })
                archivos?.forEach { archivo ->
                    put(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", archivo.mimeType)
                            put("data", Base64.encodeToString(archivo.bytes, Base64.NO_WRAP))
                        })
                    })
                }
            })
        })
    }

    private fun ejecutarEnHilo(cuerpo: JSONObject, onExito: (String) -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                val texto = llamarApiConReintentos(cuerpo)
                Handler(Looper.getMainLooper()).post { onExito(texto) }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { onError(e.message ?: "Error de red desconocido") }
            }
        }.start()
    }

    // EXPONENTIAL BACKOFF: reintenta con pausas de 1s, 2s, 4s si hay 503/429
    private fun llamarApiConReintentos(cuerpo: JSONObject): String {
        var ultimoError = ""
        for (intento in 1..MAX_REINTENTOS) {
            try {
                return ejecutarLlamada(cuerpo)
            } catch (e: Exception) {
                ultimoError = e.message ?: "Error desconocido"
                val esCuota = ultimoError.contains("503") || ultimoError.contains("429")
                val esTimeout = ultimoError.contains("timeout", true) || ultimoError.contains("timed out", true)
                val maxIntentos = if (esCuota) MAX_REINTENTOS else if (esTimeout) 2 else 1
                if ((esCuota || esTimeout) && intento < maxIntentos) {
                    Thread.sleep(1000L * (1 shl (intento - 1))) // 1s, 2s, 4s
                    continue
                }
                throw e
            }
        }
        throw Exception(ultimoError)
    }

    private fun ejecutarLlamada(cuerpo: JSONObject): String {
        val url = URL("$URL_BASE$MODELO:generateContent?key=${Secretos.GEMINI_API_KEY}")
        val conexion = url.openConnection() as HttpURLConnection
        conexion.requestMethod = "POST"
        conexion.setRequestProperty("Content-Type", "application/json")
        conexion.connectTimeout = 30000
        conexion.readTimeout = 45000

        OutputStreamWriter(conexion.outputStream, "UTF-8").use { it.write(cuerpo.toString()) }

        val codigo = conexion.responseCode
        if (codigo != HttpURLConnection.HTTP_OK) {
            val errorBody = conexion.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("Error HTTP $codigo: $errorBody")
        }

        val respuestaJson = BufferedReader(InputStreamReader(conexion.inputStream, "UTF-8"))
            .use { it.readText() }
        return extraerTexto(respuestaJson)
    }

    private fun extraerTexto(json: String): String {
        val obj = JSONObject(json)
        val candidatos = obj.optJSONArray("candidates")
            ?: throw Exception("La API no devolvió candidatos")
        if (candidatos.length() == 0) throw Exception("Respuesta vacía de la API")

        val partes = candidatos.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw Exception("Estructura de respuesta inesperada")
        if (partes.length() == 0) throw Exception("La API no devolvió texto")

        return partes.getJSONObject(0).optString("text", "")
    }
}