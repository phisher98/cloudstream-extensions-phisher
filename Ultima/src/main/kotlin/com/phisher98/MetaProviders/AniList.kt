package com.phisher98

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.phisher98.UltimaMediaProvidersUtils.invokeExtractors
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.api.Log
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.CoverImage
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.LikePageInfo
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Media
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.RecommendationConnection
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.SeasonNextAiringEpisode
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Title
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.roundToInt

class AniList(val plugin: UltimaPlugin) : MainAPI() {
    override var name = "AniList"
    override var mainUrl = "https://anilist.co"
    override var supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Anilist)
    override val hasMainPage = true
    override val hasQuickSearch = false
    private val api = AccountManager.aniListApi
    private val apiUrl = "https://graphql.anilist.co"
    private final val mediaLimit = 20
    private final val isAdult = false
    private val headerJSON =
            mapOf("Accept" to "application/json", "Content-Type" to "application/json")

    protected fun Any.toStringData(): String {
        return mapper.writeValueAsString(this)
    }

    private suspend fun anilistAPICall(query: String): AnilistAPIResponse {
        val data = mapOf("query" to query)
        val test = app.post(apiUrl, headers = headerJSON, data = data)
        val res =
                test.parsedSafe<AnilistAPIResponse>()
                        ?: throw Exception("Unable to fetch or parse Anilist api response")
        return res
    }

    private fun AniListApi.Media.toSearchResponse(): SearchResponse {
        val title = this.title.english ?: this.title.romaji ?: ""
        val url = "$mainUrl/anime/${this.id}"
        val posterUrl = this.coverImage.large
        return newAnimeSearchResponse(title, url, TvType.Anime) { this.posterUrl = posterUrl }
    }

    private suspend fun MainPageRequest.toSearchResponseList(
            page: Int
    ): Pair<List<SearchResponse>, Boolean> {
        val res = anilistAPICall(this.data.replace("###", "$page"))
        val data =
                res.data.page?.media?.map { it.toSearchResponse() }
                        ?: throw Exception("Unable to read media data")
        val hasNextPage = res.data.page.pageInfo.hasNextPage ?: false
        return data to hasNextPage
    }

    override val mainPage =
            mainPageOf(
                    "query (\$page: Int = ###, \$sort: [MediaSort] = [TRENDING_DESC, POPULARITY_DESC], \$isAdult: Boolean = $isAdult) { Page(page: \$page, perPage: $mediaLimit) { pageInfo { total perPage currentPage lastPage hasNextPage } media(sort: \$sort, isAdult: \$isAdult, type: ANIME) { id idMal season seasonYear format episodes chapters title { english romaji } coverImage { extraLarge large medium } synonyms nextAiringEpisode { timeUntilAiring episode } } } }" to
                            "Trending Now",
                    "query (\$page: Int = ###, \$seasonYear: Int = 2024, \$sort: [MediaSort] = [TRENDING_DESC, POPULARITY_DESC], \$isAdult: Boolean = $isAdult) { Page(page: \$page, perPage: $mediaLimit) { pageInfo { total perPage currentPage lastPage hasNextPage } media(sort: \$sort, seasonYear: \$seasonYear, season: SPRING, isAdult: \$isAdult, type: ANIME) { id idMal season seasonYear format episodes chapters title { english romaji } coverImage { extraLarge large medium } synonyms nextAiringEpisode { timeUntilAiring episode } } } }" to
                            "Popular This Season",
                    "query (\$page: Int = ###, \$sort: [MediaSort] = [POPULARITY_DESC], \$isAdult: Boolean = $isAdult) { Page(page: \$page, perPage: $mediaLimit) { pageInfo { total perPage currentPage lastPage hasNextPage } media(sort: \$sort, isAdult: \$isAdult, type: ANIME) { id idMal season seasonYear format episodes chapters title { english romaji } coverImage { extraLarge large medium } synonyms nextAiringEpisode { timeUntilAiring episode } } } }" to
                            "All Time Popular",
                    "query (\$page: Int = ###, \$sort: [MediaSort] = [SCORE_DESC], \$isAdult: Boolean = $isAdult) { Page(page: \$page, perPage: $mediaLimit) { pageInfo { total perPage currentPage lastPage hasNextPage } media(sort: \$sort, isAdult: \$isAdult, type: ANIME) { id idMal season seasonYear format episodes chapters title { english romaji } coverImage { extraLarge large medium } synonyms nextAiringEpisode { timeUntilAiring episode } } } }" to
                            "Top 100 Anime",
                    "Personal" to "Personal"
            )

    override suspend fun search(query: String): List<SearchResponse>? {
        val res =
                anilistAPICall(
                        "query (\$search: String = \"$query\") { Page(page: 1, perPage: $mediaLimit) { pageInfo { total perPage currentPage lastPage hasNextPage } media(search: \$search, isAdult: $isAdult, type: ANIME) { id idMal season seasonYear format episodes chapters title { english romaji } coverImage { extraLarge large medium } synonyms nextAiringEpisode { timeUntilAiring episode } } } }"
                )
        return res.data.page?.media?.map { it.toSearchResponse() }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (request.name.contains("Personal")) {
            // Reading and manipulating personal library
            api.loginInfo()
                    ?: return newHomePageResponse(
                            "Login required for personal content.",
                            emptyList<SearchResponse>(),
                            false
                    )
            var homePageList =
                    api.getPersonalLibrary().allLibraryLists.mapNotNull {
                        if (it.items.isEmpty()) return@mapNotNull null
                        val libraryName =
                                it.name.asString(plugin.activity ?: return@mapNotNull null)
                        HomePageList("${request.name}: $libraryName", it.items)
                    }
            return newHomePageResponse(homePageList, false)
        } else {
            // Other new sections will be generated if toSearchResponseList() is
            // overridden
            val data = request.toSearchResponseList(page)
            return newHomePageResponse(request.name, data.first, data.second)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.removeSuffix("/").substringAfterLast("/")
        val data = anilistAPICall(
            "query (\$id: Int = $id) { Media(id: \$id, type: ANIME) { id title { romaji english } startDate { year } genres description averageScore bannerImage coverImage { extraLarge large medium } bannerImage episodes format nextAiringEpisode { episode } airingSchedule { nodes { episode } } recommendations { edges { node { id mediaRecommendation { id title { romaji english } coverImage { extraLarge large medium } } } } } } }"
        ).data.media ?: throw Exception("Unable to fetch media details")

        val anititle = data.getTitle()
        val aniyear = data.startDate.year
        val anitype = if (data.format!!.contains("MOVIE", ignoreCase = true)) TvType.AnimeMovie else TvType.TvSeries
        val ids = tmdbToAnimeId(anititle, aniyear, anitype)

        val jpTitle = data.title.romaji
        val animeData = if (ids.id != null) {
            val syncData = app.get("https://api.ani.zip/mappings?anilist_id=${ids.id}").toString()
            parseAnimeData(syncData)
        } else {
            null
        }
        val href=LinkData(
            malId = ids.idMal.toString(),
            aniId = ids.id.toString(),
            title = data.getTitle(),
            jpTitle = jpTitle,
            year = data.startDate.year,
            isAnime = true
        ).toStringData()

        val episodes = (1..data.totalEpisodes()).map { i ->
            val linkData = LinkData(
                malId = ids.idMal.toString(),
                aniId = ids.id.toString(),
                title = data.getTitle(),
                jpTitle = jpTitle,
                year = data.startDate.year,
                season = 1,
                episode = i,
                isAnime = true
            ).toStringData()

            newEpisode(linkData) {
                this.season = 1
                this.episode = i
                this.posterUrl = animeData?.episodes?.get(episode?.toString())?.image ?: return@newEpisode
                this.description = animeData.episodes[episode?.toString()]?.overview ?: "No summary available"
                this.rating = animeData.episodes[episode?.toString()]?.rating
                    ?.toDoubleOrNull()
                    ?.times(10)
                    ?.roundToInt()
                    ?: 0
            }
        }

        return if (data.format.contains("Movie",ignoreCase = true)) {
            newMovieLoadResponse(data.getTitle(), url, TvType.AnimeMovie, href) {
                addAniListId(id.toInt())
                this.year = data.startDate.year
                this.plot = data.description
                this.backgroundPosterUrl = animeData?.images?.firstOrNull { it.coverType == "Fanart" }?.url ?: data.bannerImage
                this.posterUrl = animeData?.images?.firstOrNull { it.coverType == "Fanart" }?.url ?: data.getCoverImage()
                this.tags = data.genres
            }
        } else {
            newAnimeLoadResponse(data.getTitle(), url, TvType.Anime) {
                addAniListId(id.toInt())
                addEpisodes(DubStatus.Subbed, episodes)
                this.year = data.startDate.year
                this.plot = data.description
                this.backgroundPosterUrl =
                    animeData?.images?.firstOrNull { it.coverType == "Fanart" }?.url
                        ?: data.bannerImage
                this.posterUrl = animeData?.images?.firstOrNull { it.coverType == "Fanart" }?.url
                    ?: data.getCoverImage()
                this.tags = data.genres
                this.recommendations = data.recommendations?.edges
                    ?.mapNotNull { edge ->
                        val recommendation = edge.node.mediaRecommendation ?: return@mapNotNull null
                        val title = recommendation.title?.english
                            ?: recommendation.title?.romaji
                            ?: "Unknown"
                        val recommendationUrl = "$mainUrl/anime/${recommendation.id}"
                        newAnimeSearchResponse(title, recommendationUrl, TvType.Anime).apply {
                            this.posterUrl = recommendation.coverImage?.large
                        }
                    }
            }
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mediaData = AppUtils.parseJson<LinkData>(data)
        Log.d("Phisher ANilist",mediaData.toJson())
        invokeExtractors(Category.ANIME, mediaData, subtitleCallback, callback)
        return true
    }

    data class AnilistAPIResponse(
            @JsonProperty("data") val data: AnilistData,
    ) {
        data class AnilistData(
                @JsonProperty("Page") val page: AnilistPage?,
                @JsonProperty("Media") val media: anilistMedia?,
        ) {
            data class AnilistPage(
                    @JsonProperty("pageInfo") val pageInfo: LikePageInfo,
                    @JsonProperty("media") val media: List<Media>,
            )
        }

        data class anilistMedia(
                @JsonProperty("id") val id: Int,
                @JsonProperty("startDate") val startDate: StartDate,
                @JsonProperty("episodes") val episodes: Int?,
                @JsonProperty("title") val title: Title,
                @JsonProperty("genres") val genres: List<String>,
                @JsonProperty("description") val description: String?,
                @JsonProperty("coverImage") val coverImage: CoverImage,
                @JsonProperty("bannerImage") val bannerImage: String?,
                @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
                @JsonProperty("airingSchedule") val airingSchedule: AiringScheduleNodes?,
                @JsonProperty("recommendations") val recommendations: RecommendationConnection?,
                @JsonProperty("format") val format: String?,
        ) {
            data class StartDate(@JsonProperty("year") val year: Int)

            data class AiringScheduleNodes(
                    @JsonProperty("nodes") val nodes: List<SeasonNextAiringEpisode>?
            )

            fun totalEpisodes(): Int {
                return nextAiringEpisode?.episode?.minus(1)
                        ?: episodes ?: airingSchedule?.nodes?.getOrNull(0)?.episode
                                ?: throw Exception("Unable to calculate total episodes")
            }

            fun getTitle(): String {
                return title.english
                        ?: title.romaji ?: throw Exception("Unable to calculate total episodes")
            }

            fun getCoverImage(): String? {
                return coverImage.extraLarge ?: coverImage.large ?: coverImage.medium
            }
        }
    }

    private suspend fun tmdbToAnimeId(title: String?, year: Int?, type: TvType): AniIds {
        if (title.isNullOrBlank()) return AniIds(null, null)

        val query = """
        query (
          ${'$'}page: Int = 1
          ${'$'}search: String
          ${'$'}sort: [MediaSort] = [POPULARITY_DESC, SCORE_DESC]
          ${'$'}type: MediaType
          ${'$'}season: MediaSeason
          ${'$'}seasonYear: Int
          ${'$'}format: [MediaFormat]
        ) {
          Page(page: ${'$'}page, perPage: 20) {
            media(
              search: ${'$'}search
              sort: ${'$'}sort
              type: ${'$'}type
              season: ${'$'}season
              seasonYear: ${'$'}seasonYear
              format_in: ${'$'}format
            ) {
              id
              idMal
            }
          }
        }
    """.trimIndent()

        val variables = mutableMapOf(
            "search" to title,
            "sort" to listOf("SEARCH_MATCH"),
            "type" to "ANIME",
            "format" to listOf(
                if (type == TvType.AnimeMovie) "MOVIE" else "TV",
                "ONA"
            )
        )

        val data = mapOf(
            "query" to query,
            "variables" to variables
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val res = app.post(apiUrl, requestBody = data)
            .parsedSafe<AniSearch>()
            ?.data
            ?.let { it.Page?.media ?: it.media }
            ?.firstOrNull()
        return AniIds(res?.id, res?.idMal)
    }
}

fun parseAnimeData(jsonString: String): AnimeData {
    val objectMapper = ObjectMapper()
    return objectMapper.readValue(jsonString, AnimeData::class.java)
}


@JsonIgnoreProperties(ignoreUnknown = true)
data class ImageData(
    @JsonProperty("coverType") val coverType: String?,
    @JsonProperty("url") val url: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpisodeData(
    @JsonProperty("episode") val episode: String?,
    @JsonProperty("airdate") val airdate: String?,
    @JsonProperty("airDate") val airDate: String?,
    @JsonProperty("airDateUtc") val airDateUtc: String?,
    @JsonProperty("length") val length: Int?,
    @JsonProperty("runtime") val runtime: Int?,
    @JsonProperty("image") val image: String?,
    @JsonProperty("title") val title: Map<String, String>?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("rating") val rating: String?,
    @JsonProperty("finaleType") val finaleType: String?
)


@JsonIgnoreProperties(ignoreUnknown = true)
data class AnimeData(
    @JsonProperty("titles") val titles: Map<String, String>? = null,
    @JsonProperty("images") val images: List<ImageData>? = null,
    @JsonProperty("episodes") val episodes: Map<String, EpisodeData>? = null,
)

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

data class AniIds(var id: Int? = null, var idMal: Int? = null)