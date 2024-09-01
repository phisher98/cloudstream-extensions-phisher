package com.HindiProviders

// import android.util.Log
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addSeasonNames
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URI
import org.jsoup.nodes.Element

@OptIn(ExperimentalStdlibApi::class)
class AnimeWorld : MainAPI() {
    override var mainUrl = "https://anime-world.in"
    override var name = "AnimeWorld"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.Cartoon)

    companion object {
        const val api = "https://anime-world.in/wp-json/kiranime/v1/episode?id="
    }

    override val mainPage =
            mainPageOf(
                    "$mainUrl/advanced-search/?s_status=airing&s_orderby=viewed" to "Airing",
                    "$mainUrl/advanced-search/?s_status=all&s_orderby=viewed" to "Popular",
                    "$mainUrl/advanced-search/?s_status=completed&s_orderby=viewed" to "Completed",
                    "$mainUrl/advanced-search/?s_keyword=&s_type=all&s_status=completed&s_lang=all&s_sub_type=anime&s_year=all&s_orderby=viewed&s_genre=" to
                            "Anime",
                    "$mainUrl/advanced-search/?s_keyword=&s_type=movies&s_status=all&s_lang=all&s_sub_type=all&s_year=all&s_orderby=default&s_genre=" to
                            "Movies",
                    "$mainUrl/advanced-search/?s_keyword=&s_type=all&s_status=all&s_lang=all&s_sub_type=cartoon&s_year=all&s_orderby=default&s_genre=" to
                            "Cartoon"
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home = document.select("div.col-span-1").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.flex.h-fit a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("div.flex.h-fit a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/advanced-search/?s_keyword=$query").document

        return document.select("div.col-span-1").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val script =document.selectFirst("script:containsData(season_list)")?.data()?: throw NotImplementedError("Unable to collect JSON data")
        val json=Regex("""season_list.=.(.*);""").find(script)?.groupValues?.get(1).toString()
        val root = parseJson<Array<Root>>(json)
        val title =document.selectFirst("main h2")?.text()?: throw NotImplementedError("Unable to find title")
        val poster = fixUrlNull(document.selectFirst("main div.bg-cover")?.attr("data-bg"))
        val tags = document.select(".genres a").map { it.text() }
        val year = document.select(".year").text().trim().toIntOrNull()
        val tvType = if (document.selectFirst("ul.flex:nth-child(3) > li:nth-child(3) > a:nth-child(1)")!!
                .text().contains("Movie")) TvType.Movie else TvType.TvSeries
        var movieid= ""
        if (tvType==TvType.Movie)
        {
            root.forEachIndexed { _, it->
                it.episodes.all.forEach {
                    val href=it.id.toString()
                    movieid += href
                }
            }
        }
        val description = document.selectFirst("main section span.block.w-full")?.text()?.trim()
        val trailer = fixUrlNull(document.select("iframe").attr("src"))
        // val actors = document.select("#cast > div:nth-child(4)").map { it.text() }
        val recommendations = document.select("article").mapNotNull { it.toSearchResult() }
        val seasonNames = mutableListOf<String>()
        return if (tvType == TvType.TvSeries) {
            val episodes: MutableList<Episode> = mutableListOf()
            root.forEachIndexed { counter, season ->
                seasonNames.plus(season.name)
                season.episodes.all.forEach {
                    val href = it.id.toString()
                    var name = it.metadata.title
                    if (name.isEmpty()) {
                        name = it.title
                    }
                    var thumbs = it.metadata.thumbnail
                    if (!thumbs.startsWith("https")) {
                        thumbs = mainUrl + thumbs
                    }
                    var rawepisode = it.metadata.number
                    if (rawepisode.isEmpty()) {
                        rawepisode = "1"
                    }
                    val episode = rawepisode.toInt()
                    episodes +=
                            Episode(
                                    data = href,
                                    name = name,
                                    episode = episode,
                                    season = counter + 1,
                                    posterUrl = thumbs
                            )
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.addSeasonNames(seasonNames)
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, movieid) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val episodeData = app.get(api + data).parsedSafe<Episodedata>()
        val filteredPlayers =
                episodeData?.players?.filter { !it.url.contains("mega", ignoreCase = true) }
        val urlAndLanguageList = filteredPlayers?.map { it.url to it.language }
        urlAndLanguageList?.forEach { (url, language) ->
            val domain = getBaseUrl(url)
            val token = app.get(url).text.substringAfter("m3u8\\/").substringBefore("\\/")
            val lang = language.uppercase()
            val link = "$domain/m3u8/$token/master.txt?s=1&lang=$language&cache=1"
            callback.invoke(
                    ExtractorLink(
                            "AnimeWorld-${lang}",
                            "AnimeWorld-${lang}",
                            link,
                            mainUrl,
                            Qualities.Unknown.value,
                            true
                    )
            )
        }
        return true
    }

    fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}
