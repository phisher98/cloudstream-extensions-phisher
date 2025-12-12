package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName


//TorBox

data class TorBoxDebian(
    val streams: List<TorBoxDebianStream>,
)

data class TorBoxDebianStream(
    val name: String,
    val description: String,
    val behaviorHints: TorBoxDebianBehaviorHints,
    val url: String,
)

data class TorBoxDebianBehaviorHints(
    val notWebReady: Boolean,
    val videoSize: Long,
    val filename: String,
    val bingeGroup: String,
    val videoHash: String?,
)


data class MediafusionResponse(
    val streams: List<MediafusionStream>,
)

data class MediafusionStream(
    val name: String,
    val description: String,
    val infoHash: String,
    val fileIdx: Long?,
    val behaviorHints: MediafusionBehaviorHints,
    val sources: List<String>,
)

data class MediafusionBehaviorHints(
    val bingeGroup: String,
    val filename: String,
    val videoSize: Long,
)

data class TBPResponse(
    val streams: List<TBPStream>,
    val cacheMaxAge: Long,
    val staleRevalidate: Long,
    val staleError: Long,
)

data class TBPStream(
    val name: String,
    val title: String,
    val infoHash: String,
    val tag: String,
)

data class PeerflixResponse(
    val streams: List<PeerflixStream>,
)

data class PeerflixStream(
    val name: String,
    val description: String,
    val infoHash: String,
    val sources: List<String>,
    val fileIdx: Long?,
    val language: String,
    val quality: String,
    val seed: Long,
    val sizebytes: Long?,
)

data class SubtitlesAPI(
    val subtitles: List<Subtitle1>,
    val cacheMaxAge: Long,
)

data class Subtitle1(
    val id: String,
    val url: String,
    @JsonProperty("SubEncoding")
    val subEncoding: String,
    val lang: String,
    val m: String,
    val g: String,
)

data class AnimetoshoItem(
    val id: Long,
    val title: String,
    val link: String,
    val timestamp: Long,
    val status: String,
    @SerializedName("tosho_id")
    val toshoId: Long?,
    @SerializedName("nyaa_id")
    val nyaaId: Long,
    @SerializedName("nyaa_subdom")
    val nyaaSubdom: Any?,
    @SerializedName("anidex_id")
    val anidexId: Any?,
    @SerializedName("torrent_url")
    val torrentUrl: String,
    @SerializedName("torrent_name")
    val torrentName: String,
    @SerializedName("info_hash")
    val infoHash: String,
    @SerializedName("info_hash_v2")
    val infoHashV2: Any?,
    @SerializedName("magnet_uri")
    val magnetUri: String,
    val seeders: Long,
    val leechers: Long,
    @SerializedName("torrent_downloaded_count")
    val torrentDownloadedCount: Long,
    @SerializedName("tracker_updated")
    val trackerUpdated: Long?,
    @SerializedName("nzb_url")
    val nzbUrl: String,
    @SerializedName("total_size")
    val totalSize: Long,
    @SerializedName("num_files")
    val numFiles: Long,
    @SerializedName("anidb_aid")
    val anidbAid: Long,
    @SerializedName("anidb_eid")
    val anidbEid: Long,
    @SerializedName("anidb_fid")
    val anidbFid: Long?,
    @SerializedName("article_url")
    val articleUrl: Any?,
    @SerializedName("article_title")
    val articleTitle: Any?,
    @SerializedName("website_url")
    val websiteUrl: String?
)
data class AIO(
    val streams: List<AIOStream>,
)

data class AIOStream(
    val url: String,
    val name: String,
    val description: String,
    val behaviorHints: AIOBehaviorHints,
)

data class AIOBehaviorHints(
    val videoSize: Long,
    val filename: String,
    val bingeGroup: String,
)

data class MagnetStream(
    val title: String,
    val quality: String,
    val magnet: String
)



data class AIODebian(
    val streams: List<AIODebianStream>,
)

data class AIODebianStream(
    val name: String,
    val description: String,
    val url: String,
    val behaviorHints: AIODebianBehaviorHints,
    val streamData: AIODebianStreamData,
)

data class AIODebianBehaviorHints(
    val videoSize: Long,
    val filename: String,
)

data class AIODebianStreamData(
    val type: String,
    val proxied: Boolean,
    val indexer: String,
    val duration: Long,
    val library: Boolean,
    val size: Long,
    val torrent: AIODebianTorrent,
    val addon: String,
    val filename: String,
    val service: Service,
    val parsedFile: ParsedFile,
    val id: String,
    val folderName: String?,
)

data class AIODebianTorrent(
    val infoHash: String,
    val seeders: Long,
)

data class Service(
    val id: String,
    val cached: Boolean,
)

data class ParsedFile(
    val title: String,
    val year: String,
    val resolution: String,
    val quality: String,
    val encode: String?,
    val releaseGroup: String?,
    val seasonEpisode: List<Any?>,
    val visualTags: List<String>,
    val audioTags: List<String>,
    val audioChannels: List<String>,
    val languages: List<String>,
)
