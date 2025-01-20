package com.TorraStream

import com.fasterxml.jackson.annotation.JsonProperty

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

