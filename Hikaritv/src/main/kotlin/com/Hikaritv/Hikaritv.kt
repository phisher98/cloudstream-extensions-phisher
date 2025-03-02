package com.hikaritv

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Hikaritv : MainAPI() {
    override var mainUrl = "https://watch.hikaritv.xyz"
    override var name = "HikariTV"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val supportedSyncNames = setOf(
        SyncIdName.MyAnimeList,
    )

    override val mainPage = mainPageOf(
        "${mainUrl}/recently-updated" to "Latest Episode",
        "ajax/getfilter?type=2&sort=default&page=" to "Movies",
        "ajax/getfilter?stats=2&sort=default&page=" to "Finished Airing",
        "ajax/getfilter?sort=score&page=" to "Most Popular",
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document:Document
        if (request.data.startsWith(mainUrl))
        {
            document= app.get(request.data).document
        }
        else {
            val res = app.get("$mainUrl/${request.data}$page").parsedSafe<HomePage>()?.html ?: ""
            document = Jsoup.parse(res)
        }
        val home =
            document.select("div.flw-item").mapNotNull {
                it.toSearchResult()
            }
        return newHomePageResponse(request.name, home)
    }

    private fun getProperLink(uri: String): String {
        return when {
            uri.contains("/episodes/") -> {
                var title = uri.substringAfter("$mainUrl/episodes/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvshows/$title"
            }
            uri.contains("/seasons/") -> {
                var title = uri.substringAfter("$mainUrl/seasons/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvshows/$title"
            }
            else -> {
                uri
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3 a")?.text() ?: return null
        val href = getProperLink(fixUrl(this.selectFirst("h3 > a")!!.attr("href")))
        val posterUrl = fixUrlNull(this.select("img").last()?.getImageAttr())
        val quality = getQualityFromString(this.select("span.quality").text())
        return newMovieSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            this.quality = quality
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?keyword=$query").document
        return document.select("#main-wrapper div.tab-content div.flw-item").map {
            val title = it.selectFirst("h3 a")?.text() ?: "Unknown"
            val href = getProperLink(fixUrl(it.selectFirst("h3 > a")!!.attr("href")))
            val posterlink=fixUrlNull(it.select("img").last()?.getImageAttr())
            newMovieSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterlink
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val animeId = url.substringAfter("/anime/").substringBefore("/").toIntOrNull()
        val response = app.get(url)
        val document = response.document
        val animeTitle = document.selectFirst("h2.film-name.dynamic-name")?.text()?.trim().orEmpty()
        val posterUrl = document.select("div.anis-cover").attr("style").substringAfter("(").substringBefore(")")
        val genreTags = document.select("div.sgeneros > a").map { it.text() }
        val animeDuration = document.selectFirst("span.item-head:contains(Duration:)")?.nextElementSibling()?.text()?.substringBefore("per")?.trim()
        val releaseYear = document.selectFirst("span.item-head:contains(Aired:)")?.nextElementSibling()?.text()?.substringBefore("-")?.trim()?.toIntOrNull()
        val animeDescription = document.selectFirst("div.anime-description p")?.text()?.trim()
        val trailerUrl = document.selectFirst("div.embed iframe")?.attr("src")

        val episodeListHtml = app.get("$mainUrl/ajax/episodelist/$animeId").parsedSafe<Load>()?.html.orEmpty()
        val episodeDocument = Jsoup.parse(episodeListHtml)

        val tvType = if (episodeDocument.select("a[class~=ep-item]").size > 1) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        if (tvType == TvType.TvSeries) {
            val subbedEpisodes = mutableListOf<Episode>()
            val dubbedEpisodes = mutableListOf<Episode>()

            // Create a mutex for thread-safe access
            val mutex = Mutex()

            // List to accumulate episodes before sorting
            val episodes = mutableListOf<Pair<Int, Episode>>()

            episodeDocument.select("a[class~=ep-item]").amap { episodeElement ->
                val SubUrls = mutableListOf<String>()
                val DubUrls = mutableListOf<String>()
                val episodeNumber = episodeElement.selectFirst(".ssli-order")?.text()?.toIntOrNull()
                    ?: episodeElement.attr("data-number").toIntOrNull()
                    ?: episodeElement.selectFirst(".ssli-order")!!.text().toInt()
                val episodeName = episodeElement.selectFirst("div.ep-name")?.text()?.substringAfter(".")

                val embedResponseHtml = app.get("$mainUrl/ajax/embedserver/$animeId/$episodeNumber").parsedSafe<TypeRes>()?.html.orEmpty()
                val embedDocument = Jsoup.parse(embedResponseHtml)

                // For subbed URLs
                embedDocument.select(".servers-sub .server-item a").forEach { subElement ->
                    val embedId = subElement.attr("id").substringAfter("embed-")
                    if (embedId.toIntOrNull() != null) {
                        val href = "$mainUrl/ajax/embed/$animeId/$episodeNumber/$embedId"
                        SubUrls.add(href)
                    }
                }

                if (SubUrls.isEmpty()) {
                    embedDocument.select("#items-category-multi-0 .server-item a").forEach { subElement ->
                        val embedId = subElement.attr("id").substringAfter("embed-")
                        if (embedId.toIntOrNull() != null) {
                            val href = "$mainUrl/ajax/embed/$animeId/$episodeNumber/$embedId"
                            SubUrls.add(href)
                        }
                    }
                }

                mutex.withLock {
                    if (subbedEpisodes.none { it.episode == episodeNumber && it.data == SubUrls.joinToString(",") }) {
                        episodes.add(
                            episodeNumber to
                                    newEpisode(LoadSeriesUrls("sub", SubUrls).toJson())
                                    {
                                        this.name=episodeName
                                        this.season=1
                                        this.episode=episodeNumber
                                    })
                    }
                }

                // For dubbed URLs
                embedDocument.select(".servers-dub .server-item a").forEach { dubElement ->
                    val embedId = dubElement.attr("id").substringAfter("embed-")
                    if (embedId.toIntOrNull() != null) {
                        val href = "$mainUrl/ajax/embed/$animeId/$episodeNumber/$embedId"
                        DubUrls.add(href)
                    }
                }

                mutex.withLock {
                    if (dubbedEpisodes.none { it.episode == episodeNumber && it.data == DubUrls.joinToString(",") }) {
                        episodes.add(episodeNumber to
                                newEpisode(LoadSeriesUrls("dub", DubUrls).toJson())
                                {
                                    this.name=episodeName
                                    this.season=1
                                    this.episode=episodeNumber
                                })
                    }
                }
            }

            // Sort episodes in ascending order by episode number
            val sortedEpisodes = episodes.sortedBy { it.first }

            // Add to subbed and dubbed episodes
            sortedEpisodes.forEach { (_, episode) ->
                if (episode.data.contains("sub")) {
                    subbedEpisodes.add(episode)
                } else {
                    dubbedEpisodes.add(episode)
                }
            }

            return newAnimeLoadResponse(animeTitle, url, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.year = releaseYear
                this.plot = animeDescription
                this.tags = genreTags
                addEpisodes(DubStatus.Subbed, subbedEpisodes)
                addEpisodes(DubStatus.Dubbed, dubbedEpisodes)
                addTrailer(trailerUrl)
                addMalId(animeId)
            }
        } else {
            val embedData = extractEmbedIdsAndEpisodesToJson(animeId)
            return newMovieLoadResponse(animeTitle, url, TvType.Movie, embedData) {
                this.posterUrl = posterUrl
                this.year = releaseYear
                this.plot = animeDescription
                this.tags = genreTags
                addTrailer(trailerUrl)
                addMalId(animeId)
                addDuration(animeDuration)
            }
        }

    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.startsWith("["))
        {
            val videoList: List<LoadUrls>? = tryParseJson(data)
            videoList?.forEach { video ->
                val url = video.href
                val jsonResponse = app.get(url).toString()
                val href = extractIframeSrc(jsonResponse) ?: ""
                val type = video.type
                loadCustomTagExtractor(
                    "$name ${type.uppercase()}",
                    href,
                    "",
                    subtitleCallback,
                    callback
                )
            }
        } else if (data.startsWith("{"))
        {
            val gson = Gson()
            val video: LoadSeriesUrls? = try {
                gson.fromJson(data, LoadSeriesUrls::class.java)
            } catch (e: Exception) {
                null
            }
            video?.href?.forEach { url ->
                val type = video.type
                val jsonResponse = app.get(url).toString()
                val href = extractIframeSrc(jsonResponse) ?: ""
                loadCustomTagExtractor(
                    "$name ${type.uppercase()}",
                    href,
                    "",
                    subtitleCallback,
                    callback
                )
            }
        }
        else
        {
            loadCustomTagExtractor(
                name,
                data,
                "",
                subtitleCallback,
                callback
            )
        }
        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

    private fun extractIframeSrc(jsonResponse: String): String? {
        val type = object : TypeToken<List<String>>() {}.type
        val iframeHtmlList: List<String> = gson.fromJson(jsonResponse, type)
        val iframeHtml = iframeHtmlList.firstOrNull() ?: return null
        return Jsoup.parse(iframeHtml).selectFirst("iframe")?.attr("src")
    }


    private val gson = Gson()
    private suspend fun extractEmbedUrls(id: Int?, serverClass: String): List<LoadUrls> {
        val typeres = app.get("$mainUrl/ajax/embedserver/$id/1").parsedSafe<TypeRes>()?.html ?: ""
        val typedoc = Jsoup.parse(typeres)
        val embedUrls = mutableListOf<LoadUrls>()

        typedoc.select(serverClass).forEach { type ->
            val embedId = type.attr("id").substringAfter("embed-")
            if (embedId.toIntOrNull() != null) {
                val href = "$mainUrl/ajax/embed/$id/1/$embedId" // Construct the URL
                embedUrls.add(LoadUrls(type = if (serverClass.contains("sub")) "sub" else "dub", href = href))
            }
        }
        return embedUrls
    }

    private suspend fun extractEmbedIdsAndEpisodesToJson(id: Int?): String {
        val subUrls = extractEmbedUrls(id, ".servers-sub .server-item a")
        val dubUrls = extractEmbedUrls(id, ".servers-dub .server-item a")
        val allEmbedUrls = subUrls + dubUrls
        return gson.toJson(allEmbedUrls)
    }


    data class LoadUrls(
        val type: String,
        val href: String,
    )

    data class LoadSeriesUrls(val type: String, val href: List<String>)


    //Custom
    private suspend fun loadCustomTagExtractor(
        tag: String? = null,
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null,
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            callback.invoke(
                ExtractorLink(
                    link.source,
                    "${link.name}${if (tag!!.contains("dub", true) || tag.contains("sub", true)) " $tag" else ""}",
                    link.url,
                    link.referer,
                    when (link.type) {
                        ExtractorLinkType.M3U8 -> link.quality
                        else -> quality ?: link.quality
                    },
                    link.type,
                    link.headers,
                    link.extractorData
                )
            )
        }
    }

}
