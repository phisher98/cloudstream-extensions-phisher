package com.phisher98

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.extractors.helper.GogoHelper
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.jsoup.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.net.URI
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.max


val session = Session(Requests().baseClient)

object StreamPlayExtractor : StreamPlay() {

    suspend fun invokeDreamfilm(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$dreamfilmAPI/$fixTitle"
        } else {
            "$dreamfilmAPI/series/$fixTitle/season-$season/episode-$episode"
        }
        val doc = app.get(url).document
        doc.select("div#videosen a").map {
            val iframe =
                app.get(it.attr("href")).document.selectFirst("div.card-video iframe")
                    ?.attr("data-src")
            loadCustomExtractor(
                "Dreamfilm",
                iframe
                    ?: "",
                "$dreamfilmAPI/",
                subtitleCallback,
                callback,
                Qualities.P1080.value
            )
        }
    }

    @Suppress("NewApi")
    suspend fun invokeMultiEmbed(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$MultiEmbedAPI/directstream.php?video_id=$imdbId"
        } else {
            "$MultiEmbedAPI/directstream.php?video_id=$imdbId&s=$season&e=$episode"
        }
        val res = app.get(url, referer = url).document
        val script =
            res.selectFirst("script:containsData(function(h,u,n,t,e,r))")?.data()
        if (script != null) {
            val firstJS =
                """
        var globalArgument = null;
        function Playerjs(arg) {
        globalArgument = arg;
        };
        """.trimIndent()
            val rhino = Context.enter()
            rhino.setInterpretedMode(true)
            val scope: Scriptable = rhino.initSafeStandardObjects()
            rhino.evaluateString(scope, firstJS + script, "JavaScript", 1, null)
            val file =
                (scope.get("globalArgument", scope).toJson()).substringAfter("file\":\"")
                    .substringBefore("\",")
            callback(
                newExtractorLink(
                    "MultiEmbeded API",
                    "MultiEmbeded API",
                    url = file,
                    type = INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }

    /*
    suspend fun invokeAsianHD(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$AsianhdAPI/watch/$fixTitle-episode-$episode"
        } else {
            "$AsianhdAPI/watch/$fixTitle-$year-episode-$episode"
        }
        val host = AsianhdAPI.substringAfter("//")
        val AsianHDepAPI = "https://api.$host/episodes/detail/"
        val apisuburl = url.substringAfter("watch/")
        val json = app.get(AsianHDepAPI + apisuburl).parsedSafe<AsianHDResponse>()
        json?.data?.links?.forEach { link ->
            if (link.url.contains("asianbxkiun")) {
                val iframe = app.get(httpsify(link.url))
                val iframeDoc = iframe.document
                argamap({
                    iframeDoc.select("#list-server-more ul li")
                        .amap { element ->
                            val extractorData = element.attr("data-video").substringBefore("=http")
                            val dataprovider = element.attr("data-provider")
                            if (dataprovider == "serverwithtoken") return@amap
                            loadExtractor(extractorData, iframe.url, subtitleCallback, callback)
                        }
                }, {
                    val iv = "9262859232435825"
                    val secretKey = "93422192433952489752342908585752"
                    val secretDecryptKey = secretKey
                    GogoHelper.extractVidstream(
                        iframe.url,
                        "AsianHD",
                        callback,
                        iv,
                        secretKey,
                        secretDecryptKey,
                        isUsingAdaptiveKeys = false,
                        isUsingAdaptiveData = true,
                        iframeDocument = iframeDoc
                    )
                })
            } else if (link.url.contains("bulbasaur.online")) {
                val href = app.get(link.url).document.selectFirst("iframe")?.attr("src") ?: ""
                loadExtractor(href, subtitleCallback, callback)
            } else {
                loadExtractor(link.url, subtitleCallback, callback)
            }
        }
    }
     */

    suspend fun invokeMultimovies(
        apiUrl: String,
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val MultimoviesApi= app.get(apiUrl).document.selectFirst("div.sc-bdfBwQ > a")?.attr("href")
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$MultimoviesApi/movies/$fixTitle"
        } else {
            "$MultimoviesApi/episodes/$fixTitle-${season}x${episode}"
        }
        val req = app.get(url).document
        req.select("ul#playeroptionsul li").map {
            Triple(
                it.attr("data-post"),
                it.attr("data-nume"),
                it.attr("data-type")
            )
        }.amap { (id, nume, type) ->
            if (!nume.contains("trailer")) {
                val source = app.post(
                    url = "$MultimoviesApi/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post" to id,
                        "nume" to nume,
                        "type" to type
                    ),
                    referer = url,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).parsed<ResponseHash>().embed_url
                val link = source.substringAfter("\"").substringBefore("\"")
                when {
                    !link.contains("youtube") -> {
                        loadExtractor(link, referer = apiUrl, subtitleCallback, callback)
                    }
                    else -> Log.d("Error", "Not Found")
                }
            }
        }
    }

    suspend fun invokeAoneroom(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers =
            mapOf("Authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjg5OTM3MDA1MDUzOTQ1NDk2OCwiZXhwIjoxNzQxMTk0NDg4LCJpYXQiOjE3MzM0MTg0ODh9.UQ7pslt7o85nuNW14jchnrp7DxpQT0d0vl50kDTXSnI")
        val subjectIds = app.post(
            "$aoneroomAPI/wefeed-mobile-bff/subject-api/search", data = mapOf(
                "page" to "1",
                "perPage" to "10",
                "keyword" to "$title",
                "subjectType" to if (season == null) "1" else "2",
            ), headers = headers
        ).parsedSafe<AoneroomResponse>()?.data?.items?.filter {
            it.title?.contains("$title", ignoreCase = true) ?: return &&
                    it.releaseDate?.substringBefore("-") == year.toString()
        } // Filter by title and release year
            ?.map { it.subjectId }
        subjectIds?.forEach { subjectId ->
            val data = app.get(
                "$aoneroomAPI/wefeed-mobile-bff/subject-api/resource?subjectId=${subjectId ?: return}",
                headers = headers
            ).parsedSafe<AoneroomResponse>()?.data?.list?.findLast {
                it.se == (season ?: 0) && it.ep == (episode ?: 0)
            }
            if (episode != null) {
                app.get(
                    "$aoneroomAPI/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=$season&ep=$episode",
                    headers = headers
                ).parsedSafe<Aoneroomep>()?.data?.streams?.map {
                    val res = it.resolutions.toInt()
                    callback(
                        newExtractorLink(
                            "Aoneroom",
                            "Aoneroom",
                            url = it.url,
                            type = INFER_TYPE
                        ) {
                            this.referer = ""
                            this.quality = res
                        }
                    )
                }
            }

            callback(
                newExtractorLink(
                    "Aoneroom",
                    "Aoneroom",
                    url = data?.resourceLink ?: return,
                    type = INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = data.resolution ?: Qualities.Unknown.value
                }
            )

            data.extCaptions?.map { sub ->
                subtitleCallback.invoke(
                    SubtitleFile(
                        sub.lanName ?: return@map,
                        sub.url ?: return@map,
                    )
                )
            }
        }

    }

    suspend fun invokeWatchCartoon(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$watchCartoonAPI/movies/$fixTitle-$year"
        } else {
            "$watchCartoonAPI/episode/$fixTitle-season-$season-episode-$episode"
        }

        val req = app.get(url)
        val host = getBaseUrl(req.url)
        val doc = req.document

        val id = doc.select("link[rel=shortlink]").attr("href").substringAfterLast("=")
        doc.select("div.form-group.list-server option").amap {
            val server = app.get(
                "$host/ajax-get-link-stream/?server=${it.attr("value")}&filmId=$id",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).text
                loadExtractor(server, "$host/", subtitleCallback) { link ->
                    CoroutineScope(Dispatchers.IO).launch {
                    if (link.quality == Qualities.Unknown.value) {
                        callback(
                            newExtractorLink(
                                "WatchCartoon",
                                "WatchCartoon",
                                url = link.url,
                                type = link.type
                            ) {
                                this.referer = link.referer
                                this.quality = Qualities.P720.value
                                this.headers = link.headers
                                this.extractorData = link.extractorData
                            }
                        )
                    }
                }
            }
        }
    }

    suspend fun invokeZshow(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$zshowAPI/movie/$fixTitle-$year"
        } else {
            "$zshowAPI/episode/$fixTitle-season-$season-episode-$episode"
        }
        invokeWpmovies("ZShow", url, subtitleCallback, callback, encrypt = true)
    }

    private suspend fun invokeWpmovies(
        name: String? = null,
        url: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        fixIframe: Boolean = false,
        encrypt: Boolean = false,
        hasCloudflare: Boolean = false,
        interceptor: Interceptor? = null,
    ) {
        fun String.fixBloat(): String {
            return this.replace("\"", "").replace("\\", "")
        }

        val res = app.get(
            url ?: return,
            interceptor = if (hasCloudflare) interceptor else null
        )
        val referer = getBaseUrl(res.url)
        val document = res.document
        document.select("ul#playeroptionsul > li").map {
            Triple(it.attr("data-post"), it.attr("data-nume"), it.attr("data-type"))
        }.amap { (id, nume, type) ->
            delay(1000)
            val json = app.post(
                url = "$referer/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "doo_player_ajax",
                    "post" to id,
                    "nume" to nume,
                    "type" to type
                ),
                headers = mapOf(
                    "Accept" to "*/*",
                    "X-Requested-With" to "XMLHttpRequest"
                ),
                referer = url,
                interceptor = if (hasCloudflare) interceptor else null
            )
            val source = tryParseJson<ResponseHash>(json.text)?.let {
                when {
                    encrypt -> {
                        val meta =
                            tryParseJson<ZShowEmbed>(it.embed_url)?.meta ?: return@amap
                        val key = generateWpKey(it.key ?: return@amap, meta)
                        cryptoAESHandler(
                            it.embed_url,
                            key.toByteArray(),
                            false
                        )?.fixBloat()
                    }

                    fixIframe -> Jsoup.parse(it.embed_url).select("IFRAME").attr("SRC")
                    else -> it.embed_url
                }
            } ?: return@amap
            when {
                !source.contains("youtube") -> {
                    loadCustomExtractor(
                        name,
                        source,
                        "$referer/",
                        subtitleCallback,
                        callback
                    )
                }
            }
        }
    }

    suspend fun invokeDramaday(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        fun String.getQuality(): String? =
            Regex("""\d{3,4}[pP]""").find(this)?.groupValues?.getOrNull(0)

        fun String.getTag(): String? =
            Regex("""\d{3,4}[pP]\s*(.*)""").find(this)?.groupValues?.getOrNull(1)

        val slug = title.createSlug()
        val epsSlug = getEpisodeSlug(season, episode)
        val url = if (season == null) {
            "$dramadayAPI/$slug-$year/"
        } else {
            "$dramadayAPI/$slug/"
        }
        val res = app.get(url).document
        val servers = if (season == null) {
            val player = res.select("div.tabs__pane p a[href*=https://]").attr("href")
            val ouo = bypassOuo(player)
            app.get(
                ouo
                    ?: return
            ).document.select("article p:matches(\\d{3,4}[pP]) + p:has(a)")
                .flatMap { ele ->
                    val entry = ele.previousElementSibling()?.text() ?: ""
                    ele.select("a").map {
                        Triple(entry.getQuality(), entry.getTag(), it.attr("href"))
                    }.filter {
                        it.third.startsWith("https://pixeldrain.com") || it.third.startsWith(
                            "https://krakenfiles.com"
                        )
                    }
                }
        } else {
            val data = res.select("tbody tr:has(td[data-order=${epsSlug.second}])")
            val qualities =
                data.select("td:nth-child(2)").attr("data-order").split("<br>")
                    .map { it }
            val iframe = data.select("a[href*=https://]").map { it.attr("href") }
            iframe.forEach {
                if (it.contains("pixel")) {
                    loadExtractor(it, subtitleCallback, callback)
                }
                return@forEach
            }
            qualities.zip(iframe).map {
                Triple(it.first.getQuality(), it.first.getTag(), it.second)
            }
        }

        servers.amap {
            val server =
                if (it.third.startsWith("https://ouo")) bypassOuo(it.third) else it.third
            loadCustomTagExtractor(
                it.second,
                server
                    ?: return@amap,
                "$dramadayAPI/",
                subtitleCallback,
                callback,
                getQualityFromName(it.first)
            )
        }

    }

    suspend fun invokeKimcartoon(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val doc = if (season == null) {
            app.get("$kimcartoonAPI/Cartoon/$fixTitle").document
        } else {
            val res = app.get("$kimcartoonAPI/Cartoon/$fixTitle-Season-$season")
            //Log.d("Test res iSelector",res.toString())
            if (res.url == "$kimcartoonAPI/") app.get("$kimcartoonAPI/Cartoon/$fixTitle-Season-0$season").document else res.document
        }
        //Log.d("Test iSelector",doc.toString())
        val iframe = if (season == null) {
            doc.select("table.listing tr td a").firstNotNullOf { it.attr("href") }
        } else {
            doc.select("table.listing tr td a").find {
                it.attr("href").contains(Regex("(?i)Episode-0*$episode"))
            }?.attr("href")
        } ?: return
        val servers =
            app.get(
                fixUrl(
                    iframe,
                    kimcartoonAPI
                )
            ).document.select("#selectServer > option")
                .map { fixUrl(it.attr("value"), kimcartoonAPI) }
        servers.amap {
            app.get(it).document.select("#my_video_1").attr("src").let { iframe ->
                if (iframe.isNotEmpty()) {
                    loadExtractor(iframe, "$kimcartoonAPI/", subtitleCallback, callback)
                }
            }
        }
    }

    suspend fun invokeazseries(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$azseriesAPI/embed/$fixTitle"
        } else {
            "$azseriesAPI/episodes/$fixTitle-season-$season-episode-$episode"
        }
        val res = app.get(url, referer = azseriesAPI)
        val document = res.document
        val id = document.selectFirst("#show_player_lazy")?.attr("movie-id").toString()
        val server_doc = app.post(
            url = "$azseriesAPI/wp-admin/admin-ajax.php", data = mapOf(
                "action" to "lazy_player",
                "movieID" to id
            ), headers = mapOf("X-Requested-With" to "XMLHttpRequest"), referer = azseriesAPI
        ).document
        server_doc.select("div#playeroptions > ul > li").forEach {
            it.attr("data-vs").let { href ->
                val response = app.get(
                    href,
                    referer = azseriesAPI,
                    allowRedirects = false
                ).headers["Location"] ?: ""
                Log.d("AZSeries", response)
                loadExtractor(response, subtitleCallback, callback)
            }
        }
    }

    suspend fun invokeDumpStream(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val (id, type) = getDumpIdAndType(title, year, season)
        val json = fetchDumpEpisodes("$id", "$type", episode) ?: return

        json.subtitlingList?.map { sub ->
            subtitleCallback.invoke(
                SubtitleFile(
                    getVipLanguage(
                        sub.languageAbbr
                            ?: return@map
                    ), sub.subtitlingUrl ?: return@map
                )
            )
        }
    }

    suspend fun invokeMoviehubAPI(
        id: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$MOVIE_API/embed/$id"
        } else {
            "$MOVIE_API/embed/$id/$season/$episode"
        }
        val movieid =
            app.get(url).document.selectFirst("#embed-player")?.attr("data-movie-id")
                ?: return
        app.get(url).document.select("a.server.dropdown-item").forEach {
            val dataid = it.attr("data-id")
            val link = extractMovieAPIlinks(dataid, movieid, MOVIE_API)
            if (link.contains(".stream"))
                loadExtractor(link, referer = MOVIE_API, subtitleCallback, callback)
        }
    }

    suspend fun invokeAnitaku(
        url: String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val subDub = if (url?.contains("-dub") == true) "Dub" else "Sub"
        val epUrl = url?.replace("category/", "")?.plus("-episode-${episode}") ?: return
        val epRes = app.get(epUrl).document

        epRes.select("div.anime_muti_link > ul > li").forEach { item ->
            val iframe = item.selectFirst("a")?.attr("data-video") ?: return@forEach
            val sourceName = item.className()
            if (sourceName.contains("anime")) {
                GogoHelper.extractVidstream(
                    iframe,
                    "Anitaku Vidstreaming [$subDub]",
                    callback,
                    "3134003223491201",
                    "37911490979715163134003223491201",
                    "54674138327930866480207815084989",
                    isUsingAdaptiveKeys = false,
                    isUsingAdaptiveData = true
                )
            } else {
                loadCustomExtractor(
                    "Anitaku $sourceName [$subDub]",
                    iframe,
                    "",
                    subtitleCallback,
                    callback
                )
            }
        }

    }

    private suspend fun invokeTokyoInsider(
        jptitle: String? = null,
        title: String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        fun formatString(input: String?) = input?.replace(" ", "_").orEmpty()

        val jpFixTitle = formatString(jptitle)
        val fixTitle = formatString(title)
        val ep = episode ?: ""

        if (jpFixTitle.isBlank() && fixTitle.isBlank()) return

        var doc =
            app.get("https://www.tokyoinsider.com/anime/S/${jpFixTitle}_(TV)/episode/$ep").document
        if (doc.select("div.c_h2").text().contains("We don't have any files for this episode")) {
            doc =
                app.get("https://www.tokyoinsider.com/anime/S/${fixTitle}_(TV)/episode/$ep").document
        }

        val href = doc.select("div.c_h2 > div:nth-child(1) > a").attr("href")
        if (href.isNotBlank()) {
            callback(
                newExtractorLink(
                    "TokyoInsider",
                    "TokyoInsider",
                    url = href,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }


    suspend fun invokeAnizone(
        jptitle: String? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val href = app.get("https://anizone.to/anime?search=${jptitle}")
            .document
            .select("div.h-6.inline.truncate a")
            .firstOrNull {
                val text = it.text()
                text.equals(jptitle, ignoreCase = true)
            }?.attr("href")
        val m3u8 = href?.let {
            app.get("$it/$episode").document.select("media-player").attr("src")
        } ?: ""
        callback(
            newExtractorLink(
                "Anizone",
                "Anizone",
                url = m3u8,
                INFER_TYPE
            ) {
                this.referer = ""
                this.quality = Qualities.P1080.value
            }
        )
    }


    suspend fun invokeKisskh(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        lastSeason: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val slug = title.createSlug() ?: return
        val type = when {
            season == null -> "2"
            else -> "1"
        }
        val response = app.get(
            "$kissKhAPI/api/DramaList/Search?q=$title&type=$type",
            referer = "$kissKhAPI/"
        ).text
        val res = tryParseJson<ArrayList<KisskhResults>>(response)
        if (res != null) {
            val (id, contentTitle) = if (res.size == 1) {
                res.first().id to res.first().title
            } else {
                val data = res.find {
                    val slugTitle = it.title.createSlug() ?: return
                    when {
                        season == null -> slugTitle == slug
                        lastSeason == 1 -> slugTitle.contains(slug)
                        else -> (slugTitle.contains(slug) && it.title?.contains(
                            "Season $season",
                            true
                        ) == true)
                    }
                } ?: res.find { it.title.equals(title) }
                data?.id to data?.title
            }

            val resDetail = app.get(
                "$kissKhAPI/api/DramaList/Drama/$id?isq=false",
                referer = "$kissKhAPI/Drama/${
                    getKisskhTitle(contentTitle)
                }?id=$id"
            ).parsedSafe<KisskhDetail>() ?: return
            val epsId = if (season == null) {
                resDetail.episodes?.first()?.id
            } else {
                resDetail.episodes?.find { it.number == episode }?.id
            }
            val kkey = app.get("${BuildConfig.KissKh}${epsId}&version=2.8.10", timeout = 10000)
                .parsedSafe<KisskhKey>()?.key ?: ""
            app.get(
                "$kissKhAPI/api/DramaList/Episode/$epsId.png?err=false&ts=&time=&kkey=$kkey",
                referer = "$kissKhAPI/Drama/${getKisskhTitle(contentTitle)}/Episode-${episode ?: 0}?id=$id&ep=$epsId&page=0&pageSize=100"
            ).parsedSafe<KisskhSources>()?.let { source ->
                listOf(source.video, source.thirdParty).amap { link ->
                    if (link?.contains(".m3u8") == true) {
                        M3u8Helper.generateM3u8(
                            "Kisskh",
                            link,
                            "$kissKhAPI/",
                            headers = mapOf("Origin" to kissKhAPI)
                        ).forEach(callback)
                    } else if (link?.contains(".mp4") == true) {
                        loadNameExtractor(
                            "Kisskh",
                            link,
                            referer = null,
                            subtitleCallback,
                            callback,
                            Qualities.P720.value
                        )
                    } else {
                        loadExtractor(
                            link?.substringBefore("=http")
                                ?: return@amap null,
                            "$kissKhAPI/",
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }
            val kkey1 = app.get("${BuildConfig.KisskhSub}${epsId}&version=2.8.10", timeout = 10000)
                .parsedSafe<KisskhKey>()?.key ?: ""
            app.get("$kissKhAPI/api/Sub/$epsId&kkey=$kkey1").text.let { resSub ->
                tryParseJson<List<KisskhSubtitle>>(resSub)?.map { sub ->
                    val lang = getLanguage(sub.label) ?: "UnKnown"
                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang, sub.src
                                ?: return@map
                        )
                    )
                }
            }
        }
    }

    suspend fun invokeAnimes(
        title: String?,
        jptitle: String? = null,
        epsTitle: String? = null,
        date: String?,
        airedDate: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val (aniId, malId) = convertTmdbToAnimeId(
            title, date, airedDate, if (season == null) TvType.AnimeMovie else TvType.Anime
        )

        val jikanSeason = malId?.let {
            runCatching { app.get("$jikanAPI/anime/$it").parsedSafe<JikanResponse>()?.data?.season }
                .getOrNull()
        }

        val malsync = malId?.let {
            runCatching {
                app.get("$malsyncAPI/mal/anime/$it").parsedSafe<MALSyncResponses>()?.sites
            }
                .getOrNull()
        }

        val zoro = malsync?.zoro
        val zoroIds = zoro?.keys?.toList().orEmpty()
        val zorotitle = zoro?.values?.firstNotNullOfOrNull { it["title"] }?.replace(":", " ")
        val hianimeUrl = zoro?.values?.firstNotNullOfOrNull { it["url"] }
        val animepaheTitle = malsync?.animepahe?.values?.firstNotNullOfOrNull { it["title"] }
        val kaasSlug = malsync?.KickAssAnime?.values?.firstNotNullOfOrNull { it["identifier"] }
        val animepaheUrl = malsync?.animepahe?.values?.firstNotNullOfOrNull { it["url"] }
        val gogoUrl = malsync?.Gogoanime?.values?.firstNotNullOfOrNull { it["url"] }
        val tmdbYear = date?.substringBefore("-")?.toIntOrNull()
        val jptitleSlug = jptitle.createSlug()

        runAllAsync(
            { malId?.let { invokeAnimetosho(it, season, episode, subtitleCallback, callback) } },
            { invokeHianime(zoroIds, hianimeUrl, episode, subtitleCallback, callback) },
            { malId?.let { invokeAnimeKai(jptitle,zorotitle,it, episode, subtitleCallback, callback) } },
            {
                animepaheTitle?.let {
                    invokeMiruroanimeGogo(
                        zoroIds,
                        it,
                        episode,
                        subtitleCallback,
                        callback
                    )
                }
            },
            { kaasSlug?.let { invokeKickAssAnime(it, episode, subtitleCallback, callback) } },
            { animepaheUrl?.let { invokeAnimepahe(it, episode, subtitleCallback, callback) } },
            { invokeGrani(title.orEmpty(), episode, callback) },
            { invokeGojo(aniId, jptitleSlug, episode, subtitleCallback, callback) },
            { invokeAnichi(zorotitle,tmdbYear,episode, subtitleCallback, callback) },
            { invokeAnimeOwl(zorotitle, episode, subtitleCallback, callback) },
            { gogoUrl?.let { invokeAnitaku(it, episode, subtitleCallback, callback) } },
            { invokeTokyoInsider(jptitle, title, episode, subtitleCallback, callback) },
            { invokeAnizone(jptitle, episode, callback) })
    }


    suspend fun invokeAnichi(
        name: String?,
        year: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val privatereferer = "https://allmanga.to"
        val ephash = "5f1a64b73793cc2234a389cf3a8f93ad82de7043017dd551f38f65b89daa65e0"
        val queryhash = "06327bc10dd682e1ee7e07b6db9c16e9ad2fd56c1b769e47513128cd5c9fc77a"
        var type = ""
        if (episode == null) {
            type = "Movie"
        } else {
            type = "TV"
        }

        val query =
            """${BuildConfig.ANICHI_API}?variables={"search":{"types":["$type"],"year":$year,"query":"$name"},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$queryhash"}}"""
        val response =
            app.get(query, referer = privatereferer).parsedSafe<AnichiRoot>()?.data?.shows?.edges
        if (response != null) {
            val id = response.firstOrNull()?.id ?: return
            val langType = listOf("sub", "dub")
            for (i in langType) {
                val epData =
                    """${BuildConfig.ANICHI_API}?variables={"showId":"$id","translationType":"$i","episodeString":"${episode ?: 1}"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$ephash"}}"""
                val eplinks = app.get(epData, referer = privatereferer)
                    .parsedSafe<AnichiEP>()?.data?.episode?.sourceUrls
                eplinks?.amap { source ->
                    safeApiCall {
                        val headers =
                            mapOf(
                                "app-version" to "android_c-247",
                                "platformstr" to "android_c",
                                "Referer" to "https://allmanga.to"
                            )
                        val sourceUrl = source.sourceUrl
                        if (sourceUrl.startsWith("http")) {
                                val sourcename = sourceUrl.getHost()
                                loadCustomExtractor(
                                    "Allanime [${i.uppercase()}] [$sourcename]",
                                    sourceUrl
                                        ?: "",
                                    "",
                                    subtitleCallback,
                                    callback,
                                )
                            } else {
                            val decodedlink=if (sourceUrl.startsWith("--"))
                            {
                                decrypthex(sourceUrl)
                            }
                            else sourceUrl
                            val fixedLink = decodedlink.fixUrlPath()
                            val links = try {
                                app.get(fixedLink, headers=headers).parsedSafe<AnichiVideoApiResponse>()?.links ?: emptyList()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                return@safeApiCall
                            }
                            links.forEach { server ->
                                val host = server.link.getHost()
                                when {
                                    source.sourceName.contains("Default") && (server.resolutionStr == "SUB" || server.resolutionStr == "Alt vo_SUB") -> {
                                        getM3u8Qualities(
                                            server.link,
                                            "https://static.crunchyroll.com/",
                                            host
                                        ).forEach(callback)
                                    }

                                    server.hls == null -> {
                                        callback.invoke(
                                            newExtractorLink(
                                                "Allanime ${host.capitalize()}",
                                                "Allanime ${host.capitalize()}",
                                                server.link,
                                                INFER_TYPE
                                            )
                                            {
                                                this.quality=Qualities.P1080.value
                                            }
                                        )
                                    }

                                    server.hls == true -> {
                                        val endpoint = "https://allanime.day/player?uri=" +
                                                (if (URI(server.link).host.isNotEmpty())
                                                    server.link
                                                else "https://allanime.day" + URI(server.link).path)

                                        getM3u8Qualities(server.link, server.headers?.referer ?: endpoint, host).forEach(callback)
                                    }

                                    else -> {
                                        server.subtitles?.forEach { sub ->
                                            val lang = SubtitleHelper.fromTwoLettersToLanguage(sub.lang ?: "") ?: sub.lang.orEmpty()
                                            val src = sub.src ?: return@forEach
                                            subtitleCallback(SubtitleFile(lang, httpsify(src)))
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
    }

    suspend fun invokeAnimeOwl(
        name: String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixtitle = name.createSlug()
        val url = "$AnimeOwlAPI/anime/$fixtitle"
        app.get(url).document.select("#anime-cover-sub-content, #anime-cover-dub-content").amap {
            val subtype = if (it.id() == "anime-cover-sub-content") "SUB" else "DUB"
            val href = it.select(".episode-node")
                .firstOrNull { element -> element.text().contains("$episode") }?.select("a")
                ?.attr("href")
            if (href != null)
                loadCustomExtractor(
                    "AnimeOwl [$subtype]",
                    href,
                    AnimeOwlAPI,
                    subtitleCallback,
                    callback,
                    Qualities.P1080.value
                )
        }
    }

    suspend fun invokeAnimepahe(
        url: String,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf("Cookie" to "__ddg2_=1234567890")
        val id = app.get(url, headers).document.selectFirst("meta[property=og:url]")
            ?.attr("content").toString().substringAfterLast("/")
        val animeData =
            app.get("$animepaheAPI/api?m=release&id=$id&sort=episode_desc&page=1", headers)
                .parsedSafe<animepahe>()?.data
        var session = animeData?.find { it.episode == episode }?.session ?: ""
        if (session.isEmpty()) session =
            animeData?.find { it.episode == (episode?.plus(12) ?: episode) }?.session ?: ""
        val document=app.get("$animepaheAPI/play/$id/$session", headers).document

        document.select("#resolutionMenu button")
            .map {
                val dubText = it.select("span").text().lowercase()
                val type = if ("eng" in dubText) "DUB" else "SUB"

                val qualityRegex = Regex("""(.+?)\s+·\s+(\d{3,4}p)""")
                val text = it.text()
                val match = qualityRegex.find(text)

                val source = match?.groupValues?.getOrNull(1)?.trim() ?: "Unknown"
                val quality = match?.groupValues?.getOrNull(2)?.substringBefore("p")?.toIntOrNull()
                    ?: Qualities.Unknown.value

                val href = it.attr("data-src")
                if ("kwik.si" in href) {
                    loadCustomExtractor(
                        "Animepahe $source [$type]",
                        href,
                        "",
                        subtitleCallback,
                        callback,
                        quality
                    )
                }
            }

        document.select("div#pickDownload > a").amap {
            val qualityRegex = Regex("""(.+?)\s+·\s+(\d{3,4}p)""")

            val href = it.attr("href")
            var type = "SUB"
            if(it.select("span").text().contains("eng"))
                type="DUB"
            val text = it.text()
            val match = qualityRegex.find(text)
            val source = match?.groupValues?.getOrNull(1) ?: "Unknown"
            val quality = match?.groupValues?.getOrNull(2)?.substringBefore("p") ?: "Unknown"

            loadCustomExtractor(
                "Animepahe Pahe $source [$type]",
                href,
                "",
                subtitleCallback,
                callback,
                quality.toIntOrNull()
            )
        }
    }

    suspend fun invokeGrani(
        title: String,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        app.get("https://grani.me/search/$title").document.selectFirst("div.iep a")?.attr("href")
            ?.let {
                val document = app.get(it).document
                val href = document.select("a.infovan")
                    .firstOrNull {
                        it.selectFirst("div.infoept2 div.centerv")?.text() == episode.toString()
                    }
                    ?.attr("href") ?: ""
                val iframe = app.get(href).document.select("#iframevideo").attr("src")
                callback(
                    newExtractorLink(
                        "Grani",
                        "Grani",
                        url = iframe,
                        INFER_TYPE
                    ) {
                        this.referer = ""
                        this.quality = Qualities.P1080.value
                    }
                )
            }
    }

    private suspend fun invokeGojo(
        aniid: Int? = null,
        jptitle: String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val sourcelist = listOf("shashh", "roro", "vibe")
        for (source in sourcelist) {
            val headers = mapOf("Origin" to "https://gojo.wtf")
            val endpoint = "${BuildConfig.GojoAPI}/api/anime/tiddies?provider=$source&id=$aniid&watchId=$jptitle-episode-$episode"
            val response = if (source == "shashh") {
                app.get(endpoint, headers = headers).parsedSafe<Gojoresponseshashh>()?.sources?.map { it.url }
            } else {
                app.get(endpoint, headers = headers).parsedSafe<Gojoresponsevibe>()?.sources?.map { it.url }
            }
            val m3u8 = response?.firstOrNull().orEmpty()

            callback(
                newExtractorLink(
                    "GojoAPI [${source.capitalize()}]",
                    "GojoAPI [${source.capitalize()}]",
                    url = m3u8,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = ""
                    this.quality = Qualities.P1080.value
                    this.headers = headers
                }
            )
        }
    }

    private suspend fun invokeAnimetosho(
        malId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        fun Elements.getLinks(): List<Triple<String, String, Int>> {
            return this.flatMap { ele ->
                ele.select("div.links a:matches(KrakenFiles|GoFile)").map {
                    Triple(
                        it.attr("href"),
                        ele.select("div.size").text(),
                        getIndexQuality(ele.select("div.link a").text())
                    )
                }
            }
        }

        val (seasonSLug, episodeSlug) = getEpisodeSlug(season, episode)
        val jikan =
            app.get("$jikanAPI/anime/$malId/full").parsedSafe<JikanResponse>()?.data
        val aniId =
            jikan?.external?.find { it.name == "AniDB" }?.url?.substringAfterLast("=")
        for (i in 1..3) {
            val res =
                app.get("$animetoshoAPI/series/${jikan?.title?.createSlug()}.$aniId?filter[0][t]=nyaa_class&filter[0][v]=trusted&page=$i").document
            val servers = if (season == null) {
                res.select("div.home_list_entry:has(div.links)").getLinks()
            } else {
                res.select("div.home_list_entry:has(div.link a:matches([\\.\\s]$episodeSlug[\\.\\s]|S${seasonSLug}E$episodeSlug))")
                    .getLinks()
            }
            servers.filter {
                it.third in arrayOf(
                    Qualities.P1080.value,
                    Qualities.P720.value
                )
            }.map {
                loadCustomTagExtractor(
                    it.second,
                    it.first,
                    "$animetoshoAPI/",
                    subtitleCallback,
                    callback,
                    it.third
                )
            }
        }
    }

    @SuppressLint("NewApi")
    suspend fun invokeAnimeKai(
        jptitle: String? = null,
        title: String? = null,
        malId: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (jptitle.isNullOrBlank() || title.isNullOrBlank()) return

        try {
            // Perform the search requests sequentially but avoid redundant requests
            val searchEnglish = app.get("$AnimeKai/ajax/anime/search?keyword=$title").body.string()
            val searchRomaji = app.get("$AnimeKai/ajax/anime/search?keyword=$jptitle").body.string()

            val resultsEng = parseAnimeKaiResults(searchEnglish)
            val resultsRom = parseAnimeKaiResults(searchRomaji)

            val combined = (resultsEng + resultsRom).distinctBy { it.id }

            // Find the best match based on title similarity
            var bestMatch: AnimeKaiSearchResult? = null
            var highestScore = 0.0

            for (result in combined) {
                val engScore = similarity(title, result.title)
                val romScore = similarity(jptitle, result.japaneseTitle ?: "")
                val score = max(engScore, romScore)

                if (score > highestScore) {
                    highestScore = score
                    bestMatch = result
                }
            }

            bestMatch?.let { match ->
                val decoder = AnimekaiDecoder()
                val homekey = getHomeKeys()
                val matchedId = match.id
                val href = "$AnimeKai/watch/$matchedId"

                // Fetch anime details and episode list
                val animeId = app.get(href).document.selectFirst("div.rate-box")?.attr("data-id")
                val epRes = app.get("$AnimeKai/ajax/episodes/list?ani_id=$animeId&_=${decoder.generateToken(animeId ?: "", homeKeysSrc = homekey)}")
                    .parsedSafe<AnimeKaiResponse>()?.getDocument()

                epRes?.select("div.eplist a")?.forEach { ep ->
                    val epNum = ep.attr("num").toIntOrNull()
                    if (epNum == episode) {
                        val token = ep.attr("token")

                        // Fetch episode links for this episode
                        val document = app.get("$AnimeKai/ajax/links/list?token=$token&_=${decoder.generateToken(token, homekey)}")
                            .parsed<AnimeKaiResponse>()
                            .getDocument()

                        val types = listOf("sub", "softsub", "dub")
                        val servers = types.flatMap { type ->
                            document.select("div.server-items[data-id=$type] span.server[data-lid]").map { server ->
                                val lid = server.attr("data-lid")
                                val serverName = server.text()
                                Triple(type, lid, serverName)
                            }
                        }

                        // Process each server sequentially
                        for ((type, lid, serverName) in servers) {
                            val result = app.get("$AnimeKai/ajax/links/view?id=$lid&_=${decoder.generateToken(lid, homekey)}")
                                .parsed<AnimeKaiResponse>().result
                            val homekeys = getHomeKeys()
                            val iframe = extractVideoUrlFromJsonAnimekai(decoder.decodeIframeData(result, homekeys))

                            val nameSuffix = when {
                                type.contains("soft", ignoreCase = true) -> " [Soft Sub]"
                                type.contains("sub", ignoreCase = true) -> " [Sub]"
                                type.contains("dub", ignoreCase = true) -> " [Dub]"
                                else -> ""
                            }

                            val name = "⌜ AnimeKai ⌟  |  $serverName  | $nameSuffix"
                            loadExtractor(iframe, name, subtitleCallback, callback)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun parseAnimeKaiResults(jsonResponse: String): List<AnimeKaiSearchResult> {
        val results = mutableListOf<AnimeKaiSearchResult>()
        val html = JSONObject(jsonResponse).optJSONObject("result")?.optString("html") ?: return results
        val doc = Jsoup.parse(html)

        for (element in doc.select("a.aitem")) {
            val href = element.attr("href").substringAfterLast("/") ?: continue
            val titleElem = element.selectFirst("h6.title") ?: continue
            val title = titleElem.text().trim()
            val jpTitle = titleElem.attr("data-jp").trim().takeIf { it.isNotBlank() }

            results.add(AnimeKaiSearchResult(href, title, jpTitle))
        }

        return results
    }

    private fun similarity(a: String?, b: String?): Double {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return 0.0
        val tokensA = a.lowercase().split(Regex("\\W+")).toSet()
        val tokensB = b.lowercase().split(Regex("\\W+")).toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val intersection = tokensA.intersect(tokensB).size
        return intersection.toDouble() / max(tokensA.size, tokensB.size)
    }

    data class AnimeKaiSearchResult(
        val id: String,
        val title: String,
        val japaneseTitle: String? = null
    )


    /*
    @SuppressLint("NewApi")
    suspend fun invokeAnimeKai(
        malId: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "x-atx" to "12RmYtJexlqnNym38z4ahwy+g1g0la/El8nkkMOVtiQ=",
            "Origin" to "https://www.miruro.tv"
        )
        val response=app.get(
            "https://www.miruro.to/api/episodes?malId=$malId",
            headers = headers
        ).text
        val miroTV: MiroTV? = Gson().fromJson(response, object : TypeToken<MiroTV>() {}.type)
        val token = miroTV?.ANIMEKAI
            ?.asSequence()
            ?.mapNotNull { it.value.episodeList.episodes.find { ep -> ep.number == episode }?.id }
            ?.firstOrNull() ?: ""
        val decoder = AnimekaiDecoder()
        val servers = listOf("sub", "dub", "softsub").flatMap { type ->
            val url = "$AnimeKai/ajax/links/list?token=$token&_=${decoder.generateToken(token,
                emptyList() //TODO
            )}"
            app.get(url)
                .parsed<AnimeKaiResponse>()
                .getDocument()
                .select("div.server-items[data-id=$type] span.server[data-lid]") // Selects spans with server info
                .map { server ->
                    val lid = server.attr("data-lid")
                    val serverName = server.text()
                    Triple(type, lid, serverName)
                }
        }.distinct()

        servers.amap { (type, lid, serverName) ->
            val result =
                app.get("$AnimeKai/ajax/links/view?id=$lid&_=${decoder.generateToken(lid,
                    emptyList()
                )}")
                    .parsed<AnimeKaiResponse>().result

            val iframe = extractVideoUrlFromJson(decoder.decodeIframeData(result, emptyList()))
            val nameSuffix = if (type == "softsub") " [Soft Sub]" else "[${type.uppercase()}]"
            val name = "⌜ AnimeKai ⌟  |  $serverName  | $nameSuffix | "
            loadExtractor(iframe, name, subtitleCallback, callback)
        }

    }


     */

    internal suspend fun invokeHianime(
        animeIds: List<String?>? = null,
        url: String?,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "X-Requested-With" to "XMLHttpRequest",
        )
        animeIds?.amap { id ->
            val episodeId = app.get(
                "$hianimeAPI/ajax/v2/episode/list/${id ?: return@amap}",
                headers = headers
            ).parsedSafe<HianimeResponses>()?.html?.let {
                Jsoup.parse(it)
            }?.select("div.ss-list a")
                ?.find { it.attr("data-number") == "${episode ?: 1}" }
                ?.attr("data-id")
            val servers = app.get(
                "$hianimeAPI/ajax/v2/episode/servers?episodeId=${episodeId ?: return@amap}",
                headers = headers
            ).parsedSafe<HianimeResponses>()?.html?.let { Jsoup.parse(it) }
                ?.select("div.item.server-item")?.map {
                    Triple(
                        it.text(),
                        it.attr("data-id"),
                        it.attr("data-type"),
                    )
                }

            val gson = Gson()

            servers?.map servers@{ server ->
                val animeEpisodeId = url?.substringAfter("to/")
                val serverName = if (server.third.equals("HD-1", ignoreCase = true)) "vidstreaming" else "vidcloud"
                val dubtype=if (server.third == "raw") "sub" else server.third
                //val api = "${BuildConfig.HianimeAPI}?animeEpisodeId=$animeEpisodeId?ep=$episodeId&server=${server.first.lowercase()}&category=${server.third}"
                val api= "${BuildConfig.HianimeAPI}episodeId=$animeEpisodeId${'$'}episode$$episodeId$$dubtype&server=$serverName"

                try {
                    val responseText = app.get(api, referer = api).text

                    val response = try {
                        gson.fromJson(responseText, HiAnimeResponse::class.java)
                    } catch (e: JsonSyntaxException) {
                        null
                    }

                    if (response != null) {
                        response.sources.firstOrNull { it.isM3U8 }?.let { source ->
                            val m3u8headers = mapOf(
                                "Referer" to "https://megacloud.club/",
                                "Origin" to "https://megacloud.club/"
                            )
                            M3u8Helper.generateM3u8(
                                "⌜ HiAnime ⌟ | ${server.third.uppercase()} | ${server.first.uppercase()}",
                                source.url,
                                mainUrl,
                                headers = m3u8headers
                            ).forEach(callback)
                        }


                        response.subtitles.forEach { subtitle ->
                            subtitleCallback.invoke(
                                SubtitleFile(
                                    lang = subtitle.lang,
                                    url = subtitle.url
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }


    suspend fun invokeKickAssAnime(
        slug: String?,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val json = app.get("$KickassAPI/api/show/$slug/episodes?ep=1&lang=ja-JP").toString()
        val jsonresponse = parseJsonToEpisodes(json)

        val matchedSlug = jsonresponse.firstOrNull {
            it.episode_number.toString().substringBefore(".").toIntOrNull() == episode
        }?.slug ?: return

        val href = "$KickassAPI/api/show/$slug/episode/ep-$episode-$matchedSlug"
        val servers = app.get(href).parsedSafe<ServersResKAA>()?.servers ?: return

        servers.firstOrNull { it.name.contains("VidStreaming") }?.let { server ->
            val host = getBaseUrl(server.src)
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )

            val key = "e13d38099bf562e8b9851a652d2043d3".toByteArray()
            val query = server.src.substringAfter("?id=").substringBefore("&")
            val html = app.get(server.src).toString()

            val (sig, timeStamp, route) = getSignature(html, server.name, query, key) ?: return
            val sourceUrl = "$host$route?id=$query&e=$timeStamp&s=$sig"

            val encJson =
                app.get(sourceUrl, headers = headers).parsedSafe<EncryptedKAA>()?.data ?: return
            val (encryptedData, ivHex) = encJson.substringAfter(":\"").substringBefore('"')
                .split(":")
            val decrypted = tryParseJson<m3u8KAA>(
                CryptoAES.decrypt(encryptedData, key, ivHex.decodeHex()).toJson()
            ) ?: return

            val m3u8 = httpsify(decrypted.hls)
            val videoHeaders = mapOf(
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.5",
                "Origin" to host,
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site"
            )

            callback(
                ExtractorLink(
                    "VidStreaming", "VidStreaming", m3u8, "", Qualities.P1080.value,
                    type = ExtractorLinkType.M3U8, headers = videoHeaders
                )
            )

            decrypted.subtitles.forEach { subtitle ->
                subtitleCallback(SubtitleFile(subtitle.name, subtitle.src))
            }
        } ?: Log.d("Error:", "Not Found")
    }


    suspend fun invokeMiruroanimeGogo(
        animeIds: List<String?>? = null,
        title: String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = "https://gamma.miruro.tv/?url=https://api.miruro.tv"
        val header = mapOf("x-atx" to "12RmYtJexlqnNym38z4ahwy+g1g0la/El8nkkMOVtiQ=")
        val fixtitle = title.createSlug()
        val sub = "$api/meta/anilist/watch/$fixtitle-episode-$episode"
        val dub = "$api/meta/anilist/watch/$fixtitle-dub-episode-$episode"
        val list = listOf(sub, dub)
        for (url in list) {
            val json = app.get(url, header).parsedSafe<MiruroanimeGogo>()?.sources
            json?.amap {
                val href = it.url
                var quality = it.quality
                if (quality.contains("backup")) {
                    quality = "Master"
                }
                val type = if (url.contains("-dub-")) "DUB" else "SUB"
                if (quality != "Master")
                    loadNameExtractor(
                        "Miruro Gogo [$type]",
                        href,
                        "",
                        subtitleCallback,
                        callback,
                        getIndexQuality(quality)
                    )
            }
        }
    }


    suspend fun invokeAnimenexus(
        title: String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val apiurl = "https://api.anime.nexus"
        val api =
            "$apiurl/api/anime/shows?search=$title&sortBy=name+asc&page=1&includes%5B%5D=poster&includes%5B%5D=genres&hasVideos=1"
        val apires = app.get(api, interceptor = UserAgentInterceptor()).parsedSafe<AnimeNexus>()
        val id = apires?.data?.find {
            it.name.equals(
                "$title", true
            )
        }?.id
        val gson = Gson()
        val epjson =
            app.get("$apiurl/api/anime/details/episodes?id=$id&page=1&perPage=24&order=asc",interceptor = UserAgentInterceptor())
                .toString()
        val animeNexusEp = gson.fromJson(epjson, AnimeNexusEp::class.java)
        val epres = animeNexusEp.data
        val epid = epres.find { it.number == episode }?.id
        val iframe = app.get("$apiurl/api/anime/details/episode/stream?id=$epid",interceptor = UserAgentInterceptor())
            .parsedSafe<AnimeNexusservers>()?.data
        val m3u8 = iframe?.hls ?: ""
        callback(
            newExtractorLink(
                "Animenexus",
                "Animenexus",
                url = m3u8,
                type = INFER_TYPE
            ) {
                this.referer = "https://anime.nexus/"
                this.quality = Qualities.P1080.value
            }
        )
    }

    suspend fun invokeLing(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title?.replace("–", "-")
        val url = if (season == null) {
            "$lingAPI/en/videos/films/?title=$fixTitle"
        } else {
            "$lingAPI/en/videos/serials/?title=$fixTitle"
        }

        val scriptData =
            app.get(url).document.select("div.blk.padding_b0 div.col-sm-30").map {
                Triple(
                    it.selectFirst("div.video-body h5")?.text(),
                    it.selectFirst("div.video-body > p")?.text(),
                    it.selectFirst("div.video-body a")?.attr("href"),
                )
            }

        val script = if (scriptData.size == 1) {
            scriptData.first()
        } else {
            scriptData.find {
                it.first?.contains(
                    "$fixTitle",
                    true
                ) == true && it.second?.contains("$year") == true
            }
        }

        val doc = app.get(fixUrl(script?.third ?: return, lingAPI)).document
        val iframe = (if (season == null) {
            doc.selectFirst("a.video-js.vjs-default-skin")?.attr("data-href")
        } else {
            doc.select("div.blk div#tab_$season li")[episode!!.minus(1)].select("h5 a")
                .attr("data-href")
        })?.let { fixUrl(it, lingAPI) }

        val source = app.get(iframe ?: return)
        val link =
            Regex("((https:|http:)//.*\\.mp4)").find(source.text)?.value ?: return
        callback(
            newExtractorLink(
                "Ling",
                "Ling",
                url = "$link/index.m3u8",
                type = INFER_TYPE
            ) {
                this.referer = "$lingAPI/"
                this.quality = Qualities.P720.value
            }
        )

        source.document.select("div#player-tracks track").map {
            subtitleCallback.invoke(
                SubtitleFile(
                    SubtitleHelper.fromTwoLettersToLanguage(it.attr("srclang"))
                        ?: return@map null, it.attr("src")
                )
            )
        }

    }

    suspend fun invokeUhdmovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit,
        maxRetries: Int = 3,
        delayBetweenRetries: Long = 2000L
    ) {
        var retries = 0

        while (retries < maxRetries) {
            try {
                val fixTitle = title?.replace("-", " ")?.replace(":", " ") ?: return
                val searchTitle = fixTitle.createSlug()
                val (seasonSlug) = getEpisodeSlug(season, episode)

                val uhdmoviesAPI = try {
                    app.get("$modflixAPI/?type=uhdmovies", allowRedirects = false)
                        .document
                        .selectFirst("meta[http-equiv=refresh]")
                        ?.attr("content")
                        ?.substringAfter("url=")
                        ?.takeIf { it.startsWith("http") }
                } catch (e: Exception) {
                    Log.e("UHDMovies", "Failed to fetch redirect: ${e.localizedMessage}")
                    return
                }

                val searchUrl = "$uhdmoviesAPI/search/$fixTitle $year"
                val searchResults = try {
                    app.get(searchUrl).document.select("#content article")
                } catch (e: Exception) {
                    Log.e("UHDMovies", "Search failed: ${e.localizedMessage}")
                    return
                }

                val hrefPatternRegex = Regex("""(?i)<a\s+href="([^"]+)"[^>]*title="[^"]*\b$searchTitle\b[^"]*\b$year\b[^"]*"[^>]*>""")
                val seasonPattern = season?.let { "(?i)(S0?$it|Season 0?$it)" }
                val episodePattern = episode?.let { "(?i)(Episode $it)" }

                searchResults.forEach { article ->
                    try {
                        val hrefPattern = hrefPatternRegex.find(article.toString())?.groupValues?.get(1) ?: return@forEach
                        val doc = app.get(hrefPattern).document

                        val selector = if (season == null) {
                            "div.entry-content p:matches($year)"
                        } else {
                            "div.entry-content p:matches($seasonPattern)"
                        }
                        val epSelector = if (season == null) {
                            "a:matches((?i)(Download))"
                        } else {
                            "a:matches($episodePattern)"
                        }

                        val links = doc.select(selector).mapNotNull {
                            it.nextElementSibling()?.select(epSelector)?.attr("href")
                        }

                        links.amap {
                            try {
                                if (it.isNotEmpty()) {
                                    val driveLink = bypassHrefli(it) ?: return@amap
                                    if (driveLink.isNotEmpty()) {
                                        loadSourceNameExtractor(
                                            "UHDMovies",
                                            driveLink,
                                            "",
                                            subtitleCallback,
                                            callback
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("UHDMovies", "Link processing error: ${e.localizedMessage}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("UHDMovies", "Article processing error: ${e.localizedMessage}")
                    }
                }
                return
            } catch (e: Exception) {
                retries++
                Log.e("UHDMovies", "Attempt $retries failed: ${e.localizedMessage}")

                if (retries >= maxRetries) {
                    Log.e("UHDMovies", "Max retries reached. Exiting.")
                    return
                }

                delay(delayBetweenRetries)
            }
        }
    }



    suspend fun invokeSubtitleAPI(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$SubtitlesAPI/subtitles/movie/$id.json"
        } else {
            "$SubtitlesAPI/subtitles/series/$id:$season:$episode.json"
        }
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        )
        app.get(url, headers = headers, timeout = 100L)
            .parsedSafe<SubtitlesAPI>()?.subtitles?.amap { it ->
                val lan = getLanguage(it.lang) ?: "Unknown"
                val suburl = it.url
                subtitleCallback.invoke(
                    SubtitleFile(
                        lan.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },  // Use label for the name
                        suburl     // Use extracted URL
                    )
                )
            }
    }


    suspend fun invokeWyZIESUBAPI(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        if (id.isNullOrBlank()) return  // Prevents API call with invalid ID

        val url = buildString {
            append("$WyZIESUBAPI/search?id=$id")
            if (season != null && episode != null) append("&season=$season&episode=$episode")
        }

        val subtitles = runCatching {
            val res = app.get(url).toString()
            Gson().fromJson<List<WyZIESUB>>(res, object : TypeToken<List<WyZIESUB>>() {}.type)
        }.getOrElse { emptyList() }

        subtitles.forEach {
            val language = it.display.replaceFirstChar { ch -> ch.titlecase(Locale.getDefault()) }
            subtitleCallback(SubtitleFile(language, it.url))
        }
    }

    suspend fun invokeXPrimeAPI(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$Xprime/primebox?id=$id"
        } else {
            "$Xprime/primebox?id=$id&season=$season&episode=$episode"
        }

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        )

        val response = app.get(url, headers = headers, timeout = 100L).parsedSafe<Xprime>() ?: return
        val streamMap = mapOf(
            "1080p" to (response.streams.n1080p to Qualities.P1080.value),
            "720p" to (response.streams.n720p to Qualities.P720.value),
            "480p" to (response.streams.n480p to Qualities.P480.value)
        )

        for ((quality, pair) in streamMap) {
            val (streamUrl, qualityValue) = pair
            if (quality in response.availableQualities && streamUrl.isNotBlank()) {
                callback(
                    newExtractorLink(
                        "XPrime",
                        "XPrime",
                        url = streamUrl,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = referer
                        this.quality = qualityValue
                    }
                )
            }
        }

        response.subtitles.forEach {
            val language = it.label.ifBlank { "Unknown" }
            subtitleCallback.invoke(
                SubtitleFile(
                    language.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString() },
                    it.file
                )
            )
        }
    }

    suspend fun invokevidzeeUltra(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$Vidzee/movie/movie.php?id=$id"
        } else {
            "$Vidzee/tv/$id/$season/$episode"
        }
        val response = app.get(url).document
        val script = response.select("script").map { it.data() }.firstOrNull { "qualityOptions" in it } ?: return

        val regex = Regex("""const\s+qualityOptions\s*=\s*(\[[\s\S]*?])""")
        val match = regex.find(script)
        val jsonArrayRaw = match?.groups?.get(1)?.value ?: return

        try {
            val jsonArray = JSONArray(jsonArrayRaw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val label = obj.optString("html")
                val file = obj.optString("url")

                if (file.isNotBlank()) {
                    callback(
                        newExtractorLink(
                            "Vidzee",
                            "Vidzee",
                            file,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$Vidzee/"
                            this.quality = label.replace(Regex("""[^\d]"""), "").toIntOrNull() ?: Qualities.Unknown.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("VidzeeParser", "Failed to parse qualityOptions $e")
        }
    }


    suspend fun invokevidzeeMulti(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$Vidzee/movie/multi.php?id=$id"
        } else {
            "$Vidzee/tv/multi.php?id=$id&season=$season&episode=$episode"
        }
        val response = app.get(url).document
        val script = response.select("script").map { it.data() }.firstOrNull { "qualityOptions" in it } ?: return

        val regex = Regex("""const\s+qualityOptions\s*=\s*(\[[\s\S]*?])""")
        val match = regex.find(script)
        val jsonArrayRaw = match?.groups?.get(1)?.value ?: return

        try {
            val jsonArray = JSONArray(jsonArrayRaw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val label = obj.optString("html")
                val file = obj.optString("url")

                if (file.isNotBlank()) {
                    callback(
                        newExtractorLink(
                            "Vidzee Multi",
                            "Vidzee Multi",
                            file,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$Vidzee/"
                            this.quality = label.replace(Regex("""[^\d]"""), "").toIntOrNull() ?: Qualities.Unknown.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("VidzeeParser", "Failed to parse qualityOptions $e")
        }

    }



    suspend fun invokeTopMovies(
        imdbId: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        lastSeason: Int?,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        maxRetries: Int = 3,
        delayBetweenRetries: Long = 2000L
    ) {
        var retries = 0

        while (retries < maxRetries) {
            try {
                val topmoviesAPI = runCatching {
                    app.get("$modflixAPI/?type=bollywood", allowRedirects = false)
                        .document
                        .selectFirst("meta[http-equiv=refresh]")
                        ?.attr("content")
                        ?.substringAfter("url=")
                }.getOrNull() ?: return

                val url = if (season == null) {
                    "$topmoviesAPI/search/${imdbId.orEmpty()} ${year ?: ""}"
                } else {
                    "$topmoviesAPI/search/${imdbId.orEmpty()} Season ${season} ${year ?: ""}"
                }

                val res1 = runCatching {
                    app.get(url).document.select("#content_box article a")
                }.getOrNull() ?: return

                val hrefpattern = res1.attr("href").takeIf(String::isNotBlank) ?: return

                val res = runCatching {
                    app.get(
                        hrefpattern,
                        headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"),
                        interceptor = wpRedisInterceptor
                    ).document
                }.getOrNull() ?: return

                if (season == null) {
                    res.select("a.maxbutton-download-links")
                        .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }
                        .forEach { detailPageUrl ->
                            val detailPageDocument = runCatching { app.get(detailPageUrl).document }.getOrNull()
                                ?: return@forEach

                            detailPageDocument.select("a.maxbutton-fast-server-gdrive")
                                .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }
                                .forEach { driveLink ->
                                    bypassHrefli(driveLink)?.let { streamUrl ->
                                        loadSourceNameExtractor(
                                            "TopMovies",
                                            streamUrl,
                                            "$topmoviesAPI/",
                                            subtitleCallback,
                                            callback,
                                        )
                                    }
                                }
                        }
                } else {
                    res.select("a.maxbutton-g-drive")
                        .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }
                        .forEach { detailPageUrl ->
                            val detailPageDocument = runCatching { app.get(detailPageUrl).document }.getOrNull()
                                ?: return@forEach

                            detailPageDocument.select("span strong")
                                .firstOrNull {
                                    it.text().matches(Regex(".*Episode\\s+$episode.*", RegexOption.IGNORE_CASE))
                                }
                                ?.parent()?.closest("a")?.attr("href")?.takeIf(String::isNotBlank)
                                ?.let { driveLink ->
                                    bypassHrefli(driveLink)?.let { streamUrl ->
                                        loadSourceNameExtractor(
                                            "TopMovies",
                                            streamUrl,
                                            "$topmoviesAPI/",
                                            subtitleCallback,
                                            callback,
                                        )
                                    }
                                }
                        }
                }
                return
            } catch (e: Exception) {
                retries++
                Log.e("TopMovies", "Attempt $retries failed: ${e.localizedMessage}")

                if (retries >= maxRetries) {
                    Log.e("TopMovies", "Max retries reached. Exiting.")
                    return
                }

                delay(delayBetweenRetries)
            }
        }
    }


    suspend fun invokeMoviesmod(
        imdbId: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        maxRetries: Int = 3,
        delayBetweenRetries: Long = 2000L
    ) {
        var retries = 0

        while (retries < maxRetries) {
            try {
                val redirectUrl = app.get("$modflixAPI/?type=hollywood", allowRedirects = false)
                    .document
                    .selectFirst("meta[http-equiv=refresh]")
                    ?.attr("content")
                    ?.substringAfter("url=")
                    ?.takeIf { it.startsWith("http") }

                redirectUrl?.let {
                    invokeModflix(
                        imdbId = imdbId,
                        year = year,
                        season = season,
                        episode = episode,
                        subtitleCallback = subtitleCallback,
                        callback = callback,
                        api = it
                    )
                    return
                } ?: run {
                    Log.e("Moviesmod", "Redirect URL is null or invalid")
                }
            } catch (e: Exception) {
                retries++
                Log.e("Moviesmod", "Attempt $retries failed: ${e.localizedMessage}")

                if (retries >= maxRetries) {
                    Log.e("Moviesmod", "Max retries reached. Exiting.")
                    return
                }

                delay(delayBetweenRetries)
            }
        }
    }


    private suspend fun invokeModflix(
        imdbId: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        api: String
    ) {
        val searchUrl = if (season == null) {
            "$api/search/$imdbId $year"
        } else {
            "$api/search/$imdbId Season $season $year"
        }

        val hrefpattern = runCatching {
            app.get(searchUrl).document.selectFirst("#content_box article a")?.attr("href")
                .orEmpty()
        }.getOrElse {
            Log.e("Error:", "Failed to fetch search results: ${it.message}")
            return
        }

        if (hrefpattern.isBlank()) {
            Log.e("Error:", "No valid search result found for $searchUrl")
            return
        }

        val document = runCatching {
            app.get(
                hrefpattern,
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"),
                interceptor = wpRedisInterceptor
            ).document
        }.getOrElse {
            Log.e("Error:", "Failed to load content page: ${it.message}")
            return
        }

        if (season == null) {
            document.select("a.maxbutton-download-links")
                .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }
                .forEach { detailPageUrl ->
                    runCatching {
                        base64Decode(detailPageUrl.substringAfter("="))
                            .takeIf { it.isNotBlank() }
                            ?.let { decodedUrl ->
                                val detailPageDoc = runCatching { app.get(decodedUrl).document }
                                    .getOrElse {
                                        Log.e(
                                            "Error:",
                                            "Failed to fetch detail page: ${it.message}"
                                        )
                                        return@let null
                                    }
                                detailPageDoc?.select("a.maxbutton-fast-server-gdrive")
                                    ?.mapNotNull { it.attr("href").takeIf(String::isNotBlank) }
                                    ?.forEach { driveLink ->
                                        bypassHrefli(driveLink)?.let { streamUrl ->
                                            loadSourceNameExtractor(
                                                "MoviesMod",
                                                streamUrl,
                                                "$api/",
                                                subtitleCallback,
                                                callback
                                            )
                                        }
                                    }
                            }
                    }.onFailure { error ->
                        Log.e("Error:", "Error processing detail page URL: ${error.message}")
                    }
                }
        } else {
            val seasonPattern = Regex("Season\\s+$season\\b", RegexOption.IGNORE_CASE)
            document.select("div.mod").forEach { modDiv ->
                modDiv.select("h3").forEach { h3Element ->
                    if (!seasonPattern.containsMatchIn(h3Element.text().trim())) return@forEach

                    h3Element.nextElementSibling()?.select("a.maxbutton-episode-links")
                        ?.mapNotNull { it.attr("href").takeIf(String::isNotBlank) }
                        ?.forEach { detailPageUrl ->
                            val decodedUrl = base64Decode(detailPageUrl.substringAfter("="))
                            val detailPageDoc = runCatching { app.get(decodedUrl).document }
                                .getOrElse {
                                    Log.e(
                                        "Error:",
                                        "Failed to fetch episode detail page: ${it.message}"
                                    )
                                    return@forEach
                                }

                            val episodeLink = detailPageDoc.select("span strong")
                                .firstOrNull {
                                    it.text().matches(
                                        Regex(
                                            ".*Episode\\s+$episode.*",
                                            RegexOption.IGNORE_CASE
                                        )
                                    )
                                }
                                ?.parent()?.closest("a")?.attr("href")

                            episodeLink?.takeIf(String::isNotBlank)?.let { driveLink ->
                                bypassHrefli(driveLink)?.let { streamUrl ->
                                    loadSourceNameExtractor(
                                        "MoviesMod",
                                        streamUrl,
                                        "$api/",
                                        subtitleCallback,
                                        callback
                                    )
                                }
                            }
                        }
                }
            }
        }
    }


    suspend fun invokeDotmovies(
        imdbId: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        lastSeason: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        maxRetries: Int = 3, // Maximum number of retries
        delayBetweenRetries: Long = 2000L // Delay between retries (in milliseconds)
    ) {
        val targetUrl = "$Vglist/?re=luxmovies"
        var retries = 0

        while (retries < maxRetries) {
            try {
                val response = app.get(targetUrl, allowRedirects = false)
                val document = response.document
                val redirectUrl = document.selectFirst("meta[http-equiv=refresh]")
                    ?.attr("content")
                    ?.substringAfter("url=")
                    ?.takeIf { it.startsWith("http") }

                if (!redirectUrl.isNullOrBlank()) {
                    invokeWpredis(
                        source = "LuxMovies",
                        imdbId = imdbId,
                        title = title,
                        year = year,
                        season = season,
                        lastSeason = lastSeason,
                        episode = episode,
                        subtitleCallback = subtitleCallback,
                        callback = callback,
                        api = redirectUrl
                    )
                    return // Success, exit the function
                } else {
                    Log.e("Dotmovies", "Redirect URL is null or invalid: check meta refresh or use fallback.")
                    return // Exit after logging error
                }
            } catch (e: Exception) {
                retries++
                Log.e("Dotmovies", "Attempt $retries failed: ${e.localizedMessage}")

                if (retries >= maxRetries) {
                    Log.e("Dotmovies", "Max retries reached. Exiting.")
                    return // Exit after max retries
                }
                delay(delayBetweenRetries)
            }
        }
    }



    suspend fun invokeRogmovies(
        imdbId: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        lastSeason: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        maxRetries: Int = 3,
        delayBetweenRetries: Long = 2000L
    ) {
        var retries = 0

        while (retries < maxRetries) {
            try {
                val redirectUrl = app.get("$Vglist/?re=rogmovies", allowRedirects = false)
                    .document
                    .selectFirst("meta[http-equiv=refresh]")
                    ?.attr("content")
                    ?.substringAfter("url=")
                    ?.takeIf { it.startsWith("http") }

                redirectUrl?.let {
                    invokeWpredis(
                        source = "RogMovies",
                        imdbId = imdbId,
                        title = title,
                        year = year,
                        season = season,
                        lastSeason = lastSeason,
                        episode = episode,
                        subtitleCallback = subtitleCallback,
                        callback = callback,
                        api = it
                    )
                    return
                } ?: run {
                    Log.e("RogMovies", "Redirect URL is null or invalid")
                }
            } catch (e: Exception) {
                retries++
                Log.e("RogMovies", "Attempt $retries failed: ${e.localizedMessage}")

                if (retries >= maxRetries) {
                    Log.e("RogMovies", "Max retries reached. Exiting.")
                    return
                }

                delay(delayBetweenRetries)
            }
        }
    }


    suspend fun invokeVegamovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val vegaMoviesAPI = try {
            app.get("$Vglist/?re=vegamovies",allowRedirects = false)
                .document
                .selectFirst("meta[http-equiv=refresh]")
                ?.attr("content")
                ?.substringAfter("url=")
                ?.takeIf { it.startsWith("http") }
        } catch (e: Exception) {
            Log.e("VegaMovies", "Failed to fetch VegaMovies redirect: ${e.localizedMessage}")
            null
        }
        val cfInterceptor = CloudflareKiller()
        val fixtitle = title?.substringBefore("-")?.substringBefore(":")?.replace("&", " ")
        val query = if (season == null) "$fixtitle $year" else "$fixtitle season $season"
        val url = "$vegaMoviesAPI/?s=$query"

        val excludedButtonTexts = setOf("Filepress", "GDToT", "DropGalaxy")

        app.get(url, interceptor = cfInterceptor).document.select("article h2").amap { article ->
            val hrefpattern = article.selectFirst("a")?.attr("href").orEmpty()
            if (hrefpattern.isNotBlank()) {
                val doc = app.get(hrefpattern).document
                if (season == null) {
                    doc.select("button.dwd-button")
                        .filterNot { button ->
                            excludedButtonTexts.any {
                                button.text().contains(it, ignoreCase = true)
                            }
                        }
                        .mapNotNull {
                            it.closest("a")?.attr("href")?.takeIf { url -> url.isNotBlank() }
                        }
                        .forEach { detailPageUrl ->
                            val detailPageDocument = app.get(detailPageUrl).document
                            detailPageDocument.select("button.btn.btn-sm.btn-outline")
                                .filterNot { button ->
                                    excludedButtonTexts.any {
                                        button.text().contains(it, ignoreCase = true)
                                    }
                                }
                                .mapNotNull {
                                    it.closest("a")?.attr("href")
                                        ?.takeIf { url -> url.isNotBlank() }
                                }
                                .forEach { streamingUrl ->
                                    loadSourceNameExtractor(
                                        "VegaMovies V-Cloud",
                                        streamingUrl,
                                        "$vegaMoviesAPI/",
                                        subtitleCallback,
                                        callback
                                    )
                                }
                        }
                } else {
                    val seasonPattern = "(?i)(Season $season)"
                    val episodePattern = "(?i)(V-Cloud|Single|Episode|G-Direct)"

                    doc.select("h4:matches($seasonPattern), h3:matches($seasonPattern)")
                        .forEach { seasonElement ->
                            seasonElement.nextElementSibling()
                                ?.select("a:matches($episodePattern)")
                                ?.forEach { episodeLink ->
                                    val episodeUrl = episodeLink.attr("href")
                                    val episodeDoc = app.get(episodeUrl).document
                                    episodeDoc.selectFirst("h4:contains(Episodes):contains($episode)")
                                        ?.nextElementSibling()
                                        ?.select("a:matches((?i)(V-Cloud|G-Direct))")
                                        ?.mapNotNull {
                                            it.attr("href").takeIf { url -> url.isNotBlank() }
                                        }
                                        ?.forEach { streamingUrl ->
                                            loadSourceNameExtractor(
                                                "VegaMovies V-Cloud",
                                                streamingUrl,
                                                "$vegaMoviesAPI/",
                                                subtitleCallback,
                                                callback
                                            )
                                        }
                                }
                        }
                }
            } else {
                Log.e("Error:", "No valid link found for title: $title")
            }
        }
    }


    private suspend fun invokeWpredis(
        source : String? = null,
        imdbId: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        lastSeason: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        api: String
    ) {
        val cfInterceptor = CloudflareKiller()

        suspend fun getSearchResults(query: String): Elements? {
            return try {
                val url = "$api/$query"
                val doc = app.get(url, interceptor = cfInterceptor).document
                val results = doc.select("article h3 a")
                results.ifEmpty { null }
            } catch (e: Exception) {
                Log.e("Error", "Search failed for $query: ${e.localizedMessage} $e")
                null
            }
        }

        val primaryQuery = if (season == null) "search/$imdbId" else "search/$imdbId season $season"
        var searchResults = getSearchResults(primaryQuery)

        if (searchResults == null && !title.isNullOrBlank()) {
            val fallbackQuery = buildString {
                append("search/")
                append(title.trim().replace(" ", "+"))
                if (year != null) append("+$year")
                if (season != null) append("+season+$season")
            }
            searchResults = getSearchResults(fallbackQuery)
        }

        searchResults?.amap { element ->
            val href = element.attr("href")
            val doc = app.get(href).document
            if (season == null) {
                processMovieLinks(source,doc, api, subtitleCallback, callback)
            } else {
                processSeasonLinks(source,doc, season, episode, api, subtitleCallback, callback)
            }
        }
    }


    private suspend fun processMovieLinks(
        source: String?,
        doc: Document,
        api: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val excludedButtonTexts = listOf("Filepress", "GDToT", "DropGalaxy")

        doc.select("button.dwd-button")
            .filterNot { button ->
                excludedButtonTexts.any {
                    button.text().contains(it, ignoreCase = true)
                }
            }
            .mapNotNull { button -> button.closest("a")?.attr("href")?.takeIf { it.isNotBlank() } }
            .forEach { detailPageUrl ->
                try {
                    val detailPageDocument = app.get(detailPageUrl).document
                    detailPageDocument.select("button.btn.btn-sm.btn-outline")
                        .filterNot { button ->
                            excludedButtonTexts.any {
                                button.text().contains(it, ignoreCase = true)
                            }
                        }
                        .mapNotNull { button ->
                            button.closest("a")?.attr("href")?.takeIf { it.isNotBlank() }
                        }
                        .forEach { streamingUrl ->
                            loadSourceNameExtractor(
                                "$source V-Cloud",
                                streamingUrl,
                                "$api/",
                                subtitleCallback,
                                callback
                            )
                        }
                } catch (e: Exception) {
                    Log.e("Error:", "Failed to fetch movie details: ${e.localizedMessage} $e")
                }
            }
    }

    private suspend fun processSeasonLinks(
        source: String?,
        doc: Document,
        season: Int,
        episode: Int?,
        api: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val seasonPattern = "(?i)(Season $season)"
        val episodePattern = "(?i)(V-Cloud|Single|Episode|G-Direct|Download Now)"

        doc.select("h4:matches($seasonPattern), h3:matches($seasonPattern)").forEach { h4 ->
            val episodeLinks =
                h4.nextElementSibling()?.select("a:matches($episodePattern)") ?: return@forEach

            episodeLinks.forEach { episodeLink ->
                val episodeUrl = episodeLink.attr("href")
                try {
                    val res = app.get(episodeUrl).document
                    val streamingUrls = res.selectFirst("h4:contains(Episodes):contains($episode)")
                        ?.nextElementSibling()
                        ?.select("a:matches((?i)(V-Cloud|G-Direct))")
                        ?.map { it.attr("href") }

                    streamingUrls?.forEach { link ->
                        loadSourceNameExtractor(
                            "$source V-Cloud",
                            link,
                            "$api/",
                            subtitleCallback,
                            callback
                        )
                    }
                } catch (e: Exception) {
                    Log.e("Error:", "Failed to fetch episode details: ${e.localizedMessage} $e")
                }
            }
        }
    }


    suspend fun invokeTom(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$TomAPI/api/getVideoSource?type=movie&id=$id"
        } else {
            "$TomAPI/api/getVideoSource?type=tv&id=$id/$season/$episode"
        }

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
            "Referer" to "https://autoembed.cc"
        )

        val response = try {
            app.get(url, headers = headers)
        } catch (e: Exception) {
            Log.e("invokeTom", "Error fetching data: ${e.localizedMessage}")
            return
        }

        if (response.code != 200) {
            Log.e("invokeTom", "Failed to fetch data. Status code: ${response.code}")
            return
        }

        val data = tryParseJson<TomResponse>(response.text) ?: return
        callback(
            newExtractorLink(
                "Tom Embeded",
                "Tom Embeded",
                url = data.videoSource,
                ExtractorLinkType.M3U8
            ) {
                this.referer = ""
                this.quality = Qualities.P1080.value
            }
        )

        data.subtitles.forEach {
            subtitleCallback.invoke(
                SubtitleFile(
                    it.label,
                    it.file
                )
            )
        }
    }


    suspend fun invokeExtramovies(
        imdbId: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = "$Extramovies/search/$imdbId"
        app.get(url).document.select("h3 a").amap {
            val link = it.attr("href")
            app.get(link).document.select("div.entry-content a.maxbutton-8").map { it ->
                val href = it.select("a").attr("href")
                loadSourceNameExtractor(
                    "ExtraMovies",
                    href,
                    "",
                    subtitleCallback,
                    callback,
                )
            }
        }
    }

    suspend fun invokeVidbinge(
        imdbId: String? = null,
        tmdbId: Int? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val type = if (season == null) "movie" else "tv"
        val servers = listOf("astra", "orion", "nova")
        for (source in servers) {
            val token = app.get(BuildConfig.WhvxT).parsedSafe<Vidbinge>()?.token ?: ""
            val s = season ?: ""
            val e = episode ?: ""
            val query =
                """{"title":"$title","imdbId":"$imdbId","tmdbId":"$tmdbId","type":"$type","season":"$s","episode":"$e","releaseYear":"$year"}"""
            val encodedQuery = encodeQuery(query)
            val encodedToken = encodeQuery(token)
            val originalUrl =
                "$WhvxAPI/search?query=$encodedQuery&provider=$source&token=$encodedToken"
            val headers = mapOf(
                "accept" to "*/*",
                "origin" to "https://www.vidbinge.com",
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )
            val oneurl = app.get(originalUrl, headers = headers, timeout = 50L)
                .parsedSafe<Vidbingesources>()?.url
            oneurl?.let {
                val encodedit = encodeQuery(it)
                app.get("$WhvxAPI/source?resourceId=$encodedit&provider=$source", headers = headers)
                    .parsedSafe<Vidbingeplaylist>()?.stream?.amap {
                        val playlist = it.playlist
                        callback(
                            newExtractorLink(
                                "Vidbinge ${source.capitalize()}",
                                "Vidbinge ${source.capitalize()}",
                                url = playlist,
                                type = INFER_TYPE
                            ) {
                                this.referer = ""
                                this.quality = Qualities.P1080.value
                            }
                        )
                    }
            }
        }
    }

    suspend fun invokeSharmaflix(
        title: String? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val searchtitle = encodeQuery(title ?: "").replace("+", "%20")
        val url = "$Sharmaflix/searchContent/$searchtitle/0"
        val headers = mapOf(
            "x-api-key" to BuildConfig.SharmaflixApikey,
            "User-Agent" to "Dalvik/2.1.0 (Linux; U; Android 13; Subsystem for Android(TM) Build/TQ3A.230901.001)"
        )
        val json = app.get(url, headers = headers).toString().toJson()
        val objectMapper = jacksonObjectMapper()
        val parsedResponse: SharmaFlixRoot = objectMapper.readValue(json)
        parsedResponse.forEach { root ->
            val id = root.id
            val movieurl = "$Sharmaflix/getMoviePlayLinks/$id/0"
            val hrefresponse = app.get(movieurl, headers = headers).toString().toJson()
            val hrefparser: SharmaFlixLinks = objectMapper.readValue(hrefresponse)
            hrefparser.forEach {
                callback(
                    newExtractorLink(
                        "Sharmaflix",
                        "Sharmaflix",
                        url = it.url,
                        type = INFER_TYPE
                    ) {
                        this.referer = ""
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        }
    }

    //Kindly don't copy it
    suspend fun invokeEmbedsu(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val embedPath = if (season == null) "movie/$id" else "tv/$id/$season/$episode"
            val url = "$EmbedSu/embed/$embedPath"

            val scriptContent = runCatching {
                app.get(url, referer = EmbedSu)
                    .document.selectFirst("script:containsData(window.vConfig)")
                    ?.data()
            }.getOrNull() ?: return

            val encodedJson = runCatching {
                Regex("atob\\(`(.*?)`\\)").find(scriptContent)
                    ?.groupValues?.getOrNull(1)
                    ?.let(::base64Decode)
                    ?.toJson()
            }.getOrNull() ?: return

            val embedData = runCatching {
                Gson().fromJson(encodedJson, Embedsu::class.java)
            }.getOrNull() ?: return

            val decodedPayload = runCatching {
                val hashDecoded = base64Decode(embedData.hash)
                base64Decode(
                    hashDecoded.split(".")
                        .joinToString("") { it.reversed() }
                        .reversed() + "="
                )
            }.getOrNull() ?: return

            EmbedSuitemparseJson(decodedPayload).forEach { item ->
                runCatching {
                    val sourceUrl = app.get("$EmbedSu/api/e/${item.hash}", referer = EmbedSu)
                        .parsedSafe<Embedsuhref>()
                        ?.source ?: return@runCatching

                    callback(
                        newExtractorLink(
                            "Embedsu Viper",
                            "Embedsu Viper",
                            url = sourceUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            referer = EmbedSu
                            quality = Qualities.P1080.value
                            headers = mapOf(
                                "Origin" to "https://embed.su",
                                "Referer" to "https://embed.su/",
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                            )
                        }
                    )
                }.onFailure {
                    Log.w("Embedsu", "Failed to fetch or parse link for item: ${item.hash} $it")
                }
            }
        } catch (e: Exception) {
            Log.e("Embedsu", "Unexpected error in invokeEmbedsu $e")
        }
    }


    suspend fun invokeTheyallsayflix(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val type = if (season == null) "movie" else "show"
        val url = if (season == null) {
            "$Theyallsayflix/api/v1/search?type=$type&imdb_id=$id"
        } else {
            "$Theyallsayflix/api/v1/search?type=$type&imdb_id=$id&season=$season&episode=$episode"
        }
        app.get(url).parsedSafe<Theyallsayflix>()?.streams?.amap {
            val href = it.playUrl
            val quality = it.quality.toInt()
            val name = it.fileName
            val size = it.fileSize
            callback(
                newExtractorLink(
                    "DebianFlix $name $size",
                    "DebianFlix $name $size",
                    url = href
                ) {
                    this.referer = ""
                    this.quality = quality
                }
            )
        }

    }


    // Thanks to Repo for code https://github.com/giammirove/videogatherer/blob/main/src/sources/vidsrc.cc.ts#L34
    //Still in progress
    @SuppressLint("NewApi")
    suspend fun invokeVidsrccc(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$vidsrctoAPI/v2/embed/movie/$id"
        } else {
            "$vidsrctoAPI/v2/embed/tv/$id/$season/$episode"
        }
        val type = if (season == null) "movie" else "tv"
        val doc = app.get(url).document.toString()
        val v_value = Regex("var.v.=.\"(.*?)\"").find(doc)?.groupValues?.get(1).toString()
        val vrfres = app.get("${BuildConfig.Vidsrccc}/vrf/$id").parsedSafe<Vidsrccc>()
        val vrf = generateVidsrcVrf(id)
        val timetamp = vrfres?.timestamp
        val instant = Instant.parse(timetamp)
        val unixTimeMs = instant.toEpochMilli()
        val api_url = if (season == null) {
            "${vidsrctoAPI}/api/$id/servers?id=${id}&type=$type&v=$v_value&vrf=${vrf}"
        } else {
            "${vidsrctoAPI}/api/$id/servers?id=${id}&type=$type&season=$season&episode=$episode&v=$v_value&vrf=${vrf}"
        }
        app.get(api_url).parsedSafe<Vidsrcccservers>()?.data?.forEach {
            val servername = it.name
            val m3u8 = app.get("$vidsrctoAPI/api/source/${it.hash}?t=$unixTimeMs")
                .parsedSafe<Vidsrcccm3u8>()?.data?.source
            callback(
                newExtractorLink(
                    "Vidsrc [$servername]",
                    "Vidsrc [$servername]",
                    url = m3u8 ?: return,
                ) {
                    this.referer = "https://vidsrc.stream/"
                    this.quality = Qualities.P1080.value
                }
            )
        }

    }

    suspend fun invokeVidsrcsu(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$vidsrcsu/embed/movie/$id"
        } else {
            "$vidsrcsu/embed/tv/$id/$season/$episode"
        }
        val json = app.get(url).document.toString().substringAfter("const MultiLang = ")
            .substringBefore(";").trim()

        val gson = Gson()
        val type = object : TypeToken<List<Vidsrcsu>>() {}.type
        val streams: List<Vidsrcsu> = gson.fromJson(json, type)
        val objectMapper = jacksonObjectMapper()
        val vidsrcsuList: List<Vidsrcsu> = objectMapper.readValue(json)

        for (vid in vidsrcsuList) {
            callback(
                newExtractorLink(
                    "VidsrcSU ${vid.language}",
                    "VidsrcSU ${vid.language}",
                    url = vid.m3u8Url,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = ""
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }

    /*
    suspend fun invokeFlicky(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$FlickyAPI/embed/movie/?id=$id"
        } else {
            "$FlickyAPI/embed/tv/?id=$id/$season/$episode"
        }

        val headers = mapOf(
            "Sec-Fetch-Dest" to "iframe",
            "User-Agent" to "Mozilla/5.0",
            "Accept" to "*//*"
        )

        if (!url.startsWith("http")) {
            Log.e("Flicky", "Invalid URL: $url")
            return
        }

        val res = runCatching {
            app.get(url, referer = FlickyAPI, headers = headers, timeout = 50000).toString()
        }
            .getOrElse {
                Log.e("Flicky", "Request failed: $it")
                return
            }

        val matches =
            Regex("serverUrl\\s*=\\s*\"(.*?)\"").findAll(res).map { it.groupValues[1] }.toList()
        if (matches.isEmpty()) return Log.e("Flicky", "No server URLs found.")

        matches.amap { serverUrl ->
            if (!serverUrl.startsWith("http")) {
                Log.e("Flicky", "Skipping invalid server URL: $serverUrl")
                return@amap
            }
            val iframe =
                if (season == null) serverUrl else "$serverUrl?id=$id&season=$season&episode=$episode"
            when {
                iframe.contains("/nexa") -> processSingleM3U8(
                    iframe,
                    "Flicky Nexa",
                    headers,
                    callback
                )

                iframe.contains("/multi") -> processJsonStreams(
                    iframe,
                    "Flicky Multi",
                    headers,
                    callback
                )

                iframe.contains("/shukra") -> processJsonStreams(
                    iframe,
                    "Flicky Shukra",
                    headers,
                    callback
                )

                iframe.contains("/desi") -> processJsonStreams(
                    iframe,
                    "Flicky Desi",
                    headers,
                    callback
                )

                iframe.contains("/vietflick") -> processSingleM3U8(
                    iframe,
                    "Flicky Vietflick",
                    headers,
                    callback
                )

                iframe.contains("/oyo") -> processOyoStream(iframe, headers, callback)
                else -> Log.d("Flicky", "Unknown stream type: $iframe")
            }
        }
    }

    private suspend fun processSingleM3U8(
        url: String,
        name: String,
        headers: Map<String, String>,
        callback: (ExtractorLink) -> Unit
    ) {
        if (!url.startsWith("http")) {
            Log.e("Flicky", "Skipping invalid URL: $url")
            return
        }
        val m3u8 = runCatching {
            app.get(url, referer = FlickyAPI, headers = headers).toString()
                .substringAfter("file: \"").substringBefore("\",")
        }.getOrNull()

        if (!m3u8.isNullOrBlank() && m3u8.startsWith("http")) {
            callback(
                newExtractorLink(
                    name,
                    name,
                    url = m3u8,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = FlickyAPI
                    this.quality = Qualities.P1080.value
                }
            )
        } else {
            Log.e("Flicky", "M3U8 URL not found for $name")
        }
    }

    private suspend fun processJsonStreams(
        url: String,
        baseName: String,
        headers: Map<String, String>,
        callback: (ExtractorLink) -> Unit
    ) {
        if (!url.startsWith("http")) {
            Log.e("Flicky", "Skipping invalid URL: $url")
            return
        }

        val json = runCatching {
            app.get(url, referer = FlickyAPI, headers = headers, timeout = 50000).toString()
                .substringAfter("const streams = ").substringBefore(";")
        }.getOrNull()

        Log.d("Flicky", "JSON Response for $baseName: $json")

        if (json.isNullOrBlank()) {
            Log.e("Flicky", "Failed to extract streams JSON for $baseName")
            return
        }

        val streams: List<FlickyStream> = try {
            Gson().fromJson(json, object : TypeToken<List<FlickyStream>>() {}.type)
        } catch (e: Exception) {
            Log.e("Flicky", "Invalid JSON format for $baseName: ${e.message}")
            return
        }

        streams.forEach { vid ->
            callback(
                newExtractorLink(
                    "$baseName ${vid.language}",
                    "$baseName ${vid.language}",
                    url = vid.url,
                   ExtractorLinkType.M3U8
                ) {
                    this.referer = FlickyAPI
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }

    private suspend fun processOyoStream(
        url: String,
        headers: Map<String, String>,
        callback: (ExtractorLink) -> Unit
    ) {
        if (!url.startsWith("http")) {
            Log.e("Flicky", "Skipping invalid URL: $url")
            return
        }

        val m3u8 = runCatching {
            app.get(url, referer = FlickyAPI, headers = headers).toString()
                .substringAfter("var streamLink = \"").substringBefore("\";")
        }.getOrNull()

        if (!m3u8.isNullOrBlank() && m3u8.startsWith("http")) {
            callback(
                newExtractorLink(
                    "Flicky OYO",
                    "Flicky OYO",
                    url = m3u8,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = FlickyAPI
                    this.quality = Qualities.P1080.value
                }
            )

        } else {
            Log.e("Flicky", "OYO stream URL not found")
        }
    }

  */

    suspend fun invokeFlixon(
        tmdbId: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        onionUrl: String = "https://onionplay.asia/"
    ) {
        val request = if (season == null) {
            val res = app.get("$flixonAPI/$imdbId", referer = onionUrl)
            if (res.text.contains("BEGIN PGP SIGNED MESSAGE")) app.get(
                "$flixonAPI/$imdbId-1",
                referer = onionUrl
            ) else res
        } else {
            app.get("$flixonAPI/$tmdbId-$season-$episode", referer = onionUrl)
        }
        val script =
            request.document.selectFirst("script:containsData(= \"\";)")?.data()
        val collection = script?.substringAfter("= [")?.substringBefore("];")
        val num =
            script?.substringAfterLast("(value) -")?.substringBefore(");")?.trim()
                ?.toInt()
                ?: return

        val iframe = collection?.split(",")?.map { it.trim().toInt() }?.map { nums ->
            nums.minus(num).toChar()
        }?.joinToString("")?.let { Jsoup.parse(it) }?.selectFirst("button.redirect")
            ?.attr("onclick")?.substringAfter("('")?.substringBefore("')")

        delay(1000)
        val unPacker = app.get(
            iframe
                ?: return, referer = "$flixonAPI/"
        ).document.selectFirst("script:containsData(JuicyCodes.Run)")?.data()
            ?.substringAfter("JuicyCodes.Run(")?.substringBefore(");")?.split("+")
            ?.joinToString("") { it.replace("\"", "").trim() }
            ?.let { getAndUnpack(base64Decode(it)) }
        val link = Regex("[\"']file[\"']:[\"'](.+?)[\"'],").find(
            unPacker
                ?: return
        )?.groupValues?.getOrNull(1)
        callback.invoke(
            newExtractorLink(
                "Flixon",
                "Flixon",
                url = link ?: return,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "https://onionflux.com/"
                this.quality = Qualities.P720.value
            }
        )

    }

    suspend fun invokeNepu(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val slug = title?.createSlug()
        val headers = mapOf(
            "X-Requested-With" to "XMLHttpRequest"
        )
        val data =
            app.get(
                "$nepuAPI/ajax/posts?q=$title",
                headers = headers,
                referer = "$nepuAPI/"
            )
                .parsedSafe<NepuSearch>()?.data

        val media =
            data?.find { it.url?.startsWith(if (season == null) "/movie/$slug-$year-" else "/serie/$slug-$year-") == true }
                ?: data?.find {
                    (it.name.equals(
                        title,
                        true
                    ) && it.type == if (season == null) "Movie" else "Serie")
                }

        if (media?.url == null) return
        val mediaUrl = if (season == null) {
            media.url
        } else {
            "${media.url}/season/$season/episode/$episode"
        }

        val dataId =
            app.get(fixUrl(mediaUrl, nepuAPI)).document.selectFirst("a[data-embed]")
                ?.attr("data-embed") ?: return
        val res = app.post(
            "$nepuAPI/ajax/embed", data = mapOf(
                "id" to dataId
            ), referer = mediaUrl, headers = headers
        ).text

        val m3u8 = "(http[^\"]+)".toRegex().find(res)?.groupValues?.get(1)

        callback.invoke(
            newExtractorLink(
                "Nepu",
                "Nepu",
                url = m3u8 ?: return,
                INFER_TYPE
            ) {
                this.referer = "$nepuAPI/"
                this.quality = Qualities.P1080.value
            }
        )
    }

    suspend fun invokeMoflix(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = (if (season == null) {
            "tmdb|movie|$tmdbId"
        } else {
            "tmdb|series|$tmdbId"
        }).let { base64Encode(it.toByteArray()) }

        val loaderUrl = "$moflixAPI/api/v1/titles/$id?loader=titlePage"
        val url = if (season == null) {
            loaderUrl
        } else {
            val mediaId = app.get(loaderUrl, referer = "$moflixAPI/")
                .parsedSafe<MoflixResponse>()?.title?.id
            "$moflixAPI/api/v1/titles/$mediaId/seasons/$season/episodes/$episode?loader=episodePage"
        }

        val res = app.get(url, referer = "$moflixAPI/").parsedSafe<MoflixResponse>()
        (res?.episode ?: res?.title)?.videos?.filter {
            it.category.equals(
                "full",
                true
            )
        }
            ?.amap { iframe ->
                val response =
                    app.get(iframe.src ?: return@amap, referer = "$moflixAPI/")
                val host = getBaseUrl(iframe.src)
                val doc = response.document.selectFirst("script:containsData(sources:)")
                    ?.data()
                val script = if (doc.isNullOrEmpty()) {
                    getAndUnpack(response.text)
                } else {
                    doc
                }
                val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(
                    script
                )?.groupValues?.getOrNull(1)
                if (m3u8?.haveDub("$host/") == false) return@amap
                callback.invoke(
                    newExtractorLink(
                        "Moflix",
                        "Moflix [${iframe.name}]",
                        url = m3u8 ?: return@amap,
                        INFER_TYPE
                    ) {
                        this.referer = "$host/"
                        this.quality = iframe.quality?.filter { it.isDigit() }?.toIntOrNull()
                            ?: Qualities.Unknown.value
                    }
                )
            }

    }

    // only subs
    suspend fun invokeWatchsomuch(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val id = imdbId?.removePrefix("tt")
        val epsId = app.post(
            "$watchSomuchAPI/Watch/ajMovieTorrents.aspx",
            data = mapOf(
                "index" to "0",
                "mid" to "$id",
                "wsk" to "30fb68aa-1c71-4b8c-b5d4-4ca9222cfb45",
                "lid" to "",
                "liu" to ""
            ),
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<WatchsomuchResponses>()?.movie?.torrents?.let { eps ->
            if (season == null) {
                eps.firstOrNull()?.id
            } else {
                eps.find { it.episode == episode && it.season == season }?.id
            }
        } ?: return

        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)

        val subUrl = if (season == null) {
            "$watchSomuchAPI/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part="
        } else {
            "$watchSomuchAPI/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part=S${seasonSlug}E${episodeSlug}"
        }

        app.get(subUrl).parsedSafe<WatchsomuchSubResponses>()?.subtitles?.map { sub ->
            subtitleCallback.invoke(
                SubtitleFile(
                    sub.label?.substringBefore("&nbsp") ?: "", fixUrl(
                        sub.url
                            ?: return@map null, watchSomuchAPI
                    )
                )
            )
        }


    }

    //only sub
    @Suppress("SuspiciousIndentation")
    suspend fun invokewhvx(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val subUrl = if (season == null) {
            "$Whvx_API/search?id=$imdbId"
        } else {
            "$Whvx_API/search?id=$imdbId&season=$season&episode=$episode"
        }
        val json = app.get(subUrl).text
        val data = parseJson<ArrayList<WHVXSubtitle>>(json)
        data.forEach {
            subtitleCallback.invoke(
                SubtitleFile(
                    it.languageName,
                    it.url
                )
            )
        }
    }

    suspend fun invokeShinobiMovies(
        apiUrl: String,
        api: String,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        invokeIndex(
            apiUrl,
            api,
            title,
            year,
            season,
            episode,
            callback,
        )
    }

    private suspend fun invokeIndex(
        apiUrl: String,
        api: String,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        password: String = "",
    ) {
        val passHeaders = mapOf("Authorization" to password)
        val query = getIndexQuery(title, year, season, episode).let {
            if (api in mkvIndex) "$it mkv" else it
        }
        val body =
            """{"q":"$query","password":null,"page_token":null,"page_index":0}""".toRequestBody(
                RequestBodyTypes.JSON.toMediaTypeOrNull()
            )
        val data = mapOf("q" to query, "page_token" to "", "page_index" to "0")
        val search = if (api in encodedIndex) {
            decodeIndexJson(
                if (api in lockedIndex) app.post(
                    "${apiUrl}search",
                    data = data,
                    headers = passHeaders,
                    referer = apiUrl,
                    timeout = 120L
                ).text else app.post(
                    "${apiUrl}search",
                    data = data,
                    referer = apiUrl
                ).text
            )
        } else {
            app.post(
                "${apiUrl}search",
                requestBody = body,
                referer = apiUrl,
                timeout = 120L
            ).text
        }
        val media = if (api in untrimmedIndex) searchIndex(
            title,
            season,
            episode,
            year,
            search,
            false
        ) else searchIndex(title, season, episode, year, search)
        media?.amap { file ->
            val pathBody =
                """{"id":"${file.id ?: return@amap null}"}""".toRequestBody(
                    RequestBodyTypes.JSON.toMediaTypeOrNull()
                )
            val pathData = mapOf(
                "id" to file.id,
            )
            val path = (if (api in encodedIndex) {
                if (api in lockedIndex) {
                    app.post(
                        "${apiUrl}id2path",
                        data = pathData,
                        headers = passHeaders,
                        referer = apiUrl,
                        timeout = 120L
                    )
                } else {
                    app.post(
                        "${apiUrl}id2path",
                        data = pathData,
                        referer = apiUrl,
                        timeout = 120L
                    )
                }
            } else {
                app.post(
                    "${apiUrl}id2path",
                    requestBody = pathBody,
                    referer = apiUrl,
                    timeout = 120L
                )
            }).text.let { path ->
                if (api in ddomainIndex) {
                    val worker = app.get(
                        "${fixUrl(path, apiUrl).encodeUrl()}?a=view",
                        referer = if (api in needRefererIndex) apiUrl else "",
                        timeout = 120L
                    ).document.selectFirst("script:containsData(downloaddomain)")
                        ?.data()
                        ?.substringAfter("\"downloaddomain\":\"")
                        ?.substringBefore("\",")?.let {
                            "$it/0:"
                        }
                    fixUrl(path, worker ?: return@amap null)
                } else {
                    fixUrl(path, apiUrl)
                }
            }.encodeUrl()

            val size = "%.2f GB".format(
                bytesToGigaBytes(
                    file.size?.toDouble()
                        ?: return@amap null
                )
            )
            val quality = getIndexQuality(file.name)
            val tags = getIndexQualityTags(file.name)

            callback.invoke(
                newExtractorLink(
                    api,
                    "$api $tags [$size]",
                    url = path
                ) {
                    this.referer = if (api in needRefererIndex) apiUrl else ""
                    this.quality = quality
                }
            )
        }

    }

    suspend fun invokeGdbotMovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val query = getIndexQuery(title, null, season, episode)
        val files =
            app.get("$gdbot/search?q=$query").document.select("ul.divide-y li").map {
                Triple(
                    it.select("a").attr("href"),
                    it.select("a").text(),
                    it.select("span").text()
                )
            }.filter {
                matchingIndex(
                    it.second,
                    null,
                    title,
                    year,
                    season,
                    episode,
                )
            }.sortedByDescending {
                it.third.getFileSize()
            }

        files.let { file ->
                listOfNotNull(
                    file.find { it.second.contains("2160p", true) },
                    file.find { it.second.contains("1080p", true) })
            }.amap { file ->
            val videoUrl = extractGdflix(file.first)
            val quality = getIndexQuality(file.second)
            val tags = getIndexQualityTags(file.second)
            val size =
                Regex("(\\d+\\.?\\d+\\sGB|MB)").find(file.third)?.groupValues?.get(0)
                    ?.trim()

            callback.invoke(
                newExtractorLink(
                    "GdbotMovies",
                    "GdbotMovies $tags [$size]",
                    url = videoUrl ?: return@amap null
                ) {
                    this.referer = ""
                    this.quality = quality
                }
            )
        }

    }

    suspend fun invokeDahmerMovies(
        apiurl: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$apiurl/movies/${title?.replace(":", "")} ($year)/"
        } else {
            "$apiurl/tvs/${title?.replace(":", " -")}/Season $season/"
        }
        val request = app.get(url, timeout = 60L)
        if (!request.isSuccessful) return
        val paths = request.document.select("a").map {
            it.text() to it.attr("href")
        }.filter {
            if (season == null) {
                it.first.contains(Regex("(?i)(1080p|2160p)"))
            } else {
                val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
                it.first.contains(Regex("(?i)S${seasonSlug}E${episodeSlug}"))
            }
        }.ifEmpty { return }

        fun decode(input: String): String = java.net.URLDecoder.decode(input, "utf-8")
        paths.map {
            val quality = getIndexQuality(it.first)
            val tags = getIndexQualityTags(it.first)
            callback.invoke(
                newExtractorLink(
                    "DahmerMovies",
                    "DahmerMovies $tags",
                    url = decode((url + it.second).encodeUrl())
                ) {
                    this.referer = ""
                    this.quality = quality
                }
            )
        }

    }

    suspend fun invoke2embed(
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$twoEmbedAPI/embed/$imdbId"
        } else {
            "$twoEmbedAPI/embedtv/$imdbId&s=$season&e=$episode"
        }
        val framesrc =
            app.get(url).document.selectFirst("iframe#iframesrc")?.attr("data-src")
                ?: return
        val ref = getBaseUrl(framesrc)
        val id = framesrc.substringAfter("id=").substringBefore("&")
        loadExtractor("https://uqloads.xyz/e/$id", "$ref/", subtitleCallback, callback)
    }


    suspend fun invokeShowflix(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        api: String = "https://parse.showflix.shop"
    ) {
        val where = if (season == null) "movieName" else "seriesName"
        val classes = if (season == null) "movies" else "series"
        val body = """
        {
            "where": {
                "$where": {
                    "${'$'}regex": "$title",
                    "${'$'}options": "i"
                }
            },
            "order": "-updatedAt",
            "_method": "GET",
            "_ApplicationId": "SHOWFLIXAPPID",
            "_JavaScriptKey": "SHOWFLIXMASTERKEY",
            "_ClientVersion": "js3.4.1",
            "_InstallationId": "58f0e9ca-f164-42e0-a683-a1450ccf0221"
        }
    """.trimIndent().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val data =
            app.post("$api/parse/classes/$classes", requestBody = body).text
        val iframes = if (season == null) {
            val result = tryParseJson<ShowflixSearchMovies>(data)?.resultsMovies?.find {
                it.movieName.equals("$title ($year)", true)
            }
            listOf(
                "https://streamwish.to/e/${result?.streamwish}",
                "https://filelions.to/v/${result?.filelions}.html",
                "https://streamruby.com/${result?.streamruby}",
            )
        } else {
            val result = tryParseJson<ShowflixSearchSeries>(data)?.resultsSeries?.find {
                it.seriesName.equals(title, true)
            }
            listOf(
                result?.streamwish?.get("Season $season")?.get(episode!!),
                result?.filelions?.get("Season $season")?.get(episode!!),
                result?.streamruby?.get("Season $season")?.get(episode!!),
            )
        }
        iframes.amap { iframe ->
            loadSourceNameExtractor(
                "Showflix ",
                iframe ?: return@amap,
                "$showflixAPI/",
                subtitleCallback,
                callback
            )
        }

    }

    suspend fun invokeZoechip(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val slug = title?.createSlug()
        val url = if (season == null) {
            "$zoechipAPI/film/${title?.createSlug()}-$year"
        } else {
            "$zoechipAPI/episode/$slug-season-$season-episode-$episode"
        }

        val id =
            app.get(url).document.selectFirst("div#show_player_ajax")?.attr("movie-id")
                ?: return

        val server = app.post(
            "$zoechipAPI/wp-admin/admin-ajax.php", data = mapOf(
                "action" to "lazy_player",
                "movieID" to id,
            ), referer = url, headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).document.selectFirst("ul.nav a:contains(Filemoon)")?.attr("data-server")

        val res = app.get(server ?: return, referer = "$zoechipAPI/")
        val host = getBaseUrl(res.url)
        val script =
            res.document.select("script:containsData(function(p,a,c,k,e,d))").last()
                ?.data()
        val unpacked = getAndUnpack(script ?: return)

        val m3u8 =
            Regex("file:\\s*\"(.*?m3u8.*?)\"").find(unpacked)?.groupValues?.getOrNull(1)

        M3u8Helper.generateM3u8(
            "Zoechip",
            m3u8 ?: return,
            "$host/",
        ).forEach(callback)

    }

    suspend fun invokeCinemaTv(
        imdbId: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val id = imdbId?.removePrefix("tt")
        val slug = title?.createSlug()
        val url = if (season == null) {
            "$cinemaTvAPI/movies/play/$id-$slug-$year"
        } else {
            "$cinemaTvAPI/shows/play/$id-$slug-$year"
        }

        val headers = mapOf(
            "x-requested-with" to "XMLHttpRequest",
        )
        val doc = app.get(url, headers = headers).document
        val script = doc.selectFirst("script:containsData(hash:)")?.data()
        val hash =
            Regex("hash:\\s*['\"](\\S+)['\"]").find(script ?: return)?.groupValues?.get(
                1
            )
        val expires = Regex("expires:\\s*(\\d+)").find(script)?.groupValues?.get(1)
        val episodeId = (if (season == null) {
            """id_movie:\s*(\d+)"""
        } else {
            """episode:\s*['"]$episode['"],[\n\s]+id_episode:\s*(\d+),[\n\s]+season:\s*['"]$season['"]"""
        }).let { it.toRegex().find(script)?.groupValues?.get(1) }

        val videoUrl = if (season == null) {
            "$cinemaTvAPI/api/v1/security/movie-access?id_movie=$episodeId&hash=$hash&expires=$expires"
        } else {
            "$cinemaTvAPI/api/v1/security/episode-access?id_episode=$episodeId&hash=$hash&expires=$expires"
        }

        val sources = app.get(
            videoUrl,
            referer = url,
            headers = headers
        ).parsedSafe<CinemaTvResponse>()

        sources?.streams?.mapKeys { source ->
            callback.invoke(
                newExtractorLink(
                    "CinemaTv",
                    "CinemaTv",
                    url = source.value,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = "$cinemaTvAPI/"
                    this.quality = getQualityFromName(source.key)
                }
            )
        }

        sources?.subtitles?.map { sub ->
            val file = sub.file.toString()
            subtitleCallback.invoke(
                SubtitleFile(
                    sub.language ?: return@map,
                    if (file.startsWith("[")) return@map else fixUrl(file, cinemaTvAPI),
                )
            )
        }

    }

    suspend fun invokeNinetv(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$nineTvAPI/movie/$tmdbId"
        } else {
            "$nineTvAPI/tv/$tmdbId-$season-$episode"
        }
        val iframe =
            app.get(
                url,
                referer = "https://pressplay.top/"
            ).document.selectFirst("iframe")
                ?.attr("src")
        loadExtractor(iframe ?: return, "$nineTvAPI/", subtitleCallback, callback)
    }

    suspend fun invokeNowTv(
        tmdbId: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        referer: String = "https://bflix.gs/"
    ) {
        suspend fun String.isSuccess(): Boolean {
            return app.get(this, referer = referer).isSuccessful
        }

        val slug = getEpisodeSlug(season, episode)
        var url =
            if (season == null) "$nowTvAPI/$tmdbId.mp4" else "$nowTvAPI/tv/$tmdbId/s${season}e${slug.second}.mp4"
        if (!url.isSuccess()) {
            url = if (season == null) {
                val temp = "$nowTvAPI/$imdbId.mp4"
                if (temp.isSuccess()) temp else "$nowTvAPI/$tmdbId-1.mp4"
            } else {
                "$nowTvAPI/tv/$imdbId/s${season}e${slug.second}.mp4"
            }
            if (!app.get(url, referer = referer).isSuccessful) return
        }
        callback.invoke(
            newExtractorLink(
                "NowTv",
                "NowTv",
                url = url
            ) {
                this.referer = referer
                this.quality = Qualities.P1080.value
            }
        )
    }

    suspend fun invokeRidomovies(
        tmdbId: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val mediaSlug =
            app.get("$ridomoviesAPI/core/api/search?q=$imdbId", interceptor = wpRedisInterceptor)
                .parsedSafe<RidoSearch>()?.data?.items?.find {
                    it.contentable?.tmdbId == tmdbId || it.contentable?.imdbId == imdbId
                }?.slug ?: return

        val id = season?.let {
            val episodeUrl = "$ridomoviesAPI/tv/$mediaSlug/season-$it/episode-$episode"
            app.get(
                episodeUrl,
                interceptor = wpRedisInterceptor
            ).text.substringAfterLast("""postid\":\"""").substringBefore("\"")
        } ?: mediaSlug

        val url =
            "$ridomoviesAPI/core/api/${if (season == null) "movies" else "episodes"}/$id/videos"
        app.get(url, interceptor = wpRedisInterceptor)
            .parsedSafe<RidoResponses>()?.data?.amap { link ->
                val iframe = Jsoup.parse(link.url ?: return@amap).select("iframe").attr("data-src")
                if (iframe.startsWith("https://closeload.top")) {
                    val unpacked = getAndUnpack(
                        app.get(
                            iframe,
                            referer = "$ridomoviesAPI/",
                            interceptor = wpRedisInterceptor
                        ).text
                    )
                    val encodeHash =
                        Regex("\\(\"([^\"]+)\"\\);").find(unpacked)?.groupValues?.get(1) ?: ""
                    val video = base64Decode(base64Decode(encodeHash).reversed()).split("|").get(1)
                    callback.invoke(
                        newExtractorLink(
                            "Ridomovies",
                            "Ridomovies",
                            url = video,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = "${getBaseUrl(iframe)}/"
                            this.quality = Qualities.P1080.value
                        }
                    )
                } else {
                    loadSourceNameExtractor(
                        "Ridomovies",
                        iframe,
                        "$ridomoviesAPI/",
                        subtitleCallback,
                        callback
                    )
                }
            }
    }

    suspend fun invokeAllMovieland(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        runCatching {
            val playerScript = app.get("https://allmovieland.link/player.js?v=60%20128").toString()
            val domainRegex = Regex("const AwsIndStreamDomain.*'(.*)';")
            val host = domainRegex.find(playerScript)?.groupValues?.getOrNull(1) ?: return

            val resData = app.get(
                "$host/play/$imdbId",
                referer = "$allmovielandAPI/"
            ).document.selectFirst("script:containsData(playlist)")?.data()
                ?.substringAfter("{")?.substringBefore(";")?.substringBefore(")") ?: return

            val json = tryParseJson<AllMovielandPlaylist>("{$resData}") ?: return
            val headers = mapOf(("X-CSRF-TOKEN" to "${json.key}"))

            val serverJson = app.get(
                fixUrl(json.file ?: return, host),
                headers = headers,
                referer = "$allmovielandAPI/"
            ).text.replace(Regex(""",\\s*\\/"""), "")

            val servers = tryParseJson<ArrayList<AllMovielandServer>>(serverJson)?.let { list ->
                if (season == null) {
                    list.mapNotNull { it.file?.let { file -> file to it.title.orEmpty() } }
                } else {
                    list.find { it.id == season.toString() }
                        ?.folder?.find { it.episode == episode.toString() }
                        ?.folder?.mapNotNull { it.file?.let { file -> file to it.title.orEmpty() } }
                }
            } ?: return

            servers.amap { (server, lang) ->
                runCatching {
                    val playlistUrl = app.post(
                        "$host/playlist/$server.txt",
                        headers = headers,
                        referer = "$allmovielandAPI/"
                    ).text

                    callback.invoke(
                        newExtractorLink(
                            "AllMovieLand-$lang",
                            "AllMovieLand-$lang",
                            url = playlistUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            referer = allmovielandAPI
                            quality = Qualities.Unknown.value
                        }
                    )
                }.onFailure { it.printStackTrace() }
            }
        }.onFailure {
            it.printStackTrace()
        }
    }


    suspend fun invokeEmovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val slug = title.createSlug()
        val url = if (season == null) {
            "$emoviesAPI/watch-$slug-$year-1080p-hd-online-free/watching.html"
        } else {
            val first =
                "$emoviesAPI/watch-$slug-season-$season-$year-1080p-hd-online-free.html"
            val second = "$emoviesAPI/watch-$slug-$year-1080p-hd-online-free.html"
            if (app.get(first).isSuccessful) first else second
        }

        val res = app.get(url).document
        val id = (if (season == null) {
            res.selectFirst("select#selectServer option[sv=oserver]")?.attr("value")
        } else {
            res.select("div.le-server a").find {
                val num =
                    Regex("Episode (\\d+)").find(it.text())?.groupValues?.get(1)
                        ?.toIntOrNull()
                num == episode
            }?.attr("href")
        })?.substringAfter("id=")?.substringBefore("&")

        val server = app.get(
            "$emoviesAPI/ajax/v4_get_sources?s=oserver&id=${id ?: return}&_=${unixTimeMS}",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<EMovieServer>()?.value

        val script = app.get(
            server
                ?: return, referer = "$emoviesAPI/"
        ).document.selectFirst("script:containsData(sources:)")?.data()
            ?: return
        val sources =
            Regex("sources:\\s*\\[(.*)],").find(script)?.groupValues?.get(1)?.let {
                tryParseJson<List<EMovieSources>>("[$it]")
            }
        val tracks =
            Regex("tracks:\\s*\\[(.*)],").find(script)?.groupValues?.get(1)?.let {
                tryParseJson<List<EMovieTraks>>("[$it]")
            }

        sources?.map { source ->
            M3u8Helper.generateM3u8(
                "Emovies", source.file
                    ?: return@map, "https://embed.vodstream.xyz/"
            ).forEach(callback)
        }

        tracks?.map { track ->
            subtitleCallback.invoke(
                SubtitleFile(
                    track.label ?: "",
                    track.file ?: return@map,
                )
            )
        }


    }

    suspend fun invokeSFMovies(
        tmdbId: Int? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers =
            mapOf("Authorization" to "Bearer 44d784c55e9a1e3dbb586f24b18b1cbcd1521673bd6178ef385890d2f989681fe22d05e291e2e0f03fce99cbc50cd520219e52cc6e30c944a559daf53a129af18349ec98f6a0e4e66b8d370a354f4f7fbd49df0ab806d533a3db71eecc7f75131a59ce8cffc5e0cc38e8af5919c23c0d904fbe31995308f065f0ff9cd1eda488")
        val data = app.get(
            "${BuildConfig.SFMOVIES_API}/api/mains?filters[title][\$contains]=$title",
            headers = headers
        ).parsedSafe<SFMoviesSearch>()?.data
        val media = data?.find {
            it.attributes?.contentId.equals("$tmdbId") || (it.attributes?.title.equals(
                title,
                true
            ) || it.attributes?.releaseDate?.substringBefore("-").equals("$year"))
        }
        val video = if (season == null || episode == null) {
            media?.attributes?.video
        } else {
            media?.attributes?.seriess?.get(season - 1)?.get(episode - 1)?.svideos
        } ?: return
        callback.invoke(
            newExtractorLink(
                "SFMovies",
                "SFMovies",
                url = fixUrl(video, getSfServer()),
                INFER_TYPE
        ) {
            this.referer = ""
            this.quality = Qualities.P1080.value
        }
        )
    }

    suspend fun invokePlaydesi(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$PlaydesiAPI/$fixTitle"
        } else {
            "$PlaydesiAPI/$fixTitle-season-$season-episode-$episode-watch-online"
        }
        val document = app.get(url).document
        document.select("div.entry-content > p a").forEach {
            val link = it.attr("href")
            val trueurl = app.get((link)).document.selectFirst("iframe")?.attr("src").toString()
            loadExtractor(trueurl, subtitleCallback, callback)
        }
    }


    suspend fun invokeMoviesdrive(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val firstRedirectUrl = retryIO(times = 3) {
            val response = app.get(MovieDriveAPI, timeout = 15_000)
            response.document
                .selectFirst("meta[http-equiv=refresh]")
                ?.attr("content")
                ?.substringAfter("url=", "")
                ?.takeIf { it.startsWith("http") }
        }

        val movieDriveAPI = runCatching {
            firstRedirectUrl?.let {
                app.get(it, allowRedirects = false).headers["Location"]
            }
        }.getOrNull()?.takeIf { it.startsWith("http") } ?: return

        val cfInterceptor = CloudflareKiller()
        val fixTitle = title?.substringBefore("-")
            ?.replace(":", " ")
            ?.replace("&", " ")
            ?.trim()
            .orEmpty()

        val searchTitle = fixTitle.createSlug()
        val searchUrl = if (season == null) {
            "$movieDriveAPI/search/$fixTitle $year"
        } else {
            "$movieDriveAPI/search/$fixTitle"
        }

        val searchResultHtml = runCatching {
            app.get(searchUrl, interceptor = wpRedisInterceptor).document.select("figure").toString()
        }.getOrNull() ?: return

        val hrefPattern = Regex("""(?i)<a\s+href="([^"]*\b$searchTitle\b[^"]*)"""")
            .find(searchResultHtml)
            ?.groupValues?.getOrNull(1)
            ?.takeIf { it.startsWith("http") }
            ?: return

        val doc = app.get(hrefPattern, interceptor = cfInterceptor).document

        if (season == null) {
            doc.select("h5 a").amap { element ->
                extractMdrive(element.attr("href")).forEach { serverUrl ->
                    processMoviesdriveUrl(serverUrl, subtitleCallback, callback)
                }
            }
        } else {
            val seasonPattern = "(?i)Season\\s*0?$season\\b|S0?$season\\b"
            val episodePattern = "(?i)Ep0?$episode\\b|Ep\\s+$episode\\b"

            doc.select("h5:matches($seasonPattern)").forEach { seasonElement ->
                seasonElement.nextElementSibling()
                    ?.selectFirst("a")
                    ?.attr("href")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { seasonHref ->
                        val episodeDoc = app.get(seasonHref).document
                        val episodeHeader = episodeDoc.selectFirst("h5:matches($episodePattern)")

                        val links = episodeHeader?.let {
                            generateSequence(it.nextElementSibling()) { next -> next.nextElementSibling() }
                                .takeWhile { it.tagName() == "h5" }
                                .mapNotNull { it.selectFirst("a")?.attr("href") }
                                .toList()
                        }

                        if (!links.isNullOrEmpty()) {
                            links.forEach { serverUrl ->
                                processMoviesdriveUrl(serverUrl, subtitleCallback, callback)
                            }
                        } else {
                            episodeDoc.selectFirst("h5 a:contains(HubCloud)")?.attr("href")?.let { fallbackUrl ->
                                processMoviesdriveUrl(fallbackUrl, subtitleCallback, callback)
                            }
                        }
                    }
            }
        }
    }

    private suspend fun processMoviesdriveUrl(
        serverUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        when {
            serverUrl.contains("hubcloud", ignoreCase = true) -> {
                HubCloud().getUrl(serverUrl, "MoviesDrive", subtitleCallback, callback)
            }
            serverUrl.contains("gdlink", ignoreCase = true) -> {
                GDFlix().getUrl(serverUrl, referer = "MoviesDrive", subtitleCallback = subtitleCallback, callback = callback)
            }
            else -> {
                loadExtractor(serverUrl, referer = "MoviesDrive", subtitleCallback, callback)
            }
        }
    }

    suspend fun invokeBollyflix(
        imdbId: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        lastSeason: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val doc = app.get(
            "$bollyflixAPI/search/$imdbId",
            interceptor = wpRedisInterceptor
        ).document.select("#content_box article")
        val url = doc.select("a").attr("href")
        val urldoc = app.get(url).document
        if (season == null) {
            urldoc.select("a.dl").amap {
                val href = it.attr("href")
                val token = href.substringAfter("id=")
                val encodedurl =
                    app.get("https://blog.finzoox.com/?id=$token").text.substringAfter("link\":\"")
                        .substringBefore("\"};")
                val decodedurl = base64Decode(encodedurl)
                val source = app.get(decodedurl, allowRedirects = false).headers["location"] ?: ""
                if (source.contains("gdflix")) {
                    val trueurl = app.get(source, allowRedirects = false).headers["location"] ?: ""
                    loadCustomExtractor(
                        "BollyFlix",
                        trueurl,
                        "",
                        subtitleCallback,
                        callback
                    )
                } else loadExtractor(source, "Bollyflix", subtitleCallback, callback)
            }
        } else {
            urldoc.select("a.maxbutton-2").forEach {
                //TODO
            }
        }
    }

    suspend fun invokecatflix(
        id: Int? = null,
        epid: Int? = null,
        title: String? = null,
        episode: Int? = null,
        season: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        try {
            val fixtitle = title.createSlug()

            val juicyKey = runCatching {
                app.get(BuildConfig.CatflixAPI, referer = Catflix)
                    .parsedSafe<CatflixJuicy>()
                    ?.juice
            }.getOrNull()

            val href = if (season == null) {
                "$Catflix/movie/$fixtitle-$id"
            } else {
                "$Catflix/episode/${fixtitle}-season-${season}-episode-${episode}/eid-$epid"
            }
            val pageHtml = runCatching {
                app.get(href, referer = Catflix).toString()
            }.getOrNull() ?: return

            val iframe = runCatching {
                Regex("""(?:const|let)\s+main_origin\s*=\s*"(.*)";""")
                    .find(pageHtml)
                    ?.groupValues?.get(1)
                    ?.let(::base64Decode)
            }.getOrNull() ?: return

            val iframeHtml = runCatching {
                app.get(iframe, referer = Catflix).toString()
            }.getOrNull() ?: return

            val apkey = Regex("""apkey\s*=\s*['"](.*?)["']""")
                .find(iframeHtml)
                ?.groupValues?.getOrNull(1)

            val xxid = Regex("""xxid\s*=\s*['"](.*?)["']""")
                .find(iframeHtml)
                ?.groupValues?.getOrNull(1)

            if (apkey.isNullOrEmpty() || xxid.isNullOrEmpty()) return

            val juiceUrl = "https://turbovid.eu/api/cucked/the_juice/?$apkey=$xxid"

            val juiceData = runCatching {
                app.get(
                    juiceUrl,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    referer = juiceUrl
                ).parsedSafe<CatflixJuicydata>()?.data
            }.getOrNull()

            if (!juiceData.isNullOrEmpty() && !juicyKey.isNullOrEmpty()) {
                val finalUrl = runCatching {
                    CatdecryptHexWithKey(juiceData, juicyKey)
                }.getOrNull()

                if (!finalUrl.isNullOrEmpty()) {
                    val headers = mapOf(
                        "Origin" to "https://turbovid.eu/",
                        "Connection" to "keep-alive"
                    )

                    callback(
                        newExtractorLink(
                            "Catflix",
                            "Catflix",
                            url = finalUrl,
                            INFER_TYPE
                        ) {
                            referer = "https://turbovid.eu/"
                            quality = Qualities.P1080.value
                            this.headers = headers
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("Catflix", "Unexpected error in invokecatflix $e")
        }
    }


    suspend fun invokeDramacool(
        title: String?,
        provider: String,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val titleSlug = title?.replace(" ", "-")
        val s = if(season != 1) "-season-$season" else ""
        val url = "$Dramacool/stream/series/$provider-${titleSlug}${s}::$titleSlug${s}-ep-$episode.json"
        val json = app.get(url).text
        val data = tryParseJson<Dramacool>(json) ?: return
        data.streams.forEach {
            callback.invoke(
                newExtractorLink(
                    it.title,
                    it.title,
                    url = it.url,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = Qualities.Unknown.value
                }
            )

            it.subtitles.forEach {
                subtitleCallback.invoke(
                    SubtitleFile(
                        it.lang,
                        it.url
                    )
                )
            }
        }
    }

    suspend fun invokeBollyflixvip(
        imdbId: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        lastSeason: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val res1 = app.get(
            "$bollyflixAPI/search/$imdbId${season ?: ""}",
            interceptor = wpRedisInterceptor
        ).document
        val url = res1.select("div > article > a").attr("href")

        val res = app.get(url).document
        val hTag = if (season == null) "h5" else "h4"
        val sTag = if (season == null) "" else "Season $season"

        val entries =
            res.select("div.thecontent.clearfix > $hTag:matches((?i)$sTag.*(720p|1080p|2160p))")
                .filter { element -> !element.text().contains("Download", true) }
                .takeLast(4)

        entries.forEach { element ->
            val token =
                element.nextElementSibling()?.select("a")?.attr("href")?.substringAfter("id=")
            val encodedUrl = token?.let {
                app.get("https://blog.finzoox.com/?id=$it").text.substringAfter("link\":\"")
                    .substringBefore("\"};")
            }
            val decodedUrl = encodedUrl?.let { base64Decode(it) }

            decodedUrl?.let {
                if (season == null) {
                    loadSourceNameExtractor("Bollyflix", it, "", subtitleCallback, callback)
                } else {
                    app.get(it).document.selectFirst("article h3 a:contains(Episode 0$episode)")
                        ?.attr("href")?.let { episodeLink ->
                            loadSourceNameExtractor(
                                "Bollyflix",
                                episodeLink,
                                "",
                                subtitleCallback,
                                callback
                            )
                        }
                }
            }
        }
    }

    suspend fun invokeFlixAPIHQ(
        title: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val type = if (season == null) "Movie" else "TV Series"

        val searchData = runCatching {
            val searchJson = app.get("${consumetFlixhqAPI}/$title", timeout = 120L).text
            tryParseJson<ConsumetSearch>(searchJson)
        }.getOrNull() ?: return

        val id = searchData.results.firstOrNull { it.title == title && it.type == type }?.id ?: return

        val infoData = runCatching {
            val infoJson = app.get("$consumetFlixhqAPI/info?id=$id", timeout = 120L).text
            tryParseJson<ConsumetInfo>(infoJson)
        }.getOrNull() ?: return

        val epId = if (season == null) {
            infoData.episodes.firstOrNull()?.id
        } else {
            infoData.episodes.firstOrNull { it.number == episode && it.season == season }?.id
        } ?: return

        val servers = listOf("upcloud", "vidcloud")

        for (server in servers) {
            val epData = runCatching {
                val epJson = app.get("$consumetFlixhqAPI/watch?episodeId=$epId&mediaId=$id&server=$server", timeout = 120L).text
                tryParseJson<ConsumetWatch>(epJson)
            }.getOrNull() ?: continue

            val referer = epData.headers.Referer ?: ""

            epData.sources.forEach {
                callback(
                    newExtractorLink(
                        "FlixHQ ${server.uppercase()}",
                        "FlixHQ ${server.uppercase()}",
                        url = it.url,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = referer
                        this.quality = it.quality.toIntOrNull() ?: Qualities.Unknown.value
                    }
                )

            }

            epData.subtitles.forEach {
                subtitleCallback(
                    SubtitleFile(
                        lang = it.lang,
                        url = it.url
                    )
                )
            }
        }
    }

    /*
    suspend fun invokeHinAuto(
        id: Int? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url =
            if (season == null) "$HinAutoAPI/movie/$id" else "$HinAutoAPI/tv/$id/$season/$episode"
        val res = app.get(url, referer = "https://autoembed.cc", timeout = 5000L).toString()
        val json =
            Regex("sources\\s*:\\s*(\\[[^]]*])").find(res)?.groupValues?.get(1).toString() ?: null
        if (json != null) {
            val jsondata = parseJsonHinAuto((json))
            jsondata.forEach { data ->
                val m3u8 = data.file
                val lang = data.label
                callback.invoke(
                    newExtractorLink(
                        "HIN Autoembed $lang",
                        "HIN Autoembed $lang",
                        url = m3u8,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = ""
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        }
    }

    private fun parseJsonHinAuto(json: String): HinAuto {
        val gson = Gson()

        return gson.fromJson(json, Array<HinAutoRoot2>::class.java).toList()
    }
     */
    suspend fun invokenyaa(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = "$NyaaAPI?f=0&c=0_0&q=$title+S0${season}E0$episode&s=seeders&o=desc"
        app.get(url).document.select("tr.danger,tr.default").take(10).amap {
            val Qualities = getIndexQuality(it.selectFirst("tr td:nth-of-type(2)")?.text())
            val href = getfullURL(it.select("td.text-center a:nth-child(1)").attr("href"), NyaaAPI)
            callback.invoke(
                newExtractorLink(
                    "Nyaa $Qualities",
                    "Nyaa $Qualities",
                    url = href,
                    ExtractorLinkType.TORRENT
                ) {
                    this.referer = ""
                    this.quality = Qualities
                }
            )
        }
    }

    @SuppressLint("NewApi")
    suspend fun invokeRiveStream(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val sourceApiUrl =
            "$RiveStreamAPI/api/backendfetch?requestID=VideoProviderServices&secretKey=rive"
        val sourceList = app.get(sourceApiUrl).parsedSafe<RiveStreamSource>()
        val document = app.get(RiveStreamAPI, timeout = 20).document
        val scripts = document.select("script")
        val appScript =
            scripts.filter { element -> element.attr("src").contains("_app") }.first().attr("src")
        val js = app.get("$RiveStreamAPI$appScript").text
        val regex = """let\s+c\s*=\s*(\[[^]]*])""".toRegex()
        val allMatches = regex.findAll(js).toList()
        val firstNonEmptyMatch = allMatches.firstOrNull { it.groupValues[1].length > 2 } // [] is length 2
        val keyList: List<String> = firstNonEmptyMatch?.let { match ->
            val arrayText = match.groupValues[1]  // e.g., ["a","b","c"]
            Regex("\"([^\"]+)\"").findAll(arrayText).map { it.groupValues[1] }.toList()
        } ?: emptyList()
        val secretKey = app.get("https://rivestream.supe2372.workers.dev/?input=$id&cList=${keyList.joinToString(",")}").text
        if (sourceList != null) {
            for (source: String in sourceList.data) {
                try {
                    val sourceStreamLink = if (season == null) {
                        "$RiveStreamAPI/api/backendfetch?requestID=movieVideoProvider&id=$id&service=$source&secretKey=${secretKey}"
                    } else {
                        "$RiveStreamAPI/api/backendfetch?requestID=tvVideoProvider&id=$id&season=$season&episode=$episode&service=$source&secretKey=${secretKey}"
                    }

                    val sourceJson = app.get(sourceStreamLink, timeout = 10).parsedSafe<RiveStreamResponse>()

                    if (sourceJson?.data != null) {
                        Log.d("RiveStreamResponse", "Sources found for service: $source")
                        sourceJson.data.sources.forEach { source ->

                            if (source.url.contains("m3u8-proxy?url")) {
                                val href = URLDecoder.decode(
                                    source.url.substringAfter("m3u8-proxy?url=").substringBefore("&headers="),
                                    "UTF-8"
                                )
                                callback.invoke(
                                    newExtractorLink(
                                        "RiveStream ${source.source} ${source.quality}",
                                        "RiveStream ${source.source} ${source.quality}",
                                        url = href,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        this.referer = "https://megacloud.store/"
                                        this.quality = Qualities.P1080.value
                                    }
                                )
                            } else {
                                val linkType =
                                    if (source.url.contains(".m3u8", ignoreCase = true)) {
                                        ExtractorLinkType.M3U8
                                    } else {
                                        INFER_TYPE
                                    }

                                callback.invoke(
                                    newExtractorLink(
                                        "RiveStream ${source.source} ${source.quality} (VLC)",
                                        "RiveStream ${source.source} ${source.quality} (VLC)",
                                        url = source.url,
                                        type = linkType
                                    ) {
                                        this.referer = ""
                                        this.quality = Qualities.P1080.value
                                    }
                                )
                            }
                        }
                    } else {
                        Log.d("RiveStreamResponse", "No data returned for service: $source")
                    }
                } catch (e: Exception) {
                    Log.e("RiveStreamError", "Failed to process source: $source")
                }
            }

        }
    }

    suspend fun invokeVidSrcViP(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$VidSrcVip/hnd.php?id=$id"
        } else {
            "$VidSrcVip/hnd.php?id=${id}&s=${season}&e=${episode}"
        }
        val json = app.get(url).text
        try {
            val objectMapper = jacksonObjectMapper()
            val vidsrcsuList: List<VidSrcVipSource> = objectMapper.readValue(json)
            for (source in vidsrcsuList) {
                callback.invoke(
                    newExtractorLink(
                        "VidSrcVip ${source.language}",
                        "VidSrcVip ${source.language}",
                        url = source.m3u8Stream,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = ""
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        } catch (_: Exception) {

        }
    }


    suspend fun invokeVidSrcXyz(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$Vidsrcxyz/embed/movie?imdb=$id"
        } else {
            "$Vidsrcxyz/embed/tv?imdb=$id&season=$season&episode=$episode"
        }
        val iframeUrl = extractIframeUrl(url) ?: return
        val prorcpUrl = extractProrcpUrl(iframeUrl) ?: return
        val decryptedSource = extractAndDecryptSource(prorcpUrl) ?: return

        val referer = prorcpUrl.substringBefore("rcp")
        callback.invoke(
            newExtractorLink(
                "Vidsrc",
                "Vidsrc",
                url = decryptedSource,
                ExtractorLinkType.M3U8
            ) {
                this.referer = referer
                this.quality = Qualities.P1080.value
            }
        )
    }

    private suspend fun extractIframeUrl(url: String): String? {
        return httpsify(
            app.get(url).document.select("iframe").attr("src")
        ).takeIf { it.isNotEmpty() }
    }

    private suspend fun extractProrcpUrl(iframeUrl: String): String? {
        val doc = app.get(iframeUrl).document
        val regex = Regex("src:\\s+'(.*?)'")
        val matchedSrc = regex.find(doc.html())?.groupValues?.get(1) ?: return null
        val host = getBaseUrl(iframeUrl)
        val newDoc = app.get(host + matchedSrc).document

        val regex1 = Regex("""(https?://.*?/prorcp.*?)["']\)""")
        return regex1.find(newDoc.html())?.groupValues?.get(1)
    }

    private suspend fun extractAndDecryptSource(prorcpUrl: String): String? {
        val responseText = app.get(prorcpUrl).text

        val playerJsRegex = Regex("""Playerjs\(\{.*?file:"(.*?)".*?\}\)""")
        val temp = playerJsRegex.find(responseText)?.groupValues?.get(1)

        val encryptedURLNode = if (!temp.isNullOrEmpty()) {
            mapOf("id" to "playerjs", "content" to temp)
        } else {
            val document = Jsoup.parse(responseText)
            val node = document.select("#reporting_content").next()
            mapOf("id" to node.attr("id"), "content" to node.text())
        }

        return encryptedURLNode["id"]?.let { id ->
            encryptedURLNode["content"]?.let { content ->
                decryptMethods[id]?.invoke(content)
            }
        }
    }


    suspend fun invokePrimeWire(
        id: Int? = null,
        imdbId: String? = null,
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$Primewire/embed/movie?imdb=$imdbId"
        } else {
            "$Primewire/embed/tv?imdb=$imdbId&season=$season&episode=$episode"
        }
        val doc = app.get(url, timeout = 10).document
        val userData = doc.select("#user-data")
        var decryptedLinks = decryptLinks(userData.attr("v"))
        for (link in decryptedLinks) {
            val url = "$Primewire/links/go/$link"
            val oUrl = app.get(url, timeout = 10)
            loadSourceNameExtractor(
                "Primewire",
                oUrl.url,
                "",
                subtitleCallback,
                callback
            )
        }
    }


    suspend fun invokeFilm1k(
        id: Int? = null,
        imdbId: String? = null,
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (season == null) {
            try {
                val fixTitle = title?.replace(":", "")?.replace(" ", "+")
                val doc = app.get("$Film1kApi/?s=$fixTitle", cacheTime = 60, timeout = 30).document
                val posts = doc.select("header.entry-header").filter { element ->
                    element.selectFirst(".entry-title")?.text().toString().contains(
                        "${
                            title?.replace(
                                ":",
                                ""
                            )
                        }"
                    ) && element.selectFirst(".entry-title")?.text().toString()
                        .contains(year.toString())
                }.toList()
                val url = posts.firstOrNull()?.select("a:nth-child(1)")?.attr("href")
                val postDoc = url?.let { app.get(it, cacheTime = 60, timeout = 30).document }
                val id = postDoc?.select("a.Button.B.on")?.attr("data-ide")
                repeat(5) { i ->
                    val mediaType = "application/x-www-form-urlencoded".toMediaType()
                    val body =
                        "action=action_change_player_eroz&ide=$id&key=$i".toRequestBody(mediaType)
                    val ajaxUrl = "$Film1kApi/wp-admin/admin-ajax.php"
                    val doc =
                        app.post(ajaxUrl, requestBody = body, cacheTime = 60, timeout = 30).document
                    var url = doc.select("iframe").attr("src").replace("\\", "").replace(
                        "\"",
                        ""
                    ) // It is necessary because it returns link with double qoutes like this ("https://voe.sx/e/edpgpjsilexe")
                    val film1kRegex = Regex("https://film1k\\.xyz/e/([^/]+)/.*")
                    if (url.contains("https://film1k.xyz")) {
                        val matchResult = film1kRegex.find(url)
                        if (matchResult != null) {
                            val code = matchResult.groupValues[1]
                            url = "https://filemoon.sx/e/$code"
                        }
                    }
                    url = url.replace("https://films5k.com", "https://mwish.pro")
                    loadSourceNameExtractor(
                        "Film1k",
                        url,
                        "",
                        subtitleCallback,
                        callback
                    )
                }
            } catch (e: Exception) {
            }

        }


    }


    suspend fun invokeHindMoviez(
        title: String? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val fixTitle = if (season == null) {
                imdbId
            } else {
                "$title season $season".replace(" ", "+")
            }
            val doc = app.get("$HindMoviezApi/?s=$fixTitle", cacheTime = 60, timeout = 30).document
            var filterpost: Element? = null
            if (season != null) {

                //doc.select(".post").filter { element -> element.select(".entry-title-link").text().contains(title.toString()) }.firstOrNull()
                for (element in doc.select(".post")) {
                    val title = element.select(".entry-title-link").text()
                    if (title.contains(title)) {
                        val multiSeasonRegex = "(\\d)-(\\d)"
                        val seasonRegex = "([S|s]eason\\s*(\\d{1,3}))"
                        val isMultiSeason = multiSeasonRegex.toRegex().containsMatchIn(title)
                        var startSeasonFilter: Int? = 1
                        var endSeasonFilter: Int? = 1
                        if (isMultiSeason) {
                            startSeasonFilter = multiSeasonRegex.toRegex().find(title)?.groups?.get(
                                1
                            )?.value?.toInt()
                            endSeasonFilter = multiSeasonRegex.toRegex()
                                .find(title)?.groups?.get(2)?.value?.toInt()
                            if (startSeasonFilter != null && endSeasonFilter != null) {
                                if (season >= startSeasonFilter && season <= endSeasonFilter) {
                                    filterpost = element
                                }
                            }
                        } else {
                            val postSeason =
                                seasonRegex.toRegex().find(title)?.groups?.get(2)?.value?.toInt()
                            if (season == postSeason) {
                                filterpost = element
                            }
                        }
                    }
                }
            } else {
                filterpost = doc.select(".post").firstOrNull()
            }
            if (filterpost != null) {
                val postUrl = filterpost?.select(".entry-title-link")?.attr("href")
                val postDoc = postUrl?.let { app.get(it, cacheTime = 60, timeout = 30).document }
                val title = postDoc?.selectFirst(".entry-title")?.text() ?: ""
                val qualityRegex2 = "(\\d{3,4})[pP]".toRegex()
                if (title.lowercase().contains("season") && season != null) {
                    val elements = postDoc?.selectFirst(".entry-content")
                    val qualityRegex = ">(\\d{3,4}p).*<".toRegex()
                    val seasonRegex = "(\\d)-(\\d)"
                    val isMultiSeason = seasonRegex.toRegex().containsMatchIn(title)
                    var startSeason: Int? = 1
                    var endSeason: Int? = 1
                    if (isMultiSeason) {
                        startSeason =
                            seasonRegex.toRegex().find(title)?.groups?.get(1)?.value?.toInt()
                        endSeason =
                            seasonRegex.toRegex().find(title)?.groups?.get(2)?.value?.toInt()
                    }
                    if (startSeason != null && endSeason != null) {
                        val seasonList = mutableListOf<SeasonDetail>()
                        for (i in startSeason..endSeason) {
                            if (elements != null) {
                                for (j in 0..<elements.children().size) {
                                    val item = elements.children().get(j)
                                    val currentSeason = "Season $i"
                                    if (item.tagName() == "h3" && (qualityRegex.containsMatchIn(item.html()) || qualityRegex2.containsMatchIn(
                                            item.html()
                                        ))
                                    ) {
                                        if (item.text().lowercase()
                                                .contains(currentSeason.lowercase())
                                        ) {

                                            val quality =
                                                item.select("span[style=\"color: #ff00ff;\"]")
                                                    .text()
                                            val episodeUrls = item.nextElementSibling()?.select("a")
                                            val episodeLinksMap =
                                                mutableMapOf<String, MutableList<String>>()
                                            if (episodeUrls != null) {
                                                episodeUrls.forEach { item ->
                                                    val episodeUrl = item.attr("href")
                                                    if (episodeUrl.isNotEmpty()) {
                                                        val doc = app.get(
                                                            episodeUrl,
                                                            allowRedirects = true,
                                                            timeout = 30
                                                        ).document
                                                        val episodelinks =
                                                            doc.select(".entry-content h3")
                                                        episodelinks.forEach { item ->
                                                            val url = item.select("a").attr("href")
                                                            val episodeName =
                                                                item.select("a").text()
                                                            if (!episodeName.lowercase()
                                                                    .contains("batch")
                                                            ) {
                                                                if (!episodeLinksMap[episodeName].isNullOrEmpty()) {
                                                                    episodeLinksMap[episodeName]?.add(
                                                                        url
                                                                    )
                                                                } else {
                                                                    val links =
                                                                        mutableListOf<String>()
                                                                    links.add(url)
                                                                    episodeLinksMap[episodeName] =
                                                                        links

                                                                }
                                                            }
                                                        }


                                                    }
                                                }
                                            }
                                            seasonList.add(
                                                SeasonDetail(
                                                    quality,
                                                    episodeLinksMap,
                                                    currentSeason
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        val seasonListFilter = seasonList.filter { Season ->
                            Season.season == "Season $season"
                        }
                        val episodeMap = mutableMapOf<String, MutableList<String>>()
                        seasonListFilter.forEach { item ->
                            val episodeList = item.episodeLinkMap
                            if (episodeList != null) {
                                for ((k, v) in episodeList) {
                                    if (!episodeMap[k].isNullOrEmpty()) {
                                        episodeMap[k]?.addAll(v)
                                    } else {
                                        episodeMap[k] = v
                                    }
                                }

                            }
                        }
                        for ((k, v) in episodeMap) {
                            val episodeNo = "([E|e]pisode\\s*(\\d{1,3}))".toRegex()
                                .find(k)?.groups?.get(2)?.value.toString().toInt()
                            if (episodeNo == episode) {
                                loadHindMoviezLinks(v.joinToString("+"), callback)
                            }
                        }
                    }
                } else {
                    val elements = postDoc?.selectFirst(".entry-content")
                    val qualityRegex = ">(\\d{3,4}p).*<".toRegex()
                    val movieLinksList = mutableListOf<String>()
                    if (elements != null) {
                        for (j in 0..(elements.children().size - 1)) {

                            val item = elements?.children()?.get(j)
                            if (item != null) {
                                if (item.tagName() == "h3" && (qualityRegex.containsMatchIn(item.html()) || qualityRegex2.containsMatchIn(
                                        item.html()
                                    ))
                                ) {
                                    item.nextElementSibling()?.select("a")?.forEach { item ->
                                        val episodeUrl =
                                            if (item.attr("href").contains("href.li")) {
                                                item.attr("href").substringAfter("/?")
                                            } else {
                                                item.attr("href")
                                            }
                                        if (episodeUrl.isNotEmpty()) {
                                            val doc = app.get(
                                                episodeUrl,
                                                allowRedirects = true,
                                                timeout = 30
                                            ).document
                                            val episodelinks = doc.select(".entry-content h3")
                                            episodelinks.forEach { item ->
                                                val url = item.select("a").attr("href")
                                                movieLinksList.add(url)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    loadHindMoviezLinks(movieLinksList.joinToString("+"), callback)
                }
            }
        } catch (e: Exception) {
            println("Error: No Links found for HindMoviezLinks")
        }
    }

    suspend fun invokeSuperstream(
        token: String? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val searchUrl = "$fourthAPI/search?keyword=$imdbId"
        val href = app.get(searchUrl).document.selectFirst("h2.film-name a")?.attr("href")
            ?.let { fourthAPI + it }
        val mediaId = href?.let {
            app.get(it).document.selectFirst("h2.heading-name a")?.attr("href")
                ?.substringAfterLast("/")?.toIntOrNull()
        }
        mediaId?.let {
            invokeExternalSource(it, if (season == null) 1 else 2, season, episode, callback, token)
        }
    }


    suspend fun invokePlayer4U(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title?.createPlayerSlug().orEmpty()
        val fixQuery = (season?.let { "$fixTitle S${"%02d".format(it)}E${"%02d".format(episode)}" }
            ?: "$fixTitle $year").replace(
            " ",
            "+"
        ) // It is necessary for query with year otherwise it will give wrong movie
        val allLinks = HashSet<Player4uLinkData>()

        var page = 0
        var nextPageExists: Boolean

        do {
            val url = if (page == 0) {
                "$Player4uApi/embed?key=$fixQuery"
            } else {
                "$Player4uApi/embed?key=$fixQuery&page=$page"
            }
            val document = app.get(url, timeout = 20).document
            allLinks.addAll(
                document.select(".playbtnx").map {
                    Player4uLinkData(name = it.text(), url = it.attr("onclick"))
                }
            )

            nextPageExists = document.select("div a").any { it.text().contains("Next", true) }
            page++
        } while (nextPageExists && page <= 10)

        val sizeAllLinks = allLinks.distinctBy { it.name }.size
        allLinks.distinctBy { it.name }.forEach { link ->
            try {

                val splitName = link.name.split("|").reversed()
                val firstPart = splitName.getOrNull(0)?.replace(title.toString(), "")
                    ?.replace(year.toString(), "")?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                val nameFormatted = "Player4U ${
                    if (firstPart.isEmpty()) {
                        ""
                    } else {
                        "{$firstPart}"
                    }
                }"

                val qualityFromName = Regex(
                    """(\d{3,4}p|4K|CAM|HQ|HD|SD|WEBRip|DVDRip|BluRay|HDRip|TVRip|HDTC|PREDVD)""",
                    RegexOption.IGNORE_CASE
                )
                    .find(nameFormatted)?.value?.uppercase() ?: "UNKNOWN"


                val selectedQuality = getPlayer4UQuality(qualityFromName)

                if (selectedQuality >= Qualities.P1080.value || sizeAllLinks < 10) { // For less links
                    val subLink = "go\\('(.*)'\\)".toRegex().find(link.url)?.groups?.get(1)?.value
                        ?: return@forEach
                    val iframeSource =
                        app.get("$Player4uApi$subLink", timeout = 10, referer = Player4uApi)
                            .document.select("iframe").attr("src")

                    getPlayer4uUrl(
                        nameFormatted,
                        selectedQuality,
                        "https://uqloads.xyz/e/$iframeSource",
                        Player4uApi,
                        callback
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    suspend fun invokeStreamPlay(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url =
            if (season == null) "${BuildConfig.StreamPlayAPI}/$tmdbId" else "${BuildConfig.StreamPlayAPI}/$tmdbId/seasons/$season/episodes/$episode"
        app.get(url).parsedSafe<StremplayAPI>()?.fields?.links?.arrayValue?.values?.amap {
            val source = it.mapValue.fields.source.stringValue
            val href = it.mapValue.fields.href.stringValue
            val quality = it.mapValue.fields.quality.stringValue
            loadCustomExtractor(
                "StreamPlay $source",
                href,
                "",
                subtitleCallback,
                callback,
                getQualityFromName(quality)
            )
        }
    }


}




