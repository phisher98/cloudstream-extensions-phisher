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

data class PersonalComments(
    val code: Int,
    val file: FileData?,
    val html2: String?,
    val id: String?
)

data class FileData(
    val fid: Long,
    val uid: Long,
    val file_size: String?,
    val path: String?,
    val file_name: String,
    val ext: String?,
    val add_time: String?,
    val file_create_time: Long?,
    val file_update_time: Long?,
    val parent_id: Long?,
    val update_time: String?,
    val last_open_time: String?,
    val is_dir: Int?,
    val epub: Int?,
    val is_music_list: Int?,
    val oss_fid: Long?,
    val faststart: Int?,
    val has_video_quality: Int?,
    val total_download: Int?,
    val status: Int?,
    val remark: String?,
    val old_hash: String?,
    val hash: String?,
    val hash_type: String?,
    val from_uid: Long?,
    val fid_org: Long?,
    val share_id: Long?,
    val invite_permission: Int?,
    val comment_table: String?,
    val is_delete: Int?,
    val thumb_small: String?,
    val thumb_small_width: Int?,
    val thumb_small_height: Int?,
    val thumb: String?,
    val thumb_width: Int?,
    val thumb_height: Int?,
    val thumb_big: String?,
    val thumb_big_width: Int?,
    val thumb_big_height: Int?,
    val is_custom_thumb: Int?,
    val fix_thumb: Int?,
    val ffmpeg_ing: Int?,
    val quality: String?,
    val runtime: Int?,
    val ffmpeg_info: String?,
    val attribute: String?,
    val data: String?,
    val ffmpeg_status: Int?,
    val allow_delete: Int?,
    val allow_download: Int?,
    val allow_comment: Int?,
    val hide_location: Int?,
    val hide_email: Int?,
    val allow_copy: Int?,
    val error_video: Int?,
    val third_data: String?,
    val photos: Int?,
    val is_album: Int?,
    val is_cloud_sync_dir: Int?,
    val ai_tags: String?,
    val maybe_tags: String?,
    val ai_tag_last_time: Long?,
    val user_tags: String?,
    val is_collect: Int?,
    val sub_fid: Long?,
    val read_only: Int?,
    val is_shared: Int?,
    val bind_imdb_id: String?,
    val top_is_shared: Int?,
    val type: String?,
    val update_time2: String?,
    val file_icon: String?,
    val param2: String?
)


//html

data class HTML(
    val code: Long,
    val html: String,
    @JsonProperty("path_html")
    val pathHtml: String,
    @JsonProperty("path_html2")
    val pathHtml2: String,
    @JsonProperty("file_name")
    val fileName: String,
    val starttime: Double,
    val starttime2: Double,
    val endtime: Double,
)
