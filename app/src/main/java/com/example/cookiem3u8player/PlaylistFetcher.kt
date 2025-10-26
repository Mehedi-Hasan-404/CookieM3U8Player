package com.example.cookiem3u8player

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PlaylistFetcher(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    suspend fun fetchPlaylistFromUrl(url: String, cookie: String = ""): Result<List<Channel>> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        if (cookie.isNotEmpty()) {
                            addHeader("Cookie", cookie)
                        }
                    }
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to fetch playlist: ${response.code}"))
                }
                
                val content = response.body?.string() ?: ""
                val channels = parsePlaylistContent(content, url)
                
                // Cache the channels
                cacheChannels(url, channels)
                
                Result.success(channels)
            } catch (e: Exception) {
                Log.e("PlaylistFetcher", "Error fetching playlist", e)
                Result.failure(e)
            }
        }
    }
    
    private fun parsePlaylistContent(content: String, sourceUrl: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        
        // Detect format
        when {
            content.trim().startsWith("[") || content.trim().startsWith("{") -> {
                // JSON format
                channels.addAll(parseJsonChannels(content))
            }
            content.contains("#EXTINF") -> {
                // M3U/M3U8 format
                channels.addAll(parseM3UChannels(content))
            }
        }
        
        return channels
    }
    
    private fun parseJsonChannels(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        
        try {
            val jsonArray = org.json.JSONArray(content)
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
            Log.e("PlaylistFetcher", "Error parsing JSON", e)
        }
        
        return channels
    }
    
    private fun parseM3UChannels(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        
        for (i in lines.indices) {
            val line = lines[i].trim()
            
            when {
                line.startsWith("#EXTINF:") -> {
                    // Extract tvg-logo
                    val logoMatch = Regex("""tvg-logo="([^"]+)"""").find(line)
                    currentLogo = logoMatch?.groupValues?.get(1) ?: ""
                    
                    // Extract group-title
                    val groupMatch = Regex("""group-title="([^"]+)"""").find(line)
                    currentGroup = groupMatch?.groupValues?.get(1) ?: ""
                    
                    // Extract name (after the last comma)
                    val commaIndex = line.lastIndexOf(',')
                    if (commaIndex != -1) {
                        currentName = line.substring(commaIndex + 1).trim()
                    }
                    
                    if (currentName.isEmpty()) {
                        currentName = "Channel ${i + 1}"
                    }
                }
                
                line.startsWith("http") || line.startsWith("https") -> {
                    if (currentName.isEmpty()) {
                        currentName = "Stream ${i + 1}"
                    }
                    
                    // Parse URL for embedded headers
                    val urlParts = line.split("|")
                    val url = urlParts[0].trim()
                    var referer = ""
                    var cookie = ""
                    var userAgent = "Default"
                    
                    // Parse additional parameters
                    if (urlParts.size > 1) {
                        for (j in 1 until urlParts.size) {
                            val param = urlParts[j].trim()
                            when {
                                param.startsWith("Referer=", ignoreCase = true) -> {
                                    referer = param.substring(8)
                                }
                                param.startsWith("cookie=", ignoreCase = true) -> {
                                    cookie = param.substring(7)
                                }
                                param.startsWith("User-Agent=", ignoreCase = true) -> {
                                    userAgent = "Chrome"
                                }
                            }
                        }
                    }
                    
                    val channel = Channel(
                        name = currentName,
                        url = url,
                        logo = currentLogo,
                        referer = referer,
                        cookie = cookie,
                        userAgent = userAgent,
                        groupTitle = currentGroup
                    )
                    channels.add(channel)
                    
                    // Reset for next entry
                    currentName = ""
                    currentLogo = ""
                }
            }
        }
        
        return channels
    }
    
    private fun cacheChannels(url: String, channels: List<Channel>) {
        val prefs = context.getSharedPreferences("CookieM3U8PlayerPrefs", Context.MODE_PRIVATE)
        val channelsJson = Gson().toJson(channels)
        prefs.edit().putString("cached_channels_$url", channelsJson).apply()
    }
}
