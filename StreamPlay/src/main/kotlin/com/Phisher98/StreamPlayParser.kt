package com.phisher98

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

//Anichi

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
    @JsonProperty("_id")
    val id: String,
    val name: String,
    val englishName: String,
    val nativeName: String,
)

//Anichi Ep Parser

data class AnichiEP(
    val data: AnichiEPData,
)

data class AnichiEPData(
    val episode: AnichiEpisode,
)

data class AnichiEpisode(
    val sourceUrls: List<SourceUrl>,
)

data class SourceUrl(
    val sourceUrl: String,
    val sourceName: String,
    val downloads: AnichiDownloads?,
)

data class AnichiDownloads(
    val sourceName: String,
    val downloadUrl: String,
)

data class AniIds(var id: Int? = null, var idMal: Int? = null)

data class TmdbDate(
    val today: String,
    val nextWeek: String,
    val lastWeekStart: String,
    val monthStart: String
)

data class MoflixResponse(
    @JsonProperty("title") val title: Episode? = null,
    @JsonProperty("episode") val episode: Episode? = null,
) {
    data class Episode(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("videos") val videos: ArrayList<Videos>? = arrayListOf(),
    ) {
        data class Videos(
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("category") val category: String? = null,
            @JsonProperty("src") val src: String? = null,
            @JsonProperty("quality") val quality: String? = null,
        )
    }
}

data class AniMedia(
    @JsonProperty("id") var id: Int? = null,
    @JsonProperty("idMal") var idMal: Int? = null
)

data class AniPage(
    @JsonProperty("media") var media: ArrayList<AniMedia> = arrayListOf()
)

data class AniData(
    @JsonProperty("Page") var Page: AniPage? = null,
    @JsonProperty("media") var media: ArrayList<AniMedia>? = null
)

data class AniSearch(
    @JsonProperty("data") var data: AniData? = null
)


//WyZIESUBAPI

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

data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String,
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("type") val type: String? = null,
)

data class KisskhSources(
    @JsonProperty("Video") val video: String?,
    @JsonProperty("ThirdParty") val thirdParty: String?,
)

data class KisskhSubtitle(
    @JsonProperty("src") val src: String?,
    @JsonProperty("label") val label: String?,
)

data class KisskhEpisodes(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("number") val number: Int?,
)

data class KisskhDetail(
    @JsonProperty("episodes") val episodes: ArrayList<KisskhEpisodes>? = arrayListOf(),
)

data class KisskhResults(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?,
)

data class ZShowEmbed(
    @JsonProperty("m") val meta: String? = null,
)

data class WatchsomuchTorrents(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("movieId") val movieId: Int? = null,
    @JsonProperty("season") val season: Int? = null,
    @JsonProperty("episode") val episode: Int? = null,
)

data class WatchsomuchMovies(
    @JsonProperty("torrents") val torrents: ArrayList<WatchsomuchTorrents>? = arrayListOf(),
)

data class WatchsomuchResponses(
    @JsonProperty("movie") val movie: WatchsomuchMovies? = null,
)

data class WatchsomuchSubtitles(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("label") val label: String? = null,
)

data class WatchsomuchSubResponses(
    @JsonProperty("subtitles") val subtitles: ArrayList<WatchsomuchSubtitles>? = arrayListOf(),
)

data class IndexMedia(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("driveId") val driveId: String? = null,
    @JsonProperty("mimeType") val mimeType: String? = null,
    @JsonProperty("size") val size: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("modifiedTime") val modifiedTime: String? = null,
)

data class IndexData(
    @JsonProperty("files") val files: ArrayList<IndexMedia>? = arrayListOf(),
)

data class IndexSearch(
    @JsonProperty("data") val data: IndexData? = null,
)

data class JikanExternal(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("url") val url: String? = null,
)

data class JikanData(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("external") val external: List<JikanExternal>? = null,
    @JsonProperty("season") val season: String? = null,
)

data class JikanResponse(
    @JsonProperty("data") val data: JikanData? = null,
)

//Hianime

data class EpisodeServers(
    val type: String,
    val link: String,
    val server: Long,
    val sources: List<Any?>,
    val tracks: List<Any?>,
    val htmlGuide: String,
)


//anime animepahe parser

data class animepahe(
    val total: Long,
    @JsonProperty("per_page")
    val perPage: Long,
    @JsonProperty("current_page")
    val currentPage: Long,
    @JsonProperty("last_page")
    val lastPage: Long,
    @JsonProperty("next_page_url")
    val nextPageUrl: Any?,
    @JsonProperty("prev_page_url")
    val prevPageUrl: Any?,
    val from: Long,
    val to: Long,
    val data: List<Daum>,
)

data class Daum(
    val id: Long,
    @JsonProperty("anime_id")
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
    @JsonProperty("created_at")
    val createdAt: String,
)


data class MALSyncSites(
    @JsonProperty("AniXL") val AniXL: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
    @JsonProperty("Zoro") val zoro: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
    @JsonProperty("9anime") val nineAnime: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
    @JsonProperty("animepahe") val animepahe: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
    @JsonProperty("KickAssAnime") val KickAssAnime: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
)

data class MALSyncResponses(
    @JsonProperty("Sites") val sites: MALSyncSites? = null,
)

data class HianimeResponses(
    @JsonProperty("html") val html: String? = null,
    @JsonProperty("link") val link: String? = null,
)

data class AllMovielandEpisodeFolder(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("file") val file: String? = null,
)

data class AllMovielandSeasonFolder(
    @JsonProperty("episode") val episode: String? = null,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("folder") val folder: ArrayList<AllMovielandEpisodeFolder>? = arrayListOf(),
)

data class AllMovielandServer(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("folder") val folder: ArrayList<AllMovielandSeasonFolder>? = arrayListOf(),
)

data class AllMovielandPlaylist(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("href") val href: String? = null,
)

data class ShowflixSearchMovies(
    @JsonProperty("results")
    val resultsMovies: ArrayList<ShowflixResultsMovies>? = arrayListOf()
)

data class ShowflixResultsMovies(
    @JsonProperty("objectId")
    val objectId: String? = null,
    @JsonProperty("name")
    val name: String? = null,
    @JsonProperty("releaseYear")
    val releaseYear: Int? = null,
    @JsonProperty("tmdbId")
    val tmdbId: Int? = null,
    @JsonProperty("embedLinks")
    val embedLinks: Map<String, String>? = null,
    @JsonProperty("languages")
    val languages: List<String>? = null,
    @JsonProperty("genres")
    val genres: List<String>? = null,
    @JsonProperty("backdropURL")
    val backdropURL: String? = null,
    @JsonProperty("posterURL")
    val posterURL: String? = null,
    @JsonProperty("hdLink")
    val hdLink: String? = null,
    @JsonProperty("hubCloudLink")
    val hubCloudLink: String? = null,
    @JsonProperty("storyline")
    val storyline: String? = null,
    @JsonProperty("rating")
    val rating: String? = null,
    @JsonProperty("createdAt")
    val createdAt: String? = null,
    @JsonProperty("updatedAt")
    val updatedAt: String? = null
)

data class ShowflixSearchSeries(
    @JsonProperty("results")
    val resultsSeries: ArrayList<ShowflixResultsSeries>? = arrayListOf()
)

data class ShowflixResultsSeries(
    @JsonProperty("objectId")
    val objectId: String? = null,
    @JsonProperty("seriesName")
    val seriesName: String? = null,
    @JsonProperty("releaseYear")
    val releaseYear: Int? = null,
    @JsonProperty("tmdbId")
    val tmdbId: Int? = null,
    @JsonProperty("streamwish")
    val streamwish: Map<String, List<String>>? = null,
    @JsonProperty("filelions")
    val filelions: Map<String, List<String>>? = null,
    @JsonProperty("streamruby")
    val streamruby: Map<String, List<String>>? = null,
    @JsonProperty("languages")
    val languages: List<String>? = null,
    @JsonProperty("genres")
    val genres: List<String>? = null,
    @JsonProperty("backdropURL")
    val backdropURL: String? = null,
    @JsonProperty("posterURL")
    val posterURL: String? = null,
    @JsonProperty("hdLink")
    val hdLink: String? = null,
    @JsonProperty("hubCloudLink")
    val hubCloudLink: String? = null,
    @JsonProperty("storyline")
    val storyline: String? = null,
    @JsonProperty("rating")
    val rating: String? = null,
    @JsonProperty("createdAt")
    val createdAt: String? = null,
    @JsonProperty("updatedAt")
    val updatedAt: String? = null
)

data class RidoContentable(
    @JsonProperty("imdbId") var imdbId: String? = null,
    @JsonProperty("tmdbId") var tmdbId: Int? = null,
)

data class RidoItems(
    @JsonProperty("slug") var slug: String? = null,
    @JsonProperty("contentable") var contentable: RidoContentable? = null,
)

data class RidoData(
    @JsonProperty("url") var url: String? = null,
    @JsonProperty("items") var items: ArrayList<RidoItems>? = arrayListOf(),
)

data class RidoResponses(
    @JsonProperty("data") var data: ArrayList<RidoData>? = arrayListOf(),
)

data class RidoSearch(
    @JsonProperty("data") var data: RidoData? = null,
)

data class NepuSearch(
    @JsonProperty("data") val data: ArrayList<Data>? = arrayListOf(),
) {
    data class Data(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("type") val type: String? = null,
    )
}
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

data class RiveStreamSource(
    val data: List<String>
)

data class KisskhKey(
    val id: String,
    val version: String,
    val key: String,
)

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

//
//StremplayAPI

data class StremplayAPI(
    val name: String,
    val fields: StremplayFields,
    val createTime: String,
    val updateTime: String,
)

data class StremplayFields(
    val links: StremplayLinks,
)

data class StremplayLinks(
    val arrayValue: StremplayArrayValue,
)

data class StremplayArrayValue(
    val values: List<StremplayValue>,
)

data class StremplayValue(
    val mapValue: StremplayMapValue,
)

data class StremplayMapValue(
    val fields: StremplayFields2,
)

data class StremplayFields2(
    val href: StremplayHref,
    val quality: StremplayQuality,
    val source: StremplaySource,
)

data class StremplayHref(
    val stringValue: String,
)

data class StremplayQuality(
    val stringValue: String,
)

data class StremplaySource(
    val stringValue: String,
)

data class AnimeKaiResponse(
    @JsonProperty("status") val status: Boolean,
    @JsonProperty("result") val result: String
) {
    fun getDocument(): Document {
        return Jsoup.parse(result)
    }
}

fun extractVideoUrlFromJsonAnimekai(jsonData: String): String {
    val jsonObject = JSONObject(jsonData)
    return jsonObject.getString("url")
}
data class AnichiStream(
    @JsonProperty("format") val format: String? = null,
    @JsonProperty("audio_lang") val audio_lang: String? = null,
    @JsonProperty("hardsub_lang") val hardsub_lang: String? = null,
    @JsonProperty("url") val url: String? = null,
)

data class PortData(
    @JsonProperty("streams") val streams: ArrayList<AnichiStream>? = arrayListOf(),
)

data class AnichiSubtitles(
    @JsonProperty("lang") val lang: String?,
    @JsonProperty("label") val label: String?,
    @JsonProperty("src") val src: String?,
)

data class AnichiLinks(
    @JsonProperty("link") val link: String,
    @JsonProperty("hls") val hls: Boolean? = null,
    @JsonProperty("resolutionStr") val resolutionStr: String,
    @JsonProperty("src") val src: String? = null,
    @JsonProperty("headers") val headers: Headers? = null,
    @JsonProperty("portData") val portData: PortData? = null,
    @JsonProperty("subtitles") val subtitles: ArrayList<AnichiSubtitles>? = arrayListOf(),
)

data class Headers(
    @JsonProperty("Referer") val referer: String? = null,
    @JsonProperty("Origin") val origin: String? = null,
    @JsonProperty("user-agent") val userAgent: String? = null,
)


data class AnichiVideoApiResponse(@JsonProperty("links") val links: List<AnichiLinks>)


data class ElevenmoviesServerEntry(
    val name: String,
    val description: String,
    val image: String,
    val data: String,
)

data class ElevenmoviesStreamResponse(
    val url: String?,
    val tracks: List<ElevenmoviesSubtitle>?
)

data class ElevenmoviesSubtitle(
    val label: String?,
    val file: String?
)

data class Elevenmoviesjson(
    val src: String,
    val dst: String,
    val static_path: String,
    val http_method: String,
    val key_hex: String,
    val iv_hex: String,
    val xor_key: String,
    val content_types: String,
)


//Domains Parser
data class DomainsParser(
    val moviesdrive: String,
    @JsonProperty("HDHUB4u")
    val hdhub4u: String,
    @JsonProperty("4khdhub")
    val n4khdhub: String,
    @JsonProperty("MultiMovies")
    val multiMovies: String,
    val bollyflix: String,
    @JsonProperty("UHDMovies")
    val uhdmovies: String,
    val moviesmod: String,
    val topMovies: String,
    val hdmovie2: String,
    val vegamovies: String,
    val rogmovies: String,
    val luxmovies: String,
    val movierulzhd: String,
    val extramovies: String,
    val dramadrip: String,
    val banglaplex: String,
    val toonstream: String,
    val telugumv: String,
    val filmycab: String,
    val tellyhd: String,
    val hindmoviez: String,
    @JsonProperty("hubcloud")
    val hubcloud: String,
)

//OXXFile

data class oxxfile(
    val id: String,
    val code: String,
    val fileName: String,
    val size: Long,
    val driveLinks: List<DriveLink>,
    val metadata: Metadata,
    val createdAt: String,
    val views: Long,
    val status: String,
    val gdtotLink: String?, // formerly Any?
    val gdtotName: String?, // formerly Any?
    val hubcloudLink: String,
    val filepressLink: String,
    val vikingLink: String?, // formerly Any?
    val pixeldrainLink: String?, // formerly Any?
    @SerializedName("credential_index")
    val credentialIndex: Long,
    val duration: String?, // safer to assume it's String for now
    val userName: String,
)

data class DriveLink(
    val fileId: String,
    val webViewLink: String,
    val driveLabel: String,
    val credentialIndex: Int,
    val isLoginDrive: Boolean,
    val isDrive2: Boolean
)

data class Metadata(
    val mimeType: String,
    val fileExtension: String,
    val modifiedTime: String,
    val createdTime: String,
    val pixeldrainConversionFailed: Boolean,
    val pixeldrainConversionFailedAt: String,
    val pixeldrainConversionError: String,
    val vikingConversionFailed: Boolean,
    val vikingConversionFailedAt: String
)

// CinemetaRes

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

data class Watch32(
    val type: String,
    val link: String,
)

data class Morph(
    @JsonProperty("error_code")
    val errorCode: Long,
    val message: String,
    val data: List<MorphDaum>,
)

data class MorphDaum(
    val title: String,
    val link: String,
)

data class CinemaOsSecretKeyRequest(
    val tmdbId: String,

    val imdbId: String,
    val seasonId: String,
    val episodeId: String
)


data class CinemaOSReponse(
    val data: CinemaOSReponseData,
    val encrypted: Boolean,
)

data class CinemaOSReponseData(
    val encrypted: String,
    val cin: String,
    val mao: String,
    val salt: String,
)


data class Vidlink(
    val sourceId: String,
    val stream: VidlinkStream,
)

data class VidlinkStream(
    val id: String,
    val type: String,
    val playlist: String,
    val flags: List<String>,
    val captions: List<VidlinkCaption>,
    @JsonProperty("TTL")
    val ttl: Long,
)

data class VidlinkCaption(
    val id: String,
    val url: String,
    val language: String,
    val type: String,
    val hasCorsRestrictions: Boolean,
)

data class PrimeSrcServerList(
    val servers: List<PrimeSrcServer>,
)

data class PrimeSrcServer(
    val name: String,
    val key: String,
    @JsonProperty("file_size")
    val fileSize: String?,
    @JsonProperty("file_name")
    val fileName: String?,
)

data class VidFastServer(
    val name: String,
    val description: String,
    val image: String,
    val data: String,
)

data class KeyIvResult(
    val keyBytes: ByteArray,
    val ivBytes: ByteArray,
    val keyHex: String,
    val ivHex: String
)

data class NuvioStreams(
    val streams: List<NuvioStreamsStream>,
)

data class NuvioStreamsStream(
    val name: String,
    val title: String,
    val url: String,
    val type: String,
    val availability: Long,
    val behaviorHints: NuvioStreamsBehaviorHints,
)

data class NuvioStreamsBehaviorHints(
    val notWebReady: Boolean,
)

data class YflixResponse(
    @get:JsonProperty("result") val result: String
) {
    fun getDocument(): Document {
        return Jsoup.parse(result)
    }
}

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

@JsonIgnoreProperties(ignoreUnknown = true)
data class EmbedmasterSourceItem(
    val id: Any? = null,
    val type: String? = null,
    @JsonProperty("source_type")
    val sourceType: Long? = null,
    @JsonProperty("source_quality")
    val sourceQuality: String? = null,
    @JsonProperty("source_name")
    val sourceName: String? = null,
    @JsonProperty("source_title")
    val sourceTitle: String? = null,
    @JsonProperty("source_subtitle")
    val sourceSubtitle: Boolean? = null,
    @JsonProperty("source_icon")
    val sourceIcon: Long? = null,
    @JsonProperty("source_url")
    val sourceUrl: String,
)

data class VideasySource(
    val key: String,
    val name: String,
    val language: String,
    val movieOnly: Boolean = false
)

data class BidSrcResponse(
    val total: Long,
    val sources: List<String>,
    val servers: List<Server>,
)

data class Server(
    val name: String,
    val quality: String,
    val type: String,
    val url: String,
    val headers: BidSrcHeaders?,
)

data class BidSrcHeaders(
    @JsonProperty("Referer")
    val referer: String,
    @JsonProperty("Origin")
    val origin: String,
)

data class Flixindia(
    val results: List<FlixindiaResult>,
    val query: String,
    val count: Long,
)

data class FlixindiaResult(
    val id: Long,
    val title: String,
    val url: String,
    @JsonProperty("created_at")
    val createdAt: String,
)


