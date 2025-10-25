package com.example.cookiem3u8player

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.ui.PlayerView

class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        val urlEdit = findViewById<EditText>(R.id.urlEdit)
        val cookieEdit = findViewById<EditText>(R.id.cookieEdit)
        val playButton = findViewById<Button>(R.id.playButton)

        playButton.setOnClickListener {
            val url = urlEdit.text.toString()
            val cookie = cookieEdit.text.toString()
            playStream(url, cookie)
        }
    }

    private fun playStream(url: String, cookie: String) {
        player?.release()
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Cookie" to cookie))

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMimeType("application/vnd.apple.mpegurl")
            .build()

        player!!.setMediaItem(mediaItem)
        player!!.prepare()
        player!!.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}