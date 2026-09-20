package com.turkligasi

import com.fasterxml.jackson.annotation.JsonProperty

data class DomainsConfig(
    @JsonProperty("taraftarium") val taraftarium: String = "https://taraftarium60.com",
    @JsonProperty("trgoals") val trgoals: String = "https://www.trgoals183.top",
    @JsonProperty("mackeyfi") val mackeyfi: String = "https://www.mackeyfi549.sbs",
    @JsonProperty("izlemac") val izlemac: String = "https://izlemac529.sbs",
    @JsonProperty("biatsports") val biatsports: String = "https://biatsports42.com"
)

data class MatchSource(
    @JsonProperty("name") val name: String,
    @JsonProperty("provider") val provider: String,
    @JsonProperty("sourceId") val sourceId: String,
    @JsonProperty("url") val url: String = "",
    @JsonProperty("extra") val extra: String = ""
)

data class MatchDataPayload(
    @JsonProperty("title") val title: String,
    @JsonProperty("sport") val sport: String = "Futbol",
    @JsonProperty("time") val time: String = "",
    @JsonProperty("poster") val poster: String = "",
    @JsonProperty("sources") val sources: List<MatchSource> = emptyList()
)

data class TrGoalsAuthResponse(
    @JsonProperty("URL") val url: String? = null,
    @JsonProperty("TOKEN") val token: String? = null,
    @JsonProperty("SERVER") val server: Int? = null,
    @JsonProperty("ERROR") val error: String? = null
)

data class BiatDomainResponse(
    @JsonProperty("baseurl") val baseurl: String? = null
)
