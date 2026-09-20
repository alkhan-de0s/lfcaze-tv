package com.daddylive

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Document
import java.net.URI

data class ScheduleChannel(
    @JsonProperty("n") val name: String = "",
    @JsonProperty("id") val id: String = ""
)

data class ScheduleEvent(
    @JsonProperty("t") val title: String = "",
    @JsonProperty("tm") val time: String = "",
    @JsonProperty("c") val category: String = "",
    @JsonProperty("s") val sport: String = "",
    @JsonProperty("ch") val channels: List<ScheduleChannel> = emptyList()
)

class DaddyLiveProvider : MainAPI() {
    override var mainUrl = "https://dlive.sx"
    override var name = "DaddyLive"
    override val supportedTypes = setOf(TvType.Live, TvType.TvSeries)
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = true

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

    private val defaultPoster = "https://dlive.sx/assets/logos/logo.png"

    private val jsonMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // In-memory cache for 24/7 channels
    private var cachedChannels: List<LiveSearchResponse> = emptyList()
    private var lastCacheTime: Long = 0L
    private val cacheDurationMs = 15 * 60 * 1000L // 15 minutes

    // In-memory cache for scheduled sports events
    private var cachedScheduleBySport: Map<String, List<LiveSearchResponse>> = emptyMap()
    private var cachedAllEvents: List<ScheduleEvent> = emptyList()
    private var lastScheduleFetchTime: Long = 0L
    private var lastScheduleDayOfYear: Int = -1
    private val scheduleCacheDurationMs = 10 * 60 * 1000L // 10 minutes

    private fun getCurrentGmtCalendar(): java.util.Calendar {
        return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT"))
    }

    private fun isTargetDay(dayTitle: String, cal: java.util.Calendar): Boolean {
        val dayOfWeekNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        val targetDayOfWeek = dayOfWeekNames[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
        val targetDayNum = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val targetMonth = monthNames[cal.get(java.util.Calendar.MONTH)]

        val lower = dayTitle.lowercase()
        val hasDayName = lower.contains(targetDayOfWeek.lowercase())
        val hasMonth = lower.contains(targetMonth.lowercase())
        val hasDayNum = lower.contains("$targetDayNum ") ||
                lower.contains("${targetDayNum}th") ||
                lower.contains("${targetDayNum}st") ||
                lower.contains("${targetDayNum}nd") ||
                lower.contains("${targetDayNum}rd")

        return (hasDayName && hasMonth) || (hasMonth && hasDayNum)
    }

    override val mainPage = mainPageOf(
        "24-7-channels.php" to "📺 Bütün Kanallar (24/7)",
        "soccer" to "⚽ Futbol (Canlı & Bu gün)",
        "basketball" to "🏀 Basketbol",
        "tennis" to "🎾 Tennis",
        "combat" to "🥊 Döyüş (UFC, MMA, Boks)",
        "motorsport" to "🏎️ Motorsport (F1, Moto)",
        "hockey" to "🏒 Xokkey (NHL)",
        "football_rugby" to "🏈 Amerikan Futbolu & Reqbi",
        "baseball" to "⚾ Beyzbol (MLB)",
        "other" to "🏆 Digər İdman Növləri"
    )

    private fun categorizeSport(catName: String, eventTitle: String): String {
        val text = "$catName $eventTitle".lowercase()
        return when {
            text.contains("⚽") || text.contains("soccer") || text.contains("premiership") ||
            (text.contains("league") && !text.contains("rugby") && !text.contains("hockey")) ||
            (text.contains("championship") && !text.contains("usl") && !text.contains("rugby")) ||
            text.contains("fifa") || text.contains("uefa") || text.contains("mls") ||
            text.contains("taça de portugal") -> "soccer"

            text.contains("🏀") || text.contains("basketball") || text.contains("nba") || text.contains("wnba") -> "basketball"

            text.contains("🎾") || text.contains("tennis") || text.contains("atp") || text.contains("wta") || text.contains("davis cup") -> "tennis"

            text.contains("🥊") || text.contains("ufc") || text.contains("mma") || text.contains("boxing") || text.contains("wrestling") || text.contains("aew") -> "combat"

            text.contains("🏎") || text.contains("🏁") || text.contains("motorsport") || text.contains("f1") || text.contains("moto") || text.contains("rally") -> "motorsport"

            text.contains("🏒") || text.contains("hockey") || text.contains("nhl") || text.contains("khl") || text.contains("ohl") || text.contains("ushl") -> "hockey"

            text.contains("🏈") || text.contains("cfl") || text.contains("nfl") || text.contains("college football") ||
            text.contains("🏉") || text.contains("rugby") || text.contains("afl") -> "football_rugby"

            text.contains("⚾") || text.contains("baseball") || text.contains("mlb") -> "baseball"

            else -> "other"
        }
    }

    private fun getPosterForSport(sport: String): String {
        return when (sport) {
            "soccer" -> "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=600&auto=format&fit=crop&q=80"
            "basketball" -> "https://images.unsplash.com/photo-1546519638-68e109498ffc?w=600&auto=format&fit=crop&q=80"
            "tennis" -> "https://images.unsplash.com/photo-1595435934249-5df7ed86e1c0?w=600&auto=format&fit=crop&q=80"
            "combat" -> "https://images.unsplash.com/photo-1517438322307-e67111335449?w=600&auto=format&fit=crop&q=80"
            "motorsport" -> "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=600&auto=format&fit=crop&q=80"
            "hockey" -> "https://images.unsplash.com/photo-1580748141549-71748dbe0bdc?w=600&auto=format&fit=crop&q=80"
            "football_rugby" -> "https://images.unsplash.com/photo-1566577739112-5180d4bf9390?w=600&auto=format&fit=crop&q=80"
            "baseball" -> "https://images.unsplash.com/photo-1508344928928-7165b67de128?w=600&auto=format&fit=crop&q=80"
            else -> defaultPoster
        }
    }

    private fun encodeEventUrl(event: ScheduleEvent): String {
        return try {
            val json = jsonMapper.writeValueAsString(event)
            val b64 = Base64.encodeToString(
                json.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP
            )
            "$mainUrl/event.php?data=$b64"
        } catch (e: Throwable) {
            "$mainUrl/"
        }
    }

    private fun decodeEventUrl(url: String): ScheduleEvent? {
        val b64 = url.substringAfter("data=", "").substringBefore("&").takeIf { it.isNotBlank() } ?: return null
        return try {
            val json = String(
                Base64.decode(b64, Base64.URL_SAFE or Base64.NO_WRAP),
                Charsets.UTF_8
            )
            jsonMapper.readValue(json, ScheduleEvent::class.java)
        } catch (e: Throwable) {
            null
        }
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

    private suspend fun fetchSchedule(): Map<String, List<LiveSearchResponse>> {
        val now = System.currentTimeMillis()
        val cal = getCurrentGmtCalendar()
        val currentDayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR)

        if (cachedScheduleBySport.isNotEmpty() &&
            currentDayOfYear == lastScheduleDayOfYear &&
            (now - lastScheduleFetchTime) < scheduleCacheDurationMs
        ) {
            return cachedScheduleBySport
        }

        return try {
            val doc = app.get(
                "$mainUrl/",
                headers = mapOf(
                    "User-Agent" to userAgent,
                    "Cache-Control" to "no-cache, no-store, must-revalidate",
                    "Pragma" to "no-cache"
                ),
                cacheTime = 0
            ).document

            val eventsList = mutableListOf<ScheduleEvent>()
            val bySport = mutableMapOf<String, MutableList<LiveSearchResponse>>()

            val dayElements = doc.select("div.schedule__day")
            val targetDays: List<Pair<org.jsoup.nodes.Element, Boolean>> = if (dayElements.isNotEmpty()) {
                val todayElem = dayElements.firstOrNull {
                    isTargetDay(it.selectFirst(".schedule__dayTitle")?.text() ?: "", cal)
                }

                val tomorrowCal = getCurrentGmtCalendar().apply { add(java.util.Calendar.DAY_OF_MONTH, 1) }
                val tomorrowElem = dayElements.firstOrNull {
                    isTargetDay(it.selectFirst(".schedule__dayTitle")?.text() ?: "", tomorrowCal)
                }

                if (todayElem != null) {
                    val list = mutableListOf<Pair<org.jsoup.nodes.Element, Boolean>>()
                    list.add(todayElem to true)
                    if (tomorrowElem != null) {
                        list.add(tomorrowElem to false)
                    }
                    list
                } else {
                    val yesterdayCal = getCurrentGmtCalendar().apply { add(java.util.Calendar.DAY_OF_MONTH, -1) }
                    val nonYesterday = dayElements.filterNot {
                        isTargetDay(it.selectFirst(".schedule__dayTitle")?.text() ?: "", yesterdayCal)
                    }
                    (nonYesterday.ifEmpty { dayElements }).map { it to false }
                }
            } else {
                emptyList()
            }

            val categoryScopes = if (targetDays.isNotEmpty()) {
                targetDays.flatMap { (dayElem, isToday) ->
                    dayElem.select("div.schedule__category").map { catElem ->
                        catElem to isToday
                    }
                }
            } else {
                doc.select("div.schedule__category").map { it to true }
            }

            for ((catElem, isToday) in categoryScopes) {
                val catName = catElem.selectFirst(".schedule__catHeader .card__meta, .card__meta")?.text()?.trim() ?: ""
                val eventElements = catElem.select(".schedule__event")
                for (evElem in eventElements) {
                    val time = evElem.selectFirst(".schedule__time")?.text()?.trim() ?: ""
                    val rawTitle = evElem.selectFirst(".schedule__eventTitle")?.text()?.trim() ?: ""
                    if (rawTitle.isBlank()) continue

                    val channelLinks = evElem.select(".schedule__channels a[href*='watch.php?id=']").mapNotNull { a ->
                        val chName = a.text().trim()
                        val chHref = a.attr("href").trim()
                        val idMatch = """(?:id=)(\d+)""".toRegex().find(chHref)
                        val chId = idMatch?.groupValues?.get(1) ?: ""
                        if (chName.isNotBlank() && chId.isNotBlank()) {
                            ScheduleChannel(name = chName, id = chId)
                        } else null
                    }
                    if (channelLinks.isEmpty()) continue

                    val sport = categorizeSport(catName, rawTitle)
                    val event = ScheduleEvent(
                        title = rawTitle,
                        time = time,
                        category = catName,
                        sport = sport,
                        channels = channelLinks
                    )
                    eventsList.add(event)

                    val timePrefix = if (time.isNotBlank()) {
                        if (isToday) "[$time]" else "[Sabah $time]"
                    } else ""
                    val displayName = if (timePrefix.isNotBlank()) "$timePrefix $rawTitle" else rawTitle
                    val eventUrl = encodeEventUrl(event)
                    val poster = getPosterForSport(sport)

                    val searchResponse = newLiveSearchResponse(displayName, eventUrl, TvType.Live) {
                        this.posterUrl = poster
                    }

                    bySport.getOrPut(sport) { mutableListOf() }.add(searchResponse)
                }
            }

            if (eventsList.isNotEmpty()) {
                cachedAllEvents = eventsList
                cachedScheduleBySport = bySport
                lastScheduleFetchTime = now
                lastScheduleDayOfYear = currentDayOfYear
            }
            bySport
        } catch (e: Throwable) {
            e.printStackTrace()
            cachedScheduleBySport
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val list = if (request.data == "24-7-channels.php") {
            fetchAll247Channels()
        } else {
            val scheduleMap = fetchSchedule()
            scheduleMap[request.data] ?: emptyList()
        }

        return newHomePageResponse(
            request.name,
            list,
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()

        val tokens = q.split("""\s+""".toRegex()).filter { it.isNotBlank() }
        fun norm(s: String) = s.lowercase().replace("""[^a-z0-9]""".toRegex(), "")
        val normQ = norm(q)

        // 1. Search 24/7 channels
        val channels = fetchAll247Channels()
        val channelMatches = channels.filter { ch ->
            val nameLower = ch.name.lowercase()
            val normName = norm(nameLower)
            nameLower.contains(q) || normName.contains(normQ) || tokens.all { t -> nameLower.contains(t) || normName.contains(norm(t)) }
        }

        // 2. Search live schedule events
        fetchSchedule() // ensure cachedAllEvents is populated
        val eventMatches = cachedAllEvents.filter { event ->
            val nameLower = event.title.lowercase()
            val normName = norm(nameLower)
            val catLower = event.category.lowercase()
            nameLower.contains(q) || normName.contains(normQ) || catLower.contains(q) ||
                    tokens.all { t -> nameLower.contains(t) || normName.contains(norm(t)) }
        }.map { event ->
            val displayName = if (event.time.isNotBlank()) "[${event.time}] ${event.title}" else event.title
            val eventUrl = encodeEventUrl(event)
            newLiveSearchResponse(displayName, eventUrl, TvType.Live) {
                this.posterUrl = getPosterForSport(event.sport)
            }
        }

        return (channelMatches + eventMatches).distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val cleanUrl = fixUrl(url)

        // 1. Scheduled Live Event with multiple channels
        if (cleanUrl.contains("event.php?data=")) {
            val event = decodeEventUrl(cleanUrl)
            if (event != null && event.channels.isNotEmpty()) {
                val sportPoster = getPosterForSport(event.sport)
                val eventTitle = if (event.time.isNotBlank()) "[${event.time}] ${event.title}" else event.title

                return newTvSeriesLoadResponse(
                    name = eventTitle,
                    url = cleanUrl,
                    type = TvType.TvSeries,
                    episodes = event.channels.mapIndexed { index, ch ->
                        val chWatchUrl = "$mainUrl/watch.php?id=${ch.id}"
                        newEpisode(chWatchUrl) {
                            this.name = "${index + 1}. ${ch.name}"
                            this.episode = index + 1
                            this.season = 1
                            this.posterUrl = sportPoster
                        }
                    }
                ) {
                    this.posterUrl = sportPoster
                    this.plot = "⏰ Başlama vaxtı: ${event.time}\n🏆 Kateqoriya: ${event.category}\n\n📺 Mövcud yayım kanalları:\n" +
                            event.channels.joinToString("\n") { "• " + it.name }
                }
            }
        }

        // 2. 24/7 Channel
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
        val playerServers = listOf(
            "stream" to "Player 1 (Əsas)",
            "cast" to "Player 2 (Cast)",
            "watch" to "Player 3 (Watch)",
            "plus" to "Player 4 (Plus)",
            "casting" to "Player 5 (Casting)",
            "player" to "Player 6 (Player)"
        )

        var foundAny = false

        for ((folder, playerLabel) in playerServers) {
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
                if (frameSrc.isBlank() || frameSrc.contains("about:blank")) continue

                val embedUrl = when {
                    frameSrc.startsWith("//") -> "https:$frameSrc"
                    frameSrc.startsWith("http") -> frameSrc
                    else -> "$mainUrl/$frameSrc"
                }

                val embedUri = URI(embedUrl)
                val embedHost = "${embedUri.scheme}://${embedUri.host}"

                val embedHtml = app.get(
                    embedUrl,
                    headers = mapOf(
                        "Referer" to streamPageUrl,
                        "User-Agent" to userAgent
                    )
                ).text

                val econfigRegex = """window\._econfig\s*=\s*'([^']+)'""".toRegex()
                val econfig = econfigRegex.find(embedHtml)?.groupValues?.get(1) ?: continue

                val decodedJson = DaddyLiveDecoder.decodeEConfig(econfig) ?: continue
                val streamUrl = DaddyLiveDecoder.extractStreamUrl(decodedJson) ?: continue

                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "DaddyLive - $playerLabel",
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
                foundAny = true
            } catch (err: Throwable) {
                continue
            }
        }

        return foundAny
    }
}
