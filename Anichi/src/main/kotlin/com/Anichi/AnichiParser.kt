package com.Anichi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.Serializable

object AnichiParser {

    data class AnichiLoadData(
            val hash: String,
            val dubStatus: String,
            val episode: String,
            val idMal: Int? = null,
    )

    data class JikanData(
            @param:JsonProperty("title") val title: String? = null,
            @param:JsonProperty("title_english") val title_english: String? = null,
            @param:JsonProperty("title_japanese") val title_japanese: String? = null,
            @param:JsonProperty("year") val year: Int? = null,
            @param:JsonProperty("season") val season: String? = null,
            @param:JsonProperty("type") val type: String? = null,
    )

    data class JikanResponse(
            @param:JsonProperty("data") val data: JikanData? = null,
    )

    data class IdMal(
            @param:JsonProperty("idMal") val idMal: String? = null,
    )

    data class MediaAni(
            @param:JsonProperty("Media") val media: IdMal? = null,
    )

    data class DataAni(
            @param:JsonProperty("data") val data: MediaAni? = null,
    )

    data class CoverImage(
            @param:JsonProperty("extraLarge") var extraLarge: String? = null,
            @param:JsonProperty("large") var large: String? = null,
    )

    data class AniMedia(
        @param:JsonProperty("id") val id: Int,
        @param:JsonProperty("idMal") val idMal: Int?,
        @param:JsonProperty("seasonYear") val seasonYear: Int?,
        @param:JsonProperty("format") val format: String?,
        @param:JsonProperty("title") val title: Title?,
        @param:JsonProperty("synonyms") val synonyms: List<String>?,
        @param:JsonProperty("coverImage") val coverImage: CoverImage?,
        @param:JsonProperty("bannerImage") val bannerImage: String?
    )

    data class Title(
        @param:JsonProperty("romaji") val romaji: String?,
        @param:JsonProperty("english") val english: String?,
        @param:JsonProperty("native") val native: String?
    )

    data class AniPage(@param:JsonProperty("media") var media: ArrayList<AniMedia> = arrayListOf())

    data class AniData(@param:JsonProperty("Page") var Page: AniPage? = AniPage())

    data class AniSearch(@param:JsonProperty("data") var data: AniData? = AniData())

    data class AkIframe(
            @param:JsonProperty("idUrl") val idUrl: String? = null,
    )

    data class Stream(
            @param:JsonProperty("format") val format: String? = null,
            @param:JsonProperty("audio_lang") val audio_lang: String? = null,
            @param:JsonProperty("hardsub_lang") val hardsub_lang: String? = null,
            @param:JsonProperty("url") val url: String? = null,
    )

    data class PortData(
            @param:JsonProperty("streams") val streams: ArrayList<Stream>? = arrayListOf(),
    )

    data class Subtitles(
            @param:JsonProperty("lang") val lang: String?,
            @param:JsonProperty("label") val label: String?,
            @param:JsonProperty("src") val src: String?,
    )

    data class Links(
        @param:JsonProperty("link") val link: String,
        @param:JsonProperty("hls") val hls: Boolean? = null,
        @param:JsonProperty("resolutionStr") val resolutionStr: String,
        @param:JsonProperty("src") val src: String? = null,
        @param:JsonProperty("headers") val headers: Headers? = null,
        @param:JsonProperty("portData") val portData: PortData? = null,
        @param:JsonProperty("subtitles") val subtitles: ArrayList<Subtitles>? = arrayListOf(),
    )

    data class Headers(
        @param:JsonProperty("Referer") val referer: String? = null,
        @param:JsonProperty("Origin") val origin: String? = null,
        @param:JsonProperty("user-agent") val userAgent: String? = null,
    )


    data class AnichiVideoApiResponse(@param:JsonProperty("links") val links: List<Links>)

    data class Data(
            @param:JsonProperty("shows") val shows: Shows? = null,
            @param:JsonProperty("queryListForTag") val queryListForTag: Shows? = null,
            @param:JsonProperty("queryPopular") val queryPopular: Shows? = null,
    )

    data class Shows(
            @param:JsonProperty("edges") val edges: List<Edges>? = arrayListOf(),
            @param:JsonProperty("recommendations") val recommendations: List<EdgesCard>? = arrayListOf(),
    )

    data class EdgesCard(
            @param:JsonProperty("anyCard") val anyCard: Edges? = null,
    )

    data class CharacterImage(
            @param:JsonProperty("large") val large: String?,
            @param:JsonProperty("medium") val medium: String?
    )

    data class CharacterName(
            @param:JsonProperty("full") val full: String?,
            @param:JsonProperty("native") val native: String?
    )

    data class Characters(
            @param:JsonProperty("image") val image: CharacterImage?,
            @param:JsonProperty("role") val role: String?,
            @param:JsonProperty("name") val name: CharacterName?,
    )

    data class Edges(
            @param:JsonProperty("_id") val Id: String?,
            @param:JsonProperty("name") val name: String?,
            @param:JsonProperty("englishName") val englishName: String?,
            @param:JsonProperty("nativeName") val nativeName: String?,
            @param:JsonProperty("thumbnail") val thumbnail: String?,
            @param:JsonProperty("type") val type: String?,
            @param:JsonProperty("season") val season: Season?,
            @param:JsonProperty("score") val score: Double?,
            @param:JsonProperty("airedStart") val airedStart: AiredStart?,
            @param:JsonProperty("availableEpisodes") val availableEpisodes: AvailableEpisodes?,
            @param:JsonProperty("availableEpisodesDetail")
            val availableEpisodesDetail: AvailableEpisodesDetail?,
            @param:JsonProperty("studios") val studios: List<String>?,
            @param:JsonProperty("genres") val genres: List<String>?,
            @param:JsonProperty("averageScore") val averageScore: Int?,
            @param:JsonProperty("characters") val characters: List<Characters>?,
            @param:JsonProperty("altNames") val altNames: List<String>?,
            @param:JsonProperty("description") val description: String?,
            @param:JsonProperty("status") val status: String?,
            @param:JsonProperty("banner") val banner: String?,
            @param:JsonProperty("episodeDuration") val episodeDuration: Int?,
            @param:JsonProperty("prevideos") val prevideos: List<String> = emptyList(),
    )

    data class AvailableEpisodes(
            @param:JsonProperty("sub") val sub: Int,
            @param:JsonProperty("dub") val dub: Int,
            @param:JsonProperty("raw") val raw: Int
    )

    data class AiredStart(
            @param:JsonProperty("year") val year: Int,
            @param:JsonProperty("month") val month: Int,
            @param:JsonProperty("date") val date: Int
    )

    data class Season(
            @param:JsonProperty("quarter") val quarter: String,
            @param:JsonProperty("year") val year: Int
    )

    data class AnichiQuery(@param:JsonProperty("data") val data: Data? = null)

    data class Detail(@param:JsonProperty("data") val data: DetailShow)

    data class DetailShow(@param:JsonProperty("show") val show: Edges)

    data class AvailableEpisodesDetail(
            @param:JsonProperty("sub") val sub: List<String>,
            @param:JsonProperty("dub") val dub: List<String>,
            @param:JsonProperty("raw") val raw: List<String>
    )

    data class LinksQuery(
        val data: LinkData? = null,
        val episode: Episode? = null
    )

    data class LinkData(
        val episode: Episode? = null
    )

    data class Episode(
        val sourceUrls: ArrayList<SourceUrls> = arrayListOf()
    )

    data class SourceUrls(
        val sourceUrl: String? = null,
        val downloads: Downloads? = null,
        val priority: Double? = null,
        val sourceName: String? = null,
        val type: String? = null,
        val className: String? = null,
        val streamerId: String? = null
    )

    data class Downloads(
        val sourceName: String? = null,
        val downloadUrl: String? = null
    )

}


@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaMappings(
    @param:JsonProperty("themoviedb_id") val themoviedbId: String? = null,
    @param:JsonProperty("thetvdb_id") val thetvdbId: Int? = null,
    @param:JsonProperty("imdb_id") val imdbId: String? = null,
    @param:JsonProperty("mal_id") val malId: Int? = null,
    @param:JsonProperty("anilist_id") val anilistId: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Image(
    @param:JsonProperty("coverType") val coverType: String?,
    @param:JsonProperty("url") val url: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpisodeInfo(
    @param:JsonProperty("episode") val episode: String?,
    @param:JsonProperty("airDateUtc") val airDateUtc: String?,  // Keeping only one field
    @param:JsonProperty("runtime") val runtime: Int?,     // Keeping only one field
    @param:JsonProperty("image") val image: String?,
    @param:JsonProperty("title") val title: Map<String, String>?,
    @param:JsonProperty("overview") val overview: String?,
    @param:JsonProperty("rating") val rating: String?,
    @param:JsonProperty("finaleType") val finaleType: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaAnimeData(
    @param:JsonProperty("titles") val titles: Map<String, String>?,
    @param:JsonProperty("images") val images: List<Image>?,
    @param:JsonProperty("episodes") val episodes: Map<String, EpisodeInfo>?,
    @param:JsonProperty("mappings") val mappings: MetaMappings? = null
)

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

@Serializable
data class EncryptedResponse(
    val data: EncryptedData? = null
)

@Serializable
data class EncryptedData(
    val _m: String? = null,
    val tobeparsed: String? = null
)