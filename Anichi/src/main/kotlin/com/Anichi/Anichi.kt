package com.Anichi

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.Anichi.AnichiExtractors.invokeInternalSources
import com.Anichi.AnichiParser.AnichiLoadData
import com.Anichi.AnichiParser.AnichiQuery
import com.Anichi.AnichiParser.Detail
import com.Anichi.AnichiParser.Edges
import com.Anichi.AnichiParser.JikanResponse
import com.Anichi.AnichiUtils.aniToMal
import com.Anichi.AnichiUtils.getTracker
import com.lagradost.api.Log
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
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
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
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.RequestBodyTypes
import com.phisher98.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import kotlin.math.roundToInt

open class Anichi : MainAPI() {
    override var name = "Anichi"
    override val instantLinkLoading = true
    override val hasQuickSearch = false
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
    @RequiresApi(Build.VERSION_CODES.O)
    val currentYear = LocalDate.now().year  // Get the current year
    @SuppressLint("NewApi")
    override val mainPage = mainPageOf(
        """$apiUrl?variables={"search":{"season":"Spring","year":$currentYear},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$maipageshaHash"}}""" to "New Series",
        """$apiUrl?variables={"search":{},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$maipageshaHash"}}""" to animeRecentTitle,
        """$apiUrl?variables={"search":{},"limit":26,"page":1,"translationType":"sub","countryOrigin":"CN"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$maipageshaHash"}}""" to donghuaRecentTitle,
        """$apiUrl?variables={"type":"anime","size":30,"dateRange":1,"page":%d,"allowAdult":${settingsForProvider.enableAdult},"allowUnknown":false}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$popularHash"}}""" to popularTitle,
        """$apiUrl?variables={"search":{"types":["Movie"]},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$maipageshaHash"}}""" to movieTitle,
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val url = request.data.format(page)
        val res = app.get(url, headers = headers).parsedSafe<AnichiQuery>()?.data
        val query = res?.shows ?: res?.queryPopular ?: res?.queryListForTag
        val card =
                if (request.name == popularTitle) query?.recommendations?.map { it.anyCard }
                else query?.edges
        val home =
                card
                        ?.filter {
                            // filtering in case there is an anime with 0 episodes available on the
                            // site.
                            !(it?.availableEpisodes?.raw == 0 &&
                                    it.availableEpisodes.sub == 0 &&
                                    it.availableEpisodes.dub == 0)
                        }
                        ?.mapNotNull { media -> media?.toSearchResponse() }
                        ?: emptyList()
        return newHomePageResponse(
                list =
                        HomePageList(
                                name = request.name,
                                list = home,
                        ),
                hasNext = request.name != movieTitle
        )
    }

    private fun Edges.toSearchResponse(): AnimeSearchResponse? {
        val posterUrl = if (thumbnail?.startsWith("http") == true) thumbnail else "https://wp.youtube-anime.com/aln.youtube-anime.com/$thumbnail"
        return newAnimeSearchResponse(
                name ?: englishName ?: nativeName ?: "",
                Id ?: return null,
                fix = false
        ) {
            this.posterUrl = posterUrl
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
        }
    )

    override suspend fun search(query: String): List<SearchResponse>? {
        val encodedQuery = withContext(Dispatchers.IO) {
            URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        }
        val link =
                """$apiUrl?variables={"search":{"query":"$encodedQuery"},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$maipageshaHash"}}"""
        val res =
                app.get(link, headers = headers).text.takeUnless {
                    it.contains("PERSISTED_QUERY_NOT_FOUND")
                }
                // Retries
                ?: app.get(link, headers = headers).text.takeUnless {
                            it.contains("PERSISTED_QUERY_NOT_FOUND")
                        }
                                ?: return emptyList()

        val response = parseJson<AnichiQuery>(res)

        val results =
                response.data?.shows?.edges?.filter {
                    // filtering in case there is an anime with 0 episodes available on the site.
                    !(it.availableEpisodes?.raw == 0 &&
                            it.availableEpisodes.sub == 0 &&
                            it.availableEpisodes.dub == 0)
                }

        return results?.map {
            val posterUrl = if (it.thumbnail?.startsWith("http") == true) it.thumbnail else "https://wp.youtube-anime.com/aln.youtube-anime.com/${it.thumbnail}"
            newAnimeSearchResponse(it.name ?: "", "${it.Id}", fix = false) {
                this.posterUrl = posterUrl
                this.year = it.airedStart?.year
                this.otherName = it.englishName
                addDub(it.availableEpisodes?.dub)
                addSub(it.availableEpisodes?.sub)
            }
        }
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
        val res = app.get(link, headers = headers).parsedSafe<AnichiQuery>()?.data?.shows?.edges
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
        val syncData = app.get("https://api.ani.zip/mappings?mal_id=${trackers?.idMal}").toString()
        val animeData = parseAnimeData(syncData)
        val poster = animeData?.images?.firstOrNull { it.coverType == "Fanart" }?.url ?: showData.thumbnail
        val episodes = showData.availableEpisodesDetail?.let { detail ->
            val id = showData.Id ?: return@let null

            val sub = detail.sub.map { eps ->
                newEpisode(
                    AnichiLoadData(id, "sub", eps, trackers?.idMal).toJson()
                ) {
                    episode = eps.toIntOrNull()
                    this.name = animeData?.episodes?.get(episode?.toString())?.title?.get("en")
                    this.rating = animeData?.episodes?.get(episode?.toString())?.rating
                        ?.toDoubleOrNull()
                        ?.times(10)
                        ?.roundToInt()
                        ?: 0
                    this.posterUrl = animeData?.episodes?.get(episode?.toString())?.image
                        ?: return@newEpisode
                    this.description = animeData.episodes[episode?.toString()]?.overview
                        ?: "No summary available"
                }
            }

            val dub = detail.dub.map { eps ->
                newEpisode(
                    AnichiLoadData(id, "dub", eps, trackers?.idMal).toJson()
                ) {
                    episode = eps.toIntOrNull()
                    this.name = animeData?.episodes?.get(episode?.toString())?.title?.get("en")
                    this.rating = animeData?.episodes?.get(episode?.toString())?.rating
                        ?.toDoubleOrNull()
                        ?.times(10)
                        ?.roundToInt()
                        ?: 0
                    this.posterUrl = animeData?.episodes?.get(episode?.toString())?.image
                        ?: return@newEpisode
                    this.description = animeData.episodes[episode?.toString()]?.overview
                        ?: "No summary available"
                }
            }
            Pair(sub.reversed(), dub.reversed())
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

        return newAnimeLoadResponse(title ?: "", url, TvType.Anime) {
            engName = showData.altNames?.firstOrNull()
            posterUrl = poster ?: trackers?.coverImage?.extraLarge ?: trackers?.coverImage?.large
            rating = showData.averageScore?.times(100)
            tags = showData.genres
            year = showData.airedStart?.year
            duration = showData.episodeDuration?.div(60_000)
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

        private const val mainHash =
                "e42a4466d984b2c0a2cecae5dd13aa68867f634b16ee0f17b380047d14482406"
        private const val popularHash =
                "31a117653812a2547fd981632e8c99fa8bf8a75c4ef1a77a1567ef1741a7ab9c"
        //private const val slugHash = "bf603205eb2533ca21d0324a11f623854d62ed838a27e1b3fcfb712ab98b03f4"
        private const val detailHash =
                "bb263f91e5bdd048c1c978f324613aeccdfe2cbc694a419466a31edb58c0cc0b"
        const val serverHash = "5f1a64b73793cc2234a389cf3a8f93ad82de7043017dd551f38f65b89daa65e0"
        const val maipageshaHash="06327bc10dd682e1ee7e07b6db9c16e9ad2fd56c1b769e47513128cd5c9fc77a"
        val headers =
                mapOf(
                        "app-version" to "android_c-247",
                        "from-app" to BuildConfig.ANICHI_APP,
                        "platformstr" to "android_c",
                        "Referer" to "https://allmanga.to"
                )
    }
}
