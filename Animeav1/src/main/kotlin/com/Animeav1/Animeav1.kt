package com.Animeav1

import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.jsoup.nodes.Element

class Animeav1 : MainAPI() {
    override var mainUrl              = "https://animeav1.com"
    override var name                 = "AnimeAv1"
    override val hasMainPage          = true
    override var lang                 = "es-mx"
    override val hasDownloadSupport   = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Anime, TvType.AnimeMovie)

    override val mainPage = mainPageOf(
        "catalogo?status=emision" to "Emision",
        "catalogo?status=finalizado" to "Finalizado",
        "catalogo?category=pelicula" to "Pelicula",
        "catalogo?category=ova" to "OVA",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}&page=$page").documentLarge
        val home     = document.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("h3").text()
        val href      = this.select("a").attr("href")
        val posterUrl = fixUrlNull(this.select("figure img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/catalogo?search=$query").documentLarge
        val results = document.select("article").mapNotNull { it.toSearchResult() }
        return results
    }


    private fun String.toTvType(): TvType {
        return when {
            this.contains("TV Anime", ignoreCase = true) -> TvType.Anime
            this.contains("Película", ignoreCase = true) -> TvType.Movie
            this.contains("OVA", ignoreCase = true) -> TvType.Anime
            else -> TvType.Movie
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document    = app.get(url).documentLarge
        val title       = document.selectFirst("article h1")?.text() ?: "Desconocido"
        val poster      = document.select("img.aspect-poster").attr("src")
        val description = document.selectFirst("div.entry.text-lead p")?.text()
        val type        = document.select("header div.text-sm").text().toTvType()
        val tags        = document.select("header a[href*=?genre=]").map { it.text() }
        val year        = document.select("header div.text-sm span:matches(\\d{4})").text().toIntOrNull()
        val score       = document.select("article [class*=ic-star] .text-lead").text()
        val href        = fixUrl(document.select("div.grid > article a").attr("href"))

        return if (type == TvType.Anime) {
            val episodes = mutableListOf<Episode>()
            val mediaId = Regex("/(\\d+)\\.jpg$").find(poster)?.groupValues?.get(1) ?: "0"
            val scriptContent = document.select("script").html()
            val episodeZeroRegex = Regex("number:\\s*0")
            val regex = Regex("media:\\{.*?episodesCount:(\\d+).*?slug:\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)

            val match = regex.find(scriptContent)
            if (match != null) {
                val totalEpisodes = match.groupValues[1].toIntOrNull() ?: 0
                val slug = match.groupValues[2]
                val hasEpisodeZero = episodeZeroRegex.containsMatchIn(scriptContent)
                val startEp = if (hasEpisodeZero) 0 else 1

                for (i in startEp..totalEpisodes) {
                    val epUrl = "https://animeav1.com/media/$slug/$i"
                    val epposter = "https://cdn.animeav1.com/screenshots/$mediaId/$i.jpg"

                    episodes.add(
                        newEpisode(epUrl) {
                            this.name = "Episode $i"
                            this.episode = i
                            this.posterUrl = epposter
                        }
                    )
                }
            }
            newAnimeLoadResponse(title, url, TvType.Anime) {
                addEpisodes(DubStatus.Subbed, episodes)
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.year = year
                this.score = Score.from10(score)
            }
        } else newMovieLoadResponse(title, url, TvType.AnimeMovie, href) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.year = year
            this.score = Score.from10(score)
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).documentLarge

        val scriptHtml = document.select("script")
            .firstOrNull { it.html().contains("__sveltekit_") }
            ?.html()
            .orEmpty()

        fun cleanJsToJson(js: String): String {
            var cleaned = js.replaceFirst("""^\s*\w+\s*:\s*""".toRegex(), "")
            cleaned = cleaned.replace("void 0", "null")
            cleaned = Regex("""(?<=[{,])\s*(\w+)\s*:""").replace(cleaned) { "\"${it.groupValues[1]}\":" }

            return cleaned.trim()
        }

        val embedsPattern = "embeds:\\s*\\{([^}]*\\{[^}]*\\})*[^}]*\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
        val embedsMatch = embedsPattern.find(scriptHtml)?.value
        val embedsJson = embedsMatch?.let { cleanJsToJson(it) }

        if (!embedsJson.isNullOrEmpty()) {
            val embedsObject = JSONObject(embedsJson)
            fun extractLinks(arrayName: String): List<Pair<String, String>> {
                val list = mutableListOf<Pair<String, String>>()
                if (embedsObject.has(arrayName)) {
                    val jsonArray = embedsObject.getJSONArray(arrayName)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(obj.getString("server") to obj.getString("url"))
                    }
                }
                return list
            }

            val subEmbeds = extractLinks("SUB")
            val dubEmbeds = extractLinks("DUB")

            subEmbeds.forEach { (server, url) ->
                loadCustomExtractor(
                    "Animeav1 [SUB:$server]",
                    url,
                    "",
                    subtitleCallback,
                    callback
                )
            }

            dubEmbeds.forEach { (server, url) ->
                loadCustomExtractor(
                    "Animeav1 [DUB:$server]",
                    url,
                    "",
                    subtitleCallback,
                    callback
                )
            }
        }
        return true
    }
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