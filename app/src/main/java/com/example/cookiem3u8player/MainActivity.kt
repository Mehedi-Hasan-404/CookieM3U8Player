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
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback
import com.google.android.exoplayer2.drm.FrameworkMediaDrm
import com.google.android.exoplayer2.C
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.Context

data class PlaylistItem(
    var name: String = "",
    var url: String = "",
    var cookie: String = "",
    var referer: String = "",
    var origin: String = "",
    var drmLicense: String = "",
    var userAgent: String = "Default",
    var drmScheme: String = "clearkey"
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
    private lateinit var playlistLayout: LinearLayout
    private lateinit var playlistRecyclerView: RecyclerView
    private lateinit var playlistAdapter: PlaylistAdapter
    
    private val playlist = mutableListOf<PlaylistItem>()
    private val PREFS_NAME = "CookieM3U8PlayerPrefs"
    private val PLAYLIST_KEY = "playlist"

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
        playlistLayout = findViewById(R.id.playlist_layout)
        playlistRecyclerView = findViewById(R.id.playlist_recycler_view)
        
        // Setup bottom navigation
        findViewById<ImageButton>(R.id.nav_home).setOnClickListener { showHome() }
        findViewById<ImageButton>(R.id.nav_playlist).setOnClickListener { showPlaylist() }
        findViewById<ImageButton>(R.id.nav_settings).setOnClickListener { 
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
        }
        
        // Setup UserAgent spinner
        val userAgents = arrayOf("Default", "Chrome", "Firefox", "Safari", "Edge", "Custom")
        val userAgentAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, userAgents)
        userAgentSpinner.adapter = userAgentAdapter
        
        // Setup DRM Scheme spinner
        val drmSchemes = arrayOf("clearkey", "widevine", "playready")
        val drmSchemeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, drmSchemes)
        drmSchemeSpinner.adapter = drmSchemeAdapter
        
        // Setup playlist
        loadPlaylist()
        setupPlaylistRecyclerView()
        
        // Sample data
        urlEditText.setText("https://bldcmprod-cdn.toffeelive.com/cdn/live/sonysab_hd/playlist.m3u8")
        cookieEditText.setText("Edge-Cache-Cookie=URLPrefix=aHR0cHM6Ly9ibGRjbXByb2QtY2RuLnRvZmZlZWxpdmUuY29t:Expires=1761572334:KeyName=prod_linear:Signature=eiX9W8NcWl19TxAcUDjRNX5w6jgFcueipQAGjfw-eV4k37n1sakXqAlUouKZvRkirj2462qa9PMKRC3HI9kyBQ")

        playButton.setOnClickListener {
            val url = urlEditText.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter a stream URL", Toast.LENGTH_SHORT).show()
            } else {
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
        
        // Add to playlist button
        findViewById<ImageButton>(R.id.button_save_playlist).setOnClickListener {
            showSavePlaylistDialog()
        }
    }

    private fun showHome() {
        homeLayout.visibility = LinearLayout.VISIBLE
        playlistLayout.visibility = LinearLayout.GONE
    }
    
    private fun showPlaylist() {
        homeLayout.visibility = LinearLayout.GONE
        playlistLayout.visibility = LinearLayout.VISIBLE
    }
    
    private fun setupPlaylistRecyclerView() {
        playlistAdapter = PlaylistAdapter(playlist, 
            onItemClick = { item ->
                // Load item into player
                urlEditText.setText(item.url)
                cookieEditText.setText(item.cookie)
                refererEditText.setText(item.referer)
                originEditText.setText(item.origin)
                drmLicenseEditText.setText(item.drmLicense)
                
                val userAgentPosition = (userAgentSpinner.adapter as ArrayAdapter<String>).getPosition(item.userAgent)
                userAgentSpinner.setSelection(userAgentPosition)
                
                val drmSchemePosition = (drmSchemeSpinner.adapter as ArrayAdapter<String>).getPosition(item.drmScheme)
                drmSchemeSpinner.setSelection(drmSchemePosition)
                
                showHome()
                startPlayback(item.url, item.cookie, item.referer, item.origin, item.drmLicense, item.userAgent, item.drmScheme)
            },
            onItemEdit = { item, position ->
                showEditPlaylistDialog(item, position)
            },
            onItemDelete = { position ->
                playlist.removeAt(position)
                playlistAdapter.notifyItemRemoved(position)
                savePlaylist()
                Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show()
            }
        )
        playlistRecyclerView.layoutManager = LinearLayoutManager(this)
        playlistRecyclerView.adapter = playlistAdapter
    }
    
    private fun showSavePlaylistDialog() {
        val input = EditText(this)
        input.hint = "Enter playlist name"
        
        AlertDialog.Builder(this)
            .setTitle("Save to Playlist")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val item = PlaylistItem(
                        name = name,
                        url = urlEditText.text.toString().trim(),
                        cookie = cookieEditText.text.toString().trim(),
                        referer = refererEditText.text.toString().trim(),
                        origin = originEditText.text.toString().trim(),
                        drmLicense = drmLicenseEditText.text.toString().trim(),
                        userAgent = userAgentSpinner.selectedItem.toString(),
                        drmScheme = drmSchemeSpinner.selectedItem.toString()
                    )
                    playlist.add(item)
                    playlistAdapter.notifyItemInserted(playlist.size - 1)
                    savePlaylist()
                    Toast.makeText(this, "Added to playlist", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showEditPlaylistDialog(item: PlaylistItem, position: Int) {
        val input = EditText(this)
        input.setText(item.name)
        input.hint = "Enter playlist name"
        
        AlertDialog.Builder(this)
            .setTitle("Edit Playlist Item")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    item.name = name
                    playlistAdapter.notifyItemChanged(position)
                    savePlaylist()
                    Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun savePlaylist() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(playlist)
        prefs.edit().putString(PLAYLIST_KEY, json).apply()
    }
    
    private fun loadPlaylist() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(PLAYLIST_KEY, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<PlaylistItem>>() {}.type
            val loadedPlaylist: MutableList<PlaylistItem> = Gson().fromJson(json, type)
            playlist.addAll(loadedPlaylist)
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
        
        // Create HTTP Data Source Factory with headers
        val requestProperties = mutableMapOf<String, String>()
        
        if (cookie.isNotEmpty()) {
            requestProperties["Cookie"] = cookie
        }
        if (referer.isNotEmpty()) {
            requestProperties["Referer"] = referer
        }
        if (origin.isNotEmpty()) {
            requestProperties["Origin"] = origin
        }
        
        val userAgentString = when (userAgent) {
            "Chrome" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            "Firefox" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
            "Safari" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15"
            "Edge" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
            else -> Util.getUserAgent(this, "CookieM3U8Player")
        }
        
        var httpDataSourceFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgentString)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(requestProperties)

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .apply {
                if (drmLicense.isNotEmpty()) {
                    val drmSchemeUuid = when (drmScheme.lowercase()) {
                        "widevine" -> C.WIDEVINE_UUID
                        "playready" -> C.PLAYREADY_UUID
                        "clearkey" -> C.CLEARKEY_UUID
                        else -> C.CLEARKEY_UUID
                    }
                    
                    setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(drmSchemeUuid)
                            .setLicenseUri(drmLicense)
                            .build()
                    )
                }
            }
            .build()

        val hlsMediaSource = HlsMediaSource.Factory(httpDataSourceFactory)
            .createMediaSource(mediaItem)

        player?.setMediaSource(hlsMediaSource)
        player?.playWhenReady = true
        player?.prepare()
        
        Log.d("CookieM3U8Player", "Playing stream with headers: $requestProperties")
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
