package com.animedubhindi

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class Animedubhindi : MainAPI() {
    override var mainUrl = "https://www.animedubhindi.me"
    override var name = "AnimeDubHindi"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.Cartoon)

    override val mainPage = mainPageOf(
        "" to "Home",
        "category/movie" to "Movies",
        "category/series" to "Series",
        "category/genres/action" to "Action",
        "category/drama" to "Drama",
        "category/romance" to "Romance",
        "category/thriller" to "Thriller"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}").document
        val home = document.select("article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    override suspend fun search(query: String,page: Int): SearchResponseList {
        val document = app.get("${mainUrl}/?s=$query").document
        val results = document.select("article").mapNotNull { it.toSearchResult() }
        return results.toNewSearchResponseList()
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("h2 a").text().substringBeforeLast("(")
        val href = fixUrl(this.select("h2 a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        return newMovieSearchResponse(title.capitalize(), href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val infoMap = doc.select("ul.wp-block-list li").associate { li ->
            val key = li.selectFirst("strong")?.text()?.removeSuffix(":")?.trim().orEmpty()
            val value = li.ownText().trim()
            key to value
        }
        val iframe = doc.select("div.wp-block-button a").attr("href")

        val audio = infoMap["Audio Tracks"]?.split("|")?.map { it.trim() } ?: emptyList()
        val rawtitle = doc.select("meta[property=og:title]").attr("content")
        val title = rawtitle.substringBeforeLast("(").trim()
        val description = doc.selectFirst("div.entry-content p")?.ownText()?.trim() + "\n$audio"
        val backgroundposter = doc.select("div.entry-content img").attr("src")
        val rating = infoMap["MAL Rating"]?.substringBefore("/") ?: infoMap["IMDb Rating"]?.substringBefore("/")
        val genres = infoMap["Genres"]?.split("|")?.map { it.trim() } ?: emptyList()
        val contentRating = infoMap["Official Dub By"]
        val tvtag = if (rawtitle.contains("Movie",ignoreCase = true)) TvType.Movie else TvType.TvSeries

        return if (tvtag == TvType.TvSeries) {
            val document = app.get(iframe).document

            val episodes = buildList {

                // --------(wp-block-group) --------
                document.select("div.wp-block-group")
                    .filter { block ->
                        block.selectFirst("h2:contains(Episode)") != null &&
                                block.selectFirst("h4") != null
                    }
                    .mapNotNull { block ->

                        val epText = block.selectFirst("h2:contains(Episode)")?.text().orEmpty()
                        val epnum = epText.substringAfter("Episode:")
                            .substringBefore(" ")
                            .trim()
                            .toIntOrNull()

                        val links = block.select("a").mapNotNull { a ->
                            val href = a.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            if (!href.contains("hubcloud") && !href.contains("gdflix")) return@mapNotNull null

                            mapOf(
                                "name" to a.text().ifBlank { "Link" },
                                "url" to href
                            )
                        }

                        if (links.isEmpty()) return@mapNotNull null

                        newEpisode(links.toJson()) {
                            this.episode = epnum
                            this.name = epnum?.let { "Episode $it" } ?: epText
                        }
                    }
                    .let { addAll(it) }


                // -------- (pro-ep-card) --------
                document.select("div.pro-ep-card").mapNotNull { card ->

                    val epText = card.selectFirst(".pro-ep-title")?.text().orEmpty()
                    val epnum = epText.substringAfter("Episode:")
                        .trim()
                        .toIntOrNull()

                    val links = card.select(".pro-btn-group a").mapNotNull { a ->
                        val href = a.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        if (!href.contains("hubcloud") && !href.contains("gdflix")) return@mapNotNull null

                        mapOf(
                            "name" to a.text().ifBlank { "Link" },
                            "url" to href
                        )
                    }

                    if (links.isEmpty()) return@mapNotNull null

                    newEpisode(links.toJson()) {
                        this.episode = epnum
                        this.name = epnum?.let { "Episode $it" } ?: epText
                    }
                }.let { addAll(it) }
            }

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = backgroundposter
                this.tags = genres
                this.score = Score.from10(rating)
                this.contentRating = contentRating
                this.plot = description
            }

        } else {
            val doc = app.get(iframe).document
            val hrefs = (
                    // OLD STRUCTURE
                    doc.select("div.entry-content h4").flatMap { h4 ->
                        val quality = h4.ownText().substringBefore("[Size").trim()

                        h4.select("a").mapNotNull { a ->
                            val url = a.attr("href").takeIf { it.contains("hubcloud") || it.contains("gdflix") }
                                ?: return@mapNotNull null

                            mapOf(
                                "name" to "${a.text()} $quality".trim(),
                                "url" to url
                            )
                        }
                    } + doc.select("div.pro-ep-card .pro-quality-wrapper").flatMap { sec ->
                                val quality = sec.selectFirst(".pro-ep-quality")
                                    ?.text()
                                    ?.removeSurrounding("[", "]")
                                    .orEmpty()

                                sec.select(".pro-btn-group a").mapNotNull { a ->
                                    val url = a.attr("href").takeIf { it.contains("hubcloud") || it.contains("gdflix") }
                                        ?: return@mapNotNull null

                                    mapOf(
                                        "name" to "${a.text()} $quality".trim(),
                                        "url" to url
                                    )
                                }
                            }
                    ).toJson()
            Log.d("Phisher",hrefs)
            newMovieLoadResponse(title, url, TvType.Movie, hrefs) {
                this.posterUrl = backgroundposter
                this.tags = genres
                this.score = Score.from10(rating)
                this.contentRating = contentRating
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Phisher",data.toJson())
        val links = tryParseJson<List<Map<String, String>>>(data) ?: return false
        links.forEach { item ->
            val url = item["url"] ?: return@forEach
            loadExtractor(url, url, subtitleCallback, callback
            )
        }
        return true
    }
}

