package com.example.carryacademy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MateriaAdapter(
    private val materias: List<Materia>,
    private val onItemClick: (Materia) -> Unit,
    private val onLongClick: (Materia) -> Unit = {}
) : RecyclerView.Adapter<MateriaAdapter.MateriaViewHolder>() {

    inner class MateriaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvInicial: TextView = itemView.findViewById(R.id.tv_inicial)
        val tvNombre: TextView = itemView.findViewById(R.id.tv_nombre_materia)
        val tvMensaje: TextView = itemView.findViewById(R.id.tv_ultimo_mensaje)
        val tvHora: TextView = itemView.findViewById(R.id.tv_hora)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MateriaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_item_materia, parent, false)
        return MateriaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MateriaViewHolder, position: Int) {
        val materia = materias[position]
        holder.tvInicial.text = materia.nombre.take(1).uppercase()
        holder.tvNombre.text = materia.nombre
        holder.tvMensaje.text = materia.ultimoMensaje
        holder.tvHora.text = materia.hora
        holder.itemView.setOnClickListener { onItemClick(materia) }
        holder.itemView.setOnLongClickListener { onLongClick(materia); true }
    }

    override fun getItemCount() = materias.size
}