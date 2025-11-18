package com.example.cookiem3u8player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import java.util.*
import kotlin.math.abs

@UnstableApi
class PlayerActivity : AppCompatActivity() {
    
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var backButton: ImageButton
    private lateinit var channelNameText: TextView
    private lateinit var topOverlay: LinearLayout
    
    // Controls
    private lateinit var fullscreenButton: ImageButton
    private lateinit var pipButton: ImageButton
    private lateinit var qualityButton: ImageButton
    private lateinit var subtitleButton: ImageButton
    private lateinit var audioTrackButton: ImageButton
    private lateinit var lockButton: ImageButton
    private lateinit var titleTextView: TextView
    private lateinit var playbackSpeedButton: TextView
    
    private var isFullscreen = false
    private var isLocked = false
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var gestureDetector: GestureDetectorCompat
    
    // Info Panel
    private lateinit var centerInfoPanel: LinearLayout
    private lateinit var infoIcon: ImageView
    private lateinit var infoText: TextView
    
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        
        // Initialize views
        playerView = findViewById(R.id.fullscreen_player_view)
        backButton = findViewById(R.id.player_back_button)
        channelNameText = findViewById(R.id.channel_name_text)
        topOverlay = findViewById(R.id.top_overlay)
        centerInfoPanel = findViewById(R.id.center_info_panel)
        infoIcon = findViewById(R.id.info_icon)
        infoText = findViewById(R.id.info_text)
        
        // Initialize Control Buttons (These are inside the playerView's controller layout)
        fullscreenButton = findViewById(R.id.exo_fullscreen)
        pipButton = findViewById(R.id.exo_pip)
        qualityButton = findViewById(R.id.exo_quality)
        subtitleButton = findViewById(R.id.exo_subtitle)
        audioTrackButton = findViewById(R.id.exo_audio_track)
        lockButton = findViewById(R.id.exo_lock)
        titleTextView = findViewById(R.id.exo_title)
        playbackSpeedButton = findViewById(R.id.exo_playback_speed)
        
        setupGestureDetector()
        setupButtons()
        
        // Get Intent Data
        val channelUrl = intent.getStringExtra("channel_url") ?: ""
        val channelName = intent.getStringExtra("channel_name") ?: "Channel"
        val cookie = intent.getStringExtra("channel_cookie") ?: ""
        val referer = intent.getStringExtra("channel_referer") ?: ""
        val origin = intent.getStringExtra("channel_origin") ?: ""
        val userAgent = intent.getStringExtra("channel_user_agent") ?: "Default"
        
        channelNameText.text = channelName
        titleTextView.text = channelName
        
        if (channelUrl.isNotEmpty()) {
            startPlayback(channelUrl, cookie, referer, origin, userAgent)
        } else {
            Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun setupButtons() {
        backButton.setOnClickListener { finish() }
        findViewById<View>(R.id.exo_back)?.setOnClickListener { finish() }
        
        fullscreenButton.setOnClickListener { toggleFullscreen() }
        lockButton.setOnClickListener { toggleLock() }
        
        pipButton.setOnClickListener { 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPipMode()
            } else {
                Toast.makeText(this, "PiP not supported", Toast.LENGTH_SHORT).show()
            }
        }
        
        qualityButton.setOnClickListener { showTrackSelectionDialog(C.TRACK_TYPE_VIDEO, "Select Quality") }
        subtitleButton.setOnClickListener { showTrackSelectionDialog(C.TRACK_TYPE_TEXT, "Select Subtitles") }
        audioTrackButton.setOnClickListener { showTrackSelectionDialog(C.TRACK_TYPE_AUDIO, "Select Audio") }
        
        playbackSpeedButton.setOnClickListener { showPlaybackSpeedDialog() }
        
        // Controller Visibility
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (!isLocked) {
                topOverlay.visibility = visibility
            }
            if (visibility == View.GONE) {
                hideSystemUI()
            }
        })
    }
    
    private fun startPlayback(url: String, cookie: String, referer: String, origin: String, userAgent: String) {
        trackSelector = DefaultTrackSelector(this)
        
        // Prepare Headers
        val headers = mutableMapOf<String, String>()
        if (cookie.isNotEmpty()) headers["Cookie"] = cookie
        if (referer.isNotEmpty()) headers["Referer"] = referer
        if (origin.isNotEmpty()) headers["Origin"] = origin
        
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(getUserAgentString(userAgent))
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)

        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            Toast.makeText(applicationContext, "Playback Ended", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(applicationContext, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                })
            }

        playerView.player = player
        
        // Build Media Item with DRM support if needed
        val mediaItemBuilder = MediaItem.Builder().setUri(url)
        if (url.contains("drmScheme") && url.contains("drmLicense")) {
             // Add DRM logic here if you use it, similar to your old code
             // Media3 handles DRM configuration inside MediaItem.Builder().setDrmConfiguration(...)
        }
        
        player?.setMediaItem(mediaItemBuilder.build())
        player?.prepare()
        player?.play()
    }

    private fun toggleFullscreen() {
        if (isFullscreen) {
            // Exit Fullscreen (Portrait)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen)
            isFullscreen = false
        } else {
            // Enter Fullscreen (Landscape Sensor)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit)
            isFullscreen = true
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }
    
    // --- Orientation Change Handler ---
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        if (isLandscape) {
            isFullscreen = true
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit)
            hideSystemUI()
        } else {
            isFullscreen = false
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen)
        }
    }

    private fun toggleLock() {
        isLocked = !isLocked
        if (isLocked) {
            lockButton.setImageResource(R.drawable.ic_lock_closed)
            playerView.useController = false
            topOverlay.visibility = View.GONE
            Toast.makeText(this, "Locked", Toast.LENGTH_SHORT).show()
        } else {
            lockButton.setImageResource(R.drawable.ic_lock_open)
            playerView.useController = true
            topOverlay.visibility = View.VISIBLE
            Toast.makeText(this, "Unlocked", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Gesture Logic (Volume/Brightness) ---
    private fun setupGestureDetector() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (isLocked || e1 == null) return false
                
                val width = playerView.width
                val isLeft = e1.x < width / 2
                val delta = distanceY / playerView.height
                
                if (abs(distanceY) > abs(distanceX)) {
                    if (isLeft) adjustBrightness(delta) else adjustVolume(delta)
                    return true
                }
                return false
            }
            
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isLocked) {
                    // Temporarily show unlock button logic could go here
                    Toast.makeText(applicationContext, "Screen Locked", Toast.LENGTH_SHORT).show()
                } else {
                    if (playerView.isControllerFullyVisible) playerView.hideController() else playerView.showController()
                }
                return true
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isLocked) return false
                player?.let { if (it.isPlaying) it.pause() else it.play() }
                return true
            }
        })
        
        playerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                centerInfoPanel.visibility = View.GONE
            }
            gestureDetector.onTouchEvent(event)
        }
    }
    
    private fun adjustBrightness(delta: Float) {
        val lp = window.attributes
        lp.screenBrightness = (lp.screenBrightness + delta).coerceIn(0.01f, 1.0f)
        window.attributes = lp
        showInfoPanel(R.drawable.ic_menu_day, "${(lp.screenBrightness * 100).toInt()}%")
    }
    
    private fun adjustVolume(delta: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val change = (delta * max * 1.5f).toInt() // 1.5x sensitivity
        val newVol = (current + change).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        showInfoPanel(R.drawable.ic_volume, "${(newVol * 100 / max)}%")
    }

    private fun showInfoPanel(iconRes: Int, text: String) {
        centerInfoPanel.visibility = View.VISIBLE
        infoIcon.setImageResource(iconRes)
        infoText.text = text
    }
    
    private fun showPlaybackSpeedDialog() {
        val speeds = arrayOf("0.25x", "0.5x", "1.0x", "1.25x", "1.5x", "2.0x")
        val values = floatArrayOf(0.25f, 0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
        
        AlertDialog.Builder(this)
            .setTitle("Speed")
            .setItems(speeds) { _, i ->
                player?.setPlaybackSpeed(values[i])
                playbackSpeedButton.text = speeds[i]
            }.show()
    }
    
    private fun showTrackSelectionDialog(trackType: Int, title: String) {
        // Simplified track selection logic for brevity
        Toast.makeText(this, "$title selection coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun getUserAgentString(name: String): String {
        return when (name) {
            "Chrome" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            "Firefox" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
            else -> "CookieM3U8Player/5.0"
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        playerView.useController = !isInPip
        topOverlay.visibility = if (isInPip) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
