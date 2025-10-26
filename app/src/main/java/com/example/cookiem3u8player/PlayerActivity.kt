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
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.Util
import java.util.*
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {
    
    private var player: ExoPlayer? = null
    private lateinit var playerView: StyledPlayerView
    private lateinit var backButton: ImageButton
    private lateinit var channelNameText: TextView
    private lateinit var topOverlay: LinearLayout
    private lateinit var fullscreenButton: ImageButton
    private lateinit var pipButton: ImageButton
    private lateinit var qualityButton: ImageButton
    private lateinit var subtitleButton: ImageButton
    private lateinit var audioTrackButton: ImageButton
    
    private var isFullscreen = false
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var gestureDetector: GestureDetectorCompat
    
    private var brightnessPanel: View? = null
    private var volumePanel: View? = null
    private var brightnessText: TextView? = null
    private var volumeText: TextView? = null
    
    private val audioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        
        initializeViews()
        setupGestureDetector()
        
        val channelUrl = intent.getStringExtra("channel_url") ?: ""
        val channelName = intent.getStringExtra("channel_name") ?: "Channel"
        val cookie = intent.getStringExtra("channel_cookie") ?: ""
        val referer = intent.getStringExtra("channel_referer") ?: ""
        val origin = intent.getStringExtra("channel_origin") ?: ""
        val userAgent = intent.getStringExtra("channel_user_agent") ?: "Default"
        
        channelNameText.text = channelName
        
        setupButtons()
        
        if (channelUrl.isNotEmpty()) {
            initializePlayer()
            startPlayback(channelUrl, cookie, referer, origin, userAgent)
        } else {
            Toast.makeText(this, "Invalid channel URL", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun initializeViews() {
        playerView = findViewById(R.id.fullscreen_player_view)
        backButton = findViewById(R.id.player_back_button)
        channelNameText = findViewById(R.id.channel_name_text)
        topOverlay = findViewById(R.id.top_overlay)
        fullscreenButton = findViewById(R.id.exo_fullscreen)
        pipButton = findViewById(R.id.exo_pip)
        qualityButton = findViewById(R.id.exo_quality)
        subtitleButton = findViewById(R.id.exo_subtitle)
        audioTrackButton = findViewById(R.id.exo_audio_track)
        
        // Create overlay panels for brightness and volume
        brightnessPanel = findViewById(R.id.brightness_panel)
        volumePanel = findViewById(R.id.volume_panel)
        brightnessText = findViewById(R.id.brightness_text)
        volumeText = findViewById(R.id.volume_text)
    }
    
    private fun setupButtons() {
        backButton.setOnClickListener { finish() }
        
        fullscreenButton.setOnClickListener { toggleFullscreen() }
        
        pipButton.setOnClickListener { 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPipMode()
            } else {
                Toast.makeText(this, "PiP not supported on this device", Toast.LENGTH_SHORT).show()
            }
        }
        
        qualityButton.setOnClickListener { showQualityDialog() }
        
        subtitleButton.setOnClickListener { showSubtitleDialog() }
        
        audioTrackButton.setOnClickListener { showAudioTrackDialog() }
        
        setupPlayerControlsVisibility()
    }
    
    private fun setupPlayerControlsVisibility() {
        playerView.setControllerVisibilityListener(
            StyledPlayerView.ControllerVisibilityListener { visibility ->
                topOverlay.visibility = visibility
            }
        )
        
        playerView.controllerShowTimeoutMs = 3000
        playerView.controllerHideOnTouch = true
    }
    
    private fun setupGestureDetector() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            
            private var isVolumeGesture = false
            private var isBrightnessGesture = false
            private val SWIPE_THRESHOLD = 50
            
            override fun onDown(e: MotionEvent): Boolean = true
            
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val screenWidth = playerView.width
                val screenHeight = playerView.height
                
                // Determine if this is a vertical swipe
                if (abs(distanceY) > abs(distanceX) && abs(distanceY) > SWIPE_THRESHOLD) {
                    
                    // Left side = brightness, Right side = volume
                    if (e1.x < screenWidth / 2) {
                        // Brightness control (left side)
                        if (!isBrightnessGesture) {
                            isBrightnessGesture = true
                            brightnessPanel?.visibility = View.VISIBLE
                        }
                        adjustBrightness(-distanceY / screenHeight)
                    } else {
                        // Volume control (right side)
                        if (!isVolumeGesture) {
                            isVolumeGesture = true
                            volumePanel?.visibility = View.VISIBLE
                        }
                        adjustVolume(-distanceY / screenHeight)
                    }
                    return true
                }
                
                return false
            }
            
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                playerView.performClick()
                return true
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                player?.let {
                    if (it.isPlaying) {
                        it.pause()
                    } else {
                        it.play()
                    }
                }
                return true
            }
        })
        
        playerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                brightnessPanel?.visibility = View.GONE
                volumePanel?.visibility = View.GONE
            }
            gestureDetector.onTouchEvent(event)
        }
    }
    
    private fun adjustBrightness(delta: Float) {
        val window = window
        val layoutParams = window.attributes
        val currentBrightness = if (layoutParams.screenBrightness < 0) {
            Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                125
            ) / 255f
        } else {
            layoutParams.screenBrightness
        }
        
        val newBrightness = (currentBrightness + delta * 2).coerceIn(0.01f, 1f)
        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams
        
        brightnessText?.text = "Brightness\n${(newBrightness * 100).toInt()}%"
    }
    
    private fun adjustVolume(delta: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeDelta = (delta * maxVolume * 2).toInt()
        val newVolume = (currentVolume + volumeDelta).coerceIn(0, maxVolume)
        
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        
        val volumePercent = (newVolume * 100 / maxVolume)
        volumeText?.text = "Volume\n$volumePercent%"
    }
    
    private fun toggleFullscreen() {
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen)
            showSystemUI()
            isFullscreen = false
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit)
            hideSystemUI()
            isFullscreen = true
        }
    }
    
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }
    
    private fun showSystemUI() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
    
    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
    
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            playerView.useController = false
            topOverlay.visibility = View.GONE
        } else {
            playerView.useController = true
        }
    }
    
    private fun showQualityDialog() {
        val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: run {
            Toast.makeText(this, "No track info available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val videoRenderer = 0
        val trackGroupArray = mappedTrackInfo.getTrackGroups(videoRenderer)
        
        if (trackGroupArray.length == 0) {
            Toast.makeText(this, "No quality options available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val qualityOptions = mutableListOf("Auto")
        val trackIndices = mutableListOf(-1)
        
        for (groupIndex in 0 until trackGroupArray.length) {
            val trackGroup = trackGroupArray[groupIndex]
            for (trackIndex in 0 until trackGroup.length) {
                val format = trackGroup.getFormat(trackIndex)
                val quality = "${format.height}p"
                if (!qualityOptions.contains(quality)) {
                    qualityOptions.add(quality)
                    trackIndices.add(trackIndex)
                }
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Select Quality")
            .setItems(qualityOptions.toTypedArray()) { _, which ->
                setVideoQuality(trackIndices[which])
            }
            .show()
    }
    
    private fun setVideoQuality(trackIndex: Int) {
        val parametersBuilder = trackSelector.buildUponParameters()
        
        if (trackIndex == -1) {
            parametersBuilder.clearOverrides()
        } else {
            // Set specific quality
            parametersBuilder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        }
        
        trackSelector.setParameters(parametersBuilder)
        Toast.makeText(this, "Quality changed", Toast.LENGTH_SHORT).show()
    }
    
    private fun showSubtitleDialog() {
        val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: run {
            Toast.makeText(this, "No track info available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val textRenderer = getRendererIndex(C.TRACK_TYPE_TEXT)
        if (textRenderer == -1) {
            Toast.makeText(this, "No subtitles available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val trackGroupArray = mappedTrackInfo.getTrackGroups(textRenderer)
        val subtitleOptions = mutableListOf("Off")
        
        for (groupIndex in 0 until trackGroupArray.length) {
            val trackGroup = trackGroupArray[groupIndex]
            for (trackIndex in 0 until trackGroup.length) {
                val format = trackGroup.getFormat(trackIndex)
                subtitleOptions.add(format.label ?: format.language ?: "Track $trackIndex")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Select Subtitle")
            .setItems(subtitleOptions.toTypedArray()) { _, which ->
                setSubtitleTrack(textRenderer, which - 1)
            }
            .show()
    }
    
    private fun showAudioTrackDialog() {
        val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: run {
            Toast.makeText(this, "No track info available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val audioRenderer = getRendererIndex(C.TRACK_TYPE_AUDIO)
        if (audioRenderer == -1) {
            Toast.makeText(this, "No audio tracks available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val trackGroupArray = mappedTrackInfo.getTrackGroups(audioRenderer)
        val audioOptions = mutableListOf<String>()
        
        for (groupIndex in 0 until trackGroupArray.length) {
            val trackGroup = trackGroupArray[groupIndex]
            for (trackIndex in 0 until trackGroup.length) {
                val format = trackGroup.getFormat(trackIndex)
                audioOptions.add(format.label ?: format.language ?: "Track $trackIndex")
            }
        }
        
        if (audioOptions.isEmpty()) {
            Toast.makeText(this, "No audio tracks available", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Select Audio Track")
            .setItems(audioOptions.toTypedArray()) { _, which ->
                setAudioTrack(audioRenderer, which)
            }
            .show()
    }
    
    private fun getRendererIndex(trackType: Int): Int {
        val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return -1
        for (i in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getRendererType(i) == trackType) {
                return i
            }
        }
        return -1
    }
    
    private fun setSubtitleTrack(rendererIndex: Int, trackIndex: Int) {
        val parametersBuilder = trackSelector.buildUponParameters()
        
        if (trackIndex == -1) {
            parametersBuilder.setRendererDisabled(rendererIndex, true)
        } else {
            parametersBuilder.setRendererDisabled(rendererIndex, false)
        }
        
        trackSelector.setParameters(parametersBuilder)
        Toast.makeText(this, "Subtitle changed", Toast.LENGTH_SHORT).show()
    }
    
    private fun setAudioTrack(rendererIndex: Int, trackIndex: Int) {
        val parametersBuilder = trackSelector.buildUponParameters()
        trackSelector.setParameters(parametersBuilder)
        Toast.makeText(this, "Audio track changed", Toast.LENGTH_SHORT).show()
    }
    
    private fun initializePlayer() {
        if (player == null) {
            trackSelector = DefaultTrackSelector(this).apply {
                setParameters(buildUponParameters().setMaxVideoSizeSd())
            }
            
            player = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .build()
                .apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> {
                                    Toast.makeText(this@PlayerActivity, "Buffering...", Toast.LENGTH_SHORT).show()
                                }
                                Player.STATE_READY -> {
                                    Log.d("Player", "Ready to play")
                                }
                                Player.STATE_ENDED -> {
                                    Toast.makeText(this@PlayerActivity, "Playback ended", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        
                        override fun onPlayerError(error: PlaybackException) {
                            Toast.makeText(
                                this@PlayerActivity,
                                "Playback error: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            Log.e("Player", "Error", error)
                        }
                    })
                }
            playerView.player = player
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
        
        try {
            val parsedUrl = parseUrlWithHeaders(streamUrl)
            val url = parsedUrl.first
            val headers = parsedUrl.second.toMutableMap()
            
            if (cookie.isNotEmpty()) headers["Cookie"] = cookie
            if (referer.isNotEmpty()) headers["Referer"] = referer
            if (origin.isNotEmpty()) headers["Origin"] = origin
            
            val userAgentString = getUserAgentString(userAgent)
            
            val httpDataSourceFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgentString)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)
            
            val mediaItemBuilder = MediaItem.Builder().setUri(url)
            
            // Check for DRM
            if (streamUrl.contains("drmScheme=") && streamUrl.contains("drmLicense=")) {
                val drmScheme = extractDrmScheme(streamUrl)
                val licenseKey = extractDrmLicense(streamUrl)
                
                if (drmScheme == "clearkey" && licenseKey.isNotEmpty()) {
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
            
            Toast.makeText(this, "Loading stream...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("PlayerActivity", "Playback error", e)
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
    
    private fun getUserAgentString(userAgent: String): String {
        return when (userAgent) {
            "Chrome" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            "Firefox" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
            "Safari" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15"
            "Edge" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
            else -> Util.getUserAgent(this, "CookieM3U8Player")
        }
    }
    
    private fun releasePlayer() {
        player?.release()
        player = null
    }
    
    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode) {
            releasePlayer()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}
