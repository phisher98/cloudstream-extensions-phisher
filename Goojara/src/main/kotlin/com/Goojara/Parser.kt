package com.Coflix

import com.fasterxml.jackson.annotation.JsonProperty


data class Response(
    val res: String,
    val results: List<Result>,
    val next: Boolean,
    val page: String,
    val total: Long,
    val genres: String,
    val years: String,
    val sort: String,
)

data class Result(
    val uuid: Long,
    val name: String,
    val ranking: Any?,
    val url: String,
    val path: String,
    val ts: String,
    val release: String,
    val director: String,
    val casts: String,
    val slug: String,
    val excerpt: String,
)


data class EpRes(
    @JsonProperty("post_id")
    val postId: String,
    val title: String,
    val episodes: List<Episode>,
)

data class Episode(
    val id: Long,
    val title: String,
    val number: String,
    val season: String,
    val links: String,
    val image: String,
)

typealias Search = List<Search2>

data class Search2(
    @JsonProperty("ID")
    val id: Long,
    val title: String,
    val excerpt: String,
    val url: String,
    @JsonProperty("post_type")
    val postType: String,
    val year: String,
    val rating: String,
    val image: String,
    val director: String,
    val cast: String?,
)
