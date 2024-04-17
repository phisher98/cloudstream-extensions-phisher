package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty

data class AnichiLoadData(
    val hash: String,
    val dubStatus: String,
    val episode: String,
    val idMal: Int? = null,
)

data class JikanData(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("title_english") val title_english: String? = null,
    @JsonProperty("title_japanese") val title_japanese: String? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("season") val season: String? = null,
    @JsonProperty("type") val type: String? = null,
)

data class JikanResponse(
    @JsonProperty("data") val data: JikanData? = null,
)

data class IdMal(
    @JsonProperty("idMal") val idMal: String? = null,
)

data class MediaAni(
    @JsonProperty("Media") val media: IdMal? = null,
)

data class DataAni(
    @JsonProperty("data") val data: MediaAni? = null,
)

data class CoverImage(
    @JsonProperty("extraLarge") var extraLarge: String? = null,
    @JsonProperty("large") var large: String? = null,
)

data class AniMedia(
    @JsonProperty("id") var id: Int? = null,
    @JsonProperty("idMal") var idMal: Int? = null,
    @JsonProperty("coverImage") var coverImage: CoverImage? = null,
    @JsonProperty("bannerImage") var bannerImage: String? = null,
)

data class AniPage(
    @JsonProperty("media") var media: ArrayList<AniMedia> = arrayListOf()
)

data class AniData(
    @JsonProperty("Page") var Page: AniPage? = AniPage()
)

data class AniSearch(
    @JsonProperty("data") var data: AniData? = AniData()
)

data class AkIframe(
    @JsonProperty("idUrl") val idUrl: String? = null,
)

data class Stream(
    @JsonProperty("format") val format: String? = null,
    @JsonProperty("audio_lang") val audio_lang: String? = null,
    @JsonProperty("hardsub_lang") val hardsub_lang: String? = null,
    @JsonProperty("url") val url: String? = null,
)

data class PortData(
    @JsonProperty("streams") val streams: ArrayList<Stream>? = arrayListOf(),
)

data class Subtitles(
    @JsonProperty("lang") val lang: String?,
    @JsonProperty("label") val label: String?,
    @JsonProperty("src") val src: String?,
)

data class Links(
    @JsonProperty("link") val link: String,
    @JsonProperty("hls") val hls: Boolean?,
    @JsonProperty("resolutionStr") val resolutionStr: String,
    @JsonProperty("src") val src: String?,
    @JsonProperty("portData") val portData: PortData? = null,
    @JsonProperty("subtitles") val subtitles: ArrayList<Subtitles>? = arrayListOf(),
)

data class AnichiVideoApiResponse(
    @JsonProperty("links") val links: List<Links>
)

data class Data(
    @JsonProperty("shows") val shows: Shows? = null,
    @JsonProperty("queryListForTag") val queryListForTag: Shows? = null,
    @JsonProperty("queryPopular") val queryPopular: Shows? = null,
)

data class Shows(
    @JsonProperty("edges") val edges: List<Edges>? = arrayListOf(),
    @JsonProperty("recommendations") val recommendations: List<EdgesCard>? = arrayListOf(),
)

data class EdgesCard(
    @JsonProperty("anyCard") val anyCard: Edges? = null,
)

data class CharacterImage(
    @JsonProperty("large") val large: String?,
    @JsonProperty("medium") val medium: String?
)

data class CharacterName(
    @JsonProperty("full") val full: String?,
    @JsonProperty("native") val native: String?
)

data class Characters(
    @JsonProperty("image") val image: CharacterImage?,
    @JsonProperty("role") val role: String?,
    @JsonProperty("name") val name: CharacterName?,
)

data class Edges(
    @JsonProperty("_id") val Id: String?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("englishName") val englishName: String?,
    @JsonProperty("nativeName") val nativeName: String?,
    @JsonProperty("thumbnail") val thumbnail: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("season") val season: Season?,
    @JsonProperty("score") val score: Double?,
    @JsonProperty("airedStart") val airedStart: AiredStart?,
    @JsonProperty("availableEpisodes") val availableEpisodes: AvailableEpisodes?,
    @JsonProperty("availableEpisodesDetail") val availableEpisodesDetail: AvailableEpisodesDetail?,
    @JsonProperty("studios") val studios: List<String>?,
    @JsonProperty("genres") val genres: List<String>?,
    @JsonProperty("averageScore") val averageScore: Int?,
    @JsonProperty("characters") val characters: List<Characters>?,
    @JsonProperty("altNames") val altNames: List<String>?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("status") val status: String?,
    @JsonProperty("banner") val banner: String?,
    @JsonProperty("episodeDuration") val episodeDuration: Int?,
    @JsonProperty("prevideos") val prevideos: List<String> = emptyList(),
)

data class AvailableEpisodes(
    @JsonProperty("sub") val sub: Int,
    @JsonProperty("dub") val dub: Int,
    @JsonProperty("raw") val raw: Int
)

data class AiredStart(
    @JsonProperty("year") val year: Int,
    @JsonProperty("month") val month: Int,
    @JsonProperty("date") val date: Int
)

data class Season(
    @JsonProperty("quarter") val quarter: String,
    @JsonProperty("year") val year: Int
)

data class AnichiQuery(
    @JsonProperty("data") val data: Data? = null
)

data class Detail(
    @JsonProperty("data") val data: DetailShow
)

data class DetailShow(
    @JsonProperty("show") val show: Edges
)

data class AvailableEpisodesDetail(
    @JsonProperty("sub") val sub: List<String>,
    @JsonProperty("dub") val dub: List<String>,
    @JsonProperty("raw") val raw: List<String>
)

data class LinksQuery(
    @JsonProperty("data") val data: LinkData? = LinkData()
)

data class LinkData(
    @JsonProperty("episode") val episode: Episode? = Episode()
)

data class SourceUrls(
    @JsonProperty("sourceUrl") val sourceUrl: String? = null,
    @JsonProperty("priority") val priority: Int? = null,
    @JsonProperty("sourceName") val sourceName: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("className") val className: String? = null,
    @JsonProperty("streamerId") val streamerId: String? = null
)

data class Episode(
    @JsonProperty("sourceUrls") val sourceUrls: ArrayList<SourceUrls> = arrayListOf(),
)

data class Sub(
    @JsonProperty("hour") val hour: Int? = null,
    @JsonProperty("minute") val minute: Int? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("month") val month: Int? = null,
    @JsonProperty("date") val date: Int? = null
)

data class LastEpisodeDate(
    @JsonProperty("dub") val dub: Sub? = Sub(),
    @JsonProperty("sub") val sub: Sub? = Sub(),
    @JsonProperty("raw") val raw: Sub? = Sub()
)

data class AnyCard(
    @JsonProperty("_id") val Id: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("englishName") val englishName: String? = null,
    @JsonProperty("nativeName") val nativeName: String? = null,
    @JsonProperty("availableEpisodes") val availableEpisodes: AvailableEpisodes? = null,
    @JsonProperty("score") val score: Double? = null,
    @JsonProperty("lastEpisodeDate") val lastEpisodeDate: LastEpisodeDate? = LastEpisodeDate(),
    @JsonProperty("thumbnail") val thumbnail: String? = null,
    @JsonProperty("lastChapterDate") val lastChapterDate: String? = null,
    @JsonProperty("availableChapters") val availableChapters: String? = null,
    @JsonProperty("__typename") val _typename: String? = null
)

data class PageStatus(
    @JsonProperty("_id") val Id: String? = null,
    @JsonProperty("views") val views: String? = null,
    @JsonProperty("showId") val showId: String? = null,
    @JsonProperty("rangeViews") val rangeViews: String? = null,
    @JsonProperty("isManga") val isManga: Boolean? = null,
    @JsonProperty("__typename") val _typename: String? = null
)

data class Recommendations(
    @JsonProperty("anyCard") val anyCard: AnyCard? = null,
    @JsonProperty("pageStatus") val pageStatus: PageStatus? = PageStatus(),
    @JsonProperty("__typename") val _typename: String? = null
)

data class QueryPopular(
    @JsonProperty("total") val total: Int? = null,
    @JsonProperty("recommendations") val recommendations: ArrayList<Recommendations> = arrayListOf(),
    @JsonProperty("__typename") val _typename: String? = null
)

data class DataPopular(
    @JsonProperty("queryPopular") val queryPopular: QueryPopular? = QueryPopular()
)