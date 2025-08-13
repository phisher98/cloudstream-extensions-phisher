package com.PublicSportsIPTV

import com.fasterxml.jackson.annotation.JsonProperty

data class Root(
    @JsonProperty("Author")
    val author: String,
    val name: String,
    @JsonProperty("last_updated")
    val lastUpdated: String,
    val headers: Headers,
    @JsonProperty("total_matches")
    val totalMatches: Long,
    @JsonProperty("live_matches")
    val liveMatches: Long,
    @JsonProperty("upcoming_matches")
    val upcomingMatches: Long,
    val matches: List<Match>,
)

data class Headers(
    @JsonProperty("User-Agent")
    val userAgent: String,
    @JsonProperty("Referer")
    val referer: String,
)

data class Match(
    val category: String,
    val title: String,
    val tournament: String,
    @JsonProperty("match_id")
    val matchId: Long,
    val status: String,
    val streamingStatus: String,
    val startTime: String,
    val startDate: String,
    val image: String,
    @JsonProperty("image_cdn")
    val imageCdn: ImageCdn,
    val teams: List<Team>,
    val language: String,
    @JsonProperty("adfree_stream")
    val adfreeStream: String?,
    @JsonProperty("dai_stream")
    val daiStream: String?,
    @JsonProperty("STREAMING_CDN")
    val streamingCdn: StreamingCdn,
)

data class ImageCdn(
    @JsonProperty("TATAPLAY")
    val tataplay: String,
    @JsonProperty("APP")
    val app: String,
    @JsonProperty("PLAYBACK")
    val playback: String?,
    @JsonProperty("LOGO")
    val logo: String,
    @JsonProperty("SPORTS")
    val sports: String,
    @JsonProperty("BG_IMAGE")
    val bgImage: String,
    @JsonProperty("SPORT_BY_IMAGE")
    val sportByImage: String,
    @JsonProperty("CLOUDFARE")
    val cloudfare: String,
)

data class Team(
    val name: String,
    val shortName: String,
    val flag: Flag,
    val isWinner: Boolean?,
    val color: String,
    val cricketScore: List<CricketScore>?,
    val kabaddiScore: Any?,
    val footballScore: Any?,
    val basketBallScore: Any?,
    val hockeyScore: Any?,
    val status: Status?,
)

data class Flag(
    val src: String,
)

data class CricketScore(
    val runs: Long,
    val overs: String,
    val balls: String,
    val status: String,
    val wickets: Long,
)

data class Status(
    val cricket: Cricket,
)

data class Cricket(
    val isBatting: Boolean,
)

data class StreamingCdn(
    @JsonProperty("Primary_Playback_URL")
    val primaryPlaybackUrl: String?,
    @JsonProperty("fancode_cdn")
    val fancodeCdn: String?,
    @JsonProperty("dai_google_cdn")
    val daiGoogleCdn: String?,
    @JsonProperty("cloudfront_cdn")
    val cloudfrontCdn: String?,
    val language: String,
)

data class LoadURL(
    @JsonProperty("Primary_Playback_URL")
    val primaryPlaybackUrl: String?,
    @JsonProperty("fancode_cdn")
    val fancodeCdn: String?,
    @JsonProperty("dai_google_cdn")
    val daiGoogleCdn: String?,
    @JsonProperty("cloudfront_cdn")
    val cloudfrontCdn: String?,
)