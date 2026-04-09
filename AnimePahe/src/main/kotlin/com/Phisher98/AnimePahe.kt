package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.mvvm.safeAsync
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.Jsoup
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class AnimePahe : MainAPI() {
    companion object {
        val headers = mapOf("Cookie" to "__ddg2_=1234567890")
        private const val Proxy="https://animepaheproxy.phisheranimepahe.workers.dev/?url="
        //var cookies: Map<String, String> = mapOf()
        private fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie")) TvType.AnimeMovie
            else TvType.Anime
        }
    }

    override var mainUrl = AnimePaheProviderPlugin.currentAnimepaheServer
    override var name = "AnimePahe"
    override val hasQuickSearch = false
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    override val mainPage =
        listOf(MainPageData("Latest Releases", "$Proxy$mainUrl/api?m=airing&page=", true))

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        data class Data(
//            @JsonProperty("id") val id: Int,
//            @JsonProperty("anime_id") val animeId: Int,
            @JsonProperty("anime_title") val animeTitle: String,
//            @JsonProperty("anime_slug") val animeSlug: String,
            @JsonProperty("episode") val episode: Int?,
            @JsonProperty("snapshot") val snapshot: String?,
            @JsonProperty("created_at") val createdAt: String?,
            @JsonProperty("anime_session") val animeSession: String,
        )

        data class AnimePaheLatestReleases(
            @JsonProperty("total") val total: Int,
            @JsonProperty("data") val data: List<Data>
        )
        val response = app.get(request.data + page, headers = headers).text
        val episodes = parseJson<AnimePaheLatestReleases>(response).data.map {
            newAnimeSearchResponse(
                it.animeTitle,
                LoadData(it.animeSession, unixTime, it.animeTitle).toJson(),
                fix = false
            ) {
                this.posterUrl = it.snapshot
                addDubStatus(DubStatus.Subbed, it.episode)
            }
        }
        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = episodes,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    data class AnimePaheSearchData(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("title") val title: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("episodes") val episodes: Int?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("season") val season: String?,
        @JsonProperty("year") val year: Int?,
        @JsonProperty("score") val score: Double?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("session") val session: String,
        @JsonProperty("relevance") val relevance: String?
    )

    data class AnimePaheSearch(
        @JsonProperty("total") val total: Int,
        @JsonProperty("data") val data: List<AnimePaheSearchData>
    )


    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$Proxy$mainUrl/api?m=search&l=8&q=$query"
        val headers = mapOf("referer" to "$mainUrl/","Cookie" to "__ddg2_=1234567890")

        val req = app.get(url, headers = headers).text
        val data = parseJson<AnimePaheSearch>(req)

        return data.data.map {
            newAnimeSearchResponse(
                it.title,
                LoadData(it.session, unixTime, it.title).toJson(),
                fix = false
            ) {
                this.posterUrl = it.poster
                addDubStatus(DubStatus.Subbed, it.episodes)
            }
        }
    }

    private data class AnimeData(
        @JsonProperty("id") val id: Int,
        @JsonProperty("anime_id") val animeId: Int,
        @JsonProperty("episode") val episode: Int,
        @JsonProperty("title") val title: String,
        @JsonProperty("snapshot") val snapshot: String,
        @JsonProperty("session") val session: String,
        @JsonProperty("filler") val filler: Int,
        @JsonProperty("created_at") val createdAt: String
    )

    private data class AnimePaheAnimeData(
        @JsonProperty("total") val total: Int,
        @JsonProperty("per_page") val perPage: Int,
        @JsonProperty("current_page") val currentPage: Int,
        @JsonProperty("last_page") val lastPage: Int,
        @JsonProperty("next_page_url") val nextPageUrl: String?,
        @JsonProperty("prev_page_url") val prevPageUrl: String?,
        @JsonProperty("from") val from: Int,
        @JsonProperty("to") val to: Int,
        @JsonProperty("data") val data: List<AnimeData>
    )

    data class LinkLoadData(
        @JsonProperty("mainUrl") val mainUrl: String,
        @JsonProperty("is_play_page") val is_play_page: Boolean,
        @JsonProperty("episode_num") val episode_num: Int,
        @JsonProperty("page") val page: Int,
        @JsonProperty("session") val session: String,
        @JsonProperty("episode_session") val episode_session: String,
    ) {
        private val headers = mapOf("Cookie" to "__ddg2_=1234567890")
        suspend fun getUrl(): String? {
            return if (is_play_page) {
                "$Proxy$mainUrl/play/${session}/${episode_session}"
            } else {
                val url = "$Proxy$mainUrl/api?m=release&id=${session}&sort=episode_asc&page=${page + 1}"
                val jsonResponse = app.get(url,headers=headers).parsedSafe<AnimePaheAnimeData>() ?: return null
                val episode = jsonResponse.data.firstOrNull { it.episode == episode_num }?.session
                    ?: return null
                "$Proxy$mainUrl/play/${session}/${episode}"
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun generateListOfEpisodes(
        session: String,
        metaEpisodes: Map<String, MetaEpisode>?
    ): ArrayList<Episode> {
        val episodes = ArrayList<Episode>()
        val semaphore = Semaphore(5) // Limit to 5 concurrent requests (adjust based on server capability)

        try {
            val uri = "https://animepaheproxy.phisheranimepahe.workers.dev/?url=$mainUrl/api?m=release&id=$session&sort=episode_asc&page=1"
            val req = app.get(uri, headers = headers).text
            val data = parseJson<AnimePaheAnimeData>(req)

            val lastPage = data.lastPage
            val perPage = data.perPage
            val total = data.total
            var currentEpisode = 1

            fun getEpisodeTitle(episodeData: AnimeData): String {
                return episodeData.title.ifEmpty { "Episode ${episodeData.episode}" }
            }
            // If only one page, process all episodes in that page
            if (lastPage == 1 && perPage > total) {
                data.data.forEach { episodeData ->
                    val epNum = episodeData.episode.toString()
                    val meta = metaEpisodes?.get(epNum)
                    episodes.add(
                        newEpisode(
                            LinkLoadData(
                                mainUrl,
                                true,
                                0,
                                0,
                                session,
                                episodeData.session
                            ).toJson()
                        ) {
                            addDate(episodeData.createdAt)
                            this.name = meta?.title?.get("en") ?: getEpisodeTitle(episodeData)
                            this.posterUrl = meta?.image ?: episodeData.snapshot
                            this.description = meta?.overview
                            this.score = Score.from10(meta?.rating)
                            this.runTime = meta?.runtime
                        }
                    )
                }
            } else {
                // Fetch multiple pages concurrently with limited threads
                val deferredResults = (1..lastPage).map { page ->
                    GlobalScope.async {
                        semaphore.withPermit {
                            try {
                                val pageUri = "$mainUrl/api?m=release&id=$session&sort=episode_asc&page=$page"
                                val pageReq = app.get(pageUri, headers = headers).text
                                val pageData = parseJson<AnimePaheAnimeData>(pageReq)
                                pageData.data.map { episodeData ->
                                    val epNum = episodeData.episode.toString()
                                    val meta = metaEpisodes?.get(epNum)
                                    newEpisode(
                                        LinkLoadData(
                                            mainUrl,
                                            true,
                                            currentEpisode++,
                                            page,
                                            session,
                                            episodeData.session
                                        ).toJson()
                                    ) {
                                        addDate(episodeData.createdAt)
                                        this.name = meta?.title?.get("en") ?: getEpisodeTitle(episodeData)
                                        this.posterUrl = meta?.image ?: episodeData.snapshot
                                        this.description = meta?.overview
                                        this.score = Score.from10(meta?.rating)
                                        this.runTime = meta?.runtime
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("generateListOfEpisodes", "Error on page $page: ${e.message}")
                                emptyList<Episode>()
                            }
                        }
                    }
                }

                // Wait for all pages to load and combine results
                episodes.addAll(deferredResults.awaitAll().flatten())
            }

        } catch (e: Exception) {
            Log.e("generateListOfEpisodes", "Error generating episodes: ${e.message}")
        }
        return episodes
    }
    /**
     * Required to make bookmarks work with a session system
     **/
    data class LoadData(val session: String, val sessionDate: Long, val name: String)

    override suspend fun load(url: String): LoadResponse? {
        return safeAsync {
            val session = parseJson<LoadData>(url).let { data ->
                // Outdated
                if (data.sessionDate + 60 * 10 < unixTime) {
                    parseJson<LoadData>(
                        search(data.name).firstOrNull()?.url ?: return@let null
                    ).session
                } else {
                    data.session
                }
            } ?: return@safeAsync null
            val html = app.get("$Proxy$mainUrl/anime/$session",headers=headers).text
            val doc = Jsoup.parse(html)
            val japTitle = doc.selectFirst("h2.japanese")?.text()
            val animeTitle = doc.selectFirst("span.sr-only.unselectable")?.text()
            val poster = doc.selectFirst(".anime-poster a")?.attr("href")

            val tvType = doc.selectFirst("""a[href*="/anime/type/"]""")?.text()

            val recommendations = doc.select("div.anime-recommendation div.row").mapNotNull { it ->
                val title = it.select("a").attr("title")
                val rawHref = it.select("a").attr("href")

                val session = rawHref.substringAfter("/anime/", "")
                    .takeIf { it.isNotBlank() } ?: return@mapNotNull null

                val json = LoadData(
                    session = session,
                    name = title,
                    sessionDate = unixTime
                ).toJson()

                val posterurl = it.select("img").attr("data-src").ifEmpty {
                    it.select("img").attr("src")
                }

                newMovieSearchResponse(title, json, TvType.TvSeries) {
                    this.posterUrl = posterurl
                }
            }

            val year = Regex("""<strong>Aired:</strong>[^,]*, (\d+)""")
                .find(html)?.destructured?.component1()
                ?.toIntOrNull()

            val status =
                if (doc.selectFirst("a[href='/anime/airing']") != null)
                    ShowStatus.Ongoing
                else if (doc.selectFirst("a[href='/anime/completed']") != null)
                    ShowStatus.Completed
                else null

            val synopsis = doc.selectFirst(".anime-synopsis")?.text()

            var anilistId: Int? = null
            var malId: Int? = null

            doc.select(".external-links > a").forEach { aTag ->
                val split = aTag.attr("href").split("/")

                if (aTag.attr("href").contains("anilist.co")) {
                    anilistId = split[split.size - 1].toIntOrNull()
                } else if (aTag.attr("href").contains("myanimelist.net")) {
                    malId = split[split.size - 1].toIntOrNull()
                }
            }

            val syncMetaData = app.get("https://api.ani.zip/mappings?mal_id=$malId").text
            val animeMetaData = parseAnimeData(syncMetaData)
            val backgroundposter = animeMetaData?.images?.find { it.coverType == "Fanart" }?.url

            val episodes = generateListOfEpisodes(session, animeMetaData?.episodes)

            newAnimeLoadResponse(animeTitle ?: japTitle ?: "", url, getType(tvType.toString())) {
                engName = animeTitle
                japName = japTitle
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundposter ?: poster
                this.year = year
                addEpisodes(DubStatus.Subbed, episodes)
                this.showStatus = status
                plot = synopsis
                tags = if (!doc.select(".anime-genre > ul a").isEmpty()) {
                    ArrayList(doc.select(".anime-genre > ul a").map { it.text() })
                } else {
                    null
                }
                this.recommendations = recommendations
                addMalId(malId)
                addAniListId(anilistId)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsed = parseJson<LinkLoadData>(data)
        val episodeUrl = parsed.getUrl() ?: ""
        val document= app.get(episodeUrl, headers= headers).document
        document.select("#resolutionMenu button")
            .map {
                val dubText = it.select("span").text().lowercase()
                val type = if ("eng" in dubText) "DUB" else "SUB"

                val qualityRegex = Regex("""(.+?)\s+·\s+(\d{3,4}p)""")
                val text = it.text()
                val match = qualityRegex.find(text)
                val source = match?.groupValues?.getOrNull(1)?.trim() ?: "Unknown"
                val quality = match?.groupValues?.getOrNull(2)?.substringBefore("p")?.toIntOrNull()
                    ?: Qualities.Unknown.value

                val href = it.attr("data-src")
                if ("kwik" in href) {
                    loadCustomExtractor(
                        "Animepahe $source [$type]",
                        href,
                        "",
                        subtitleCallback,
                        callback,
                        quality
                    )
                }
            }


        document.select("div#pickDownload > a").amap {
            val qualityRegex = Regex("""(.+?)\s+·\s+(\d{3,4}p)""")
            val href = it.attr("href")
            var type = "SUB"
            if(it.select("span").text().contains("eng"))
                type="DUB"
            val text = it.text()
            val match = qualityRegex.find(text)
            val source = match?.groupValues?.getOrNull(1) ?: "Unknown"
            val quality = match?.groupValues?.getOrNull(2)?.substringBefore("p") ?: "Unknown"

            loadCustomExtractor(
                "Animepahe Pahe $source [$type]",
                href,
                "",
                subtitleCallback,
                callback,
                quality.toIntOrNull()
            )
        }
        return true
    }
}
