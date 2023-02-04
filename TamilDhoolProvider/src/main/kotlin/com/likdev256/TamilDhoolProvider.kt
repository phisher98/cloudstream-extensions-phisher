package com.likdev256

//import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import org.jsoup.nodes.Element
import okhttp3.FormBody
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TamilDhoolProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.tamildhool.net"
    override var name = "TamilDhool"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "sun-tv" to "Sun TV",
        "vijay-tv" to "Vijay TV",
        "zee-tamil" to "Zee Tamil TV",
        "colors-tamil" to "Colors Tamil",
        "kalaignar-tv" to "Kalaignar TV",
        "news-gossips" to "News Gossips TV",
        "tamil-tv" to "Tamil TV"
    )

    private val dateOne   = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yy"))
    private val dateTwo   = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    private val dateThree = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    data class homeDocument (
        @JsonProperty("type"       ) var type       : String?,
        @JsonProperty("html"       ) var html       : String,
        @JsonProperty("lastbatch"  ) var lastbatch  : Boolean?,
        @JsonProperty("currentday" ) var currentday : String
    )

    private suspend fun queryTVApi(page: Int, query: String): String {
        //Log.d("mygoddate", dateOne)
        val body = FormBody.Builder()
            .addEncoded("action", "infinite_scroll")
            .addEncoded("page", "$page")
            .addEncoded("currentday", dateOne)
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
            .addEncoded("query_before", "$dateTwo%2018%3A06%3A56")
            .addEncoded("last_post_date", "$dateThree%2023%3A10%3A55")
            .build()

        return app.post(
            "$mainUrl/?infinity=scrolling",
            requestBody = body,
            referer = "$mainUrl/"
        ).parsed<homeDocument>().html
    }

    private suspend fun queryTVsearchApi(query: String): String {
        val body = FormBody.Builder()
            .addEncoded("action", "infinite_scroll")
            .addEncoded("page", "1")
            .addEncoded("currentday", dateOne)
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
            .addEncoded("query_before", "$dateTwo%2018%3A14%3A37")
            .addEncoded("last_post_date", "$dateThree%2000%3A23%3A09")
            .build()

        return app.post(
            "$mainUrl/?infinity=scrolling",
            requestBody = body,
            referer = "$mainUrl/"
        ).parsed<homeDocument>().html
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val query = request.data.format(page)

        val document = Jsoup.parse(
            queryTVApi(
                page,
                query
            )
        )
        //Log.d("mygodquery", query)
        //Log.d("mygoddocument", document.toString())
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
        val link = doc.select("div.entry-content > div > iframe").map {
            it.attr("src")
        }.filter { it.toString().contains("thiraione", true) }.jointToString()
        //Log.d("mygodlink", link)
        val episodes = listOf(
            Episode(
                data = link,
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
        val link = data.replace("/p/", "/v/") + ".m3u8"
        //Log.d("mygoddata", link)

        safeApiCall {
            callback.invoke(
                ExtractorLink(
                    "TamilDhool",
                    "TamilDhool",
                    link,
                    data,
                    Qualities.Unknown.value,
                    true
                )
            )
        }
        return true
    }
}
