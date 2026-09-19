package com.xstream.tv.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class LiverpoolChannel(
    val id: String = "",
    val name: String = "",
    val url: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LiverpoolConfig(
    val active: Boolean = false,
    val match: String = "Liverpool FC",
    val info: String = "",
    val poster: String = "https://upload.wikimedia.org/wikipedia/en/thumb/0/0c/Liverpool_FC.svg/800px-Liverpool_FC.svg.png",
    val channels: List<LiverpoolChannel> = emptyList()
)

data class ResolvedStream(
    val streamUrl: String,
    val referer: String,
    val origin: String,
    val userAgent: String,
    val channelName: String,
    val serverName: String = "Player 1"
) : java.io.Serializable
