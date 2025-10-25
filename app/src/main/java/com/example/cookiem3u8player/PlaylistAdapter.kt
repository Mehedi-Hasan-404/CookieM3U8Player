package com.example.cookiem3u8player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Data class for PlaylistItem (matching the layout expectations)
data class PlaylistItem(
    var name: String = "",
    var url: String = ""
)

class PlaylistAdapter(
    private val items: MutableList<PlaylistItem>,
    private val onItemClick: (PlaylistItem) -> Unit,
    private val onItemEdit: (PlaylistItem, Int) -> Unit,
    private val onItemDelete: (Int) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.playlist_item_name)
        val urlText: TextView = view.findViewById(R.id.playlist_item_url)
        val editButton: ImageButton = view.findViewById(R.id.playlist_item_edit)
        val deleteButton: ImageButton = view.findViewById(R.id.playlist_item_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.playlist_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.nameText.text = item.name
        holder.urlText.text = item.url
        
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
        
        holder.editButton.setOnClickListener {
            onItemEdit(item, position)
        }
        
        holder.deleteButton.setOnClickListener {
            onItemDelete(position)
        }
    }

    override fun getItemCount() = items.size
}
