package com.example.carryacademy

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecordatorioAdapter(
    private val recordatorios: MutableList<Recordatorio>,
    private val materiasMap: Map<Int, String>, // id -> nombre de materia
    private val onCompletadoChanged: (Recordatorio, Boolean) -> Unit,
    private val onEliminar: (Recordatorio) -> Unit
) : RecyclerView.Adapter<RecordatorioAdapter.RecordatorioViewHolder>() {

    inner class RecordatorioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMateriaBadge: TextView = itemView.findViewById(R.id.tv_materia_badge)
        val cbCompletado: CheckBox = itemView.findViewById(R.id.cb_completado)
        val tvTitulo: TextView = itemView.findViewById(R.id.tv_tarea_titulo)
        val tvFecha: TextView = itemView.findViewById(R.id.tv_tarea_fecha)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordatorioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_item_recordatorio, parent, false)
        return RecordatorioViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordatorioViewHolder, position: Int) {
        val recordatorio = recordatorios[position]

        holder.tvTitulo.text = recordatorio.titulo
        holder.tvFecha.text = recordatorio.fecha

        // Mostrar el nombre de la materia en el badge
        val nombreMateria = materiasMap[recordatorio.materiaId]
        holder.tvMateriaBadge.text = if (nombreMateria != null) {
            " $nombreMateria"
        } else {
            "📋 General" // Para recordatorios globales (materiaId = 0)
        }

        // Evita que el listener se dispare al reciclar la vista
        holder.cbCompletado.setOnCheckedChangeListener(null)
        holder.cbCompletado.isChecked = recordatorio.completado

        // Efecto visual de tachado
        if (recordatorio.completado) {
            holder.tvTitulo.paintFlags = holder.tvTitulo.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.tvTitulo.paintFlags = holder.tvTitulo.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // Al marcar/desmarcar: avisar a la Activity para persistir en BD
        holder.cbCompletado.setOnCheckedChangeListener { _, isChecked ->
            recordatorio.completado = isChecked
            if (isChecked) {
                holder.tvTitulo.paintFlags = holder.tvTitulo.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                holder.tvTitulo.paintFlags = holder.tvTitulo.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            onCompletadoChanged(recordatorio, isChecked)
        }

        // Mantener presionado = eliminar
        holder.itemView.setOnLongClickListener {
            onEliminar(recordatorio)
            true
        }
    }

    override fun getItemCount() = recordatorios.size
}