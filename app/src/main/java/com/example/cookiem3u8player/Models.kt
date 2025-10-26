package com.example.cookiem3u8player

/**
 * Data class representing a channel/stream
 * This should be in its own file: Channel.kt
 */
data class Channel(
    val name: String,
    val url: String,
    val logo: String = "",
    val cookie: String = "",
    val referer: String = "",
    val origin: String = "",
    val userAgent: String = "Default",
    val groupTitle: String = ""
)

/**
 * Data class for history items
 */
data class HistoryItem(
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Data class for playlist entries
 */
data class PlaylistEntry(
    var name: String = "",
    var url: String = "",
    var logo: String = "",
    var cookie: String = "",
    var referer: String = "",
    var origin: String = "",
    var userAgent: String = "Default"
)
