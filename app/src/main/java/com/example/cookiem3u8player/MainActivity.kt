package com.example.cookiem3u8player

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.Util
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

data class HistoryItem(
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PlaylistEntry(
    var name: String = "",
    var url: String = ""
)

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: StyledPlayerView
    private lateinit var urlEditText: EditText
    private lateinit var cookieEditText: EditText
    private lateinit var refererEditText: EditText
    private lateinit var originEditText: EditText
    private lateinit var drmLicenseEditText: EditText
    private lateinit var userAgentSpinner: Spinner
    private lateinit var drmSchemeSpinner: Spinner
    private lateinit var playButton: ImageButton
    
    private lateinit var homeLayout: LinearLayout
    private lateinit var historyLayout: LinearLayout
    private lateinit var playlistLayout: LinearLayout
    private lateinit var settingsLayout: LinearLayout
    
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var playlistRecyclerView: RecyclerView
    
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var playlistAdapter: PlaylistEntriesAdapter
    
    private val history = mutableListOf<HistoryItem>()
    private val playlistEntries = mutableListOf<PlaylistEntry>()
    
    private val PREFS_NAME = "CookieM3U8PlayerPrefs"
    private val HISTORY_KEY = "history"
    private val REQUEST_CODE_PICK_FILE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        playerView = findViewById(R.id.player_view)
        urlEditText = findViewById(R.id.edit_text_url)
        cookieEditText = findViewById(R.id.edit_text_cookie)
        refererEditText = findViewById(R.id.edit_text_referer)
        originEditText = findViewById(R.id.edit_text_origin)
        drmLicenseEditText = findViewById(R.id.edit_text_drm_license)
        userAgentSpinner = findViewById(R.id.spinner_user_agent)
        drmSchemeSpinner = findViewById(R.id.spinner_drm_scheme)
        playButton = findViewById(R.id.button_play)
        
        homeLayout = findViewById(R.id.home_layout)
        historyLayout = findViewById(R.id.history_layout)
        playlistLayout = findViewById(R.id.playlist_layout)
        settingsLayout = findViewById(R.id.settings_layout)
        
        historyRecyclerView = findViewById(R.id.history_recycler_view)
        playlistRecyclerView = findViewById(R.id.playlist_recycler_view)
        
        // Setup bottom navigation
        findViewById<LinearLayout>(R.id.nav_home).setOnClickListener { showHome() }
        findViewById<LinearLayout>(R.id.nav_history).setOnClickListener { showHistory() }
        findViewById<LinearLayout>(R.id.nav_playlist).setOnClickListener { showPlaylist() }
        findViewById<LinearLayout>(R.id.nav_settings).setOnClickListener { showSettings() }
        
        // Setup UserAgent spinner
        val userAgents = arrayOf("Default", "Chrome", "Firefox", "Safari", "Edge")
        val userAgentAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, userAgents)
        userAgentSpinner.adapter = userAgentAdapter
        
        // Setup DRM Scheme spinner
        val drmSchemes = arrayOf("clearkey", "widevine", "playready")
        val drmSchemeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, drmSchemes)
        drmSchemeSpinner.adapter = drmSchemeAdapter
        
        // Setup history
        loadHistory()
        setupHistoryRecyclerView()
        
        // Setup playlist
        setupPlaylistRecyclerView()
        
        // Sample data
        urlEditText.setText("https://bldcmprod-cdn.toffeelive.com/cdn/live/sonysab_hd/playlist.m3u8")
        cookieEditText.setText("Edge-Cache-Cookie=URLPrefix=aHR0cHM6Ly9ibGRjbXByb2QtY2RuLnRvZmZlZWxpdmUuY29t:Expires=1761572334:KeyName=prod_linear:Signature=eiX9W8NcWl19TxAcUDjRNX5w6jgFcueipQAGjfw-eV4k37n1sakXqAlUouKZvRkirj2462qa9PMKRC3HI9kyBQ")

        playButton.setOnClickListener {
            val url = urlEditText.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter a stream URL", Toast.LENGTH_SHORT).show()
            } else {
                addToHistory(url)
                startPlayback(
                    url,
                    cookieEditText.text.toString().trim(),
                    refererEditText.text.toString().trim(),
                    originEditText.text.toString().trim(),
                    drmLicenseEditText.text.toString().trim(),
                    userAgentSpinner.selectedItem.toString(),
                    drmSchemeSpinner.selectedItem.toString()
                )
            }
        }
        
        // History icon button
        findViewById<ImageButton>(R.id.button_history).setOnClickListener {
            showHistory()
        }
        
        // Playlist add URL button
        findViewById<Button>(R.id.button_add_url).setOnClickListener {
            showAddUrlDialog()
        }
        
        // Playlist select file button
        findViewById<Button>(R.id.button_select_file).setOnClickListener {
            openFilePicker()
        }
    }

    private fun showHome() {
        homeLayout.visibility = LinearLayout.VISIBLE
        historyLayout.visibility = LinearLayout.GONE
        playlistLayout.visibility = LinearLayout.GONE
        settingsLayout.visibility = LinearLayout.GONE
        updateNavBar(0)
    }
    
    private fun showHistory() {
        homeLayout.visibility = LinearLayout.GONE
        historyLayout.visibility = LinearLayout.VISIBLE
        playlistLayout.visibility = LinearLayout.GONE
        settingsLayout.visibility = LinearLayout.GONE
        updateNavBar(1)
    }
    
    private fun showPlaylist() {
        homeLayout.visibility = LinearLayout.GONE
        historyLayout.visibility = LinearLayout.GONE
        playlistLayout.visibility = LinearLayout.VISIBLE
        settingsLayout.visibility = LinearLayout.GONE
        updateNavBar(2)
    }
    
    private fun showSettings() {
        homeLayout.visibility = LinearLayout.GONE
        historyLayout.visibility = LinearLayout.GONE
        playlistLayout.visibility = LinearLayout.GONE
        settingsLayout.visibility = LinearLayout.VISIBLE
        updateNavBar(3)
        Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateNavBar(activeIndex: Int) {
        val navItems = listOf(
            Triple(R.id.nav_home_icon, R.id.nav_home_text, 0),
            Triple(R.id.nav_history_icon, R.id.nav_history_text, 1),
            Triple(R.id.nav_playlist_icon, R.id.nav_playlist_text, 2),
            Triple(R.id.nav_settings_icon, R.id.nav_settings_text, 3)
        )
        
        navItems.forEach { (iconId, textId, index) ->
            val icon = findViewById<ImageView>(iconId)
            val text = findViewById<TextView>(textId)
            if (index == activeIndex) {
                icon.setColorFilter(resources.getColor(android.R.color.holo_blue_light, null))
                text.setTextColor(resources.getColor(android.R.color.holo_blue_light, null))
            } else {
                icon.setColorFilter(resources.getColor(android.R.color.darker_gray, null))
                text.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            }
        }
    }
    
    private fun setupHistoryRecyclerView() {
        historyAdapter = HistoryAdapter(history,
            onItemClick = { item ->
                urlEditText.setText(item.url)
                showHome()
            },
            onItemDelete = { position ->
                history.removeAt(position)
                historyAdapter.notifyItemRemoved(position)
                saveHistory()
            }
        )
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = historyAdapter
    }
    
    private fun setupPlaylistRecyclerView() {
        playlistAdapter = PlaylistEntriesAdapter(playlistEntries,
            onItemClick = { entry ->
                urlEditText.setText(entry.url)
                showHome()
                addToHistory(entry.url)
                startPlayback(
                    entry.url,
                    cookieEditText.text.toString().trim(),
                    refererEditText.text.toString().trim(),
                    originEditText.text.toString().trim(),
                    drmLicenseEditText.text.toString().trim(),
                    userAgentSpinner.selectedItem.toString(),
                    drmSchemeSpinner.selectedItem.toString()
                )
            }
        )
        playlistRecyclerView.layoutManager = LinearLayoutManager(this)
        playlistRecyclerView.adapter = playlistAdapter
    }
    
    private fun showAddUrlDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_url, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.edit_name)
        val urlInput = dialogView.findViewById<EditText>(R.id.edit_url)
        
        AlertDialog.Builder(this)
            .setTitle("Add URL")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    playlistEntries.add(PlaylistEntry(name, url))
                    playlistAdapter.notifyItemInserted(playlistEntries.size - 1)
                    Toast.makeText(this, "Added to playlist", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("*/*", "audio/x-mpegurl", "application/vnd.apple.mpegurl"))
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_FILE)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                parsePlaylistFile(uri)
            }
        }
    }
    
    private fun parsePlaylistFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                var currentName = ""
                var lineNumber = 0
                
                playlistEntries.clear()
                
                reader.forEachLine { line ->
                    lineNumber++
                    val trimmedLine = line.trim()
                    
                    when {
                        trimmedLine.startsWith("#EXTINF:") -> {
                            currentName = trimmedLine.substringAfter(",").trim()
                            if (currentName.isEmpty()) {
                                currentName = "Stream $lineNumber"
                            }
                        }
                        trimmedLine.startsWith("http") -> {
                            if (currentName.isEmpty()) {
                                currentName = "Stream $lineNumber"
                            }
                            playlistEntries.add(PlaylistEntry(currentName, trimmedLine))
                            currentName = ""
                        }
                        trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#") -> {
                            if (currentName.isEmpty()) {
                                currentName = "Stream $lineNumber"
                            }
                            playlistEntries.add(PlaylistEntry(currentName, trimmedLine))
                            currentName = ""
                        }
                    }
                }
                
                playlistAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Loaded ${playlistEntries.size} entries", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("PlaylistParser", "Error parsing playlist", e)
            Toast.makeText(this, "Error parsing playlist file", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun addToHistory(url: String) {
        history.removeAll { it.url == url }
        history.add(0, HistoryItem(url))
        if (history.size > 50) {
            history.removeAt(history.size - 1)
        }
        historyAdapter.notifyDataSetChanged()
        saveHistory()
    }
    
    private fun saveHistory() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(history)
        prefs.edit().putString(HISTORY_KEY, json).apply()
    }
    
    private fun loadHistory() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(HISTORY_KEY, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<HistoryItem>>() {}.type
            val loadedHistory: MutableList<HistoryItem> = Gson().fromJson(json, type)
            history.addAll(loadedHistory)
        }
    }

    private fun initializePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(this).build()
            playerView.player = player
        }
    }

    private fun startPlayback(
        streamUrl: String, 
        cookie: String, 
        referer: String, 
        origin: String,
        drmLicense: String,
        userAgent: String,
        drmScheme: String
    ) {
        initializePlayer()
        player?.stop()
        player?.clearMediaItems()
        
        val requestProperties = mutableMapOf<String, String>()
        
        if (cookie.isNotEmpty()) requestProperties["Cookie"] = cookie
        if (referer.isNotEmpty()) requestProperties["Referer"] = referer
        if (origin.isNotEmpty()) requestProperties["Origin"] = origin
        
        val userAgentString = when (userAgent) {
            "Chrome" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            "Firefox" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
            "Safari" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15"
            "Edge" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
            else -> Util.getUserAgent(this, "CookieM3U8Player")
        }
        
        val httpDataSourceFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgentString)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(requestProperties)

        val mediaItem = MediaItem.fromUri(streamUrl)
        val hlsMediaSource = HlsMediaSource.Factory(httpDataSourceFactory)
            .createMediaSource(mediaItem)

        player?.setMediaSource(hlsMediaSource)
        player?.playWhenReady = true
        player?.prepare()
        
        Log.d("CookieM3U8Player", "Playing: $streamUrl")
        Toast.makeText(this, "Loading stream...", Toast.LENGTH_SHORT).show()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }
}
