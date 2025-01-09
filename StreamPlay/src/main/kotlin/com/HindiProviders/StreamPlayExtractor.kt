package com.Phisher98

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.nicehttp.RequestBodyTypes
import kotlinx.coroutines.delay
import okhttp3.Interceptor
import java.time.Instant
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.*
import org.jsoup.select.Elements
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.mozilla.javascript.Scriptable
import com.lagradost.cloudstream3.extractors.VidSrcTo
import com.lagradost.cloudstream3.extractors.helper.GogoHelper
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.util.Locale

val session = Session(Requests().baseClient)

object StreamPlayExtractor : StreamPlay() {
    /*
    suspend fun invokeGoku(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        lastSeason: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")

        fun Document.getServers(): List<Pair<String, String>> {
            return this.select("a").map { it.attr("data-id") to it.text() }
        }

        val media =
            app.get(
                "$gokuAPI/ajax/movie/search?keyword=$title",
                headers = headers
            ).document.select(
                "div.item"
            ).find { ele ->
                val url = ele.selectFirst("a.movie-link")?.attr("href")
                val titleMedia = ele.select("h3.movie-name").text()
                val titleSlug = title.createSlug()
                val yearMedia =
                    ele.select("div.info-split > div:first-child").text().toIntOrNull()
                val lastSeasonMedia =
                    ele.select("div.info-split > div:nth-child(2)").text()
                        .substringAfter("SS")
                        .substringBefore("/").trim().toIntOrNull()
                (titleMedia.equals(title, true) || titleMedia.createSlug()
                    .equals(titleSlug) || url?.contains("$titleSlug-") == true) && (if (season == null) {
                    yearMedia == year && url?.contains("/movie/") == true
                } else {
                    lastSeasonMedia == lastSeason && url?.contains("/series/") == true
                })
            } ?: return
        Log.d("Phisher goku serversId", media.toString())
        val serversId = if (season == null) {
            val movieId = app.get(
                fixUrl(
                    media.selectFirst("a")?.attr("href")
                        ?: return, gokuAPI
                )
            ).url.substringAfterLast("/")
            app.get(
                "$gokuAPI/ajax/movie/episode/servers/$movieId",
                headers = headers
            ).document.getServers()
        } else {
            val seasonId = app.get(
                "$gokuAPI/ajax/movie/seasons/${
                    media.selectFirst("a.btn-wl")?.attr("data-id") ?: return
                }", headers = headers
            ).document.select("a.ss-item")
                .find { it.ownText().equals("Season $season", true) }
                ?.attr("data-id")
            val episodeId = app.get(
                "$gokuAPI/ajax/movie/season/episodes/${seasonId ?: return}",
                headers = headers
            ).document.select("div.item").find {
                it.selectFirst("strong")?.text().equals("Eps $episode:", true)
            }?.selectFirst("a")?.attr("data-id")

            app.get(
                "$gokuAPI/ajax/movie/episode/servers/${episodeId ?: return}",
                headers = headers
            ).document.getServers()
        }
        serversId.apmap { (id, name) ->
            val iframe =
                app.get(
                    "$gokuAPI/ajax/movie/episode/server/sources/$id",
                    headers = headers
                )
                    .parsedSafe<GokuServer>()?.data?.link
                    ?: return@apmap
            Log.d("Phisher goku",iframe)
            loadExtractor(iframe,subtitleCallback, callback)
        }
    }
*/
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
        Log.d("Phisher", script.toString())
        if (script!=null) {
            val firstJS =
                """
        var globalArgument = null;
        function Playerjs(arg) {
        globalArgument = arg;
        };
        """.trimIndent()
            val rhino = org.mozilla.javascript.Context.enter()
            rhino.optimizationLevel = -1
            val scope: Scriptable = rhino.initSafeStandardObjects()
            rhino.evaluateString(scope, firstJS + script, "JavaScript", 1, null)
            val file =
                (scope.get("globalArgument", scope).toJson()).substringAfter("file\":\"")
                    .substringBefore("\",")
            callback.invoke(
                ExtractorLink(
                    source = "MultiEmbeded API",
                    name = "MultiEmbeded API",
                    url = file,
                    referer = "",
                    quality = Qualities.P1080.value,
                    type = INFER_TYPE
                )
            )

        }
    }

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
        val host=AsianhdAPI.substringAfter("//")
        val AsianHDepAPI = "https://api.$host/episodes/detail/"
        val apisuburl = url.substringAfter("watch/")
        val json = app.get(AsianHDepAPI + apisuburl).parsedSafe<AsianHDResponse>()
        json?.data?.links?.forEach { link ->
            if (link.url.contains("asianbxkiun"))
            {
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
            } else if (link.url.contains("bulbasaur.online"))
            {
                val href=app.get(link.url).document.selectFirst("iframe")?.attr("src") ?:""
                loadExtractor(href, subtitleCallback, callback)
            } else
            {
                loadExtractor(link.url, subtitleCallback, callback)
            }
        }
    }

    /*
    suspend fun invokeWatchasian(
        title: String? = null,
        year: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val servers = mutableListOf<String>()
        val fixTitle = title.createSlug()
        val url = "$WatchasinAPI/$fixTitle-$year-episode-$episode.html"
        val doc = app.get(url).document
        doc.select("div.anime_muti_link li").forEach {
            val link = it.attr("data-video")
            if (link.contains("pladrac")) {
                servers.add("")
            } else {
                servers.add(link)
            }
        }
        servers.forEach {
            if (it.isNotEmpty()) {
                loadExtractor(it, subtitleCallback, callback)
            }
        }
    }

    suspend fun invokekissasian(
        title: String? = null,
        year: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val doc = Jsoup.parse(
            AppUtils.parseJson<KissasianAPIResponse>(
                app.get(
                    "$KissasianAPI/ajax/v2/episode/servers?episodeId=$fixTitle-$episode"
                ).text
            ).html
        )
        Log.d("Phisher", fixTitle.toString())
        doc.select("div.ps__-list > div.item.server-item").forEach {
            val serverid = it.attr("data-id")
            val apiRes =
                app.get("$KissasianAPI/ajax/v2/episode/sources?id=$serverid")
                    .parsedSafe<KissasianAPISourceresponse>()
            val fstream = apiRes?.link.toString()
            Log.d("Phisher", serverid.toString())
            Log.d("Phisher", apiRes.toString())
            Log.d("Phisher", fstream.toString())
            val response = app.get(
                fstream,
                referer = KissasianAPI,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                interceptor = WebViewResolver(Regex("""ajax/getSources"""))
            )
            val test = response.text.replace("\": ", "\":")
            val regex = """"file":"(.*?)""""
            val matchResult = regex.toRegex().find(test)
            val fileUrl = matchResult?.groups?.get(1)?.value
            if (fileUrl != null) {
                if (fileUrl.contains("mp4")) {
                    callback.invoke(
                        ExtractorLink(
                            source = "Kissasian MP4",
                            name = "Kissasian MP4",
                            url = fileUrl,
                            referer = "$KissasianAPI/",
                            quality = Qualities.Unknown.value,
                            isM3u8 = false
                        )
                    )
                } else {
                    callback.invoke(
                        ExtractorLink(
                            source = "Kissasian",
                            name = "Kissasian",
                            url = fileUrl,
                            referer = "$KissasianAPI/",
                            quality = Qualities.Unknown.value,
                            isM3u8 = true
                        )
                    )
                }
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
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$apiUrl/movies/$fixTitle"
        } else {
            "$apiUrl/episodes/$fixTitle-${season}x${episode}"
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
                    url = "$apiUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post" to id,
                        "nume" to nume,
                        "type" to type
                    ),
                    referer = url,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).parsed<ResponseHash>().embed_url
                Log.d("Phisher",source)
                val link = source.substringAfter("\"").substringBefore("\"")
                when {
                    !link.contains("youtube") -> {
                        loadExtractor(link, referer = apiUrl, subtitleCallback, callback)
                    }
                    else -> Log.d("Error","Not Found")
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
            if (episode!=null)
            {
                app.get(
                    "$aoneroomAPI/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=$season&ep=$episode",
                    headers = headers
                ).parsedSafe<Aoneroomep>()?.data?.streams?.map {
                    val res= it.resolutions.toInt()
                    callback.invoke(
                        ExtractorLink(
                            "Aoneroom",
                            "Aoneroom",
                            it.url,
                            "",
                            res,
                            INFER_TYPE
                        )
                    )
                }
            }
            callback.invoke(
                ExtractorLink(
                    "Aoneroom",
                    "Aoneroom",
                    data?.resourceLink
                        ?: return,
                    "",
                    data.resolution ?: Qualities.Unknown.value,
                    INFER_TYPE
                )
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
        doc.select("div.form-group.list-server option").apmap {
            val server = app.get(
                "$host/ajax-get-link-stream/?server=${it.attr("value")}&filmId=$id",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).text
            loadExtractor(server, "$host/", subtitleCallback) { link ->
                if (link.quality == Qualities.Unknown.value) {
                    callback.invoke(
                        ExtractorLink(
                            "WatchCartoon",
                            "WatchCartoon",
                            link.url,
                            link.referer,
                            Qualities.P720.value,
                            link.type,
                            link.headers,
                            link.extractorData
                        )
                    )
                }
            }
        }
    }

    suspend fun invokeNetmovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$netmoviesAPI/movies/$fixTitle-$year"
        } else {
            "$netmoviesAPI/episodes/$fixTitle-${season}x${episode}"
        }
        invokeWpmovies(null, url, subtitleCallback, callback)
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
        }.apmap { (id, nume, type) ->
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
                            tryParseJson<ZShowEmbed>(it.embed_url)?.meta ?: return@apmap
                        val key = generateWpKey(it.key ?: return@apmap, meta)
                        cryptoAESHandler(
                            it.embed_url,
                            key.toByteArray(),
                            false
                        )?.fixBloat()
                    }

                    fixIframe -> Jsoup.parse(it.embed_url).select("IFRAME").attr("SRC")
                    else -> it.embed_url
                }
            } ?: return@apmap
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

    suspend fun invokeDoomovies(
        title: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get("$doomoviesAPI/movies/${title.createSlug()}/")
        val host = getBaseUrl(res.url)
        val document = res.document
        document.select("ul#playeroptionsul > li")
            .filter { element ->
                element.select("span.flag img").attr("src").contains("/en.")
            }
            .map {
                Triple(
                    it.attr("data-post"),
                    it.attr("data-nume"),
                    it.attr("data-type")
                )
            }.apmap { (id, nume, type) ->
                val source = app.get(
                    "$host/wp-json/dooplayer/v2/${id}/${type}/${nume}",
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    referer = "$host/"
                ).parsed<ResponseHash>().embed_url
                if (!source.contains("youtube")) {
                    loadCustomExtractor("DooMovies", source, "", subtitleCallback, callback)
                }
            }
    }

    suspend fun invokeNoverse(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$noverseAPI/movie/$fixTitle/download/"
        } else {
            "$noverseAPI/serie/$fixTitle/season-$season"
        }

        val doc = app.get(url).document

        val links = if (season == null) {
            doc.select("table.table-striped tbody tr").map {
                it.select("a").attr("href") to it.selectFirst("td")?.text()
            }
        } else {
            doc.select("table.table-striped tbody tr")
                .find { it.text().contains("Episode $episode") }?.select("td")?.map {
                    it.select("a").attr("href") to it.select("a").text()
                }
        } ?: return

        delay(4000)
        links.map { (link, quality) ->
            val name =
                quality?.replace(Regex("\\d{3,4}p"), "Noverse")?.replace(".", " ")
                    ?: "Noverse"
            callback.invoke(
                ExtractorLink(
                    "Noverse",
                    name,
                    link,
                    "",
                    getQualityFromName("${quality?.substringBefore("p")?.trim()}p"),
                )
            )
        }

    }

    suspend fun invokeFilmxy(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "${filmxyAPI}/movie/$imdbId"
        } else {
            "${filmxyAPI}/tv/$imdbId"
        }
        val filmxyCookies = getFilmxyCookies(url)
        val doc = app.get(url, cookies = filmxyCookies).document
        val script =
            doc.selectFirst("script:containsData(var isSingle)")?.data() ?: return

        val sourcesData =
            Regex("listSE\\s*=\\s?(.*?),[\\n|\\s]").find(script)?.groupValues?.get(1)
                .let {
                    tryParseJson<HashMap<String, HashMap<String, List<String>>>>(it)
                }
        val sourcesDetail =
            Regex("linkDetails\\s*=\\s?(.*?),[\\n|\\s]").find(script)?.groupValues?.get(
                1
            ).let {
                tryParseJson<HashMap<String, HashMap<String, String>>>(it)
            }
        val subSourcesData =
            Regex("dSubtitles\\s*=\\s?(.*?),[\\n|\\s]").find(script)?.groupValues?.get(1)
                .let {
                    tryParseJson<HashMap<String, HashMap<String, HashMap<String, String>>>>(
                        it
                    )
                }

        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)

        val sources = if (season == null) {
            sourcesData?.get("movie")?.get("movie")
        } else {
            sourcesData?.get("s$seasonSlug")?.get("e$episodeSlug")
        } ?: return
        val subSources = if (season == null) {
            subSourcesData?.get("movie")?.get("movie")
        } else {
            subSourcesData?.get("s$seasonSlug")?.get("e$episodeSlug")
        }

        val scriptUser =
            doc.select("script").find { it.data().contains("var userNonce") }?.data()
                ?: return
        val userNonce =
            Regex("var\\suserNonce.*?[\"|'](\\S+?)[\"|'];").find(scriptUser)?.groupValues?.get(
                1
            )
        val userId =
            Regex("var\\suser_id.*?[\"|'](\\S+?)[\"|'];").find(scriptUser)?.groupValues?.get(
                1
            )

        val listSources = sources.withIndex().groupBy { it.index / 2 }
            .map { entry -> entry.value.map { it.value } }

        listSources.apmap { src ->
            val linkIDs = src.joinToString("") {
                "&linkIDs%5B%5D=$it"
            }.replace("\"", "")
            val json = app.post(
                "$filmxyAPI/wp-admin/admin-ajax.php",
                requestBody = "action=get_vid_links$linkIDs&user_id=$userId&nonce=$userNonce".toRequestBody(),
                referer = url,
                headers = mapOf(
                    "Accept" to "*/*",
                    "DNT" to "1",
                    "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                    "Origin" to filmxyAPI,
                    "X-Requested-With" to "XMLHttpRequest",
                ),
                cookies = filmxyCookies
            ).text.let { tryParseJson<HashMap<String, String>>(it) }

            src.map { source ->
                val link = json?.get(source)
                val quality = sourcesDetail?.get(source)?.get("resolution")
                val server = sourcesDetail?.get(source)?.get("server")
                val size = sourcesDetail?.get(source)?.get("size")

                callback.invoke(
                    ExtractorLink(
                        "Filmxy", "Filmxy $server [$size]", link
                            ?: return@map, "$filmxyAPI/", getQualityFromName(quality)
                    )
                )
            }
        }

        subSources?.mapKeys { sub ->
            subtitleCallback.invoke(
                SubtitleFile(
                    SubtitleHelper.fromTwoLettersToLanguage(sub.key)
                        ?: return@mapKeys,
                    "https://www.mysubs.org/get-subtitle/${sub.value}"
                )
            )
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

        servers.apmap {
            val server =
                if (it.third.startsWith("https://ouo")) bypassOuo(it.third) else it.third
            loadCustomTagExtractor(
                it.second,
                server
                    ?: return@apmap,
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
        //Log.d("Test iSelector",iframe.toString())
        val servers =
            app.get(
                fixUrl(
                    iframe,
                    kimcartoonAPI
                )
            ).document.select("#selectServer > option")
                .map { fixUrl(it.attr("value"), kimcartoonAPI) }
        //Log.d("Test iSelector",servers.toString())
        servers.apmap {
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
            loadCustomExtractor(
                "MovieHub",
                link,
                referer = "",
                subtitleCallback,
                callback
            )
        }
    }

    suspend fun invokeVidsrcto(
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$vidsrctoAPI/embed/movie/$imdbId"
        } else {
            "$vidsrctoAPI/embed/tv/$imdbId/$season/$episode"
        }
        VidSrcTo().getUrl(url, url, subtitleCallback, callback)

    }


    suspend fun invokeAnitaku(
        url: String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
            val subDub = if (url!!.contains("-dub")) "Dub" else "Sub"
            val epUrl = url.replace("category/", "").plus("-episode-${episode}")
            val epRes = app.get(epUrl).document
        epRes.select("div.anime_muti_link > ul > li").forEach {
                val sourcename = it.selectFirst("a")?.ownText() ?: return@forEach
                val iframe = it.selectFirst("a")?.attr("data-video") ?: return@forEach
                if(iframe.contains("s3taku"))
                {
                    val iv = "3134003223491201"
                    val secretKey = "37911490979715163134003223491201"
                    val secretDecryptKey = "54674138327930866480207815084989"
                    GogoHelper.extractVidstream(
                        iframe,
                        "Anitaku Vidstreaming [$subDub]",
                        callback,
                        iv,
                        secretKey,
                        secretDecryptKey,
                        isUsingAdaptiveKeys = false,
                        isUsingAdaptiveData = true
                    )
                }
                else
                loadCustomExtractor(
                    "Anitaku $sourcename [$subDub]",
                    iframe,
                    "",
                    subtitleCallback,
                    callback
                )
            }
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
        val res=tryParseJson<ArrayList<KisskhResults>>(response)
        Log.d("Phisher", res.toString())
        if (res!=null) {
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
            app.get(
                "$kissKhAPI/api/DramaList/Episode/$epsId.png?err=false&ts=&time=",
                referer = "$kissKhAPI/Drama/${getKisskhTitle(contentTitle)}/Episode-${episode ?: 0}?id=$id&ep=$epsId&page=0&pageSize=100"
            ).parsedSafe<KisskhSources>()?.let { source ->
                listOf(source.video, source.thirdParty).apmap { link ->
                    Log.d("Phisher", link.toString())
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
                                ?: return@apmap null,
                            "$kissKhAPI/",
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }

            app.get("$kissKhAPI/api/Sub/$epsId").text.let { resSub ->
                tryParseJson<List<KisskhSubtitle>>(resSub)?.map { sub ->
                    subtitleCallback.invoke(
                        SubtitleFile(
                            getLanguage(sub.label ?: return@map), sub.src
                                ?: return@map
                        )
                    )
                }
            }
        }
    }

    suspend fun invokeAnimes(
        title: String? = null,
        jptitle:String? =null,
        epsTitle: String? = null,
        date: String?,
        airedDate: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val (aniId, malId) = convertTmdbToAnimeId(
            title,
            date,
            airedDate,
            if (season == null) TvType.AnimeMovie else TvType.Anime
        )
        val Season=app.get("$jikanAPI/anime/${malId ?: return}").parsedSafe<JikanResponse>()?.data?.season ?:""
        val malsync = app.get("$malsyncAPI/mal/anime/${malId ?: return}")
            .parsedSafe<MALSyncResponses>()?.sites
        val zoroIds = malsync?.zoro?.keys?.map { it }
        val TMDBdate=date?.substringBefore("-")
        val zorotitle = malsync?.zoro?.firstNotNullOf { it.value["title"] }?.replace(":"," ")
        val hianimeurl=malsync?.zoro?.firstNotNullOf { it.value["url"] }
        Log.d("Phisher zoroIds", malsync.toString())
        argamap(
            {
                //invokeAnimetosho(malId, season, episode, subtitleCallback, callback)
            },
            {
                //invokeHianime(zoroIds,hianimeurl, episode, subtitleCallback, callback)
            },
            {
                //val animepahetitle = malsync?.animepahe?.firstNotNullOf { it.value["title"] }
                //if (animepahetitle!=null)
                //invokeMiruroanimeGogo(zoroIds,animepahetitle, episode, subtitleCallback, callback)
            },
            {
                //invokeAniwave(aniwaveId, episode, subtitleCallback, callback)
            },
            {
                val animepahe = malsync?.animepahe?.firstNotNullOfOrNull { it.value["url"] }
                if (animepahe!=null)
                invokeAnimepahe(animepahe, episode, subtitleCallback, callback)
            },
            {
                val aniid=malsync?.Gogoanime?.firstNotNullOf { it.value["aniId"] }
                val jptitleslug=jptitle.createSlug()
               // invokeGojo(aniid,jptitleslug, episode, subtitleCallback, callback)
            },
            {
                //invokeAnichi(zorotitle,Season,TMDBdate, episode, subtitleCallback, callback)
            },
            {
                val Gogourl = malsync?.Gogoanime?.firstNotNullOfOrNull { it.value["url"] }
                //if (Gogourl != null)
                //invokeAnitaku(Gogourl, episode, subtitleCallback, callback)
            }
        )
    }

    private suspend fun invokeAnichi(
        name: String? = null,
        Season: String? = null,
        year:String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val api=BuildConfig.ANICHI_API
        var season = Season?.replaceFirstChar { it.uppercase() }
        val privatereferer="https://allmanga.to"
        val ephash="5f1a64b73793cc2234a389cf3a8f93ad82de7043017dd551f38f65b89daa65e0"
        val queryhash="06327bc10dd682e1ee7e07b6db9c16e9ad2fd56c1b769e47513128cd5c9fc77a"
        var type = ""
         if (episode==null) {
                type = "Movie"
                season = ""
            }
         else
            {
                type = "TV"
            }
            val query="""$api?variables={"search":{"types":["$type"],"year":$year,"season":"$season","query":"$name"},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$queryhash"}}"""
            val response= app.get(query, referer = privatereferer).parsedSafe<Anichi>()?.data?.shows?.edges
        if (response!=null) {
            val id = response.firstOrNull {
                it.name.contains("$name", ignoreCase = true) || it.englishName.contains("$name", ignoreCase = true)
            }?.id
            val langType = listOf("sub", "dub")
            for (i in langType) {
                val epData =
                    """$api?variables={"showId":"$id","translationType":"$i","episodeString":"$episode"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$ephash"}}"""
                val eplinks = app.get(epData, referer = privatereferer)
                    .parsedSafe<AnichiEP>()?.data?.episode?.sourceUrls
                eplinks?.apmap { source ->
                    safeApiCall {
                        val sourceUrl=source.sourceUrl
                        val downloadUrl= source.downloads?.downloadUrl ?:""
                        if (downloadUrl.contains("blog.allanime.day")) {
                            if (downloadUrl.isNotEmpty()) {
                                val downloadid=downloadUrl.substringAfter("id=")
                                val sourcename=downloadUrl.getHost()
                                app.get("https://allanime.day/apivtwo/clock.json?id=$downloadid").parsedSafe<AnichiDownload>()?.links?.amap {
                                    val href=it.link
                                    loadNameExtractor(
                                        "Anichi [${i.uppercase()}] [$sourcename]",
                                        href,
                                        "",
                                        subtitleCallback,
                                        callback,
                                        Qualities.P1080.value
                                    )
                                }
                            } else {
                                Log.d("Error:", "Not Found")
                            }
                        } else {
                            if (sourceUrl.startsWith("http")) {
                                if (sourceUrl.contains("embtaku.pro"))
                                {
                                    val iv = "3134003223491201"
                                    val secretKey = "37911490979715163134003223491201"
                                    val secretDecryptKey = "54674138327930866480207815084989"
                                    GogoHelper.extractVidstream(
                                        sourceUrl,
                                        "Anichi [${i.uppercase()}] [Vidstreaming]",
                                        callback,
                                        iv,
                                        secretKey,
                                        secretDecryptKey,
                                        isUsingAdaptiveKeys = false,
                                        isUsingAdaptiveData = true
                                    )
                                }
                                val sourcename=sourceUrl.getHost()
                                loadCustomExtractor(
                                    "Anichi [${i.uppercase()}] [$sourcename]",
                                    sourceUrl
                                        ?: "",
                                    "",
                                    subtitleCallback,
                                    callback,
                                )
                            } else {
                                Log.d("Error:", "Not Found")
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun invokeAnimepahe(
        url: String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf("Cookie" to "__ddg2_=1234567890")
        val id = app.get(url ?: "", headers).document.selectFirst("meta[property=og:url]")
            ?.attr("content").toString().substringAfterLast("/")
        val animeData =
            app.get("$animepaheAPI/api?m=release&id=$id&sort=episode_desc&page=1", headers)
                .parsedSafe<animepahe>()?.data
        val session = animeData?.find { it.episode == episode }?.session ?: ""
        app.get("$animepaheAPI/play/$id/$session", headers).document.select("div.dropup button")
            .map {
                    Log.d("Phisher it",it.toString())
                    var lang=""
                    val dub=it.select("span").text()
                    if (dub.contains("eng")) lang="DUB" else lang="SUB"
                    val quality = it.attr("data-resolution").toIntOrNull()
                    Log.d("Phisher",quality.toString())
                    val href = it.attr("data-src")
                    if (href.contains("kwik.si")) {
                    loadCustomExtractor(
                        "Animepahe [$lang]",
                        href,
                        "$quality",
                        subtitleCallback,
                        callback,
                        quality ?: Qualities.Unknown.value
                    )
                }
            }
    }

    private suspend fun invokeGojo(
        aniid: String? = null,
        jptitle:String?=null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val sourcelist = listOf("shashh", "roro", "vibe")
        for (source in sourcelist)
        {
            Log.d("Phisher gojo",source)
            val headers= mapOf("Origin" to "https://gojo.wtf")
            if (source=="shashh")
            {
                val response = app.get("${BuildConfig.GojoAPI}/api/anime/tiddies?provider=$source&id=$aniid&watchId=$jptitle-episode-$episode", headers = headers)
                    .parsedSafe<Gojoresponseshashh>()?.sources?.map { it.url }
                val m3u8 = response?.firstOrNull() ?: ""
                callback.invoke(
                    ExtractorLink(
                        "GojoAPI [${source.capitalize()}]",
                        "GojoAPI [${source.capitalize()}]",
                        m3u8,
                        "",
                        Qualities.P1080.value,
                        ExtractorLinkType.M3U8,
                        headers = headers
                    )
                )
            }
            else{
                val response = app.get("${BuildConfig.GojoAPI}/api/anime/tiddies?provider=$source&id=$aniid&watchId=$jptitle-episode-$episode", headers = headers)
                    .parsedSafe<Gojoresponsevibe>()?.sources?.map { it.url }
                val m3u8 = response?.firstOrNull() ?: ""
                callback.invoke(
                    ExtractorLink(
                        "GojoAPI [${source.capitalize()}]",
                        "GojoAPI [${source.capitalize()}]",
                        m3u8,
                        "",
                        Qualities.P1080.value,
                        ExtractorLinkType.M3U8,
                        headers = headers
                    )
                )
            }
        }
    }


    private suspend fun invokeAniwave(
        url: String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mainDocument = app.get(url ?: return).document
        val id = mainDocument.select("div#watch-main").attr("data-id")
        val episodeId = fetchEpisodeId(id, episode) ?: return
        val servers = fetchServers(episodeId) ?: return
        servers.forEach { serverElement ->
            val linkId = serverElement.attr("data-link-id")
            val iframe = fetchServerIframe(linkId) ?: return@forEach
            val audio =
                if (serverElement.attr("data-cmid").endsWith("softsub")) "Raw" else "English Dub"
            loadCustomExtractor(
                "Aniwave ${serverElement.text()} [$audio]",
                iframe,
                "$aniwaveAPI/",
                subtitleCallback,
                callback
            )
        }
    }

    private suspend fun fetchEpisodeId(id: String, episode: Int?): String? {
        val response = app.get(
            "$aniwaveAPI/ajax/episode/list/$id?${AniwaveUtils.vrfEncrypt(id)}",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<AniwaveResponse>()?.asJsoup()
        return response?.selectFirst("ul.ep-range li a[data-num=${episode ?: 1}]")?.attr("data-ids")
    }

    private suspend fun fetchServers(episodeId: String): Elements? {
        val response =
            app.get("$aniwaveAPI/ajax/server/list/$episodeId?${AniwaveUtils.vrfEncrypt(episodeId)}")
                .parsedSafe<AniwaveResponse>()?.asJsoup()
        return response?.select("div.servers > div[data-type!=sub] ul li")
    }

    private suspend fun fetchServerIframe(linkId: String): String? {
        val response = app.get("$aniwaveAPI/ajax/server/$linkId?${AniwaveUtils.vrfEncrypt(linkId)}")
            .parsedSafe<AniwaveServer>()?.result?.decrypt()
        return response
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

    private suspend fun invokeHianime(
        animeIds: List<String?>? = null,
        url: String?,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "X-Requested-With" to "XMLHttpRequest",
        )
        animeIds?.apmap { id ->
            val episodeId = app.get(
                "$hianimeAPI/ajax/v2/episode/list/${id ?: return@apmap}",
                headers = headers
            ).parsedSafe<HianimeResponses>()?.html?.let {
                Jsoup.parse(it)
            }?.select("div.ss-list a")
                ?.find { it.attr("data-number") == "${episode ?: 1}" }
                ?.attr("data-id")
            val servers = app.get(
                "$hianimeAPI/ajax/v2/episode/servers?episodeId=${episodeId ?: return@apmap}",
                headers = headers
            ).parsedSafe<HianimeResponses>()?.html?.let { Jsoup.parse(it) }
                ?.select("div.item.server-item")?.map {
                    Triple(
                        it.text(),
                        it.attr("data-id"),
                        it.attr("data-type"),
                    )
                }
            servers?.map servers@{ server ->
                val iframe = app.get(
                    "$hianimeAPI/ajax/v2/episode/sources?id=${server.second ?: return@servers}",
                    headers = headers
                ).parsedSafe<HianimeResponses>()?.link
                    ?: return@servers
                val audio = if (server.third == "sub") "Raw" else "English Dub"
                val animeEpisodeId=url?.substringAfter("to/")
                val api="${BuildConfig.HianimeAPI}/api/v2/hianime/episode/sources?animeEpisodeId=$animeEpisodeId?ep=$episodeId&server=${server.first.lowercase()}&category=${server.third}"
                app.get(api).parsedSafe<Hianime>()?.data?.let { data ->
                    val m3u8Urls = data.sources.map { it.url }
                    val m3u8 = m3u8Urls.firstOrNull()
                    if (m3u8Urls.isNotEmpty()) {
                        loadNameExtractor(
                            "HiAnime ${server.first} [$audio]",
                            m3u8 ?:"",
                            "",
                            subtitleCallback,
                            callback,
                            Qualities.P1080.value
                        )
                    } else {
                        Log.w("Phisher", "No m3u8 URLs found in sources.")
                    }

                    data.tracks.amap { track ->
                        val vtt = track.file
                        val lang=track.label
                        subtitleCallback.invoke(
                            SubtitleFile(
                                getLanguage(lang ?: return@amap),
                                vtt
                            )
                        )
                    }
                }

                /*
                Log.d("Phisher",iframe.toString())
                loadCustomExtractor(
                    "HiAnime ${server.first} [$audio]",
                    iframe,
                    "$hianimeAPI/",
                    subtitleCallback,
                    callback,
                )

                 */
            }
        }
    }

    private suspend fun invokeMiruroanimeGogo(
        animeIds: List<String?>? = null,
        title:String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val api="https://gamma.miruro.tv/?url=https://api.miruro.tv"
        val header= mapOf("x-atx" to "12RmYtJexlqnNym38z4ahwy+g1g0la/El8nkkMOVtiQ=")
        val fixtitle=title.createSlug()
        val sub="$api/meta/anilist/watch/$fixtitle-episode-$episode"
        val dub="$api/meta/anilist/watch/$fixtitle-dub-episode-$episode"
        val list = listOf(sub, dub)
        for(url in list) {
            val json = app.get(url, header).parsedSafe<MiruroanimeGogo>()?.sources
            json?.amap {
                val href = it.url
                var quality = it.quality
                if (quality.contains("backup"))
                {
                    quality="Master"
                }
                val type= if (url.contains("-dub-")) "DUB" else "SUB"
                if (quality!="Master")
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
        callback.invoke(
            ExtractorLink(
                "Ling",
                "Ling",
                "$link/index.m3u8",
                "$lingAPI/",
                Qualities.P720.value,
                INFER_TYPE
            )
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

    @SuppressLint("SuspiciousIndentation")
    suspend fun invokeUhdmovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val fixTitle = title?.replace("-", " ")?.replace(":", " ")
        val searchtitle = fixTitle.createSlug()
        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
        app.get("$uhdmoviesAPI/search/$fixTitle $year").document.select("#content article").map {
            val hrefpattern =
                Regex("""(?i)<a\s+href="([^"]*?\b$searchtitle\b[^"]*?\b$year\b[^"]*?)"[^>]*>""").find(
                    it.toString()
                )?.groupValues?.get(1)
            val detailDoc = hrefpattern?.let { app.get(it).document }
            val iSelector = if (season == null) {
                "div.entry-content p:has(:matches($year))"
            } else {
                "div.entry-content p:has(:matches((?i)(?:S\\s*$seasonSlug|Season\\s*$seasonSlug)))"
            }
            val iframeList = detailDoc!!.select(iSelector).mapNotNull {
                if (season == null) {
                    it.text() to it.nextElementSibling()?.select("a")?.attr("href")
                } else {
                    it.text() to it.nextElementSibling()?.select("a")?.find { child ->
                        child.select("span").text().equals("Episode $episode", true)
                    }?.attr("href")
                }
            }.filter { it.first.contains(Regex("(2160p)|(1080p)")) }
                .filter { element -> !element.toString().contains("Download", true) }
            iframeList.amap { (quality, link) ->
                val driveLink = bypassHrefli(link ?: "") ?: ""
                loadSourceNameExtractor(
                    "UHDMovies",
                    driveLink,
                    "",
                    subtitleCallback,
                    callback,
                    getQualityFromName("")
                )
            }
        }
    }
//Kindly don't copy this as well
    suspend fun invokeSubtitleAPI(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if(season == null) {
            "$SubtitlesAPI/subtitles/movie/$id.json"
        }
        else {
            "$SubtitlesAPI/subtitles/series/$id:$season:$episode.json"
        }
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        )
    Log.d("Phisher", url)
    app.get(url, headers = headers, timeout = 100L).parsedSafe<SubtitlesAPI>()?.subtitles?.amap {
            val lan=it.lang
            val suburl=it.url ?: null
            if (suburl!=null)
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
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if(season == null) {
            "$WyZIESUBAPI/search?id=$id"
        }
        else {
            "$WyZIESUBAPI/search?id=$id/$season/$episode"
        }

        val res=app.get(url).toString()
        val gson = Gson()
        val listType = object : TypeToken<List<WyZIESUB>>() {}.type
        val subtitles: List<WyZIESUB> = gson.fromJson(res, listType)
        subtitles.map {
            val lan=it.display
            val suburl=it.url
            subtitleCallback.invoke(
                SubtitleFile(
                    lan.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },  // Use label for the name
                    suburl     // Use extracted URL
                )
            )
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
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title?.replace("-", " ")?.substringBefore(":")
        var url = ""
        val searchtitle = title?.replace("-", " ")?.substringBefore(":").createSlug()
        if (season == null) {
            url = "$topmoviesAPI/search/$imdbId $year"
        } else {
            url = "$topmoviesAPI/search/$imdbId Season $season $year"
        }
        Log.d("Phisher1 url", url.toString())
        var res1 =
            app.get(url).document.select("#content_box article")
                .toString()
        val hrefpattern =
            Regex("""(?i)<article[^>]*>\s*<a\s+href="([^"]*$searchtitle[^"]*)"""").find(res1)?.groupValues?.get(
                1
            )
        val hTag = if (season == null) "h3" else "div.single_post h3"
        val aTag = if (season == null) "Download" else "G-Drive"
        val sTag = if (season == null) "" else "(Season $season)"
        //val media =res.selectFirst("div.post-cards article:has(h2.title.front-view-title:matches((?i)$title.*$match)) a")?.attr("href")
        Log.d("Phisher", hrefpattern.toString())
        val res = app.get(
            hrefpattern ?: return,
            headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"),
            interceptor = wpRedisInterceptor
        ).document
        val entries = if (season == null) {
            res.select("$hTag:matches((?i)$sTag.*(720p|1080p|2160p|4K))")
                .filter { element -> !element.text().contains("Batch/Zip", true) && !element.text().contains("Info:", true) }.reversed()
        } else {
            res.select("$hTag:matches((?i)$sTag.*(720p|1080p|2160p|4K))")
                .filter { element -> !element.text().contains("Batch/Zip", true) || !element.text().contains("720p & 480p", true) || !element.text().contains("Series Info", true)}
        }
        entries.amap {
            val href =
                it.nextElementSibling()?.select("a.maxbutton:contains($aTag)")?.attr("href")
            val selector =
                if (season == null) "a.maxbutton-5:contains(Server)" else "h3 a:matches(Episode $episode)"
            if (href!!.isNotEmpty()) {
                Log.d("Phisher", href.toString())
                app.get(
                    href ?: "",
                    headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"),
                    interceptor = wpRedisInterceptor, timeout = 10L
                ).document.selectFirst(selector)
                    ?.attr("href")?.let {
                        val link = bypassHrefli(it) ?:""
                        Log.d("Phisher bypass", link)
                        loadExtractor(link, referer = "TopMovies", subtitleCallback, callback)
                    }
            }
        }
    }



    suspend fun invokeMoviesmod(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        lastSeason: Int?,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        invokeModflix(
            title,
            year,
            season,
            lastSeason,
            episode,
            subtitleCallback,
            callback,
            MoviesmodAPI
        )
    }

    suspend fun invokeModflix(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        lastSeason: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        api: String
    ) {
        val fixTitle = title?.replace("-", " ")?.replace(":", " ")?.replace("&", " ")
        var url = ""
        val searchtitle =
            title?.replace("-", " ")?.replace(":", " ")?.replace("&", " ").createSlug()
        if (season == null) {
            url = "$api/search/$fixTitle"
        } else {
            url = "$api/search/$fixTitle $season"
        }
        var res1 =
            app.get(url, interceptor = wpRedisInterceptor).document.select("#content_box article")
                .toString()
        val hrefpattern =
            Regex("""(?i)<article[^>]*>\s*<a\s+href="([^"]*$searchtitle[^"]*)"""").find(res1)?.groupValues?.get(
                1
            )
        val hTag = if (season == null) "h4" else "h3"
        val aTag = if (season == null) "Download" else "Episode"
        val sTag = if (season == null) "" else "(S0$season|Season $season)"
        val res = app.get(
            hrefpattern ?: return,
            headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"),
            interceptor = wpRedisInterceptor
        ).document
        val entries =
            res.select("div.thecontent $hTag:matches((?i)$sTag.*(720p|1080p|2160p))")
                .filter { element ->
                    !element.text().contains("MoviesMod", true) && !element.text()
                        .contains("1080p", true) || !element.text().contains("720p", true)
                }
        entries.amap { it ->
            val tags =
                """(?:720p|1080p|2160p)(.*)""".toRegex().find(it.text())?.groupValues?.get(1)
                    ?.trim()
            val quality =
                """720p|1080p|2160p""".toRegex().find(it.text())?.groupValues?.get(0)
                    ?.trim()
            var href =
                it.nextElementSibling()?.select("a:contains($aTag)")?.attr("href")
                    ?.substringAfter("=") ?: ""
            Log.d("Phisher",href)
            href = base64Decode(href)
            Log.d("Phisher",href)
            val selector =
                if (season == null) "p a.maxbutton" else "h3 a:matches(Episode $episode)"
            if (href.isNotEmpty())
            app.get(
                href,
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
            ).document.selectFirst(selector)?.let {
                val link = it.attr("href")
                if (link.contains("driveseed.org"))
                {
                    val file=app.get(link).toString().substringAfter("replace(\"").substringBefore("\")")
                    val domain= getBaseUrl(link)
                    val server="$domain$file"
                    Log.d("Phisher",link)
                    loadSourceNameExtractor(
                        "Modflix",
                        server,
                        "",
                        subtitleCallback,
                        callback,
                        getQualityFromName("")
                    )
                }
                val server = Unblockedlinks(link) ?: ""
                if (server.isNotEmpty()) {
                    Log.d("Phisher",server)
                    loadSourceNameExtractor(
                        "Modflix",
                        server,
                        "",
                        subtitleCallback,
                        callback,
                        getQualityFromName("")
                    )
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
        callback: (ExtractorLink) -> Unit
    ) {
        invokeWpredis(
            imdbId,
            title,
            year,
            season,
            lastSeason,
            episode,
            subtitleCallback,
            callback,
            dotmoviesAPI
        )
    }

    suspend fun invokeRogmovies(
        imdbId: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        lastSeason: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        invokeWpredis(
            imdbId,
            title,
            year,
            season,
            lastSeason,
            episode,
            subtitleCallback,
            callback,
            rogmoviesAPI
        )
    }

    suspend fun invokeVegamovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
        val cfInterceptor = CloudflareKiller()
        val fixtitle = title?.substringBefore("-")?.substringBefore(":")?.replace("&", " ")
        val url = if (season == null) {
            "$vegaMoviesAPI/?s=$fixtitle $year"
        } else {
            "$vegaMoviesAPI/?s=$fixtitle season $season"
        }
        val domain= vegaMoviesAPI.substringAfter("//").substringBefore(".")
        app.get(url, interceptor = cfInterceptor).document.select("article h2")
            .amap {
                val hrefpattern = Regex("""(?i)<a\s+href="([^"]+)"[^>]*?>[^<]*?\b($title)\b[^<]*?""").find( it.toString() )?.groupValues?.get(1)
                Log.d("Phisher", hrefpattern.toString())
                if (hrefpattern!=null) {
                    val res = hrefpattern.let { app.get(it).document }
                    val hTag = if (season == null) "h5" else "h3,h5"
                    val aTag =
                        if (season == null) "Download Now" else "V-Cloud,Download Now,G-Direct,Episode Links"
                    val sTag = if (season == null) "" else "(Season $season|S$seasonSlug)"
                    val entries =
                        res.select("div.entry-inner > $hTag:matches((?i)$sTag.*(720p|1080p|2160p))")
                            .filter { element ->
                                !element.text().contains("Series Info", true) &&
                                        !element.text().contains("Zip", true) &&
                                        !element.text().contains("[Complete]", true) &&
                                        !element.text().contains("480p, 720p, 1080p", true) &&
                                        !element.text().contains(domain, true) &&
                                        element.text().matches("(?i).*($sTag).*".toRegex())
                            }
                    Log.d("Phisher url entries", entries.toString())
                    entries.amap { it ->
                        val tags =
                            """(?:720p|1080p|2160p)(.*)""".toRegex().find(it.text())?.groupValues?.get(1)
                                ?.trim()
                        val tagList = aTag.split(",")  // Changed variable name to tagList
                        val href = it.nextElementSibling()?.select("a")?.filter { anchor ->
                            tagList.any { tag ->
                                anchor.text().contains(tag.trim(), true)
                            }
                        }?.map { anchor ->
                            anchor.attr("href")
                        } ?: emptyList()
                        val selector =
                            if (season == null) "p a:matches(V-Cloud|G-Direct)" else "h4:matches(0?$episode)"
                        Log.d("Phisher href", it.toString())
                        if (href.isNotEmpty()) {
                            href.amap { url ->
                                if (season==null)
                                {
                                    app.get(
                                        url, interceptor = wpRedisInterceptor
                                    ).document.select("div.entry-inner > $selector").map { sources ->
                                        val server = sources.attr("href")
                                        loadSourceNameExtractor(
                                            "V-Cloud",
                                            server,
                                            "$vegaMoviesAPI/",
                                            subtitleCallback,
                                            callback,
                                            getIndexQuality(sources.text())
                                        )
                                    }
                                }
                                else
                                {
                                    app.get(url, interceptor = wpRedisInterceptor).document.select("div.entry-content > $selector")
                                        .forEach { h4Element ->
                                            Log.d("Phisher href veg", (h4Element ?: "").toString())
                                            var sibling = h4Element.nextElementSibling()
                                            while (sibling != null && sibling.tagName() != "p") {
                                                sibling = sibling.nextElementSibling()
                                            }
                                            while (sibling != null && sibling.tagName() == "p") {
                                                sibling.select("a:matches(V-Cloud|G-Direct)").forEach { sources ->
                                                    val server = sources.attr("href")
                                                    Log.d("Phisher href veg", server ?: "")
                                                    loadSourceNameExtractor(
                                                        "V-Cloud",
                                                        server,
                                                        "$vegaMoviesAPI/",
                                                        subtitleCallback,
                                                        callback,
                                                        getIndexQuality(sources.text())
                                                    )
                                                }
                                                sibling = sibling.nextElementSibling()
                                            }
                                        }
                                }
                            }
                        }
                    }
                }
            }
    }

    private suspend fun invokeWpredis(
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
        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
        val cfInterceptor = CloudflareKiller()
        val fixtitle = title?.substringBefore("-")?.substringBefore(":")?.replace("&", " ")
        val url = if (season == null) {
            "$api/?s=$imdbId"
        } else {
            "$api/?s=$imdbId season $season"
        }
        Log.d("Phisher url veg", "$api/?s=$imdbId")
        val domain= api.substringAfter("//").substringBefore(".")
        app.get(url, interceptor = cfInterceptor).document.select("article h3 a")
            .amap {
                //val hrefpattern = Regex("""(?i)<a\s+href="([^"]+)"[^>]*?>[^<]*?\b($fixtitle)\b[^<]*?""").find( it.toString() )?.groupValues?.get(1)
                val hrefpattern=it.attr("href") ?: null
                Log.d("Phisher", hrefpattern.toString())
                if (hrefpattern!=null) {
                    val res = hrefpattern.let { app.get(it).document }
                    val hTag = if (season == null) "h5" else "h3,h5"
                    val aTag =
                        if (season == null) "Download Now" else "V-Cloud,Download Now,G-Direct,Episode Links"
                    val sTag = if (season == null) "" else "(Season $season|S$seasonSlug)"
                    val entries =
                        res.select("div.entry-content > $hTag:matches((?i)$sTag.*(720p|1080p|2160p))")
                            .filter { element ->
                                !element.text().contains("Series Info", true) &&
                                        !element.text().contains("Zip", true) &&
                                        !element.text().contains("[Complete]", true) &&
                                        !element.text().contains("480p, 720p, 1080p", true) &&
                                        !element.text().contains(domain, true) &&
                                element.text().matches("(?i).*($sTag).*".toRegex())
                            }
                    //Log.d("Phisher url entries", entries.toString())
                    entries.amap { it ->
                        val tags =
                            """(?:720p|1080p|2160p)(.*)""".toRegex().find(it.text())?.groupValues?.get(1)
                                ?.trim()
                        val tagList = aTag.split(",")  // Changed variable name to tagList
                        val href = it.nextElementSibling()?.select("a")?.filter { anchor ->
                            tagList.any { tag ->
                                anchor.text().contains(tag.trim(), true)
                            }
                        }?.map { anchor ->
                            anchor.attr("href")
                        } ?: emptyList()
                        val selector =
                            if (season == null) "p a:matches(V-Cloud|G-Direct)" else "h4:matches(0?$episode)"
                        Log.d("Phisher href", href.toString())
                        if (href.isNotEmpty()) {
                            href.amap { url ->
                            if (season==null)
                            {
                                app.get(
                                    url, interceptor = wpRedisInterceptor
                                ).document.select("div.entry-inner > $selector").map { sources ->
                                    val server = sources.attr("href")
                                    loadSourceNameExtractor(
                                        "V-Cloud",
                                        server,
                                        "$api/",
                                        subtitleCallback,
                                        callback,
                                        getIndexQuality(sources.text())
                                    )
                                }
                            }
                            else
                            {
                                app.get(url, interceptor = wpRedisInterceptor).document.select("div.entry-content > $selector")
                                    .forEach { h4Element ->
                                        Log.d("Phisher href veg", (h4Element ?: "").toString())
                                        var sibling = h4Element.nextElementSibling()
                                        while (sibling != null && sibling.tagName() != "p") {
                                            sibling = sibling.nextElementSibling()
                                        }
                                        while (sibling != null && sibling.tagName() == "p") {
                                            sibling.select("a:matches(V-Cloud|G-Direct)").forEach { sources ->
                                                val server = sources.attr("href")
                                                Log.d("Phisher href veg", server ?: "")
                                                loadSourceNameExtractor(
                                                    "V-Cloud",
                                                    server,
                                                    "$api/",
                                                    subtitleCallback,
                                                    callback,
                                                    getIndexQuality(sources.text())
                                                )
                                            }
                                            sibling = sibling.nextElementSibling()
                                        }
                                    }
                             }
                            }
                        }
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
        val url = if(season == null) "$TomAPI/api/getVideoSource?type=movie&id=$id" else "$TomAPI/api/getVideoSource?type=tv&id=$id/$season/$episode"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
            "Referer" to "https://autoembed.cc"
        )
        val json = app.get(url, headers = headers).text
        Log.d("Phisher", json.toString())
        val data = tryParseJson<TomResponse>(json) ?: return
        Log.d("Phisher", data.toString())
        callback.invoke(
            ExtractorLink(
                "Tom Embeded",
                "Tom Embeded",
                data.videoSource,
                "",
                Qualities.P1080.value,
                true
            )
        )

        data.subtitles.map {
            subtitleCallback.invoke(
                SubtitleFile(
                    it.label,
                    it.file,
                )
            )
        }
    }

    suspend fun invokeExtramovies(
        imdbId: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url="$Extramovies/search/$imdbId"
        app.get(url).document.select("h3 a").amap {
                val link=it.attr("href")
                app.get(link).document.select("div.entry-content a.maxbutton-8").map { it ->
                    val href=it.select("a").attr("href")
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
        tmdbId: Int?=null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val type=if (season==null) "movie" else "tv"
        val servers = listOf("astra", "orion","nova")
        for(source in servers) {
            val token= app.get(BuildConfig.WhvxT).parsedSafe<Vidbinge>()?.token ?:""
            val s= season ?: ""
            val e= episode ?: ""
            val query="""{"title":"$title","imdbId":"$imdbId","tmdbId":"$tmdbId","type":"$type","season":"$s","episode":"$e","releaseYear":"$year"}"""
            val encodedQuery = encodeQuery(query)
            val encodedToken = encodeQuery(token)
            val originalUrl = "$WhvxAPI/search?query=$encodedQuery&provider=$source&token=$encodedToken"
            //Log.d("Phisher", originalUrl)
            val headers = mapOf(
                "accept" to "*/*",
                "origin" to "https://www.vidbinge.com",
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )
            val oneurl= app.get(originalUrl, headers=headers, timeout = 50L).parsedSafe<Vidbingesources>()?.url
            oneurl?.let {
                val encodedit=encodeQuery(it)
                Log.d("Phisher", "$source $encodedit")
                app.get("$WhvxAPI/source?resourceId=$encodedit&provider=$source",headers=headers).parsedSafe<Vidbingeplaylist>()?.stream?.amap {
                    Log.d("Phisher", "$source ${it.playlist}")
                    val playlist=it.playlist
                    callback.invoke(
                        ExtractorLink(
                            "Vidbinge ${source.capitalize()}", "Vidbinge ${source.capitalize()}", playlist
                            , "", Qualities.P1080.value, INFER_TYPE
                        )
                    )
                }
            }
        }
    }

    suspend fun invokeBroflixVidlink(
        tmdbId: Int?=null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = "$BroflixVidlink/embed/movie/$tmdbId"
        app.get(url).document.select("div.menu a").amap {
            val encoded=it.attr("data-url").substringAfter("url=")
            val href=base64Decode(encoded)
            loadSourceNameExtractor("Broflix Vidlink",href,"",subtitleCallback,callback,
                getQualityFromName("")
            )
        }
    }

    suspend fun invokeSharmaflix(
        title: String? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val searchtitle=encodeQuery(title ?:"").replace("+", "%20")
        val url="$Sharmaflix/searchContent/$searchtitle/0"
        val headers= mapOf("x-api-key" to BuildConfig.SharmaflixApikey,"User-Agent" to "Dalvik/2.1.0 (Linux; U; Android 13; Subsystem for Android(TM) Build/TQ3A.230901.001)")
        val json= app.get(url, headers = headers).toString().toJson()
        val objectMapper = jacksonObjectMapper()
        val parsedResponse: SharmaFlixRoot = objectMapper.readValue(json)
        parsedResponse.forEach { root ->
            val id=root.id
            val movieurl="$Sharmaflix/getMoviePlayLinks/$id/0"
            val hrefresponse= app.get(movieurl, headers = headers).toString().toJson()
            val hrefparser: SharmaFlixLinks = objectMapper.readValue(hrefresponse)
            hrefparser.forEach {
                callback.invoke(
                    ExtractorLink(
                        "Sharmaflix", "Sharmaflix", it.url
                            ?: return, "", Qualities.P1080.value, INFER_TYPE
                    )
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
    val url = if (season == null) {
            "$EmbedSu/embed/movie/$id"
        } else {
            "$EmbedSu/embed/tv/$id/$season/$episode"
        }
        Log.d("Phisher", url.toString())
        val res = app.get(url, referer = EmbedSu).document.selectFirst("script:containsData(window.vConfig)")?.data()
            .toString()
        val jsonencoded =
            Regex("JSON\\.parse\\(atob\\(`(.*?)`\\)\\);").find(res)?.groupValues?.getOrNull(1) ?: ""
        val decodedjson = base64Decode(jsonencoded).toJson()
        val gson = Gson()
        val json = gson.fromJson(decodedjson, Embedsu::class.java)
        val hash = json.hash
        val decodedResult = simpleDecodeProcess(hash) ?:""
        val items = EmbedSuitemparseJson(decodedResult)
        items.map {
            val sourceurl="${EmbedSu}/api/e/${it.hash}"
            app.get(sourceurl,referer = EmbedSu).parsedSafe<Embedsuhref>()?.let { href->
                Log.d("Phisher", href.toString())
                val m3u8=href.source
                val headers = mapOf(
                    "Origin" to "https://embed.su",
                    "Referer" to "https://embed.su/",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                callback.invoke(
                    ExtractorLink(
                        "Embedsu Viper",
                        "Embedsu Viper",
                        m3u8,
                        EmbedSu,
                        Qualities.P1080.value,
                        ExtractorLinkType.M3U8,
                        headers = headers
                    )
                )
            }
        }
    }

    suspend fun invokeTheyallsayflix(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val type=if (season==null) "movie" else "show"
        val url = if (season == null) {
            "$Theyallsayflix/api/v1/search?type=$type&imdb_id=$id"
        } else {
            "$Theyallsayflix/api/v1/search?type=$type&imdb_id=$id&season=$season&episode=$episode"
        }
        app.get(url).parsedSafe<Theyallsayflix>()?.streams?.amap {
            Log.d("Phisher",it.toString() )
            val href=it.playUrl
            val quality=it.quality.toInt()
            val name=it.fileName
            val size=it.fileSize
            callback.invoke(
                ExtractorLink(
                    "DebianFlix $name $size", "DebianFlix $name $size", href, "", quality, INFER_TYPE
                )
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
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$vidsrctoAPI/v2/embed/movie/$id"
        } else {
            "$vidsrctoAPI/v2/embed/tv/$id/$season/$episode"
        }
        val type=if (season==null) "movie" else "tv"
        Log.d("Phisher",url)
        val doc = app.get(url).document.toString()
        //val new_data_id= Regex("data-id=\"(.*?)\".*data-number=").find(doc)?.groupValues?.get(1).toString()
        val v_value= Regex("var.v.=.\"(.*?)\"").find(doc)?.groupValues?.get(1).toString()
        val vrfres= app.get("${BuildConfig.Vidsrccc}/vrf/$id").parsedSafe<Vidsrccc>()
        val vrf= vrfres?.vrf
        val timetamp= vrfres?.timestamp
        val instant = Instant.parse(timetamp)
        val unixTimeMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            instant.toEpochMilli()
        } else {
            TODO("VERSION.SDK_INT < O")
        }
    val api_url=if (season==null)
        {
            "${vidsrctoAPI}/api/$id/servers?id=${id}&type=$type&v=$v_value&vrf=${vrf}"
        }
        else
        {
            "${vidsrctoAPI}/api/$id/servers?id=${id}&type=$type&season=$season&episode=$episode&v=$v_value&vrf=${vrf}"
        }
    app.get(api_url).parsedSafe<Vidsrcccservers>()?.data?.forEach {
            val servername=it.name
            val m3u8= app.get("$vidsrctoAPI/api/source/${it.hash}?t=$unixTimeMs").parsedSafe<Vidsrcccm3u8>()?.data?.source
            callback.invoke(
                ExtractorLink(
                "Vidsrc [$servername]", "Vidsrc [$servername]", m3u8
                    ?: return, "https://vidsrc.stream/", Qualities.P1080.value, INFER_TYPE
                )
            )
    }

}
/*
    suspend fun invokeHdmovies4u(
        title: String? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        fun String.decodeLink(): String {
            return base64Decode(this.substringAfterLast("/"))
        }
        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
        val media =
            app.get("$hdmovies4uAPI/?s=${if (season == null) imdbId else title}").document.let {
                val selector =
                    if (season == null) "a" else "a:matches((?i)$title.*Season $season)"
                it.selectFirst("div.gridxw.gridxe $selector")?.attr("href")
            }
        val selector =
            if (season == null) "1080p|2160p" else "(?i)Episode.*(?:1080p|2160p)"
        app.get(media ?: return).document.select("section h4:matches($selector)")
            .apmap { ele ->
                val (tags, size) = ele.select("span").map {
                    it.text()
                }.let { it[it.lastIndex - 1] to it.last().substringAfter("-").trim() }
                val link = ele.nextElementSibling()?.select("a:contains(DriveTOT)")
                    ?.attr("href")
                val iframe = bypassBqrecipes(link?.decodeLink() ?: return@apmap).let {
                    Log.d("Phisher HD", it.toString())
                    if (it?.contains("/pack/") == true) {
                        val href =
                            app.get(it).document.select("table tbody tr:contains(S${seasonSlug}E${episodeSlug}) a")
                                .attr("href")
                        bypassBqrecipes(href.decodeLink())
                    } else {
                        it
                    }
                }
                invokeDrivetot(
                    iframe ?: return@apmap,
                    tags,
                    size,
                    subtitleCallback,
                    callback
                )
            }
    }
 */
    suspend fun invokeFDMovies(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$fdMoviesAPI/movies/$fixTitle"
        } else {
            "$fdMoviesAPI/episodes/$fixTitle-s${season}xe${episode}/"
        }

        val request = app.get(url)
        if (!request.isSuccessful) return

        val iframe = request.document.select("div#download tbody tr").map {
            FDMovieIFrame(
                it.select("a").attr("href"),
                it.select("strong.quality").text(),
                it.select("td:nth-child(4)").text(),
                it.select("img").attr("src")
            )
        }.filter {
            it.quality.contains(Regex("(?i)(1080p|4k)")) && it.type.contains(Regex("(gdtot|oiya|rarbgx)"))
        }
        iframe.apmap { (link, quality, size, type) ->
            val qualities = getFDoviesQuality(quality)
            val fdLink = bypassFdAds(link)
            val videoLink = when {
                type.contains("gdtot") -> {
                    val gdBotLink = extractGdbot(fdLink ?: return@apmap null)
                    extractGdflix(gdBotLink ?: return@apmap null)
                }

                type.contains("oiya") || type.contains("rarbgx") -> {
                    val oiyaLink = extractOiya(fdLink ?: return@apmap null)
                    if (oiyaLink?.contains("gdtot") == true) {
                        val gdBotLink = extractGdbot(oiyaLink)
                        extractGdflix(gdBotLink ?: return@apmap null)
                    } else {
                        oiyaLink
                    }
                }

                else -> {
                    return@apmap null
                }
            }

            callback.invoke(
                ExtractorLink(
                    "FDMovies", "FDMovies [$size]", videoLink
                        ?: return@apmap null, "", getQualityFromName(qualities)
                )
            )
        }

    }

    suspend fun invokeFlicky(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$FlickyAPI/Server-main.php?id=$id"
        } else {
            "$FlickyAPI/Server-main.php?id=$id&season=${season}&episode=${episode}"
        }
        val res= app.get(url, referer = FlickyAPI, timeout = 50000).toString().substringAfter("const streams = ").substringBefore(";")
        val gson = Gson()
        val listType = object : TypeToken<List<FlickyStream>>() {}.type
        val streams: List<FlickyStream> = gson.fromJson(res, listType)
        streams.map {
            val href=it.url
            val name=it.language
            M3u8Helper.generateM3u8(
                "Flicky $name",
                href,
                "",
            ).forEach(callback)
        }
    }


    suspend fun invokeM4uhd(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val slugTitle = title?.createSlug()
        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
        val req = app.get("$m4uhdAPI/search/$slugTitle", timeout = 20)
        val referer = getBaseUrl(req.url)
        val media = req.document.select("div.row div.item a").map { it.attr("href") }
        val mediaUrl = if (media.size == 1) {
            media.first()
        } else {
            media.find {
                if (season == null) it.startsWith("movies/$slugTitle-$year.") else it.startsWith(
                    "tv-series/$slugTitle-$year."
                )
            }
        }

        val link = fixUrl(mediaUrl ?: return, referer)
        val request = app.get(link, timeout = 20)
        var cookies = request.cookies
        val headers = mapOf("Accept" to "*/*", "X-Requested-With" to "XMLHttpRequest")
        val doc = request.document
        val token = doc.selectFirst("meta[name=csrf-token]")?.attr("content")
        val m4uData = if (season == null) {
            doc.select("div.le-server span").map { it.attr("data") }
        } else {
            val idepisode =
                doc.selectFirst("button[class=episode]:matches(S$seasonSlug-E$episodeSlug)")
                    ?.attr("idepisode")
                    ?: return
            val requestEmbed = app.post(
                "$referer/ajaxtv", data = mapOf(
                    "idepisode" to idepisode, "_token" to "$token"
                ), referer = link, headers = headers, cookies = cookies, timeout = 20
            )
            cookies = requestEmbed.cookies
            requestEmbed.document.select("div.le-server span").map { it.attr("data") }
        }
        m4uData.apmap { data ->
            val iframe = app.post(
                "$referer/ajax",
                data = mapOf("m4u" to data, "_token" to "$token"),
                referer = link,
                headers = headers,
                cookies = cookies,
                timeout = 20
            ).document.select("iframe").attr("src")
            loadExtractor(iframe, referer, subtitleCallback, callback)
        }

    }

    suspend fun invokeTvMovies(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$tvMoviesAPI/show/$fixTitle"
        } else {
            "$tvMoviesAPI/show/index-of-$fixTitle"
        }

        val server = getTvMoviesServer(url, season, episode) ?: return
        val videoData = extractCovyn(server.second ?: return)
        val quality =
            Regex("(\\d{3,4})p").find(server.first)?.groupValues?.getOrNull(1)
                ?.toIntOrNull()

        callback.invoke(
            ExtractorLink(
                "TVMovies", "TVMovies [${videoData?.second}]", videoData?.first
                    ?: return, "", quality ?: Qualities.Unknown.value
            )
        )


    }

    private suspend fun invokeCrunchyroll(
        aniId: Int? = null,
        malId: Int? = null,
        epsTitle: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = getCrunchyrollId("${aniId ?: return}")
            ?: getCrunchyrollIdFromMalSync("${malId ?: return}")
        val audioLocal = listOf(
            "ja-JP",
            "en-US",
            "zh-CN",
        )
        val token = getCrunchyrollToken()
        val headers =
            mapOf("Authorization" to "${token.tokenType} ${token.accessToken}")
        val seasonIdData = app.get(
            "$crunchyrollAPI/content/v2/cms/series/${id ?: return}/seasons",
            headers = headers
        ).parsedSafe<CrunchyrollResponses>()?.data?.let { s ->
            if (s.size == 1) {
                s.firstOrNull()
            } else {
                s.find {
                    when (epsTitle) {
                        "One Piece" -> it.season_number == 13
                        "Hunter x Hunter" -> it.season_number == 5
                        else -> it.season_number == season
                    }
                } ?: s.find { it.season_number?.plus(1) == season }
            }
        }
        val seasonId = seasonIdData?.versions?.filter { it.audio_locale in audioLocal }
            ?.map { it.guid to it.audio_locale }
            ?: listOf(seasonIdData?.id to "ja-JP")

        seasonId.apmap { (sId, audioL) ->
            val streamsLink = app.get(
                "$crunchyrollAPI/content/v2/cms/seasons/${sId ?: return@apmap}/episodes",
                headers = headers
            ).parsedSafe<CrunchyrollResponses>()?.data?.find {
                it.title.equals(epsTitle, true) || it.slug_title.equals(
                    epsTitle.createSlug(),
                    true
                ) || it.episode_number == episode
            }?.streams_link?.substringAfter("/videos/")?.substringBefore("/streams")
                ?: return@apmap
            val sources = app.get(
                "$crunchyrollAPI/cms/v2${token.bucket}/videos/$streamsLink/streams?Policy=${token.policy}&Signature=${token.signature}&Key-Pair-Id=${token.key_pair_id}",
                headers = headers
            ).parsedSafe<CrunchyrollSourcesResponses>()

            listOf("adaptive_hls", "vo_adaptive_hls").map { hls ->
                val name = if (hls == "adaptive_hls") "Crunchyroll" else "Vrv"
                val audio = if (audioL == "en-US") "English Dub" else "Raw"
                val source = sources?.streams?.let {
                    if (hls == "adaptive_hls") it.adaptive_hls else it.vo_adaptive_hls
                }
                M3u8Helper.generateM3u8(
                    "$name [$audio]", source?.get("")?.get("url")
                        ?: return@map, "https://static.crunchyroll.com/"
                ).forEach(callback)
            }

            sources?.subtitles?.map { sub ->
                subtitleCallback.invoke(
                    SubtitleFile(
                        "${fixCrunchyrollLang(sub.key) ?: sub.key} [ass]",
                        sub.value["url"]
                            ?: return@map null
                    )
                )
            }
        }
    }


    suspend fun invokeFlixon(
        tmdbId: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        onionUrl: String = "https://onionplay.se/"
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
            ExtractorLink(
                "Flixon",
                "Flixon",
                link
                    ?: return,
                "https://onionplay.stream/",
                Qualities.P720.value,
                link.contains(".m3u8")
            )
        )

    }

    suspend fun invokeSmashyStream(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        //Log.d("Phisher it url", "Inside Smashy")
        val url = if (season == null) {
            "$smashyStreamAPI/api/v1/videoflx/$tmdbId?token=3de0RPi5w90iubA0WyDwgVya9BRCErNUh4Da.ZJkSG5iMPgYjZbjJocYnkw.1725989195"
        } else {
            "$smashyStreamAPI/dataa.php?tmdb=$tmdbId&season=$season&episode=$episode"
        }
        //Log.d("Phisher it url", url.toString())
        val json = app.get(
            url,
            referer = "https://player.smashy.stream/"
        ).parsedSafe<SmashyRoot>()?.data?.sources?.map { it ->
            val file = it.file
            val extractm3u8 = file
                .substringAfter("#5")
                .substringBefore("\"")
                .replace(Regex("///.*?="), "")  // Remove specific pattern
                .let { base64Decode(it) }
            //Log.d("Phisher it url", extractm3u8.toString())
        }
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
            ExtractorLink(
                "Nepu",
                "Nepu",
                m3u8 ?: return,
                "$nepuAPI/",
                Qualities.P1080.value,
                INFER_TYPE
            )
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
            ?.apmap { iframe ->
                val response =
                    app.get(iframe.src ?: return@apmap, referer = "$moflixAPI/")
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
                if (m3u8?.haveDub("$host/") == false) return@apmap
                callback.invoke(
                    ExtractorLink(
                        "Moflix",
                        "Moflix [${iframe.name}]",
                        m3u8 ?: return@apmap,
                        "$host/",
                        iframe.quality?.filter { it.isDigit() }?.toIntOrNull()
                            ?: Qualities.Unknown.value,
                        INFER_TYPE
                    )
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
        media?.apmap { file ->
            val pathBody =
                """{"id":"${file.id ?: return@apmap null}"}""".toRequestBody(
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
                    fixUrl(path, worker ?: return@apmap null)
                } else {
                    fixUrl(path, apiUrl)
                }
            }.encodeUrl()

            val size = "%.2f GB".format(
                bytesToGigaBytes(
                    file.size?.toDouble()
                        ?: return@apmap null
                )
            )
            val quality = getIndexQuality(file.name)
            val tags = getIndexQualityTags(file.name)

            callback.invoke(
                ExtractorLink(
                    api,
                    "$api $tags [$size]",
                    path,
                    if (api in needRefererIndex) apiUrl else "",
                    quality,
                )
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
        }.apmap { file ->
            val videoUrl = extractGdflix(file.first)
            val quality = getIndexQuality(file.second)
            val tags = getIndexQualityTags(file.second)
            val size =
                Regex("(\\d+\\.?\\d+\\sGB|MB)").find(file.third)?.groupValues?.get(0)
                    ?.trim()

            callback.invoke(
                ExtractorLink(
                    "GdbotMovies",
                    "GdbotMovies $tags [$size]",
                    videoUrl ?: return@apmap null,
                    "",
                    quality,
                )
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
                ExtractorLink(
                    "DahmerMovies",
                    "DahmerMovies $tags",
                    decode((url + it.second).encodeUrl()),
                    "",
                    quality,
                )
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

    suspend fun invokeGhostx(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        invokeGpress(
            title,
            year,
            season,
            episode,
            callback,
            BuildConfig.GHOSTX_API,
            "Ghostx",
            base64Decode("X3NtUWFtQlFzRVRi"),
            base64Decode("X3NCV2NxYlRCTWFU")
        )
    }

    private suspend fun invokeGpress(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        api: String,
        name: String,
        mediaSelector: String,
        episodeSelector: String,
    ) {
        fun String.decrypt(key: String): List<GpressSources>? {
            return tryParseJson<List<GpressSources>>(base64Decode(this).xorDecrypt(key))
        }

        val slug = getEpisodeSlug(season, episode)
        val query = if (season == null) {
            title
        } else {
            "$title Season $season"
        }
        val savedCookies = mapOf(
            base64Decode("X2lkZW50aXR5Z29tb3ZpZXM3") to base64Decode("NTJmZGM3MGIwMDhjMGIxZDg4MWRhYzBmMDFjY2E4MTllZGQ1MTJkZTAxY2M4YmJjMTIyNGVkNGFhZmI3OGI1MmElM0EyJTNBJTdCaSUzQTAlM0JzJTNBMTglM0ElMjJfaWRlbnRpdHlnb21vdmllczclMjIlM0JpJTNBMSUzQnMlM0E1MiUzQSUyMiU1QjIwNTAzNjYlMkMlMjJIblZSUkFPYlRBU09KRXI0NVl5Q004d2lIb2wwVjFrbyUyMiUyQzI1OTIwMDAlNUQlMjIlM0IlN0Q="),
        )

        var res = app.get("$api/search/$query", timeout = 20)
        val cookies = savedCookies + res.cookies
        val doc = res.document
        val media = doc.select("div.$mediaSelector").map {
            Triple(
                it.attr("data-filmName"),
                it.attr("data-year"),
                it.select("a").attr("href")
            )
        }.let { el ->
            if (el.size == 1) {
                el.firstOrNull()
            } else {
                el.find {
                    if (season == null) {
                        (it.first.equals(title, true) || it.first.equals(
                            "$title ($year)",
                            true
                        )) && it.second.equals("$year")
                    } else {
                        it.first.equals("$title - Season $season", true)
                    }
                }
            } ?: el.find {
                it.first.contains(
                    "$title",
                    true
                ) && it.second.equals("$year")
            }
        } ?: return

        val iframe = if (season == null) {
            media.third
        } else {
            app.get(fixUrl(media.third, api), cookies = cookies, timeout = 20)
                .document.selectFirst("div#$episodeSelector a:contains(Episode ${slug.second})")
                ?.attr("href")
        }

        res = app.get(fixUrl(iframe ?: return, api), cookies = cookies, timeout = 20)
        val url = res.document.select("meta[property=og:url]").attr("content")
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val qualities = intArrayOf(2160, 1440, 1080, 720, 480, 360)

        val (serverId, episodeId) = if (season == null) {
            url.substringAfterLast("/") to "0"
        } else {
            url.substringBeforeLast("/")
                .substringAfterLast("/") to url.substringAfterLast("/")
                .substringBefore("-")
        }
        val serverRes = app.get(
            "$api/user/servers/$serverId?ep=$episodeId",
            cookies = cookies,
            referer = url,
            headers = headers,
            timeout = 20
        )
        val script = getAndUnpack(serverRes.text)
        val key =
            """key\s*=\s*(\d+)""".toRegex().find(script)?.groupValues?.get(1) ?: return
        serverRes.document.select("ul li").apmap { el ->
            val server = el.attr("data-value")
            val encryptedData = app.get(
                "$url?server=$server&_=$unixTimeMS",
                cookies = cookies,
                referer = url,
                headers = headers,
                timeout = 20
            ).text
            val links = encryptedData.decrypt(key)
            links?.forEach { video ->
                qualities.filter { it <= video.max.toInt() }.forEach {
                    callback(
                        ExtractorLink(
                            name,
                            name,
                            video.src.split("360", limit = 3)
                                .joinToString(it.toString()),
                            "$api/",
                            it,
                        )
                    )
                }
            }
        }

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
        iframes.apmap { iframe ->
            loadSourceNameExtractor(
                "Showflix ",
                iframe ?: return@apmap,
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
                ExtractorLink(
                    "CinemaTv",
                    "CinemaTv",
                    source.value,
                    "$cinemaTvAPI/",
                    getQualityFromName(source.key),
                    true
                )
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
        Log.d("Phisher",iframe.toString())
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
            ExtractorLink(
                "NowTv",
                "NowTv",
                url,
                referer,
                Qualities.P1080.value,
            )
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
        val mediaSlug = app.get("$ridomoviesAPI/core/api/search?q=$imdbId")
            .parsedSafe<RidoSearch>()?.data?.items?.find {
                it.contentable?.tmdbId == tmdbId || it.contentable?.imdbId == imdbId
            }?.slug ?: return

        val id = season?.let {
            val episodeUrl = "$ridomoviesAPI/tv/$mediaSlug/season-$it/episode-$episode"
            app.get(episodeUrl).text.substringAfterLast("""postid\":\"""")
                .substringBefore("""\""")
        } ?: mediaSlug

        val url =
            "$ridomoviesAPI/core/api/${if (season == null) "movies" else "episodes"}/$id/videos"
        app.get(url).parsedSafe<RidoResponses>()?.data?.apmap { link ->
            val iframe =
                Jsoup.parse(link.url ?: return@apmap).select("iframe").attr("data-src")
            if (iframe.startsWith("https://closeload.top")) {
                val unpacked =
                    getAndUnpack(app.get(iframe, referer = "$ridomoviesAPI/").text)
                val video = Regex("=\"(aHR.*?)\";").find(unpacked)?.groupValues?.get(1)
                callback.invoke(
                    ExtractorLink(
                        "Ridomovies",
                        "Ridomovies",
                        base64Decode(video ?: return@apmap),
                        "${getBaseUrl(iframe)}/",
                        Qualities.P1080.value,
                        isM3u8 = true
                    )
                )
            } else {
                loadExtractor(iframe, "$ridomoviesAPI/", subtitleCallback, callback)
            }
        }
    }

    suspend fun invokeAllMovieland(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val doc = app.get("$allmovielandAPI/5499-love-lies-bleeding.html").toString()
        val domainRegex = Regex("const AwsIndStreamDomain.*'(.*)';")
        val host = domainRegex.find(doc)?.groups?.get(1)?.value ?:""
        //val host="https://kioled326aps.com"
        val res = app.get(
            "$host/play/$imdbId",
            referer = "$allmovielandAPI/"
        ).document.selectFirst("script:containsData(playlist)")?.data()
            ?.substringAfter("{")
            ?.substringBefore(";")?.substringBefore(")")
        val json = tryParseJson<AllMovielandPlaylist>("{${res ?: return}")
        Log.d("Phisher", json.toString())
        val headers = mapOf("X-CSRF-TOKEN" to "${json?.key}")
        val serverRes = app.get(
            fixUrl(
                json?.file
                    ?: return, host
            ), headers = headers, referer = "$allmovielandAPI/"
        ).text.replace(Regex(""",\s*\[]"""), "")
        val servers =
            tryParseJson<ArrayList<AllMovielandServer>>(serverRes).let { server ->
                if (season == null) {
                    server?.map { it.file to it.title }
                } else {
                    server?.find { it.id.equals("$season") }?.folder?.find {
                        it.episode.equals(
                            "$episode"
                        )
                    }?.folder?.map {
                        it.file to it.title
                    }
                }
            }

        servers?.apmap { (server, lang) ->
            val path = app.post(
                "${host}/playlist/${server ?: return@apmap}.txt",
                headers = headers,
                referer = "$allmovielandAPI/"
            ).text
            safeApiCall {
                callback.invoke(
                    ExtractorLink(
                        "AllMovieLand-${lang}",
                        "AllMovieLand-${lang}",
                        path,
                        allmovielandAPI,
                        Qualities.Unknown.value,
                        true
                    )
                )
            }
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
            ExtractorLink(
                "SFMovies",
                "SFMovies",
                fixUrl(video, getSfServer()),
                "",
                Qualities.P1080.value,
                INFER_TYPE
            )
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
        val cfInterceptor = CloudflareKiller()
        val fixTitle = title?.substringBefore("-")?.replace(":", " ")?.replace("&", " ")
        val searchtitle = title?.substringBefore("-").createSlug()
        val url = if (season == null) {
            "$MovieDrive_API/search/$fixTitle $year"
        } else {
            "$MovieDrive_API/search/$fixTitle"
        }
        Log.d("Phisher Moviedrive", url)
        val res1 =
            app.get(url).document.select("figure")
                .toString()
        Log.d("Phisher Moviedrive", res1)
        val hrefpattern =
            Regex("""(?i)<a\s+href="([^"]*\b$searchtitle\b[^"]*)"""").find(res1)?.groupValues?.get(1)
                ?: ""
        if (hrefpattern.isNotEmpty()) {
            val document = app.get(hrefpattern, interceptor = cfInterceptor).document
            if (season == null) {
                document.select("h5 > a").amap {
                    Log.d("Phisher M href", it.toString())
                    val href = it.attr("href")
                    Log.d("Phisher M href", href)
                    val server = extractMdrive(href)
                    server.amap {
                        if (it.startsWith("https://hubcloud"))
                        {
                            HubCloud().getUrl(it, referer = "MoviesDrive")
                        }
                        Log.d("Phisher M server", it)
                        loadExtractor(it, referer = "MoviesDrive", subtitleCallback, callback)
                    }
                }
            } else {
                val stag = "Season $season|S0$season"
                val sep = "Ep0$episode|Ep$episode"
                val entries = document.select("h5:matches((?i)$stag)")
                Log.d("Phisher Moviedrive", entries.toString())
                entries.amap { entry ->
                    val href = entry.nextElementSibling()?.selectFirst("a")?.attr("href") ?: ""
                    if (href.isNotBlank()) {
                        val doc = app.get(href).document
                        val fEp = doc.selectFirst("h5:matches((?i)$sep)")?.toString()
                        if (fEp.isNullOrEmpty()) {
                            val furl = doc.select("h5 a:contains(HubCloud)").attr("href")
                            if (furl.startsWith("https://hubcloud"))
                            {
                                HubCloud().getUrl(furl, referer = "MoviesDrive")
                            }
                            loadExtractor(furl, referer = "MoviesDrive", subtitleCallback, callback)
                        } else
                            doc.selectFirst("h5:matches((?i)$sep)")?.let { epElement ->
                                val linklist = mutableListOf<String>()
                                val firstHubCloudH5 = epElement.nextElementSibling()
                                val secondHubCloudH5 = firstHubCloudH5?.nextElementSibling()
                                val firstLink = secondHubCloudH5?.selectFirst("a")?.attr("href")
                                val secondLink = secondHubCloudH5?.selectFirst("a")?.attr("href")
                                if (firstLink != null) linklist.add(firstLink)
                                if (secondLink != null) linklist.add(secondLink)
                                Log.d("Phisher Moviedrive", linklist.toString())
                                linklist.forEach { url ->
                                    if (url.startsWith("https://hubcloud"))
                                    {
                                        HubCloud().getUrl(url, referer = "MoviesDrive")
                                    }
                                    loadExtractor(
                                        url,
                                        referer = "MoviesDrive",
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

    suspend fun invokeAsiandrama(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "$Asiandrama_API/streaming.php?slug=$fixTitle-$year-episode-1"
        } else {
            "$Asiandrama_API/streaming.php?slug=$fixTitle-$year-episode-$episode"
        }
        val document = app.get(url).document
        document.select("ul.list-server-items > li").map {
            val server = it.attr("data-video")
            loadExtractor(server, subtitleCallback, callback)
        }
    }

    @SuppressLint("SuspiciousIndentation")
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
        val fixtitle = title?.substringBefore("-")?.substringBefore(":")?.replace("&", " ")
        val searchtitle = title?.substringBefore("-").createSlug()
        Log.d("Phisher bolly", "$bollyflixAPI/search/$imdbId")
        var res1 =
            app.get("$bollyflixAPI/search/$imdbId", interceptor = wpRedisInterceptor).document.select("#content_box article")
                .toString()
        val hrefpattern =
            Regex("""(?i)<article[^>]*>\s*<a\s+href="([^"]*\b$searchtitle\b[^"]*)""").find(res1)?.groupValues?.get(
                1
            )
        Log.d("Phisher bolly", "$hrefpattern")
        val res = hrefpattern?.let { app.get(it).document }
        val hTag = if (season == null) "h5" else "h4"
        //val aTag = if (season == null) "" else ""
        val sTag = if (season == null) "" else "Season $season"
        val entries =
            res?.select("div.thecontent.clearfix > $hTag:matches((?i)$sTag.*(1080p|2160p))")
                ?.filter { element -> !element.text().contains("Download", true) }?.takeLast(3)
        Log.d("Phisher bolly", "$entries")
        entries?.map {
            val href = it.nextElementSibling()?.select("a")?.attr("href")
            val token = href?.substringAfter("id=")
            val encodedurl =
                app.get("https://web.sidexfee.com/?id=$token").text.substringAfter("link\":\"")
                    .substringBefore("\"};")
            val decodedurl = base64Decode(encodedurl)
            //Log.d("Phisher media decoded",decodedurl.toString())
            if (season == null) {
                val source =
                    app.get(decodedurl, allowRedirects = false).headers["location"].toString()
                loadExtractor(source, "Bollyflix", subtitleCallback, callback)
            } else {
                val link =
                    app.get(decodedurl).document.selectFirst("article h3 a:contains(Episode 0$episode)")!!
                        .attr("href")
                val source = app.get(link, allowRedirects = false).headers["location"].toString()
                loadExtractor(source, "Bollyflix", subtitleCallback, callback)
            }
        }
    }

    suspend fun invokemovies4u(
        title: String? = null,
        episode: Int?= null,
        season: Int?= null,
        year: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val fixtitle = title?.substringBefore("-")?.replace(":","")
        val searchtitle = title?.substringBefore(":")?.substringBefore("-").createSlug()
        var res1 =
            app.get("$movies4u/search/$fixtitle $year").document.select("section.site-main article")
                .toString()
        val hrefpattern =
            Regex("""(?i)<h3[^>]*>\s*<a\s+href="([^"]*\b$searchtitle\b[^"]*)"""").find(res1)?.groupValues?.get(1)
        Log.d("Phisher","$movies4u/search/$searchtitle $year")
        Log.d("Phisher",hrefpattern ?:"")
        Log.d("Phisher", searchtitle.toString())
        if (hrefpattern!=null) {
            if (season == null) {
                val servers = mutableSetOf<String>()
                app.get(hrefpattern).document.select("div.watch-links-div a, div.download-links-div a")
                    .forEach {
                        servers += it.attr("href")
                    }
                servers.forEach { links ->
                    if (links.contains("linkz.wiki")) {
                        app.get(links).document.select("div.download-links-div > div a:matches(Hub-Cloud)").amap {
                            val link = it.attr("href")
                            loadCustomExtractor(
                                "Movies4u Hub-Cloud",
                                link,
                                "",
                                subtitleCallback,
                                callback
                            )
                        }
                    }
                    else if (links.contains("vidhideplus"))
                    {
                        loadCustomExtractor(
                            "Movies4u Vidhide Hub-Cloud",
                            links,
                            "",
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }
            else
            {
                val sTag="Season $season"
                val doc=app.get(hrefpattern).document
                val entries = doc.select("h4:matches((?i)$sTag)")
                //Log.d("Phisher",entries.toString())
                entries.amap {
                    val href=it.nextElementSibling()?.select("a:matches(Download Links)")?.attr("href")
                    if (href!=null) {
                        val iframe = app.get(href).document.selectFirst("h5:matches(Episodes: $episode)")?.nextElementSibling()?.select("a:contains(GDFlix)")?.attr("href")
                        if (iframe != null) {
                                loadCustomExtractor(
                                    "Movies4u",
                                    iframe,
                                    "",
                                    subtitleCallback,
                                    callback
                                )
                        }
                    }
                }
            }
        }
    }

    suspend fun invokecatflix(
        id: Int? = null,
        epid: Int? = null,
        title: String? = null,
        episode: Int?= null,
        season: Int?= null,
        year: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val fixtitle=title.createSlug()
        val Juicykey=app.get(BuildConfig.CatflixAPI, referer = Catflix).parsedSafe<CatflixJuicy>()?.juice
        val href=if (season==null)
        {
            "$Catflix/movie/$fixtitle-$id"
        }
        else
        {
            "$Catflix/episode/${fixtitle}-season-${season}-episode-${episode}/eid-$epid"
        }
        val res=app.get(href, referer = Catflix).toString()
        val iframe= base64Decode(Regex("""(?:const|let)\s+main_origin\s*=\s*"(.*)";""").find(res)?.groupValues?.get(1)!!)
        val iframedata=app.get(iframe, referer = Catflix).toString()
        val apkey=Regex("""apkey\s*=\s*['"](.*?)["']""").find(iframedata)?.groupValues?.get(1)
        val xxid=Regex("""xxid\s*=\s*['"](.*?)["']""").find(iframedata)?.groupValues?.get(1)
        val theJuiceData = "https://turbovid.eu/api/cucked/the_juice/?${apkey}=${xxid}"
        val Juicedata= app.get(theJuiceData, headers = mapOf("X-Requested-With" to "XMLHttpRequest"), referer = theJuiceData).parsedSafe<CatflixJuicydata>()?.data
        if (Juicedata!=null && Juicykey!=null)
        {
            val url= CatdecryptHexWithKey(Juicedata,Juicykey)
            val headers= mapOf("Origin" to "https://turbovid.eu/","Connection" to "keep-alive")
            callback.invoke(
                ExtractorLink(
                    "Catflix",
                    "Catflix",
                    url,
                    "https://turbovid.eu/",
                    Qualities.P1080.value,
                    type = INFER_TYPE,
                    headers=headers,
                )
            )
        }
    }

    suspend fun invokeDramaCool(
        title: String?,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {

        val json = if(season == null && episode == null) {
            var episodeSlug = "$title episode 1".createSlug()
            val url = "${ConsumetAPI}/movies/dramacool/watch?episodeId=${episodeSlug}"
            val res = app.get(url).text
            if(res.contains("Media Not found")) {
                val newEpisodeSlug = "$title $year episode 1".createSlug()
                val newUrl = "$ConsumetAPI/movies/dramacool/watch?episodeId=${newEpisodeSlug}"
                app.get(newUrl).text
            }
            else {
                res
            }
        }
        else {
            val seasonText = if(season == 1) "" else "season $season"
            val episodeSlug = "$title $seasonText episode $episode".createSlug()
            val url =  "${ConsumetAPI}/movies/dramacool/watch?episodeId=${episodeSlug}"
            val res = app.get(url).text
            if(res.contains("Media Not found")) {
                val newEpisodeSlug = "$title $seasonText $year episode $episode".createSlug()
                val newUrl = "$ConsumetAPI/movies/dramacool/watch?episodeId=${newEpisodeSlug}"
                app.get(newUrl).text
            }
            else {
                res
            }
        }

        val data = parseJson<ConsumetSources>(json)
        data.sources?.forEach {
            callback.invoke(
                ExtractorLink(
                    "DramaCool",
                    "DramaCool",
                    it.url,
                    referer = "",
                    quality = Qualities.P1080.value,
                    isM3u8 = true
                )
            )
        }

        data.subtitles?.forEach {
            subtitleCallback.invoke(
                SubtitleFile(
                    it.lang,
                    it.url
                )
            )
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
        //val fixtitle = title?.substringBefore("-")?.substringBefore(":")?.replace("&", " ")
        val searchtitle = title?.substringBefore("-").createSlug()
        //Log.d("Phisher bolly", "$BollyflixVIP/search/$imdbId")
        var res1 =
            app.get("$BollyflixVIP/search/$imdbId", interceptor = wpRedisInterceptor).document.select("#content_box article")
                .toString()
        val hrefpattern =
            Regex("""(?i)<article[^>]*>\s*<a\s+href="([^"]*\b$searchtitle\b[^"]*)""").find(res1)?.groupValues?.get(
                1
            )
        val res = hrefpattern?.let { app.get(it).document }
        val hTag = if (season == null) "h5" else "h4"
        //val aTag = if (season == null) "" else ""
        val sTag = if (season == null) "" else "Season $season"
        val entries =
            res?.select("div.thecontent.clearfix > $hTag:matches((?i)$sTag.*(720p|1080p|2160p))")
                ?.filter { element -> !element.text().contains("Download", true) }?.takeLast(3)
        entries?.map {
            val href = it.nextElementSibling()?.select("a")?.attr("href")
            val token = href?.substringAfter("id=")
            val encodedurl =
                app.get("https://web.sidexfee.com/?id=$token").text.substringAfter("link\":\"")
                    .substringBefore("\"};")
            val decodedurl = base64Decode(encodedurl)
            //Log.d("Phisher media decoded",decodedurl.toString())
            if (season == null) {
                val source =
                    app.get(decodedurl, allowRedirects = false).headers["location"].toString()
                loadExtractor(source, "Bollyflix", subtitleCallback, callback)
            } else {
                val link =
                    app.get(decodedurl).document.selectFirst("article h3 a:contains(Episode 0$episode)")!!
                        .attr("href")
                val source = app.get(link, allowRedirects = false).headers["location"].toString()
                loadExtractor(source, "Bollyflix VIP", subtitleCallback, callback)
            }
        }
    }

suspend fun invokeFlixAPIHQ(
        title: String? = null,
        year: Int?=null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = "$FlixAPI/search?q=$title"
        val id= app.get(url).parsedSafe<SerachFlix>()?.items?.find {
        if (season==null)
        {
            it.title.equals(
                "$title", true
            ) && it.stats.year == "$year"
        }
        else {
            //TODO
            it.title.equals(
                "$title", true
            ) && it.stats.year == "$year"
        }
        }?.id ?: return
    val epid=if (season == null)
        {
            app.get("$FlixAPI/movie/$id", timeout = 5000L).parsedSafe<MoviedetailsResponse>()?.episodeId
        } else {
            app.get("$FlixAPI/movie/$id").parsedSafe<MoviedetailsResponse>()?.episodeId
        }
    val listOfServers = if (season == null) {
        app.get("$FlixAPI/movie/$id/servers?episodeId=$epid/")
            .parsedSafe<FlixServers>()
            ?.servers
            ?.map { server ->
                server.id to server.name // Pair of id and name
            }
    } else {
        app.get("$FlixAPI/movie/$id/servers?episodeId=$epid/")
            .parsedSafe<FlixServers>()
            ?.servers
            ?.map { server ->
                server.id to null // Pair of id and null (no name if not required)
            }
    }
    Log.d("Phisher",listOfServers.toString())
    listOfServers?.forEach { (serverid, name) ->
        val data= app.get("$FlixAPI/movie/$id/sources?serverId=$serverid").parsedSafe<FlixHQsources>()
        Log.d("Phisher","\"$FlixAPI/movie/$id/sources?serverId=$serverid\" $data".toString())
        val m3u8=data?.sources?.map { it.file }?.firstOrNull()
        if (name != null) {
            callback.invoke(
                ExtractorLink(
                    name.capitalize(),
                    name,
                    fixUrl( m3u8 ?:""),
                    "",
                    Qualities.P1080.value,
                    INFER_TYPE
                )
            )
        }
        data?.tracks?.amap { track->
            val vtt=track.file
            val lang=track.label
            subtitleCallback.invoke(
                SubtitleFile(
                    getLanguage(lang),
                    vtt
                )
            )
        }
    }

}

    suspend fun invokeHinAuto(
        id: Int? = null,
        year: Int?=null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season==null) "$HinAutoAPI/movie/$id" else "$HinAutoAPI/tv/$id/$season/$episode"
        val res= app.get(url, referer = "https://autoembed.cc", timeout = 5000L).toString()
            val json =
                Regex("sources\\s*:\\s*(\\[[^]]*])").find(res)?.groupValues?.get(1).toString() ?:null
            if (json!=null) {
                val jsondata = parseJsonHinAuto((json))
                jsondata.forEach { data ->
                    val m3u8 = data.file
                    val lang = data.label
                    callback.invoke(
                        ExtractorLink(
                            "HIN Autoembed $lang",
                            "HIN Autoembed $lang",
                            m3u8,
                            "",
                            Qualities.P1080.value,
                            ExtractorLinkType.M3U8
                        )
                    )
                }
            }
    }
    private fun parseJsonHinAuto(json: String): HinAuto {
        val gson = Gson()

        // Deserialize the JSON into the Root type (List of Root2)
        return gson.fromJson(json, Array<HinAutoRoot2>::class.java).toList()
    }

suspend fun invokenyaa(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url="$NyaaAPI?f=0&c=0_0&q=$title+S0${season}E0$episode&s=seeders&o=desc"
        app.get(url).document.select("tr.danger,tr.default").take(10).amap {
            val Qualities=getIndexQuality(it.selectFirst("tr td:nth-of-type(2)")?.text())
            val href= getfullURL(it.select("td.text-center a:nth-child(1)").attr("href"), NyaaAPI)
            callback.invoke(
                ExtractorLink(
                    "Nyaa $Qualities",
                    "Nyaa $Qualities",
                    href,
                    "",
                    Qualities,
                    ExtractorLinkType.TORRENT
                )
            )
        }
    }

}






