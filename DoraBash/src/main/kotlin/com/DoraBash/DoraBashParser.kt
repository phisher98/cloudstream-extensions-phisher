package com.DoraBash

import com.fasterxml.jackson.annotation.JsonProperty

data class EpJson(
    val success: Boolean,
    val data: Data,
)

data class Data(
    val episodes: List<Episode>,
    @JsonProperty("max_episodes_page")
    val maxEpisodesPage: Long,
    val message: String,
)

data class Episode(
    val number: String,
    val thumbnail: String,
    val title: String,
    val duration: String,
    val released: String,
    @JsonProperty("tmdb_fetch_episode")
    val tmdbFetchEpisode: Long,
    val id: Long,
    val type: String,
    val url: String,
    @JsonProperty("post_title")
    val postTitle: String,
    @JsonProperty("meta_number")
    val metaNumber: String,
)