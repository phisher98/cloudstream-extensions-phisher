package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

//Vidsrccc

data class Vidsrccc(
    val id: String,
    val vrf: String,
    val timestamp: String,
)

data class Vidsrcccservers(
    val data: List<VidsrcccDaum>,
    val success: Boolean,
)

data class VidsrcccDaum(
    val name: String,
    val hash: String,
)

data class Vidsrcccm3u8(
    val data: VidsrcccData,
    val success: Boolean,
)

data class VidsrcccData(
    val type: String,
    val source: String,
)

data class VidsrcccSubtitle(
    val kind: String,
    val file: String,
    val label: String,
    val default: Boolean?,
)


//FlixHQAPI

data class SearchFlixAPI(
    val items: List<Item>,
    val pagination: Pagination,
)

data class Item(
    val id: String,
    val title: String,
    val poster: String,
    val stats: Stats,
)

data class Stats(
    val seasons: String?,
    val rating: String,
    val year: String?,
    val duration: String?,
)

data class Pagination(
    val current: Long,
    val total: Long,
)

//SeasonResponse

data class SeasonResponseFlixHQAPI(
    val seasons: List<SeasonFlixHQAPI>,
)

data class SeasonFlixHQAPI(
    val id: String,
    val number: Long,
)

//EpisodeResponseFlixHQAPI


data class EpisodeResponseFlixHQAPI(
    val episodes: List<EpisodeFlixHQAPI>,
)

data class EpisodeFlixHQAPI(
    val id: String,
    val number: Long,
    val title: String,
)


data class MoviedetailsResponse(
    val title: String,
    val description: String,
    val type: String,
    val stats: List<Stat>,
    val related: List<Related>,
    val episodeId: String,
)

data class Stat(
    val name: String,
    val value: Any?,
)

data class Related(
    val id: String,
    val title: String,
    val poster: String,
    val stats: MovieStats,
)

data class MovieStats(
    val year: String?,
    val duration: String?,
    val rating: String,
    val seasons: String?,
)


data class FlixServers(
    val servers: List<Server>,
)

data class Server(
    val id: String,
    val name: String,
)


data class FlixHQsources(
    val sources: List<FlixSource>,
    val tracks: List<Track>,
    val t: Long,
    val server: Long,
)

data class FlixSource(
    val file: String,
    val type: String,
)

data class Track(
    val file: String,
    val label: String,
    val kind: String,
    val default: Boolean?,
)


data class Seasonresponse(
    val seasons: List<Seasondata>,
)

data class Seasondata(
    val id: String,
    val number: Long,
)


data class Episoderesponse(
    val episodes: List<Episodedata>,
)

data class Episodedata(
    val id: String,
    val number: Long,
    val title: String,
)


//HinAuto

typealias HinAuto = List<HinAutoRoot2>;

data class HinAutoRoot2(
    val file: String,
    val label: String,
    val type: String,
)


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

//Anichi Download URL Parser

data class AnichiDownload(
    val links: List<AnichiDownloadLink>,
)

data class AnichiDownloadLink(
    val link: String,
    val hls: Boolean,
    val mp4: Boolean?,
    val resolutionStr: String,
    val priority: Long,
    val src: String?,
)




data class CrunchyrollAccessToken(
    val accessToken: String? = null,
    val tokenType: String? = null,
    val bucket: String? = null,
    val policy: String? = null,
    val signature: String? = null,
    val key_pair_id: String? = null,
)

data class FDMovieIFrame(
    val link: String,
    val quality: String,
    val size: String,
    val type: String,
)

data class AniIds(var id: Int? = null, var idMal: Int? = null)

data class TmdbDate(
    val today: String,
    val nextWeek: String,
    val lastWeekStart: String,
    val monthStart: String
)

data class AniwaveResponse(
    val result: String
) {
    fun asJsoup(): Document {
        return Jsoup.parse(result)
    }
}

data class AniwaveServer(
    val result: Result
) {
    data class Result(
        val url: String
    ) {
        fun decrypt(): String {
            return AniwaveUtils.vrfDecrypt(url)
        }
    }
}

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

data class GpressSources(
    @JsonProperty("src") val src: String,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: Int? = null,
    @JsonProperty("max") val max: String,
)
//AsianHDResponse
data class AsianHDResponse(
    val data: Data
)
data class Data(
    val links: List<Link>
)
data class Link(
    val type: String,
    val url: String
)

//Flicky

data class FlickyStream(
    val language: String,
    val url: String,
)


//Vidsrcsu

data class Vidsrcsu(
    @JsonProperty("m3u8_url")
    val m3u8Url: String,
    val language: String,
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

data class UHDBackupUrl(
    @JsonProperty("url") val url: String? = null,
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

data class DriveBotLink(
    @JsonProperty("url") val url: String? = null,
)

data class DirectDl(
    @JsonProperty("download_url") val download_url: String? = null,
)

data class Safelink(
    @JsonProperty("safelink") val safelink: String? = null,
)

data class FDAds(
    @JsonProperty("linkr") val linkr: String? = null,
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

data class WHVXSubtitle(
    val url: String,
    val languageName: String,
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
    @JsonProperty("external") val external: ArrayList<JikanExternal>? = arrayListOf(),
    val season: String,
)

data class JikanResponse(
    @JsonProperty("data") val data: JikanData? = null,
)

data class VidsrctoResult(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("url") val url: String? = null,
)

data class VidsrctoResponse(
    @JsonProperty("result") val result: VidsrctoResult? = null,
)

data class VidsrctoSources(
    @JsonProperty("result") val result: ArrayList<VidsrctoResult>? = arrayListOf(),
)

data class VidsrctoSubtitles(
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("file") val file: String? = null,
)


data class SmashyRoot(
    val data: SmashyData,
    val success: Boolean,
)

data class SmashyData(
    val sources: List<Source>,
    val tracks: String,
)

data class Source(
    val file: String,
)



data class AnilistExternalLinks(
    @JsonProperty("id") var id: Int? = null,
    @JsonProperty("site") var site: String? = null,
    @JsonProperty("url") var url: String? = null,
    @JsonProperty("type") var type: String? = null,
)

data class AnilistMedia(@JsonProperty("externalLinks") var externalLinks: ArrayList<AnilistExternalLinks> = arrayListOf())

data class AnilistData(@JsonProperty("Media") var Media: AnilistMedia? = AnilistMedia())

data class AnilistResponses(@JsonProperty("data") var data: AnilistData? = AnilistData())

data class CrunchyrollToken(
    @JsonProperty("access_token") val accessToken: String? = null,
    @JsonProperty("token_type") val tokenType: String? = null,
    @JsonProperty("cms") val cms: Cms? = null,
) {
    data class Cms(
        @JsonProperty("bucket") var bucket: String? = null,
        @JsonProperty("policy") var policy: String? = null,
        @JsonProperty("signature") var signature: String? = null,
        @JsonProperty("key_pair_id") var key_pair_id: String? = null,
    )
}

data class CrunchyrollVersions(
    @JsonProperty("audio_locale") val audio_locale: String? = null,
    @JsonProperty("guid") val guid: String? = null,
)

data class CrunchyrollData(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug_title") val slug_title: String? = null,
    @JsonProperty("season_number") val season_number: Int? = null,
    @JsonProperty("episode_number") val episode_number: Int? = null,
    @JsonProperty("versions") val versions: ArrayList<CrunchyrollVersions>? = null,
    @JsonProperty("streams_link") val streams_link: String? = null,
)

data class CrunchyrollResponses(
    @JsonProperty("data") val data: ArrayList<CrunchyrollData>? = arrayListOf(),
)

data class CrunchyrollSourcesResponses(
    @JsonProperty("streams") val streams: Streams? = Streams(),
    @JsonProperty("subtitles") val subtitles: HashMap<String, HashMap<String, String>>? = hashMapOf(),
) {
    data class Streams(
        @JsonProperty("adaptive_hls") val adaptive_hls: HashMap<String, HashMap<String, String>>? = hashMapOf(),
        @JsonProperty("vo_adaptive_hls") val vo_adaptive_hls: HashMap<String, HashMap<String, String>>? = hashMapOf(),
    )
}
//Hianime


data class HiAnimeResponse(
    val intro: HiAnimeIntro,
    val outro: HiAnimeOutro,
    val sources: List<HiAnimeSource>,
    val subtitles: List<HiAnimeSubtitle>,
)

data class HiAnimeIntro(
    val start: Long,
    val end: Long,
)

data class HiAnimeOutro(
    val start: Long,
    val end: Long,
)

data class HiAnimeSource(
    val url: String,
    val isM3U8: Boolean,
    val type: String,
)

data class HiAnimeSubtitle(
    val url: String,
    val lang: String,
)


data class HiAnimeAPI(
    val sources: List<HiAnimeAPISource>,
    val tracks: List<HiAnimeAPITrack>,
)

data class HiAnimeAPISource(
    val file: String,
    val type: String,
)

data class HiAnimeAPITrack(
    val file: String,
    val label: String,
)

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

data class MalSyncRes(
    @JsonProperty("Sites") val Sites: Map<String, Map<String, Map<String, String>>>? = null,
)

data class GokuData(
    @JsonProperty("link") val link: String? = null,
)

data class GokuServer(
    @JsonProperty("data") val data: GokuData? = GokuData(),
)
//Tom

data class TomResponse (
    var videoSource    : String,
    var subtitles      : ArrayList<TomSubtitles> = arrayListOf(),
)

data class TomSubtitles (
    var file    : String,
    var label   : String
)

//Gojo

data class Gojoresponseshashh(
    val sources: List<shashhSource>,
    val thumbs: String,
    val skips: Any?,
)

data class shashhSource(
    val quality: String,
    val url: String,
)

data class Gojoresponsevibe(
    val sources: List<vibeSource>,
    val skips: Any?,
)

data class vibeSource(
    val url: String,
    val quality: String,
    val type: String,
)


//MiruroanimeGogo

data class MiruroanimeGogo(
    val sources: List<MiruroSource>,
    val download: String,
)

data class MiruroSource(
    val url: String,
    val isM3U8: Boolean,
    val quality: String,
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

data class DumpMedia(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("domainType") val domainType: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("releaseTime") val releaseTime: String? = null,
)

data class DumpQuickSearchData(
    @JsonProperty("searchResults") val searchResults: ArrayList<DumpMedia>? = arrayListOf(),
)

data class SubtitlingList(
    @JsonProperty("languageAbbr") val languageAbbr: String? = null,
    @JsonProperty("language") val language: String? = null,
    @JsonProperty("subtitlingUrl") val subtitlingUrl: String? = null,
)

data class DefinitionList(
    @JsonProperty("code") val code: String? = null,
    @JsonProperty("description") val description: String? = null,
)

data class EpisodeVo(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("seriesNo") val seriesNo: Int? = null,
    @JsonProperty("definitionList") val definitionList: ArrayList<DefinitionList>? = arrayListOf(),
    @JsonProperty("subtitlingList") val subtitlingList: ArrayList<SubtitlingList>? = arrayListOf(),
)

data class DumpMediaDetail(
    @JsonProperty("episodeVo") val episodeVo: ArrayList<EpisodeVo>? = arrayListOf(),
)

data class EMovieServer(
    @JsonProperty("value") val value: String? = null,
)

data class EMovieSources(
    @JsonProperty("file") val file: String? = null,
)

data class EMovieTraks(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null,
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


data class SFMoviesSeriess(
    @JsonProperty("title") var title: String? = null,
    @JsonProperty("svideos") var svideos: String? = null,
)

data class SFMoviesAttributes(
    @JsonProperty("title") var title: String? = null,
    @JsonProperty("video") var video: String? = null,
    @JsonProperty("releaseDate") var releaseDate: String? = null,
    @JsonProperty("seriess") var seriess: ArrayList<ArrayList<SFMoviesSeriess>>? = arrayListOf(),
    @JsonProperty("contentId") var contentId: String? = null,
)

data class SFMoviesData(
    @JsonProperty("id") var id: Int? = null,
    @JsonProperty("attributes") var attributes: SFMoviesAttributes? = SFMoviesAttributes()
)

data class SFMoviesSearch(
    @JsonProperty("data") var data: ArrayList<SFMoviesData>? = arrayListOf(),
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

data class SmashySources(
    @JsonProperty("sourceUrls") var sourceUrls: ArrayList<String>? = arrayListOf(),
    @JsonProperty("subtitleUrls") var subtitleUrls: String? = null,
)

data class AoneroomResponse(
    @JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
        @JsonProperty("list") val list: ArrayList<List>? = arrayListOf(),
    ) {
        data class Items(
            @JsonProperty("subjectId") val subjectId: String? = null,
            @JsonProperty("title") val title: String? = null,
            @JsonProperty("releaseDate") val releaseDate: String? = null,
        )

        data class List(
            @JsonProperty("resourceLink") val resourceLink: String? = null,
            @JsonProperty("extCaptions") val extCaptions: ArrayList<ExtCaptions>? = arrayListOf(),
            @JsonProperty("se") val se: Int? = null,
            @JsonProperty("ep") val ep: Int? = null,
            @JsonProperty("resolution") val resolution: Int? = null,
        ) {
            data class ExtCaptions(
                @JsonProperty("lanName") val lanName: String? = null,
                @JsonProperty("url") val url: String? = null,
            )
        }
    }
}

//

data class Aoneroomep(
    val code: Long,
    val message: String,
    val data: AoneroomepData,
)

data class AoneroomepData(
    val streams: List<AoneroomepStream>,
    val title: String,
)

data class AoneroomepStream(
    val format: String,
    val id: String,
    val url: String,
    val resolutions: String,
    val size: String,
    val duration: Long,
    val codecName: String,
    val signCookie: String,
)


data class CinemaTvResponse(
    @JsonProperty("streams") val streams: HashMap<String, String>? = null,
    @JsonProperty("subtitles") val subtitles: ArrayList<Subtitles>? = arrayListOf(),
) {
    data class Subtitles(
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("file") val file: Any? = null,
    )
}

data class NepuSearch(
    @JsonProperty("data") val data: ArrayList<Data>? = arrayListOf(),
) {
    data class Data(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("type") val type: String? = null,
    )
}

data class Vidbinge(
    val token: String,
)

data class Vidbingesources(
    val embedId: String,
    val url: String,
)

data class Vidbingeplaylist(
    val stream: List<Streamplaylist>,
)

//SharmaFlix

// Top-level list alias
typealias SharmaFlixRoot = List<SharmaFlixRoot2>

// Root2 data class
data class SharmaFlixRoot2(
    val id: String,
    @JsonProperty("TMDB_ID")
    val tmdbId: String,
    val name: String,
    val description: String,
    val genres: String,
    @JsonProperty("release_date")
    val releaseDate: String,
    val runtime: String,
    val poster: String,
    val banner: String,
    @JsonProperty("youtube_trailer")
    val youtubeTrailer: String,
    val downloadable: String,
    val type: String,
    val status: String,
    @JsonProperty("content_type")
    val contentType: String,
    @JsonProperty("custom_tag")
    val customTag: CustomTag
)

// CustomTag data class
data class CustomTag(
    val id: String,
    @JsonProperty("custom_tags_id")
    val customTagsId: String,
    @JsonProperty("content_id")
    val contentId: String,
    @JsonProperty("content_type")
    val contentType: String,
    @JsonProperty("custom_tags_name")
    val customTagsName: String,
    @JsonProperty("background_color")
    val backgroundColor: String,
    @JsonProperty("text_color")
    val textColor: String
)

typealias SharmaFlixLinks = List<SharmaFlixLink>

data class SharmaFlixLink(
    val id: String,
    val name: String,
    val size: String,
    val quality: String,
    @JsonProperty("link_order")
    val linkOrder: String,
    @JsonProperty("movie_id")
    val movieId: String,
    val url: String,
    val type: String,
    val status: String,
    @JsonProperty("skip_available")
    val skipAvailable: String,
    @JsonProperty("intro_start")
    val introStart: String?,
    @JsonProperty("intro_end")
    val introEnd: String?,
    @JsonProperty("end_credits_marker")
    val endCreditsMarker: String,
    @JsonProperty("link_type")
    val linkType: String,
    @JsonProperty("drm_uuid")
    val drmUuid: String?,
    @JsonProperty("drm_license_uri")
    val drmLicenseUri: String?
)


data class Streamplaylist(
    val id: String,
    val type: String,
    val playlist: String,
    val flags: List<String>,
    val captions: List<Captionplaylist>,
)

data class Captionplaylist(
    val id: String,
    val url: String,
    val type: String,
    val hasCorsRestrictions: Boolean,
    val language: String,
)

//

data class Embedsu(
    val title: String,
    val server: String,
    val ref: String,
    val xid: String,
    val uwuId: String,
    val episodeId: String,
    val hash: String,
    val poster: String,
)

data class EmbedsuItem(val name: String, val hash: String)


data class Embedsuhref(
    val source: String,
    val subtitles: List<EmbedsuhrefSubtitle>,
    val skips: List<Any?>,
    val format: String,
)

data class EmbedsuhrefSubtitle(
    val label: String,
    val file: String,
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

// Theyallsayflix

data class Theyallsayflix(
    val error: Boolean,
    val title: String,
    @JsonProperty("poster_url")
    val posterUrl: String,
    @JsonProperty("backdrop_url")
    val backdropUrl: String,
    @JsonProperty("release_date")
    val releaseDate: String,
    val rating: Double,
    val desc: String,
    @JsonProperty("original_lang")
    val originalLang: String,
    val minutes: Long,
    val status: String,
    @JsonProperty("status_desc")
    val statusDesc: String,
    val streams: List<TheyallsayflixStream>,
    val subtitles: List<TheyallsayflixSubtitle>,
)

data class TheyallsayflixStream(
    val quality: Long,
    @JsonProperty("file_size")
    val fileSize: Long,
    @JsonProperty("file_name")
    val fileName: String,
    @JsonProperty("play_url")
    val playUrl: String,
)

data class TheyallsayflixSubtitle(
    @JsonProperty("lang_code")
    val langCode: String,
    @JsonProperty("lang_name")
    val langName: String,
    @JsonProperty("sub_name")
    val subName: String,
    @JsonProperty("is_hearing_impaired")
    val isHearingImpaired: Boolean,
    @JsonProperty("download_link")
    val downloadLink: String,
)

//AnimeNexus

data class AnimeNexus(
    val data: List<AnimeNexusDaum>,
)

data class AnimeNexusDaum(
    val broadcast: Any?,
    val description: String,
    @JsonProperty("end_date")
    val endDate: String?,
    @JsonProperty("episode_count")
    val episodeCount: Long,
    val id: String,
    val name: String,
    @JsonProperty("name_alt")
    val nameAlt: String,
    @JsonProperty("parental_rating")
    val parentalRating: String,
    @JsonProperty("release_date")
    val releaseDate: String,
    val slug: String,
    val status: String,
    val type: String,
)



data class AnimeNexusEp(
    val data: List<AnimeNexusEpDaum>,
)

data class AnimeNexusEpDaum(
    val id: String,
    val title: Any?,
    val slug: String,
    val number: Int,
    val duration: Long,
    @JsonProperty("video_meta")
    val isFiller: Long,
    @JsonProperty("is_recap")
    val isRecap: Long,
)


data class AnimeNexusservers(
    val data: AnimeNexusserversData,
)

data class AnimeNexusserversData(
    val subtitles: List<AnimeNexusserversSubtitle>,
    @JsonProperty("video_meta")
    val videoMeta: AnimeNexusserversVideoMeta,
    val hls: String,
    val mpd: String,
    val thumbnails: String,
)

data class AnimeNexusserversSubtitle(
    val id: String,
    val src: String,
    val label: String,
    val srcLang: String,
)

data class AnimeNexusserversVideoMeta(
    val duration: Long,
    val chapters: String,
    @JsonProperty("audio_languages")
    val audioLanguages: List<String>,
    val status: String,
    val qualities: AnimeNexusserversQualities,
    @JsonProperty("file_size_streams")
    val fileSizeStreams: AnimeNexusserversFileSizeStreams,
)

data class AnimeNexusserversQualities(
    @JsonProperty("1920x1080")
    val n1920x1080: Long,
    @JsonProperty("1280x720")
    val n1280x720: Long,
    @JsonProperty("848x480")
    val n848x480: Long,
)

data class AnimeNexusserversFileSizeStreams(
    @JsonProperty("848x480")
    val n848x480: Long,
    @JsonProperty("1280x720")
    val n1280x720: Long,
    @JsonProperty("1920x1080")
    val n1920x1080: Long,
)


data class RiveStreamSource(
    val data: List<String>
)

data class RiveStreamResponse(
    val data: RiveStreamData,
)

data class RiveStreamData(
    val sources: List<RiveStreamSourceData>,
)

data class RiveStreamSourceData(
    val quality: String,
    val url: String,
    val source: String,
    val format: String,
)

data class RivestreamEmbedResponse(
    val data: RivestreamEmbedData,
)

data class RivestreamEmbedData(
    val sources: List<RivestreamEmbedSource>,
)

data class RivestreamEmbedSource(
    val host: String,
    @JsonProperty("host_id")
    val hostId: Long,
    val link: String,
)

data class VidSrcVipSource(
    val language: String,
    @JsonProperty("m3u8_stream")
    val m3u8Stream: String,
)


//Dramacool

data class Dramacool (
    var streams : ArrayList<DramacoolStreams> = arrayListOf()
)

data class DramacoolSubtitles (
    var lang : String,
    var url  : String
)

data class DramacoolStreams (
    var subtitles : ArrayList<DramacoolSubtitles> = arrayListOf(),
    var title     : String,
    var url       : String
)


//Consumet

data class ConsumetSearch (
    var results     : ArrayList<ConsumetResults> = arrayListOf()
)

data class ConsumetResults (
    var id          : String,
    var title       : String,
    var type        : String
)

data class ConsumetInfo (
    var id          : String,
    var episodes    : ArrayList<ConsumetEpisodes> = arrayListOf()
)


data class ConsumetEpisodes (
    var id     : String,
    var number : Int? = null,
    var season : Int? = null,
)

data class ConsumetWatch (
    var headers   : ConsumetHeaders      = ConsumetHeaders(),
    var sources   : ArrayList<ConsumetSources>   = arrayListOf(),
    var subtitles : ArrayList<ConsumetSubtitles> = arrayListOf()
)

data class ConsumetHeaders (
    var Referer : String? = null,
)

data class ConsumetSources (
    var url     : String,
    var quality : String,
    var isM3U8  : Boolean
)

data class ConsumetSubtitles (
    var url  : String,
    var lang : String
)


data class RgshowsFlickyStream(
    val stream: List<RgshowFlickySourceResponse>,
)

data class RgshowsStream(
    val stream: RgshowSourceResponse,
)

data class RgshowFlickySourceResponse(
    val url: String,
    val quality: String,
)

data class RgshowSourceResponse(
    val url: String,
)

data class RgshowsHindi(
    val success: Boolean,
    val data: RgshowsHindiData,
)

data class RgshowsHindiData(
    val playlist: List<RgshowsHindiPlaylist>,
    val key: String,
)

data class RgshowsHindiPlaylist(
    val title: String,
    val id: String,
    val file: String,
)

data class RgshowsHindiResponse(
    val success: Boolean,
    val data: RgshowsHindiResponseData,
)

data class RgshowsHindiResponseData(
    val link: String,
)


data class KisskhKey(
    val id: String,
    val version: String,
    val key: String,
)


data class ConsumetServers2(
    val name: String,
    val url: String,
)

data class FlixHQIframe(
    val type: String,
    val link: String,
)


data class FlixHQIframeiframe(
    val sources: List<Sourceiframe>,
    val tracks: List<Trackiframe>,
)

data class Sourceiframe(
    val file: String,
    val type: String,
)

data class Trackiframe(
    val file: String,
    val label: String,
)

data class SeasonDetail
    (
    val quality:String?,
    val episodeLinkMap:MutableMap<String,MutableList<String>>?,
    val season:String?,
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

data class UiraResponse(
    val sourceId: String,
    val stream: UiraStream,
)

data class UiraStream(
    val id: String,
    val playlist: String,
    val type: String,
    val flags: List<String>,
    val captions: List<UiraCaption>,
)

data class UiraCaption(
    val id: String,
    val language: String,
    val url: String,
)
//Kickass

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

data class Player4uLinkData(
    val name: String,
    val url: String,
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


//MiroTv

data class MiroTV(
    val ANIMEKAI: Map<String, AnimeKaiDetails>,
    val ANIMEZ: Map<String, AnimeZDetails>
) {
    data class AnimeKaiDetails(
        val altTitle: String,
        val description: String,
        val details: Details,
        val episodeList: EpisodeList,
        val genres: List<Any>,
        val poster: String,
        val rateBox: RateBox,
        val rating: String,
        val recommendedAnime: List<RecommendedAnime>,
        val relatedAnime: List<Any>,
        val seasons: List<Any>,
        val title: String
    ) {
        data class Details(
            val broadcast: String,
            val country: String,
            val dateAired: String,
            val duration: String,
            val episodes: String,
            val malScore: String,
            val premiered: String,
            val producers: List<String>,
            val status: String,
            val studios: List<String>
        )

        data class EpisodeList(
            val episodes: List<Episode>,
            val totalEpisodes: Int
        ) {
            data class Episode(
                val dub: Boolean,
                val id: String,
                val number: Int,
                val slug: String,
                val title: String
            )
        }

        data class RateBox(
            val value: String,
            val voteStats: String
        )

        data class RecommendedAnime(
            val dub: Int,
            val sub: Int,
            val title: String,
            val total: Int,
            val type: String,
            val url: String
        )
    }

    data class AnimeZDetails(
        val altTitle: String,
        val episodeList: EpisodeList,
        val genres: List<String>,
        val id: String,
        val image: String,
        val status: String,
        val summary: String,
        val title: String,
        val type: String,
        val views: String
    ) {
        data class EpisodeList(
            val episodes: Episodes,
            val totalEpisodes: Int
        ) {
            data class Episodes(
                val dub: List<Any>,
                val sub: List<Sub>
            ) {
                data class Sub(
                    val id: String,
                    val number: Int,
                    val title: String,
                    val url: String
                )
            }
        }
    }
}


data class AnimeKaiResponse(
    @JsonProperty("status") val status: Boolean,
    @JsonProperty("result") val result: String
) {
    fun getDocument(): Document {
        return Jsoup.parse(result)
    }
}

data class VideoData(
    val url: String,
    val skip: Skip,
)

data class Skip(
    val intro: List<Long>,
    val outro: List<Long>,
)

fun extractVideoUrlFromJson(jsonData: String): String {
    val gson = com.google.gson.Gson()
    val videoData = gson.fromJson(jsonData, VideoData::class.java)
    return videoData.url
}

fun extractVideoUrlFromJsonAnimekai(jsonData: String): String {
    val jsonObject = JSONObject(jsonData)
    return jsonObject.getString("url")
}

data class AnimeKaiM3U8(
    val sources: List<AnimekaiSource>,
    val tracks: List<AnimekaiTrack>,
    val download: String,
)
data class AnimekaiSource(
    val file: String,
)

data class AnimekaiTrack(
    val file: String,
    val label: String?,
    val kind: String,
    val default: Boolean?,
)



data class Xprime(
    @JsonProperty("available_qualities")
    val availableQualities: List<String>,
    @JsonProperty("has_subtitles")
    val hasSubtitles: Boolean,
    val message: Any?,
    val status: String,
    val streams: XprimeStreams,
    val subtitles: List<XprimeSubtitle>,
)

data class XprimeStreams(
    @JsonProperty("1080p")
    val n1080p: String,
    @JsonProperty("720p")
    val n720p: String,
    @JsonProperty("480p")
    val n480p: String,
)

data class XprimeSubtitle(
    val file: String,
    val label: String,
)


data class AkIframe(
    @JsonProperty("idUrl") val idUrl: String? = null,
)




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


typealias ElevenmoviesServer = List<ElevenmoviesServerEntry>

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
    @JsonProperty("static_path")
    val staticPath: String,
    @JsonProperty("http_method")
    val httpMethod: String,
    @JsonProperty("key_hex")
    val keyHex: String,
    @JsonProperty("iv_hex")
    val ivHex: String,
    @JsonProperty("xor_key")
    val xorKey: String,
    @JsonProperty("csrf_token")
    val csrfToken: String,
    @JsonProperty("content_types")
    val contentTypes: String,
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
    val xprime: String,
    val extramovies:String,
)


//Xprime
data class XprimeServers(
    val servers: List<XprimeServer1>,
)

data class XprimeServer1(
    val name: String,
    val status: String,
    val language: String,
)


data class XprimeStream(
    @JsonProperty("available_qualities") val qualities: List<String>,
    @JsonProperty("status") val status: String,
    @JsonProperty("has_subtitles") val hasSubtitles: Boolean,
    @JsonProperty("subtitles") val subtitles: List<XprimePrimeSubs>
)

data class XprimePrimeSubs(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null,
)


//


data class Cinemeta(
    val meta: cinemetaMeta,
)

data class cinemetaMeta(
    val description: String,
    val director: List<String>,
    val genre: List<String>,
    val imdbRating: String,
    val name: String,
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

//
data class Beamup(
    val meta: BeamupMeta?
)

data class BeamupMeta(
    val name: String?
)