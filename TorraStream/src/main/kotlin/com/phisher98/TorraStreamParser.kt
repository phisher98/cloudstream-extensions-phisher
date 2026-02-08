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


data class LoadData(
    val title: String? = null,
    val year: Int? =null,
    val isAnime: Boolean = false,
    val imdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

data class Data(
    val id: Int? = null,
    val type: String? = null,
    val aniId: String? = null,
    val malId: Int? = null,
)

data class Results(
    @param:JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
)

data class Media(
    @param:JsonProperty("id") val id: Int? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("original_title") val originalTitle: String? = null,
    @param:JsonProperty("media_type") val mediaType: String? = null,
    @param:JsonProperty("poster_path") val posterPath: String? = null,
    @param:JsonProperty("vote_average") val voteAverage: Double? = null,
)

data class Genres(
    @get:JsonProperty("id") val id: Int? = null,
    @get:JsonProperty("name") val name: String? = null,
)

data class Keywords(
    @get:JsonProperty("id") val id: Int? = null,
    @get:JsonProperty("name") val name: String? = null,
)

data class KeywordResults(
    @get:JsonProperty("results") val results: ArrayList<Keywords>? = arrayListOf(),
    @get:JsonProperty("keywords") val keywords: ArrayList<Keywords>? = arrayListOf(),
)

data class Seasons(
    @get:JsonProperty("id") val id: Int? = null,
    @get:JsonProperty("name") val name: String? = null,
    @get:JsonProperty("season_number") val seasonNumber: Int? = null,
    @get:JsonProperty("air_date") val airDate: String? = null,
)

data class Cast(
    @get:JsonProperty("id") val id: Int? = null,
    @get:JsonProperty("name") val name: String? = null,
    @get:JsonProperty("original_name") val originalName: String? = null,
    @get:JsonProperty("character") val character: String? = null,
    @get:JsonProperty("known_for_department") val knownForDepartment: String? = null,
    @get:JsonProperty("profile_path") val profilePath: String? = null,
)

data class Episodes(
    @get:JsonProperty("id") val id: Int? = null,
    @get:JsonProperty("name") val name: String? = null,
    @get:JsonProperty("overview") val overview: String? = null,
    @get:JsonProperty("air_date") val airDate: String? = null,
    @get:JsonProperty("still_path") val stillPath: String? = null,
    @get:JsonProperty("vote_average") val voteAverage: Double? = null,
    @get:JsonProperty("episode_number") val episodeNumber: Int? = null,
    @get:JsonProperty("season_number") val seasonNumber: Int? = null,
    @get:JsonProperty("runtime") val runTime: Int? = null
)

data class MediaDetailEpisodes(
    @get:JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
)

data class Trailers(
    @get:JsonProperty("key") val key: String? = null,
    @get:JsonProperty("type") val type: String? = null,
)

data class ResultsTrailer(
    @get:JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
)

data class AltTitles(
    @get:JsonProperty("iso_3166_1") val iso_3166_1: String? = null,
    @get:JsonProperty("title") val title: String? = null,
    @get:JsonProperty("type") val type: String? = null,
)

data class ResultsAltTitles(
    @get:JsonProperty("results") val results: ArrayList<AltTitles>? = arrayListOf(),
)

data class ExternalIds(
    @get:JsonProperty("imdb_id") val imdb_id: String? = null,
    @get:JsonProperty("tvdb_id") val tvdb_id: Int? = null,
)

data class Credits(
    @get:JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
)

data class ResultsRecommendations(
    @get:JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
)

data class LastEpisodeToAir(
    @get:JsonProperty("episode_number") val episode_number: Int? = null,
    @get:JsonProperty("season_number") val season_number: Int? = null,
)

data class ProductionCountries(
    @get:JsonProperty("name") val name: String? = null,
)

data class MediaDetail(
    @get:JsonProperty("id") val id: Int? = null,
    @get:JsonProperty("imdb_id") val imdbId: String? = null,
    @get:JsonProperty("title") val title: String? = null,
    @get:JsonProperty("name") val name: String? = null,
    @get:JsonProperty("original_title") val originalTitle: String? = null,
    @get:JsonProperty("original_name") val originalName: String? = null,
    @get:JsonProperty("poster_path") val posterPath: String? = null,
    @get:JsonProperty("backdrop_path") val backdropPath: String? = null,
    @get:JsonProperty("release_date") val releaseDate: String? = null,
    @get:JsonProperty("first_air_date") val firstAirDate: String? = null,
    @get:JsonProperty("overview") val overview: String? = null,
    @get:JsonProperty("runtime") val runtime: Int? = null,
    @get:JsonProperty("vote_average") val vote_average: Any? = null,
    @get:JsonProperty("original_language") val original_language: String? = null,
    @get:JsonProperty("status") val status: String? = null,
    @get:JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
    @get:JsonProperty("keywords") val keywords: KeywordResults? = null,
    @get:JsonProperty("last_episode_to_air") val last_episode_to_air: LastEpisodeToAir? = null,
    @get:JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
    @get:JsonProperty("videos") val videos: ResultsTrailer? = null,
    @get:JsonProperty("external_ids") val external_ids: ExternalIds? = null,
    @get:JsonProperty("credits") val credits: Credits? = null,
    @get:JsonProperty("recommendations") val recommendations: ResultsRecommendations? = null,
    @get:JsonProperty("alternative_titles") val alternative_titles: ResultsAltTitles? = null,
    @get:JsonProperty("production_countries") val production_countries: ArrayList<ProductionCountries>? = arrayListOf(),
)

data class TmdbDate(
    val today: String,
    val nextWeek: String,
    val lastWeekStart: String,
    val monthStart: String
)

data class CinemetaRes(
    val meta: Meta? = null
) {

    data class Meta(
        val id: String? = null,
        val type: String? = null,
        val name: String? = null,

        @JsonProperty("imdb_id")
        val imdbId: String? = null,

        val slug: String? = null,

        val director: String? = null,
        val writer: String? = null,

        val description: String? = null,
        val year: String? = null,
        val releaseInfo: String? = null,
        val released: String? = null,
        val runtime: String? = null,
        val status: String? = null,
        val country: String? = null,
        val imdbRating: String? = null,
        val genres: List<String>? = null,
        val poster: String? = null,
        @JsonProperty("_rawPosterUrl")
        val rawPosterUrl: String? = null,

        val background: String? = null,
        val logo: String? = null,

        val videos: List<Video>? = null,
        val trailers: List<Trailer>? = null,
        val trailerStreams: List<TrailerStream>? = null,
        val links: List<Link>? = null,

        val behaviorHints: BehaviorHints? = null,

        @JsonProperty("app_extras")
        val appExtras: AppExtras? = null,
    ) {

        data class BehaviorHints(
            val defaultVideoId: Any? = null,
            val hasScheduledVideos: Boolean? = null
        )

        data class Link(
            val name: String? = null,
            val category: String? = null,
            val url: String? = null
        )

        data class Trailer(
            val source: String? = null,
            val type: String? = null,
            val name: String? = null
        )

        data class TrailerStream(
            val ytId: String? = null,
            val title: String? = null
        )

        data class Video(
            val id: String? = null,
            val title: String? = null,
            val season: Int? = null,
            val episode: Int? = null,
            val thumbnail: String? = null,
            val overview: String? = null,
            val released: String? = null,
            val available: Boolean? = null,
            val runtime: String? = null
        )

        data class AppExtras(
            val cast: List<Cast>? = null,
            val directors: List<Any?>? = null,
            val writers: List<Any?>? = null,
            val seasonPosters: List<String?>? = null,
            val certification: String? = null
        )

        data class Cast(
            val name: String? = null,
            val character: String? = null,
            val photo: String? = null
        )
    }
}

data class LinkData(
    val id: Int? = null,
    val imdbId: String? = null,
    val tvdbId: Int? = null,
    val type: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val epid: Int? = null,
    val aniId: String? = null,
    val animeId: String? = null,
    val title: String? = null,
    val year: Int? = null,
    val orgTitle: String? = null,
    val isAnime: Boolean = false,
    val airedYear: Int? = null,
    val lastSeason: Int? = null,
    val epsTitle: String? = null,
    val jpTitle: String? = null,
    val date: String? = null,
    val airedDate: String? = null,
    val isAsian: Boolean = false,
    val isBollywood: Boolean = false,
    val isCartoon: Boolean = false,
    val alttitle: String? = null,
    val nametitle: String? = null,
    val isDub: Boolean = false,
    val isMovie: Boolean? = false,
)