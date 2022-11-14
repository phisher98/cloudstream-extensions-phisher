package com.likdev256

import android.util.Log
import com.lagradost.cloudstream3.*
//import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
//import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import com.fasterxml.jackson.module.kotlin.*
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.fasterxml.jackson.annotation.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.syncproviders.providers.Kitsu
import okhttp3.FormBody
import okhttp3.RequestBody

class NOXXProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://noxx.to"
    override var name = "NOXX"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.TvSeries
    )

    private suspend fun queryTVApi(count: Int, query: String): NiceResponse {
        val body = FormBody.Builder()
            .addEncoded("no", "$count")
            .addEncoded("gpar", query)
            .addEncoded("qpar", "")
            .addEncoded("spar", "added_date+desc")
            .build()

        return app.post(
            "$mainUrl/fetch.php",
            requestBody = body,
            referer = "$mainUrl/"
        )
    }

    private suspend fun queryTVsearchApi(query: String): NiceResponse {
        return app.post(
            "$mainUrl/livesearch.php",
            data = mapOf(
                "searchVal" to query
            ),
            referer = "$mainUrl/"
        )
    }

    private val scifiShows = "Sci-Fi"
    private val advenShows = "Adventure"
    private val actionShows = "Action"
    private val animShows = "Animation"
    private val horrorShows = "Horror"
    private val comedyShows = "Comedy"
    private val fantasyShows = "Fantasy"
    private val romanceShows = "Romance"

    override val mainPage = mainPageOf(
        //TV Shows
        scifiShows to scifiShows,
        advenShows to advenShows,
        actionShows to actionShows,
        animShows to animShows,
        horrorShows to horrorShows,
        comedyShows to comedyShows,
        fantasyShows to fantasyShows,
        romanceShows to romanceShows
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val query = request.data.format(page)
        //Log.d("RRREEEQQQ", query)
        val TVlist = queryTVApi(
            page * 48,
            query
        ).document
        //Log.d("TV",TVlist.toString())
        val home = TVlist.select("a.block").mapNotNull {
            it.toSearchResult()
        }
        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div > div > span")?.text()?.toString()?.trim() ?: return null
        //Log.d("title", title)
        val href = fixUrl(this.attr("href").toString())
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        //Log.d("posterUrl", posterUrl.toString())
        val quality = SearchQuality.HD
        //Log.d("Quality", quality.toString())

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val TVlist = queryTVsearchApi(
            query
        ).document
        //Log.d("document", document.toString())

        return TVlist.select("a.block").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val title = doc.selectFirst("h1.px-5")?.text()?.toString()?.trim() ?: return null
        //Log.d("title", title)
        val poster = fixUrlNull(doc.selectFirst("img.relative")?.attr("src"))
        //Log.d("poster", poster.toString())
        val tags = doc.select("div.relative a[class*=\"py-0.5\"]").map { it.text() }
        //Log.d("TTAAGG", tags.toString())
        val year = doc.selectFirst("h1.px-5 span.text-gray-400")?.text().toString().removePrefix("(").removeSuffix(")").toInt()
        //Log.d("year", year.toString())
        val description = doc.selectFirst("p.leading-tight")?.text()?.trim()
        //val trailer = fixUrlNull(document.select("iframe#iframe-trailer").attr("src"))
        val rating = doc.select("span.text-xl").text().toRatingInt()
        val actors = doc.select("div.font-semibold span.text-blue-300").map { it.text() }
        val recommendations = doc.select("a.block").mapNotNull {
            it.toSearchResult()
        }

        val titRegex = Regex("[0-9]+")
        val episodes = ArrayList<Episode>()
        doc.select("section.container > div.border-b").forEach { me ->
            val seasonNum = me.select("button > span").text()
            me.select("div.season-list > a").forEach {
                episodes.add(
                    Episode(
                        data = it.attr("href").toString(),
                        name = it.ownText().toString(),//.removeSuffix("Now Playing Episode "),//.replaceFirst(epName.first().toString(), ""),
                        season = titRegex.find(seasonNum)?.value?.toInt(),
                        episode = titRegex.find(it.select("span.flex").text().toString())?.value?.toInt()
                    )
                )
            }
        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.rating = rating
            addActors(actors)
            this.recommendations = recommendations
            //addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("khiHCJJBBJ", data)
        val links = app.get(data).document.select("div.h-vw-65 iframe.w-full").attr("src").toString()
        Log.d("LLLLLLLIIIIIKKKKJJJJJ", links)
        loadExtractor(links, subtitleCallback, callback)

        return true
    }
}

class DoodPmExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.pm"
}
