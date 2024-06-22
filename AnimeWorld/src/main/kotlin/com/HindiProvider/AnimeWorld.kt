package com.HindiProviders

//import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI

class AnimeWorld : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://anime-world.in"
    override var name = "AnimeWorld"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Cartoon
    )

    companion object
    {
        const val api="https://anime-world.in/wp-json/kiranime/v1/episode?id="
    }

    override val mainPage = mainPageOf(
        "$mainUrl/advanced-search/?s_status=airing&s_orderby=viewed" to "Airing",
        "$mainUrl/advanced-search/?s_status=all&s_orderby=viewed" to "Popular",
        "$mainUrl/advanced-search/?s_status=completed&s_orderby=viewed" to "Completed",
        "$mainUrl/advanced-search/?s_keyword=&s_type=all&s_status=completed&s_lang=all&s_sub_type=anime&s_year=all&s_orderby=viewed&s_genre=" to "Anime",
        "$mainUrl/advanced-search/?s_keyword=&s_type=movies&s_status=all&s_lang=all&s_sub_type=all&s_year=all&s_orderby=default&s_genre=" to "Movies",
        "$mainUrl/advanced-search/?s_keyword=&s_type=all&s_status=all&s_lang=all&s_sub_type=cartoon&s_year=all&s_orderby=default&s_genre=" to "Cartoon"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data).document
        //Log.d("Phisher Test",document.toString())
        val home = document.select("div.col-span-1").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.flex.h-fit a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("div.flex.h-fit a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        //val year = this.select(".year").text().trim().toIntOrNull()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            //this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/advanced-search/?s_keyword=$query").document

        return document.select("div.col-span-1").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val script =
            document.selectFirst("script:containsData(season_list)")?.data()?.substringAfter("[")
                ?.substringBefore("];") ?: ""
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val root: Root = objectMapper.readValue(script)
        val title = document.selectFirst("main h2")?.text() ?: ""
        val poster = fixUrlNull(document.selectFirst("main div.bg-cover")?.attr("data-bg"))
        val tags = document.select(".genres a").map { it.text() }
        val year = document.select(".year").text().trim().toIntOrNull()
        val tvType = if (url.contains("movie")) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst("main section span.block.w-full")?.text()?.trim()
        val trailer = fixUrlNull(document.select("iframe").attr("src"))
        val rating = document.select("span.num").text().toRatingInt()
        //val actors = document.select("#cast > div:nth-child(4)").map { it.text() }
        val recommendations = document.select("article").mapNotNull {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val episodes: MutableList<Episode> = mutableListOf()
            val allEpisodes: List<All> = root.episodes.all
            episodes += allEpisodes.amap {
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
                if (rawepisode.isEmpty())
                {
                    rawepisode="1"
                }
                val episode=rawepisode.toInt()
                Episode(
                    data = href,
                    name = name,
                    episode = episode,
                    posterUrl = thumbs
                )
            }.reversed()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
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
        val episodeData = app.get(api+data).parsedSafe<Episodedata>()
        val filteredPlayers = episodeData?.players?.filter { !it.url.contains("mega", ignoreCase = true) }
        val urlAndLanguageList = filteredPlayers?.map { it.url to it.language }
        urlAndLanguageList?.forEach { (url, language) ->
            val domain=getBaseUrl(url)
            val token=app.get(url).text.substringAfter("m3u8\\/").substringBefore("\\/")
            val lang=language.uppercase()
            val link="$domain/m3u8/$token/master.txt?s=1&lang=$language&cache=1"
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
}


fun getBaseUrl(url: String): String {
    return URI(url).let {
        "${it.scheme}://${it.host}"
    }
}