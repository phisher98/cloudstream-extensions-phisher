package com.Aniworld

import com.Aniworld.AniworldPlugin.ByseSX
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

open class Aniworld : MainAPI() {
    override var mainUrl = "https://aniworld.to"
    override var name = "Aniworld"
    override val hasMainPage = true
    override var lang = "de"

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val isTvSeries = name.equals("Serienstream", ignoreCase = true)
        val requesturl = if (isTvSeries) "$mainUrl/beliebte-serien" else mainUrl

        val document = app.get(requesturl).documentLarge

        val item = arrayListOf<HomePageList>()
        document.select("div.carousel,div.mb-5").map { ele ->
            val header = ele.selectFirst("h2,h3")?.text() ?: return@map
            val home = ele.select("div.coverListItem,div.col-6").mapNotNull {
                it.toSearchResult()
            }
            if (home.isNotEmpty()) item.add(HomePageList(header, home))
        }
        return newHomePageResponse(item)
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val isTvSeries = name.equals("Serienstream", ignoreCase = true)

        if (isTvSeries) {
            return app
                .get("https://serienstream.to/api/search/suggest?term=$query")
                .parsedSafe<SerienstreamSearch>()
                ?.shows
                ?.map {
                    newAnimeSearchResponse(
                        it.name.replace(Regex("</?em>"), ""),
                        fixUrl(it.url),
                        TvType.TvSeries
                    ) {
                        posterUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/aniworld.jpg"
                    }
                }
                ?: emptyList()
        }


        val json = app.post(
            "$mainUrl/ajax/search",
            data = mapOf("keyword" to query),
            referer = "$mainUrl/search",
            headers = mapOf(
                "x-requested-with" to "XMLHttpRequest"
            )
        )
        return tryParseJson<List<AnimeSearch>>(json.text)?.filter {
            !it.link.contains("episode-") && it.link.contains(
                "/stream"
            )
        }?.map {
            newAnimeSearchResponse(
                it.title?.replace(Regex("</?em>"), "") ?: "",
                fixUrl(it.link),
                TvType.Anime
            ) {
                this.posterUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/aniworld.jpg"
            }
        } ?: throw ErrorLoadingException()

    }

    @Suppress("LABEL_NAME_CLASH")
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).documentLarge
        val imdbid = document.selectFirst("div.series-title > a")?.attr("data-imdb") ?: document
            .selectFirst("p a[href^='https://www.imdb.com/title/']")
            ?.attr("href")
            ?.substringAfter("/title/")
            ?.substringBefore("/")

        val isTvSeries = name.equals("Serienstream", ignoreCase = true)
        val jsonObject: JSONObject? =
            if (!isTvSeries && imdbid?.isNotBlank() == true) {
                runCatching {
                    val response =
                        app.get("https://api.ani.zip/mappings?imdb_id=$imdbid").text

                    val value = JSONTokener(response).nextValue()
                    value as? JSONObject
                }.getOrNull()
            } else null

        val mappings = jsonObject?.optJSONObject("mappings")
        val malidId: Int? = mappings?.optInt("mal_id")?.takeIf { mappings.has("mal_id") }
        val anilistid: Int? = mappings?.optInt("anilist_id")?.takeIf { mappings.has("anilist_id") }
        val title = document.selectFirst("div.series-title span,div.row h1")?.text() ?: return null
        val imdbBGPoster: String? = imdbid.takeIf { it?.isNotBlank() == true }?.let { "https://images.metahub.space/background/medium/$it/img" }

        val poster: String? = if (!isTvSeries) {
            jsonObject?.optJSONArray("images")
                ?.let { images ->
                    (0 until images.length())
                        .map { images.getJSONObject(it) }
                        .firstOrNull { it.optString("coverType") == "Fanart" }
                        ?.optString("url")
                }
                ?: fixUrlNull(document.selectFirst("div.seriesCoverBox img")?.attr("data-src"))
        } else {
            fixUrlNull(document.selectFirst("div.col-3 img")?.attr("data-src"))?.takeIf { it.isNotBlank() } ?: document.selectFirst("div.col-3 img")?.attr("src")
        }

        val tags = document.select("div.genres li a,li.series-group:has(strong:contains(Genre)) a").map { it.text() }
        val year = document.selectFirst("span[itemprop=startDate] a")?.text()?.toIntOrNull()
        val description = document.select("p.seri_des,span.description-text").text()
        val actor = document.select("li:contains(Schauspieler:) ul li a").map { it.select("span").text() }

        val episodes = mutableListOf<Episode>()
        document.select("div#stream > ul:first-child li,#season-nav ul:first-child li").forEach { ele ->
            val pageLink = ele.selectFirst("a")?.attr("href") ?: return@forEach
            val seasonno = if ("-" in pageLink) { pageLink.substringAfterLast("-").toIntOrNull() ?: 0 } else { 0 }
            val epsDocument = app.get(fixUrl(pageLink)).documentLarge

            epsDocument.select("#season$seasonno tr,tr.episode-row").forEach { eps ->
                val epno = eps.select("td > meta").attr("content").toIntOrNull() ?: eps.attr("data-episode-season-id")
                    .toIntOrNull() ?: eps.selectFirst("th.episode-number-cell")?.text()?.trim()
                    ?.toIntOrNull() ?: return@forEach

                val epname = eps.selectFirst("td.seasonEpisodeTitle span")?.text() ?: eps
                        .selectFirst(".episode-title-ger")?.text()?.trim()?.ifBlank { "Episode $epno"  }

                val href = fixUrlNull(eps.selectFirst("td.seasonEpisodeTitle a")?.attr("href")) ?: eps
                    .attr("onclick")
                    .substringAfter("window.location='", "")
                    .substringBefore("'")
                    .takeIf { it.isNotBlank() }
                    ?.let(::fixUrl)
                val epposter = "https://episodes.metahub.space/$imdbid/$seasonno/$epno/w780.jpg"
                episodes.add(
                    newEpisode(href) {
                        this.name = epname
                        this.season = seasonno
                        this.episode = epno
                        this.posterUrl = epposter
                    }
                )
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title
            posterUrl = poster
            backgroundPosterUrl = imdbBGPoster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            addActors(actor)
            if (!isTvSeries) {
                addMalId(malidId)
                addAniListId(anilistid)
            }
            addImdbId(imdbid)
            plot = description
            this.tags = tags
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).documentLarge
        document
            .select("div.hosterSiteVideo ul li, #episode-links button.link-box")
            .mapNotNull { el ->

                when (el.tagName()) {

                    "li" -> {
                        val lang = el.attr("data-lang-key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val link = el.attr("data-link-target").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val name = el.selectFirst("h4")?.text()?.trim().orEmpty()

                        Triple(lang, fixUrl(link), name)
                    }

                    "button" -> {
                        val lang = el.attr("data-language-label").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val link = el.attr("data-play-url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val name = el.attr("data-provider-name").ifBlank {
                            el.selectFirst("span")?.text()?.trim().orEmpty()
                        }

                        Triple(lang, fixUrl(link), name)
                    }

                    else -> null
                }
            }
            .filter { it.third != "Vidoza" }
            .amap { (langKey, link, providerName) ->
                val response = app.get(link, allowRedirects = false)
                val redirectUrl = response.headers["Location"] ?: return@amap

                val lang = langKey.getLanguage(document) ?: langKey
                val name = "$providerName [$lang]"

                if (redirectUrl.contains("filemoon")) {
                    FileMoon().getUrl(redirectUrl, name, subtitleCallback, callback)
                } else {
                    loadCustomExtractor(name, redirectUrl, "", subtitleCallback, callback)
                }
            }

        return true
    }

    fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val title = this.selectFirst("h3")?.text()?.takeIf { it.isNotBlank() }
            ?: this.selectFirst("img")?.attr("alt").orEmpty()
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src")?.takeIf { it.isNotBlank() } ?: this.selectFirst("img")?.attr("src"))
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    private fun String.getLanguage(document: Document): String? {
        return document.selectFirst("div.changeLanguageBox img[data-lang-key=$this]")?.attr("title")
            ?.removePrefix("mit")?.trim()
    }

    private data class AnimeSearch(
        @JsonProperty("link") val link: String,
        @JsonProperty("title") val title: String? = null,
    )

    data class SerienstreamSearch(
        val shows: List<Show>,
        val people: List<Any?>,
        val genres: List<Any?>,
    )

    data class Show(
        val name: String,
        val url: String,
    )
}

class Dooood : DoodLaExtractor() {
    override var mainUrl = "https://urochsunloath.com"
}

class FileMoon : ByseSX() {
    override var mainUrl = "https://filemoon.to"
    override var name = "FileMoon"
}

suspend fun loadCustomExtractor(
    name: String? = null,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    name ?: link.source,
                    name ?: link.name,
                    link.url,
                ) {
                    this.quality = when {
                        else -> quality ?: link.quality
                    }
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}