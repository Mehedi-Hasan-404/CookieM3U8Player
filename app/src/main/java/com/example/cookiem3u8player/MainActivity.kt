// Add these imports to the existing MainActivity.kt

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.source.dash.DashMediaSource
import java.util.*

// Replace the startPlayback function in MainActivity with this updated version:

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

// Add these helper functions to MainActivity:

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
