package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty

data class Season(val objectId: String, val name: String)

data class SeasonResult(val results: List<Season>)

data class EpisodeResult(
    val results: List<EpisodeDetails>
)

data class EpisodeDetails(
    val objectId: String,
    val name: String?,
    val seasonId: String?,
    val seasonNumber: String?,
    val episodeNumber: Int,
    val embedLinks: EmbedLinks?,
    val createdAt: String?,
    val updatedAt: String?
)

data class EmbedLinks(
    val streamruby: String?,
    val upnshare: String?,
    val streamwish: String?,
    val vihide: String?
)


data class Loadlinks(
    val streamruby: String,
    val upnshare: String,
    val streamwish: String,
    val vihide: String,
    val hdlink: String?,
    @JsonProperty("originalURL")
    val originalUrl: String?,
    val drive: String?,
    val goFile: String?,
    val hubCloudLink: String?
)