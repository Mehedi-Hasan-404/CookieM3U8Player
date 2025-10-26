package com.example.cookiem3u8player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class ChannelAdapter(
    private val channels: MutableList<Channel>,
    private val onChannelClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logoImage: ImageView = view.findViewById(R.id.channel_logo)
        val nameText: TextView = view.findViewById(R.id.channel_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.channel_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = channels[position]
        holder.nameText.text = channel.name
        
        // Load logo using Glide (you'll need to add Glide dependency)
        if (channel.logo.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(channel.logo)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(holder.logoImage)
        } else {
            holder.logoImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        
        holder.itemView.setOnClickListener {
            onChannelClick(channel)
        }
    }

    override fun getItemCount() = channels.size
}
