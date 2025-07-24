package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.TvType


data class LoadData(
    val name: String,
    val posterurl: String,
    val type: TvType,
    val id: String,
    val userid: String,
)

//JellyFin


data class Authparser(
    @JsonProperty("AccessToken")
    val accessToken: String,
    @JsonProperty("ServerId")
    val serverId: String,
    @JsonProperty("User")
    val user: User,
)

data class User(
    @JsonProperty("Name")
    val name: String,
    @JsonProperty("ServerId")
    val serverId: String,
    @JsonProperty("Id")
    val id: String,
)

//Homepage

data class Home(
    @JsonProperty("Items")
    val items: List<HomeItem>,
    @JsonProperty("TotalRecordCount")
    val totalRecordCount: Long,
    @JsonProperty("StartIndex")
    val startIndex: Long
)

data class HomeItem(
    @JsonProperty("Name")
    val name: String,
    @JsonProperty("Id")
    val id: String,
    @JsonProperty("IsFolder")
    val isFolder: Boolean = false,
    @JsonProperty("Type")
    val type: String? = null,
    @JsonProperty("ProductionYear")
    val productionYear: Int? = null,
    @JsonProperty("PremiereDate")
    val premiereDate: String? = null,
    @JsonProperty("ImageTags")
    val imageTags: Map<String, String>? = null,
)

//LoadURL

data class LoadURL(
    @JsonProperty("MediaSources")
    val mediaSources: List<MediaSource>,
    @JsonProperty("PlaySessionId")
    val playSessionId: String,
)

data class MediaSource(
    @JsonProperty("TranscodingUrl")
    val transcodingUrl: String? = null,
    @JsonProperty("Path")
    val path: String? = null,
    @JsonProperty("SupportsDirectPlay")
    val supportsDirectPlay: Boolean = false,
    @JsonProperty("Protocol")
    val protocol: String = "",
    @JsonProperty("SupportsTranscoding")
    val supportsTranscoding: Boolean = false,
    @JsonProperty("SupportsDirectStream")
    val supportsDirectStream: Boolean = false,
    @JsonProperty("Id")
    val id: String? = null,
    @JsonProperty("Container")
    val container: String? = null,
    @JsonProperty("DefaultAudioStreamIndex")
    val defaultAudioStreamIndex: Int? = null
)

data class SeriesInfo(
    @JsonProperty("Id") val id: String,
    @JsonProperty("ParentId") val parentId: String?,
    @JsonProperty("Name") val name: String,
    @JsonProperty("Overview") val overview: String?,
    @JsonProperty("ProductionYear") val productionYear: Int?,
    @JsonProperty("ImageTags") val imageTags: ImageTags?,
    @JsonProperty("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double?,
    @JsonProperty("People") val people: List<Person> = emptyList()
)

data class ImageTags(
    @JsonProperty("Primary")
    val primary: String?
)

data class Person(
    @JsonProperty("Name")
    val name: String,

    @JsonProperty("Role")
    val role: String,

    @JsonProperty("Type")
    val type: String
)

data class SeasonItem(
    @JsonProperty("Id") val id: String,
    @JsonProperty("Name") val name: String
)

data class SeasonResponse(
    @JsonProperty("Items") val items: List<SeasonItem> = emptyList()
)


//Episodes

data class EpisodeJson(
    @JsonProperty("Items")
    val items: List<EpisodeItem>? = emptyList()
)

data class EpisodeItem(
    @JsonProperty("Id")
    val id: String,
    @JsonProperty("Name")
    val name: String,
    @JsonProperty("IndexNumber")
    val indexNumber: Int? = null,
    @JsonProperty("SeasonName")
    val seasonName: String? = null,
    @JsonProperty("ImageTags")
    val imageTags: EpisodeImageTags? = null
)

data class EpisodeImageTags(
    @JsonProperty("Primary")
    val primary: String?
)

//Metadata
data class MovieMetadata(
    @JsonProperty("People")
    val people: List<MovieMetadataPerson> = emptyList(),

    @JsonProperty("Name")
    val name: String,

    @JsonProperty("OriginalTitle")
    val originalTitle: String,

    @JsonProperty("Overview")
    val overview: String,

    @JsonProperty("ProductionYear")
    val productionYear: Long,

    @JsonProperty("ProviderIds")
    val providerIds: ProviderIds,

    @JsonProperty("ExternalUrls")
    val externalUrls: List<ExternalUrl>,

    @JsonProperty("CommunityRating")
    val communityRating: Double,

    @JsonProperty("Genres")
    val genres: List<String>,

    @JsonProperty("Id")
    val id: String,

    @JsonProperty("ImageTags")
    val imageTags: MovieMetadataImageTags,

    @JsonProperty("RemoteTrailers")
    val remoteTrailers: List<RemoteTrailer>
)

data class ProviderIds(
    @JsonProperty("Tmdb")
    val tmdb: String? = null,

    @JsonProperty("Imdb")
    val imdb: String? = null,

    @JsonProperty("Tvdb")
    val tvdb: String? = null
)

data class ExternalUrl(
    @JsonProperty("Name")
    val name: String,

    @JsonProperty("Url")
    val url: String
)

data class MovieMetadataImageTags(
    @JsonProperty("Primary")
    val primary: String? = null
)

data class RemoteTrailer(
    @JsonProperty("Url")
    val url: String
)

data class MovieMetadataPerson(
    @JsonProperty("Name")
    val name: String,

    @JsonProperty("Id")
    val id: String,

    @JsonProperty("Role")
    val role: String,

    @JsonProperty("Type")
    val type: String,

    @JsonProperty("PrimaryImageTag")
    val primaryImageTag: String? = null,

    @JsonProperty("ImageBlurHashes")
    val imageBlurHashes: ImageBlurHashesWrapper? = null
)

data class ImageBlurHashesWrapper(
    @JsonProperty("Primary")
    val primary: Map<String, String> = emptyMap()
)

//Search

data class SearchResult(
    @JsonProperty("Items")
    val items: List<SearchItem>,

    @JsonProperty("TotalRecordCount")
    val totalRecordCount: Int,

    @JsonProperty("StartIndex")
    val startIndex: Int
)

data class SearchItem(
    @JsonProperty("Name")
    val name: String,

    @JsonProperty("Id")
    val id: String,

    @JsonProperty("ServerId")
    val serverId: String? = null,

    @JsonProperty("Type")
    val type: String? = null,

    @JsonProperty("ProductionYear")
    val productionYear: Int? = null,

    @JsonProperty("PremiereDate")
    val premiereDate: String? = null,

    @JsonProperty("ImageTags")
    val imageTags: SearchImageTags? = null,

    @JsonProperty("BackdropImageTags")
    val backdropImageTags: List<String>? = null,

    @JsonProperty("HasSubtitles")
    val hasSubtitles: Boolean? = null,

    @JsonProperty("RunTimeTicks")
    val runTimeTicks: Long? = null,

    @JsonProperty("MediaType")
    val mediaType: String? = null,

    @JsonProperty("IsFolder")
    val isFolder: Boolean = false,

    @JsonProperty("Container")
    val container: String? = null,

    @JsonProperty("CommunityRating")
    val communityRating: Double? = null,

    @JsonProperty("OfficialRating")
    val officialRating: String? = null,

    @JsonProperty("UserData")
    val userData: UserData? = null
)

data class SearchImageTags(
    @JsonProperty("Primary")
    val primary: String? = null,

    @JsonProperty("Logo")
    val logo: String? = null
)

data class UserData(
    @JsonProperty("PlaybackPositionTicks")
    val playbackPositionTicks: Long? = null,

    @JsonProperty("PlayCount")
    val playCount: Int? = null,

    @JsonProperty("IsFavorite")
    val isFavorite: Boolean? = null,

    @JsonProperty("Played")
    val played: Boolean? = null
)