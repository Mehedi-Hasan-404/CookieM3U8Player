package com.example.cookiem3u8player

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.Util

class PlayerActivity : AppCompatActivity() {
    
    private var player: ExoPlayer? = null
    private lateinit var playerView: StyledPlayerView
    private lateinit var backButton: ImageButton
    private lateinit var channelNameText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        
        playerView = findViewById(R.id.fullscreen_player_view)
        backButton = findViewById(R.id.player_back_button)
        channelNameText = findViewById(R.id.channel_name_text)
        
        val channelUrl = intent.getStringExtra("channel_url") ?: ""
        val channelName = intent.getStringExtra("channel_name") ?: "Channel"
        val cookie = intent.getStringExtra("channel_cookie") ?: ""
        val referer = intent.getStringExtra("channel_referer") ?: ""
        val origin = intent.getStringExtra("channel_origin") ?: ""
        val userAgent = intent.getStringExtra("channel_user_agent") ?: "Default"
        
        channelNameText.text = channelName
        
        backButton.setOnClickListener {
            finish()
        }
        
        if (channelUrl.isNotEmpty()) {
            initializePlayer()
            startPlayback(channelUrl, cookie, referer, origin, userAgent)
        } else {
            Toast.makeText(this, "Invalid channel URL", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun initializePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(this).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                Toast.makeText(this@PlayerActivity, "Buffering...", Toast.LENGTH_SHORT).show()
                            }
                            Player.STATE_READY -> {
                                // Ready to play
                            }
                            Player.STATE_ENDED -> {
                                Toast.makeText(this@PlayerActivity, "Playback ended", Toast.LENGTH_SHORT).show()
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
        userAgent: String
    ) {
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
    
    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}
