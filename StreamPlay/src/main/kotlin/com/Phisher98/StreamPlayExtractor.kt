package com.phisher98

import android.annotation.SuppressLint
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.extractors.FileMoonSx
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.RequestBodyTypes
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import kotlinx.coroutines.*
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max


val session = Session(Requests().baseClient)

object StreamPlayExtractor : StreamPlay() {

    //Need Fix
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

    suspend fun invokeMultimovies(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val multimoviesApi = getDomains()?.multiMovies ?: return
        val fixTitle = title.createSlug()

        val url = if (season == null) {
            "$multimoviesApi/movies/$fixTitle"
        } else {
            "$multimoviesApi/episodes/$fixTitle-${season}x${episode}"
        }

        Log.d("Phisher", url)

        val req = app.get(url, interceptor = CloudflareKiller()).document

        if (req.body().text().contains("Just a moment", ignoreCase = true)) return

        val playerOptions = req.select("ul#playeroptionsul li").map {
            Triple(
                it.attr("data-post"),
                it.attr("data-nume"),
                it.attr("data-type")
            )
        }

        playerOptions.amap { (postId, nume, type) ->
            if (!nume.contains("trailer", ignoreCase = true)) {
                runCatching {
                    val response = app.post(
                        url = "$multimoviesApi/wp-admin/admin-ajax.php",
                        data = mapOf(
                            "action" to "doo_player_ajax",
                            "post" to postId,
                            "nume" to nume,
                            "type" to type
                        ),
                        referer = url,
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                    ).parsed<ResponseHash>()

                    val embedUrl = response.embed_url
                    val link = embedUrl.substringAfter("\"").substringBefore("\"")

                    if (!link.contains("youtube", ignoreCase = true)) {
                        loadCustomExtractor(
                            "Multimovies",
                            link,
                            "$multimoviesApi/",
                            subtitleCallback,
                            callback
                        )
                    } else {
                        Log.d("Multimovies", "Skipped YouTube link")
                    }
                }.onFailure {
                    Log.e("Multimovies", "Failed to extract player: ${it.localizedMessage}")
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


    suspend fun invokeazseries(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val targetUrl = if (season == null) {
            "$azseriesAPI/embed/$fixTitle"
        } else {
            "$azseriesAPI/episodes/$fixTitle-season-$season-episode-$episode"
        }

        val res = app.get(targetUrl, referer = azseriesAPI)
        val document = res.document

        val movieId = document.selectFirst("#show_player_lazy")?.attr("movie-id") ?: return

        val serverDoc = app.post(
            url = "$azseriesAPI/wp-admin/admin-ajax.php",
            data = mapOf("action" to "lazy_player", "movieID" to movieId),
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            referer = azseriesAPI
        ).document

        val servers = serverDoc.select("div#playeroptions > ul > li")
        if (servers.isEmpty()) return

        servers.forEach { server ->
            val name = server.text().trim()
            val serverUrl = server.attr("data-vs").takeIf { it.isNotBlank() } ?: return@forEach

            val redirectUrl = app.get(
                serverUrl,
                referer = azseriesAPI,
                allowRedirects = false
            ).headers["Location"] ?: return@forEach

            when (name) {
                "Filemoon" -> FileMoonSx().getUrl(redirectUrl, azseriesAPI, subtitleCallback, callback)
                "Streamhide" -> StreamWishExtractor().getUrl(redirectUrl, azseriesAPI, subtitleCallback, callback)
                else -> loadExtractor(redirectUrl, subtitleCallback, callback)
            }
        }
    }



    suspend fun invokeMoviehubAPI(
        id: Int? = null,
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

    private suspend fun invokeTokyoInsider(
        jptitle: String? = null,
        title: String? = null,
        episode: Int? = null,
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
                    val safeLink = link ?: return@amap null
                    when {
                        safeLink.contains(".m3u8") -> {
                            callback(
                                newExtractorLink(
                                    "Kisskh",
                                    "Kisskh",
                                    safeLink,
                                    INFER_TYPE
                                ) {
                                    referer = kissKhAPI
                                    quality = Qualities.P720.value
                                    headers = mapOf("Origin" to kissKhAPI)
                                }
                            )
                        }

                        safeLink.contains(".mp4") -> {
                            callback(
                                newExtractorLink(
                                    "Kisskh",
                                    "Kisskh",
                                    safeLink,
                                    INFER_TYPE
                                ) {
                                    referer = kissKhAPI
                                    quality = Qualities.P720.value
                                    headers = mapOf("Origin" to kissKhAPI)
                                }
                            )
                        }

                        else -> {
                            val cleanedLink = safeLink.substringBefore("?").takeIf { it.isNotBlank() } ?: return@amap null
                            loadSourceNameExtractor(
                                "Kisskh",
                                cleanedLink,
                                "$kissKhAPI/",
                                subtitleCallback,
                                callback,
                                Qualities.P720.value
                            )
                        }
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
        date: String?,
        airedDate: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val (_, malId) = convertTmdbToAnimeId(
            title, date, airedDate, if (season == null) TvType.AnimeMovie else TvType.Anime
        )

        val malsync = malId?.let {
            runCatching {
                app.get("$malsyncAPI/mal/anime/$it").parsedSafe<MALSyncResponses>()?.sites
            }
                .getOrNull()
        }

        val zoro = malsync?.zoro
        val zoroIds = zoro?.keys?.toList().orEmpty()
        val zorotitle = zoro?.values?.firstNotNullOfOrNull { it["title"] }?.replace(":", " ")
        val anititle = title
        val aniXL = malsync?.AniXL?.values?.firstNotNullOfOrNull { it["url"] }
        val kaasSlug = malsync?.KickAssAnime?.values?.firstNotNullOfOrNull { it["identifier"] }
        val animepaheUrl = malsync?.animepahe?.values?.firstNotNullOfOrNull { it["url"] }
        val tmdbYear = date?.substringBefore("-")?.toIntOrNull()

        runAllAsync(
            { malId?.let { invokeAnimetosho(it, season, episode, subtitleCallback, callback) } },
            { invokeHianime(zoroIds, episode, subtitleCallback, callback) },
            {
                malId?.let {
                    invokeAnimeKai(
                        jptitle,
                        zorotitle,
                        episode,
                        subtitleCallback,
                        callback
                    )
                }
            },
            { kaasSlug?.let { invokeKickAssAnime(it, episode, subtitleCallback, callback) } },
            { animepaheUrl?.let { invokeAnimepahe(it, episode, subtitleCallback, callback) } },
            { invokeAnichi(zorotitle,anititle, tmdbYear, episode, subtitleCallback, callback) },
            { invokeAnimeOwl(zorotitle, episode, subtitleCallback, callback) },
            { invokeTokyoInsider(jptitle, title, episode, callback) },
            { invokeAnizone(jptitle, episode, callback) },
            {
                if (aniXL != null) {
                    invokeAniXL(aniXL, episode, callback)
                }
            })
    }

    suspend fun invokeAniXL(
        url: String,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseurl = getBaseUrl(url)
        val document = app.get(url).document

        val episodeLink = (baseurl + document
            .select("a.btn")
            .firstOrNull { it.text().trim() == episode?.toString() }
            ?.attr("href"))

        val jsonText = app.get(episodeLink).text
        val parts = jsonText.split(",").map { it.trim('"') }

        var dubUrl: String? = null
        var rawUrl: String? = null
        for (i in parts.indices) {
            when (parts[i]) {
                "dub" -> {
                    val possibleUrl = parts.getOrNull(i + 1)
                    if (possibleUrl != null) {
                        if (
                            possibleUrl.endsWith(".m3u8") &&
                            !possibleUrl.contains(".ico")
                        ) {
                            dubUrl = possibleUrl
                        }
                    }
                }

                "raw" -> {
                    val possibleUrl = parts.getOrNull(i + 1)
                    if (possibleUrl != null) {
                        if (
                            possibleUrl.endsWith(".m3u8") &&
                            !possibleUrl.contains(".ico")
                        ) {
                            rawUrl = possibleUrl
                        }
                    }
                }

                "sub" -> {
                    val possibleUrl = parts.getOrNull(i + 1)
                    if (possibleUrl != null) {
                        if (
                            possibleUrl.endsWith(".m3u8") &&
                            !possibleUrl.contains(".ico")
                        ) {
                            rawUrl = possibleUrl
                        }
                    }
                }
            }
        }

        if (dubUrl != null) {
            callback(
                newExtractorLink(
                    "AniXL DUB",
                    "AniXL DUB",
                    dubUrl,
                    INFER_TYPE
                ) {
                    quality = Qualities.P1080.value
                }
            )
        }

        if (rawUrl != null) {
            callback(
                newExtractorLink(
                    "AniXL SUB",
                    "AniXL SUB",
                    rawUrl,
                    INFER_TYPE
                ) {
                    quality = Qualities.P1080.value
                }
            )
        }
    }




    suspend fun invokeAnichi(
        name: String?,
        engtitle: String?,
        year: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val privatereferer = "https://allmanga.to"
        val ephash = "5f1a64b73793cc2234a389cf3a8f93ad82de7043017dd551f38f65b89daa65e0"
        val queryhash = "06327bc10dd682e1ee7e07b6db9c16e9ad2fd56c1b769e47513128cd5c9fc77a"
        val type = if (episode == null) "Movie" else "TV"

        val normalizedName = name?.trim()?.lowercase()
        val normalizedEngTitle = engtitle?.trim()?.lowercase()

        val query = """${BuildConfig.ANICHI_API}?variables={"search":{"types":["$type"],"year":$year,"query":"$name"},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$queryhash"}}"""
        val response = app.get(query, referer = privatereferer)
            .parsedSafe<AnichiRoot>()
            ?.data?.shows?.edges ?: return

        val matched = response.find { item ->
            val itemName = item.name.trim().lowercase()
            val itemEnglishName = item.englishName.trim().lowercase()
            (normalizedName != null && (itemName == normalizedName || itemEnglishName == normalizedName)) ||
                    (normalizedEngTitle != null && (itemName == normalizedEngTitle || itemEnglishName == normalizedEngTitle))
        } ?: response.find { item ->
            val allTitles = listOfNotNull(item.name, item.englishName).map { it.trim().lowercase() }
            allTitles.any {
                (normalizedName != null && it.contains(normalizedName)) ||
                        (normalizedEngTitle != null && it.contains(normalizedEngTitle))
            }
        } ?: return

        val id = matched.id
        val langTypes = listOf("sub", "dub")

        langTypes.forEach { lang ->
            val epQuery = """${BuildConfig.ANICHI_API}?variables={"showId":"$id","translationType":"$lang","episodeString":"${episode ?: 1}"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$ephash"}}"""
            val episodeLinks = app.get(epQuery, referer = privatereferer)
                .parsedSafe<AnichiEP>()
                ?.data?.episode?.sourceUrls ?: return@forEach

            episodeLinks.amap { source ->
                safeApiCall {
                    val sourceUrl = source.sourceUrl
                    val headers = mapOf(
                        "app-version" to "android_c-247",
                        "platformstr" to "android_c",
                        "Referer" to privatereferer
                    )

                    if (sourceUrl.startsWith("http")) {
                        val host = sourceUrl.getHost()
                        loadCustomExtractor(
                            "Allanime [${lang.uppercase()}] [$host]",
                            sourceUrl,
                            "",
                            subtitleCallback,
                            callback
                        )
                        return@safeApiCall
                    }

                    val decoded = if (sourceUrl.startsWith("--")) decrypthex(sourceUrl) else sourceUrl
                    val fixedLink = decoded.fixUrlPath()
                    val links = try {
                        app.get(fixedLink, headers = headers)
                            .parsedSafe<AnichiVideoApiResponse>()
                            ?.links ?: emptyList()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return@safeApiCall
                    }

                    links.forEach { server ->
                        val host = server.link.getHost()
                        when {
                            source.sourceName.contains("Default") && server.resolutionStr in listOf("SUB", "Alt vo_SUB") -> {
                                getM3u8Qualities(server.link, "https://static.crunchyroll.com/", host)
                                    .forEach(callback)
                            }
                            server.hls == null -> {
                                callback(
                                    newExtractorLink(
                                        "Allanime [${lang.uppercase()}] ${host.capitalize()}",
                                        "Allanime [${lang.uppercase()}] ${host.capitalize()}",
                                        server.link,
                                        INFER_TYPE
                                    ) {
                                        this.quality = Qualities.P1080.value
                                    }
                                )
                            }
                            server.hls == true -> {
                                val endpoint = "https://allanime.day/player?uri=" +
                                        if (URI(server.link).host.isNotEmpty()) server.link
                                        else "https://allanime.day${URI(server.link).path}"
                                getM3u8Qualities(server.link, server.headers?.referer ?: endpoint, host)
                                    .forEach(callback)
                            }
                            else -> {
                                server.subtitles?.forEach { sub ->
                                    val langName = SubtitleHelper.fromTwoLettersToLanguage(sub.lang ?: "") ?: sub.lang.orEmpty()
                                    val src = sub.src ?: return@forEach
                                    subtitleCallback(SubtitleFile(langName, httpsify(src)))
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
        val slug = name?.createSlug() ?: return
        val url = "$AnimeOwlAPI/anime/$slug"

        val document = app.get(url).document

        val contentBlocks = document.select("#anime-cover-sub-content, #anime-cover-dub-content")

        contentBlocks.forEach { block ->
            val type = if (block.id().contains("sub", ignoreCase = true)) "SUB" else "DUB"

            val episodeLink = block.select("a.episode-node")
                .firstOrNull { it.attr("title") == episode?.toString() }
                ?.attr("href")

            if (!episodeLink.isNullOrEmpty()) {
                loadCustomExtractor(
                    name = "AnimeOwl [$type]",
                    url = episodeLink,
                    referer = AnimeOwlAPI,
                    subtitleCallback = subtitleCallback,
                    callback = callback
                )
            }
        }
    }


    suspend fun invokeAnimepahe(
        url: String,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf("Cookie" to "__ddg2_=1234567890")

        val id = app.get("https://animepaheproxy.phisheranimepahe.workers.dev/?url=$url", headers)
            .document.selectFirst("meta[property=og:url]")
            ?.attr("content").toString().substringAfterLast("/")

        val animeData = app.get(
            "https://animepaheproxy.phisheranimepahe.workers.dev/?url=$animepaheAPI/api?m=release&id=$id&sort=episode_desc&page=1",
            headers
        ).parsedSafe<animepahe>()?.data.orEmpty()

        val reversedData = animeData.reversed()

        val targetIndex = (episode ?: 1) - 1
        if (targetIndex !in reversedData.indices) return
        val session = reversedData[targetIndex].session

        val document = app.get(
            "https://animepaheproxy.phisheranimepahe.workers.dev/?url=$animepaheAPI/play/$id/$session",
            headers
        ).document

        document.select("#resolutionMenu button").map {
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
            if (it.select("span").text().contains("eng", ignoreCase = true))
                type = "DUB"

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


    suspend fun invokeAnimetosho(
        malId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        fun Elements.getLinks(): List<Triple<String, String, Int>> {
            return this.flatMap { ele ->
                ele.select("div.links a:matches(KrakenFiles|GoFile|Akirabox|BuzzHeavier)").map {
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
                    "Animetosho",
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
                val matchedId = match.id
                val href = "$AnimeKai/watch/$matchedId"

                // Fetch anime details and episode list
                val animeId = app.get(href).document.selectFirst("div.rate-box")?.attr("data-id")
                val decoded = app.get("${BuildConfig.KAISVA}/?f=e&d=$animeId")
                val epRes = app.get("$AnimeKai/ajax/episodes/list?ani_id=$animeId&_=$decoded")
                    .parsedSafe<AnimeKaiResponse>()?.getDocument()

                epRes?.select("div.eplist a")?.forEach { ep ->
                    val epNum = ep.attr("num").toIntOrNull()
                    if (epNum == episode) {
                        val token = ep.attr("token")

                        // Fetch episode links for this episode
                        val decodedtoken = app.get("${BuildConfig.KAISVA}/?f=e&d=$token")
                        val document =
                            app.get("$AnimeKai/ajax/links/list?token=$token&_=$decodedtoken")
                                .parsed<AnimeKaiResponse>()
                                .getDocument()

                        val types = listOf("sub", "softsub", "dub")
                        val servers = types.flatMap { type ->
                            document.select("div.server-items[data-id=$type] span.server[data-lid]")
                                .map { server ->
                                    val lid = server.attr("data-lid")
                                    val serverName = server.text()
                                    Triple(type, lid, serverName)
                                }
                        }

                        // Process each server sequentially
                        for ((type, lid, serverName) in servers) {
                            val decodelid = app.get("${BuildConfig.KAISVA}/?f=e&d=$lid")
                            val result = app.get("$AnimeKai/ajax/links/view?id=$lid&_=$decodelid")
                                .parsed<AnimeKaiResponse>().result
                            val decodeiframe = app.get("${BuildConfig.KAISVA}/?f=d&d=$result").text
                            val iframe = extractVideoUrlFromJsonAnimekai(decodeiframe)

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
        val html =
            JSONObject(jsonResponse).optJSONObject("result")?.optString("html") ?: return results
        val doc = Jsoup.parse(html)

        for (element in doc.select("a.aitem")) {
            val href = element.attr("href").substringAfterLast("/")
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


    internal suspend fun invokeHianime(
        animeIds: List<String?>? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        for (api in hianimeAPIs) {
            try {
                animeIds?.amap { id ->
                    val episodeId = app.get(
                        "$api/ajax/v2/episode/list/${id ?: return@amap}",
                        headers = headers
                    ).parsedSafe<HianimeResponses>()?.html?.let {
                        Jsoup.parse(it)
                    }?.select("div.ss-list a")
                        ?.find { it.attr("data-number") == "${episode ?: 1}" }
                        ?.attr("data-id")

                    val servers = app.get(
                        "$api/ajax/v2/episode/servers?episodeId=${episodeId ?: return@amap}",
                        headers = headers
                    ).parsedSafe<HianimeResponses>()?.html?.let { Jsoup.parse(it) }
                        ?.select("div.item.server-item")?.map {
                            Triple(
                                it.text(),
                                it.attr("data-id"),
                                it.attr("data-type"),
                            )
                        }

                    servers?.map { (label, id, effectiveType) ->
                        val sourceurl = app.get("$api/ajax/v2/episode/sources?id=$id")
                            .parsedSafe<EpisodeServers>()?.link
                        if (sourceurl != null) {
                            loadCustomExtractor(
                                "⌜ HiAnime ⌟ | ${label.uppercase()} | ${effectiveType.uppercase()}",
                                sourceurl,
                                "",
                                subtitleCallback,
                                callback,
                            )
                        }
                    }
                }
                return
            } catch (e: Exception) {
                println("Failed with domain $api: ${e.message}")
                continue
            }
        }
        println("All hianimeAPI domains failed.")
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
    ) {
        val uhdmoviesAPI = getDomains()?.uhdmovies ?: return
        val searchTitle = title?.replace("-", " ")?.replace(":", " ") ?: return

        val searchUrl = if (season != null) {
            "$uhdmoviesAPI/search/$searchTitle $year"
        } else {
            "$uhdmoviesAPI/search/$searchTitle"
        }

        val url = try {
            app.get(searchUrl).document
                .select("article div.entry-image a")
                .firstOrNull()
                ?.attr("href")
                ?.takeIf(String::isNotBlank)
                ?: return
        } catch (e: Exception) {
            Log.e("UHDMovies", "Search error: ${e.localizedMessage}")
            return
        }

        val doc = try {
            app.get(url).document
        } catch (e: Exception) {
            Log.e("UHDMovies", "Main page load failed: ${e.localizedMessage}")
            return
        }

        val seasonPattern = season?.let { "(?i)(S0?$it|Season 0?$it)" }
        val episodePattern = episode?.let { "(?i)(Episode $it)" }

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

        for (link in links) {
            try {
                if (link.isNotEmpty()) {
                    val finalLink = if (link.contains("unblockedgames")) {
                        bypassHrefli(link)
                    } else {
                        link
                    }

                    if (!finalLink.isNullOrBlank()) {
                        loadSourceNameExtractor(
                            "UHDMovies",
                            finalLink,
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
    }



    suspend fun invokeSubtitleAPI(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
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
        title: String?,
        year: Int?,
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val backendAPI = getDomains()?.xprime ?: return
        val servers = app.get("$backendAPI/servers").parsedSafe<XprimeServers>() ?: return

        val objectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        servers.servers.forEach { server ->
            if (server.status != "ok") return@forEach

            val baseUrl = "$backendAPI/${server.name}"
            val queryParams = buildString {
                append("?name=${title.orEmpty()}")
                when (server.name) {
                    "primebox" -> {
                        if (year != null) append("&fallback_year=$year")
                        if (season != null && episode != null) append("&season=$season&episode=$episode")
                    }
                    else -> {
                        if (year != null) append("&year=$year")
                        if (!id.isNullOrBlank()) append("&id=$id&imdb=$id")
                        if (season != null && episode != null) append("&season=$season&episode=$episode")
                    }
                }
            }

            val finalUrl = baseUrl + queryParams

            try {
                val response = app.get(finalUrl)
                val json = response.text
                val serverLabel = "Xprime ${server.name.replaceFirstChar { it.uppercaseChar() }}"

                if (server.name == "primebox") {
                    val stream = objectMapper.readValue<XprimeStream>(json)
                    val streamsJson = objectMapper.readTree(json).get("streams")

                    stream.qualities.forEach { quality ->
                        val url = streamsJson?.get(quality)?.textValue()
                        if (!url.isNullOrBlank()) {
                            callback(
                                newExtractorLink(
                                    source = serverLabel,
                                    name = serverLabel,
                                    url = url,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.quality = getQualityFromName(quality)
                                    this.headers = mapOf("Origin" to Xprime)
                                    this.referer = Xprime
                                }
                            )
                        }
                    }

                    if (stream.hasSubtitles) {
                        stream.subtitles.forEach { subtitle ->
                            val subUrl = subtitle.file.orEmpty()
                            if (subUrl.isNotBlank()) {
                                subtitleCallback(
                                    SubtitleFile(
                                        lang = subtitle.label ?: "Unknown",
                                        url = subUrl
                                    )
                                )
                            }
                        }
                    }
                } else {
                    val url = objectMapper.readTree(json).get("url")?.textValue().orEmpty()
                    if (url.isNotBlank()) {
                        callback(
                            newExtractorLink(
                                source = serverLabel,
                                name = serverLabel,
                                url = url,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.headers = mapOf("Origin" to Xprime)
                                this.referer = Xprime
                                this.quality = Qualities.Unknown.value
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("XPrimeAPI", "Error on server ${server.name} $e")
            }
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
        val script =
            response.select("script").map { it.data() }.firstOrNull { "qualityOptions" in it }
                ?: return

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
                            this.quality = label.replace(Regex("""[^\d]"""), "").toIntOrNull()
                                ?: Qualities.Unknown.value
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
        val script =
            response.select("script").map { it.data() }.firstOrNull { "qualityOptions" in it }
                ?: return

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
                            this.quality = label.replace(Regex("""[^\d]"""), "").toIntOrNull()
                                ?: Qualities.Unknown.value
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
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val topmoviesAPI = getDomains()?.topMovies ?: return

        val url = if (season == null) {
            "$topmoviesAPI/search/${imdbId.orEmpty()} ${year ?: ""}"
        } else {
            "$topmoviesAPI/search/${imdbId.orEmpty()} Season $season ${year ?: ""}"
        }

        val hrefpattern = runCatching {
            app.get(url).document.select("#content_box article a")
                .firstOrNull()?.attr("href")?.takeIf(String::isNotBlank)
        }.getOrNull() ?: return

        val res = runCatching {
            app.get(
                hrefpattern,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
                ),
                interceptor = wpRedisInterceptor
            ).document
        }.getOrNull() ?: return

        if (season == null) {
            val detailPageUrls = res.select("a.maxbutton-download-links")
                .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }

            for (detailPageUrl in detailPageUrls) {
                val detailPageDocument = runCatching { app.get(detailPageUrl).document }.getOrNull() ?: continue

                val driveLinks = detailPageDocument.select("a.maxbutton-fast-server-gdrive")
                    .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }

                for (driveLink in driveLinks) {
                    val finalLink = if (driveLink.contains("unblockedgames")) {
                        bypassHrefli(driveLink) ?: continue
                    } else {
                        driveLink
                    }

                    loadSourceNameExtractor(
                        "TopMovies",
                        finalLink,
                        "$topmoviesAPI/",
                        subtitleCallback,
                        callback
                    )
                }
            }
        } else {
            val detailPageUrls = res.select("a.maxbutton-g-drive")
                .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }

            for (detailPageUrl in detailPageUrls) {
                val detailPageDocument = runCatching { app.get(detailPageUrl).document }.getOrNull() ?: continue

                val episodeLink = detailPageDocument.select("span strong")
                    .firstOrNull {
                        it.text().matches(Regex(".*Episode\\s+$episode.*", RegexOption.IGNORE_CASE))
                    }
                    ?.parent()?.closest("a")?.attr("href")
                    ?.takeIf(String::isNotBlank) ?: continue

                val finalLink = if (episodeLink.contains("unblockedgames")) {
                    bypassHrefli(episodeLink) ?: continue
                } else {
                    episodeLink
                }

                loadSourceNameExtractor(
                    "TopMovies",
                    finalLink,
                    "$topmoviesAPI/",
                    subtitleCallback,
                    callback
                )
            }
        }
    }




    suspend fun invokeMoviesmod(
        imdbId: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val MoviesmodAPI = getDomains()?.moviesmod ?: return
        invokeModflix(
            imdbId = imdbId,
            year = year,
            season = season,
            episode = episode,
            subtitleCallback = subtitleCallback,
            callback = callback,
            api = MoviesmodAPI
        )
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

        val hrefpattern = app.get(searchUrl).document
            .selectFirst("#content_box article a")
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?: return Log.e("Modflix", "No valid result for $searchUrl")

        val document = runCatching {
            app.get(
                hrefpattern,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
                ),
                interceptor = wpRedisInterceptor
            ).document
        }.getOrElse {
            Log.e("Modflix", "Failed to load page: ${it.message}")
            return
        }

        if (season == null) {
            val detailLinks = document.select("a.maxbutton-download-links")
                .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }

            for (url in detailLinks) {
                val decodedUrl = base64Decode(url.substringAfter("="))
                if (decodedUrl.isBlank()) continue

                val detailDoc = runCatching { app.get(decodedUrl).document }.getOrNull() ?: continue

                val driveLinks = detailDoc.select("a.maxbutton-fast-server-gdrive")
                    .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }

                for (driveLink in driveLinks) {
                    val finalLink = if ("unblockedgames" in driveLink) {
                        bypassHrefli(driveLink) ?: continue
                    } else driveLink

                    loadSourceNameExtractor("MoviesMod", finalLink, "$api/", subtitleCallback, callback)
                }
            }
        } else {
            val seasonPattern = Regex("Season\\s+$season\\b", RegexOption.IGNORE_CASE)

            for (modDiv in document.select("div.mod")) {
                for (h3 in modDiv.select("h3")) {
                    if (!seasonPattern.containsMatchIn(h3.text())) continue

                    val episodeLinks = h3.nextElementSibling()
                        ?.select("a.maxbutton-episode-links")
                        ?.mapNotNull { it.attr("href").takeIf(String::isNotBlank) }
                        ?: emptyList()

                    for (url in episodeLinks) {
                        val decodedUrl = base64Decode(url.substringAfter("="))
                        val detailDoc = runCatching { app.get(decodedUrl).document }.getOrNull() ?: continue

                        val link = detailDoc.select("span strong")
                            .firstOrNull { it.text().contains("Episode $episode", true) }
                            ?.parent()?.closest("a")?.attr("href")
                            ?.takeIf { it.isNotBlank() } ?: continue

                        val driveLink = if (link.startsWith("unblockedgames")) {
                            bypassHrefli(link) ?: continue
                        } else link

                        loadSourceNameExtractor("MoviesMod", driveLink, "$api/", subtitleCallback, callback)
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
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val LuxMoviesAPI = getDomains()?.luxmovies ?: return
        if (LuxMoviesAPI.isNotBlank()) {
            invokeWpredis(
                source = "LuxMovies",
                imdbId = imdbId,
                title = title,
                year = year,
                season = season,
                episode = episode,
                subtitleCallback = subtitleCallback,
                callback = callback,
                api = LuxMoviesAPI
            )
        }
    }


    suspend fun invokeRogmovies(
        imdbId: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val RogMoviesAPI = getDomains()?.rogmovies ?: return
        if (RogMoviesAPI.isNotBlank()) {
            invokeWpredis(
                source = "RogMovies",
                imdbId = imdbId,
                title = title,
                year = year,
                season = season,
                episode = episode,
                subtitleCallback = subtitleCallback,
                callback = callback,
                api = RogMoviesAPI
            )
        }
    }


    suspend fun invokeVegamovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        imdbId: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val vegaMoviesAPI = getDomains()?.vegamovies ?: return
        val cfInterceptor = CloudflareKiller()
        val fixtitle = title?.substringBefore("-")?.substringBefore(":")?.replace("&", " ")?.trim().orEmpty()
        val query = if (season == null) "$fixtitle $year" else "$fixtitle season $season $year"
        val url = "$vegaMoviesAPI/?s=$query"
        val excludedButtonTexts = setOf("Filepress", "GDToT", "DropGalaxy")

        val searchDoc = retry { app.get(url, interceptor = cfInterceptor).document } ?: return
        val articles = searchDoc.select("article h2")
        if (articles.isEmpty()) return

        var foundLinks = false

        for (article in articles) {
            val hrefpattern = article.selectFirst("a")?.attr("href").orEmpty()
            if (hrefpattern.isBlank()) continue

            val doc = retry { app.get(hrefpattern).document } ?: continue

            val imdbAnchor = doc.selectFirst("div.entry-inner p strong a[href*=\"imdb.com/title/tt\"]")
            val imdbHref = imdbAnchor?.attr("href")?.lowercase()

            if (imdbId != null && (imdbHref == null || !imdbHref.contains(imdbId.lowercase()))) {
                Log.i("Skip", "IMDb ID mismatch: $imdbHref != $imdbId")
                continue
            }

            if (season == null) {
                // Movie Mode
                val btnLinks = doc.select("button.dwd-button")
                    .filterNot { btn -> excludedButtonTexts.any { btn.text().contains(it, ignoreCase = true) } }
                    .mapNotNull { it.closest("a")?.attr("href")?.takeIf { link -> link.isNotBlank() } }

                if (btnLinks.isEmpty()) continue

                for (detailUrl in btnLinks) {
                    val detailDoc = retry { app.get(detailUrl).document } ?: continue

                    val streamingLinks = detailDoc.select("button.btn.btn-sm.btn-outline")
                        .filterNot { btn -> excludedButtonTexts.any { btn.text().contains(it, ignoreCase = true) } }
                        .mapNotNull { it.closest("a")?.attr("href")?.takeIf { link -> link.isNotBlank() } }

                    if (streamingLinks.isEmpty()) continue

                    for (streamingUrl in streamingLinks) {
                        loadSourceNameExtractor(
                            "VegaMovies",
                            streamingUrl,
                            "$vegaMoviesAPI/",
                            subtitleCallback,
                            callback
                        )
                        foundLinks = true
                    }
                }

            } else {
                // TV Show Mode
                val seasonPattern = "(?i)(Season $season)"
                val episodePattern = "(?i)(V-Cloud|Single|Episode|G-Direct)"

                val seasonElements = doc.select("h4:matches($seasonPattern), h3:matches($seasonPattern)")

                if (seasonElements.isEmpty()) continue

                for (seasonElement in seasonElements) {
                    val episodeLinks = seasonElement.nextElementSibling()
                        ?.select("a:matches($episodePattern)")
                        ?.mapNotNull { it.attr("href").takeIf { link -> link.isNotBlank() } }
                        ?: continue

                    if (episodeLinks.isEmpty()) continue

                    for (episodeUrl in episodeLinks) {
                        val episodeDoc = retry { app.get(episodeUrl).document } ?: continue

                        val matchBlock = episodeDoc.selectFirst("h4:contains(Episodes):contains($episode)")
                            ?.nextElementSibling()
                            ?.select("a:matches((?i)(V-Cloud|G-Direct|OxxFile))")
                            ?.mapNotNull { it.attr("href").takeIf { link -> link.isNotBlank() } }

                        if (matchBlock.isNullOrEmpty()) continue

                        for (streamingUrl in matchBlock) {
                            loadSourceNameExtractor(
                                "VegaMovies",
                                streamingUrl,
                                "$vegaMoviesAPI/",
                                subtitleCallback,
                                callback
                            )
                            foundLinks = true
                        }
                    }
                }
            }
        }

        if (!foundLinks) {
            Log.d("VegaMovies", "No valid streaming links found for: $title")
            return
        }
    }




    private suspend fun invokeWpredis(
        source: String? = null,
        imdbId: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        api: String
    ) {
        val cfInterceptor = CloudflareKiller()

        suspend fun retry(times: Int = 3, delayMillis: Long = 1000, block: suspend () -> Document): Document? {
            repeat(times - 1) {
                runCatching { return block() }.onFailure { delay(delayMillis) }
            }
            return runCatching { block() }.getOrNull()
        }

        suspend fun searchAndFilter(query: String, fallbackTitle: String? = null): Boolean {
            val url = "$api/$query"
            val doc = retry { app.get(url, interceptor = cfInterceptor).document } ?: return false
            val articles = doc.select("article h3")

            for (article in articles) {
                val h3Text = article.text().trim().lowercase()
                val href = article.selectFirst("a")?.absUrl("href").orEmpty()
                if (href.isBlank()) continue

                val detailDoc = retry { app.get(href, interceptor = cfInterceptor).document } ?: continue

                val matchedHref = detailDoc.select("a[href*=\"imdb.com/title/\"]")
                    .firstOrNull { anchor ->
                        imdbId != null && Regex("tt\\d+").find(anchor.attr("href"))?.value.equals(imdbId, ignoreCase = true)
                    }?.attr("href")

                val matched = matchedHref != null
                val titleMatch = !matched && fallbackTitle != null &&
                        h3Text.contains(fallbackTitle.lowercase().removeSuffix(" $year").trim(), ignoreCase = true)

                if (!matched && !titleMatch) {
                    Log.i("invokeWpredis", "❌ No match in: $href")
                    continue
                }

                Log.i("invokeWpredis", "✅ Matched via ${if (matched) "IMDb ID" else "Title"}: $href")

                if (season == null) {
                    processMovieLinks(source, detailDoc, api, subtitleCallback, callback)
                } else {
                    processSeasonLinks(source, detailDoc, season, episode, api, subtitleCallback, callback)
                }

                return true
            }

            return false
        }

        val imdbQuery = if (season == null) "search/$imdbId" else "search/$imdbId season $season"
        val foundByImdb = !imdbId.isNullOrBlank() && searchAndFilter(imdbQuery)

        if (!foundByImdb && !title.isNullOrBlank()) {
            val titleQuery = buildString {
                append("search/")
                append(title.trim().replace(" ", "+"))
                if (year != null) append("+$year")
                if (season != null) append("+season+$season")
            }
            searchAndFilter(titleQuery, fallbackTitle = title)
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

        val detailPageUrls = doc.select("button.dwd-button")
            .filterNot { button ->
                excludedButtonTexts.any { button.text().contains(it, ignoreCase = true) }
            }
            .mapNotNull { button ->
                button.closest("a")?.attr("href")?.takeIf { it.isNotBlank() }
            }

        for (detailPageUrl in detailPageUrls) {
            runCatching {
                val detailDoc = app.get(detailPageUrl).document
                val streamUrls = detailDoc.select("button.btn.btn-sm.btn-outline")
                    .filterNot { btn ->
                        excludedButtonTexts.any { btn.text().contains(it, ignoreCase = true) }
                    }
                    .mapNotNull { btn ->
                        btn.closest("a")?.attr("href")?.takeIf { it.isNotBlank() }
                    }

                for (streamingUrl in streamUrls) {
                    loadSourceNameExtractor(source ?: "", streamingUrl, "$api/", subtitleCallback, callback)
                }
            }.onFailure {
                Log.e("Error:", "Failed fetching detail page: ${it.localizedMessage}")
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
        val seasonPattern = "(?i)(season\\s*$season|s0?$season\\b)"
        val episodePattern = "(?i)(V-Cloud|Single|Episode|G-Direct|Download Now)"

        val episodeLinks = doc.select("h4:matches($seasonPattern), h3:matches($seasonPattern), h5:matches($seasonPattern)")
            .flatMap { h4 ->
                h4.nextElementSibling()?.select("a:matches($episodePattern)")?.toList() ?: emptyList()
            }

        for (episodeLink in episodeLinks) {
            val episodeUrl = episodeLink.attr("href")
            runCatching {
                val res = app.get(episodeUrl).document
                val streamingUrls = res.selectFirst("h4:contains(Episodes):contains($episode)")
                    ?.nextElementSibling()
                    ?.select("a:matches((?i)(V-Cloud|G-Direct|OXXFile))")
                    ?.mapNotNull { it.attr("href").takeIf { url -> url.isNotBlank() } }
                    ?: return@runCatching

                for (link in streamingUrls) {
                    loadSourceNameExtractor(source ?: "", link, "$api/", subtitleCallback, callback)
                }
            }.onFailure {
                Log.e("Error:", "Failed to fetch episode details: ${it.localizedMessage}")
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
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val extramovies = getDomains()?.extramovies ?: return
        val url = "$extramovies/search/$imdbId"
        app.get(url).document.select("h3 a").amap {
            val link = it.attr("href")

            app.get(link).document.select("div.entry-content a.maxbutton-8").map { it ->
                val href = it.select("a").attr("href")
                val detailDoc = app.get(href).document
                if (season == null) {
                    processMovieLinks("Extramovies", detailDoc, url, subtitleCallback, callback)
                } else {
                    processSeasonLinks(
                        "Extramovies",
                        detailDoc,
                        season,
                        episode,
                        url,
                        subtitleCallback,
                        callback
                    )
                }
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
                val paddedHash = embedData.hash.padEnd((embedData.hash.length + 3) / 4 * 4, '=')
                val hashDecoded = base64Decode(paddedHash)
                val reversedTransformed = hashDecoded
                    .split(".")
                    .joinToString("") { it.reversed() }
                    .reversed()
                val finalInput = reversedTransformed.padEnd((reversedTransformed.length + 3) / 4 * 4, '=')
                base64Decode(finalInput)
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


    @SuppressLint("NewApi")
    suspend fun invokeVidsrccc(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$vidsrctoAPI/v2/embed/movie/$id?autoPlay=false"
        } else {
            "$vidsrctoAPI/v2/embed/tv/$id/$season/$episode?autoPlay=false"
        }
        val doc = app.get(url).document.toString()
        val regex = Regex("""var\s+(\w+)\s*=\s*(?:"([^"]*)"|(\w+));""")
        val variables = mutableMapOf<String, String>()

        regex.findAll(doc).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            variables[key] = value
        }
        val vvalue = variables["v"] ?: ""
        val userId = variables["userId"] ?: ""
        val imdbId = variables["imdbId"] ?: ""
        val movieId = variables["movieId"] ?: ""
        val movieType = variables["movieType"] ?: ""

        val vrf = generateVidsrcVrf(movieId,userId)
        val apiurl = if (season == null) {
            "${vidsrctoAPI}/api/$id/servers?id=$id&type=$movieType&v=$vvalue=&vrf=$vrf&imdbId=$imdbId"
        } else {
            "${vidsrctoAPI}/api/$id/servers?id=$id&type=$movieType&season=$season&episode=$episode&v=$vvalue&vrf=${vrf}&imdbId=$imdbId"
        }
        app.get(apiurl).parsedSafe<Vidsrcccservers>()?.data?.forEach {
            val servername = it.name
            val iframe = app.get("$vidsrctoAPI/api/source/${it.hash}")
                .parsedSafe<Vidsrcccm3u8>()?.data?.source
            val sourceUrl = iframe?.let { iframeUrl ->
                val response = app.get(iframeUrl, referer = vidsrctoAPI).text
                val urlregex = Regex("""var\s+source\s*=\s*"([^"]+)"""")
                val match = urlregex.find(response)
                match?.groups?.get(1)?.value?.replace("""\\/""".toRegex(), "/")
            }

            sourceUrl?.let { url->
                loadCustomExtractor(
                    "⌜ Vidsrc ⌟ | [$servername]",
                    url,
                    vidsrctoAPI,
                    subtitleCallback,
                    callback
                )
            }
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
        val doc = app.get(url).text

        val fixedServersRaw = doc.substringAfter("const fixedServers = ")
            .substringBefore(";")
            .trim()

        val multiLangRaw = doc.substringAfter("const MultiLang = ")
            .substringBefore(";")
            .trim()

        val raw = fixedServersRaw.ifEmpty { multiLangRaw }
        val regex = Regex("""url:\s*'([^']+\.m3u8)'""")
        val matches = regex.findAll(raw).map { it.groupValues[1] }.toList()

        matches.forEachIndexed { index, m3u8 ->
            M3u8Helper.generateM3u8(
                "VidsrcSU ${index + 1}",
                m3u8,
                referer = "$mainUrl/"
            ).forEach(callback)
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
        api: String = "https://parse.showflix.sbs"
    ) {
        val where = if (season == null) "movieName" else "seriesName"
        val classes = if (season == null) "moviesv2" else "seriesv2"
        val body = """
    {
        "where": {
            "name": {
                "${'$'}regex": "$title",
                "${'$'}options": "i"
            }
        },
        "order": "-createdAt",
        "_method": "GET",
        "_ApplicationId": "SHOWFLIXAPPID",
        "_JavaScriptKey": "SHOWFLIXMASTERKEY",
        "_ClientVersion": "js3.4.1",
        "_InstallationId": "60f6b1a7-8860-4edf-b255-6bc465b6c704"
    }
""".trimIndent().toRequestBody("text/plain".toMediaTypeOrNull())


        val data =
            app.post("$api/parse/classes/$classes", requestBody = body).text
        val iframes = if (season == null) {
            val result = tryParseJson<ShowflixSearchMovies>(data)?.resultsMovies?.find {
                it.name.equals("$title ($year)", ignoreCase = true) ||
                        it.name.equals(title, ignoreCase = true)
            }
            val streamwish = result?.embedLinks?.get("streamwish")
            val filelions = result?.embedLinks?.get("filelions")
            val streamruby = result?.embedLinks?.get("streamruby")
            val upnshare = result?.embedLinks?.get("upnshare")
            val vihide = result?.embedLinks?.get("vihide")

            listOf(
                "https://embedwish.com/e/$streamwish",
                "https://filelions.to/v/$filelions.html",
                "https://rubyvidhub.com/embed-$streamruby.html",
                "https://showflix.upns.one/#$upnshare",
                "https://smoothpre.com/v/$vihide.html"
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


    private suspend fun <T> retry(
        times: Int = 3,
        delayMillis: Long = 1000,
        block: suspend () -> T
    ): T? {
        repeat(times - 1) {
            runCatching { return block() }.onFailure { delay(delayMillis) }
        }
        return runCatching { block() }.getOrNull()
    }

    suspend fun invokeMoviesdrive(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        id: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val movieDriveAPI = getDomains()?.moviesdrive ?: return
        val cleanTitle = title.orEmpty()

        val searchUrl = buildString {
            append("$movieDriveAPI/?s=$cleanTitle")
            if (season != null && !cleanTitle.contains(season.toString(), ignoreCase = true)) {
                append(" $season")
            } else if (season == null && year != null) {
                append(" $year")
            }
        }
        val figures = retry {
            val allFigures =
                app.get(searchUrl, interceptor = wpRedisInterceptor).document.select("figure")
            if (season == null) {
                allFigures
            } else {
                val seasonPattern = Regex("""season\s*${season}\b""", RegexOption.IGNORE_CASE)
                allFigures.filter { figure ->
                    val img = figure.selectFirst("img")
                    val alt = img?.attr("alt").orEmpty()
                    val titleAttr = img?.attr("title").orEmpty()
                    seasonPattern.containsMatchIn(alt) || seasonPattern.containsMatchIn(titleAttr)
                }
            }
        } ?: return

        for (figure in figures) {
            val detailUrl = figure.selectFirst("a[href]")?.attr("href").orEmpty()

            if (detailUrl.isBlank()) continue

            val detailDoc = retry {
                app.get(detailUrl, interceptor = wpRedisInterceptor).document
            } ?: continue

            val imdbId = detailDoc
                .select("a[href*=\"imdb.com/title/\"]")
                .firstOrNull()
                ?.attr("href")
                ?.substringAfter("title/")
                ?.substringBefore("/")
                ?.takeIf { it.isNotBlank() }
            Log.d("Phisher",imdbId.toString())

            val titleMatch = imdbId == id.orEmpty() || detailDoc
                .select("main > p:nth-child(10),p strong:contains(Movie Name:) + span")
                .firstOrNull()
                ?.text()
                ?.contains(cleanTitle, ignoreCase = true) == true

            if (!titleMatch) continue

            if (season == null) {
                val links = detailDoc.select("h5 a")

                for (element in links) {
                    val urls = retry { extractMdrive(element.attr("href")) } ?: continue
                    for (serverUrl in urls) {
                        processMoviesdriveUrl(serverUrl, subtitleCallback, callback)
                    }
                }
            } else {
                val seasonPattern = "(?i)Season\\s*0?$season\\b|S0?$season\\b"
                val episodePattern =
                    "(?i)Ep\\s?0?$episode\\b|Episode\\s+0?$episode\\b|V-Cloud|G-Direct|OXXFile"

                val seasonElements = detailDoc.select("h5:matches($seasonPattern)")
                if (seasonElements.isEmpty()) continue

                val allLinks = mutableListOf<String>()

                for (seasonElement in seasonElements) {
                    val seasonHref = seasonElement.nextElementSibling()
                        ?.selectFirst("a")
                        ?.attr("href")
                        ?.takeIf { it.isNotBlank() } ?: continue

                    val episodeDoc = retry { app.get(seasonHref).document } ?: continue
                    val episodeHeaders = episodeDoc.select("h5:matches($episodePattern)")

                    for (header in episodeHeaders) {

                        val siblingLinks =
                            generateSequence(header.nextElementSibling()) { it.nextElementSibling() }
                                .takeWhile { it.tagName() != "hr" }
                                .filter { it.tagName() == "h5" }
                                .mapNotNull { h5 ->
                                    h5.selectFirst("a")?.takeIf { a ->
                                        !a.text()
                                            .contains("Zip", ignoreCase = true) && a.hasAttr("href")
                                    }?.attr("href")
                                }.toList()

                        allLinks.addAll(siblingLinks)
                    }
                }
                if (allLinks.isNotEmpty()) {
                    for (serverUrl in allLinks) {
                        processMoviesdriveUrl(serverUrl, subtitleCallback, callback)
                    }
                } else {
                    detailDoc.select("h5 a:contains(HubCloud)")
                        .mapNotNull { it.attr("href").takeIf { href -> href.isNotBlank() } }
                        .forEach { fallbackUrl ->
                            processMoviesdriveUrl(fallbackUrl, subtitleCallback, callback)
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
                GDFlix().getUrl(serverUrl, referer = "MoviesDrive",
                )
            }
            else -> {
                loadExtractor(serverUrl, referer = "MoviesDrive", subtitleCallback, callback)
            }
        }
    }

    suspend fun invokeBollyflix(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val bollyflixAPI = getDomains()?.bollyflix ?: return

        val searchQuery = buildString {
            append(id ?: return)
            if (season != null) append(" $season")
        }

        val searchUrl = "$bollyflixAPI/search/$searchQuery"
        val searchDoc = retryIO { app.get(searchUrl, interceptor = wpRedisInterceptor).document }
        val contentUrl = searchDoc.selectFirst("div > article > a")?.attr("href") ?: return
        val contentDoc = retryIO { app.get(contentUrl).document }

        val hTag = if (season == null) "h5" else "h4"
        val sTag = if (season != null) "Season $season" else ""
        val entries = contentDoc
            .select("div.thecontent.clearfix > $hTag:matches((?i)$sTag.*(720p|1080p|2160p))")
            .filterNot { it.text().contains("Download", ignoreCase = true) }
            .takeLast(4)

        for (entry in entries) {
            val href = entry.nextElementSibling()?.selectFirst("a")?.attr("href") ?: continue
            val token = href.substringAfter("id=", "")
            if (token.isEmpty()) continue

            val encodedUrl = retryIO {
                app.get("https://blog.finzoox.com/?id=$token").text
                    .substringAfter("link\":\"")
                    .substringBefore("\"};")
            }

            val decodedUrl = base64Decode(encodedUrl)

            if (season == null) {
                val redirectUrl = retryIO {
                    app.get(decodedUrl, allowRedirects = false).headers["location"].orEmpty()
                }

                if ("gdflix" in redirectUrl.lowercase()) {
                    GDFlix().getUrl(redirectUrl, "BollyFlix", subtitleCallback, callback)
                }
                loadSourceNameExtractor("Bollyflix", redirectUrl, "", subtitleCallback, callback)

            } else {
                val episodeSelector = "article h3 a:contains(Episode 0$episode)"
                val episodeLink = retryIO {
                    app.get(decodedUrl).document
                        .selectFirst(episodeSelector)?.attr("href")
                } ?: continue

                val redirectUrl = retryIO {
                    app.get(episodeLink, allowRedirects = false).headers["location"].orEmpty()
                }

                if ("gdflix" in redirectUrl.lowercase()) {
                    GDFlix().getUrl(redirectUrl, "BollyFlix", subtitleCallback, callback)
                } else {
                    loadSourceNameExtractor("Bollyflix", episodeLink, "", subtitleCallback, callback)
                }
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
        val fixtitle = title.createSlug()
        val juiceHeaders = mapOf(
            "Referer" to "https://turbovid.eu",
            "X-Turbo" to "TurboVidClient",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        )

        val juicyKey = runCatching {
            app.get(BuildConfig.CatflixAPI, headers = juiceHeaders)
                .parsedSafe<CatflixJuicy>()
                ?.juice
        }.getOrNull().orEmpty()

        if (juicyKey.isEmpty()) return

        val href = if (season == null) {
            "$Catflix/movie/$fixtitle-$id"
        } else {
            "$Catflix/episode/${fixtitle}-season-${season}-episode-${episode}/eid-$epid"
        }

        val pageHtml = runCatching {
            app.get(href, referer = Catflix).toString()
        }.getOrElse {
            val proxyUrl = "https://catflix.catflixphisher.workers.dev/?url=$href"
            runCatching {
                app.get(proxyUrl, referer = Catflix).toString()
            }.getOrNull()
        } ?: return

        val iframe = Regex("""(?:const|let)\s+main_origin\s*=\s*"(.*)";""")
            .find(pageHtml)
            ?.groupValues?.getOrNull(1)
            ?.let(::base64Decode) ?: return

        val iframeHtml = runCatching {
            app.get(iframe, referer = Catflix).toString()
        }.getOrNull() ?: return

        val apkey = extractcatflixValue(iframeHtml, "apkey") ?: return
        val xxid = extractcatflixValue(iframeHtml, "xxid") ?: return

        val juiceUrl = "https://turbovid.eu/api/cucked/the_juice/?$apkey=$xxid"

        val juiceData = runCatching {
            app.get(juiceUrl, headers = juiceHeaders, referer = juiceUrl)
                .parsedSafe<CatflixJuicydata>()
                ?.data
        }.getOrNull().orEmpty()

        if (juiceData.isEmpty()) return

        val finalUrl = runCatching {
            catdecryptHexWithKey(juiceData, juicyKey)
        }.getOrNull() ?: return

        val headers = mapOf(
            "Origin" to "https://turbovid.eu/",
            "Connection" to "keep-alive"
        )

        callback(
            newExtractorLink("Catflix", "Catflix", url = finalUrl, INFER_TYPE) {
                referer = "https://turbovid.eu/"
                quality = Qualities.P1080.value
                this.headers = headers
            }
        )
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
        val s = if (season != 1) "-season-$season" else ""
        val url =
            "$Dramacool/stream/series/$provider-${titleSlug}${s}::$titleSlug${s}-ep-$episode.json"
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

    suspend fun invokeFlixAPIHQ(
        title: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (title.isNullOrBlank()) return

        val type = if (season == null) "Movie" else "TV Series"
        val searchUrl = "$consumetFlixhqAPI/$title"

        val searchData = runCatching {
            val searchJson = app.get(searchUrl, timeout = 120L).text
            tryParseJson<ConsumetSearch>(searchJson)
        }.getOrNull() ?: return

        val id = searchData.results.firstOrNull {
            it.title.equals(title, ignoreCase = true) && it.type.equals(type, ignoreCase = true)
        }?.id ?: return

        val infoUrl = "$consumetFlixhqAPI/info?id=$id"
        val infoData = runCatching {
            val infoJson = app.get(infoUrl, timeout = 120L).text
            tryParseJson<ConsumetInfo>(infoJson)
        }.getOrNull() ?: return

        val episodeId = if (season == null) {
            infoData.episodes.firstOrNull()?.id
        } else {
            infoData.episodes.firstOrNull { it.number == episode && it.season == season }?.id
        } ?: return

        val serversUrl = "$consumetFlixhqAPI/servers?episodeId=$episodeId&mediaId=$id"
        val serverListJson = app.get(serversUrl, timeout = 120L).text
        val serverList = parseServerList(serverListJson)

        serverList.amap { server ->
            val sourceUrl = runCatching {
                val endpoint = server.url.substringAfterLast(".")
                val proxyUrl = "https://proxy.phisher2.workers.dev/?url=$FlixHQ/ajax/episode/sources/$endpoint"
                app.get(proxyUrl, timeout = 3000).parsedSafe<FlixHQLinks>()?.link
            }.getOrNull()
            sourceUrl?.let {
                loadCustomExtractor(
                    "⌜ FlixHQ ⌟ | ${server.name.uppercase()}",
                    it,
                    "",
                    subtitleCallback,
                    callback
                )
            }
        }
    }

    data class FlixHQServers(
        val name: String,
        val url: String
    )

    data class FlixHQLinks(
        val type: String,
        val link: String,
    )


    private fun parseServerList(json: String): List<FlixHQServers> = runCatching {
        val jsonArray = JSONArray(json)
        List(jsonArray.length()) { i ->
            val obj = jsonArray.getJSONObject(i)
            FlixHQServers(
                name = obj.getString("name"),
                url = obj.getString("url")
            )
        }
    }.getOrElse {
        it.printStackTrace()
        emptyList()
    }


    @SuppressLint("NewApi")
    suspend fun invokeRiveStream(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf("User-Agent" to USER_AGENT)

        suspend fun <T> retry(times: Int = 3, block: suspend () -> T): T? {
            repeat(times - 1) {
                try {
                    return block()
                } catch (_: Exception) {}
            }
            return try {
                block()
            } catch (_: Exception) {
                null
            }
        }

        val sourceApiUrl = "$RiveStreamAPI/api/backendfetch?requestID=VideoProviderServices&secretKey=rive"
        val sourceList = retry { app.get(sourceApiUrl, headers).parsedSafe<RiveStreamSource>() }

        val document = retry { app.get(RiveStreamAPI, headers, timeout = 20).document } ?: return
        val appScript = document.select("script")
            .firstOrNull { it.attr("src").contains("_app") }?.attr("src") ?: return

        val js = retry { app.get("$RiveStreamAPI$appScript").text } ?: return
        val keyList = Regex("""let\s+c\s*=\s*(\[[^]]*])""")
            .findAll(js).firstOrNull { it.groupValues[1].length > 2 }?.groupValues?.get(1)
            ?.let { array ->
                Regex("\"([^\"]+)\"").findAll(array).map { it.groupValues[1] }.toList()
            } ?: emptyList()

        val secretKey = retry {
            app.get(
                "https://rivestream.supe2372.workers.dev/?input=$id&cList=${keyList.joinToString(",")}"
            ).text
        } ?: return

        sourceList?.data?.forEach { source ->
            try {
                val streamUrl = if (season == null) {
                    "$RiveStreamAPI/api/backendfetch?requestID=movieVideoProvider&id=$id&service=$source&secretKey=$secretKey"
                } else {
                    "$RiveStreamAPI/api/backendfetch?requestID=tvVideoProvider&id=$id&season=$season&episode=$episode&service=$source&secretKey=$secretKey"
                }

                val sourceJson = retry {
                    app.get(streamUrl, headers, timeout = 10).parsedSafe<RiveStreamResponse>()
                } ?: return@forEach

                sourceJson.data.sources.forEach { src ->
                    try {
                        val label = "RiveStream ${src.source} ${src.quality}"
                        val quality = Qualities.P1080.value

                        if (src.url.contains("m3u8-proxy?url")) {
                            val decodedUrl = URLDecoder.decode(
                                src.url.substringAfter("m3u8-proxy?url=").substringBefore("&headers="),
                                "UTF-8"
                            )

                            callback(newExtractorLink(label, label, decodedUrl, ExtractorLinkType.M3U8) {
                                this.referer = "https://megacloud.store/"
                                this.quality = quality
                            })
                        } else {
                            val type = if (src.url.contains(".m3u8", ignoreCase = true))
                                ExtractorLinkType.M3U8 else INFER_TYPE

                            callback(newExtractorLink("$label (VLC)", "$label (VLC)", src.url, type) {
                                this.referer = ""
                                this.quality = quality
                            })
                        }
                    } catch (e: Exception) {
                        Log.e("RiveStreamSourceError", "Source parse failed: ${src.url} $e")
                    }
                }
            } catch (e: Exception) {
                Log.e("RiveStreamError", "Failed service: $source $e")
            }
        }
    }




    suspend fun invokeVidSrcViP(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
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


    @Suppress("NAME_SHADOWING")
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
            } catch (_: Exception) {
            }
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
        val queryWithEpisode = season?.let { "$fixTitle S${"%02d".format(it)}E${"%02d".format(episode)}" }
        val baseQuery = queryWithEpisode ?: "$fixTitle $year"
        val encodedQuery = baseQuery.replace(" ", "+")

        val allLinks = linkedSetOf<Player4uLinkData>()
        var page = 0

        while (true) {
            val url = "$Player4uApi/embed?key=$encodedQuery" + if (page > 0) "&page=$page" else ""
            val document = runCatching { app.get(url, timeout = 20).document }.getOrNull()

            if (document == null) break

            allLinks += extractPlayer4uLinks(document)

            if (page == 0 && season == null && allLinks.isEmpty()) {
                val fallbackUrl = "$Player4uApi/embed?key=${fixTitle.replace(" ", "+")}"
                val fallbackDoc = runCatching { app.get(fallbackUrl, timeout = 20).document }.getOrNull()
                if (fallbackDoc != null) {
                    allLinks += extractPlayer4uLinks(fallbackDoc)
                }
                break
            }

            val hasNext = document.select("div a").any { it.text().contains("Next", true) }
            if (!hasNext || page >= 4) break

            page++
        }

        for (link in allLinks.distinctBy { it.name }) {
            try {
                val namePart = link.name.split("|").lastOrNull()?.trim().orEmpty()
                val displayName = buildString {
                    append("Player4U")
                    if (namePart.isNotEmpty()) append(" {$namePart}")
                }

                val qualityMatch = Regex(
                    """(\d{3,4}p|4K|CAM|HQ|HD|SD|WEBRip|DVDRip|BluRay|HDRip|TVRip|HDTC|PREDVD)""",
                    RegexOption.IGNORE_CASE
                ).find(displayName)?.value?.uppercase() ?: "UNKNOWN"

                val quality = getPlayer4UQuality(qualityMatch)
                val subPath = Regex("""go\('(.*?)'\)""").find(link.url)?.groupValues?.get(1) ?: continue

                val iframeSrc = runCatching {
                    app.get("$Player4uApi$subPath", timeout = 10, referer = Player4uApi)
                        .document.selectFirst("iframe")?.attr("src")
                }.getOrNull() ?: continue

                getPlayer4uUrl(
                    displayName,
                    quality,
                    "https://uqloads.xyz/e/$iframeSrc",
                    Player4uApi,
                    callback
                )
            } catch (_: Exception) {
                continue
            }
        }
    }


    private fun extractPlayer4uLinks(document: Document): List<Player4uLinkData> {
        return document.select(".playbtnx").map {
            Player4uLinkData(name = it.text(), url = it.attr("onclick"))
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


    suspend fun invoke4khdhub(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (title.isNullOrBlank()) return

        val baseUrl = runCatching { getDomains()?.n4khdhub }.getOrNull() ?: return
        val searchUrl = "$baseUrl/?s=${title.trim().replace(" ", "+")}"

        val searchDoc = runCatching { app.get(searchUrl).document }.getOrNull() ?: return
        val normalizedTitle = title.lowercase().trim()

        val postLink = searchDoc.select("div.card-grid > a.movie-card")
            .firstOrNull { card ->
                val titleText = card.selectFirst("div.movie-card-content > h3")
                    ?.text()?.trim()?.lowercase() ?: return@firstOrNull false

                val metaText = card.selectFirst("div.movie-card-content > p.movie-card-meta")
                    ?.text()?.trim() ?: return@firstOrNull false

                val titleMatch = titleText == normalizedTitle ||
                        titleText.contains(" $normalizedTitle ") ||
                        titleText.contains("$normalizedTitle:")

                val yearMatch = if (season == null) {
                    year?.let { metaText.contains(it.toString()) } ?: true
                } else true

                titleMatch && yearMatch
            }?.attr("href") ?: return

        val doc = runCatching { app.get("$baseUrl$postLink").document }.getOrNull() ?: return

        val links = if (season == null) {
            doc.select("div.download-item a")
        } else {
            val seasonText = "S${season.toString().padStart(2, '0')}"
            val episodeText = "E${episode.toString().padStart(2, '0')}"
            doc.select("div.episode-download-item:has(div.episode-file-title:contains(${seasonText}${episodeText}))")
                .flatMap { it.select("div.episode-links > a") }
        }

        for (element in links) {
            val rawHref = element.attr("href")
            if (rawHref.isBlank()) continue

            val link = runCatching { hdhubgetRedirectLinks(rawHref) }.getOrNull() ?: continue
            dispatchToExtractor(link, "4Khdhub", subtitleCallback, callback)
        }
    }


    suspend fun invokeElevenmovies(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$Elevenmovies/movie/$id"
        } else {
            "$Elevenmovies/tv/$id/$season/$episode"
        }

        val encodedToken = app.get(url).document.selectFirst("script[type=application/json]")
            ?.data()
            ?.substringAfter("{\"data\":\"")
            ?.substringBefore("\",")

        checkNotNull(encodedToken) { "❌ Encoded token not found. Check if the page structure has changed." }

        val jsonUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/main/output.json"
        val jsonString = try {
            app.get(jsonUrl).text
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("❌ Failed to fetch JSON from GitHub: $jsonUrl")
        }

        println("✅ Fetched JSON from GitHub")

        val gson = Gson()
        val json: Elevenmoviesjson = try {
            gson.fromJson(jsonString, Elevenmoviesjson::class.java)
        } catch (e: JsonSyntaxException) {
            e.printStackTrace()
            throw RuntimeException("❌ Failed to parse Elevenmovies JSON")
        }

        val token = elevenMoviesTokenV2(encodedToken)
        val staticPath = json.static_path
        val apiServerUrl = "$Elevenmovies/$staticPath/${token}/sr"

        val headers = mapOf(
            "Referer" to Elevenmovies,
            "Content-Type" to json.content_types,
        )

        val responseString = try {
            if (json.http_method == "GET") {
                app.get(apiServerUrl, headers = headers).body.string()
            } else {
                val postHeaders = headers + mapOf("X-Requested-With" to "XMLHttpRequest")
                val mediaType = json.content_types.toMediaType()
                val requestBody = "".toRequestBody(mediaType)
                app.post(apiServerUrl, headers = postHeaders, requestBody = requestBody).body.string()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("❌ Failed to fetch server list from Elevenmovies")
        }

        val listType = object : TypeToken<List<ElevenmoviesServerEntry>>() {}.type
        val serverList: List<ElevenmoviesServerEntry> = try {
            gson.fromJson(responseString, listType)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("❌ Failed to parse server list")
        }

        for (entry in serverList) {
            val serverToken = entry.data
            val serverName = entry.name
            val streamApiUrl = "$Elevenmovies/$staticPath/$serverToken"

            val streamResponseString = try {
                if (json.http_method == "GET") {
                    app.get(streamApiUrl, headers = headers).body.string()
                } else {
                    val postHeaders = mapOf(
                        "Referer" to Elevenmovies,
                        "Content-Type" to "application/vnd.api+json",
                        "X-Requested-With" to "XMLHttpRequest"
                    )
                    val mediaType = "application/vnd.api+json".toMediaType()
                    val requestBody = "".toRequestBody(mediaType)
                    app.post(streamApiUrl, headers = postHeaders, requestBody = requestBody).body.string()
                }
            } catch (e: Exception) {
                println("⚠️ Failed to fetch stream for server $serverName")
                e.printStackTrace()
                continue
            }

            val streamRes = try {
                gson.fromJson(streamResponseString, ElevenmoviesStreamResponse::class.java)
            } catch (e: Exception) {
                println("⚠️ Failed to parse stream response for server $serverName")
                e.printStackTrace()
                continue
            }

            val videoUrl = streamRes?.url ?: continue

            M3u8Helper.generateM3u8(
                "Eleven Movies $serverName",
                videoUrl,
                ""
            ).forEach(callback)

            streamRes.tracks?.forEach { sub ->
                val label = sub.label ?: return@forEach
                val file = sub.file ?: return@forEach
                subtitleCallback.invoke(SubtitleFile(label, file))
            }
        }
    }



    @SuppressLint("NewApi")
    suspend fun invokehdhub4u(
        imdbId: String?,
        title: String?,
        year: Int?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val baseUrl = runCatching { getDomains()?.hdhub4u }.getOrNull() ?: return
        if (title.isNullOrBlank()) return

        val query = buildString {
            append(title)
            when {
                season != null -> append(" season $season")
                year != null -> append(" $year")
            }
        }.replace(" ", "+")

        val searchUrl = "$baseUrl/?s=$query"
        val searchDoc = runCatching { app.get(searchUrl).document }.getOrNull() ?: return

        val normalizedTitle = title.lowercase().replace(Regex("[^a-z0-9]"), "")
        val seasonStr = season?.toString()

        val posts = searchDoc.select("ul.recent-movies li.thumb").filter { li ->
            val text = li.selectFirst("figcaption p")?.text()?.lowercase().orEmpty()
            val cleanText = text.replace(Regex("[^a-z0-9]"), "")
            when {
                season == null && year != null -> cleanText.contains(normalizedTitle) && text.contains(year.toString())
                season != null -> cleanText.contains(normalizedTitle) &&
                        text.contains("season", true) &&
                        text.contains("season $seasonStr", true)
                else -> cleanText.contains(normalizedTitle)
            }
        }.mapNotNull { li -> li.selectFirst("figcaption a") }

        val matchedPosts = if (!imdbId.isNullOrBlank()) {
            val matched = posts.mapNotNull { post ->
                val postUrl = post.absUrl("href")
                val postDoc = runCatching { app.get(postUrl).document }.getOrNull() ?: return@mapNotNull null
                val imdbLink = postDoc.selectFirst("div.kp-hc a[href*=\"imdb.com/title/$imdbId\"]")?.attr("href")
                val matchedImdbId = imdbLink?.substringAfterLast("/tt")?.substringBefore("/")?.let { "tt$it" }
                if (matchedImdbId == imdbId) post else null
            }
            matched.ifEmpty { posts }
        } else posts

        for (el in matchedPosts) {
            val postUrl = el.absUrl("href")
            val doc = runCatching { app.get(postUrl).document }.getOrNull() ?: continue

            if (season == null) {
                val qualityLinks = doc.select("h3 a:matches(480|720|1080|2160|4K), h4 a:matches(480|720|1080|2160|4K)")
                for (linkEl in qualityLinks) {
                    val resolvedLink = linkEl.attr("href")
                    val resolvedWatch = if ("id=" in resolvedLink) hdhubgetRedirectLinks(resolvedLink) else resolvedLink
                    dispatchToExtractor(resolvedWatch, "HDhub4u", subtitleCallback, callback)
                }
            } else {
                val episodeRegex = Regex("episode\\s*(\\d+)", RegexOption.IGNORE_CASE)
                val h3s = doc.select("h3")

                for (h3 in h3s) {
                    val links = h3.select("a[href]")
                    val episodeLink = links.find { it.text().contains("episode", true) }
                    val watchLink = links.find { it.text().equals("watch", true) }

                    val episodeNum = episodeRegex.find(episodeLink?.text().orEmpty())
                        ?.groupValues?.getOrNull(1)?.toIntOrNull()

                    if (episodeNum != null && (episode == null || episode == episodeNum)) {
                        episodeLink?.absUrl("href")?.let { href ->
                            val resolved = if ("id=" in href) hdhubgetRedirectLinks(href) else href
                            val episodeDoc = runCatching { app.get(resolved).document }.getOrNull() ?: return@let

                            episodeDoc.select("h3 a[href], h4 a[href], h5 a[href]")
                                .mapNotNull { it.absUrl("href").takeIf { url -> url.isNotBlank() } }
                                .forEach { link ->
                                    val resolvedWatch = if ("id=" in link) hdhubgetRedirectLinks(link) else link
                                    dispatchToExtractor(resolvedWatch, "HDhub4u", subtitleCallback, callback)
                                }
                        }

                        watchLink?.absUrl("href")?.let { watchHref ->
                            val resolvedWatch = if ("id=" in watchHref) hdhubgetRedirectLinks(watchHref) else watchHref
                            loadSourceNameExtractor("HDhub4u", resolvedWatch, "", subtitleCallback, callback)
                        }
                    }
                }
            }
        }
    }

    //TODO
    suspend fun invokeHdmovie2(
        title: String? = null,
        year: Int? = null,
        season: Int?=null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val hdmovie2API = getDomains()?.hdmovie2 ?: return
        val slug = title?.createSlug() ?: return
        val url = "$hdmovie2API/movies/$slug-$year"

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        )

        val document = app.get(url, headers = headers, allowRedirects = true).document

        document.selectFirst("div.wp-content p a")?.map { linkElement ->
            val linkText = linkElement.text()
            val linkUrl = linkElement.attr("href")
            val isEpisodeMatch = episode?.let {
                Regex("EP0?$it\\b", RegexOption.IGNORE_CASE).containsMatchIn(linkText)
            } ?: true

            if (!isEpisodeMatch && episode != null && linkText.contains("EP")) {
                Log.d("Hdmovie2", "Episode $episode not matched in link: $linkText")
                return@map
            }

            val type = if (episode != null && !linkText.contains("EP")) "(Combined)" else ""

            /*
            app.get(linkUrl).document.select("div > p > a").amap {
                if (it.text().contains("GDFlix"))
                {
                    val href = it.attr("href")
                    var redirectedUrl: String? = null
                    val gg = app.get(href).url
                    Log.d("gg", "Success on attempt ${gg + 1}gg")

                    repeat(10) { attempt ->
                        val response = app.get(href, allowRedirects = false)
                        redirectedUrl = response.headers["location"]
                        if (!redirectedUrl.isNullOrEmpty()) {
                            Log.d("Retry", "Success on attempt ${attempt + 1}")
                            return@repeat
                        }
                        Log.d("Retry", "Attempt ${attempt + 1}: No location header found")
                    }
                    redirectedUrl = redirectedUrl ?: ""
                    Log.d("Phisher", redirectedUrl!!)

                    loadSourceNameExtractor(
                        "Hdmovie2 $type",
                        redirectedUrl!!,
                        "",
                        subtitleCallback,
                        callback,
                    )
                }
            }

             */
        }
    }

    suspend fun invokeDramadrip(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val dramadripAPI = getDomains()?.dramadrip ?: return
        val link = app.get("$dramadripAPI/?s=$imdbId").document.selectFirst("article > a")?.attr("href") ?: return
        val document = app.get(link).document
        if(season != null && episode != null) {
            val seasonLink = document.select("div.file-spoiler h2").filter { element ->
                val text = element.text().trim().lowercase()
                "season $season ".lowercase() in text && "zip" !in text
            }.flatMap { h2 ->
                val sibling = h2.nextElementSibling()
                sibling?.select("a")?.mapNotNull { it.attr("href") } ?: emptyList()
            }

            seasonLink.amap { seasonUrl ->
                val episodeDoc = app.get(seasonUrl).document

                val episodeHref = episodeDoc.select("h3 > a,div.wp-block-button a")
                    .firstOrNull { it.text().contains("Episode $episode") }
                    ?.attr("href")
                    ?: return@amap
                val finalUrl = if ("unblockedgames" in episodeHref) { bypassHrefli(episodeHref) } else { episodeHref }
                if (finalUrl != null) {
                    loadSourceNameExtractor("DramaDrip", finalUrl, "", subtitleCallback, callback)
                }
            }
        } else {
            document.select("div.file-spoiler a").amap {
                val doc = app.get(it.attr("href")).document
                doc.select("a.wp-element-button").amap { source ->
                    val finalUrl = if ("unblockedgames" in source.attr("href")) { bypassHrefli(source.attr("href")) } else { source.attr("href") }
                    if (finalUrl != null) {
                        loadSourceNameExtractor(
                            "DramaDrip",
                            finalUrl,
                            "",
                            subtitleCallback,
                            callback
                        )
                    }

                }
            }
        }
    }

    // Credit: https://github.com/yogesh-hacker/MediaVanced/blob/main/sites/vidfast.py
    suspend fun invokeVidfast(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$Vidfast/movie/$imdbId"
        } else {
            "$Vidfast/tv/$imdbId/$season/$episode"
        }

        val regexData = app.get(url).text
        val regex = Regex("""\\"en\\":\\"(.*?)\\""")

        val rawData = regex.find(regexData)?.groupValues?.getOrNull(1)
            ?: return println("❌ No match found.")
        val vidFastKey = app.get("https://raw.githubusercontent.com/phisher98/TVVVV/main/Vidfast.json")
            .parsedSafe<VidFastkey>()

        val keyHex = vidFastKey?.keyHex ?: "Key Hex not Found"
        val ivHex = vidFastKey?.ivHex ?: "Key IV Hex not Found"

        val aesKey = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val aesIv = ivHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(aesKey, "AES")
        val ivSpec = IvParameterSpec(aesIv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        val blockSize = 16
        val padLength = blockSize - rawData.toByteArray().size % blockSize
        val paddedData = rawData.toByteArray() + ByteArray(padLength) { padLength.toByte() }
        val encrypted = cipher.doFinal(paddedData)
        val xorKeyHexRaw = vidFastKey?.xorKey ?: "XorKey Hex not Found"
        val xorKeyHex = if (xorKeyHexRaw.length > 1) xorKeyHexRaw.dropLast(1) else xorKeyHexRaw
        val xorKey = xorKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val xorResult = ByteArray(encrypted.size) { i ->
            (encrypted[i].toInt() xor xorKey[i % xorKey.size].toInt()).toByte()
        }
        val headers = mapOf(
            "Accept" to "*/*",
            "Referer" to Vidfast,
            "Origin" to "$Vidfast/",
            "User-Agent" to USER_AGENT,
            "x-session" to "",
            "Content-Type" to "application/json",
            "X-Requested-With" to "XMLHttpRequest"
        )


        val encodedFinal = customBase64EncodeVidfast(xorResult)
        val staticPath = vidFastKey?.staticPath
        val subpath= vidFastKey?.subPath
        val apiServersUrl = "$Vidfast/$staticPath/$subpath/$encodedFinal"
        val gson = Gson()
        val jsonMedia = "{}".toRequestBody()
        val responseBody = app.post(apiServersUrl, headers = headers, requestBody = jsonMedia).body.string()
        val type = object : TypeToken<List<VidfastServerData>>() {}.type
        val apiResponse: List<VidfastServerData>? = gson.fromJson(responseBody, type)
        val apisubpath= vidFastKey?.apisubpath

        if (apiResponse != null) {
            for (item in apiResponse) {
                val serverName = item.name
                val serverData = item.data ?: continue
                val apiStream = "$Vidfast/$staticPath/$apisubpath/$serverData"

                try {
                    val streamResponse = app.post(apiStream, headers = headers, requestBody = jsonMedia)
                    val streamBody = streamResponse.body.string()
                    val resultJson = JSONObject(streamBody)

                    val streamUrl = if (resultJson.has("url")) resultJson.getString("url") else null
                    val m3u8headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
                        "Referer" to "https://vidfast.pro/",
                        "Origin" to "https://vidfast.pro",
                    )

                    if (streamUrl != null) {
                        if (streamUrl.contains(".m3u8")) {
                            M3u8Helper.generateM3u8("Vidfast [$serverName]", streamUrl, Vidfast, headers = m3u8headers)
                                .forEach(callback)
                        } else {
                            callback.invoke(
                                newExtractorLink(
                                    "Vidfast",
                                    "Vidfast D [$serverName]",
                                    streamUrl,
                                    INFER_TYPE
                                ) {
                                    referer = Vidfast
                                    quality = Qualities.Unknown.value
                                    this.headers = m3u8headers
                                }
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Vidfast Error:", "❌ [$serverName] Failed for $apiStream: ${e.message}")
                }
            }
        }
    }
}




