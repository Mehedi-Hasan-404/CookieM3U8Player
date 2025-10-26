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
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.C
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

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
    private val PLAYLIST_KEY = "playlist"
    private val REQUEST_CODE_PICK_FILE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupBottomNavigation()
        setupSpinners()
        
        loadHistory()
        setupHistoryRecyclerView()
        loadPlaylist()
        setupPlaylistRecyclerView()

        setupButtons()
        initializePlayer()
    }

    private fun initializeViews() {
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
    }

    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.nav_home).setOnClickListener { showHome() }
        findViewById<LinearLayout>(R.id.nav_history).setOnClickListener { showHistory() }
        findViewById<LinearLayout>(R.id.nav_playlist).setOnClickListener { showPlaylist() }
        findViewById<LinearLayout>(R.id.nav_settings).setOnClickListener { showSettings() }
    }

    private fun setupSpinners() {
        val userAgents = arrayOf("Default", "Chrome", "Firefox", "Safari", "Edge")
        val userAgentAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, userAgents)
        userAgentSpinner.adapter = userAgentAdapter
        
        val drmSchemes = arrayOf("clearkey", "widevine", "playready")
        val drmSchemeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, drmSchemes)
        drmSchemeSpinner.adapter = drmSchemeAdapter
    }

    private fun setupButtons() {
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
        
        findViewById<ImageButton>(R.id.button_history).setOnClickListener {
            showHistory()
        }
        
        findViewById<Button>(R.id.button_add_url).setOnClickListener {
            showAddUrlDialog()
        }
        
        findViewById<Button>(R.id.button_select_file).setOnClickListener {
            openFilePicker()
        }
        
        findViewById<Button>(R.id.button_clear_playlist).setOnClickListener {
            showClearPlaylistDialog()
        }
    }

    private fun setupPlaylistRecyclerView() {
        playlistAdapter = PlaylistEntriesAdapter(
            playlistEntries,
            onItemClick = { entry ->
                if (shouldOpenChannelBrowser(entry)) {
                    openChannelBrowser(entry)
                } else {
                    playDirectStream(entry)
                }
            },
            onItemLongClick = { entry ->
                openChannelBrowser(entry)
            }
        )
        playlistRecyclerView.layoutManager = LinearLayoutManager(this)
        playlistRecyclerView.adapter = playlistAdapter
    }

    private fun shouldOpenChannelBrowser(entry: PlaylistEntry): Boolean {
        return entry.url.endsWith(".m3u") || 
               entry.url.endsWith(".m3u8") ||
               entry.url.endsWith(".json") ||
               entry.name.contains("playlist", ignoreCase = true) ||
               entry.name.contains("channels", ignoreCase = true)
    }

    private fun openChannelBrowser(entry: PlaylistEntry) {
        val intent = Intent(this, ChannelBrowserActivity::class.java).apply {
            putExtra("playlist_entry", Gson().toJson(entry))
            putExtra("playlist_url", entry.url)
        }
        startActivity(intent)
    }

    private fun playDirectStream(entry: PlaylistEntry) {
        urlEditText.setText(entry.url)
        if (entry.cookie.isNotEmpty()) cookieEditText.setText(entry.cookie)
        if (entry.referer.isNotEmpty()) refererEditText.setText(entry.referer)
        if (entry.origin.isNotEmpty()) originEditText.setText(entry.origin)
        showHome()
        addToHistory(entry.url)
        startPlayback(
            entry.url,
            entry.cookie.ifEmpty { cookieEditText.text.toString().trim() },
            entry.referer.ifEmpty { refererEditText.text.toString().trim() },
            entry.origin.ifEmpty { originEditText.text.toString().trim() },
            drmLicenseEditText.text.toString().trim(),
            entry.userAgent,
            drmSchemeSpinner.selectedItem.toString()
        )
    }

    private fun parsePlaylistFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = inputStream.bufferedReader().use { it.readText() }
                
                playlistEntries.clear()
                val channels = mutableListOf<Channel>()
                
                if (content.trim().startsWith("[") || content.trim().startsWith("{")) {
                    channels.addAll(parseJsonPlaylistWithChannels(content))
                } else {
                    channels.addAll(parseM3UPlaylistWithChannels(content))
                }
                
                channels.forEach { channel ->
                    playlistEntries.add(PlaylistEntry(
                        name = channel.name,
                        url = channel.url,
                        logo = channel.logo,
                        cookie = channel.cookie,
                        referer = channel.referer,
                        origin = channel.origin,
                        userAgent = channel.userAgent
                    ))
                }
                
                if (channels.isNotEmpty()) {
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val channelsJson = Gson().toJson(channels)
                    prefs.edit().putString("cached_channels_file", channelsJson).apply()
                }
                
                playlistAdapter.notifyDataSetChanged()
                savePlaylist()
                Toast.makeText(this, "Loaded ${playlistEntries.size} entries", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("PlaylistParser", "Error parsing playlist", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseJsonPlaylistWithChannels(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        try {
            val jsonArray = JSONArray(content)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val channel = Channel(
                    name = jsonObject.optString("name", "Channel $i"),
                    url = jsonObject.optString("link", jsonObject.optString("url", "")),
                    logo = jsonObject.optString("logo", ""),
                    cookie = jsonObject.optString("cookie", ""),
                    referer = jsonObject.optString("referer", ""),
                    origin = jsonObject.optString("origin", ""),
                    userAgent = jsonObject.optString("userAgent", "Default"),
                    groupTitle = jsonObject.optString("group", jsonObject.optString("category", ""))
                )
                if (channel.url.isNotEmpty()) {
                    channels.add(channel)
                }
            }
        } catch (e: Exception) {
            Log.e("PlaylistParser", "Error parsing JSON", e)
        }
        return channels
    }

    private fun parseM3UPlaylistWithChannels(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        
        for ((index, line) in lines.withIndex()) {
            val trimmedLine = line.trim()
            
            when {
                trimmedLine.startsWith("#EXTINF:") -> {
                    val logoMatch = Regex("""tvg-logo="([^"]+)"""").find(trimmedLine)
                    currentLogo = logoMatch?.groupValues?.get(1) ?: ""
                    
                    val groupMatch = Regex("""group-title="([^"]+)"""").find(trimmedLine)
                    currentGroup = groupMatch?.groupValues?.get(1) ?: ""
                    
                    val commaIndex = trimmedLine.lastIndexOf(',')
                    currentName = if (commaIndex != -1) {
                        trimmedLine.substring(commaIndex + 1).trim()
                    } else {
                        "Channel ${index + 1}"
                    }
                }
                
                trimmedLine.startsWith("http") || trimmedLine.startsWith("https") -> {
                    if (currentName.isEmpty()) {
                        currentName = "Stream ${index + 1}"
                    }
                    
                    val urlParts = trimmedLine.split("|")
                    val url = urlParts[0].trim()
                    var referer = ""
                    var cookie = ""
                    var origin = ""
                    var userAgent = "Default"
                    
                    if (urlParts.size > 1) {
                        for (i in 1 until urlParts.size) {
                            val param = urlParts[i].trim()
                            when {
                                param.startsWith("Referer=", ignoreCase = true) -> 
                                    referer = param.substring(8)
                                param.startsWith("Cookie=", ignoreCase = true) -> 
                                    cookie = param.substring(7)
                                param.startsWith("Origin=", ignoreCase = true) -> 
                                    origin = param.substring(7)
                                param.startsWith("User-Agent=", ignoreCase = true) -> 
                                    userAgent = "Chrome"
                            }
                        }
                    }
                    
                    channels.add(Channel(
                        name = currentName,
                        url = url,
                        logo = currentLogo,
                        referer = referer,
                        cookie = cookie,
                        origin = origin,
                        userAgent = userAgent,
                        groupTitle = currentGroup
                    ))
                    
                    currentName = ""
                    currentLogo = ""
                }
            }
        }
        
        return channels
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
                    savePlaylist()
                    Toast.makeText(this, "Added to playlist", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showClearPlaylistDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Playlist")
            .setMessage("Are you sure you want to clear all playlist entries?")
            .setPositiveButton("Clear") { _, _ ->
                playlistEntries.clear()
                playlistAdapter.notifyDataSetChanged()
                savePlaylist()
                Toast.makeText(this, "Playlist cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "*/*", 
                "audio/x-mpegurl", 
                "application/vnd.apple.mpegurl", 
                "application/json", 
                "text/plain"
            ))
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
    
    private fun savePlaylist() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(playlistEntries)
        prefs.edit().putString(PLAYLIST_KEY, json).apply()
    }
    
    private fun loadPlaylist() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(PLAYLIST_KEY, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<PlaylistEntry>>() {}.type
            val loadedPlaylist: MutableList<PlaylistEntry> = Gson().fromJson(json, type)
            playlistEntries.addAll(loadedPlaylist)
        }
    }

    private fun initializePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(this).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                Log.d("Player", "Buffering...")
                            }
                            Player.STATE_READY -> {
                                Log.d("Player", "Ready to play")
                            }
                            Player.STATE_ENDED -> {
                                Log.d("Player", "Playback ended")
                            }
                        }
                    }
                })
            }
            playerView.player = player
            playerView.controllerShowTimeoutMs = 5000
            playerView.controllerHideOnTouch = true
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
        
        try {
            // Parse URL with embedded headers
            val parsedUrl = parseUrlWithHeaders(streamUrl)
            val url = parsedUrl.first
            val headers = parsedUrl.second.toMutableMap()
            
            if (cookie.isNotEmpty()) headers["Cookie"] = cookie
            if (referer.isNotEmpty()) headers["Referer"] = referer
            if (origin.isNotEmpty()) headers["Origin"] = origin
            
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
                .setDefaultRequestProperties(headers)

            val mediaItemBuilder = MediaItem.Builder().setUri(url)
            
            // Check for DRM in URL parameters
            if (streamUrl.contains("drmScheme=") && streamUrl.contains("drmLicense=")) {
                val drmSchemeFromUrl = extractDrmScheme(streamUrl)
                val licenseKey = extractDrmLicense(streamUrl)
                
                if (drmSchemeFromUrl == "clearkey" && licenseKey.isNotEmpty()) {
                    mediaItemBuilder.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                            .setLicenseUri(buildClearKeyLicenseUrl(licenseKey))
                            .build()
                    )
                }
            }
            
            val mediaItem = mediaItemBuilder.build()
            
            val mediaSource = when {
                url.contains(".mpd") -> {
                    DashMediaSource.Factory(httpDataSourceFactory)
                        .createMediaSource(mediaItem)
                }
                else -> {
                    HlsMediaSource.Factory(httpDataSourceFactory)
                        .createMediaSource(mediaItem)
                }
            }

            player?.setMediaSource(mediaSource)
            player?.playWhenReady = true
            player?.prepare()
            
            Log.d("CookieM3U8Player", "Playing: $url")
            Toast.makeText(this, "Loading stream...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "Playback error", e)
        }
    }

    private fun parseUrlWithHeaders(url: String): Pair<String, Map<String, String>> {
        val parts = url.split("|")
        val cleanUrl = parts[0]
        val headers = mutableMapOf<String, String>()
        
        for (i in 1 until parts.size) {
            val param = parts[i]
            when {
                param.startsWith("Referer=", ignoreCase = true) ->
                    headers["Referer"] = param.substring(8)
                param.startsWith("Cookie=", ignoreCase = true) ->
                    headers["Cookie"] = param.substring(7)
                param.startsWith("Origin=", ignoreCase = true) ->
                    headers["Origin"] = param.substring(7)
                param.startsWith("User-Agent=", ignoreCase = true) ->
                    headers["User-Agent"] = param.substring(11)
            }
        }
        
        return Pair(cleanUrl, headers)
    }

    private fun extractDrmScheme(url: String): String {
        val regex = Regex("drmScheme=([^&|]+)")
        return regex.find(url)?.groupValues?.get(1)?.lowercase(Locale.getDefault()) ?: ""
    }

    private fun extractDrmLicense(url: String): String {
        val regex = Regex("drmLicense=([^&|]+)")
        return regex.find(url)?.groupValues?.get(1) ?: ""
    }

    private fun buildClearKeyLicenseUrl(license: String): String {
        val parts = license.split(":")
        if (parts.size == 2) {
            val kid = parts[0]
            val key = parts[1]
            return "data:application/json;base64,${
                android.util.Base64.encodeToString(
                    """{"keys":[{"kty":"oct","k":"$key","kid":"$kid"}],"type":"temporary"}""".toByteArray(),
                    android.util.Base64.NO_WRAP
                )
            }"
        }
        return ""
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            releasePlayer()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}
