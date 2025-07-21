package com.phisher98

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.network.DdosGuardKiller
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody
import org.jsoup.nodes.Element

class NOXXProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://noxx.to"
    override var name = "NOXX"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.TvSeries
    )
    private var ddosGuardKiller = DdosGuardKiller(true)

    private suspend fun queryTVApi(count: Int, query: String): NiceResponse {
        val body = FormBody.Builder()
            .addEncoded("no", "$count")
            .addEncoded("gpar", query)
            .addEncoded("qpar", "")
            .addEncoded("spar", "series_added_date desc")
            .build()

        return app.post(
            "$mainUrl/fetch.php",
            requestBody = body,
            interceptor = ddosGuardKiller,
            referer = "$mainUrl/"
        )
    }

    private suspend fun queryTVsearchApi(query: String): NiceResponse {
        return app.post(
            "$mainUrl/livesearch.php",
            data = mapOf(
                "searchVal" to query
            ),
            interceptor = ddosGuardKiller,
            referer = "$mainUrl/"
        )
    }

    private val scifiShows = "Sci-Fi"
    private val advenShows = "Adventure"
    private val actionShows = "Action"
    private val horrorShows = "Horror"
    private val DramaShows= "Drama"
    private val comedyShows = "Comedy"
    private val fantasyShows = "Fantasy"
    private val romanceShows = "Romance"

    override val mainPage = mainPageOf(
        //TV Shows
        scifiShows to scifiShows,
        advenShows to advenShows,
        actionShows to actionShows,
        comedyShows to comedyShows,
        fantasyShows to fantasyShows,
        DramaShows to DramaShows,
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val query = request.data.format(page)
        val TVlist = queryTVApi(
            page * 48,
            query
        ).document
        val home = TVlist.select("a.block").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div > div > span")?.text()?.toString()?.trim() ?: return null
        val href = fixUrl(this.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        val quality = SearchQuality.HD

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val TVlist = queryTVsearchApi(
            query
        ).document
        return TVlist.select("a[href^=\"/tv\"]").mapNotNull {
            val title = it.selectFirst("div > h2")?.text().toString().trim()
            val href = fixUrl(mainUrl + it.attr("href").toString())
            val posterUrl = fixUrlNull(it.selectFirst("img")?.attr("src"))
            val quality = SearchQuality.HD

            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, interceptor = ddosGuardKiller).document
        val title = doc.selectFirst("h1.px-5")?.text()?.toString()?.trim() ?: return null
        val poster = fixUrlNull(doc.selectFirst("img.relative")?.attr("src"))
        val tags = doc.select("div.relative a[class*=\"py-0.5\"]").map { it.text() }
        val description = doc.selectFirst("p.leading-tight")?.text()?.trim()
        val rating = doc.select("span.text-xl").text().toRatingInt()
        val actors = doc.select("div.font-semibold span.text-blue-300").map { it.text() }
        val recommendations = doc.select("a.block").mapNotNull {
            it.toSearchResult()
        }

        val titRegex = Regex("\\d+")
        val episodes = ArrayList<Episode>()
        doc.select("section.container > div.border-b").forEach { me ->
            val seasonNum = me.select("button > span").text()
            me.select("div.season-list > a").forEach {
                episodes.add(
                    newEpisode(mainUrl + it.attr("href"))
                    {
                        this.name=it.ownText().toString().removePrefix("Episode ").substring(2)
                        this.season=titRegex.find(seasonNum)?.value?.toInt()
                        this.episode=titRegex.find(it.select("span.flex").text().toString())?.value?.toInt()
                    }
                )
            }
        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.rating = rating
            addActors(actors)
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = app.get(data, interceptor = ddosGuardKiller).document.select("div.h-vw-65 iframe.w-full").attr("src").toString()
        val sourcelink= app.get(links, referer = mainUrl).document.selectFirst("iframe")?.attr("src")
        if (sourcelink != null) {
            val embedUrl = sourcelink.replace("/download/", "/e/")
            val response = app.get(embedUrl, headers = mapOf("Accept-Language" to "en-US,en;q=0.9"))
            val script = if (!getPacked(response.text).isNullOrEmpty()) {
                getAndUnpack(response.text)
            } else {
                response.document.selectFirst("script:containsData(sources:)")?.data()
            }
            val m3u8 =
                Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: "")?.groupValues?.getOrNull(1)
            generateM3u8(
                name,
                m3u8 ?: "",
                mainUrl
            ).forEach(callback)
        }
        return true
    }
}

class DoodPmExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.pm"
}