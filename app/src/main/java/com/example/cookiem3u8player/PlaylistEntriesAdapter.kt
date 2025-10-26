package com.example.cookiem3u8player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PlaylistEntriesAdapter(
    private val items: MutableList<PlaylistEntry>,
    private val onItemClick: (PlaylistEntry) -> Unit,
    private val onItemLongClick: ((PlaylistEntry) -> Unit)? = null
) : RecyclerView.Adapter<PlaylistEntriesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.playlist_entry_name)
        val urlText: TextView = view.findViewById(R.id.playlist_entry_url)
        val logoImage: ImageView? = try {
            view.findViewById(R.id.playlist_entry_logo)
        } catch (e: Exception) {
            null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.playlist_entry_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.nameText.text = item.name
        holder.urlText.text = item.url
        
        // Load logo if available
        holder.logoImage?.let { logoView ->
            if (item.logo.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(item.logo)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(logoView)
            } else {
                logoView.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
        
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(item)
            true
        }
    }

    override fun getItemCount() = items.size
}
