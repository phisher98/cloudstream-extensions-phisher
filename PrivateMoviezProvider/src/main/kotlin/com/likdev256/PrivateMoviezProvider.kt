package com.likdev256

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class PrivateMoviezProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://privatemoviez.biz"
    override var name = "PrivateMoviez"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private val cfKiller = CloudflareKiller()

    override val mainPage = mainPageOf(
        "240" to "Hollywood Movies",
        "241" to "Bollywood Movies",
        "242" to "Tollywood Movies",
        "" to "Movies"
    )



    data class GetMainPageHtml (
    @JsonProperty("paged"   ) val paged   : Int?,
    @JsonProperty("content" ) val content : String
    )

    data class PrivateLinks(
        @JsonProperty("sourceName") val sourceName: String?,
        @JsonProperty("sourceLink") val sourceLink: String
    )

    private suspend fun queryMainPage(query: String, count: Int): String {

        return app.get(
            "$mainUrl/wp-admin/admin-ajax.php?action=livep&data[uuid]=uid_c$query&data[category]=$query&data[name]=grid_box_2&data[posts_per_page]=9&data[pagination]=load_more&data[entry_category]=text&data[title_tag]=h2&data[entry_meta][]=date&data[review]=1&data[mobile_hide_meta][]=comment&data[mobile_last]=date&data[bookmark]=1&data[entry_format]=bottom&data[hide_excerpt]=all&data[box_style]=shadow&data[center_mode]=1&data[paged]=1&data[page_max]=28&data[processing]=true&data[page_next]=$count",
            interceptor = cfKiller,
            referer = "$mainUrl/"
        ).parsed<GetMainPageHtml>().content
    }

    private suspend fun querySearch(query: String): String {

        return app.get(
            "$mainUrl/wp-admin/admin-ajax.php?action=livep&data[uuid]=uid_search_0&data[name]=grid_box_2&data[posts_per_page]=9&data[pagination]=load_more&data[entry_category]=text&data[title_tag]=h2&data[entry_meta][]=date&data[review]=1&data[mobile_hide_meta][]=comment&data[mobile_last]=date&data[bookmark]=1&data[entry_format]=bottom&data[excerpt]=1&data[hide_excerpt]=all&data[box_style]=shadow&data[center_mode]=1&data[paged]=1&data[page_max]=282&data[processing]=true&data[page_next]=1",
            interceptor = cfKiller,
            referer = "$mainUrl/"
        ).parsed<GetMainPageHtml>().content
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val query = request.data.format(page)
        val document = Jsoup.parse(
            queryMainPage(
                query,
                page
            )
        )

        //Log.d("Document", request.data)
        val home = document.select("div.p-wrap").mapNotNull {
                it.toSearchResult()
            }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home, isHorizontalImages = true)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleRaw = this.selectFirst("a.p-url")?.text()?.toString()?.trim()
            ?: return null
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleRaw)?.groups?.get(1)?.value.toString()
        //Log.d("title", title)
        val href = fixUrl(this.selectFirst("a.p-url")?.attr("href").toString())
        //Log.d("href", href)
        val posterUrl = this.select("img.featured-img").attr("src")
        //Log.d("mybadposterUrl", posterUrl)
        //Log.d("QualityN", qualityN)
        val quality = getQualityFromString(titleRaw)
        //Log.d("Quality", quality.toString())
        return if (href.contains("Movie")) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
                this.quality = quality
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query", interceptor = cfKiller).document
        Log.d("mybadsearch", document.toString())

        return document.select("div.p-wrap").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, interceptor = cfKiller).document
        //Log.d("Doc", doc.toString())
        val titleRaw = doc.selectFirst("h1.s-title")?.text()?.toString()?.trim()
            ?: return null
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleRaw)?.groups?.get(1)?.value.toString()
        //Log.d("titleL", titleL)
        //Log.d("title", title)
        val poster = fixUrlNull(doc.selectFirst("img.attachment-foxiz_crop_o1")?.attr("data-lazy-src"))
        //Log.d("poster", poster.toString())
        val tags = doc.select("div.p-categories > a.p-category").map { it.text() }.distinct()
        val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
        val year = yearRegex.find(title)?.value
            ?.toIntOrNull()
        //Log.d("year", year.toString())
        val description = doc.selectFirst("div.entry-content > p")?.text()?.trim()
        val type = if (tags.joinToString().contains("movies", true)) TvType.Movie else TvType.TvSeries
        //Log.d("desc", description.toString())
        val trailer = fixUrlNull(doc.select("div.rll-youtube-player").attr("data-src").toString())
        //Log.d("trailer", trailer.toString())
        val recommendations = doc.select("div.p-wrap").mapNotNull {
            val titleRawrec = it.selectFirst("a.p-url")?.text()?.toString()?.trim()
                ?: return null
            val titlerec = titleRegex.find(titleRawrec)?.groups?.get(1)?.value.toString()
            //Log.d("title", title)
            val href = fixUrl(it.selectFirst("a.p-url")?.attr("href").toString())
            //Log.d("href", href)
            val posterUrl = it.select("img.featured-img").attr("data-lazy-src")
            //Log.d("mybadposterUrl", posterUrl)
            //Log.d("QualityN", qualityN)
            val quality = getQualityFromString(titleRaw)
            //Log.d("Quality", quality.toString())
            if (href.contains("Movie")) {
                newMovieSearchResponse(titlerec, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                    posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
                    this.quality = quality
                }
            } else {
                newTvSeriesSearchResponse(titlerec, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
                    this.quality = quality
                }
            }
        }

        val data = doc.select("div.wp-block-button a.wp-block-button__link").map {
            PrivateLinks(
                it.text(),
                it.attr("href")
            )
        }

        /*val episodes = ArrayList<Episode>()
        doc.select("section.container > div.border-b").forEach { me ->
            val seasonNum = me.select("button > span").text()
            me.select("div.season-list > a").forEach {
                episodes.add(
                    Episode(
                        data = mainUrl + it.attr("href").toString(),
                        name = it.ownText().toString().removePrefix("Episode ").substring(2),//.replaceFirst(epName.first().toString(), ""),
                        season = titRegex.find(seasonNum)?.value?.toInt(),
                        episode = titRegex.find(it.select("span.flex").text().toString())?.value?.toInt()
                    )
                )
            }
        }*/

        //return if (type == TvType.Movie) {
        return newMovieLoadResponse(title, url, TvType.Movie, data) {
                    this.posterUrl = poster?.trim()
                    posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
                    this.year = year
                    this.plot = if (description!!.contains("privatemoviez", true)) null else description
                    this.tags = tags
                    this.recommendations = recommendations
                    addTrailer(trailer)
            }
        /*} else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster?.trim()
                posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
                this.backgroundPosterUrl = bgposter?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer?.toString())
            }
        }*/
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("mybaddata", data)
        //parse data jsonString
        val link = parseJson<ArrayList<PrivateLinks>>(data)
        //iterate through each link
        link.forEach { me ->
            val doc = app.get(me.sourceLink, interceptor = cfKiller).document
            //get the https://privatemoviez.best/secret?data=blahblah
            val gtlinkRegex = Regex("""console\.log\("(.*?)"\)""")
            //select from the above doc and do regex to get the gtlink
            val gtlink = gtlinkRegex.find(doc.select("body > script:nth-child(2)").toString())?.groups?.get(1)?.value.toString()
            Log.d("mybadprivate", gtlink)
            //bypass gtlinks using the gtlinks_bypass fun
            //Log.d("mybadgit1", gtlinks_bypass(gtlink).toString())
            val bypassedLink = gtlinksBypass(gtlink)
            Log.d("mybadgit2", bypassedLink)
            val finaLink = when (bypassedLink != "") {
                bypassedLink.contains("linkyhash") -> app.get(bypassedLink).document.select("#text-url > a[target]").map { it.attr("href") }.filter { it.contains("gdtot") }.joinToString()
                else -> "null"
            }
            Log.d("mybadfinal", finaLink)
            val gdBotLink = extractGdbot(finaLink)
            Log.d("mybadgdbot", gdBotLink.toString())
            val videoLink = extractGdflix(gdBotLink.toString()).toString()
            Log.d("mybadgdflix", videoLink)

            safeApiCall {
                callback.invoke(
                    ExtractorLink(
                        me.sourceName.toString(),
                        me.sourceName.toString(),
                        videoLink,
                        "",
                        Qualities.Unknown.value
                    )
                )
            }
        }
        /*val doc = app.get("https://privatemoviez.best/secret?data=TVZsSmFYcHJHQVhTL2cxZjlqVnN2V2I0Q21oREFiNXlsd3dra1JuSnZkUWFZaDdCRjMrZ0NEMFVJS3IzeGRDSTo6NgrvCqXCYloczZzxb0K4cQ_e__e_", interceptor = cfKiller).document
        Log.d("mybadprivate", doc.toString())
        val gtlinkRegex = Regex("""console\.log\("(.*?)"\)""")
        val gtlink = gtlinkRegex.find(doc.select("body > script:nth-child(2)").toString())?.groups?.get(1)?.value.toString()
        Log.d("mybadgtlink", gtlink)*/

        return true
    }
}
