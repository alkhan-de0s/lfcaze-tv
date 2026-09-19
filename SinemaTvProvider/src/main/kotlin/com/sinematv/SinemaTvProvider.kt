package com.sinematv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
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

    override val mainPage = mainPageOf(
        "$mainUrl/film/page/" to "Son Filmlər",
        "$mainUrl/serial/page/" to "Seriallar",
        "$mainUrl/xarici-filmler/page/" to "Xarici Filmlər (Azərbaycanca)",
        "$mainUrl/turkce-filmler/page/" to "Türkcə Filmlər",
        "$mainUrl/hind-filmleri/page/" to "Hind Filmləri",
        "$mainUrl/mult/page/" to "Cizgi Filmləri",
        "$mainUrl/anime/page/" to "Anime",
        "$mainUrl/new-items/page/" to "Yenilər"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            request.data.removeSuffix("/page/") + "/"
        } else {
            "${request.data}$page/"
        }

        val document = app.get(url).document
        val elements = document.select("#dle-content a.poster-item")
            .ifEmpty { document.select("a.poster-item") }

        val items = elements.mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?do=search&subaction=search&story=$query"
        val document = app.get(searchUrl).document
        val elements = document.select("#dle-content a.poster-item")
            .ifEmpty { document.select("a.poster-item") }

        return elements.mapNotNull { it.toSearchResponse() }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = selectFirst(".poster-item__title")?.text()?.trim()
            ?: attr("title").trim().ifEmpty { null }
            ?: return null

        val rawHref = attr("href").trim()
        if (rawHref.isEmpty()) return null
        val href = fixUrl(rawHref)

        val imgElem = selectFirst(".poster-item__img img")
        val rawPoster = imgElem?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }
        val posterUrl = fixUrlNull(rawPoster)
        val isTvSeries = href.contains("/serial/")

        return if (isTvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: "SinemaTV"

        val rawPoster = document.selectFirst(".page__poster img")?.let {
            it.attr("src").ifEmpty { it.attr("data-src") }
        } ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        val posterUrl = fixUrlNull(rawPoster)

        val year = document.selectFirst(".page__year")?.text()?.trim()?.toIntOrNull()
        val plot = document.selectFirst(".page__text.full-text, .pmovie__text")?.text()?.trim()
        val genres = document.selectFirst(".page__meta-item--genres")?.text()
            ?.split(",")
            ?.mapNotNull { it.trim().ifEmpty { null } }

        val actors = document.selectFirst(".page__info-subinfo")?.text()
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

        val payload = tryParseJson<EpisodeDataPayload>(data)
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
                        val quality = getQualityFromName(qualityStr)

                        callback.invoke(
                            ExtractorLink(
                                source = name,
                                name = "$name - $dubTitle ($qualityStr)",
                                url = streamUrl,
                                referer = "$mainUrl/",
                                quality = quality,
                                type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                            )
                        )
                        foundLinks = true
                    }
                }
            }
        }

        // 2. If fallback URL is present, inspect third-party players/iframes
        if (fallbackUrl != null) {
            try {
                val doc = app.get(fallbackUrl).document
                val iframes = doc.select("iframe").mapNotNull {
                    val src = it.attr("src").ifEmpty { it.attr("data-src") }.ifEmpty { it.attr("data-veo-src") }
                    if (src.isNotEmpty()) fixUrl(src) else null
                }

                for (iframeUrl in iframes) {
                    if (iframeUrl.contains("vv-player.php")) continue

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
                "INT-LANG" to "PHP",
                "INT-LANG-VERSION" to "8.4.25",
                "INT-TYPE" to "DLE",
                "INT-VERSION" to "17.0",
                "INT-MODULE-VERSION" to "2.4.20"
            )

            if (!token.isNullOrEmpty()) headers["DLE-API-TOKEN"] = token
            if (!reqId.isNullOrEmpty()) headers["Iframe-Request-Id"] = reqId

            val apiUrl = "$mainUrl$envBase/catalog-api/episodes?content-id=$movieId"
            val episodesJson = app.get(apiUrl, headers = headers).text

            tryParseJson<List<EpisodeDto>>(episodesJson)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getQualityFromName(quality: String?): Int {
        return when (quality?.uppercase()) {
            "4K", "2160P", "UHD" -> Qualities.P2160.value
            "1080P", "FHD", "BDRIP" -> Qualities.P1080.value
            "720P", "HD", "WEBRIP" -> Qualities.P720.value
            "480P", "SD" -> Qualities.P480.value
            "360P" -> Qualities.P360.value
            else -> Qualities.P1080.value
        }
    }
}
