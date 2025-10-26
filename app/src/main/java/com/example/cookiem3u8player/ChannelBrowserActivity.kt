package com.example.cookiem3u8player

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Channel(
    val name: String,
    val url: String,
    val logo: String = "",
    val cookie: String = "",
    val referer: String = "",
    val origin: String = "",
    val userAgent: String = "Default",
    val groupTitle: String = ""
)

class ChannelBrowserActivity : AppCompatActivity() {
    
    private lateinit var channelRecyclerView: RecyclerView
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var backButton: ImageButton
    private lateinit var searchButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var groupSpinner: Spinner
    
    private val channels = mutableListOf<Channel>()
    private val filteredChannels = mutableListOf<Channel>()
    private var groups = mutableListOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_browser)
        
        channelRecyclerView = findViewById(R.id.channel_recycler_view)
        backButton = findViewById(R.id.button_back)
        searchButton = findViewById(R.id.button_search)
        titleText = findViewById(R.id.title_text)
        groupSpinner = findViewById(R.id.group_spinner)
        
        // Get playlist entry from intent
        val playlistJson = intent.getStringExtra("playlist_entry")
        val playlistEntry = if (playlistJson != null) {
            Gson().fromJson(playlistJson, PlaylistEntry::class.java)
        } else null
        
        titleText.text = playlistEntry?.name ?: "Channels"
        
        // Setup back button
        backButton.setOnClickListener {
            finish()
        }
        
        // Setup search button
        searchButton.setOnClickListener {
            showSearchDialog()
        }
        
        // Setup grid layout
        channelRecyclerView.layoutManager = GridLayoutManager(this, 3)
        
        // Setup adapter
        channelAdapter = ChannelAdapter(filteredChannels) { channel ->
            playChannel(channel)
        }
        channelRecyclerView.adapter = channelAdapter
        
        // Load channels from the playlist
        playlistEntry?.let {
            loadChannelsFromUrl(it.url)
        }
        
        // Setup group filter
        setupGroupSpinner()
    }
    
    private fun loadChannelsFromUrl(url: String) {
        Toast.makeText(this, "Loading channels...", Toast.LENGTH_SHORT).show()
        
        // First try to load from cache
        val prefs = getSharedPreferences("CookieM3U8PlayerPrefs", MODE_PRIVATE)
        val savedChannelsJson = prefs.getString("cached_channels_$url", null)
        
        if (savedChannelsJson != null) {
            val type = object : TypeToken<MutableList<Channel>>() {}.type
            val loadedChannels: MutableList<Channel> = Gson().fromJson(savedChannelsJson, type)
            channels.addAll(loadedChannels)
            updateChannelList()
            extractGroups()
            Toast.makeText(this, "Loaded ${channels.size} channels", Toast.LENGTH_SHORT).show()
        } else {
            // Fetch from URL
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                val fetcher = PlaylistFetcher(this@ChannelBrowserActivity)
                val result = fetcher.fetchPlaylistFromUrl(url)
                
                result.onSuccess { fetchedChannels ->
                    channels.addAll(fetchedChannels)
                    updateChannelList()
                    extractGroups()
                    Toast.makeText(this@ChannelBrowserActivity, "Loaded ${channels.size} channels", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(this@ChannelBrowserActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun extractGroups() {
        groups.clear()
        groups.add("All Channels")
        
        channels.forEach { channel ->
            if (channel.groupTitle.isNotEmpty() && !groups.contains(channel.groupTitle)) {
                groups.add(channel.groupTitle)
            }
        }
        
        setupGroupSpinner()
    }
    
    private fun setupGroupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, groups)
        groupSpinner.adapter = adapter
        
        groupSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                filterByGroup(groups[position])
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun filterByGroup(group: String) {
        filteredChannels.clear()
        
        if (group == "All Channels") {
            filteredChannels.addAll(channels)
        } else {
            filteredChannels.addAll(channels.filter { it.groupTitle == group })
        }
        
        channelAdapter.notifyDataSetChanged()
    }
    
    private fun updateChannelList() {
        filteredChannels.clear()
        filteredChannels.addAll(channels)
        channelAdapter.notifyDataSetChanged()
    }
    
    private fun showSearchDialog() {
        val input = EditText(this)
        input.hint = "Search channels..."
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Search")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString().trim()
                searchChannels(query)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun searchChannels(query: String) {
        filteredChannels.clear()
        
        if (query.isEmpty()) {
            filteredChannels.addAll(channels)
        } else {
            filteredChannels.addAll(
                channels.filter { 
                    it.name.contains(query, ignoreCase = true) ||
                    it.groupTitle.contains(query, ignoreCase = true)
                }
            )
        }
        
        channelAdapter.notifyDataSetChanged()
    }
    
    private fun playChannel(channel: Channel) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("channel_url", channel.url)
            putExtra("channel_name", channel.name)
            putExtra("channel_cookie", channel.cookie)
            putExtra("channel_referer", channel.referer)
            putExtra("channel_origin", channel.origin)
            putExtra("channel_user_agent", channel.userAgent)
        }
        startActivity(intent)
    }
}
