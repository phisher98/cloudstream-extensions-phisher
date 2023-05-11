package com.likdev256

//import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element
import okhttp3.FormBody
import org.jsoup.Jsoup
//import java.time.LocalDate
//import java.time.format.DateTimeFormatter

class TamilDhoolProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.tamildhool.net"
    override var name = "TamilDhool"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.TvSeries
    )

    private var fetchedParams: ScrollSettings? = null

    override val mainPage = mainPageOf(
        "sun-tv" to "Sun TV",
        "vijay-tv" to "Vijay TV",
        "zee-tamil" to "Zee Tamil TV",
        //"colors-tamil" to "Colors Tamil",
        // The site has removed this catergory for idk reason, Will enable if it's back
        "kalaignar-tv" to "Kalaignar TV",
        "news-gossips" to "News Gossips TV",
        //"tamil-tv" to "Tamil TV"
        //Same shit removed causing home catalouge to fail loading
    )

    // Used temporarily for POST request (Replaced by a more reliable & failsafe method)
    //private val dateOne   = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yy"))
    //private val dateTwo   = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    //private val dateThree = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    data class TamilDhoolLinks(
        @JsonProperty("sourceName") val sourceName: String,
        @JsonProperty("sourceLink") val sourceLink: String
    )

    data class HomeDocument (
        @JsonProperty("type"       ) var type       : String?,
        @JsonProperty("html"       ) var html       : String,
        @JsonProperty("lastbatch"  ) var lastbatch  : Boolean?,
        @JsonProperty("currentday" ) var currentday : String
    )

    data class ScrollSettings (
        @JsonProperty("settings" ) var settings : Settings
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Reduce parsing too many useless stuff which aren't being actively used
    data class Settings (
        @JsonProperty("currentday"       ) var currentday      : String,
        @JsonProperty("query_before"     ) var queryBefore     : String,
        @JsonProperty("last_post_date"   ) var lastPostDate    : String
    )

    // This is used to get some weird date parameters which are essential for the main POST requst
    private suspend fun fetchScrollSettings(query: String): ScrollSettings? {
        return tryParseJson<ScrollSettings>(
            app.get("$mainUrl/$query/").document
            .selectFirst("body > script[type=text/javascript]")?.html()
            ?.removePrefix("var infiniteScroll = ")
            ?.removeSuffix(";")?.trim()
        )
    }


    private suspend fun queryTVApi(page: Int, query: String, infscroll: ScrollSettings?): String {
        //Log.d("mygoddate", dateOne)
        val body = infscroll?.settings?.let {
            FormBody.Builder()
                .addEncoded("action", "infinite_scroll")
                .addEncoded("page", "$page")
                .addEncoded("currentday", it.currentday)
                .addEncoded("order", "DESC")
                .addEncoded("scripts[0]", "jquery-core")
                .addEncoded("scripts[1]", "jquery-migrate")
                .addEncoded("scripts[2]", "jquery")
                .addEncoded("scripts[3]", "unslider-js")
                .addEncoded("scripts[4]", "unslider-move-js")
                .addEncoded("scripts[5]", "unslider-swipe-js")
                .addEncoded("scripts[6]", "advanced-ads-advanced-js")
                .addEncoded("scripts[7]", "the-neverending-homepage")
                .addEncoded("scripts[8]", "rocket-browser-checker")
                .addEncoded("scripts[9]", "rocket-preload-links")
                .addEncoded("scripts[10]", "advanced-ads-pro%2Ffront")
                .addEncoded("scripts[11]", "slicknav")
                .addEncoded("scripts[12]", "flickity")
                .addEncoded("scripts[13]", "fitvids")
                .addEncoded("scripts[14]", "superfish")
                .addEncoded("scripts[15]", "search_button")
                .addEncoded("scripts[16]", "eclipse-script")
                .addEncoded("scripts[17]", "page-links-to")
                .addEncoded("scripts[18]", "advanced-ads-pro%2Fcache_busting")
                .addEncoded("styles[0]", "the-neverending-homepage")
                .addEncoded("styles[1]", "wp-block-library")
                .addEncoded("styles[2]", "classic-theme-styles")
                .addEncoded("styles[3]", "global-styles")
                .addEncoded("styles[4]", "unslider-css")
                .addEncoded("styles[5]", "slider-css")
                .addEncoded("styles[6]", "zoom-theme-utils-css")
                .addEncoded("styles[7]", "eclipse-google-fonts")
                .addEncoded("styles[8]", "eclipse-style")
                .addEncoded("styles[9]", "media-queries")
                .addEncoded("styles[10]", "dashicons")
                .addEncoded("styles[11]", "jetpack_css")
                .addEncoded("query_args[category_name]", query)
                .addEncoded("query_args[error]", "")
                .addEncoded("query_args[m]", "")
                .addEncoded("query_args[p]", "0")
                .addEncoded("query_args[post_parent]", "")
                .addEncoded("query_args[subpost]", "")
                .addEncoded("query_args[subpost_id]", "")
                .addEncoded("query_args[attachment]", "")
                .addEncoded("query_args[attachment_id]", "0")
                .addEncoded("query_args[name]", "")
                .addEncoded("query_args[pagename]", "")
                .addEncoded("query_args[page_id]", "0")
                .addEncoded("query_args[second]", "")
                .addEncoded("query_args[minute]", "")
                .addEncoded("query_args[hour]", "")
                .addEncoded("query_args[day]", "0")
                .addEncoded("query_args[monthnum]", "0")
                .addEncoded("query_args[year]", "0")
                .addEncoded("query_args[w]", "0")
                .addEncoded("query_args[tag]", "")
                .addEncoded("query_args[cat]", "")
                .addEncoded("query_args[tag_id]", "")
                .addEncoded("query_args[author]", "")
                .addEncoded("query_args[author_name]", "")
                .addEncoded("query_args[feed]", "")
                .addEncoded("query_args[tb]", "")
                .addEncoded("query_args[paged]", "0")
                .addEncoded("query_args[meta_key]", "")
                .addEncoded("query_args[meta_value]", "")
                .addEncoded("query_args[preview]", "")
                .addEncoded("query_args[s]", "")
                .addEncoded("query_args[sentence]", "")
                .addEncoded("query_args[title]", "")
                .addEncoded("query_args[fields]", "")
                .addEncoded("query_args[menu_order]", "")
                .addEncoded("query_args[embed]", "")
                .addEncoded("query_args[category__in][]", "")
                .addEncoded("query_args[category__not_in][]", "")
                .addEncoded("query_args[category__and][]", "")
                .addEncoded("query_args[post__in][]", "")
                .addEncoded("query_args[post__not_in][]", "")
                .addEncoded("query_args[post_name__in][]", "")
                .addEncoded("query_args[tag__in][]", "")
                .addEncoded("query_args[tag__not_in][]", "")
                .addEncoded("query_args[tag__and][]", "")
                .addEncoded("query_args[tag_slug__in][]", "")
                .addEncoded("query_args[tag_slug__and][]", "")
                .addEncoded("query_args[post_parent__in][]", "")
                .addEncoded("query_args[post_parent__not_in][]", "")
                .addEncoded("query_args[author__in][]", "")
                .addEncoded("query_args[author__not_in][]", "")
                .addEncoded("query_args[posts_per_page]", "12")
                .addEncoded("query_args[ignore_sticky_posts]", "false")
                .addEncoded("query_args[suppress_filters]", "false")
                .addEncoded("query_args[cache_results]", "true")
                .addEncoded("query_args[update_post_term_cache]", "true")
                .addEncoded("query_args[update_menu_item_cache]", "false")
                .addEncoded("query_args[lazy_load_term_meta]", "true")
                .addEncoded("query_args[update_post_meta_cache]", "true")
                .addEncoded("query_args[post_type]", "")
                .addEncoded("query_args[nopaging]", "false")
                .addEncoded("query_args[comments_per_page]", "50")
                .addEncoded("query_args[no_found_rows]", "false")
                .addEncoded("query_args[order]", "DESC")
                .addEncoded("query_before", it.queryBefore)
                .addEncoded("last_post_date", it.lastPostDate)
                .build()
        }

        return app.post(
            "$mainUrl/?infinity=scrolling",
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"),
            requestBody = body,
            referer = "$mainUrl/"
        ).parsed<HomeDocument>().html
    }

    private suspend fun queryTVsearchApi(query: String): String {
        val scroll = fetchScrollSettings("")
        val body = scroll?.settings?.let {
            FormBody.Builder()
                .addEncoded("action", "infinite_scroll")
                .addEncoded("page", "1")
                .addEncoded("currentday", it.currentday)
                .addEncoded("order", "DESC")
                .addEncoded("scripts[0]", "jquery-core")
                .addEncoded("scripts[1]", "jquery-migrate")
                .addEncoded("scripts[2]", "jquery")
                .addEncoded("scripts[3]", "unslider-js")
                .addEncoded("scripts[4]", "unslider-move-js")
                .addEncoded("scripts[5]", "unslider-swipe-js")
                .addEncoded("scripts[6]", "advanced-ads-advanced-js")
                .addEncoded("scripts[7]", "the-neverending-homepage")
                .addEncoded("scripts[8]", "rocket-browser-checker")
                .addEncoded("scripts[9]", "rocket-preload-links")
                .addEncoded("scripts[10]", "advanced-ads-pro%2Ffront")
                .addEncoded("scripts[11]", "slicknav")
                .addEncoded("scripts[12]", "flickity")
                .addEncoded("scripts[13]", "fitvids")
                .addEncoded("scripts[14]", "superfish")
                .addEncoded("scripts[15]", "search_button")
                .addEncoded("scripts[16]", "eclipse-script")
                .addEncoded("scripts[17]", "page-links-to")
                .addEncoded("scripts[18]", "advanced-ads-pro%2Fcache_busting")
                .addEncoded("styles[0]", "the-neverending-homepage")
                .addEncoded("styles[1]", "wp-block-library")
                .addEncoded("styles[2]", "classic-theme-styles")
                .addEncoded("styles[3]", "global-styles")
                .addEncoded("styles[4]", "unslider-css")
                .addEncoded("styles[5]", "slider-css")
                .addEncoded("styles[6]", "zoom-theme-utils-css")
                .addEncoded("styles[7]", "eclipse-google-fonts")
                .addEncoded("styles[8]", "eclipse-style")
                .addEncoded("styles[9]", "media-queries")
                .addEncoded("styles[10]", "dashicons")
                .addEncoded("styles[11]", "jetpack_css")
                .addEncoded("query_args[s]", query)
                .addEncoded("query_args[error]", "")
                .addEncoded("query_args[m]", "")
                .addEncoded("query_args[p]", "0")
                .addEncoded("query_args[post_parent]", "")
                .addEncoded("query_args[subpost]", "")
                .addEncoded("query_args[subpost_id]", "")
                .addEncoded("query_args[attachment]", "")
                .addEncoded("query_args[attachment_id]", "0")
                .addEncoded("query_args[name]", "")
                .addEncoded("query_args[pagename]", "")
                .addEncoded("query_args[page_id]", "0")
                .addEncoded("query_args[second]", "")
                .addEncoded("query_args[minute]", "")
                .addEncoded("query_args[hour]", "")
                .addEncoded("query_args[day]", "0")
                .addEncoded("query_args[monthnum]", "0")
                .addEncoded("query_args[year]", "0")
                .addEncoded("query_args[w]", "0")
                .addEncoded("query_args[category_name]", "")
                .addEncoded("query_args[tag]", "")
                .addEncoded("query_args[cat]", "")
                .addEncoded("query_args[tag_id]", "")
                .addEncoded("query_args[author]", "")
                .addEncoded("query_args[author_name]", "")
                .addEncoded("query_args[feed]", "")
                .addEncoded("query_args[tb]", "")
                .addEncoded("query_args[paged]", "0")
                .addEncoded("query_args[meta_key]", "")
                .addEncoded("query_args[meta_value]", "")
                .addEncoded("query_args[preview]", "")
                .addEncoded("query_args[sentence]", "")
                .addEncoded("query_args[title]", "")
                .addEncoded("query_args[fields]", "")
                .addEncoded("query_args[menu_order]", "")
                .addEncoded("query_args[embed]", "")
                .addEncoded("query_args[category__in][]", "")
                .addEncoded("query_args[category__not_in][]", "")
                .addEncoded("query_args[category__and][]", "")
                .addEncoded("query_args[post__in][]", "")
                .addEncoded("query_args[post__not_in][0]", "511446")
                .addEncoded("query_args[post_name__in][]", "")
                .addEncoded("query_args[tag__in][]", "")
                .addEncoded("query_args[tag__not_in][]", "")
                .addEncoded("query_args[tag__and][]", "")
                .addEncoded("query_args[tag_slug__in][]", "")
                .addEncoded("query_args[tag_slug__and][]", "")
                .addEncoded("query_args[post_parent__in][]", "")
                .addEncoded("query_args[post_parent__not_in][]", "")
                .addEncoded("query_args[author__in][]", "")
                .addEncoded("query_args[author__not_in][]", "")
                .addEncoded("query_args[posts_per_page]", "12")
                .addEncoded("query_args[ignore_sticky_posts]", "false")
                .addEncoded("query_args[suppress_filters]", "false")
                .addEncoded("query_args[cache_results]", "true")
                .addEncoded("query_args[update_post_term_cache]", "true")
                .addEncoded("query_args[update_menu_item_cache]", "false")
                .addEncoded("query_args[lazy_load_term_meta]", "true")
                .addEncoded("query_args[update_post_meta_cache]", "true")
                .addEncoded("query_args[post_type]", "any")
                .addEncoded("query_args[nopaging]", "false")
                .addEncoded("query_args[comments_per_page]", "50")
                .addEncoded("query_args[no_found_rows]", "false")
                .addEncoded("query_args[search_terms_count]", "1")
                .addEncoded("query_args[search_terms][0]", query)
                .addEncoded("query_args[search_orderby_title][0]", "wp_c58d0c057a_posts.post_title%20LIKE%20'%7B3b8a4bf4290993b6bc34eef3fe3b6a426ed7c9e299d2fb7178e638a814f0a65a%7D$query%7B3b8a4bf4290993b6bc34eef3fe3b6a426ed7c9e299d2fb7178e638a814f0a65a%7D'")
                .addEncoded("query_args[order]", "DESC")
                .addEncoded("query_before", it.queryBefore)
                .addEncoded("last_post_date", it.lastPostDate)
                .build()
        }

        return app.post(
            "$mainUrl/?infinity=scrolling",
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"),
            requestBody = body,
            referer = "$mainUrl/"
        ).parsed<HomeDocument>().html
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val query = request.data.format(page)

        //Log.d("mybadjson", fetchScrollSettings(query).toString())
        //Log.d("mygodquery", query)
        // These params are constant thoughout a session
        // Stop unessasaily loading it for every page thereby increasing performance
        if (page == 1) {
            fetchedParams = fetchScrollSettings(query)
        }

        val document = Jsoup.parse(
            queryTVApi(
                page,
                query,
                fetchedParams
            )
        )
        val home = document.select("article.regular-post").mapNotNull {
            it.toSearchResult()
        }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home, isHorizontalImages = true)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        //Log.d("mygodGot","got here")
        val title = this.selectFirst("section.entry-body > h3 > a")?.text()?.toString()?.trim() ?: return null
        //Log.d("mygodtitle", title)
        val href = fixUrl(this.selectFirst("section.entry-body > h3 > a")?.attr("href").toString())
        //Log.d("mygodhref", href)
        val posterUrl = fixUrlNull(this.selectFirst("div.post-thumb > a > img")?.attr("src"))
        //Log.d("mygodposterUrl", posterUrl.toString())
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.posterHeaders = mapOf("referer" to "$mainUrl/")
            this.quality = SearchQuality.HD
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val TVlist = Jsoup.parse(
            queryTVsearchApi(
                query
            )
        )
        //Log.d("mygoddocument", TVlist.toString())

        return TVlist.select("article.regular-post").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.entry-title")?.text()?.toString()?.trim()
            ?: return null
        val posterRegex = Regex("(https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&\\/\\/=]*jpg))")
        val posterRaw = doc.selectFirst("div.entry-cover")?.attr("style").toString()
        val poster = posterRegex.find(posterRaw)?.value?.trim()
        //Log.d("poster", poster.toString())

        val link = doc.select("div.entry-content iframe").map {
            val sourceName = if (it.attr("src").contains("thirai", true))
                "ThiraiOne" else if (it.attr("src").contains("dailymotion", true))
                "Dailymotion" else if (it.attr("src").contains("dailymotion", true))
                "Youtube" else ""
            TamilDhoolLinks(
                sourceName,
                it.attr("src")
            )
        }

        //Log.d("mygodlink", link)
        val episodes = listOf(
            Episode(
                data = link.toJson(),
                name = title,
                season = 1,
                episode = 1,
                posterUrl = poster
            )
        )

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster?.trim()
            this.posterHeaders = mapOf("referer" to "$mainUrl/")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val link = parseJson<ArrayList<TamilDhoolLinks>>(data)

        //Log.d("mygoddata", link.toString())
        val thiraione   = link.filter { it.toString().contains("thirai", true) }
        val dailymotion = link.filter { it.toString().contains("dailymotion", true) }
        val youtube     = link.filter { it.toString().contains("youtube", true) }

        //Log.d("mygodthirai", thiraione.joinToString())
        //Log.d("mygoddaily", dailymotion.first().sourceLink)
        //Log.d("mygodyou", youtube.joinToString())
        safeApiCall {
            if (thiraione.joinToString().isNotBlank()) {
                callback.invoke(
                    ExtractorLink(
                        thiraione.joinToString { it.sourceName },
                        thiraione.joinToString { it.sourceName },
                        thiraione.joinToString { it.sourceLink }
                            .replace("/p/", "/v/") + ".m3u8",
                        thiraione.joinToString { it.sourceLink },
                        Qualities.Unknown.value,
                        true
                    )
                )
            }
            if (dailymotion.joinToString().isNotBlank()) {
                loadExtractor(dailymotion.first().sourceLink, subtitleCallback, callback)
            }
            if (youtube.joinToString().isNotBlank()) {
                loadExtractor(youtube.joinToString { it.sourceLink }, subtitleCallback, callback)
            }
                // Do nothing thereby failing link loading (No link found)
        }
        return true
    }
}
