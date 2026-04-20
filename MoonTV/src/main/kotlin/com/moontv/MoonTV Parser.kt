package com.moontv

data class ApiResponse(
    val status: String,
    val result: ResultData
)

data class ResultData(
    val title: TitleData,
    val seasons: List<SeasonData>?
)

data class TitleData(
    val id: String,
    val type: String, // "movie" or "tv"
    val title: String,
    val release_year: Int?,
    val poster: ImageData,
    val backdrop: ImageData,
    val duration: String?,
    val quality: String?,
    val rating: String?,
    val season_latest: Int?,
    val episode_latest: Int?,
    val trailer_id: String?,
    val imdb_value: Any?,
    val uri: String,
    val episode_uri: String,
    val synopsis: String?,
    val genres: List<Genre>,
    val countries: List<Country>
)

data class ImageData(
    val original: String?,
    val small: String?,
    val medium: String?
)

data class Genre(
    val title: String,
    val slug: String,
    val uri: String
)

data class Country(
    val title: String,
    val slug: String,
    val uri: String
)

data class SeasonData(
    val id: Int,
    val number: Int,
    val name: String,
    val air_date: String?,
    val episodes: List<EpisodeData>
)

data class EpisodeData(
    val number: Int,
    val slug: String,
    val id: String,
    val detail_name: String,
    val detail_released_at: String?,
    val uri: String
)

//Links
data class LinksResponse(
    val status: String?,
    val result: LinksResult?
)

data class LinksResult(
    val id: String?,
    val number: Int?,
    val slug: String?,
    val links: List<ServerLink>?
)

data class ServerLink(
    val server_id: Int?,
    val id: String?,
    val name: String?
)

data class LinkDetailResponse(
    val status: String?,
    val result: String?
)