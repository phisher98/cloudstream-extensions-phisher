package com.phisher98

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName

data class Torrentmovie(
    val results: List<Result>,
    val query: String,
    val status: String,
    val seo: List<Seo>,
)

data class Result(
    val id: Long,
    val name: String,
    val type: String,
    @JsonProperty("release_date")
    val releaseDate: String,
    val year: Long,
    val description: String,
    val genre: Any?,
    val tagline: String,
    val poster: String,
    val backdrop: String,
    val runtime: Long,
    val trailer: Any?,
    val budget: Any?,
    val revenue: Any?,
    val views: Long,
    val popularity: Long,
    @JsonProperty("imdb_id")
    val imdbId: String,
    @JsonProperty("tmdb_id")
    val tmdbId: Long,
    @JsonProperty("season_count")
    val seasonCount: Long,
    @JsonProperty("fully_synced")
    val fullySynced: Boolean,
    @JsonProperty("allow_update")
    val allowUpdate: Boolean,
    @JsonProperty("created_at")
    val createdAt: String,
    @JsonProperty("updated_at")
    val updatedAt: String,
    val language: String,
    val country: Any?,
    @JsonProperty("original_title")
    val originalTitle: String,
    @JsonProperty("affiliate_link")
    val affiliateLink: Any?,
    val certification: Any?,
    @JsonProperty("episode_count")
    val episodeCount: Long,
    @JsonProperty("series_ended")
    val seriesEnded: Boolean,
    @JsonProperty("is_series")
    val isSeries: Boolean,
    @JsonProperty("show_videos")
    val showVideos: Boolean,
    val adult: Boolean,
    @JsonProperty("screen_resolution_720p")
    val screenResolution720p: String,
    @JsonProperty("screen_resolution_1080p")
    val screenResolution1080p: String,
    @JsonProperty("screen_resolution_2160p")
    val screenResolution2160p: String,
    @JsonProperty("screen_resolution_3D")
    val screenResolution3D: Any?,
    val rating: String,
    @JsonProperty("model_type")
    val modelType: String,
    @JsonProperty("vote_count")
    val voteCount: Long,
)

data class Seo(
    val property: String?,
    val content: String?,
    val nodeName: String,
    val name: String?,
    val rel: String?,
    val href: String?,
    @JsonProperty("_text")
    val text: String?,
)


//TorBox


data class TorBox(
    val streams: List<TorBoxStream>,
)

data class TorBoxStream(
    val name: String,
    val url: String,
    val magnet: String?,
    val nzb: String?,
    val seeders: Long?,
    val peers: Long?,
    val quality: String?,
    val resolution: String?,
    val language: String?,
    @JsonProperty("is_cached")
    val isCached: Boolean,
    val size: Long?,
    val hash: String,
    val adult: Boolean,
    val description: String,
    val type: String?,
    val behaviorHints: TorBoxBehaviorHints?,
)

data class TorBoxBehaviorHints(
    val notWebReady: Boolean,
    val videoSize: Long,
    val filename: String,
)


data class TorrentioResponse(val streams: List<TorrentioStream>)

data class TorrentioStream(
    val name: String?,
    val title: String?,
    val infoHash: String?,
    val fileIdx: Int?,
)

data class DebianRoot(
    val streams: List<Stream>,
    val cacheMaxAge: Long,
    val staleRevalidate: Long,
    val staleError: Long,
)

data class Stream(
    val name: String,
    val title: String,
    val url: String,
    val behaviorHints: BehaviorHints,
)

data class BehaviorHints(
    val bingeGroup: String,
    val filename: String?,
)

//Subtitles


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


data class AnidbEidEpisodeWrapper(
    @JsonProperty("episode") val episode: AnidbEidEpisode
)


data class AnidbEidEpisode(
    @JsonProperty("episodeNumber") val episodeNumber: Int?,
    @JsonProperty("anidbEid") val anidbEid: Int?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ImageData(
    @JsonProperty("coverType") val coverType: String?,
    @JsonProperty("url") val url: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpisodeData(
    @JsonProperty("episode") val episode: String?,
    @JsonProperty("airdate") val airdate: String?,
    @JsonProperty("airDate") val airDate: String?,
    @JsonProperty("airDateUtc") val airDateUtc: String?,
    @JsonProperty("length") val length: Int?,
    @JsonProperty("runtime") val runtime: Int?,
    @JsonProperty("image") val image: String?,
    @JsonProperty("title") val title: Map<String, String>?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("rating") val rating: String?,
    @JsonProperty("finaleType") val finaleType: String?
)


@JsonIgnoreProperties(ignoreUnknown = true)
data class AnimeData(
    @JsonProperty("titles") val titles: Map<String, String>? = null,
    @JsonProperty("images") val images: List<ImageData>? = null,
    @JsonProperty("episodes") val episodes: Map<String, EpisodeData>? = null,
)






