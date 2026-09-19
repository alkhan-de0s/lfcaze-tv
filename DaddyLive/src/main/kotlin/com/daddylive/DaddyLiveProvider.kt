package com.daddylive

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Document
import java.net.URI

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

    // In-memory cache for channel list
    private var cachedChannels: List<LiveSearchResponse> = emptyList()
    private var lastCacheTime: Long = 0L
    private val cacheDurationMs = 15 * 60 * 1000L // 15 minutes

    override val mainPage = mainPageOf(
        "24-7-channels.php" to "24/7 Channels",
        "" to "Live Sports & Upcoming Events"
    )

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

            doc.select("a.upcoming-card").mapNotNull { card ->
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
