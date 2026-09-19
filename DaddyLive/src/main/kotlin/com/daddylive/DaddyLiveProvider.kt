package com.daddylive

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Document
import java.net.URI

data class LiverpoolChannel(
    val id: String? = null,
    val name: String? = null,
    val url: String? = null
)

data class LiverpoolConfig(
    val active: Boolean = false,
    val match: String? = null,
    val info: String? = null,
    val poster: String? = null,
    val channels: List<LiverpoolChannel> = emptyList()
)

class DaddyLiveProvider : MainAPI() {
    override var mainUrl = "https://dlive.sx"
    override var name = "DaddyLive"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = true

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

    private val defaultPoster = "https://dlive.sx/assets/logos/logo.png"
    private val liverpoolPoster =
        "https://upload.wikimedia.org/wikipedia/en/thumb/0/0c/Liverpool_FC.svg/800px-Liverpool_FC.svg.png"
    private val remoteLiverpoolConfigUrl =
        "https://raw.githubusercontent.com/alkhan-de0s/lfcaze-tv/master/liverpool.json"

    private val jsonMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // In-memory cache for channel list
    private var cachedChannels: List<LiveSearchResponse> = emptyList()
    private var lastCacheTime: Long = 0L
    private val cacheDurationMs = 15 * 60 * 1000L // 15 minutes

    override val mainPage = mainPageOf(
        "liverpool" to "🔴 Liverpool FC Azerbaijan Supporters",
        "24-7-channels.php" to "24/7 Channels",
        "" to "Live Sports & Upcoming Events"
    )


    private suspend fun fetchLiverpoolStreams(): List<LiveSearchResponse> {
        val liverpoolStreams = mutableListOf<LiveSearchResponse>()

        // 1. Check remote admin configuration from GitHub (updated via Telegram Bot)
        try {
            val bustUrl = "$remoteLiverpoolConfigUrl?t=${System.currentTimeMillis()}"
            val responseText = app.get(
                bustUrl,
                headers = mapOf(
                    "Cache-Control" to "no-cache, no-store, must-revalidate",
                    "Pragma" to "no-cache"
                )
            ).text

            val config = jsonMapper.readValue(responseText, LiverpoolConfig::class.java)
            if (config.active && config.channels.isNotEmpty()) {
                val matchTitle = config.match?.takeIf { it.isNotBlank() } ?: "Liverpool FC"
                val matchPoster = config.poster?.takeIf { it.isNotBlank() } ?: liverpoolPoster

                for (ch in config.channels) {
                    val rawId = ch.id?.trim() ?: ""
                    val directUrl = ch.url?.trim() ?: ""
                    val targetUrl = when {
                        directUrl.isNotBlank() -> fixUrl(directUrl)
                        rawId.startsWith("http") -> rawId
                        rawId.startsWith("stream-") -> "$mainUrl/watch.php?id=$rawId"
                        rawId.isNotBlank() -> "$mainUrl/watch.php?id=stream-$rawId"
                        else -> null
                    } ?: continue

                    val streamName = ch.name?.takeIf { it.isNotBlank() } ?: "$matchTitle Yayın"
                    liverpoolStreams.add(
                        newLiveSearchResponse(streamName, targetUrl, TvType.Live) {
                            this.posterUrl = matchPoster
                        }
                    )
                }
            }
        } catch (e: Throwable) {
            // Ignore failure, fall through to auto-scraper
        }


        if (liverpoolStreams.isNotEmpty()) {
            return liverpoolStreams
        }

        // 2. Fallback: Automatically search upcoming live events for Liverpool/LFC
        try {
            val upcoming = fetchUpcomingEvents()
            val autoLfc = upcoming.filter { event ->
                val title = event.name.lowercase()
                title.contains("liverpool") || title.contains(" lfc") || title.startsWith("lfc ")
            }
            if (autoLfc.isNotEmpty()) {
                return autoLfc
            }
        } catch (e: Throwable) {
            // Ignore
        }

        // 3. Fallback: If no match today, show LFCTV if available in 24/7 channels
        try {
            val allChannels = fetchAll247Channels()
            val lfctv = allChannels.filter { it.name.contains("LFCTV", ignoreCase = true) }
            if (lfctv.isNotEmpty()) {
                return lfctv
            }
        } catch (e: Throwable) {
            // Ignore
        }

        return emptyList()
    }

    private suspend fun fetchAll247Channels(): List<LiveSearchResponse> {
        val now = System.currentTimeMillis()
        if (cachedChannels.isNotEmpty() && (now - lastCacheTime) < cacheDurationMs) {
            return cachedChannels
        }

        return try {
            val doc = app.get(
                "$mainUrl/24-7-channels.php",
                headers = mapOf("User-Agent" to userAgent)
            ).document

            val items = doc.select("a.card[href*='watch.php?id=']").mapNotNull { card ->
                val title = card.selectFirst(".card__title")?.text()?.trim()
                    ?: card.attr("data-title").trim()
                val href = card.attr("href").trim()
                if (title.isBlank() || href.isBlank()) return@mapNotNull null

                val fullUrl = fixUrl(href)
                newLiveSearchResponse(title, fullUrl, TvType.Live) {
                    this.posterUrl = defaultPoster
                }
            }

            if (items.isNotEmpty()) {
                cachedChannels = items
                lastCacheTime = now
            }
            items
        } catch (e: Throwable) {
            e.printStackTrace()
            cachedChannels
        }
    }

    private suspend fun fetchUpcomingEvents(): List<LiveSearchResponse> {
        return try {
            val doc = app.get(
                "$mainUrl/",
                headers = mapOf("User-Agent" to userAgent)
            ).document

            val items = doc.select("a.upcoming-card").mapNotNull { card ->
                val title = card.selectFirst(".upcoming-card__title")?.text()?.trim()
                    ?: card.selectFirst("img")?.attr("alt")?.trim()
                    ?: card.text().trim()
                val href = card.attr("href").trim()
                if (title.isBlank() || href.isBlank()) return@mapNotNull null

                val img = card.selectFirst("img")?.attr("src")?.trim()
                val poster = if (!img.isNullOrBlank()) fixUrl(img) else defaultPoster
                val fullUrl = fixUrl(href)

                newLiveSearchResponse(title, fullUrl, TvType.Live) {
                    this.posterUrl = poster
                }
            }
            items
        } catch (e: Throwable) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val list = when (request.data) {
            "liverpool" -> fetchLiverpoolStreams()
            "24-7-channels.php" -> fetchAll247Channels()
            "" -> fetchUpcomingEvents()
            else -> fetchAll247Channels()
        }

        return newHomePageResponse(
            request.name,
            list,
            hasNext = false
        )
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val channels = fetchAll247Channels()
        val q = query.trim().lowercase()
        return channels.filter { it.name.lowercase().contains(q) }
    }

    override suspend fun load(url: String): LoadResponse {
        val cleanUrl = fixUrl(url)
        val channelIdMatch = """(?:id=|stream-)(\d+)""".toRegex().find(cleanUrl)
        val channelId = channelIdMatch?.groupValues?.get(1) ?: ""

        val title = if (channelId.isNotBlank()) {
            val cached = cachedChannels.find { it.url.contains("id=$channelId") }
            cached?.name ?: "Channel $channelId"
        } else {
            "DaddyLive Stream"
        }

        return newLiveStreamLoadResponse(
            name = title,
            url = cleanUrl,
            dataUrl = cleanUrl
        ) {
            this.posterUrl = defaultPoster
            this.plot = "Live TV stream powered by DaddyLive ($channelId)"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val channelIdMatch = """(?:id=|stream-)(\d+)""".toRegex().find(data)
        val channelId = channelIdMatch?.groupValues?.get(1) ?: return false

        // Folders supported by DaddyLive player architecture
        val playerFolders = listOf("stream", "cast", "watch", "player", "plus", "casting")

        var iframeUrl: String? = null
        var lastStreamPageUrl: String? = null

        for (folder in playerFolders) {
            val streamPageUrl = "$mainUrl/$folder/stream-$channelId.php"
            try {
                val streamDoc: Document = app.get(
                    streamPageUrl,
                    headers = mapOf(
                        "Referer" to "$mainUrl/watch.php?id=$channelId",
                        "User-Agent" to userAgent
                    )
                ).document

                val frameSrc = streamDoc.select("iframe#thatframe, iframe[src*='/e/'], iframe").attr("src").trim()
                if (frameSrc.isNotBlank() && !frameSrc.contains("about:blank")) {
                    iframeUrl = when {
                        frameSrc.startsWith("//") -> "https:$frameSrc"
                        frameSrc.startsWith("http") -> frameSrc
                        else -> "$mainUrl/$frameSrc"
                    }
                    lastStreamPageUrl = streamPageUrl
                    break
                }
            } catch (err: Throwable) {
                continue
            }
        }

        val embedUrl = iframeUrl ?: return false
        val streamPageReferer = lastStreamPageUrl ?: "$mainUrl/stream/stream-$channelId.php"

        // Parse embed host for proper Origin and Referer headers
        val embedUri = URI(embedUrl)
        val embedHost = "${embedUri.scheme}://${embedUri.host}"

        val embedHtml = app.get(
            embedUrl,
            headers = mapOf(
                "Referer" to streamPageReferer,
                "User-Agent" to userAgent
            )
        ).text

        val econfigRegex = """window\._econfig\s*=\s*'([^']+)'""".toRegex()
        val econfig = econfigRegex.find(embedHtml)?.groupValues?.get(1) ?: return false

        val decodedJson = DaddyLiveDecoder.decodeEConfig(econfig) ?: return false
        val streamUrl = DaddyLiveDecoder.extractStreamUrl(decodedJson) ?: return false

        callback.invoke(
            ExtractorLink(
                source = this.name,
                name = this.name,
                url = streamUrl,
                referer = "$embedHost/",
                quality = Qualities.Unknown.value,
                type = ExtractorLinkType.M3U8,
                headers = mapOf(
                    "Referer" to "$embedHost/",
                    "Origin" to embedHost,
                    "User-Agent" to userAgent
                )
            )
        )

        return true
    }
}
