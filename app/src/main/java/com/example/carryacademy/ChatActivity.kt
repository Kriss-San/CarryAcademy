package com.example.carryacademy

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AdjuntoPendiente(
    val uri: Uri,
    val nombre: String,
    val mime: String,
    val tamanho: Long
)

class ChatActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var rvMensajes: RecyclerView
    private lateinit var etInputMensaje: EditText
    private lateinit var btnEnviar: ImageButton
    private lateinit var btnAdjuntar: ImageButton

    private lateinit var adapter: MensajeAdapter
    private lateinit var mensajeDao: MensajeDao
    private lateinit var materiaDao: MateriaDao
    private lateinit var configDao: ConfiguracionDao
    private lateinit var recordatorioDao: RecordatorioDao
    private val geminiClient = GeminiClient()
    private val listaMensajes = mutableListOf<Mensaje>()

    private val adjuntosPendientes = mutableListOf<AdjuntoPendiente>()
    private var pendingFotoUri: Uri? = null

    private var materiaId: Int = 0
    private var materiaNombre: String = "Materia"

    companion object {
        private const val MAX_TOTAL_BYTES = 15L * 1024 * 1024
    }

    private val selectorArchivos = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { addAdjunto(it) }
    }

    private val camaraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { exito ->
        if (exito) pendingFotoUri?.let { addAdjunto(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layout_input)) { view, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = imeHeight
            view.layoutParams = params
            insets
        }

        materiaId = intent.getIntExtra("MATERIA_ID", 0)
        materiaNombre = intent.getStringExtra("MATERIA_NOMBRE") ?: "Materia"

        toolbar = findViewById(R.id.toolbar_chat)
        rvMensajes = findViewById(R.id.rv_mensajes)
        etInputMensaje = findViewById(R.id.et_input_mensaje)
        btnEnviar = findViewById(R.id.btn_enviar)
        btnAdjuntar = findViewById(R.id.btn_adjuntar)

        toolbar.title = materiaNombre
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        mensajeDao = MensajeDao(this)
        materiaDao = MateriaDao(this)
        configDao = ConfiguracionDao(this)
        recordatorioDao = RecordatorioDao(this)

        rvMensajes.layoutManager = LinearLayoutManager(this)
        adapter = MensajeAdapter(listaMensajes)
        rvMensajes.adapter = adapter

        cargarMensajesDeLaBaseDeDatos()

        btnEnviar.setOnClickListener {
            val texto = etInputMensaje.text.toString().trim()
            if (adjuntosPendientes.isNotEmpty()) {
                enviarConAdjuntos(texto)
            } else if (texto.isNotEmpty()) {
                guardarMensajeUsuario(texto)
                etInputMensaje.text.clear()
                pedirRespuestaAGemini(texto)
            } else {
                Toast.makeText(this, "Escribe un mensaje o adjunta un archivo", Toast.LENGTH_SHORT).show()
            }
        }

        btnAdjuntar.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Adjuntar")
                .setItems(arrayOf("Tomar foto", "Elegir archivos (PDF o imagen)")) { _, cual ->
                    when (cual) {
                        0 -> abrirCamara()
                        1 -> selectorArchivos.launch(arrayOf("application/pdf", "image/*"))
                    }
                }
                .show()
        }
    }

    private fun cargarMensajesDeLaBaseDeDatos() {
        listaMensajes.clear()
        val guardados = mensajeDao.obtenerPorMateria(materiaId)
        if (guardados.isEmpty()) {
            val bienvenida = Mensaje(
                texto = "¡Hola! Soy tu asistente de $materiaNombre. ¿En qué puedo ayudarte hoy? Puedes adjuntarme PDFs o fotos de la clase con el botón (+). 📎",
                esUsuario = false,
                hora = obtenerHoraActual(),
                materiaId = materiaId
            )
            mensajeDao.insertar(bienvenida)
            listaMensajes.add(bienvenida)
        } else {
            listaMensajes.addAll(guardados)
        }
        adapter.notifyDataSetChanged()
        rvMensajes.scrollToPosition(listaMensajes.size - 1)
    }

    // ===== MEMORIA CONVERSACIONAL: últimos 10 mensajes (sin contar el que acabas de escribir) =====
    private fun construirHistorial(): List<MensajeHistorial> {
        if (listaMensajes.size <= 1) return emptyList()
        return listaMensajes
            .subList(0, listaMensajes.size - 1)
            .takeLast(10)
            .map { MensajeHistorial(it.esUsuario, it.texto.take(500)) }
    }

    // ===== CÁMARA =====
    private fun abrirCamara() {
        try {
            val dir = File(cacheDir, "fotos").apply { mkdirs() }
            val archivo = File(dir, "foto_${System.currentTimeMillis()}.jpg")
            pendingFotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", archivo)
            camaraLauncher.launch(pendingFotoUri!!)
        } catch (e: Exception) {
            Toast.makeText(this, "⚠️ No se pudo abrir la cámara", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addAdjunto(uri: Uri) {
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        if (!mime.startsWith("image/") && mime != "application/pdf") {
            Toast.makeText(this, "⚠️ Formato no soportado: solo PDF o imágenes", Toast.LENGTH_SHORT).show()
            return
        }
        val nombre = obtenerNombreArchivo(uri)
        val tamanho = obtenerTamano(uri)
        if (tamanho <= 0L) {
            Toast.makeText(this, "⚠️ No se pudo leer el tamaño del archivo", Toast.LENGTH_SHORT).show()
            return
        }
        val totalActual = adjuntosPendientes.sumOf { it.tamanho }
        if (totalActual + tamanho > MAX_TOTAL_BYTES) {
            Toast.makeText(this, "⚠️ Límite: 15 MB SUMADOS por mensaje (límite de Gemini)", Toast.LENGTH_LONG).show()
            return
        }
        adjuntosPendientes.add(AdjuntoPendiente(uri, nombre, mime, tamanho))
        renderAdjuntos()
    }

    private fun obtenerNombreArchivo(uri: Uri): String {
        var nombre = "archivo"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) nombre = cursor.getString(idx) ?: "archivo"
        }
        return nombre
    }

    private fun obtenerTamano(uri: Uri): Long {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) return cursor.getLong(idx)
        }
        uri.path?.let { ruta ->
            val f = File(ruta)
            if (f.exists()) return f.length()
        }
        return -1L
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    private fun renderAdjuntos() {
        val scroll = findViewById<HorizontalScrollView>(R.id.scroll_adjuntos)
        val cont = findViewById<LinearLayout>(R.id.contenedor_adjuntos)
        cont.removeAllViews()
        scroll.visibility = if (adjuntosPendientes.isEmpty()) View.GONE else View.VISIBLE

        adjuntosPendientes.forEachIndexed { idx, adj ->
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_chip_adjunto)
                setPadding(dp(12), dp(6), dp(6), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
            }
            val icono = ImageView(this).apply {
                setImageResource(if (adj.mime.startsWith("image/")) R.drawable.ic_foto else R.drawable.ic_pdf)
                setColorFilter(getColor(R.color.rojo_suave))
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
            }
            val texto = TextView(this).apply {
                text = adj.nombre
                setTextColor(getColor(R.color.texto_principal))
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dp(160)
                setPadding(dp(6), 0, dp(6), 0)
            }
            val cerrar = ImageView(this).apply {
                setImageResource(R.drawable.ic_cerrar)
                setColorFilter(getColor(R.color.texto_secundario))
                setPadding(dp(6), dp(6), dp(6), dp(6))
                setOnClickListener {
                    adjuntosPendientes.removeAt(idx)
                    renderAdjuntos()
                }
            }
            chip.addView(icono)
            chip.addView(texto)
            chip.addView(cerrar)
            cont.addView(chip)
        }
    }

    private fun enviarConAdjuntos(texto: String) {
        val archivos = mutableListOf<ArchivoAdjunto>()
        for (adj in adjuntosPendientes) {
            val bytes = contentResolver.openInputStream(adj.uri)?.use { it.readBytes() }
            if (bytes == null) {
                Toast.makeText(this, "⚠️ No se pudo leer: ${adj.nombre}", Toast.LENGTH_SHORT).show()
                return
            }
            archivos.add(ArchivoAdjunto(bytes, adj.mime))
        }
        val nombres = adjuntosPendientes.joinToString(", ") { it.nombre }
        val burbuja = if (texto.isNotEmpty()) "📎 $nombres\n$texto" else "📎 $nombres"
        guardarMensajeUsuario(burbuja)
        etInputMensaje.text.clear()
        adjuntosPendientes.clear()
        renderAdjuntos()
        analizarArchivosConGemini(archivos, texto, nombres)
    }

    private fun analizarArchivosConGemini(archivos: List<ArchivoAdjunto>, texto: String, nombres: String) {
        btnEnviar.isEnabled = false
        val layoutTyping = findViewById<LinearLayout>(R.id.layout_typing)
        layoutTyping.visibility = View.VISIBLE
        animarPuntos(layoutTyping)

        val nivel = configDao.obtener().nivelInsistencia
        val tareasPendientes = recordatorioDao.obtenerPendientesPorMateria(materiaId)
        val systemPrompt = construirSystemPrompt(materiaNombre, nivel, tareasPendientes) +
                " EL USUARIO ACABA DE ADJUNTAR ARCHIVO(S) de esta materia. Analízalos completos y responde con: " +
                "1) RESUMEN EJECUTIVO (máximo 5 líneas), 2) IDEAS CLAVE (viñetas), 3) TAREAS Y FECHAS DETECTADAS. " +
                "Si detectas fechas límite, aplica la REGLA DE RECORDATORIOS para crearlos automáticamente."

        val promptFinal = if (texto.isNotEmpty()) texto
        else "Analiza estos archivos ($nombres) de la materia $materiaNombre: dame resumen, ideas clave y fechas límite."

        geminiClient.generarConArchivos(
            promptUsuario = promptFinal,
            instruccionSistema = systemPrompt,
            archivos = archivos,
            historial = construirHistorial(),
            onExito = { respuesta ->
                procesarRespuestaDeGemini(respuesta)
                restaurarEntrada()
                layoutTyping.visibility = View.GONE
            },
            onError = { error ->
                guardarMensajeIA("⚠️ No pude procesar los archivos en este momento. Intenta de nuevo en unos minutos.")
                restaurarEntrada()
                layoutTyping.visibility = View.GONE
                mostrarDialogoError(error)
            }
        )
    }

    private fun pedirRespuestaAGemini(textoUsuario: String) {
        btnEnviar.isEnabled = false
        etInputMensaje.hint = ""

        val layoutTyping = findViewById<LinearLayout>(R.id.layout_typing)
        layoutTyping.visibility = View.VISIBLE
        animarPuntos(layoutTyping)

        val nivel = configDao.obtener().nivelInsistencia
        val tareasPendientes = recordatorioDao.obtenerPendientesPorMateria(materiaId)
        val systemPrompt = construirSystemPrompt(materiaNombre, nivel, tareasPendientes)

        geminiClient.generar(
            promptUsuario = textoUsuario,
            instruccionSistema = systemPrompt,
            historial = construirHistorial(),
            onExito = { respuesta ->
                procesarRespuestaDeGemini(respuesta)
                restaurarEntrada()
                layoutTyping.visibility = View.GONE
            },
            onError = { error ->
                guardarMensajeIA(generarRespuestaSimulada(textoUsuario))
                restaurarEntrada()
                layoutTyping.visibility = View.GONE
                mostrarDialogoError(error)
            }
        )
    }

    private fun mostrarDialogoError(error: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Error de conexión con Gemini")
            .setMessage("Detalle técnico:\n\n$error\n\n(Mantén presionado el texto para copiar)")
            .setPositiveButton("Entendido", null)
            .setNeutralButton("Copiar error") { dialog, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Error Gemini", error)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Error copiado al portapapeles", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }

    private fun animarPuntos(layout: LinearLayout) {
        val anim1 = AnimationUtils.loadAnimation(this, R.anim.pulse_1)
        val anim2 = AnimationUtils.loadAnimation(this, R.anim.pulse_2)
        val anim3 = AnimationUtils.loadAnimation(this, R.anim.pulse_3)

        if (layout.childCount >= 3) {
            layout.getChildAt(0).startAnimation(anim1)
            layout.getChildAt(1).startAnimation(anim2)
            layout.getChildAt(2).startAnimation(anim3)
        }
    }

    private fun restaurarEntrada() {
        btnEnviar.isEnabled = true
        etInputMensaje.hint = "Pregúntale a CarryAcademy..."
    }

    private fun construirSystemPrompt(materia: String, nivel: String, tareas: List<Recordatorio>): String {
        val personalidad = when (nivel) {
            "Tranquilo" -> "Eres amable, paciente y relajado. Animas al estudiante sin presionarlo."
            "Normal" -> "Eres equilibrado: cercano pero enfocado en que el estudiante cumpla sus tareas a tiempo."
            "Estricto" -> "Eres firme y directo. No aceptas excusas largas: recuerdas fechas límite y exiges un plan de acción inmediato."
            "Toxico" -> "Eres sarcástico y brutalmente honesto con la procrastinación (sin insultos reales ni faltas de respeto). 'Rosteas' con humor al estudiante por posponer, pero SIEMPRE terminas dando ayuda académica útil y concreta."
            else -> "Eres un asistente académico útil y claro."
        }

        val contextoTareas = if (tareas.isEmpty()) {
            "ESTADO ACTUAL: El usuario NO tiene tareas pendientes en esta materia. Está libre de culpa. Si te hace preguntas académicas, actúa como un tutor útil sin acusarlo de procrastinar, porque literalmente no tiene nada pendiente."
        } else {
            val lista = tareas.joinToString("\n") { "- ${it.titulo} (Vence: ${it.fecha})" }
            "ESTADO ACTUAL: El usuario TIENE estas tareas pendientes en esta materia que debe completar:\n$lista\n" +
                    "REGLA DE ORO: Si el modo es Estricto o Tóxico y el usuario te hace preguntas triviales o se distrae, RECUÉRDALE AGRESIVAMENTE que tiene estas tareas pendientes y que debería estar haciéndolas en lugar de perder el tiempo contigo. Usa esta lista como evidencia real para tus reclamos."
        }

        val ahora = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        return "Fecha y hora actuales del dispositivo del usuario: $ahora (úsala SIEMPRE para calcular 'hoy', 'mañana' y las horas de los recordatorios). " +
                "Eres el asistente de estudio de la materia '$materia' dentro de la app CarryAcademy. " +
                "Personalidad configurada por el usuario: $personalidad " +
                "$contextoTareas " +
                "Tienes MEMORIA CONVERSACIONAL: recibirás el historial reciente de este chat. Úsalo para responder con continuidad (nombres, acuerdos y temas previos) sin que el usuario deba repetirse. " +
                "Responde siempre en español, en máximo 3 párrafos cortos, con tono de chat de mensajería. " +
                "Si el estudiante envía contenido académico, extrae ideas clave y tareas. Si menciona fechas o exámenes, recuérdaselos y propón un plan. " +
                "REGLA DE RECORDATORIOS: si el estudiante te pide crear un recordatorio o tarea con fecha (o menciona una fecha límite clara), " +
                "agrega AL FINAL de tu respuesta, en una línea separada y con este formato exacto: " +
                "[RECORDATORIO] título de la tarea | AAAA-MM-DD | HH:MM [/RECORDATORIO] " +
                "donde HH:MM es hora en formato 24 horas. " +
                "No expliques esa línea ni la menciones en tu texto normal."
    }

    private fun procesarRespuestaDeGemini(respuestaCompleta: String) {
        val regex = Regex("\\[RECORDATORIO\\](.*?)\\[/RECORDATORIO\\]", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(respuestaCompleta)

        if (match == null) {
            guardarMensajeIA(respuestaCompleta)
            return
        }

        val partes = match.groupValues[1].trim().split("|").map { it.trim() }
        var titulo = "Recordatorio de $materiaNombre"
        var fechaMillis = 0L
        var fechaTexto = ""

        if (partes.size >= 3) {
            titulo = partes[0]
            val formato = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            try {
                fechaMillis = formato.parse("${partes[1]} ${partes[2]}")?.time ?: 0L
                val f = partes[1].split("-")
                fechaTexto = "${f[2]}/${f[1]}/${f[0]} ${partes[2]}"
            } catch (e: Exception) {
                fechaMillis = 0L
            }
        }

        val textoLimpio = regex.replace(respuestaCompleta, "").trim() +
                "\n\n📌 Recordatorio creado: $titulo"
        guardarMensajeIA(textoLimpio)

        if (fechaMillis > 0L) {
            val idNuevo = recordatorioDao.insertar(
                Recordatorio(
                    titulo = titulo,
                    fecha = fechaTexto,
                    materiaId = materiaId,
                    creadoPorIA = true,
                    fechaMillis = fechaMillis
                )
            ).toInt()
            if (idNuevo > 0) {
                MotorNotificaciones.programarInicial(
                    this,
                    Recordatorio(
                        id = idNuevo,
                        titulo = titulo,
                        fecha = fechaTexto,
                        materiaId = materiaId,
                        creadoPorIA = true,
                        fechaMillis = fechaMillis
                    )
                )
                Toast.makeText(this, "📌 La IA creó un recordatorio con alarma real", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun guardarMensajeUsuario(texto: String) {
        val mensaje = Mensaje(texto = texto, esUsuario = true, hora = obtenerHoraActual(), materiaId = materiaId)
        mensajeDao.insertar(mensaje)
        listaMensajes.add(mensaje)
        adapter.notifyItemInserted(listaMensajes.size - 1)
        rvMensajes.scrollToPosition(listaMensajes.size - 1)
        materiaDao.actualizarUltimoMensaje(materiaId, "Tú: $texto", obtenerHoraActual())
    }

    private fun guardarMensajeIA(texto: String) {
        val mensajeIA = Mensaje(texto = texto, esUsuario = false, hora = obtenerHoraActual(), materiaId = materiaId)
        mensajeDao.insertar(mensajeIA)
        listaMensajes.add(mensajeIA)
        adapter.notifyItemInserted(listaMensajes.size - 1)
        rvMensajes.scrollToPosition(listaMensajes.size - 1)
        materiaDao.actualizarUltimoMensaje(materiaId, "IA: $texto", obtenerHoraActual())
    }

    private fun generarRespuestaSimulada(texto: String): String {
        val textoLower = texto.lowercase()
        return when {
            textoLower.contains("hola") || textoLower.contains("buenas") ->
                "¡Hola! ¿En qué tema de la materia necesitas ayuda hoy?"
            textoLower.contains("tarea") || textoLower.contains("trabajo") ->
                "Recuerda revisar las fechas de entrega en la sección de Recordatorios. ¿Necesitas que te ayude a organizar tu tiempo?"
            textoLower.contains("parcial") || textoLower.contains("examen") ->
                "Te recomiendo crear un plan de estudio. ¿Quieres que te genere preguntas de práctica sobre los temas vistos?"
            textoLower.contains("ayuda") || textoLower.contains("no entiendo") ->
                "Con gusto te ayudo. ¿Podrías especificar qué tema o concepto no te queda claro?"
            textoLower.contains("gracias") ->
                "¡De nada! Estoy aquí para ayudarte a aprobar. 💪"
            else ->
                "Entendido. Déjame analizar eso con calma y te propongo un plan de estudio para: '$texto'."
        }
    }

    private fun obtenerHoraActual(): String {
        val formato = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return formato.format(Date())
    }
}