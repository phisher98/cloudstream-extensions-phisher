package com.HindiProviders

import com.fasterxml.jackson.annotation.JsonProperty

data class Root(
        val series: Series,
        val name: String,
        val episodes: Episodes,
        val featured: String,
)

data class Series(
        @JsonProperty("ID") val id: Long,
        @JsonProperty("post_author") val postAuthor: String,
        @JsonProperty("post_date") val postDate: String,
        @JsonProperty("post_date_gmt") val postDateGmt: String,
        @JsonProperty("post_content") val postContent: String,
        @JsonProperty("post_title") val postTitle: String,
        @JsonProperty("post_excerpt") val postExcerpt: String,
        @JsonProperty("post_status") val postStatus: String,
        @JsonProperty("comment_status") val commentStatus: String,
        @JsonProperty("ping_status") val pingStatus: String,
        @JsonProperty("post_password") val postPassword: String,
        @JsonProperty("post_name") val postName: String,
        @JsonProperty("to_ping") val toPing: String,
        val pinged: String,
        @JsonProperty("post_modified") val postModified: String,
        @JsonProperty("post_modified_gmt") val postModifiedGmt: String,
        @JsonProperty("post_content_filtered") val postContentFiltered: String,
        @JsonProperty("post_parent") val postParent: Long,
        val guid: String,
        @JsonProperty("menu_order") val menuOrder: Long,
        @JsonProperty("post_type") val postType: String,
        @JsonProperty("post_mime_type") val postMimeType: String,
        @JsonProperty("comment_count") val commentCount: String,
        val filter: String,
)

data class Episodes(
        val all: List<All> = emptyList(),
        val tamil: List<Tamil> = emptyList(),
        val hindi: List<Hindi> = emptyList(),
        val japanese: List<Japanese> = emptyList(),
        val malayalam: List<Malayalam> = emptyList(),
        val english: List<English> = emptyList()
)

data class All(
        val title: String,
        val metadata: Metadata,
        val id: Long,
        val url: String,
        val published: String,
        val image: String,
)

data class Metadata(
        val number: String,
        val title: String,
        val duration: String,
        val released: String,
        @JsonProperty("parent_id") val parentId: String,
        @JsonProperty("parent_name") val parentName: String,
        @JsonProperty("parent_slug") val parentSlug: String,
        val download: List<Any?>,
        val thumbnail: String,
        @JsonProperty("anime_type") val animeType: String,
        @JsonProperty("anime_season") val animeSeason: String,
        @JsonProperty("anime_id") val animeId: String,
)

data class Tamil(
        val title: String,
        val metadata: Metadata2,
        val id: Long,
        val url: String,
        val published: String,
        val image: String,
)

data class Metadata2(
        val number: String,
        val title: String,
        val duration: String,
        val released: String,
        @JsonProperty("parent_id") val parentId: String,
        @JsonProperty("parent_name") val parentName: String,
        @JsonProperty("parent_slug") val parentSlug: String,
        val download: List<Any?>,
        val thumbnail: String,
        @JsonProperty("anime_type") val animeType: String,
        @JsonProperty("anime_season") val animeSeason: String,
        @JsonProperty("anime_id") val animeId: String,
)

data class Hindi(
        val title: String,
        val metadata: Metadata3,
        val id: Long,
        val url: String,
        val published: String,
        val image: String,
)

data class Metadata3(
        val number: String,
        val title: String,
        val duration: String,
        val released: String,
        @JsonProperty("parent_id") val parentId: String,
        @JsonProperty("parent_name") val parentName: String,
        @JsonProperty("parent_slug") val parentSlug: String,
        val download: List<Any?>,
        val thumbnail: String,
        @JsonProperty("anime_type") val animeType: String,
        @JsonProperty("anime_season") val animeSeason: String,
        @JsonProperty("anime_id") val animeId: String,
)

data class Japanese(
        val title: String,
        val metadata: Metadata4,
        val id: Long,
        val url: String,
        val published: String,
        val image: String,
)

data class Metadata4(
        val number: String,
        val title: String,
        val duration: String,
        val released: String,
        @JsonProperty("parent_id") val parentId: String,
        @JsonProperty("parent_name") val parentName: String,
        @JsonProperty("parent_slug") val parentSlug: String,
        val download: List<Any?>,
        val thumbnail: String,
        @JsonProperty("anime_type") val animeType: String,
        @JsonProperty("anime_season") val animeSeason: String,
        @JsonProperty("anime_id") val animeId: String,
)

data class Malayalam(
        val title: String,
        val metadata: Metadata5,
        val id: Long,
        val url: String,
        val published: String,
        val image: String,
)

data class Metadata5(
        val number: String,
        val title: String,
        val duration: String,
        val released: String,
        @JsonProperty("parent_id") val parentId: String,
        @JsonProperty("parent_name") val parentName: String,
        @JsonProperty("parent_slug") val parentSlug: String,
        val download: List<Any?>,
        val thumbnail: String,
        @JsonProperty("anime_type") val animeType: String,
        @JsonProperty("anime_season") val animeSeason: String,
        @JsonProperty("anime_id") val animeId: String,
)

data class English(
        val title: String,
        val metadata: Metadata6,
        val id: Long,
        val url: String,
        val published: String,
        val image: String,
)

data class Metadata6(
        val number: String,
        val title: String,
        val duration: String,
        val released: String,
        @JsonProperty("parent_id") val parentId: String,
        @JsonProperty("parent_name") val parentName: String,
        @JsonProperty("parent_slug") val parentSlug: String,
        val download: List<Any?>,
        val thumbnail: String,
        @JsonProperty("anime_type") val animeType: String,
        @JsonProperty("anime_season") val animeSeason: String,
        @JsonProperty("anime_id") val animeId: String,
)

// Episode Parser

data class Episodedata(
        val players: List<Player>,
)

data class Player(
        val type: String,
        val language: String,
        val quality: String,
        val server: String,
        val url: String,
        val key: String,
)
