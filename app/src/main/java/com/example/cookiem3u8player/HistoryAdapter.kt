package com.example.cookiem3u8player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(
    private val items: MutableList<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit,
    private val onItemDelete: (Int) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val urlText: TextView = view.findViewById(R.id.history_item_url)
        val deleteButton: ImageButton = view.findViewById(R.id.history_item_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.history_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.urlText.text = item.url
        
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
        
        holder.deleteButton.setOnClickListener {
            onItemDelete(position)
        }
    }

    override fun getItemCount() = items.size
}
