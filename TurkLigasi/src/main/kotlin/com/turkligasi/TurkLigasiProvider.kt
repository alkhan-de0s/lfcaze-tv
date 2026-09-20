package com.turkligasi

import android.util.Base64
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.turkligasi.extractors.Extractors
import org.jsoup.Jsoup

class TurkLigasiProvider : MainAPI() {
    override var mainUrl = "https://www.trgoals183.top"
    override var name = "Türk Liqası"
    override val supportedTypes = setOf(TvType.Live, TvType.TvSeries)
    override var lang = "az"
    override val hasMainPage = true
    override val hasQuickSearch = true

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

    private val defaultPoster = "https://raw.githubusercontent.com/alkhan-de0s/lfcaze-tv/master/TurkLigasi/icon.png"

    private val jsonMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private var activeDomains = DomainsConfig()
    private var lastDomainsUpdate = 0L

    data class ChannelDef(
        val name: String,
        val trgoalsId: String = "",
        val biatId: String = "",
        val directUrl: String = "",
        val logo: String = ""
    )

    private val tvChannels = listOf(
        ChannelDef("beIN Sports 1 HD", trgoalsId = "100001", biatId = "zirve", logo = "https://i.imgur.com/8QeO3tL.png"),
        ChannelDef("beIN Sports 2 HD", trgoalsId = "100002", biatId = "yayinb2", logo = "https://i.imgur.com/8QeO3tL.png"),
        ChannelDef("beIN Sports 3 HD", trgoalsId = "100003", biatId = "yayinb3", logo = "https://i.imgur.com/8QeO3tL.png"),
        ChannelDef("beIN Sports 4 HD", trgoalsId = "100004", biatId = "yayinb4", logo = "https://i.imgur.com/8QeO3tL.png"),
        ChannelDef("beIN Sports 5 HD", trgoalsId = "100005", biatId = "yayinb5", logo = "https://i.imgur.com/8QeO3tL.png"),
        ChannelDef("beIN Sports Max 1", trgoalsId = "100006", biatId = "yayinbm1", logo = "https://i.imgur.com/8QeO3tL.png"),
        ChannelDef("beIN Sports Max 2", trgoalsId = "100007", biatId = "yayinbm2", logo = "https://i.imgur.com/8QeO3tL.png"),
        ChannelDef("S Sport 1 HD", trgoalsId = "100010", biatId = "yayinss", logo = "https://i.imgur.com/4qJdZ9X.png"),
        ChannelDef("S Sport 2 HD", trgoalsId = "100011", biatId = "yayinss2", logo = "https://i.imgur.com/4qJdZ9X.png"),
        ChannelDef("Tivibu Spor 1 HD", trgoalsId = "100021", biatId = "yayint1", logo = "https://i.imgur.com/M6Lg5oH.png"),
        ChannelDef("Tivibu Spor 2 HD", trgoalsId = "100022", biatId = "yayint2", logo = "https://i.imgur.com/M6Lg5oH.png"),
        ChannelDef("Tivibu Spor 3 HD", trgoalsId = "100023", biatId = "yayint3", logo = "https://i.imgur.com/M6Lg5oH.png"),
        ChannelDef("Tivibu Spor 4 HD", trgoalsId = "100024", biatId = "yayint4", logo = "https://i.imgur.com/M6Lg5oH.png"),
        ChannelDef("Spor Smart 1 HD", trgoalsId = "100030", biatId = "yayinsm", logo = "https://i.imgur.com/Z4u5nKz.png"),
        ChannelDef("Spor Smart 2 HD", trgoalsId = "100031", biatId = "yayinsm2", logo = "https://i.imgur.com/Z4u5nKz.png"),
        ChannelDef("TRT Spor HD", directUrl = "https://tv-trtspor1.medya.trt.com.tr/master.m3u8", logo = "https://i.imgur.com/kYqU3U4.png"),
        ChannelDef("TRT Spor Yıldız HD", directUrl = "https://tv-trtspor2.medya.trt.com.tr/master.m3u8", logo = "https://i.imgur.com/kYqU3U4.png"),
        ChannelDef("TRT 1 HD", directUrl = "https://d1u68oyra9spme.cloudfront.net/master.m3u8", logo = "https://i.imgur.com/kYqU3U4.png"),
        ChannelDef("A Spor HD", trgoalsId = "100052", biatId = "yayinas", logo = "https://i.imgur.com/YgY72B2.png"),
        ChannelDef("TV 8.5 HD", trgoalsId = "100051", biatId = "yayintv8", logo = "https://i.imgur.com/K3V0V1B.png"),
        ChannelDef("Eurosport 1 HD", trgoalsId = "100056", logo = "https://i.imgur.com/X4uN9oN.png"),
        ChannelDef("Eurosport 2 HD", trgoalsId = "100057", logo = "https://i.imgur.com/X4uN9oN.png"),
        ChannelDef("NBA TV HD", trgoalsId = "100053", logo = "https://i.imgur.com/1Gf3D9F.png")
    )

    override val mainPage = mainPageOf(
        "matches" to "🔥 Günün Canlı Matçları",
        "channels" to "📺 24/7 İdman Kanalları",
        "azerbaijan" to "🇦🇿 Azərbaycan İdman Yayımları (CBC Sport / İdman TV)",
        "football" to "⚽ Futbol Matçları"
    )

    private suspend fun resolveDomains(): DomainsConfig {
        val now = System.currentTimeMillis()
        if (now - lastDomainsUpdate > 30 * 60 * 1000L) { // 30 minutes
            try {
                val githubRaw = "https://raw.githubusercontent.com/alkhan-de0s/lfcaze-tv/master/domains.json"
                val res = app.get(githubRaw, timeout = 5L).text
                activeDomains = jsonMapper.readValue<DomainsConfig>(res)
                mainUrl = activeDomains.trgoals
                lastDomainsUpdate = now
            } catch (_: Exception) {
                // keep current config
            }
        }
        return activeDomains
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val domains = resolveDomains()

        val list: List<SearchResponse> = when (request.data) {
            "channels" -> {
                tvChannels.map { ch ->
                    val payload = MatchDataPayload(
                        title = ch.name,
                        sport = "Canlı TV",
                        time = "7/24",
                        poster = ch.logo.ifEmpty { defaultPoster },
                        sources = buildList {
                            if (ch.trgoalsId.isNotEmpty()) {
                                add(MatchSource("TRGoals FHD", "trgoals", ch.trgoalsId))
                            }
                            if (ch.biatId.isNotEmpty()) {
                                add(MatchSource("BiatSports HD", "biatsports", ch.biatId))
                            }
                            if (ch.directUrl.isNotEmpty()) {
                                add(MatchSource("Rəsmi M3U8", "direct", ch.directUrl))
                            }
                        }
                    )
                    val jsonPayload = jsonMapper.writeValueAsString(payload)
                    val encoded = Base64.encodeToString(jsonPayload.toByteArray(), Base64.NO_WRAP)

                    newLiveSearchResponse(ch.name, "payload:$encoded", TvType.Live) {
                        this.posterUrl = ch.logo.ifEmpty { defaultPoster }
                    }
                }
            }
            "azerbaijan" -> {
                val all = fetchMackeyfiMatches(domains.mackeyfi)
                all.filter {
                    it.name.contains("CBC", ignoreCase = true) ||
                    it.name.contains("İdman", ignoreCase = true) ||
                    it.name.contains("Idman", ignoreCase = true)
                }
            }
            "football" -> {
                val all = fetchMackeyfiMatches(domains.mackeyfi)
                all.filter {
                    !it.name.contains("Basket", ignoreCase = true) &&
                    !it.name.contains("Voleybol", ignoreCase = true) &&
                    !it.name.contains("Tenis", ignoreCase = true)
                }
            }
            else -> {
                val mackeyfi = fetchMackeyfiMatches(domains.mackeyfi)
                val trgoals = fetchTrgoalsMatches(domains.trgoals)
                val combined = mutableListOf<SearchResponse>()
                combined.addAll(mackeyfi)
                for (tm in trgoals) {
                    if (combined.none { it.name.equals(tm.name, ignoreCase = true) }) {
                        combined.add(tm)
                    }
                }
                combined
            }
        }

        return newHomePageResponse(
            request.name,
            list,
            hasNext = false
        )
    }

    private suspend fun fetchMackeyfiMatches(mackeyfiDomain: String): List<SearchResponse> {
        val list = mutableListOf<SearchResponse>()
        try {
            val html = app.get(
                "$mackeyfiDomain/",
                headers = mapOf("User-Agent" to userAgent)
            ).text

            val doc = Jsoup.parse(html)
            val links = doc.select("a[href*=/mac/]")

            for (link in links) {
                val title = link.attr("title").ifEmpty { link.text() }.trim()
                val href = link.attr("href")
                val time = link.select("time").text().trim()

                if (title.isNotEmpty() && href.isNotEmpty()) {
                    val fullUrl = if (href.startsWith("http")) href else "$mackeyfiDomain$href"

                    val payload = MatchDataPayload(
                        title = title,
                        sport = "Futbol",
                        time = time,
                        poster = defaultPoster,
                        sources = listOf(
                            MatchSource(
                                name = "Maçkeyfi HD",
                                provider = "mackeyfi_page",
                                sourceId = fullUrl
                            )
                        )
                    )
                    val jsonPayload = jsonMapper.writeValueAsString(payload)
                    val encoded = Base64.encodeToString(jsonPayload.toByteArray(), Base64.NO_WRAP)
                    val displayName = if (time.isNotEmpty()) "[$time] $title" else title

                    list.add(
                        newLiveSearchResponse(displayName, "payload:$encoded", TvType.Live) {
                            this.posterUrl = defaultPoster
                        }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private suspend fun fetchTrgoalsMatches(trgoalsDomain: String): List<SearchResponse> {
        val list = mutableListOf<SearchResponse>()
        try {
            val html = app.get(
                "$trgoalsDomain/",
                headers = mapOf("User-Agent" to userAgent)
            ).text

            val doc = Jsoup.parse(html)
            val items = doc.select("ul.match-list li")

            for (item in items) {
                val title = item.attr("data-title")
                    .replace(Regex("""\s*\|\s*TRGoals.*"""), "")
                    .replace(Regex("""\s*Canlı İzle.*""", RegexOption.IGNORE_CASE), "")
                    .trim()
                val sourceJson = item.attr("data-source")
                val homeLogo = item.attr("data-home_logo")
                val awayLogo = item.attr("data-away_logo")
                val poster = homeLogo.ifEmpty { awayLogo }.ifEmpty { defaultPoster }

                if (title.isNotEmpty() && sourceJson.isNotEmpty() && sourceJson != "[]") {
                    val sources = mutableListOf<MatchSource>()
                    try {
                        val tree = jsonMapper.readTree(sourceJson)
                        if (tree.isArray) {
                            for ((idx, node) in tree.withIndex()) {
                                val ad = node.get("ad")?.asText() ?: "Yayım ${idx + 1}"
                                val tip = node.get("tip")?.asText() ?: "vip"
                                val link = node.get("link")?.asText() ?: ""
                                if (link.isNotEmpty()) {
                                    sources.add(
                                        MatchSource(
                                            name = "TRGoals $ad",
                                            provider = if (tip == "vip" || tip == "viptv") "trgoals" else "direct",
                                            sourceId = link
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    if (sources.isNotEmpty()) {
                        val payload = MatchDataPayload(
                            title = title,
                            sport = item.attr("data-category").ifEmpty { "Futbol" },
                            poster = poster,
                            sources = sources
                        )
                        val jsonPayload = jsonMapper.writeValueAsString(payload)
                        val encoded = Base64.encodeToString(jsonPayload.toByteArray(), Base64.NO_WRAP)

                        list.add(
                            newLiveSearchResponse(title, "payload:$encoded", TvType.Live) {
                                this.posterUrl = poster
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val domains = resolveDomains()
        val results = mutableListOf<SearchResponse>()

        // 1. Search in TV Channels
        for (ch in tvChannels) {
            if (ch.name.contains(query, ignoreCase = true)) {
                val payload = MatchDataPayload(
                    title = ch.name,
                    sport = "Canlı TV",
                    poster = ch.logo.ifEmpty { defaultPoster },
                    sources = buildList {
                        if (ch.trgoalsId.isNotEmpty()) add(MatchSource("TRGoals FHD", "trgoals", ch.trgoalsId))
                        if (ch.biatId.isNotEmpty()) add(MatchSource("BiatSports HD", "biatsports", ch.biatId))
                        if (ch.directUrl.isNotEmpty()) add(MatchSource("Rəsmi M3U8", "direct", ch.directUrl))
                    }
                )
                val jsonPayload = jsonMapper.writeValueAsString(payload)
                val encoded = Base64.encodeToString(jsonPayload.toByteArray(), Base64.NO_WRAP)

                results.add(
                    newLiveSearchResponse(ch.name, "payload:$encoded", TvType.Live) {
                        this.posterUrl = ch.logo.ifEmpty { defaultPoster }
                    }
                )
            }
        }

        // 2. Search in Maçkeyfi matches
        val matches = fetchMackeyfiMatches(domains.mackeyfi)
        for (m in matches) {
            if (m.name.contains(query, ignoreCase = true)) {
                results.add(m)
            }
        }

        return results
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse? {
        val domains = resolveDomains()

        if (url.startsWith("payload:")) {
            val base64 = url.removePrefix("payload:")
            val json = String(Base64.decode(base64, Base64.DEFAULT))
            val payload = jsonMapper.readValue<MatchDataPayload>(json)

            val episodes = mutableListOf<Episode>()
            var epIdx = 1

            for (src in payload.sources) {
                if (src.provider == "mackeyfi_page") {
                    try {
                        val pageHtml = app.get(src.sourceId, headers = mapOf("User-Agent" to userAgent)).text
                        val idMatch = Regex("""match-center\.php\?id=(\d+)""").find(pageHtml)
                        val matchId = idMatch?.groupValues?.get(1)

                        if (!matchId.isNullOrEmpty()) {
                            val ep1 = epIdx++
                            episodes.add(
                                newEpisode("aga:${domains.mackeyfi}:$matchId") {
                                    this.name = "Mənbə 1: Maçkeyfi (720p HD Master)"
                                    this.episode = ep1
                                }
                            )
                            val ep2 = epIdx++
                            episodes.add(
                                newEpisode("aga:${domains.izlemac}:$matchId") {
                                    this.name = "Mənbə 2: İzlemaç Ehtiyat (720p HD)"
                                    this.episode = ep2
                                }
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    val currEp = epIdx++
                    episodes.add(
                        newEpisode("${src.provider}:${src.sourceId}") {
                            this.name = "Yayım $currEp: ${src.name}"
                            this.episode = currEp
                        }
                    )
                }
            }

            if (episodes.isEmpty()) {
                episodes.add(
                    newEpisode("trgoals:100001") {
                        this.name = "Yayım: beIN Sports 1 HD"
                        this.episode = 1
                    }
                )
            }

            return newTvSeriesLoadResponse(
                name = payload.title,
                url = url,
                type = TvType.Live,
                episodes = episodes
            ) {
                this.posterUrl = payload.poster.ifEmpty { defaultPoster }
                this.plot = "${payload.title} canlı yayımı. Başlama vaxtı: ${payload.time.ifEmpty { "İndi Canlı" }}"
            }
        }

        return null
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val domains = resolveDomains()

        try {
            val parts = data.split(":", limit = 3)
            val provider = parts.getOrNull(0) ?: ""

            when (provider) {
                "trgoals" -> {
                    val sourceId = parts.getOrNull(1) ?: ""
                    val links = Extractors.extractTrgoals(domains.trgoals, sourceId)
                    links.forEach(callback)
                    return links.isNotEmpty()
                }
                "aga" -> {
                    val domain = parts.getOrNull(1) ?: domains.mackeyfi
                    val matchId = parts.getOrNull(2) ?: ""
                    val links = Extractors.extractAga(domain, matchId)
                    links.forEach(callback)
                    return links.isNotEmpty()
                }
                "taraftarium" -> {
                    val slug = parts.getOrNull(1) ?: ""
                    val links = Extractors.extractTaraftarium(domains.taraftarium, slug)
                    links.forEach(callback)
                    return links.isNotEmpty()
                }
                "biatsports" -> {
                    val channelId = parts.getOrNull(1) ?: ""
                    val links = Extractors.extractBiatSports(domains.biatsports, channelId)
                    links.forEach(callback)
                    return links.isNotEmpty()
                }
                "direct" -> {
                    val streamUrl = data.removePrefix("direct:")
                    callback(
                        ExtractorLink(
                            source = "Rəsmi Canlı Yayım",
                            name = "Rəsmi Canlı Yayım (HD)",
                            url = streamUrl,
                            referer = "",
                            quality = Qualities.P1080.value,
                            type = ExtractorLinkType.M3U8
                        )
                    )
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }
}
