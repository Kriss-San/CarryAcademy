package com.example.carryacademy

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MensajeAdapter(
    private val mensajes: List<Mensaje>
) : RecyclerView.Adapter<MensajeAdapter.MensajeViewHolder>() {

    inner class MensajeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val contenedor: LinearLayout = itemView.findViewById(R.id.contenedor_mensaje)
        val tvMensaje: TextView = itemView.findViewById(R.id.tv_mensaje)
        val tvHora: TextView = itemView.findViewById(R.id.tv_hora)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MensajeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_item_mensaje, parent, false)
        return MensajeViewHolder(view)
    }

    override fun onBindViewHolder(holder: MensajeViewHolder, position: Int) {
        val mensaje = mensajes[position]

        holder.tvMensaje.text = mensaje.texto
        holder.tvHora.text = mensaje.hora

        val params = holder.contenedor.layoutParams as FrameLayout.LayoutParams

        if (mensaje.esUsuario) {
            // Usuario: Derecha, fondo rojo
            params.gravity = Gravity.END
            holder.tvMensaje.setBackgroundResource(R.drawable.bg_burbuja_usuario)
            holder.tvHora.gravity = Gravity.END
        } else {
            // IA: Izquierda, fondo gris oscuro
            params.gravity = Gravity.START
            holder.tvMensaje.setBackgroundResource(R.drawable.bg_burbuja_ia)
            holder.tvHora.gravity = Gravity.START
        }
        holder.contenedor.layoutParams = params
    }

    override fun getItemCount() = mensajes.size
}