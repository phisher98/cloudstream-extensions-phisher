package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

//Anichi


@Serializable
data class EncryptedResponse(
    val data: EncryptedData? = null
)

@Serializable
data class EncryptedData(
    val _m: String? = null,
    val tobeparsed: String? = null
)

data class AnichiRoot(
    val data: AnichiData,
)

data class AnichiData(
    val shows: AnichiShows,
)

data class AnichiShows(
    val pageInfo: PageInfo,
    val edges: List<Edge>,
)

data class PageInfo(
    val total: Long,
)

data class Edge(
    @param:JsonProperty("_id")
    val id: String,
    val name: String,
    val englishName: String,
    val nativeName: String,
)

//Anichi Ep Parser

data class AnichiEP(
    val data: AnichiEPData? = null,
    val episode: AnichiEpisode? = null,
)

data class AnichiEPData(
    val episode: AnichiEpisode? = null,
)

data class AnichiEpisode(
    val sourceUrls: List<SourceUrl> = emptyList(),
)

data class SourceUrl(
    val sourceUrl: String,
    val sourceName: String,
    val downloads: AnichiDownloads? = null,
)

data class AnichiDownloads(
    val sourceName: String? = null,
    val downloadUrl: String? = null,
)

data class AniIds(var id: Int? = null, var idMal: Int? = null)

data class TmdbDate(
    val today: String,
    val nextWeek: String,
    val lastWeekStart: String,
    val monthStart: String
)
data class AniMedia(
    @param:JsonProperty("id") var id: Int? = null,
    @param:JsonProperty("idMal") var idMal: Int? = null
)

data class AniPage(
    @param:JsonProperty("media") var media: ArrayList<AniMedia> = arrayListOf()
)

data class AniData(
    @param:JsonProperty("Page") var Page: AniPage? = null,
    @param:JsonProperty("media") var media: ArrayList<AniMedia>? = null
)

data class AniSearch(
    @param:JsonProperty("data") var data: AniData? = null
)


//WyZIESUBAPI

data class WYZIESubtitle(
    @JsonProperty("url")
    val url: String,
    @JsonProperty("display")
    val display: String? = null,
    @JsonProperty("language")
    val language: String? = null,
)

data class ResponseHash(
    @param:JsonProperty("embed_url") val embed_url: String,
    @param:JsonProperty("key") val key: String? = null,
    @param:JsonProperty("type") val type: String? = null,
)

data class KisskhSources(
    @param:JsonProperty("Video") val video: String?,
    @param:JsonProperty("ThirdParty") val thirdParty: String?,
)

data class KisskhSubtitle(
    @param:JsonProperty("src") val src: String?,
    @param:JsonProperty("label") val label: String?,
)

data class KisskhEpisodes(
    @param:JsonProperty("id") val id: Int?,
    @param:JsonProperty("number") val number: Int?,
)

data class KisskhDetail(
    @param:JsonProperty("episodes") val episodes: ArrayList<KisskhEpisodes>? = arrayListOf(),
)

data class KisskhResults(
    @param:JsonProperty("id") val id: Int?,
    @param:JsonProperty("title") val title: String?,
)

data class ZShowEmbed(
    @param:JsonProperty("m") val meta: String? = null,
)

data class WatchsomuchTorrents(
    @param:JsonProperty("id") val id: Int? = null,
    @param:JsonProperty("movieId") val movieId: Int? = null,
    @param:JsonProperty("season") val season: Int? = null,
    @param:JsonProperty("episode") val episode: Int? = null,
)

data class WatchsomuchMovies(
    @param:JsonProperty("torrents") val torrents: ArrayList<WatchsomuchTorrents>? = arrayListOf(),
)

data class WatchsomuchResponses(
    @param:JsonProperty("movie") val movie: WatchsomuchMovies? = null,
)

data class WatchsomuchSubtitles(
    @param:JsonProperty("url") val url: String? = null,
    @param:JsonProperty("label") val label: String? = null,
)

data class WatchsomuchSubResponses(
    @param:JsonProperty("subtitles") val subtitles: ArrayList<WatchsomuchSubtitles>? = arrayListOf(),
)

data class IndexMedia(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("driveId") val driveId: String? = null,
    @param:JsonProperty("mimeType") val mimeType: String? = null,
    @param:JsonProperty("size") val size: String? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("modifiedTime") val modifiedTime: String? = null,
)
data class JikanExternal(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("url") val url: String? = null,
)

data class JikanData(
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("external") val external: List<JikanExternal>? = null,
    @param:JsonProperty("season") val season: String? = null,
)

data class JikanResponse(
    @param:JsonProperty("data") val data: JikanData? = null,
)

//anime animepahe parser

data class animepahe(
    val total: Long,
    @param:JsonProperty("per_page")
    val perPage: Long,
    @param:JsonProperty("current_page")
    val currentPage: Long,
    @param:JsonProperty("last_page")
    val lastPage: Long,
    @param:JsonProperty("next_page_url")
    val nextPageUrl: Any?,
    @param:JsonProperty("prev_page_url")
    val prevPageUrl: Any?,
    val from: Long,
    val to: Long,
    val data: List<Daum>,
)

data class Daum(
    val id: Long,
    @param:JsonProperty("anime_id")
    val animeId: Long,
    val episode: Int,
    val episode2: Long,
    val edition: String,
    val title: String,
    val snapshot: String,
    val disc: String,
    val audio: String,
    val duration: String,
    val session: String,
    val filler: Long,
    @param:JsonProperty("created_at")
    val createdAt: String,
)


data class MALSyncSites(
    @param:JsonProperty("AniXL") val AniXL: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
    @param:JsonProperty("Zoro") val zoro: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
    @param:JsonProperty("9anime") val nineAnime: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
    @param:JsonProperty("animepahe") val animepahe: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
    @param:JsonProperty("KickAssAnime") val KickAssAnime: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
    @param:JsonProperty("AnimeKAI") val AnimeKAI: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
)
data class MALSyncResponses(
    @param:JsonProperty("Sites") val sites: MALSyncSites? = null,
)

data class AllMovielandEpisodeFolder(
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("file") val file: String? = null,
)

data class AllMovielandSeasonFolder(
    @param:JsonProperty("episode") val episode: String? = null,
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("folder") val folder: ArrayList<AllMovielandEpisodeFolder>? = arrayListOf(),
)

data class AllMovielandServer(
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("file") val file: String? = null,
    @param:JsonProperty("folder") val folder: ArrayList<AllMovielandSeasonFolder>? = arrayListOf(),
)

data class AllMovielandPlaylist(
    @param:JsonProperty("file") val file: String? = null,
    @param:JsonProperty("key") val key: String? = null,
    @param:JsonProperty("href") val href: String? = null,
)
data class RidoContentable(
    @param:JsonProperty("imdbId") var imdbId: String? = null,
    @param:JsonProperty("tmdbId") var tmdbId: Int? = null,
)

data class RidoItems(
    @param:JsonProperty("slug") var slug: String? = null,
    @param:JsonProperty("contentable") var contentable: RidoContentable? = null,
)
data class NepuSearch(
    @param:JsonProperty("data") val data: ArrayList<Data>? = arrayListOf(),
) {
    data class Data(
        @param:JsonProperty("url") val url: String? = null,
        @param:JsonProperty("name") val name: String? = null,
        @param:JsonProperty("type") val type: String? = null,
    )
}
data class SubtitlesAPI(
    val subtitles: List<Subtitle>,
    val cacheMaxAge: Long,
)
data class Subtitle(
    val id: String,
    val url: String,
    @param:JsonProperty("SubEncoding")
    val subEncoding: String,
    val lang: String,
    val m: String,
    val g: String,
)

data class RiveStreamSource(
    val data: List<String>
)

data class KisskhKey(
    val id: String,
    val version: String,
    val key: String,
)

//SuperStream


data class FebResponse(
    val success: Boolean?,
    val versions: List<Version>?
)

data class Version(
    val name: String?,
    val links: List<Link>?
)

data class Link(
    val url: String?,
    val quality: String?,
    val name: String?,
    val size: String?
)

data class ER(
    @param:JsonProperty("code") val code: Int? = null,
    @param:JsonProperty("msg") val msg: String? = null,
    @param:JsonProperty("server_runtime") val serverRuntime: Double? = null,
    @param:JsonProperty("server_name") val serverName: String? = null,
    @param:JsonProperty("data") val data: DData? = null,
)

data class DData(
    @param:JsonProperty("link") val link: String? = null,
    @param:JsonProperty("file_list") val fileList: List<FileList>? = null,
)

data class FileList(
    @param:JsonProperty("fid") val fid: Long? = null,
    @param:JsonProperty("file_name") val fileName: String? = null,
    @param:JsonProperty("oss_fid") val ossFid: Long? = null,
)

data class ExternalResponse(
    @param:JsonProperty("code") val code: Int? = null,
    @param:JsonProperty("msg") val msg: String? = null,
    @param:JsonProperty("server_runtime") val serverRuntime: Double? = null,
    @param:JsonProperty("server_name") val serverName: String? = null,
    @param:JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @param:JsonProperty("link") val link: String? = null,
        @param:JsonProperty("file_list") val fileList: List<FileList>? = null,
    ) {
        data class FileList(
            @param:JsonProperty("fid") val fid: Long? = null,
            @param:JsonProperty("file_name") val fileName: String? = null,
            @param:JsonProperty("oss_fid") val ossFid: Long? = null,
        )
    }
}

data class ExternalSourcesWrapper(
    @param:JsonProperty("sources") val sources: List<ExternalSources>? = null
)

data class ExternalSources(
    @param:JsonProperty("source") val source: String? = null,
    @param:JsonProperty("file") val file: String? = null,
    @param:JsonProperty("label") val label: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("size") val size: String? = null,
)

data class EpisoderesponseKAA(
    val slug: String,
    val title: String,
    val duration_ms: Long,
    val episode_number: Number,
    val episode_string: String,
    val thumbnail: ThumbnailKAA
)

data class ThumbnailKAA(
    val formats: List<String>,
    val sm: String,
    val aspectRatio: Double,
    val hq: String
)


data class ServersResKAA(
    val servers: List<ServerKAA>,

    )

data class ServerKAA(
    val name: String,
    val shortName: String,
    val src: String,
)


data class EncryptedKAA(
    val data: String,
)


data class m3u8KAA(
    val hls: String,
    val subtitles: List<SubtitleKAA>,
    val key: String,
)

data class SubtitleKAA(
    val language: String,
    val name: String,
    val src: String,
)

data class AnichiStream(
    @param:JsonProperty("format") val format: String? = null,
    @param:JsonProperty("audio_lang") val audio_lang: String? = null,
    @param:JsonProperty("hardsub_lang") val hardsub_lang: String? = null,
    @param:JsonProperty("url") val url: String? = null,
)

data class PortData(
    @param:JsonProperty("streams") val streams: ArrayList<AnichiStream>? = arrayListOf(),
)

data class AnichiSubtitles(
    @param:JsonProperty("lang") val lang: String?,
    @param:JsonProperty("label") val label: String?,
    @param:JsonProperty("src") val src: String?,
)

data class AnichiLinks(
    @param:JsonProperty("link") val link: String,
    @param:JsonProperty("hls") val hls: Boolean? = null,
    @param:JsonProperty("resolutionStr") val resolutionStr: String,
    @param:JsonProperty("src") val src: String? = null,
    @param:JsonProperty("headers") val headers: Headers? = null,
    @param:JsonProperty("portData") val portData: PortData? = null,
    @param:JsonProperty("subtitles") val subtitles: ArrayList<AnichiSubtitles>? = arrayListOf(),
)

data class Headers(
    @param:JsonProperty("Referer") val referer: String? = null,
    @param:JsonProperty("Origin") val origin: String? = null,
    @param:JsonProperty("user-agent") val userAgent: String? = null,
)


data class AnichiVideoApiResponse(@param:JsonProperty("links") val links: List<AnichiLinks>)

//Domains Parser

data class DomainsParser(
    val moviesdrive: String? = null,
    @param:JsonProperty("HDHUB4u")
    val hdhub4u: String? = null,
    @param:JsonProperty("4khdhub")
    val n4khdhub: String? = null,
    @param:JsonProperty("MultiMovies")
    val multiMovies: String? = null,
    val bollyflix: String? = null,
    @param:JsonProperty("UHDMovies")
    val uhdmovies: String? = null,
    val moviesmod: String? = null,
    val topMovies: String? = null,
    val hdmovie2: String? = null,
    val vegamovies: String? = null,
    val rogmovies: String? = null,
    val luxmovies: String? = null,
    val movierulzhd: String? = null,
    val extramovies: String? = null,
    val banglaplex: String? = null,
    val toonstream: String? = null,
    val telugumv: String? = null,
    val filmycab: String? = null,
    val tellyhd: String? = null,
    val filmyfiy: String? = null,
    val hindmoviez: String? = null,
    val tamilblasters: String? = null,
    val hubcloud: String? = null,
    val movienestbd: String? = null,
    val movies4u: String? = null,
    val cinevood: String? = null,
    val dudefilms: String? = null,
    val fibwatch: String? = null,
    val fibtoon: String? = null,
    val fibdrama: String? = null,
    val xprimehub: String? = null,
    val m4ufree: String? = null,
    val zinkmovies: String? = null,
    val cinefreak: String? = null,
    val pencurimoviesubmalay: String? = null,
)


// CinemetaRes

data class CinemetaRes(
    val meta: Meta? = null
) {

    data class Meta(
        val id: String? = null,
        val type: String? = null,
        val name: String? = null,

        @param:JsonProperty("imdb_id")
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
        @param:JsonProperty("_rawPosterUrl")
        val rawPosterUrl: String? = null,

        val background: String? = null,
        val logo: String? = null,

        val videos: List<Video>? = null,
        val trailers: List<Trailer>? = null,
        val trailerStreams: List<TrailerStream>? = null,
        val links: List<Link>? = null,

        val behaviorHints: BehaviorHints? = null,

        @param:JsonProperty("app_extras")
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

data class Watch32(
    val type: String,
    val link: String,
)

//Vidlink
data class VidlinkResponse(
    @SerializedName("stream") val stream: VidlinkStream
)

data class VidlinkStream(
    @SerializedName("playlist") val playlist: String
)

data class VidFastRes(
    val status: Long,
    val result: VidFastResult,
    val info: String,
)

data class VidFastResult(
    val servers: String,
    val stream: String,
    val token: String,
)


data class VidFastServers(
    val result: List<VidFastServersResult>,
)

data class VidFastServersResult(
    val name: String,
    val data: String,
)

data class VidFastServersStreamRoot(
    val status: Long,
    val result: VidFastServersStreamResult,
    val info: String?
)

data class VidFastServersStreamResult(
    val url: String?,
    val tracks: List<VidFastServersTrack>?,
    val noReferrer: Boolean?
)

data class VidFastServersTrack(
    val file: String?,
    val label: String?
)

class SearchData : ArrayList<SearchData.SearchDataItem>() {
    data class SearchDataItem(
        val audio_languages: String,
        val exact_match: Int,
        val id: Int,
        val path: String,
        val poster: String,
        val qualities: List<String>,
        val release_year: String,
        val title: String,
        val tmdb_id: Int,
        val type: String
    )
}

//Vegamovies

data class VegamoviesResponse(
    val found: Int?,
    val hits: List<VegamoviesHit>?
)

data class VegamoviesHit(
    val document: VegamoviesDocument?
)

data class VegamoviesDocument(
    val id: String?,
    val imdb_id: String?,
    val permalink: String?,
    val post_title: String?,
    val post_thumbnail: String?
)

//Dooflix

data class Dooflix(
    val id: Long,
    val links: List<DooflixLink>,
)

data class DooflixLink(
    val id: Long,
    @param:JsonProperty("movie_id")
    val movieId: Long,
    val host: String,
    val url: String,
    val quality: String,
    val size: String,
    val order: Long,
    @param:JsonProperty("created_at")
    val createdAt: String,
    @param:JsonProperty("updated_at")
    val updatedAt: String,
)

//Hexa

data class HexaEn(
    val status: Long,
    val result: HexResult,
)

data class HexResult(
    val token: String,
    val expires: String,
)

data class HexaResponse(
    val status: Int? = null,
    val result: HexaResult? = null
)

data class HexaResult(
    val sources: List<HexaSource>? = null,
    val skipTime: Any? = null
)

data class HexaSource(
    val server: String? = null,
    val url: String? = null
)

//MegaPlaybuzz HiAnime

data class HiAnimeSourcesResponse(
    val sources: HiAnimeSources?,
    val tracks: List<HiAnimeTrack>?,
    val t: Long?,
    val server: Long?,
)

data class HiAnimeSources(
    val file: String?,
)

data class HiAnimeTrack(
    val file: String?,
    val label: String?,
    val kind: String?,
    val default: Boolean?
)

//vaplayer

data class Vaplayer(
    @JsonProperty("data")
    val data: VaplayerData? = null,

    @JsonProperty("default_subs")
    val defaultSubs: List<VaplayerSub>? = null
)

data class VaplayerData(
    @JsonProperty("stream_urls")
    val streamUrls: List<String>? = null
)

data class VaplayerSub(
    @JsonProperty("lang")
    val lang: String? = null,

    @JsonProperty("code")
    val code: String? = null,

    @JsonProperty("url")
    val url: String? = null
)

//ReAnime

data class ReAnime(
    val success: Boolean,
    val servers: List<ReAnimeServer>,
)

data class ReAnimeServer(
    @param:JsonProperty($$"$id")
    val id: String,
    val serverName: String,
    val dataLink: String,
    val dataType: String,
    @param:JsonProperty("continue")
    val continue_field: Boolean,
    val softsub: Boolean,
)

data class ResolvedReAnime(
    val result: ResolvedReAnimeResult,
)

data class ResolvedReAnimeResult(
    val token: String,
    val state: ResolvedReAnimeState,
)

data class ResolvedReAnimeState(
    val token: String,
)

data class ReAnimeStream(
    val result: ReAnimeStreamResult,
)

data class ReAnimeStreamResult(
    val stream: String,
)

data class ZinkTokenResponse(
    val status: String? = null,
    val token: String? = null
)

data class ZinkLink(
    val name: String,
    val url: String,
    val title: String,
)

// Anikage

data class AnikageSearchResponse(
    val results: List<AnikageSearchResult>? = null
)

data class AnikageSearchResult(
    val id: Int? = null,
    val anilistId: Int? = null,
    val slug: String? = null
)

data class AnikageEpisodesResponse(
    val episodes: List<AnikageEpisode>? = null
)

data class AnikageEpisode(
    val number: Int? = null,
    val episode: Int? = null
)

data class AnikageServersResponse(
    val servers: List<AnikageServer>? = null
)

data class AnikageServer(
    val id: String? = null,
    val name: String? = null
)

data class AnikageSourcesResponse(
    val sources: List<AnikageSource>? = null,
    val subtitles: List<AnikageSubtitle>? = null,
    val embeds: List<AnikageEmbed>? = null
)

data class AnikageSource(
    val url: String? = null,
    val quality: String? = null,
    val isM3U8: Boolean? = null,
    val type: String? = null
)

data class AnikageSubtitle(
    val file: String? = null,
    val label: String? = null
)

data class AnikageEmbed(
    val url: String? = null
)