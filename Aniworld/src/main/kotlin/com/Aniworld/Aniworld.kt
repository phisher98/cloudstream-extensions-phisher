package com.Aniworld

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

        val document = app.get(mainUrl).document
        val item = arrayListOf<HomePageList>()
        document.select("div.carousel").map { ele ->
            val header = ele.selectFirst("h2")?.text() ?: return@map
            val home = ele.select("div.coverListItem").mapNotNull {
                it.toSearchResult()
            }
            if (home.isNotEmpty()) item.add(HomePageList(header, home))
        }
        return newHomePageResponse(item)
    }

    override suspend fun search(query: String): List<SearchResponse> {
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
            }
        } ?: throw ErrorLoadingException()

    }

    @Suppress("LABEL_NAME_CLASH")
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val imdbid = document.select("div.series-title > a").attr("data-imdb")

        val isTvSeries = name.equals("Serienstream", ignoreCase = true)

        val jsonObject: JSONObject? = if (!isTvSeries) {
            val anijson = app.get("https://api.ani.zip/mappings?imdb_id=$imdbid").toString()
            if (anijson.isNotBlank()) JSONObject(anijson) else null
        } else null

        val mappings = jsonObject?.optJSONObject("mappings")
        val malidId: Int? = mappings?.optInt("mal_id")?.takeIf { mappings.has("mal_id") }
        val anilistid: Int? = mappings?.optInt("anilist_id")?.takeIf { mappings.has("anilist_id") }

        val title = document.selectFirst("div.series-title span")?.text() ?: return null

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
            fixUrlNull(document.selectFirst("div.seriesCoverBox img")?.attr("data-src"))
        }

        val tags = document.select("div.genres li a").map { it.text() }
        val year = document.selectFirst("span[itemprop=startDate] a")?.text()?.toIntOrNull()
        val description = document.select("p.seri_des").text()
        val actor = document.select("li:contains(Schauspieler:) ul li a").map { it.select("span").text() }

        val episodes = mutableListOf<Episode>()
        document.select("div#stream > ul:first-child li").forEach { ele ->
            val pageLink = ele.selectFirst("a")?.attr("href") ?: return@forEach
            val epsDocument = app.get(fixUrl(pageLink)).document

            epsDocument.select("div#stream > ul:nth-child(4) li").forEach { eps ->
                val epsLink = eps.selectFirst("a") ?: return@forEach
                val seasonNumber = epsLink.attr("data-season-id").toIntOrNull() ?: 0
                if (seasonNumber == 0) return@forEach

                episodes.add(
                    newEpisode(fixUrl(epsLink.attr("href"))) {
                        season = seasonNumber
                        episode = epsLink.text().toIntOrNull()
                    }
                )
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            addActors(actor)
            if (!isTvSeries) {
                addMalId(malidId)
                addAniListId(anilistid)
            }
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
        val document = app.get(data).document
        document.select("div.hosterSiteVideo ul li").map {
                Triple(
                    it.attr("data-lang-key"),
                    it.attr("data-link-target"),
                    it.select("h4").text()
                )
            }.filter {
                it.third != "Vidoza"
            }.amap {
            val redirectUrl = app.get(fixUrl(it.second)).url
            val lang = it.first.getLanguage(document)
            val name = "${it.third} [${lang}]"
            loadCustomExtractor(name,redirectUrl,"",subtitleCallback,callback)
        }

        return true
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val title = this.selectFirst("h3")?.text() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
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

}

class Dooood : DoodLaExtractor() {
    override var mainUrl = "https://urochsunloath.com"
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