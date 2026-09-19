package com.sinematv

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class SinemaTvProvider : MainAPI() {
    override var name = "SinemaTV"
    override var mainUrl = "https://sinematv.az"
    override var lang = "az"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var hasMainPage = true

    private val jsonMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override val mainPage = mainPageOf(
        "$mainUrl/film/" to "Son Filmlər",
        "$mainUrl/serial/" to "Seriallar",
        "$mainUrl/xarici-filmler/" to "Xarici Filmlər (Azərbaycanca)",
        "$mainUrl/turkce-filmler/" to "Türkcə Filmlər",
        "$mainUrl/hind-filmleri/" to "Hind Filmləri",
        "$mainUrl/mult/" to "Cizgi Filmləri",
        "$mainUrl/retro-filmler/" to "Köhnə Filmlər"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val base = request.data.removeSuffix("/")
        val url = if (page <= 1) "$base/" else "$base/page/$page/"
        val document = app.get(url).document

        val home = document.select("a.poster-item, .poster-item, .grid-item").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElem = if (tagName() == "a" && hasAttr("href")) this else selectFirst("a[href]")
        val href = fixUrl(linkElem?.attr("href") ?: return null)
        if (!href.contains(".html")) return null

        val title = attr("title").takeIf { it.isNotBlank() }
            ?: selectFirst(".poster-item__title, .poster__title, h2, h3, h4")?.text()?.trim()
            ?: linkElem.attr("title").takeIf { it.isNotBlank() }
            ?: "Film"

        val imgElem = selectFirst("img")
        val rawImg = imgElem?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }
        val posterUrl = fixUrlNull(rawImg)

        val isSeries = href.contains("/serial/")

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/index.php?do=search"
        val document = app.post(
            searchUrl,
            data = mapOf(
                "do" to "search",
                "subaction" to "search",
                "story" to query
            )
        ).document

        return document.select("a.poster-item, .poster-item, .grid-item").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst(".page__title, h1")?.text()?.trim() ?: "Adsız Film"
        val posterUrl = fixUrlNull(document.selectFirst(".page__poster img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        })
        val year = document.selectFirst(".page__meta-item--year, a[href*='/year/']")?.text()
            ?.filter { it.isDigit() }
            ?.toIntOrNull()
        val plot = document.selectFirst(".page__text, #fdesc")?.text()?.trim()
        val genres = document.select("a[href*='/genre/']").map { it.text().trim() }
        val actors = document.selectFirst(".page__meta-item:contains(В ролях:), .page__meta-item:contains(Rollarda:)")?.text()
            ?.substringAfter("Rollarda:")
            ?.substringAfter("В ролях:")
            ?.split(",")
            ?.mapNotNull { it.trim().ifEmpty { null } }

        val duration = document.selectFirst(".page__meta-item--duration")?.text()
            ?.filter { it.isDigit() }
            ?.toIntOrNull()

        // Extract player iframes
        val iframes = document.select("iframe").mapNotNull {
            val src = it.attr("src").ifEmpty { it.attr("data-src") }.ifEmpty { it.attr("data-veo-src") }
            if (src.isNotEmpty()) src else null
        }

        // Look for vv-player movie_id
        val movieId = iframes.firstNotNullOfOrNull { extractMovieId(it) }
            ?: extractMovieId(document.html())

        if (movieId != null) {
            val playerResult = fetchBalancerEpisodes(movieId, url)
            if (playerResult != null && playerResult.isNotEmpty()) {
                val isTvSeries = url.contains("/serial/") ||
                        playerResult.size > 1 ||
                        (playerResult.firstOrNull()?.season?.order ?: 0) > 0

                if (isTvSeries) {
                    val episodes = playerResult.mapIndexed { index, ep ->
                        val epOrder = ep.order ?: (index + 1)
                        val seasonOrder = ep.season?.order ?: 1
                        val epTitle = ep.title?.ifEmpty { null } ?: "Bölüm $epOrder"

                        val payload = EpisodeDataPayload(
                            movieId = movieId,
                            episodeId = ep.id,
                            fallbackUrl = url
                        ).toJson()

                        newEpisode(payload) {
                            this.name = epTitle
                            this.season = seasonOrder
                            this.episode = epOrder
                            this.posterUrl = ep.episodeVariants?.firstOrNull()?.previewImageFilepath
                        }
                    }

                    return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                        this.posterUrl = posterUrl
                        this.year = year
                        this.plot = plot
                        this.tags = genres
                        this.duration = duration
                        addActors(actors)
                    }
                } else {
                    val payload = EpisodeDataPayload(
                        movieId = movieId,
                        episodeId = playerResult.firstOrNull()?.id,
                        fallbackUrl = url
                    ).toJson()

                    return newMovieLoadResponse(title, url, TvType.Movie, payload) {
                        this.posterUrl = posterUrl
                        this.year = year
                        this.plot = plot
                        this.tags = genres
                        this.duration = duration
                        addActors(actors)
                    }
                }
            }
        }

        // Fallback if no balancer episodes found
        val isTvSeries = url.contains("/serial/")
        val payload = EpisodeDataPayload(movieId = movieId ?: "", fallbackUrl = url).toJson()

        return if (isTvSeries) {
            val fallbackEpisode = newEpisode(payload) {
                this.name = "Bölüm 1"
                this.season = 1
                this.episode = 1
                this.posterUrl = posterUrl
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, listOf(fallbackEpisode)) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
                this.tags = genres
                this.duration = duration
                addActors(actors)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, payload) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
                this.tags = genres
                this.duration = duration
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false

        val payload = try {
            jsonMapper.readValue(data, EpisodeDataPayload::class.java)
        } catch (e: Throwable) {
            null
        }

        val movieId = payload?.movieId?.ifEmpty { null }
        val episodeId = payload?.episodeId
        val fallbackUrl = payload?.fallbackUrl ?: if (data.startsWith("http")) data else null

        // 1. Try to load direct M3U8 streams from SinemaTV Balancer API
        if (movieId != null) {
            val episodes = fetchBalancerEpisodes(movieId, fallbackUrl ?: mainUrl)
            if (episodes != null) {
                val targetEpisode = if (episodeId != null) {
                    episodes.firstOrNull { it.id == episodeId } ?: episodes.firstOrNull()
                } else {
                    episodes.firstOrNull()
                }

                targetEpisode?.episodeVariants?.forEach { variant ->
                    val streamUrl = variant.filepath?.trim()
                    if (!streamUrl.isNullOrEmpty()) {
                        val dubTitle = variant.title?.ifEmpty { null } ?: "Standart"
                        val qualityStr = variant.streamQuality ?: "HD"

                        callback.invoke(
                            ExtractorLink(
                                source = name,
                                name = "$name - $dubTitle ($qualityStr)",
                                url = streamUrl,
                                referer = "$mainUrl/",
                                quality = Qualities.P1080.value,
                                type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                            )
                        )
                        foundLinks = true
                    }
                }
            }
        }

        // 2. If fallback URL is present, inspect external players & CDN iframes
        if (fallbackUrl != null) {
            try {
                val doc = app.get(fallbackUrl).document
                val iframes = doc.select("iframe").mapNotNull {
                    val src = it.attr("src").ifEmpty { it.attr("data-src") }.ifEmpty { it.attr("data-veo-src") }
                    if (src.isNotEmpty()) fixUrl(src) else null
                }

                for (iframeUrl in iframes) {
                    if (iframeUrl.contains("vv-player.php")) continue

                    // Check cdn1.sinematv.az / abyss players
                    if (iframeUrl.contains("cdn1.sinematv.az") || iframeUrl.contains("abyss.to")) {
                        try {
                            val cdnHtml = app.get(
                                iframeUrl,
                                headers = mapOf("Referer" to fallbackUrl)
                            ).text

                            val datasMatch = Regex("""const datas = "([^"]+)"""").find(cdnHtml)
                            if (datasMatch != null) {
                                val b64 = datasMatch.groupValues[1]
                                val decodedJson = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                                
                                val m3u8Regex = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""")
                                val directM3u8 = m3u8Regex.find(decodedJson)?.value

                                if (directM3u8 != null) {
                                    callback.invoke(
                                        ExtractorLink(
                                            source = name,
                                            name = "$name - CDN1 (Full HD)",
                                            url = directM3u8,
                                            referer = iframeUrl,
                                            quality = Qualities.P1080.value,
                                            type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                                        )
                                    )
                                    foundLinks = true
                                }
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }

                    // Standard extractor loader
                    loadExtractor(
                        url = iframeUrl,
                        referer = "$mainUrl/",
                        subtitleCallback = subtitleCallback,
                        callback = callback
                    )
                    foundLinks = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return foundLinks
    }

    private fun extractMovieId(text: String): String? {
        val regex = Regex("""movie_id=(\d+)""")
        return regex.find(text)?.groupValues?.getOrNull(1)
    }

    private suspend fun fetchBalancerEpisodes(movieId: String, refererUrl: String): List<EpisodeDto>? {
        return try {
            val playerUrl = "$mainUrl/vv-player.php?path=/&movie_id=$movieId"
            val playerHtml = app.get(playerUrl, referer = refererUrl).text

            val tokenMatch = Regex(""""DLE-API-TOKEN"\s*:\s*"([^"]+)"""").find(playerHtml)
            val reqIdMatch = Regex(""""Iframe-Request-Id"\s*:\s*"([^"]+)"""").find(playerHtml)
            val envBaseMatch = Regex("""window\.ENV_BASE_URL\s*=\s*['"]([^'"]+)['"]""").find(playerHtml)

            val token = tokenMatch?.groupValues?.getOrNull(1)
            val reqId = reqIdMatch?.groupValues?.getOrNull(1)
            val envBase = envBaseMatch?.groupValues?.getOrNull(1)
                ?: "/vv-api.php?path=/balancer-api/proxy/playlists"

            val headers = mutableMapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Referer" to playerUrl,
                "Origin" to mainUrl,
                "Accept" to "application/json, text/plain, */*",
                "Cache-Control" to "no-cache",
                "Pragma" to "no-cache"
            )
            if (!token.isNullOrEmpty()) headers["DLE-API-TOKEN"] = token
            if (!reqId.isNullOrEmpty()) headers["Iframe-Request-Id"] = reqId

            val apiUrl = fixUrl(envBase)
            val responseText = app.get(apiUrl, headers = headers, referer = playerUrl).text

            val apiResponse = try {
                jsonMapper.readValue(responseText, BalancerApiResponse::class.java)
            } catch (e: Throwable) {
                null
            }
            apiResponse?.playlist ?: apiResponse?.data
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
