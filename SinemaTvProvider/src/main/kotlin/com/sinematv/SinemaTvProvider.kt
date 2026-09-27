package com.sinematv

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.Dns
import okhttp3.Request
import org.jsoup.nodes.Element
import java.net.InetAddress
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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

    private val gorodyshkaDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (hostname == "gorodyshka.link") {
                return listOf(
                    InetAddress.getByAddress("gorodyshka.link", byteArrayOf(76.toByte(), 164.toByte(), 203.toByte(), 166.toByte())),
                    InetAddress.getByAddress("gorodyshka.link", byteArrayOf(76.toByte(), 164.toByte(), 203.toByte(), 170.toByte())),
                    InetAddress.getByAddress("gorodyshka.link", byteArrayOf(2.toByte(), 59.toByte(), 219.toByte(), 121.toByte()))
                )
            }
            return Dns.SYSTEM.lookup(hostname)
        }
    }

    private val balancerClient by lazy {
        app.baseClient.newBuilder()
            .dns(gorodyshkaDns)
            .followRedirects(false)
            .build()
    }

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
            if (src.isNotEmpty()) fixUrl(src) else null
        }

        // Only search for balancer movie_id in iframes and document HTML when explicitly present
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

        // Fallback for movies/serials without balancer (e.g. cdn1.sinematv.az)
        val isTvSeries = url.contains("/serial/")
        val payload = EpisodeDataPayload(movieId = null, fallbackUrl = url).toJson()

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

        // 1. Try to load direct M3U8 streams from SinemaTV Balancer API (when real movieId is present)
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

                        if (streamUrl.contains("parsed.json")) {
                            try {
                                val parsedJsonText = app.get(streamUrl, referer = "$mainUrl/").text
                                val parsedDto = tryParseJson<ParsedJsonDto>(parsedJsonText)
                                val primarySource = parsedDto?.sources?.firstOrNull()

                                // 1. Emit direct quality streams if available
                                primarySource?.links?.forEach { linkItem ->
                                    val src = linkItem.src?.trim()
                                    val qStr = linkItem.quality ?: "720"
                                    if (!src.isNullOrEmpty()) {
                                        val resolvedSrc = resolveBalancerUrl(src)
                                        callback.invoke(
                                            ExtractorLink(
                                                source = name,
                                                name = "$name - $dubTitle (${qStr}p)",
                                                url = resolvedSrc,
                                                referer = "$mainUrl/",
                                                quality = getQualityFromName(qStr),
                                                type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                                            )
                                        )
                                        foundLinks = true
                                    }
                                }

                                // 2. Emit master adaptive grouped.m3u8
                                val masterLink = primarySource?.link?.trim()
                                if (!masterLink.isNullOrEmpty()) {
                                    val resolvedMaster = resolveBalancerUrl(masterLink)
                                    callback.invoke(
                                        ExtractorLink(
                                            source = name,
                                            name = "$name - $dubTitle (Auto / Adaptive)",
                                            url = resolvedMaster,
                                            referer = "$mainUrl/",
                                            quality = quality,
                                            type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                                        )
                                    )
                                    foundLinks = true
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                val resolved = resolveBalancerUrl(streamUrl)
                                callback.invoke(
                                    ExtractorLink(
                                        source = name,
                                        name = "$name - $dubTitle ($qualityStr)",
                                        url = resolved,
                                        referer = "$mainUrl/",
                                        quality = quality,
                                        type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                                    )
                                )
                                foundLinks = true
                            }
                        } else {
                            val resolved = resolveBalancerUrl(streamUrl)
                            callback.invoke(
                                ExtractorLink(
                                    source = name,
                                    name = "$name - $dubTitle ($qualityStr)",
                                    url = resolved,
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
        }

        // 2. Inspect fallback page for cdn1.sinematv.az and other video embed iframes
        if (fallbackUrl != null) {
            try {
                val doc = app.get(fallbackUrl).document
                val iframes = doc.select("iframe").mapNotNull {
                    val src = it.attr("src").ifEmpty { it.attr("data-src") }.ifEmpty { it.attr("data-veo-src") }
                    if (src.isNotEmpty()) fixUrl(src) else null
                }

                for (iframeUrl in iframes) {
                    if (iframeUrl.contains("vv-player.php")) continue

                    if (iframeUrl.contains("cdn1.sinematv.az")) {
                        val cdnFound = extractCdn1Streams(iframeUrl, callback)
                        if (cdnFound) foundLinks = true
                    } else {
                        loadExtractor(
                            url = iframeUrl,
                            referer = "$mainUrl/",
                            subtitleCallback = subtitleCallback,
                            callback = callback
                        )
                        foundLinks = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return foundLinks
    }

    private suspend fun extractCdn1Streams(iframeUrl: String, callback: (ExtractorLink) -> Unit): Boolean {
        return try {
            val html = app.get(iframeUrl, referer = "$mainUrl/").text
            val datasMatch = Regex("""const\s+datas\s*=\s*"([^"]+)"""").find(html) ?: return false
            val b64 = datasMatch.groupValues[1]

            val rawJson = String(Base64.decode(b64, Base64.DEFAULT), Charsets.ISO_8859_1)
            val datas = tryParseJson<Cdn1DatasDto>(rawJson) ?: return false
            val mediaStr = datas.media ?: return false
            val slug = datas.slug ?: return false
            val md5Id = datas.md5Id ?: return false
            val userId = datas.userId ?: return false

            val mediaBytes = ByteArray(mediaStr.length) { i -> mediaStr[i].code.toByte() }
            val mediaKey = "$userId:$slug:$md5Id"
            val keyHex = md5Hex(mediaKey.toByteArray(Charsets.UTF_8))
            val keyBytes = keyHex.toByteArray(Charsets.UTF_8)
            val ivBytes = keyBytes.copyOfRange(0, 16)

            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
            val decryptedBytes = cipher.doFinal(mediaBytes)
            val decryptedJson = String(decryptedBytes, Charsets.UTF_8)

            val abyssMedia = tryParseJson<AbyssMediaDto>(decryptedJson) ?: return false
            val sources = abyssMedia.mp4?.sources ?: return false
            val domains = abyssMedia.mp4?.domains ?: return false
            var found = false

            sources.forEach { source ->
                val size = source.size ?: return@forEach
                val resId = source.resId ?: return@forEach
                val sub = source.sub ?: ""
                val domain = domains.firstOrNull { it.contains(sub) } ?: domains.firstOrNull() ?: return@forEach

                val sizeDigits = size.toString().map { it.digitToInt().toByte() }.toByteArray()
                val sizeMd5Hex = md5Hex(sizeDigits)
                val encKeyBytes = sizeMd5Hex.toByteArray(Charsets.UTF_8)
                val encIvBytes = encKeyBytes.copyOfRange(0, 16)

                val path = "/mp4/$md5Id/$resId/$size?v=$slug"
                val encCipher = Cipher.getInstance("AES/CTR/NoPadding")
                encCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKeyBytes, "AES"), IvParameterSpec(encIvBytes))
                val encryptedPathBytes = encCipher.doFinal(path.toByteArray(Charsets.UTF_8))

                val b1 = Base64.encodeToString(encryptedPathBytes, Base64.NO_WRAP).replace("=", "")
                val b2 = Base64.encodeToString(b1.toByteArray(Charsets.ISO_8859_1), Base64.NO_WRAP).replace("=", "")

                val soraUrl = "https://$domain/sora/$size/$b2"
                val directUrl = try {
                    val res = app.get(
                        soraUrl,
                        headers = mapOf("Referer" to "https://abysscdn.com/"),
                        allowRedirects = false
                    )
                    if (res.code in 300..399 && !res.headers["location"].isNullOrEmpty()) {
                        res.headers["location"]!!
                    } else {
                        soraUrl
                    }
                } catch (e: Exception) {
                    soraUrl
                }

                callback.invoke(
                    ExtractorLink(
                        source = name,
                        name = "$name - ${source.label ?: "HD"} (Direct)",
                        url = directUrl,
                        referer = "https://abysscdn.com/",
                        quality = getQualityFromName(source.label),
                        type = com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                    )
                )
                found = true
            }

            found
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun md5Hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun resolveBalancerUrl(url: String): String {
        if (!url.contains("gorodyshka.link")) return url
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "$mainUrl/")
                .build()
            balancerClient.newCall(req).execute().use { response ->
                val location = response.header("Location")
                if (!location.isNullOrEmpty()) {
                    location
                } else {
                    url
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            url
        }
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
