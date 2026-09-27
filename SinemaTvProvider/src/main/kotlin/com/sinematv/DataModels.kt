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
    @JsonProperty("movieId") val movieId: String? = null,
    @JsonProperty("episodeId") val episodeId: Long? = null,
    @JsonProperty("fallbackUrl") val fallbackUrl: String? = null
)

data class ParsedJsonDto(
    @JsonProperty("sources") val sources: List<ParsedSourceDto>? = null
)

data class ParsedSourceDto(
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("links") val links: List<ParsedLinkDto>? = null
)

data class ParsedLinkDto(
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("src") val src: String? = null
)

data class Cdn1DatasDto(
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("md5_id") val md5Id: Long? = null,
    @JsonProperty("user_id") val userId: Long? = null,
    @JsonProperty("media") val media: String? = null
)

data class AbyssMediaDto(
    @JsonProperty("mp4") val mp4: AbyssMp4Dto? = null
)

data class AbyssMp4Dto(
    @JsonProperty("sources") val sources: List<AbyssSourceDto>? = null,
    @JsonProperty("domains") val domains: List<String>? = null
)

data class AbyssSourceDto(
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("res_id") val resId: Int? = null,
    @JsonProperty("size") val size: Long? = null,
    @JsonProperty("codec") val codec: String? = null,
    @JsonProperty("status") val status: Boolean? = null,
    @JsonProperty("sub") val sub: String? = null
)
