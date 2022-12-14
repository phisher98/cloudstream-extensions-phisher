package com.likdev256

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody

class MovieHUBProvider : MainAPI() { // all providers must be an instance of MainAPI
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
        "colors-tamil" to "Colors-Tamil",
        "kalaignar-tv" to "Kalaignar TV",
        "news-gossips" to "News Gossips TV",
        "tamil-tv" to "Tamil TV"
    )

    data class homeDocument (
        @JsonProperty("type"       ) var type       : String?,
        @JsonProperty("html"       ) var html       : String?,
        @JsonProperty("lastbatch"  ) var lastbatch  : Boolean?,
        @JsonProperty("currentday" ) var currentday : String
    )

    private suspend fun queryTVApi(page: Int, query: String): NiceResponse {
        val body = FormBody.Builder()
            .addEncoded("action", "infinite_scroll")
            .addEncoded("page", "$page")
            .addEncoded("currentday", "13.12.22")
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
            .addEncoded("query_args[cat]", "29")
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
            .addEncoded("query_before", "2022-12-14%2018%3A06%3A56")
            .addEncoded("last_post_date", "2022-12-13%2023%3A10%3A55")
            .build()

        return app.post(
            "$mainUrl/?infinity=scrolling",
            requestBody = body,
            referer = "$mainUrl/"
        )parsed<homeDocument>().html
    }

    private suspend fun queryTVsearchApi(query: String): NiceResponse {
        val body = FormBody.Builder()
            .addEncoded("action", "infinite_scroll")
            .addEncoded("page", "1")
            .addEncoded("currentday", "28.11.22")
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
            .addEncoded("query_args[s]", "$query")
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
            .addEncoded("query_args[search_terms][0]", "$query")
            .addEncoded("query_args[search_orderby_title][0]", "wp_c58d0c057a_posts.post_title%20LIKE%20'%7B3b8a4bf4290993b6bc34eef3fe3b6a426ed7c9e299d2fb7178e638a814f0a65a%7D$query%7B3b8a4bf4290993b6bc34eef3fe3b6a426ed7c9e299d2fb7178e638a814f0a65a%7D'")
            .addEncoded("query_args[order]", "DESC")
            .addEncoded("query_before", "2022-12-14%2018%3A14%3A37")
            .addEncoded("last_post_date", "2022-11-28%2000%3A23%3A09")
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

        val document = queryTVApi(
            page,
            query
        ).document

        //Log.d("Document", request.data)
        val home = document.select("article.regular-post").mapNotNull {
                it.toSearchResult()
            }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        //Log.d("Got","got here")
        val title = this.selectFirst("section.entry-body > h3 > a")?.text()?.toString()?.trim() ?: return null
        //Log.d("title", title)
        val href = fixUrl(this.selectFirst("section.entry-body > h3 > a")?.attr("href").toString())
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.selectFirst("div.post-thumb > a > img")?.attr("src"))
        //Log.d("posterUrl", posterUrl.toString())
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = SearchQuality.HD
            }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val TVlist = queryTVsearchApi(
            query
        ).document
        //Log.d("document", document.toString())

        return TVlist.select("div.result-item").mapNotNull {
            val title = it.selectFirst("article > div.details > div.title > a")?.text().toString().trim()
            //Log.d("title", titleS)
            val href = fixUrl(it.selectFirst("article > div.details > div.title > a")?.attr("href").toString())
            //Log.d("href", href)
            val posterUrl = fixUrlNull(it.selectFirst("article > div.image > div.thumbnail > a > img")?.attr("src"))
            //Log.d("posterUrl", posterUrl.toString())
            //Log.d("QualityN", qualityN)
            val quality = getQualityFromString(it.select("div.poster > div.mepo > span").text().toString())
            //Log.d("Quality", quality.toString())
            val type = newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                        this.posterUrl = posterUrl
                        this.quality = quality
                    }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val titleL = doc.selectFirst("div.sheader > div.data > h1")?.text()?.toString()?.trim()
            ?: return null
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleL)?.groups?.get(1)?.value.toString()
        //Log.d("titleL", titleL)
        //Log.d("title", title)
        val poster = fixUrlNull(doc.selectFirst("div.poster > img")?.attr("src"))
        val bgposter = fixUrlNull(
            doc.selectFirst("div.g-item:nth-child(1) > a:nth-child(1) > img:nth-child(1)")
                ?.attr("data-src").toString()
        )
        //Log.d("bgposter", bgposter.toString())
        //Log.d("poster", poster.toString())
        val tags = doc.select("div.sgeneros > a").map { it.text() }
        val year =
            doc.selectFirst("span.date")?.text()?.toString()?.substringAfter(",")?.trim()?.toInt()
        //Log.d("year", year.toString())
        val description = doc.selectFirst("div.wp-content > p > span")?.text()?.trim()
        val type = if (url.contains("movies")) TvType.Movie else TvType.TvSeries
        //Log.d("desc", description.toString())
        val trailer = if (type == TvType.Movie)
            fixUrlNull(
                getEmbed(
                    doc.select("#report-video-button-field > input[name~=postid]").attr("value").toString(),
                    "trailer",
                    url
                ).parsed<TrailerUrl>().embedUrl
            )
        else fixUrlNull(doc.select("iframe.rptss").attr("src").toString())
        //Log.d("trailer", trailer.toString())
        val rating = doc.select("span.dt_rating_vgs").text().toRatingInt()
        //Log.d("rating", rating.toString())
        val duration =
            doc.selectFirst("span.runtime")?.text()?.toString()?.removeSuffix(" Min.")?.trim()
                ?.toInt()
        //Log.d("dur", duration.toString())
        val actors =
            doc.select("div.person").map {
                ActorData(
                    Actor(
                        it.select("div.data > div.name > a").text().toString(),
                        it.select("div.img > a > img").attr("src").toString()
                    ),
                    roleString = it.select("div.data > div.caracter").text().toString(),
                )
            }
        val recommendations = doc.select("#dtw_content_related-2 article").mapNotNull {
            it.toSearchResult()
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
        return newMovieLoadResponse(title, url, TvType.Movie, url+","+doc.select("#report-video-button-field > input[name~=postid]").attr("value").toString()) {
                this.posterUrl = poster?.trim()
                this.backgroundPosterUrl = bgposter?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        /*} else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster?.trim()
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

    data class Subs (
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class StreamData (
        @JsonProperty("file") val file: String,
        @JsonProperty("cdn_img") val cdnImg: String,
        @JsonProperty("hash") val hash: String,
        @JsonProperty("subs") val subs: ArrayList<Subs>? = arrayListOf(),
        @JsonProperty("length") val length: String,
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("backup") val backup: String,
    )

    data class Main (
        @JsonProperty("stream_data") val streamData: StreamData,
        @JsonProperty("status_code") val statusCode: Int,
    )

    data class getEmbed (
        @JsonProperty("status") var status: Boolean?,
        @JsonProperty("statusCode") var statusCode: Int?,
        @JsonProperty("statusText") var statusText: String?,
        @JsonProperty("data") var data: Data?
    )

    data class Data (
        @JsonProperty("filecode") var filecode : String?
    )

    private val hexArray = "0123456789ABCDEF".toCharArray()

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF

            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val link = data.substringBefore(",")
        //Log.d("embedlink", link)
        //val postid = data.substringAfter(",")
        //Log.d("embedlink", postid)
        /*val Embedlink = getEmbed(
            postid,
            "1",
            link
        ).parsed<embedUrl>().embedUrl*/

        val doc = app.get(link).document
        doc.select("div.wp-content > p > span > a[href*=\"filepress\"]").forEach {
            //Log.d("myitboy", it.toString())
            val urlid = it.attr("href").replace("https://filepress.online/file/", "")
            //Log.d("myurlid", urlid)
            val url = "https://gdpress.xyz/e/" + app.get(
                "https://api.filepress.online/api/file/video/streamSB/$urlid",
                referer = "https://filepress.online/"
            ).parsed<getEmbed>().data?.filecode.toString() + "/"

            //Log.d("myurl", url)
            val main = "https://gdpress.xyz"
            val regexID =
                Regex("(embed-[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+|/e/[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
            val id = regexID.findAll(url).map { me ->
                me.value.replace(Regex("(embed-|/e/)"), "")
            }.first()
//        val master = "$main/sources48/6d6144797752744a454267617c7c${bytesToHex.lowercase()}7c7c4e61755a56456f34385243727c7c73747265616d7362/6b4a33767968506e4e71374f7c7c343837323439333133333462353935333633373836643638376337633462333634663539343137373761333635313533333835333763376333393636363133393635366136323733343435323332376137633763373337343732363536313664373336327c7c504d754478413835306633797c7c73747265616d7362"
            val master = "$main/sources48/" + bytesToHex("||$id||||streamsb".toByteArray()) + "/"
            val headers = mapOf(
                "watchsb" to "sbstream",
            )
            val mapped = app.get(
                master.lowercase(),
                headers = headers,
                referer = url,
            ).parsedSafe<Main>()
            // val urlmain = mapped.streamData.file.substringBefore("/hls/")
            M3u8Helper.generateM3u8(
                name,
                mapped?.streamData?.file.toString(),
                url,
                headers = headers
            ).forEach(callback)

            mapped?.streamData?.subs?.map {sub ->
                subtitleCallback.invoke(
                    SubtitleFile(
                        sub.label.toString(),
                        sub.file ?: return@map null,
                    )
                )
            }
        }
        return true
    }
}
