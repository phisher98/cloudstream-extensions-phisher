package com.Anichi

import android.annotation.SuppressLint
import com.Anichi.AnichiExtractors.invokeInternalSources
import com.Anichi.AnichiParser.AnichiLoadData
import com.Anichi.AnichiParser.AnichiQuery
import com.Anichi.AnichiParser.Detail
import com.Anichi.AnichiParser.Edges
import com.Anichi.AnichiParser.JikanResponse
import com.Anichi.AnichiUtils.aniToMal
import com.Anichi.AnichiUtils.getTracker
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorRole
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.addDub
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.addSub
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.RequestBodyTypes
import com.phisher98.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Calendar

open class Anichi : MainAPI() {
    override var name = "Anichi"
    override val instantLinkLoading = true
    override val hasQuickSearch = true
    override val hasMainPage = true

    private fun getStatus(t: String): ShowStatus {
        return when (t) {
            "Finished" -> ShowStatus.Completed
            "Releasing" -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }

    override val supportedSyncNames = setOf(SyncIdName.Anilist, SyncIdName.MyAnimeList)
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    private val popularTitle = "Popular"
    private val animeRecentTitle = "Latest Anime"
    private val donghuaRecentTitle = "Latest Donghua"
    private val movieTitle = "Movie"
    val calendar: Calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1

    val season = when (month) {
        in 1..3 -> "Winter"
        in 4..6 -> "Spring"
        in 7..9 -> "Summer"
        else -> "Fall"
    }

    @SuppressLint("NewApi")
    override val mainPage = mainPageOf(
        """$apiUrl?variables={"search":{"season":"$season","year":$year},"limit":26,"page":%d,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$maipageshaHash"}}""" to "New Series",
        """$apiUrl?variables={"search":{},"limit":26,"page":%d,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$maipageshaHash"}}""" to animeRecentTitle,
        """$apiUrl?variables={"search":{},"limit":26,"page":%d,"translationType":"sub","countryOrigin":"CN"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$maipageshaHash"}}""" to donghuaRecentTitle,
        """$apiUrl?variables={"type":"anime","size":30,"dateRange":1,"page":%d,"allowAdult":${settingsForProvider.enableAdult},"allowUnknown":false}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$popularHash"}}""" to popularTitle,
        """$apiUrl?variables={"search":{"types":["Movie"]},"limit":26,"page":%d,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$maipageshaHash"}}""" to movieTitle,
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val url = if (request.data.contains("%d")) {
            request.data.format(page)
        } else {
            request.data
        }

        val res = fetchQuery(url)?.data
        val query = res?.shows ?: res?.queryPopular ?: res?.queryListForTag

        val card =
            if (request.name == popularTitle) query?.recommendations?.map { it.anyCard }
            else query?.edges

        val home = card
            ?.filter {
                // filtering in case there is an anime with 0 episodes available on the // site.
                !(it?.availableEpisodes?.raw == 0 &&
                        it.availableEpisodes.sub == 0 &&
                        it.availableEpisodes.dub == 0)
            }
            ?.mapNotNull { media -> media?.toSearchResponse() }
            ?: emptyList()

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
            ),
            hasNext = home.isNotEmpty() // better pagination handling
        )
    }

    private suspend fun fetchQuery(url: String): AnichiQuery? =
        app.get(url, headers = headers).parsedSafe()

    private fun getPosterUrl(thumbnail: String?): String? {
        return thumbnail?.let {
            if (it.startsWith("http")) it
            else "https://wp.youtube-anime.com/aln.youtube-anime.com/$it"
        }
    }

    private fun Edges.toSearchResponse(): AnimeSearchResponse? {
        return newAnimeSearchResponse(
                name ?: englishName ?: nativeName ?: "",
                Id ?: return null,
                fix = false
        ) {
            this.posterUrl = getPosterUrl(thumbnail)
            this.year = airedStart?.year
            this.otherName = englishName
            addDub(availableEpisodes?.dub)
            addSub(availableEpisodes?.sub)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(
        withContext(
            Dispatchers.IO
        ) {
            URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        },1
    )?.items

    override suspend fun search(query: String,page: Int): SearchResponseList? {
        val encodedQuery = withContext(Dispatchers.IO) {
            URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        }
        val link =
                """$apiUrl?variables={"search":{"query":"$encodedQuery"},"limit":26,"page":$page,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$maipageshaHash"}}"""
        val responseText = app.get(link, headers = headers).text
            .takeUnless { it.contains("PERSISTED_QUERY_NOT_FOUND") }
            ?: return null
        val response = parseJson<AnichiQuery>(responseText)

        val results =
                response.data?.shows?.edges?.filter {
                    // filtering in case there is an anime with 0 episodes available on the site.
                    !(it.availableEpisodes?.raw == 0 &&
                            it.availableEpisodes.sub == 0 &&
                            it.availableEpisodes.dub == 0)
                }

        return results?.mapNotNull { it.toSearchResponse() }?.toNewSearchResponseList()
    }

    override suspend fun getLoadUrl(name: SyncIdName, id: String): String? {
        val syncId = id.split("/").last()
        val malId =
                if (name == SyncIdName.MyAnimeList) {
                    syncId
                } else {
                    aniToMal(syncId)
                }
        val media = app.get("$jikanApi/anime/$malId").parsedSafe<JikanResponse>()?.data
        val link = """$apiUrl?variables={"search":{"allowAdult":false,"allowUnknown":false,"query":"${media?.title}"},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$mainHash"}}"""
        val res = fetchQuery(link)?.data?.shows?.edges
        return res
                ?.find {
                    (it.name.equals(media?.title, true) ||
                            it.englishName.equals(media?.title_english, true) ||
                            it.nativeName.equals(media?.title_japanese, true)) &&
                            it.airedStart?.year == media?.year
                }
                ?.Id
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url.substringAfterLast("/")

        // lazy to format
        val body =
                """
        {
            "query": "                        query(\n                      ${'$'}_id: String!\n                    ) {\n                      show(\n                        _id: ${'$'}_id\n                      ) {\n                          _id\n                          name\n                          description\n                          thumbnail\n                          thumbnails\n                          lastEpisodeInfo\n                          lastEpisodeDate       \n                          type\n                          genres\n                          score\n                          status\n                          season\n                          altNames  \n                          averageScore\n                          rating\n                          episodeCount\n                          episodeDuration\n                          broadcastInterval\n                          banner\n                          airedEnd\n                          airedStart \n                          studios\n                          characters\n                          availableEpisodesDetail\n                          availableEpisodes\n                          prevideos\n                          nameOnlyString\n                          relatedShows\n                          relatedMangas\n                          musics\n                          isAdult\n                          \n                          tags\n                          countryOfOrigin\n\n                          pageStatus{\n                            _id\n                            notes\n                            pageId\n                            showId\n                            \n                              # ranks:[Object]\n    views\n    likesCount\n    commentCount\n    dislikesCount\n    reviewCount\n    userScoreCount\n    userScoreTotalValue\n    userScoreAverValue\n    viewers{\n        firstViewers{\n          viewCount\n          lastWatchedDate\n        user{\n          _id\n          displayName\n          picture\n          # description\n          hideMe\n          # createdAt\n          # badges\n          brief\n        }\n      \n      }\n      recViewers{\n        viewCount\n          lastWatchedDate\n        user{\n          _id\n          displayName\n          picture\n          # description\n          hideMe\n          # createdAt\n          # badges\n          brief\n        }\n      \n      }\n      }\n\n                        }\n                      }\n                    }",
            "extensions": "{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"$detailHash\"}}",
            "variables": "{\"_id\":\"$id\"}"
        }
    """
                        .trimIndent()
                        .trim()
                        .toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        val res = app.post(apiUrl, requestBody = body, headers = headers)
        val showData = res.parsedSafe<Detail>()?.data?.show ?: return null

        val title = showData.name
        val description = showData.description

        val trackers =
                getTracker(
                        title,
                        showData.altNames?.firstOrNull(),
                        showData.airedStart?.year,
                        showData.season?.quarter,
                        showData.type
                )

        val (data, animeMetadata) = coroutineScope {
            val anilistDeferred = async {
                trackers?.id?.let { aniId ->
                    anilistAPICall(
                        "query { Media(id: $aniId, type: ANIME) { id title { romaji english } startDate { year } genres description averageScore status bannerImage coverImage { extraLarge large medium } episodes format nextAiringEpisode { episode } airingSchedule { nodes { episode } } recommendations { edges { node { id mediaRecommendation { id title { romaji english } coverImage { extraLarge large medium } } } } } } }"
                    ).data.media
                }
            }
            val metadataDeferred = async {
                trackers?.idMal?.let { malId ->
                    parseAnimeData(app.get("https://api.ani.zip/mappings?mal_id=$malId").text)
                }
            }
            anilistDeferred.await() to metadataDeferred.await()
        }
        val fanart = animeMetadata?.images
            ?.firstOrNull { it.coverType.equals("Fanart", ignoreCase = true) }
            ?.url

        val backgroundposter = fanart ?: data?.bannerImage ?: trackers?.coverImage?.large

        val logotvType = if (showData.type?.contains("movie", ignoreCase = true) == true) TvType.AnimeMovie else TvType.Anime

        val tmdbid = animeMetadata?.mappings?.themoviedbId?.toIntOrNull()

        val logoUrl = fetchTmdbLogoUrl(
            tmdbAPI = "https://api.themoviedb.org/3",
            apiKey = "98ae14df2b8d8f8f8136499daf79f0e0",
            type = logotvType,
            tmdbId = tmdbid,
            appLangCode = "en"
        )

        val poster = showData.thumbnail
        fun buildEpisodes(episodeNumbers: List<String>, dubStatus: String) = episodeNumbers.map { eps ->
            val epNum = eps.toIntOrNull()
            val meta = epNum?.let { animeMetadata?.episodes?.get(it.toString()) }
            newEpisode(
                AnichiLoadData(id, dubStatus, eps, trackers?.idMal).toJson()
            ) {
                this.episode = epNum
                this.name = meta?.title?.get("en") ?: meta?.title?.get("ja") ?: meta?.title?.get("x-jat") ?: "Episode $eps"
                this.score = Score.from10(meta?.rating)
                this.posterUrl = meta?.image ?: showData.thumbnail
                this.description = meta?.overview ?: "No summary available"
                this.addDate(meta?.airDateUtc)
                this.runTime = meta?.runtime
            }
        }
        val episodes = showData.availableEpisodesDetail?.let { detail ->
            Pair(buildEpisodes(detail.sub, "sub").reversed(), buildEpisodes(detail.dub, "dub").reversed())
        }
        val (subEpisodes, dubEpisodes) = episodes ?: Pair(emptyList(), emptyList())
        val characters =
                showData.characters?.map {
                    val role =
                            when (it.role) {
                                "Main" -> ActorRole.Main
                                "Supporting" -> ActorRole.Supporting
                                "Background" -> ActorRole.Background
                                else -> null
                            }
                    val name = it.name?.full ?: it.name?.native ?: ""
                    val image = it.image?.large ?: it.image?.medium
                    Pair(Actor(name, image), role)
                }

        val tvType = if (showData.type?.contains("movie", ignoreCase = true) == true) TvType.AnimeMovie else TvType.Anime

        return newAnimeLoadResponse(title ?: "", url, tvType) {
            this.engName = showData.altNames?.firstOrNull()
            this.posterUrl = poster ?: trackers?.coverImage?.extraLarge ?: trackers?.coverImage?.large
            this.backgroundPosterUrl = backgroundposter ?: trackers?.coverImage?.extraLarge
            try { this.logoUrl = logoUrl } catch(_:Throwable){}
            this.score = Score.from100(showData.averageScore)
            this.tags = showData.genres
            this.year = showData.airedStart?.year
            this.duration = showData.episodeDuration?.div(60_000)
            addTrailer(
                    showData.prevideos.filter { it.isNotBlank() }.map {
                        "https://www.youtube.com/watch?v=$it"
                    }
            )
            addEpisodes(DubStatus.Subbed, subEpisodes)
            addEpisodes(DubStatus.Dubbed, dubEpisodes)
            addActors(characters)
            // this.recommendations = recommendations

            showStatus = getStatus(showData.status.toString())
            addMalId(trackers?.idMal)
            addAniListId(trackers?.id)
            plot = description?.replace(Regex("""<(.*?)>"""), "")
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {

        val loadData = parseJson<AnichiLoadData>(data)
        invokeInternalSources(
                loadData.hash,
                loadData.dubStatus,
                loadData.episode,
                subtitleCallback,
                callback
        )

        return true
    }

    companion object {
        const val apiUrl = BuildConfig.ANICHI_API
        //const val serverUrl = BuildConfig.ANICHI_SERVER
        const val apiEndPoint = BuildConfig.ANICHI_ENDPOINT

        const val anilistApi = "https://graphql.anilist.co"
        const val jikanApi = "https://api.jikan.moe/v4"

        private const val mainHash = "e42a4466d984b2c0a2cecae5dd13aa68867f634b16ee0f17b380047d14482406"
        private const val popularHash = "60f50b84bb545fa25ee7f7c8c0adbf8f5cea40f7b1ef8501cbbff70e38589489"
        //private const val slugHash = "bf603205eb2533ca21d0324a11f623854d62ed838a27e1b3fcfb712ab98b03f4"
        private const val detailHash = "bb263f91e5bdd048c1c978f324613aeccdfe2cbc694a419466a31edb58c0cc0b"
        const val serverHash = "d405d0edd690624b66baba3068e0edc3ac90f1597d898a1ec8db4e5c43c00fec"
        const val maipageshaHash="a24c500a1b765c68ae1d8dd85174931f661c71369c89b92b88b75a725afc471c"
        val headers =
                mapOf(
                        "app-version" to "android_c-247",
                        "from-app" to BuildConfig.ANICHI_APP,
                        "platformstr" to "android_c",
                        "Referer" to "https://allmanga.to"
                )
    }
}
