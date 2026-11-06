package com.abhaysurya.saathi

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private val items: List<ChatMessage>,
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: FrameLayout = view as FrameLayout
        val text: TextView = view.findViewById(R.id.messageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = items[position]
        holder.text.text = message.text
        val context = holder.itemView.context

        when (message.role) {
            "user" -> {
                holder.container.gravity = Gravity.END
                holder.text.background =
                    ContextCompat.getDrawable(context, R.drawable.bubble_user)
                holder.text.setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            "action" -> {
                holder.container.gravity = Gravity.START
                holder.text.background =
                    ContextCompat.getDrawable(context, R.drawable.bubble_action)
                holder.text.setTextColor(ContextCompat.getColor(context, R.color.text))
            }

            else -> {
                holder.container.gravity = Gravity.START
                holder.text.background =
                    ContextCompat.getDrawable(context, R.drawable.bubble_ai)
                holder.text.setTextColor(ContextCompat.getColor(context, R.color.text))
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
