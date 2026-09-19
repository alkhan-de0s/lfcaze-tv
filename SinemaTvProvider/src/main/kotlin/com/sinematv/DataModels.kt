package com.sinematv

import com.fasterxml.jackson.annotation.JsonProperty

data class EpisodeDto(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("originalTitle") val originalTitle: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("m3u8MasterFilePath") val m3u8MasterFilePath: String? = null,
    @JsonProperty("order") val order: Int? = null,
    @JsonProperty("season") val season: SeasonDto? = null,
    @JsonProperty("episodeVariants") val episodeVariants: List<EpisodeVariantDto>? = emptyList()
)

data class SeasonDto(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("order") val order: Int? = null
)

data class EpisodeVariantDto(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("episodeId") val episodeId: Long? = null,
    @JsonProperty("duration") val duration: Int? = null,
    @JsonProperty("filepath") val filepath: String? = null,
    @JsonProperty("previewImageFilepath") val previewImageFilepath: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("streamQuality") val streamQuality: String? = null,
    @JsonProperty("hasAdv") val hasAdv: Boolean? = null
)

data class ContentDetailsDto(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("originalTitle") val originalTitle: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("posterUrl") val posterUrl: String? = null,
    @JsonProperty("hasMultipleEpisodes") val hasMultipleEpisodes: Boolean? = null
)

data class EpisodeDataPayload(
    @JsonProperty("movieId") val movieId: String,
    @JsonProperty("episodeId") val episodeId: Long? = null,
    @JsonProperty("fallbackUrl") val fallbackUrl: String? = null
)
