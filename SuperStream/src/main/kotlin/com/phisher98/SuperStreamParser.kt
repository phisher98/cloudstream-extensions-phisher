package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty

//SuperStream

data class ER(
    @JsonProperty("code") val code: Int? = null,
    @JsonProperty("msg") val msg: String? = null,
    @JsonProperty("server_runtime") val serverRuntime: Double? = null,
    @JsonProperty("server_name") val serverName: String? = null,
    @JsonProperty("data") val data: DData? = null,
)

data class DData(
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("file_list") val fileList: List<FileList>? = null,
)

data class FileList(
    @JsonProperty("fid") val fid: Long? = null,
    @JsonProperty("file_name") val fileName: String? = null,
    @JsonProperty("oss_fid") val ossFid: Long? = null,
)

data class ExternalResponse(
    @JsonProperty("code") val code: Int? = null,
    @JsonProperty("msg") val msg: String? = null,
    @JsonProperty("server_runtime") val serverRuntime: Double? = null,
    @JsonProperty("server_name") val serverName: String? = null,
    @JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("file_list") val fileList: List<FileList>? = null,
    ) {
        data class FileList(
            @JsonProperty("fid") val fid: Long? = null,
            @JsonProperty("file_name") val fileName: String? = null,
            @JsonProperty("oss_fid") val ossFid: Long? = null,
        )
    }
}

data class ExternalSourcesWrapper(
    @JsonProperty("sources") val sources: List<ExternalSources>? = null
)

data class ExternalSources(
    @JsonProperty("source") val source: String? = null,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("size") val size: String? = null,
)


data class SubtitlesAPI(
    val subtitles: List<Subtitle>,
    val cacheMaxAge: Long,
)

data class Subtitle(
    val id: String,
    val url: String,
    @JsonProperty("SubEncoding")
    val subEncoding: String,
    val lang: String,
    val m: String,
    val g: String,
)

data class WyZIESUB(
    val id: String,
    val url: String,
    val flagUrl: String,
    val format: String,
    val display: String,
    val language: String,
    val media: String,
    val isHearingImpaired: Boolean,
)


//Catflix Juicey

data class CatflixJuicy(
    val action: String,
    val success: Boolean,
    val msg: String,
    val juice: String,
    val juicePost: String,
)

data class CatflixJuicydata(
    val action: String,
    val success: Boolean,
    val msg: String,
    val data: String,
    val juice: String,
)
