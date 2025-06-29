package com.animeNexus

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName

data class AnimeNexusHome(
    val data: List<Daum> = emptyList(),
    val links: Links? = null,
    val meta: Meta? = null
)

data class Daum(
    val id: String = "",
    val slug: String = "",
    val name: String = "",
    @SerializedName("name_alt")
    val nameAlt: String? = null,
    val description: String? = null,
    val poster: Poster? = null,
    val genres: List<Genre> = emptyList(),
    val views: Long = 0L
)

data class Poster(
    val original: String? = null,
    val medium: String? = null,
    val small: String? = null,
    val tiny: String? = null
)

data class Genre(
    val name: String = "",
    val id: String = "",
    val code: String = ""
)

data class Links(
    val first: Any? = null,
    val last: Any? = null,
    val prev: Any? = null,
    val next: String? = null
)

data class Meta(
    val path: String = "",
    @SerializedName("per_page")
    val perPage: Long = 0L,
    @SerializedName("next_cursor")
    val nextCursor: String? = null,
    @SerializedName("prev_cursor")
    val prevCursor: Any? = null
)

data class AnimeNexusLoad(
    val data: List<LoadDaum> = emptyList(),
    val links: LoadLinks? = null,
    val meta: LoadMeta? = null
)

data class LoadDaum(
    val id: String = "",
    val title: String? = "",
    val slug: String = "",
    val number: Long? = null,
    val duration: Long? = null,
    val image: LoadImage? = null,

    @SerializedName("video_meta")
    val videoMeta: LoadVideoMeta? = null,

    @SerializedName("is_filler")
    val isFiller: Long? = null,

    @SerializedName("is_recap")
    val isRecap: Long? = null
)

data class LoadImage(
    val medium: String? = null,
    val small: String? = null,
    val original: String? = null
)

data class LoadVideoMeta(
    @SerializedName("subtitle_languages")
    val subtitleLanguages: List<String>? = emptyList(),

    @SerializedName("audio_languages")
    val audioLanguages: List<String>? = emptyList(),

    val status: String? = null
)

data class LoadLinks(
    val first: String? = null,
    val last: String? = null,
    val prev: String? = null,
    val next: String? = null
)

data class LoadMeta(
    @SerializedName("current_page")
    val currentPage: Long? = null,

    val from: Long? = null,

    @SerializedName("last_page")
    val lastPage: Long? = null,

    @SerializedName("per_page")
    val perPage: Long? = null,

    val to: Long? = null,
    val total: Long? = null
)




data class Stream(
    val data: Data,
)

data class Data(
    val next: Next,
    val subtitles: List<Subtitle>,
    @JsonProperty("video_meta")
    val videoMeta: VideoMeta,
    val hls: String,
    val mpd: String,
    val thumbnails: String,
)

data class Next(
    val id: String,
    val title: Any?,
    val number: Long,
    val slug: String,
    val image: Image,
)

data class Image(
    val original: String,
)

data class Subtitle(
    val id: String,
    val src: String,
    val label: String,
    val srcLang: String,
)

data class VideoMeta(
    val duration: Long,
    val chapters: String,
    @JsonProperty("audio_languages")
    val audioLanguages: List<String>,
    val status: String,
    val qualities: Qualities,
    @JsonProperty("file_size_streams")
    val fileSizeStreams: FileSizeStreams,
)

data class Qualities(
    @JsonProperty("1920x1080")
    val n1920x1080: Long,
    @JsonProperty("1280x720")
    val n1280x720: Long,
    @JsonProperty("848x480")
    val n848x480: Long,
)

data class FileSizeStreams(
    @JsonProperty("848x480")
    val n848x480: Long,
    @JsonProperty("1280x720")
    val n1280x720: Long,
    @JsonProperty("1920x1080")
    val n1920x1080: Long,
)
