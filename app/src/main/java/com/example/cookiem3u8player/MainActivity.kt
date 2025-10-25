package com.example.cookiem3u8player

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.Util
import android.util.Log

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: StyledPlayerView
    private lateinit var urlEditText: EditText
    private lateinit var cookieEditText: EditText
    private lateinit var playButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind UI elements from the layout
        playerView = findViewById(R.id.player_view)
        urlEditText = findViewById(R.id.edit_text_url)
        cookieEditText = findViewById(R.id.edit_text_cookie)
        playButton = findViewById(R.id.button_play)
        
        // Populate with the last example values for quick initial testing
        urlEditText.setText("https://bldcmprod-cdn.toffeelive.com/cdn/live/sonysab_hd/playlist.m3u8")
        cookieEditText.setText("Edge-Cache-Cookie=URLPrefix=aHR0cHM6Ly9ibGRjbXByb2QtY2RuLnRvZmZlZWxpdmUuY29t:Expires=1761572334:KeyName=prod_linear:Signature=eiX9W8NcWl19TxAcUDjRNX5w6jgFcueipQAGjfw-eV4k37n1sakXqAlUouKZvRkirj2462qa9PMKRC3HI9kyBQ")

        // Set up the Play button click listener
        playButton.setOnClickListener {
            val url = urlEditText.text.toString().trim()
            val cookie = cookieEditText.text.toString().trim()
            
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter a stream URL.", Toast.LENGTH_SHORT).show()
            } else {
                startPlayback(url, cookie)
            }
        }
    }

    /**
     * Initializes the ExoPlayer instance if it doesn't already exist.
     */
    private fun initializePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(this).build()
            playerView.player = player
        }
    }

    /**
     * Starts playback using the provided stream URL and optional cookie.
     */
    private fun startPlayback(streamUrl: String, cookie: String) {
        // 1. Ensure player is initialized
        initializePlayer()
        
        // 2. Clear any previous media items
        player?.stop()
        player?.clearMediaItems()
        
        // 3. Create the HTTP Data Source Factory
        var httpDataSourceFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory()
            .setUserAgent(Util.getUserAgent(this, "CookieM3U8Player"))
            .setAllowCrossProtocolRedirects(true)
        
        // 4. --- CORE LOGIC: Conditionally set the Cookie Header ---
        if (cookie.isNotEmpty()) {
            // If a cookie is provided, set the "Cookie" header
            httpDataSourceFactory = httpDataSourceFactory
                .setDefaultRequestProperties(mapOf("Cookie" to cookie))
            
            Log.d("CookieM3U8Player", "Attempting secured stream with Cookie.")
            Toast.makeText(this, "Attempting secured stream...", Toast.LENGTH_SHORT).show()
        } else {
            // If no cookie is provided, use the default factory (for public streams)
            Log.d("CookieM3U8Player", "Attempting public stream (no cookie provided).")
            Toast.makeText(this, "Attempting public stream...", Toast.LENGTH_SHORT).show()
        }

        // 5. Create the HLS Media Source
        val mediaItem = MediaItem.fromUri(streamUrl)
        
        val hlsMediaSource = HlsMediaSource.Factory(httpDataSourceFactory)
            .createMediaSource(mediaItem)

        // 6. Prepare and play the media
        player?.setMediaSource(hlsMediaSource)
        player?.playWhenReady = true
        player?.prepare()
    }

    /**
     * Releases the player resources.
     */
    private fun releasePlayer() {
        player?.release()
        player = null
    }

    // Lifecycle methods to handle player resource management
    override fun onStop() {
        super.onStop()
        // Release player when the activity is no longer visible
        releasePlayer()
    }
    
    // Note: We don't call initializePlayer() in onStart() here, as we only want 
    // to start playback when the user explicitly presses the "Play Stream" button.
}

