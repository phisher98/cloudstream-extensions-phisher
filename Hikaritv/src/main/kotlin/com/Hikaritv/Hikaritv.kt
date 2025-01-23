package com.hikaritv

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Hikaritv : MainAPI() {
    override var mainUrl = "https://hikari.gg"
    override var name = "Hikaritv"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "ajax/getfilter?sort=recently_updated&page=" to "Recently Updated",
        "ajax/getfilter?type=2&sort=default&page=" to "Movies",
        "ajax/getfilter?stats=2&sort=default&page=" to "Finished Airing",
        "ajax/getfilter?sort=score&page=" to "Most Popular",
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val res = app.get("$mainUrl/${request.data}$page").parsedSafe<HomePage>()?.html ?:""
        val document= Jsoup.parse(res)
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
        return newMovieSearchResponse(title, href, TvType.Movie) {
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
            newMovieSearchResponse(title, href, TvType.Movie) {
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

        val tvType = if (episodeDocument.select("#episodes-load a, #episodes-page-1 a").size > 1) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        if (tvType == TvType.TvSeries) {
            val subbedEpisodes = mutableListOf<Episode>()
            val dubbedEpisodes = mutableListOf<Episode>()

            episodeDocument.select("#episodes-load a, #episodes-page-1 a").amap { episodeElement ->
                val episodeNumber = episodeElement.attr("onclick").substringAfter("$animeId,").substringBefore(")").toIntOrNull()
                val episodeName = episodeElement.selectFirst("div.ep-name")?.text()?.substringAfter(".")

                val embedResponseHtml = app.get("$mainUrl/ajax/embedserver/$animeId/$episodeNumber").parsedSafe<TypeRes>()?.html.orEmpty()
                val embedDocument = Jsoup.parse(embedResponseHtml)

                embedDocument.select(".servers-sub .server-item a").forEach { subElement ->
                    val embedId = subElement.attr("id").substringAfter("embed-")
                    if (embedId.toIntOrNull() != null) {
                        val jsonResponse = app.get("$mainUrl/ajax/embed/$animeId/$episodeNumber/$embedId").toString()
                        val href = extractIframeSrc(jsonResponse) ?: ""
                        subbedEpisodes.add(Episode(href, episodeName, 1, episodeNumber))
                    }
                }
                embedDocument.select(".servers-dub .server-item a").forEach { dubElement ->
                    val embedId = dubElement.attr("id").substringAfter("embed-")
                    if (embedId.toIntOrNull() != null) {
                        val jsonResponse = app.get("$mainUrl/ajax/embed/$animeId/$episodeNumber/$embedId").toString()
                        val href = extractIframeSrc(jsonResponse) ?: ""
                        dubbedEpisodes.add(Episode(href, episodeName, 1, episodeNumber))
                    }
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
                val iframe = video.href
                val type = video.type
                loadCustomTagExtractor(
                    "$name ${type.uppercase()}",
                    iframe,
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



    private val gson = Gson()
    private suspend fun extractEmbedIdsAndEpisodesToJson(id: Int?): String {
        val typeres = app.get("$mainUrl/ajax/embedserver/$id/1").parsedSafe<TypeRes>()?.html ?: ""
        val allEmbedUrls = mutableListOf<LoadUrls>()
        val typedoc = Jsoup.parse(typeres)

        val subElements = typedoc.select(".servers-sub .server-item a")
        subElements.forEach { type ->
            val embedId = type.attr("id").substringAfter("embed-")
            if (embedId.toIntOrNull() != null) {
                val jsonResponse = app.get("$mainUrl/ajax/embed/$id/1/$embedId").toString()
                val href = extractIframeSrc(jsonResponse) ?: ""
                allEmbedUrls.add(LoadUrls(type = "sub", href = href))
            }
        }

        val dubElements = typedoc.select(".servers-dub .server-item a")
        dubElements.forEach { type ->
            val embedId = type.attr("id").substringAfter("embed-")
            if (embedId.toIntOrNull() != null) {
                val jsonResponse = app.get("$mainUrl/ajax/embed/$id/1/$embedId").toString()
                val href = extractIframeSrc(jsonResponse) ?: ""
                allEmbedUrls.add(LoadUrls(type = "dub", href = href))
            }
        }

        return gson.toJson(allEmbedUrls)
    }

    private fun extractIframeSrc(jsonResponse: String): String? {
        val type = object : TypeToken<List<String>>() {}.type
        val iframeHtmlList: List<String> = gson.fromJson(jsonResponse, type)
        val iframeHtml = iframeHtmlList.firstOrNull() ?: return null
        return Jsoup.parse(iframeHtml).selectFirst("iframe")?.attr("src")
    }


    data class LoadUrls(
        val type: String,
        val href: String,
    )

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
