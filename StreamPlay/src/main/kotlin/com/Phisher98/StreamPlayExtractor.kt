package com.phisher98

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.runAllAsync
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
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.select.Elements
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.mapOf
import kotlin.math.max


val session = Session(Requests().baseClient)

object StreamPlayExtractor : StreamPlay() {

    suspend fun invokeMultiEmbed(
        imdbId: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (imdbId == null) return

        val userAgent = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"

        val headers = mapOf(
            "User-Agent" to userAgent,
            "Referer" to MultiEmbedAPI,
            "X-Requested-With" to "XMLHttpRequest"
        )

        val baseUrl =
            if (season == null)
                "$MultiEmbedAPI/?video_id=$imdbId"
            else
                "$MultiEmbedAPI/?video_id=$imdbId&s=$season&e=$episode"

        val resolvedUrl = app.get(baseUrl, headers = headers).url

        val postData = mapOf(
            "button-click" to "ZEhKMVpTLVF0LVBTLVF0LVAtMGs1TFMtUXpPREF0TC0wLVYzTi0wVS1RTi0wQTFORGN6TmprLTU=",
            "button-referer" to ""
        )

        val pageHtml = app.post(resolvedUrl, data = postData, headers = headers).text

        val token =
            Regex("""load_sources\("([^"]+)"\)""")
                .find(pageHtml)
                ?.groupValues
                ?.get(1)
                ?: return

        val sourcesHtml = app.post(
            "https://streamingnow.mov/response.php",
            data = mapOf("token" to token),
            headers = headers
        ).text

        val sourcesDoc = Jsoup.parse(sourcesHtml)

        sourcesDoc.select("li").forEach { server ->

            val serverId = server.attr("data-server")
            val videoId = server.attr("data-id")

            if (serverId.isBlank() || videoId.isBlank()) return@forEach

            val playUrl =
                "https://streamingnow.mov/playvideo.php" +
                        "?video_id=${videoId.substringBefore("=")}" +
                        "&server_id=$serverId" +
                        "&token=$token" +
                        "&init=1"

            runCatching {
                val playHtml = app.get(playUrl, headers = headers).text
                val iframeUrl = Jsoup.parse(playHtml)
                    .selectFirst("iframe.source-frame.show")
                    ?.attr("src")
                    ?: return@runCatching

                val iframeHtml = app.get(iframeUrl, headers = headers).text

                val fileUrl =
                    Jsoup.parse(iframeHtml)
                        .selectFirst("iframe.source-frame.show")
                        ?.attr("src")
                        ?: Regex("""file:"(https?://[^"]+)"""")
                            .find(iframeHtml)
                            ?.groupValues
                            ?.get(1)
                        ?: return@runCatching


                Log.d("Phisher Load 1",fileUrl)

                when {
                    fileUrl.contains(".m3u8", ignoreCase = true) || fileUrl.contains(".json", ignoreCase = true) -> {

                        M3u8Helper.generateM3u8(
                            "MultiEmbed VIP",
                            fileUrl,
                            MultiEmbedAPI
                        ).forEach(callback)
                    }

                    else -> {
                        val url = fileUrl.lowercase()
                        when {
                            "vidsrc" in url -> return

                            "mixdrop" in url -> {
                                MixDrop().getUrl(fileUrl)
                            }
                            "streamwish" in url -> {
                                StreamWishExtractor().getUrl(fileUrl)
                            }
                            else -> {
                                loadSourceNameExtractor(
                                    "MultiEmbed",
                                    fileUrl,
                                    MultiEmbedAPI,
                                    subtitleCallback,
                                    callback = callback
                                )
                            }
                        }
                    }
                }

            }.onFailure {
                // ignore broken servers
            }
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

        val response = app.get(url, interceptor = CloudflareKiller())
        if (response.code != 200) return
        val req = response.documentLarge
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
                    val postResponse = app.post(
                        url = "$multimoviesApi/wp-admin/admin-ajax.php",
                        data = mapOf(
                            "action" to "doo_player_ajax",
                            "post" to postId,
                            "nume" to nume,
                            "type" to type
                        ),
                        referer = url,
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                    )
                    if (postResponse.code != 200) return@amap
                    val responseData = postResponse.parsed<ResponseHash>()
                    val embedUrl = responseData.embed_url
                    val link = embedUrl.substringAfter("\"").substringBefore("\"")
                    if (!link.contains("youtube", ignoreCase = true)) {
                        loadSourceNameExtractor(
                            "Multimovies",
                            link,
                            "$multimoviesApi/",
                            subtitleCallback,
                            callback
                        )
                    }
                }.onFailure { }
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
        val response = app.get(url)
        if (response.code != 200) return
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
        val document = res.documentLarge
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
                    loadDisplaySourceNameExtractor(
                        name,
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
        val response = app.get(url)
        if (response.code != 200) return
        val movieid =
            response.documentLarge.selectFirst("#embed-player")?.attr("data-movie-id") ?: return
        response.documentLarge.select("a.server.dropdown-item").forEach {
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
        callback: (ExtractorLink) -> Unit,
        dubtype: String? = null
    ) {
        if (dubtype == null || (!dubtype.equals("SUB", ignoreCase = true) && !dubtype.equals("Movie", ignoreCase = true))) return

        fun formatString(input: String?) = input?.replace(" ", "_").orEmpty()

        val jpFixTitle = formatString(jptitle)
        val fixTitle = formatString(title)
        val ep = episode ?: ""

        if (jpFixTitle.isBlank() && fixTitle.isBlank()) return

        var response =
            app.get("https://www.tokyoinsider.com/anime/S/${jpFixTitle}_(TV)/episode/$ep")
        if (response.code != 200) return
        var doc = response.documentLarge

        if (doc.select("div.c_h2").text().contains("We don't have any files for this episode")) {
            response = app.get("https://www.tokyoinsider.com/anime/S/${fixTitle}_(TV)/episode/$ep")
            if (response.code != 200) return
            doc = response.documentLarge
        }

        val href = doc.select("div.c_h2 > div:nth-child(1) > a").attr("href")
        if (href.isNotBlank()) {
            callback.invoke(
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
        engtitle:String? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) {
        if (dubtype == null || (!dubtype.equals("SUB", ignoreCase = true) && !dubtype.equals("Movie", ignoreCase = true))) return

        val searchResponse = app.get("https://anizone.to/anime?search=${jptitle}")
        if (searchResponse.code != 200) return

        val href = searchResponse.documentLarge
            .select("div.h-6.inline.truncate a")
            .firstOrNull {
                it.text().equals(jptitle, ignoreCase = true)
            }?.attr("href")
            ?: run {
                if (engtitle.isNullOrBlank()) null
                else {
                    val fallback = app.get("https://anizone.to/anime?search=${engtitle}")
                    if (fallback.code != 200) null
                    else fallback.documentLarge
                        .select("div.h-6.inline.truncate a")
                        .firstOrNull {
                            val jp = jptitle ?: return@firstOrNull false

                            fun norm(s: String) =
                                s.lowercase()
                                    .replace(Regex("[-:]"), " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()

                            norm(it.text()).startsWith(norm(jp))
                        }
                        ?.attr("href")
                }
            }

        if (href.isNullOrBlank()) return
        val episodeResponse = app.get("$href/$episode")
        if (episodeResponse.code != 200) return
        val m3u8 = episodeResponse.documentLarge.select("media-player").attr("src")
        if (m3u8.isBlank()) return
        callback.invoke(
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
        val type = if (season == null) "2" else "1"
        val searchResponse = app.get(
            "$kissKhAPI/api/DramaList/Search?q=$title&type=$type",
            referer = "$kissKhAPI/"
        )
        if (searchResponse.code != 200) return
        val res = tryParseJson<ArrayList<KisskhResults>>(searchResponse.text) ?: return
        val (id, contentTitle) = if (res.size == 1) {
            res.first().id to res.first().title
        } else {
            val data = res.find {
                val slugTitle = it.title.createSlug() ?: return@find false
                when {
                    season == null -> slugTitle == slug
                    lastSeason == 1 -> slugTitle.contains(slug)
                    else -> slugTitle.contains(slug) && it.title?.contains(
                        "Season $season",
                        true
                    ) == true
                }
            } ?: res.find { it.title.equals(title) }
            data?.id to data?.title
        }
        val detailResponse = app.get(
            "$kissKhAPI/api/DramaList/Drama/$id?isq=false",
            referer = "$kissKhAPI/Drama/${getKisskhTitle(contentTitle)}?id=$id"
        )
        if (detailResponse.code != 200) return
        val resDetail = detailResponse.parsedSafe<KisskhDetail>() ?: return
        val epsId =
            if (season == null) resDetail.episodes?.first()?.id else resDetail.episodes?.find { it.number == episode }?.id
                ?: return
        val kkeyResponse = app.get("${BuildConfig.KissKh}$epsId&version=2.8.10", timeout = 10000)
        if (kkeyResponse.code != 200) return
        val kkey = kkeyResponse.parsedSafe<KisskhKey>()?.key ?: ""
        val sourcesResponse = app.get(
            "$kissKhAPI/api/DramaList/Episode/$epsId.png?err=false&ts=&time=&kkey=$kkey",
            referer = "$kissKhAPI/Drama/${getKisskhTitle(contentTitle)}/Episode-${episode ?: 0}?id=$id&ep=$epsId&page=0&pageSize=100"
        )
        if (sourcesResponse.code != 200) return
        sourcesResponse.parsedSafe<KisskhSources>()?.let { source ->
            listOf(source.video, source.thirdParty).amap { link ->
                val safeLink = link ?: return@amap null
                when {
                    safeLink.contains(".m3u8") || safeLink.contains(".mp4") -> {
                        callback.invoke(
                            newExtractorLink(
                                "Kisskh",
                                "Kisskh",
                                fixUrl(safeLink, kissKhAPI),
                                INFER_TYPE
                            ) {
                                referer = kissKhAPI
                                quality = Qualities.P720.value
                                headers = mapOf("Origin" to kissKhAPI)
                            }
                        )
                    }

                    else -> {
                        val cleanedLink = safeLink.substringBefore("?").takeIf { it.isNotBlank() }
                            ?: return@amap null
                        loadSourceNameExtractor(
                            "Kisskh",
                            fixUrl(cleanedLink, kissKhAPI),
                            "$kissKhAPI/",
                            subtitleCallback,
                            callback,
                            Qualities.P720.value
                        )
                    }
                }
            }
        }
        val kkeySubResponse =
            app.get("${BuildConfig.KisskhSub}$epsId&version=2.8.10", timeout = 10000)
        if (kkeySubResponse.code != 200) return
        val kkey1 = kkeySubResponse.parsedSafe<KisskhKey>()?.key ?: ""
        val subResponse = app.get("$kissKhAPI/api/Sub/$epsId&kkey=$kkey1")
        if (subResponse.code != 200) return
        tryParseJson<List<KisskhSubtitle>>(subResponse.text)?.forEach { sub ->
            val lang = getLanguage(sub.label ?: "UnKnown")
            subtitleCallback.invoke(newSubtitleFile(lang, sub.src ?: return@forEach))
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
        callback: (ExtractorLink) -> Unit,
        dubtype: Boolean,
        isMovie: Boolean?,
    ) {
        val (_, malId) = convertTmdbToAnimeId(
            title, date, airedDate, if (season == null) TvType.AnimeMovie else TvType.Anime
        )
        var anijson: String? = null
        try {
            anijson = app.get("https://api.ani.zip/mappings?mal_id=$malId").toString()
        } catch (e: Exception) {
            println("Error fetching or parsing mapping: ${e.message}")
        }

        val anidbEid = getAnidbEid(anijson ?: "{}", episode ?: 1) ?: 0

        val malsync = malId?.let {
            runCatching {
                app.get("$malsyncAPI/mal/anime/$it").parsedSafe<MALSyncResponses>()?.sites
            }
                .getOrNull()
        }

        val zoro = malsync?.zoro
        val zoroIds = zoro?.keys?.toList().orEmpty()

        val zorotitle = zoro?.values?.firstNotNullOfOrNull { it["title"] }?.replace(":", " ")
        val aniXL = malsync?.AniXL?.values?.firstNotNullOfOrNull { it["url"] }
        val kaasSlug = malsync?.KickAssAnime?.values?.firstNotNullOfOrNull { it["identifier"] }
        val animepaheUrl = malsync?.animepahe?.values?.firstNotNullOfOrNull { it["url"] }
        val tmdbYear = date?.substringBefore("-")?.toIntOrNull()

        val dubStatus: String =
            if (isMovie == true) "Movie"
            else if (dubtype) "DUB"
            else "SUB"

        runAllAsync(
            {
                malId?.let {
                    invokeAnimetosho(
                        it,
                        episode,
                        subtitleCallback,
                        callback,
                        dubStatus,
                        anidbEid
                    )
                }
            },
            { invokeHianime(zoroIds, episode, subtitleCallback, callback, dubStatus) },
            {
                malId?.let {
                    invokeAnimeKai(
                        jptitle,
                        zorotitle,
                        episode,
                        subtitleCallback,
                        callback,
                        dubStatus
                    )
                }
            },
            {
                kaasSlug?.let {
                    invokeKickAssAnime(
                        title,
                        it,
                        episode,
                        subtitleCallback,
                        callback,
                        dubStatus
                    )
                }
            },
            {
                animepaheUrl?.let {
                    invokeAnimepahe(
                        it,
                        episode,
                        subtitleCallback,
                        callback,
                        dubStatus
                    )
                }
            },
            {
                invokeAnichi(
                    zorotitle,
                    title,
                    tmdbYear,
                    episode,
                    subtitleCallback,
                    callback,
                    dubStatus
                )
            },
            { invokeTokyoInsider(jptitle, title, episode, callback, dubStatus) },
            { invokeAnizone(jptitle,title, episode, callback, dubStatus) },
            {
                if (aniXL != null) {
                    invokeAniXL(aniXL, episode, callback, dubStatus)
                }
            }

            )
    }

    suspend fun invokeAniXL(
        url: String,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?
    ) {
        val baseurl = getBaseUrl(url)
        val response = app.get(url)
        if (response.code != 200) return
        val document = response.documentLarge

        val episodeLink = baseurl + (document
            .select("a.btn")
            .firstOrNull { it.text().trim() == episode?.toString() }
            ?.attr("href") ?: return)

        val episodeResponse = app.get(episodeLink)
        if (episodeResponse.code != 200) return
        val jsonText = episodeResponse.text
        val parts = jsonText.split(",").map { it.trim('"') }

        var dubUrl: String? = null
        var rawUrl: String? = null
        for (i in parts.indices) {
            when (parts[i]) {
                "dub" -> {
                    val possibleUrl = parts.getOrNull(i + 1)
                    if (possibleUrl != null && possibleUrl.endsWith(".m3u8") && !possibleUrl.contains(
                            ".ico"
                        )
                    ) {
                        dubUrl = possibleUrl
                    }
                }

                "raw" -> {
                    val possibleUrl = parts.getOrNull(i + 1)
                    if (possibleUrl != null && possibleUrl.endsWith(".m3u8") && !possibleUrl.contains(
                            ".ico"
                        )
                    ) {
                        rawUrl = possibleUrl
                    }
                }

                "sub" -> {
                    val possibleUrl = parts.getOrNull(i + 1)
                    if (possibleUrl != null && possibleUrl.endsWith(".m3u8") && !possibleUrl.contains(
                            ".ico"
                        )
                    ) {
                        rawUrl = possibleUrl
                    }
                }
            }
        }

        if (dubtype != null &&
            (dubtype.equals("Movie", ignoreCase = true) ||
                    dubtype.equals("DUB", ignoreCase = true))
        ) {
            dubUrl?.let {
                callback.invoke(
                    newExtractorLink(
                        "AniXL DUB",
                        "AniXL DUB",
                        it,
                        INFER_TYPE
                    ) {
                        quality = Qualities.P1080.value
                    }
                )
            }
        }

        if (dubtype != null &&
            (dubtype.equals("Movie", ignoreCase = true) ||
                    dubtype.equals("SUB", ignoreCase = true))
        ) {
            rawUrl?.let {
                callback.invoke(
                    newExtractorLink(
                        "AniXL SUB",
                        "AniXL SUB",
                        it,
                        INFER_TYPE
                    ) {
                        quality = Qualities.P1080.value
                    }
                )
            }
        }

    }


    suspend fun invokeAnichi(
        name: String?,
        engtitle: String?,
        year: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) {

        val isMovie = dubtype == "Movie"
        val privatereferer = "https://allmanga.to"
        val ephash = "5f1a64b73793cc2234a389cf3a8f93ad82de7043017dd551f38f65b89daa65e0"
        val queryhash = "06327bc10dd682e1ee7e07b6db9c16e9ad2fd56c1b769e47513128cd5c9fc77a"
        val type = if (episode == null) "Movie" else "TV"

        val normalizedName = name?.trim()?.lowercase()
        val normalizedEngTitle = engtitle?.trim()?.lowercase()

        val query =
            """${BuildConfig.ANICHI_API}?variables={"search":{"types":["$type"],"year":$year,"query":"$name"},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$queryhash"}}"""
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
            if (isMovie || (dubtype != null && lang.contains(dubtype, ignoreCase = true))) {
                val epQuery =
                    """${BuildConfig.ANICHI_API}?variables={"showId":"$id","translationType":"$lang","episodeString":"${episode ?: 1}"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$ephash"}}"""
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
                            loadDisplaySourceNameExtractor(
                                "Allanime",
                                "⌜ Allanime ⌟ | $host | [${lang.uppercase()}]",
                                sourceUrl,
                                "",
                                subtitleCallback,
                                callback
                            )
                            return@safeApiCall
                        }

                        val decoded =
                            if (sourceUrl.startsWith("--")) decrypthex(sourceUrl) else sourceUrl
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
                                source.sourceName.contains("Default") && server.resolutionStr in listOf(
                                    "SUB",
                                    "Alt vo_SUB"
                                ) -> {
                                    getM3u8Qualities(
                                        server.link,
                                        "https://static.crunchyroll.com/",
                                        host
                                    )
                                        .forEach(callback)
                                }

                                server.hls == null -> {
                                    callback.invoke(
                                        newExtractorLink(
                                            "Allanime",
                                            "⌜ Allanime ⌟ | ${host.capitalize()} | [${lang.uppercase()}]",
                                            server.link,
                                            INFER_TYPE
                                        ) {
                                            this.quality = Qualities.P1080.value
                                        }
                                    )
                                }

                                server.hls -> {
                                    val endpoint = "https://allanime.day/player?uri=" +
                                            if (URI(server.link).host.isNotEmpty()) server.link
                                            else "https://allanime.day${URI(server.link).path}"
                                    getM3u8Qualities(
                                        server.link,
                                        server.headers?.referer ?: endpoint,
                                        host
                                    ).forEach(callback)
                                }

                                else -> {
                                    server.subtitles?.forEach { sub ->
                                        val langName =
                                            SubtitleHelper.fromTagToEnglishLanguageName(
                                                sub.lang ?: ""
                                            )
                                                ?: sub.lang.orEmpty()
                                        val src = sub.src ?: return@forEach
                                        subtitleCallback(newSubtitleFile(langName, httpsify(src)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    suspend fun invokeAnimepahe(
        url: String,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) {
        val isMovie = dubtype == "Movie"
        val headers = mapOf("Cookie" to "__ddg2_=1234567890")

        val id = app.get("https://animepaheproxy.phisheranimepahe.workers.dev/?url=$url", headers)
            .documentLarge.selectFirst("meta[property=og:url]")
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
        ).documentLarge

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
            if ("kwik" in href && (isMovie || (dubtype != null && type.contains(dubtype, ignoreCase = true))))
            {
                loadDisplaySourceNameExtractor(
                    "Animepahe",
                    "⌜ Animepahe ⌟ $source | [$type]",
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
            if (isMovie || (dubtype != null && type.contains(dubtype, ignoreCase = true))) {
                loadDisplaySourceNameExtractor(
                    "Animepahe Pahe",
                    "⌜ Animepahe ⌟ Pahe $source | [$type]",
                    href,
                    "",
                    subtitleCallback,
                    callback,
                    quality.toIntOrNull()
                )
            }
        }
    }

    //AnimetoSho
    private data class ServerLink(
        val url: String,
        val size: String,
        val qualityIndex: Int,
        val group: String,
        val meta: ReleaseMeta
    )

    data class ReleaseMeta(
        val group: String,
        val resolution: Int?,
        val codec: String?,
        val platform: String?,
        val source: String?,
        val audio: String?,
        val subtitles: String?,
        val bitDepth: Int? = null,
        val audioCodec: String? = null
    )

    fun parseReleaseMeta(title: String): ReleaseMeta =
        ReleaseMeta(
            group = GROUP_REGEX.find(title)?.groupValues?.get(1).orEmpty(),
            resolution = RES_REGEX.find(title)?.groupValues?.get(1)?.toIntOrNull(),
            codec = CODEC_REGEX.find(title)?.value?.uppercase(),
            platform = PLATFORM_REGEX.find(title)?.value?.uppercase(),
            source = SOURCE_REGEX.find(title)?.value?.replace(" ", "-")?.uppercase(),
            audio = AUDIO_REGEX.find(title)?.value,
            subtitles = SUB_REGEX.find(title)?.value,
            bitDepth = BIT_DEPTH_REGEX.find(title)
                ?.value
                ?.filter { it.isDigit() }
                ?.toIntOrNull(),
            audioCodec = AUDIO_CODEC_REGEX.find(title)?.value?.uppercase()
        )


    private val GROUP_REGEX = Regex("""^\[(.*?)]""")

    private val HOST_REGEX = Regex("""KrakenFiles|GoFile|AkiraBox|BuzzHeavier""", RegexOption.IGNORE_CASE)

    private val RES_REGEX = Regex("""(4320|2160|1440|1080|720|480)p""", RegexOption.IGNORE_CASE)

    private val CODEC_REGEX = Regex("""AV1|HEVC|H\.?265|x265|H\.?264|x264""", RegexOption.IGNORE_CASE)

    private val PLATFORM_REGEX = Regex("""AMZN|NF|CR|BILI|IQIYI""", RegexOption.IGNORE_CASE)

    private val SOURCE_REGEX = Regex("""WEB[- .]?DL|WEB[- .]?Rip""", RegexOption.IGNORE_CASE)

    private val AUDIO_REGEX = Regex("""Dual[- ]?Audio|Eng(lish)?[- ]?Dub|Japanese""", RegexOption.IGNORE_CASE)

    private val AUDIO_CODEC_REGEX = Regex("""AAC(\d\.\d)?|DDP(\d\.\d)?|OPUS|FLAC""", RegexOption.IGNORE_CASE)

    private val BIT_DEPTH_REGEX = Regex("""10[- ]?bit|10bits|8[- ]?bit""", RegexOption.IGNORE_CASE)

    private val SUB_REGEX = Regex("""Multi[- ]?Subs?|MultiSub|Multiple Subtitles|Eng(lish)?[- ]?Sub|ESub|Subbed""", RegexOption.IGNORE_CASE)


    private fun Elements.getLinks(): List<ServerLink> =
        flatMap { ele ->
            val title = ele.select("div.link a").text()
            val meta = parseReleaseMeta(title)

            val size = ele.select("div.size").text()
            val quality = meta.resolution ?: Qualities.Unknown.value

            ele.select("div.links a:matches(${HOST_REGEX.pattern})").map { a ->
                ServerLink(
                    url = a.attr("href"),
                    size = size,
                    qualityIndex = quality,
                    group = meta.group,
                    meta = meta
                )
            }
        }

    suspend fun invokeAnimetosho(
        malId: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
        anidbEid: Int?
    ) {
        if (dubtype !in setOf("SUB", "Movie")) return

        val jikan = app
            .get("$jikanAPI/anime/$malId/full")
            .parsedSafe<JikanResponse>()
            ?.data
            ?: return

        val slug = jikan.title.createSlug()
        val url = "$animetoshoAPI/episode/$slug-$episode.$anidbEid"

        val res = app.get(url).documentLarge

        res.select("div.home_list_entry:has(div.links)")
            .getLinks()
            .asSequence()
            .filter {
                it.qualityIndex == Qualities.P1080.value ||
                        it.qualityIndex == Qualities.P720.value
            }
            .forEach { it ->
                val displayName = buildString {
                    append("⌜ Animetosho ⌟")

                    if (it.meta.group.isNotBlank())
                        append(" ${it.meta.group}")

                    val sourceBlock = listOfNotNull(
                        it.meta.platform,
                        it.meta.source
                    ).joinToString(" ")

                    listOfNotNull(
                        sourceBlock.takeIf { it.isNotBlank() },
                        it.meta.audio,
                        it.meta.subtitles,
                        it.meta.codec,
                        it.size.takeIf { it.isNotBlank() }
                    ).joinToString(" | ").let {
                        if (it.isNotBlank()) append(" | $it")
                    }
                }

                loadDisplaySourceNameExtractor(
                    "Animetosho ${it.meta.group}",
                    displayName,
                    it.url,
                    "$animetoshoAPI/",
                    subtitleCallback,
                    callback,
                    "${it.meta.resolution}".toIntOrNull()
                )
            }
    }


    @SuppressLint("NewApi")
    internal suspend fun invokeAnimeKai(
        jptitle: String? = null,
        title: String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?
    ) {
        val isMovie = dubtype == "Movie"

        suspend fun decode(text: String?): String {
            return try {
                val res = app.get("${BuildConfig.KAIENC}?text=$text").text
                JSONObject(res).getString("result")
            } catch (_: Exception) {
                app.get("${BuildConfig.KAISVA}/?f=e&d=$text").text
            }
        }

        val json = "application/json; charset=utf-8".toMediaType()

        suspend fun decodeReverse(text: String): String {
            val jsonBody = """{"text":"$text"}""".toRequestBody(json)

            return try {
                val res = app.post(
                    BuildConfig.KAIDEC,
                    requestBody = jsonBody
                ).textLarge
                JSONObject(res).getString("result")
            } catch (_: Exception) {
                app.get("${BuildConfig.KAISVA}/?f=d&d=$text").textLarge
            }
        }

        if (jptitle.isNullOrBlank() || title.isNullOrBlank()) return
        val shuffledApis = animekaiAPIs.shuffled().toMutableList()

        while (shuffledApis.isNotEmpty()) {
            val animeKaiUrl = shuffledApis.removeAt(0)
            try {
                val searchEnglish = app.get(
                    "$animeKaiUrl/ajax/anime/search?keyword=${
                        URLEncoder.encode(
                            title,
                            "UTF-8"
                        )
                    }"
                ).textLarge
                val searchRomaji = app.get(
                    "$animeKaiUrl/ajax/anime/search?keyword=${
                        URLEncoder.encode(
                            jptitle,
                            "UTF-8"
                        )
                    }"
                ).textLarge

                val resultsEng = parseAnimeKaiResults(searchEnglish)
                val resultsRom = parseAnimeKaiResults(searchRomaji)
                val combined = (resultsEng + resultsRom).distinctBy { it.id }

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
                    val href = "$animeKaiUrl/watch/$matchedId"
                    val animeId =
                        app.get(href).documentLarge.selectFirst("div.rate-box")?.attr("data-id")
                    val decoded = decode(animeId)
                    val epRes =
                        app.get("$animeKaiUrl/ajax/episodes/list?ani_id=$animeId&_=$decoded")
                            .parsedSafe<AnimeKaiResponse>()?.getDocument()

                    epRes?.select("div.eplist a")?.forEach { ep ->

                        val epNum = ep.attr("num").toIntOrNull()
                        if (!isMovie) {
                            if (epNum != episode) return@forEach
                        } else {
                            if (epNum != 1) return@forEach
                        }

                        val token = ep.attr("token")
                        val decodedtoken = decode(token)

                        val document =
                            app.get("$animeKaiUrl/ajax/links/list?token=$token&_=$decodedtoken")
                                .parsed<AnimeKaiResponse>()
                                .getDocument()

                        val types = listOf("sub", "softsub", "dub")
                        val servers = types.flatMap { type ->
                            document.select("div.server-items[data-id=$type] span.server[data-lid]")
                                .map { server ->
                                    Triple(type, server.attr("data-lid"), server.text())
                                }
                        }

                        for ((type, lid, serverName) in servers) {
                            val decodelid = decode(lid)
                            val result =
                                app.get("$animeKaiUrl/ajax/links/view?id=$lid&_=$decodelid")
                                    .parsed<AnimeKaiResponse>().result

                            val decodeiframe = decodeReverse(result)
                            val iframe = extractVideoUrlFromJsonAnimekai(decodeiframe)

                            val nameSuffix = when {
                                type.contains("soft", ignoreCase = true) -> " [Soft Sub]"
                                type.contains("sub", ignoreCase = true) -> " [SUB]"
                                type.contains("dub", ignoreCase = true) -> " [DUB]"
                                else -> ""
                            }

                            val allow =
                                when {
                                    isMovie -> true
                                    dubtype == null -> false
                                    else -> nameSuffix.contains(dubtype, ignoreCase = true)
                                }

                            if (allow) {
                                val name = "⌜ AnimeKai ⌟ | $serverName | $nameSuffix"
                                loadExtractor(iframe, name, subtitleCallback, callback)
                            }
                        }
                    }
                }

                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) {
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val shuffledApis = hianimeAPIs.shuffled().toMutableList()
        val episodeNumber = (episode ?: 1).toString()
        val timeoutMillis = 10_000L
        val isMovie = dubtype == "Movie"

        while (shuffledApis.isNotEmpty()) {
            val api = shuffledApis.removeAt(0)
            try {
                withTimeout(timeoutMillis) {
                    animeIds?.amap { id ->
                        val animeId = id ?: return@amap

                        val episodeId = app.get(
                            "$api/ajax/v2/episode/list/$animeId",
                            headers = headers
                        )
                            .takeIf { it.isSuccessful }
                            ?.parsedSafe<HianimeResponses>()?.html
                            ?.let { Jsoup.parse(it) }
                            ?.select("div.ss-list a")
                            ?.find { it.attr("data-number") == episodeNumber }
                            ?.attr("data-id")
                            ?: return@amap

                        val servers = app.get(
                            "$api/ajax/v2/episode/servers?episodeId=$episodeId",
                            headers = headers
                        )
                            .takeIf { it.isSuccessful }
                            ?.parsedSafe<HianimeResponses>()?.html
                            ?.let { Jsoup.parse(it) }
                            ?.select("div.item.server-item")
                            ?.map {
                                Triple(
                                    it.text(),
                                    it.attr("data-id"),
                                    it.attr("data-type")
                                )
                            }

                        servers?.forEach { (label, serverId, effectiveType) ->
                            val resolvedType =
                                if (effectiveType.equals("raw", ignoreCase = true)) "SUB"
                                else effectiveType

                            val allow =
                                when {
                                    isMovie -> true                  // 🎬 movie → BOTH
                                    dubtype == null -> false         // block
                                    else -> resolvedType.contains(dubtype, ignoreCase = true)
                                }

                            if (allow) {
                                val sourceUrl = app.get(
                                    "$api/ajax/v2/episode/sources?id=$serverId",
                                    headers = headers
                                )
                                    .takeIf { it.isSuccessful }
                                    ?.parsedSafe<EpisodeServers>()
                                    ?.link

                                if (!sourceUrl.isNullOrBlank()) {
                                    loadDisplaySourceNameExtractor(
                                        "HiAnime",
                                        "⌜ HiAnime ⌟ | ${label.uppercase()} | ${resolvedType.uppercase()}",
                                        sourceUrl,
                                        "",
                                        subtitleCallback,
                                        callback,
                                    )
                                }
                            }
                        }
                    }
                }
                return
            } catch (_: TimeoutCancellationException) {
                println("Timed out with domain $api")
            } catch (e: Exception) {
                println("Failed with domain $api: ${e.message}")
            }
        }

        println("All hianimeAPI domains failed.")
    }


    suspend fun invokeKickAssAnime(
        engtitle: String?,
        slugid: String?,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) {
        val isMovie = dubtype == "Movie"
        var slug = slugid
        if (slug.isNullOrBlank()) {
            if (engtitle.isNullOrBlank()) return

            val host = "https://kickass-anime.ro/"

            val json = """
                {
                    "page": 1,
                    "query": "$engtitle"
                }
            """.trimIndent()

            val requestBody = json.toRequestBody("application/json".toMediaType())

            val headers = mapOf(
                "Accept" to "*/*",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                "Content-Type" to "application/json",
                "x-origin" to "kickass-anime.ru"
            )

            val searchJson = app
                .post("${host}api/fsearch", requestBody = requestBody, headers = headers)
                .toString()

            val resultArray = JSONObject(searchJson).optJSONArray("result") ?: return

            for (i in 0 until resultArray.length()) {
                val item = resultArray.getJSONObject(i)
                if (item.optString("title_en", "")
                        .equals(engtitle, ignoreCase = true)
                ) {
                    slug = item.optString("slug")
                    break
                }
            }
        }

        val locales: List<String> = when {
            isMovie -> listOf("en-US", "ja-JP") // BOTH
            dubtype.equals("DUB", ignoreCase = true) -> listOf("en-US")
            dubtype.equals("SUB", ignoreCase = true) -> listOf("ja-JP")
            else -> return // null or invalid → block
        }

        locales.forEach { locale ->

            val json = app.get(
                "$KickassAPI/api/show/$slug/episodes?ep=1&lang=$locale",
                timeout = 5000L
            ).toString()

            val jsonresponse = parseJsonToEpisodes(json)

            val matchedSlug = jsonresponse.firstOrNull {
                it.episode_number.toString()
                    .substringBefore(".")
                    .toIntOrNull() == episode
            }?.slug ?: return@forEach

            val href = "$KickassAPI/api/show/$slug/episode/ep-$episode-$matchedSlug"
            val servers = app.get(href).parsedSafe<ServersResKAA>()?.servers ?: return@forEach

            servers.forEach { server ->
                if (server.name.contains("VidStreaming")) {
                    val host = getBaseUrl(server.src)
                    val headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                    )

                    val key = "e13d38099bf562e8b9851a652d2043d3".toByteArray()
                    val query = server.src.substringAfter("?id=").substringBefore("&")
                    val html = app.get(server.src).toString()

                    //If HTML have m3u8
                    if (html.contains(".m3u8", ignoreCase = true)) {
                        val match =
                            Regex("""(https?:)?//[^\s"'<>]+\.m3u8""", RegexOption.IGNORE_CASE)
                                .find(html)

                        val videoheaders = mapOf(
                            "Accept" to "*/*",
                            "Accept-Language" to "en-US,en;q=0.5",
                            "Origin" to host,
                            "Sec-Fetch-Dest" to "empty",
                            "Sec-Fetch-Mode" to "cors",
                            "Sec-Fetch-Site" to "cross-site"
                        )

                        match?.value?.let { url ->
                            val m3u8Url = if (url.startsWith("//")) "https:$url" else url
                            callback.invoke(
                                newExtractorLink(
                                    "VidStreaming",
                                    "VidStreaming",
                                    m3u8Url,
                                    ExtractorLinkType.M3U8
                                )
                                {
                                    this.quality = Qualities.P1080.value
                                    this.headers = videoheaders
                                }
                            )
                        }
                    }

                    val (sig, timeStamp, route) = getSignature(html, server.name, query, key)
                        ?: return@forEach
                    val sourceUrl = "$host$route?id=$query&e=$timeStamp&s=$sig"

                    val encJson =
                        app.get(sourceUrl, headers = headers).parsedSafe<EncryptedKAA>()?.data
                            ?: return@forEach

                    val (encryptedData, ivHex) = encJson
                        .substringAfter(":\"")
                        .substringBefore('"')
                        .split(":")
                    val decrypted = tryParseJson<m3u8KAA>(
                        CryptoAES.decrypt(encryptedData, key, ivHex.decodeHex()).toJson()
                    ) ?: return@forEach

                    val m3u8 = httpsify(decrypted.hls)
                    val videoHeaders = mapOf(
                        "Accept" to "*/*",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Origin" to host,
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "cross-site"
                    )

                    callback.invoke(
                        newExtractorLink(
                            server.name,
                            server.name,
                            m3u8,
                            ExtractorLinkType.M3U8
                        )
                        {
                            this.quality = Qualities.P1080.value
                            this.headers = videoHeaders
                        }
                    )

                    decrypted.subtitles.forEach { subtitle ->
                        subtitleCallback(newSubtitleFile(subtitle.name, httpsify(subtitle.src)))
                    }
                } else if (server.name.contains("CatStream")) {
                    val baseurl = getBaseUrl(server.src)
                    val headers = mapOf(
                        "Origin" to baseurl,
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    )

                    val res = app.get(
                        server.src,
                        headers = headers
                    ).text

                    val regex = Regex("""props="(.*?)"""")
                    val match = regex.find(res)
                    val encodedJson = match?.groupValues?.get(1)

                    if (encodedJson != null) {
                        val unescapedJson = Parser.unescapeEntities(encodedJson, false)
                        val json = JSONObject(unescapedJson)

                        val videoUrl = "https:" + json.getJSONArray("manifest").getString(1)
                        callback.invoke(
                            newExtractorLink(
                                "CatStream",
                                "CatStream HLS",
                                videoUrl,
                                ExtractorLinkType.M3U8
                            )
                            {
                                this.quality = Qualities.P1080.value
                                this.headers = headers
                            }
                        )

                        val subtitleArray = json.getJSONArray("subtitles").getJSONArray(1)

                        for (i in 0 until subtitleArray.length()) {
                            val sub = subtitleArray.getJSONArray(i).getJSONObject(1)

                            val src = sub.getJSONArray("src").getString(1)
                            val name = sub.getJSONArray("name").getString(1)
                            subtitleCallback.invoke(
                                newSubtitleFile(
                                    name,  // Use label for the name
                                    httpsify(src)    // Use extracted URL
                                )
                            )
                        }

                    } else {
                        println("Could not find embedded JSON in props attribute")
                    }
                }
            }
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

        // Fetch search page
        val url = try {
            val response = app.get(searchUrl)
            if (response.code != 200) {
                Log.e("UHDMovies", "Search page returned ${response.code}")
                return
            }

            response.documentLarge
                .select("article div.entry-image a")
                .firstOrNull()
                ?.attr("href")
                ?.takeIf(String::isNotBlank)
                ?: return
        } catch (e: Exception) {
            Log.e("UHDMovies", "Search error: ${e.localizedMessage}")
            return
        }

        // Fetch main page
        val doc = try {
            val response = app.get(url)
            if (response.code != 200) {
                Log.e("UHDMovies", "Main page returned ${response.code}")
                return
            }
            response.documentLarge
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
        try {
            for (link in links) {
                if (link.isBlank()) {
                    continue
                }
                val finalLink = if (link.contains("unblockedgames")) {
                    bypassHrefli(link)
                } else {
                    link
                }

                if (!finalLink.isNullOrBlank()) {
                    val response = app.get(finalLink)
                    if (response.code == 200) {
                        loadSourceNameExtractor(
                            "UHDMovies",
                            finalLink,
                            "",
                            subtitleCallback,
                            callback
                        )
                    } else {
                        Log.e("UHDMovies", "Link returned ${response.code}: $finalLink")
                        continue
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UHDMovies", "Link processing error: ${e.localizedMessage}")
            return
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
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        )
        val response = app.get(url, headers = headers, timeout = 100L)
        if (response.code != 200) return
        response.parsedSafe<SubtitlesAPI>()?.subtitles?.amap { it ->
            val lan = getLanguage(it.lang)
            val suburl = it.url
            subtitleCallback.invoke(
                newSubtitleFile(
                    lan.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                    suburl
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
        if (id.isNullOrBlank()) return

        val url = buildString {
            append("$WyZIESUBAPI/search?id=$id")
            if (season != null && episode != null) append("&season=$season&episode=$episode")
        }

        val response = app.get(url)
        if (response.code != 200) return

        val subtitles = runCatching {
            Gson().fromJson<List<WyZIESUB>>(
                response.toString(),
                object : TypeToken<List<WyZIESUB>>() {}.type
            )
        }.getOrElse { emptyList() }

        subtitles.forEach {
            val language = it.display.replaceFirstChar { ch -> ch.titlecase(Locale.getDefault()) }
            subtitleCallback(newSubtitleFile(language, it.url))
        }
    }


    suspend fun invokeVideasy(
        id: Int? = null,
        imdbid: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        if (imdbid.isNullOrBlank()) return

        val isMovie = season == null

        val apiPath = if (isMovie) {
            "sources-with-title?title=$title&mediaType=movie&year=$year&episodeId=1&seasonId=1&tmdbId=$id&imdbId=$imdbid"
        } else {
            "sources-with-title?title=$title&mediaType=tv&year=$year&episodeId=$episode&seasonId=$season&tmdbId=$id&imdbId=$imdbid"
        }

        val videasySources = listOf(
            VideasySource("myflixerzupcloud", "Neon", "Original"),
            VideasySource("1movies", "Sage", "Original"),
            VideasySource("moviebox", "Cypher", "Original"),
            VideasySource("cdn", "Yoru", "Original", movieOnly = true),
            VideasySource("primewire", "Reyna", "Original"),
            VideasySource("onionplay", "Omen", "Original"),
            VideasySource("m4uhd", "Breach", "Original"),

            // hdmovie variants
            VideasySource("hdmovie", "Vyse", "Original"),
            VideasySource("hdmovie", "Fade", "Hindi"),

            // meine (language-based)
            VideasySource("meine?language=german", "Killjoy", "German"),
            VideasySource("meine?language=italian", "Harbor", "Italian"),
            VideasySource("meine?language=french", "Chamber", "French", movieOnly = true),

            // latin / spanish
            VideasySource("cuevana-latino", "Gekko", "Latin"),
            VideasySource("cuevana-spanish", "Kayo", "Spanish"),

            // portuguese
            VideasySource("superflix", "Raze", "Portuguese"),
            VideasySource("overflix", "Phoenix", "Portuguese"),
            VideasySource("visioncine", "Astra", "Portuguese")
        )



        for (source in videasySources) {
            if (!isMovie && source.movieOnly) continue
            val videasyheaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9",
                "Origin" to "https://player.videasy.net",
                "Referer" to "https://player.videasy.net/",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-site"
            )

            runCatching {
                val url = "$Videasy/${source.key}/$apiPath"
                val res = app.get(url)
                if (res.code != 200) return@runCatching

                val body = JSONObject().apply {
                    put("text", res.text)
                    put("id", id)
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val decRes = app.post(BuildConfig.VideasyDEC, requestBody = body)
                if (decRes.code != 200) return@runCatching

                val json = JSONObject(decRes.text)
                val result = json.optJSONObject("result") ?: return@runCatching

                result.optJSONObject("streams")?.let { streams ->
                    for (key in streams.keys()) {
                        val link = streams.optString(key)
                        if (link.isBlank()) continue
                        Log.d("Phisher","${source.name} ${source.key}")
                        M3u8Helper.generateM3u8(
                            "Videasy · ${source.name} (${source.language})",
                            link,
                            Videasy,
                            headers = videasyheaders
                        ).forEach(callback)
                    }
                }

                result.optJSONArray("sources")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val link = obj.optString("url")
                        val q = obj.optString("quality")

                        if (link.isBlank()) continue
                        Log.d("Phisher","${source.name} ${source.key} $q")
                        M3u8Helper.generateM3u8(
                            "Videasy · ${source.name} (${source.language})",
                            link,
                            Videasy,
                            headers = videasyheaders
                        ).forEach(callback)
                    }
                }

                // ───── Subtitles ─────
                result.optJSONArray("subtitles")?.let { subs ->
                    for (i in 0 until subs.length()) {
                        val s = subs.getJSONObject(i)
                        val label = s.optString("label")
                        val file = s.optString("file")
                        if (file.isNotBlank()) subtitleCallback(newSubtitleFile(label, file))
                    }
                }

            }.onFailure {
                println("Videasy failed: ${source.name} → ${it.message}")
            }
        }
    }


    suspend fun invokeVidzee(
        id: Int?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val keyHex = "6966796f75736372617065796f75617265676179000000000000000000000000"
        val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val defaultReferer = "https://core.vidzee.wtf/"

        for (sr in 1..8) {
            try {
                val apiUrl = if (season == null) {
                    "https://player.vidzee.wtf/api/server?id=$id&sr=$sr"
                } else {
                    "https://player.vidzee.wtf/api/server?id=$id&sr=$sr&ss=$season&ep=$episode"
                }

                val response = app.get(apiUrl).text
                val json = JSONObject(response)

                val globalHeaders = mutableMapOf<String, String>()
                json.optJSONObject("headers")?.let { headersObj ->
                    headersObj.keys().forEach { key ->
                        globalHeaders[key] = headersObj.getString(key)
                    }
                }

                val urls = json.optJSONArray("url") ?: JSONArray()
                for (i in 0 until urls.length()) {
                    val obj = urls.getJSONObject(i)
                    val encryptedLink = obj.optString("link")
                    val name = obj.optString("name", "Vidzee")
                    val type = obj.optString("type", "hls")
                    val lang = obj.optString("lang", "Unknown")
                    val flag = obj.optString("flag", "")

                    if (encryptedLink.isNotBlank()) {
                        val finalUrl = try {
                            decryptVidzeeUrl(encryptedLink, keyBytes)
                        } catch (e: Exception) {
                            Log.e("VidzeeDecrypt", "Failed to decrypt link: $e")
                            encryptedLink
                        }

                        URI(finalUrl)
                        val headersMap = mutableMapOf<String, String>()
                        headersMap.putAll(globalHeaders)
                        val referer = headersMap["referer"] ?: defaultReferer
                        val displayName =
                            if (flag.isNotBlank()) "VidZee $name ($lang - $flag)" else " VidZee$name ($lang)"

                        callback.invoke(
                            newExtractorLink(
                                "VidZee",
                                displayName,
                                finalUrl,
                                if (type.equals(
                                        "hls",
                                        ignoreCase = true
                                    )
                                ) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.referer = referer
                                this.headers = headersMap
                                this.quality = Qualities.P1080.value
                            }
                        )
                    }
                }

                // Handle subtitles
                val subs = json.optJSONArray("tracks") ?: JSONArray()
                for (i in 0 until subs.length()) {
                    val sub = subs.getJSONObject(i)
                    val subLang = sub.optString("lang", "Unknown")
                    val subUrl = sub.optString("url")
                    if (subUrl.isNotBlank()) subtitleCallback(newSubtitleFile(subLang, subUrl))
                }

            } catch (e: Exception) {
                Log.e("VidzeeApi", "Failed sr=$sr: $e")
            }
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
            "$topmoviesAPI/search/${imdbId.orEmpty()}"
        } else {
            "$topmoviesAPI/search/${imdbId.orEmpty()} Season $season"
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
                .mapNotNull { it.attr("href").takeIf { it.isNotBlank() } }

            for (detailPageUrl in detailPageUrls) {
                val detailPageDocument =
                    runCatching { app.get(detailPageUrl).documentLarge }.getOrNull() ?: continue

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
                val detailPageDocument =
                    runCatching { app.get(detailPageUrl).documentLarge }.getOrNull() ?: continue

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
        title: String?=null,
        imdbId: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val MoviesmodAPI = getDomains()?.moviesmod ?: return
        invokeModflix(
            title = title,
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
        title: String?,
        imdbId: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        api: String
    ) {
        val searchUrl = if (season == null) {
            "$api/search/$imdbId"
        } else {
            "$api/search/$imdbId Season $season"
        }

        val doc = app.get(searchUrl).documentLarge

        val hrefpattern = doc
            .selectFirst("#content_box article a")
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?: run {
                val titleSearchUrl = if (season == null) { "$api/search/$title"
                } else {
                    "$api/search/$title Season $season"
                }
                app.get(titleSearchUrl).documentLarge
                    .selectFirst("#content_box article a")
                    ?.attr("href")
                    ?.takeIf { it.isNotBlank() }
            }
            ?: return Log.e("Modflix", "No valid result for ID or title search")


        val document = runCatching {
            app.get(
                hrefpattern,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
                ),
                interceptor = wpRedisInterceptor
            ).documentLarge
        }.getOrElse {
            Log.e("Modflix", "Failed to load page: ${it.message}")
            return
        }

        if (season == null) {
            val detailLinks = document
                .select("a[class*=maxbutton][href]")
                .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }


            for (url in detailLinks) {
                val detailDoc =
                    runCatching { app.get(url).documentLarge }.getOrNull() ?: continue

                val driveLinks = detailDoc.select("a.maxbutton-fast-server-gdrive")
                    .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }

                for (driveLink in driveLinks) {
                    val finalLink = if ("unblockedgames" in driveLink) {
                        bypassHrefli(driveLink) ?: continue
                    } else driveLink

                    loadSourceNameExtractor(
                        "MoviesMod",
                        finalLink,
                        "$api/",
                        subtitleCallback,
                        callback
                    )
                }
            }
        } else {
        val seasonPattern = Regex("Season\\s+$season\\b", RegexOption.IGNORE_CASE)
            for (content in document.select("div.thecontent")) {
                val seasonH3 = content.select("h3")
                    .firstOrNull { seasonPattern.containsMatchIn(it.text()) }
                    ?: continue

                Log.d("Phisher seasonH3", seasonH3.toString())

                val episodeLinks = mutableListOf<String>()
                var node = seasonH3.nextElementSibling()

                while (node != null) {
                    node.select("a.maxbutton-episode-links")
                        .mapNotNullTo(episodeLinks) {
                            it.attr("href").takeIf(String::isNotBlank)
                        }
                    node = node.nextElementSibling()
                }

                for (url in episodeLinks) {
                    val detailDoc = runCatching {
                        app.get(url).documentLarge
                    }.getOrNull() ?: continue
                    Log.d("Phisher episodeLinks", episodeLinks.toString())

                    val link = detailDoc.select("span strong")
                        .firstOrNull { it.text().contains("Episode $episode", true) }
                        ?.parent()
                        ?.closest("a")
                        ?.attr("href")
                        ?.takeIf { it.isNotBlank() }
                        ?: continue

                    val finalLink = if (link.contains("unblockedgames")) {
                        bypassHrefli(link) ?: continue
                    } else link

                    loadSourceNameExtractor(
                        "MoviesMod",
                        finalLink,
                        "$api/",
                        subtitleCallback,
                        callback
                    )
                }
            }
        }
    }




    suspend fun invokeVegamovies(
        sourceName: String,
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "cookie" to "xla=s4t",
            "Accept-Language" to "en-US,en;q=0.9",
            "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Microsoft Edge\";v=\"120\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Linux\"",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Sec-Fetch-User" to "?1",
            "Upgrade-Insecure-Requests" to "1",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
        )

        val api = if(sourceName == "VegaMovies") getDomains()?.vegamovies else getDomains()?.rogmovies

        val url = "$api/?s=${id ?: return}"
        app.get(
            url,
            referer = api,
            headers = headers
        ).document.select("article h2 a,article h3 a").amap {
            val res = app.get(
                it.attr("href"),
                referer = api,
                headers = headers
            ).document
            if(season == null) {
                res.select("button.dwd-button").amap {
                    val link = it.parent()?.attr("href") ?: return@amap
                    val doc = app.get(link, referer = api, headers = headers).document
                    val source = doc.selectFirst("button.btn:matches((?i)(V-Cloud))")
                        ?.parent()
                        ?.attr("href")
                        ?: return@amap
                    loadSourceNameExtractor(sourceName, source, referer = "", subtitleCallback, callback)
                }
            }
            else {
                res.select("h5:matches((?i)Season $season), h3:matches((?i)Season $season)").amap { header ->
                    val links = header.nextElementSiblings().select("a:matches((?i)(V-Cloud|Single|Episode|G-Direct))")
                        links.amap { link ->
                            val targetHref = link.attr("href")
                            val doc = app.get(targetHref, referer = api, headers = headers).document
                            val epElement = doc.selectFirst("h4:contains(Episodes):contains($episode)") ?: return@amap
                            val epLink = epElement.nextElementSibling()?.selectFirst("a:matches((?i)(V-Cloud|Single|Episode|G-Direct))")?.attr("href")
                            if (epLink != null) { loadSourceNameExtractor(sourceName, epLink, referer = "", subtitleCallback, callback)
                           } else
                        {
                            Log.e(sourceName, "V-Cloud link missing for episode $episode")
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun invokeVidsrccc(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$vidsrctoAPI/v2/embed/movie/$id?autoPlay=false"
        } else {
            "$vidsrctoAPI/v2/embed/tv/$id/$season/$episode?autoPlay=false"
        }

        val html = app.get(url).documentLarge.toString()
        val regex = Regex("""var\s+(\w+)\s*=\s*(?:"([^"]*)"|(\w+));""")
        val vars = mutableMapOf<String, String>()

        regex.findAll(html).forEach {
            val key = it.groupValues[1]
            val value = it.groupValues[2].ifEmpty { it.groupValues[3] }
            vars[key] = value
        }

        val v = vars["v"] ?: return
        val userId = vars["userId"] ?: return
        val imdbId = vars["imdbId"] ?: ""
        val movieId = vars["movieId"] ?: return
        val movieType = vars["movieType"] ?: return

        val vrf = generateVrfAES(movieId, userId)

        val apiUrl = if (season == null) {
            "$vidsrctoAPI/api/$id/servers?id=$id&type=$movieType&v=$v&vrf=$vrf&imdbId=$imdbId"
        } else {
            "$vidsrctoAPI/api/$id/servers?id=$id&type=$movieType&season=$season&episode=$episode&v=$v&vrf=$vrf&imdbId=$imdbId"
        }

        val serversText = app.get(apiUrl).text
        val serversJson = JSONObject(serversText)

        if (!serversJson.optBoolean("success")) return

        val servers = serversJson.optJSONArray("data") ?: return

        for (i in 0 until servers.length()) {
            val server = servers.optJSONObject(i) ?: continue

            val name = server.optString("name")
            val hash = server.optString("hash")
            if (hash.isEmpty()) continue


            val sourceText = app.get("$vidsrctoAPI/api/source/$hash").text
            if (sourceText.startsWith("<")) continue

            val sourceJson = JSONObject(sourceText)
            if (!sourceJson.optBoolean("success")) continue

            val data = sourceJson.optJSONObject("data") ?: continue
            val source = data.optString("source")
            if (source.isEmpty() || source.contains(".vidbox")) continue

            callback(
                newExtractorLink(
                    "Vidsrc",
                    "⌜ Vidsrc ⌟ | [$name]",
                    source,
                ) {
                    quality = if (name.contains("4K", true))
                        Qualities.P2160.value
                    else
                        Qualities.P1080.value

                    referer = vidsrctoAPI
                }
            )
        }
    }


    suspend fun invokeNepu(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val slug = title?.createSlug() ?: return
        val headers = mapOf(
            "X-Requested-With" to "XMLHttpRequest"
        )

        val data = try {
            val res = app.get(
                "$nepuAPI/ajax/posts?q=$title",
                headers = headers,
                referer = "$nepuAPI/"
            )
            if (res.code != 200) return
            res.parsedSafe<NepuSearch>()?.data
        } catch (e: Exception) {
            Log.e("Nepu", "Search request failed: ${e.localizedMessage}")
            return
        }

        val media =
            data?.find { it.url?.startsWith(if (season == null) "/movie/$slug-$year-" else "/serie/$slug-$year-") == true }
                ?: data?.find {
                    it.name.equals(
                        title,
                        true
                    ) && it.type == if (season == null) "Movie" else "Serie"
                }

        val mediaUrl = media?.url ?: return
        val fullMediaUrl =
            if (season == null) mediaUrl else "$mediaUrl/season/$season/episode/$episode"

        val dataId = try {
            val pageRes = app.get(fixUrl(fullMediaUrl, nepuAPI))
            if (pageRes.code != 200) return
            pageRes.documentLarge.selectFirst("a[data-embed]")?.attr("data-embed")
        } catch (e: Exception) {
            Log.e("Nepu", "Media page request failed: ${e.localizedMessage}")
            return
        } ?: return

        val res = try {
            val postRes = app.post(
                "$nepuAPI/ajax/embed",
                data = mapOf("id" to dataId),
                referer = fullMediaUrl,
                headers = headers
            )
            if (postRes.code != 200) return
            postRes.text
        } catch (e: Exception) {
            Log.e("Nepu", "Embed request failed: ${e.localizedMessage}")
            return
        }

        val m3u8 = "(http[^\"]+)".toRegex().find(res)?.groupValues?.get(1) ?: return

        callback.invoke(
            newExtractorLink(
                "Nepu",
                "Nepu",
                url = m3u8,
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
                val doc = response.documentLarge.selectFirst("script:containsData(sources:)")
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
                newSubtitleFile(
                    sub.label?.substringBefore("&nbsp") ?: "", fixUrl(
                        sub.url
                            ?: return@map null, watchSomuchAPI
                    )
                )
            )
        }
    }

    suspend fun invokeDahmerMovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$dahmerMoviesAPI/movies/${title?.replace(":", "")} ($year)/"
        } else {
            "$dahmerMoviesAPI/tvs/${title?.replace(":", " -")}/Season $season/"
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

        paths.map {
            val quality = getIndexQuality(it.first)
            val tags = getIndexQualityTags(it.first)
            val href = if (it.second.contains(dahmerMoviesAPI)) it.second else (dahmerMoviesAPI + it.second)

            callback.invoke(
                newExtractorLink(
                    "DahmerMovies",
                    "DahmerMovies $tags",
                    url = href,
                    ExtractorLinkType.VIDEO
                ) {
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
            app.get(url).documentLarge.selectFirst("iframe#iframesrc")?.attr("data-src")
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

        // POST request
        val response = app.post("$api/parse/classes/$classes", requestBody = body)
        if (response.code != 200) return

        val data = response.text
        val iframes = if (season == null) {
            val result = tryParseJson<ShowflixSearchMovies>(data)?.resultsMovies?.find {
                it.name.equals("$title ($year)", ignoreCase = true) ||
                        it.name.equals(title, ignoreCase = true)
            } ?: return
            val streamwish = result.embedLinks?.get("streamwish")
            val filelions = result.embedLinks?.get("filelions")
            val streamruby = result.embedLinks?.get("streamruby")
            val upnshare = result.embedLinks?.get("upnshare")
            val vihide = result.embedLinks?.get("vihide")

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
                "Showflix",
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

        val response = app.get(url)
        if (response.code != 200) return

        val id =
            response.documentLarge.selectFirst("div#show_player_ajax")?.attr("movie-id") ?: return

        // POST request
        val postResponse = app.post(
            "$zoechipAPI/wp-admin/admin-ajax.php",
            data = mapOf(
                "action" to "lazy_player",
                "movieID" to id,
            ),
            referer = url,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest"
            )
        )
        if (postResponse.code != 200) return

        val server = postResponse.documentLarge.selectFirst("ul.nav a:contains(Filemoon)")
            ?.attr("data-server") ?: return

        val serverResponse = app.get(server, referer = "$zoechipAPI/")
        if (serverResponse.code != 200) return

        val host = getBaseUrl(serverResponse.url)
        val script =
            serverResponse.documentLarge.select("script:containsData(function(p,a,c,k,e,d))").last()
                ?.data() ?: return
        val unpacked = getAndUnpack(script)

        val m3u8 =
            Regex("file:\\s*\"(.*?m3u8.*?)\"").find(unpacked)?.groupValues?.getOrNull(1) ?: return

        M3u8Helper.generateM3u8(
            "Zoechip",
            m3u8,
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

        val response = app.get(url, referer = "https://pressplay.top/")
        if (response.code != 200) return
        val iframe = response.documentLarge.selectFirst("iframe")?.attr("src") ?: return
        loadExtractor(iframe, "$nineTvAPI/", subtitleCallback, callback)
    }


    suspend fun invokeRidomovies(
        tmdbId: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val searchResponse =
            app.get("$ridomoviesAPI/core/api/search?q=$imdbId", interceptor = wpRedisInterceptor)
        if (searchResponse.code != 200) return  // Early return if not 200

        val mediaSlug = searchResponse.parsedSafe<RidoSearch>()
            ?.data?.items?.find { it.contentable?.tmdbId == tmdbId || it.contentable?.imdbId == imdbId }
            ?.slug ?: return

        val id = season?.let {
            val episodeUrl = "$ridomoviesAPI/tv/$mediaSlug/season-$it/episode-$episode"
            val episodeResponse = app.get(episodeUrl, interceptor = wpRedisInterceptor)
            if (episodeResponse.code != 200) return@let null  // Early return if not 200

            episodeResponse.text.substringAfterLast("""postid\":\"""").substringBefore("\"")
        } ?: mediaSlug

        val url =
            "$ridomoviesAPI/core/api/${if (season == null) "movies" else "episodes"}/$id/videos"
        val videoResponse = app.get(url, interceptor = wpRedisInterceptor)
        if (videoResponse.code != 200) return  // Early return if not 200

        videoResponse.parsedSafe<RidoResponses>()?.data?.amap { link ->
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
            val playerResponse = app.get("https://allmovieland.link/player.js?v=60%20128")
            if (playerResponse.code != 200) return@runCatching
            val playerScript = playerResponse.toString()
            val domainRegex = Regex("const AwsIndStreamDomain.*'(.*)';")
            val host =
                domainRegex.find(playerScript)?.groupValues?.getOrNull(1) ?: return@runCatching

            val resResponse = app.get("$host/play/$imdbId", referer = "$allmovielandAPI/")
            if (resResponse.code != 200) return@runCatching
            val resData =
                resResponse.documentLarge.selectFirst("script:containsData(playlist)")?.data()
                    ?.substringAfter("{")?.substringBefore(";")?.substringBefore(")")
                    ?: return@runCatching

            val json = tryParseJson<AllMovielandPlaylist>("{$resData}") ?: return@runCatching
            val headers = mapOf("X-CSRF-TOKEN" to "${json.key}")
            val jsonfile =
                if (json.file?.startsWith("http") == true) json.file else host + json.file

            val serverResponse = app.get(jsonfile, headers = headers, referer = "$allmovielandAPI/")
            if (serverResponse.code != 200) return@runCatching
            val serverJson = serverResponse.text.replace(Regex(""",\s*/"""), "")
            val cleanedJson = serverJson.replace(Regex(",\\s*\\[\\s*]"), "")

            val servers = tryParseJson<ArrayList<AllMovielandServer>>(cleanedJson)?.let { list ->
                if (season == null) {
                    list.mapNotNull { it.file?.let { file -> file to it.title.orEmpty() } }
                } else {
                    list.find { it.id == season.toString() }
                        ?.folder?.find { it.episode == episode.toString() }
                        ?.folder?.mapNotNull { it.file?.let { file -> file to it.title.orEmpty() } }
                }
            } ?: return@runCatching

            servers.amap { (server, lang) ->
                runCatching {
                    val playlistResponse = app.post(
                        "$host/playlist/$server.txt",
                        headers = headers,
                        referer = "$allmovielandAPI/"
                    )
                    if (playlistResponse.code != 200) return@runCatching
                    val playlistUrl = playlistResponse.text
                    val headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36",
                        "Accept" to "*/*",
                        "Referer" to allmovielandAPI,
                        "Origin" to allmovielandAPI
                    )

                    M3u8Helper.generateM3u8(
                        "AllMovieLand-$lang",
                        playlistUrl,
                        allmovielandAPI,
                        headers = headers
                    ).forEach(callback)
                }.onFailure { it.printStackTrace() }
            }
        }.onFailure { it.printStackTrace() }
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

        val response = app.get(url)
        if (response.code != 200) return
        val document = response.documentLarge

        document.select("div.entry-content > p a").forEach {
            val link = it.attr("href")
            val iframeResponse = app.get(link)
            if (iframeResponse.code != 200) return@forEach
            val trueUrl = iframeResponse.documentLarge.selectFirst("iframe")?.attr("src").orEmpty()
            if (trueUrl.isNotBlank()) {
                loadExtractor(trueUrl, subtitleCallback, callback)
            }
        }
    }

    suspend fun invokeMoviesdrive(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        id: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val movieDriveAPI = getDomains()?.moviesdrive ?: return
        val cleanTitle = title.orEmpty()
        val searchUrl = "$movieDriveAPI/searchapi.php?q=${id}"

        val figures = retry {
            val resp = app.get(searchUrl, interceptor = wpRedisInterceptor)
            if (resp.code != 200) return@retry null

            val root = JSONObject(resp.text)
            val hits = root.optJSONArray("hits") ?: return@retry null

            val elements = Elements()

            for (i in 0 until hits.length()) {
                val hit = hits.optJSONObject(i) ?: continue
                val document = hit.optJSONObject("document") ?: continue

                if (document.optString("imdb_id").equals(id, ignoreCase = true)) {
                    val permalink = document.optString("permalink")
                    if (permalink.isBlank()) continue
                    val a = Element("a").attr("href", "$movieDriveAPI$permalink")
                    val wrapper = Element("div")
                    wrapper.appendChild(a)
                    elements.add(wrapper)
                    break
                }
            }

            elements.takeIf { it.isNotEmpty() }
        } ?: return


        for (figure in figures) {
            val detailUrl = figure.selectFirst("a[href]")?.attr("href").orEmpty()
            if (detailUrl.isBlank()) continue

            val detailDoc = retry {
                val resp = app.get(detailUrl, interceptor = wpRedisInterceptor)
                if (resp.code != 200) return@retry null
                resp.documentLarge
            } ?: continue

            val imdbId = detailDoc
                .select("a[href*=\"imdb.com/title/\"]")
                .firstOrNull()
                ?.attr("href")
                ?.substringAfter("title/")
                ?.substringBefore("/")
                ?.takeIf { it.isNotBlank() }

            val titleMatch = imdbId == id.orEmpty() || detailDoc
                .select("main > p:nth-child(10),p strong:contains(Movie Name:) + span,p strong:contains(Series Name:)")
                .firstOrNull()
                ?.text()
                ?.contains(cleanTitle, ignoreCase = true) == true

            if (!titleMatch) continue

            if (season == null) {
                val links = detailDoc.select("h5 a")
                for (element in links) {
                    val urls = retry { extractMdrive(element.attr("href")) } ?: continue
                    urls.forEach { serverUrl ->
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

                    val episodeDoc = retry {
                        val resp = app.get(seasonHref)
                        if (resp.code != 200) return@retry null
                        resp.documentLarge
                    } ?: continue

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
                    allLinks.forEach { serverUrl ->
                        Log.d("Phisher",serverUrl)
                        processMoviesdriveUrl(serverUrl, subtitleCallback, callback)
                    }
                } else {
                    detailDoc.select("h5 a:contains(HubCloud)")
                        .mapNotNull { it.attr("href").takeIf { href -> href.isNotBlank() } }
                        .forEach { fallbackUrl ->
                            Log.d("Phisher",fallbackUrl)
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
                GDFlix().getUrl(
                    serverUrl, referer = "MoviesDrive",
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
        val searchQuery = id ?: return
        val fullQuery = if (season != null) "$searchQuery $season" else searchQuery
        val searchUrl = "$bollyflixAPI/search/$fullQuery"

        fun log(message: String) {
            println("BollyflixExtractor: $message")
        }

        val searchDoc = try {
            retryIO { app.get(searchUrl, interceptor = wpRedisInterceptor).documentLarge }
        } catch (e: Exception) {
            log("Failed to fetch searchDoc: ${e.message}")
            return
        }

        val contentUrl = searchDoc.selectFirst("div > article > a")?.attr("href")
        if (contentUrl.isNullOrEmpty()) {
            log("Content URL not found for id=$id")
            return
        }

        val contentDoc = try {
            retryIO { app.get(contentUrl).documentLarge }
        } catch (e: Exception) {
            log("Failed to fetch contentDoc: ${e.message}")
            return
        }

        val hTag = if (season == null) "h5" else "h4"
        val sTag = if (season != null) "Season $season" else ""

        val entries = contentDoc
            .select("div.thecontent.clearfix > $hTag:matches((?i)$sTag.*(720p|1080p|2160p))")
            .filterNot { it.text().contains("Download", ignoreCase = true) }
            .takeLast(4)

        suspend fun processUrl(url: String) {
            val redirectUrl = try {
                retryIO { app.get(url, allowRedirects = false).headers["location"].orEmpty() }
            } catch (e: Exception) {
                log("Failed to get redirect for $url: ${e.message}")
                return
            }

            if (redirectUrl.isEmpty()) {
                log("Redirect URL empty for $url")
                return
            }

            if ("gdflix" in redirectUrl.lowercase()) {
                GDFlix().getUrl(redirectUrl, "BollyFlix", subtitleCallback, callback)
            } else {
                loadSourceNameExtractor("Bollyflix", url, "", subtitleCallback, callback)
            }
        }

        for (entry in entries) {
            val href = entry.nextElementSibling()?.selectFirst("a")?.attr("href") ?: continue
            val token = href.substringAfter("id=", "")
            if (token.isEmpty()) continue

            val encodedUrl = try {
                retryIO {
                    app.get("https://blog.finzoox.com/?id=$token").text
                        .substringAfter("link\":\"")
                        .substringBefore("\"};")
                }
            } catch (e: Exception) {
                log("Failed to fetch encoded URL for token=$token: ${e.message}")
                continue
            }

            val decodedUrl = try {
                base64Decode(encodedUrl)
            } catch (e: Exception) {
                log("Failed to decode URL for token=$token: ${e.message}")
                continue
            }

            if (season == null) {
                processUrl(decodedUrl)
            } else {
                val episodeSelector = "article h3 a:contains(Episode 0$episode)"
                val episodeLink = try {
                    retryIO {
                        app.get(decodedUrl).documentLarge.selectFirst(episodeSelector)?.attr("href")
                    }
                } catch (e: Exception) {
                    log("Failed to fetch episode document: ${e.message}")
                    continue
                }

                if (episodeLink.isNullOrEmpty()) {
                    log("Episode link not found for episode=$episode")
                    continue
                }

                processUrl(episodeLink)
            }
        }
    }

    suspend fun invokeWatch32APIHQ(
        title: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (title.isNullOrBlank()) return

        val type = if (season == null) "Movie" else "TV"
        val searchUrl = "$Watch32/search/${title.trim().replace(" ", "-")}"

        val matchedElement = runCatching {
            val doc = app.get(searchUrl, timeout = 120L).documentLarge
            val results = doc.select("div.flw-item")

            results.firstOrNull { item ->
                val titleElement = item.selectFirst("h2.film-name a")
                val typeElement = item.selectFirst("span.fdi-type")
                val name = titleElement?.text()?.trim() ?: return@firstOrNull false
                val mediaType = typeElement?.text()?.trim() ?: return@firstOrNull false

                name.contains(title, ignoreCase = true) && mediaType.equals(type, ignoreCase = true)
            }?.selectFirst("h2.film-name a")
        }.getOrNull() ?: return
        val detailUrl = Watch32 + matchedElement.attr("href")
        val typee = if (type == "Movie") TvType.Movie else TvType.TvSeries
        val infoId = detailUrl.substringAfterLast("-")

        if (typee == TvType.TvSeries) {
            val seasonLinks = runCatching {
                app.get("$Watch32/ajax/season/list/$infoId").documentLarge.select("div.dropdown-menu a")
            }.getOrNull() ?: return

            val matchedSeason = seasonLinks.firstOrNull {
                it.text().contains("Season $season", ignoreCase = true)
            } ?: return

            val seasonId = matchedSeason.attr("data-id")

            val episodeLinks = runCatching {
                app.get("$Watch32/ajax/season/episodes/$seasonId").documentLarge.select("li.nav-item a")
            }.getOrNull() ?: return

            val matchedEpisode = episodeLinks.firstOrNull {
                it.text().contains("Eps $episode:", ignoreCase = true)
            } ?: return

            val dataId = matchedEpisode.attr("data-id")

            val serverDoc = runCatching {
                app.get("$Watch32/ajax/episode/servers/$dataId").documentLarge
            }.getOrNull() ?: return

            val sourceButtons = serverDoc.select("li.nav-item a")
            for (source in sourceButtons) {
                val sourceId = source.attr("data-id")

                val iframeUrl = runCatching {
                    app.get("$Watch32/ajax/episode/sources/$sourceId")
                        .parsedSafe<Watch32>()?.link
                }.getOrNull() ?: continue
                loadDisplaySourceNameExtractor(
                    "Watch32",
                    "Watch32",
                    iframeUrl,
                    "",
                    subtitleCallback,
                    callback
                )
            }
        } else {
            val episodeLinks = runCatching {
                app.get("$Watch32/ajax/episode/list/$infoId").documentLarge.select("li.nav-item a")
            }.getOrNull() ?: return
            episodeLinks.forEach { ep ->
                val dataId = ep.attr("data-id")
                val iframeUrl = runCatching {
                    app.get("$Watch32/ajax/episode/sources/$dataId")
                        .parsedSafe<Watch32>()?.link
                }.getOrNull() ?: return@forEach
                loadDisplaySourceNameExtractor(
                    "Watch32",
                    "Watch32",
                    iframeUrl,
                    "",
                    subtitleCallback,
                    callback
                )
            }
        }
    }


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
                } catch (_: Exception) {
                }
            }
            return try {
                block()
            } catch (_: Exception) {
                null
            }
        }

        val sourceApiUrl =
            "$RiveStreamAPI/api/backendfetch?requestID=VideoProviderServices&secretKey=rive"
        val sourceList = retry { app.get(sourceApiUrl, headers).parsedSafe<RiveStreamSource>() }

        val document =
            retry { app.get(RiveStreamAPI, headers, timeout = 20).documentLarge } ?: return
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

                val responseString = retry {
                    app.get(streamUrl, headers, timeout = 10).text
                } ?: return@forEach

                try {
                    val json = JSONObject(responseString)
                    val sourcesArray =
                        json.optJSONObject("data")?.optJSONArray("sources") ?: return@forEach

                    for (i in 0 until sourcesArray.length()) {
                        val src = sourcesArray.getJSONObject(i)
                        val label = if (src.optString("source")
                                .contains("AsiaCloud", ignoreCase = true)
                        ) "RiveStream ${src.optString("source")}[${src.optString("quality")}]" else "RiveStream ${
                            src.optString(
                                "source"
                            )
                        }"
                        val quality = Qualities.P1080.value
                        val url = src.optString("url")

                        try {
                            if (url.contains("proxy?url=")) {
                                try {
                                    val fullyDecoded = URLDecoder.decode(url, "UTF-8")

                                    val encodedUrl = fullyDecoded.substringAfter("proxy?url=")
                                        .substringBefore("&headers=")
                                    val decodedUrl = URLDecoder.decode(
                                        encodedUrl,
                                        "UTF-8"
                                    ) // decode again if needed

                                    val encodedHeaders = fullyDecoded.substringAfter("&headers=")
                                    val headersMap = try {
                                        val jsonStr = URLDecoder.decode(encodedHeaders, "UTF-8")
                                        JSONObject(jsonStr).let { json ->
                                            json.keys().asSequence()
                                                .associateWith { json.getString(it) }
                                        }
                                    } catch (_: Exception) {
                                        emptyMap()
                                    }

                                    val referer = headersMap["Referer"] ?: ""
                                    val origin = headersMap["Origin"] ?: ""
                                    val videoHeaders =
                                        mapOf("Referer" to referer, "Origin" to origin)

                                    val type = if (decodedUrl.contains(".m3u8", ignoreCase = true))
                                        ExtractorLinkType.M3U8 else INFER_TYPE

                                    callback.invoke(
                                        newExtractorLink(
                                            label,
                                            label,
                                            decodedUrl,
                                            type
                                        ) {
                                            this.quality = quality
                                            this.referer = referer
                                            this.headers = videoHeaders
                                        })
                                } catch (_: Exception) {
                                    Log.e(
                                        "RiveStreamSourceError",
                                        "Failed to decode proxy URL: $url"
                                    )
                                }
                            } else {
                                val type = if (url.contains(".m3u8", ignoreCase = true))
                                    ExtractorLinkType.M3U8 else INFER_TYPE

                                callback.invoke(
                                    newExtractorLink(
                                        "$label (VLC)",
                                        "$label (VLC)",
                                        url,
                                        type
                                    ) {
                                        this.referer = ""
                                        this.quality = quality
                                    })
                            }
                        } catch (e: Exception) {
                            Log.e("RiveStreamSourceError", "Source parse failed: $url $e")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RiveStreamParseError", "Failed to parse JSON for service $source: $e")
                }
            } catch (e: Exception) {
                Log.e("RiveStreamError", "Failed service: $source $e")
            }
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
        val prorcpUrl = extractProrcpUrl(iframeUrl) ?: "Not Found 2"

        val decryptedSource = extractAndDecryptSource(prorcpUrl, iframeUrl) ?: return

        val referer = prorcpUrl.substringBefore("rcp")
        decryptedSource.forEach { (version, url) ->
            M3u8Helper.generateM3u8(
                "VidsrcXYZ Server ${version.capitalize()}",
                url,
                referer
            ).forEach(callback)
        }

    }

    private suspend fun extractIframeUrl(url: String): String? {
        return httpsify(
            app.get(url).documentLarge.select("iframe").attr("src")
        ).takeIf { it.isNotEmpty() }
    }

    private suspend fun extractProrcpUrl(iframeUrl: String): String? {
        val doc = app.get(iframeUrl, referer = iframeUrl).document
        val regex = Regex("src:\\s+'(.*?)'")
        val matchedSrc = regex.find(doc.html())?.groupValues?.get(1) ?: return null
        val host = getBaseUrl(iframeUrl)
        return host + matchedSrc
    }

    private suspend fun extractAndDecryptSource(
        prorcpUrl: String,
        referer: String
    ): List<Pair<String, String>>? {
        val responseText = app.get(prorcpUrl, referer = referer).text
        val playerJsRegex = Regex("""Playerjs\(\{.*?file:"(.*?)".*?\}\)""")
        val temp = playerJsRegex.find(responseText)?.groupValues?.get(1)

        val encryptedURLNode = if (!temp.isNullOrEmpty()) {
            mapOf("id" to "playerjs", "content" to temp)
        } else {
            val document = Jsoup.parse(responseText)
            val reporting = document.selectFirst("#reporting_content") ?: return null
            val node = reporting.nextElementSibling() ?: return null
            mapOf("id" to node.attr("id"), "content" to node.text())
        }

        val id = encryptedURLNode["id"] ?: return null
        val content = encryptedURLNode["content"] ?: return null

        val decrypted = decryptMethods[id]?.invoke(content) ?: return null

        // Domain mapping
        val vSubs = mapOf(
            "v1" to "shadowlandschronicles.com",
            "v2" to "cloudnestra.com",
            "v3" to "thepixelpioneer.com",
            "v4" to "putgate.org",
            "v5" to ""
        )
        val placeholderRegex = "\\{(v\\d+)\\}".toRegex()
        val mirrors: List<Pair<String, String>> = decrypted
            .split(" or ")
            .map { it.trim() }
            .filter { it.startsWith("http") }
            .map { rawUrl ->
                val match = placeholderRegex.find(rawUrl)
                val version = match?.groupValues?.get(1) ?: ""   // v1, v2, v3 etc or "" if none
                val domain = vSubs[version] ?: ""

                // replace {vX} with actual domain
                val finalUrl = if (domain.isNotEmpty()) {
                    placeholderRegex.replace(rawUrl) { domain }
                } else {
                    rawUrl
                }

                version to finalUrl
            }

        return mirrors.ifEmpty { null }
    }


    suspend fun invokePrimeSrc(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val headers = mapOf(
            "accept" to "*/*",
            "referer" to if (season == null) "$PrimeSrcApi/embed/movie?imdb=$imdbId" else "$PrimeSrcApi/embed/tv?imdb=$imdbId&season=$season&episode=$episode",
            "sec-ch-ua" to "\"Chromium\";v=\"140\", \"Not=A?Brand\";v=\"24\", \"Google Chrome\";v=\"140\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Windows\"",
            "sec-fetch-dest" to "empty",
            "sec-fetch-mode" to "cors",
            "sec-fetch-site" to "same-origin",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
        )
        val url = if (season == null) {
            "$PrimeSrcApi/api/v1/s?imdb=$imdbId&type=movie"
        } else {
            "$PrimeSrcApi/api/v1/s?imdb=$imdbId&season=$season&episode=$episode&type=tv"
        }

        val serverList =
            app.get(url, timeout = 30, headers = headers).parsedSafe<PrimeSrcServerList>()
        serverList?.servers?.forEach {
            val rawServerJson =
                app.get("$PrimeSrcApi/api/v1/l?key=${it.key}", timeout = 30, headers = headers).text
            val jsonObject = JSONObject(rawServerJson)
            loadSourceNameExtractor(
                "PrimeWire${if (it.fileName.isNullOrEmpty()) "" else " (${it.fileName}) "}",
                jsonObject.optString("link", ""),
                PrimeSrcApi,
                subtitleCallback,
                callback,
                null,
                it.fileSize ?: ""
            )
        }

    }


    suspend fun invokeFilm1k(
        title: String? = null,
        season: Int? = null,
        year: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val proxyUrl = "https://corsproxy.io/?url="
        val mainUrl = "$proxyUrl$Film1kApi"
        if (season == null) {
            try {
                val fixTitle = title?.replace(":", "")?.replace(" ", "+")
                val doc =
                    app.get("$mainUrl/?s=$fixTitle", cacheTime = 60, timeout = 30).documentLarge
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
                val postDoc =
                    url?.let { app.get("$proxyUrl$it", cacheTime = 60, timeout = 30).documentLarge }
                val id = postDoc?.select("a.Button.B.on")?.attr("data-ide")
                repeat(5) { i ->
                    val mediaType = "application/x-www-form-urlencoded".toMediaType()
                    val body =
                        "action=action_change_player_eroz&ide=$id&key=$i".toRequestBody(mediaType)
                    val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
                    val doc =
                        app.post(
                            ajaxUrl,
                            requestBody = body,
                            cacheTime = 60,
                            timeout = 30
                        ).documentLarge
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
        if (imdbId.isNullOrBlank()) return

        val searchUrl = "$fourthAPI/search?keyword=$imdbId"

        val href: String? = app.get(searchUrl)
            .documentLarge
            .selectFirst("h2.film-name a")
            ?.attr("href")
            ?.let { fourthAPI + it }

        val mediaId: Int? = href?.let { url ->
            app.get(url)
                .documentLarge
                .selectFirst("h2.heading-name a")
                ?.attr("href")
                ?.substringAfterLast("/")
                ?.toIntOrNull()
        }

        mediaId?.let { id ->
            val seasonNumber = season ?: 1
            invokeExternalSource(id, seasonNumber, season, episode, callback, token)
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
            val href = it.mapValue.fields.href.stringValue
            val quality = it.mapValue.fields.quality.stringValue
            loadSourceNameExtractor(
                "StreamPlay",
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

        val baseUrl = runCatching { getDomains()?.n4khdhub }
            .onFailure { Log.e("4Khdhub", "Failed to get domain: ${it.message}") }
            .getOrNull() ?: return

        val searchUrl = "$baseUrl/?s=${title.trim().replace(" ", "+")}"

        // Normalization helper
        val normalize = { str: String ->
            str.lowercase()
                .replace(Regex("[^a-z0-9 ]"), "")
                .trim()
        }

        val normalizedTitle = normalize(title)

        val searchDoc = runCatching { app.get(searchUrl).documentLarge }
            .onFailure { Log.e("4Khdhub", "Failed to fetch search page: ${it.message}") }
            .getOrNull() ?: return

        val postLink = searchDoc.select("div.card-grid > a.movie-card")
            .firstOrNull { card ->
                val titleText = card.selectFirst("div.movie-card-content > h3.movie-card-title")
                    ?.text()?.let(normalize) ?: return@firstOrNull false

                val metaText = card.selectFirst("div.movie-card-content > p.movie-card-meta")
                    ?.text()?.trim().orEmpty()

                val titleMatch = titleText.contains(normalizedTitle)

                val yearMatch = if (season == null) {
                    year?.let { metaText.contains(it.toString().trim()) } ?: true
                } else true

                titleMatch && yearMatch
            }?.attr("href") ?: return

        val doc = runCatching { app.get("$baseUrl$postLink").documentLarge }
            .onFailure { Log.e("4Khdhub", "Failed to fetch detail page: ${it.message}") }
            .getOrNull() ?: return

        val links = if (season == null) {
            doc.select("div.download-item a")
        } else {
            val seasonText = "S${season.toString().padStart(2, '0')}"
            val episodeText = "E${episode.toString().padStart(2, '0')}"

            doc.select("div.episode-download-item")
                .filter {
                    it.text()
                        .contains(Regex("${seasonText}${episodeText}", RegexOption.IGNORE_CASE))
                }
                .flatMap { it.select("div.episode-links > a") }
        }

        for (element in links) {
            val rawHref = element.attr("href")
            if (rawHref.isBlank()) continue

            val link = runCatching { hdhubgetRedirectLinks(rawHref) }
                .onFailure {
                    Log.e(
                        "4Khdhub",
                        "Failed to resolve redirect for $rawHref: ${it.message}"
                    )
                }
                .getOrNull() ?: continue

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
        val url =
            if (season == null) "$Elevenmovies/movie/$id" else "$Elevenmovies/tv/$id/$season/$episode"

        val encodedToken = app.get(url).documentLarge.selectFirst("script[type=application/json]")!!
            .data()
            .substringAfter("{\"data\":\"")
            .substringBefore("\",")

        val jsonUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/main/output.json"
        val jsonString = app.get(jsonUrl).text
        val gson = Gson()
        val json: Elevenmoviesjson = gson.fromJson(jsonString, Elevenmoviesjson::class.java)
        val token = elevenMoviesToken(encodedToken)

        val staticPath = json.static_path
        val apiServerUrl = "$Elevenmovies/$staticPath/$token/sr"
        val headers = mutableMapOf("Referer" to Elevenmovies)

        val responseString = try {
            if (json.http_method.contains("GET")) {
                val res = app.get(apiServerUrl, headers = headers).body.string()
                res
            } else {
                val postHeaders = headers.toMutableMap()
                postHeaders["X-Requested-With"] = "XMLHttpRequest"
                postHeaders["User-Agent"] = USER_AGENT
                val res = app.post(apiServerUrl, headers = postHeaders).body.string()
                res
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to fetch server list: ${e.message}")
        }

        val listType = object : TypeToken<List<ElevenmoviesServerEntry>>() {}.type
        val serverList: List<ElevenmoviesServerEntry> = gson.fromJson(responseString, listType)

        for (entry in serverList) {
            val serverToken = entry.data
            val serverName = entry.name
            val streamApiUrl = "$Elevenmovies/$staticPath/$serverToken"

            val streamResponseString = try {
                val streamHeaders = headers.toMutableMap()
                streamHeaders["X-Requested-With"] = "XMLHttpRequest"
                streamHeaders["User-Agent"] = USER_AGENT
                streamHeaders["Content-Type"] = "application/vnd.api+json"

                val requestBody = "".toRequestBody("application/vnd.api+json".toMediaType())

                app.post(
                    streamApiUrl,
                    headers = streamHeaders,
                    requestBody = requestBody
                ).body.string()
            } catch (_: Exception) {
                continue
            }

            val streamRes =
                gson.fromJson(streamResponseString, ElevenmoviesStreamResponse::class.java)
            val videoUrl = streamRes?.url ?: continue

            M3u8Helper.generateM3u8("Eleven Movies $serverName", videoUrl, "").forEach(callback)
            streamRes.tracks?.forEach { sub ->
                val label = sub.label ?: return@forEach
                val file = sub.file ?: return@forEach
                subtitleCallback(newSubtitleFile(label, file))
            }
        }
    }


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

        val response = app.get(
            "https://search.pingora.fyi/collections/post/documents/search" +
                    "?q=$title" +
                    "&query_by=post_title,category" +
                    "&query_by_weights=4,2" +
                    "&sort_by=sort_by_date:desc" +
                    "&limit=20" +
                    "&highlight_fields=none" +
                    "&use_cache=true" +
                    "&page=1",
            referer = baseUrl
        )

        val json = try {
            JSONObject(response.text)
        } catch (e: Exception) {
            Log.d("HDhub4u", "Failed to parse JSON: ${e.message}")
            return
        }
        val hits = json.optJSONArray("hits") ?: return
        Log.d("HDhub4u", "Hits count = ${hits.length()}")

        val normalizeRegex = Regex("[^a-z0-9]")
        val normalizedTitle = title.lowercase().replace(normalizeRegex, "")
        val seasonText = season?.let { "season $it" }

        val posts = mutableListOf<String>()

        for (i in 0 until hits.length()) {
            val document = hits.optJSONObject(i)
                ?.optJSONObject("document")
                ?: continue

            val postTitle = document.optString("post_title").lowercase()
            val permalink = baseUrl + document.optString("permalink")

            if (postTitle.isBlank() || permalink.isBlank()) continue

            val cleanTitle = postTitle.replace(normalizeRegex, "")
            Log.d("HDhub4u", "Post[$i] title=$postTitle url=$permalink")
            val matches = when {
                season != null ->
                    cleanTitle.contains(normalizedTitle) &&
                            postTitle.contains(seasonText!!, ignoreCase = true)

                year != null ->
                    cleanTitle.contains(normalizedTitle) &&
                            postTitle.contains(year.toString())

                else ->
                    cleanTitle.contains(normalizedTitle)
            }

            if (matches) {
                posts += permalink
            }
        }

        val matchedPosts = if (!imdbId.isNullOrBlank()) {
            val matched = posts.mapNotNull { postUrl ->
                val postDoc = runCatching {
                    app.get(postUrl).documentLarge
                }.getOrNull() ?: return@mapNotNull null

                val imdbHref = postDoc
                    .selectFirst("""a[href*="imdb.com/title/$imdbId"]""")
                    ?.attr("href")
                    ?: return@mapNotNull null

                val foundImdbId = imdbHref
                    .substringAfter("/tt")
                    .substringBefore("/")
                    .let { "tt$it" }

                if (foundImdbId == imdbId) postUrl else null
            }

            matched.ifEmpty { posts }
        } else posts


        for (el in matchedPosts) {
            val doc = runCatching { app.get(el).documentLarge }.getOrNull() ?: continue

            if (season == null) {
                val qualityLinks =
                    doc.select("h3 a:matches(480|720|1080|2160|4K), h4 a:matches(480|720|1080|2160|4K)")
                for (linkEl in qualityLinks) {
                    val resolvedLink = linkEl.attr("href")
                    val resolvedWatch =
                        if ("id=" in resolvedLink) hdhubgetRedirectLinks(resolvedLink) else resolvedLink
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
                            val episodeDoc =
                                runCatching { app.get(resolved).documentLarge }.getOrNull()
                                    ?: return@let

                            episodeDoc.select("h3 a[href], h4 a[href], h5 a[href]")
                                .mapNotNull { it.absUrl("href").takeIf { url -> url.isNotBlank() } }
                                .forEach { link ->
                                    val resolvedWatch =
                                        if ("id=" in link) hdhubgetRedirectLinks(link) else link
                                    dispatchToExtractor(
                                        resolvedWatch,
                                        "HDhub4u",
                                        subtitleCallback,
                                        callback
                                    )
                                }
                        }

                        watchLink?.absUrl("href")?.let { watchHref ->
                            val resolvedWatch =
                                if ("id=" in watchHref) hdhubgetRedirectLinks(watchHref) else watchHref
                            loadSourceNameExtractor(
                                "HDhub4u",
                                resolvedWatch,
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

    suspend fun invokeHdmovie2(
        title: String? = null,
        year: Int? = null,
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

        val document = app.get(url, headers = headers, allowRedirects = true).documentLarge
        val ajaxUrl = "$hdmovie2API/wp-admin/admin-ajax.php"

        val commonHeaders = headers + mapOf(
            "Accept" to "*/*",
            "X-Requested-With" to "XMLHttpRequest"
        )

        fun String.getIframe(): String = Jsoup.parse(this).select("iframe").attr("src")

        suspend fun fetchSource(post: String, nume: String, type: String): String {
            val response = app.post(
                url = ajaxUrl,
                data = mapOf(
                    "action" to "doo_player_ajax",
                    "post" to post,
                    "nume" to nume,
                    "type" to type
                ),
                referer = hdmovie2API,
                headers = commonHeaders
            ).parsed<ResponseHash>()
            return response.embed_url.getIframe()
        }

        var link: String? = null

        if (episode != null) {
            document.select("ul#playeroptionsul > li").getOrNull(1)?.let { ep ->
                val post = ep.attr("data-post")
                val nume = (episode + 1).toString()
                link = fetchSource(post, nume, "movie")
            }
        } else {
            document.select("ul#playeroptionsul > li")
                .firstOrNull { it.text().contains("v2", ignoreCase = true) }
                ?.let { mv ->
                    val post = mv.attr("data-post")
                    val nume = mv.attr("data-nume")
                    link = fetchSource(post, nume, "movie")
                }
        }

        // If ajax link failed, fallback to legacy anchors
        if (link.isNullOrEmpty()) {
            val type = if (episode != null) "(Combined)" else ""
            document.select("a[href*=dwo]").forEach { anchor ->
                val innerDoc = app.get(anchor.attr("href")).documentLarge
                innerDoc.select("div > p > a").forEach {
                    val href = it.attr("href")
                    if (href.contains("GDFlix")) {
                        val redirectedUrl = (1..10).firstNotNullOfOrNull {
                            app.get(href, allowRedirects = false).headers["location"]
                        } ?: href

                        loadSourceNameExtractor(
                            "Hdmovie2$type",
                            redirectedUrl,
                            "",
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }
        } else {
            loadSourceNameExtractor(
                "Hdmovie2",
                link,
                hdmovie2API,
                subtitleCallback,
                callback
            )
        }
    }

    suspend fun invokeMovieBox(
        title: String?,
        season: Int? = 0,
        episode: Int? = 0,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            if (title.isNullOrBlank()) return false

            val url = "$movieBox/wefeed-mobile-bff/subject-api/search/v2"
            val jsonBody = """{"page":1,"perPage":10,"keyword":"$title"}"""
            val xClientToken = generateXClientToken()
            val xTrSignature = generateXTrSignature(
                "POST", "application/json", "application/json; charset=utf-8", url, jsonBody
            )
            val headers = mapOf(
                "user-agent" to "com.community.mbox.in/50020042 (Linux; Android 16)",
                "accept" to "application/json",
                "content-type" to "application/json",
                "x-client-token" to xClientToken,
                "x-tr-signature" to xTrSignature,
                "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","sp_code":""}""",
                "x-client-status" to "0"
            )

            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            val response = app.post(url, headers = headers, requestBody = requestBody)
            if (response.code != 200) return false

            val mapper = jacksonObjectMapper()
            val root = mapper.readTree(response.body.string())
            val results = root["data"]?.get("results") ?: return false

            val matchingIds = mutableListOf<String>()
            for (result in results) {
                val subjects = result["subjects"] ?: continue
                for (subject in subjects) {
                    val name = subject["title"]?.asText() ?: continue
                    val id = subject["subjectId"]?.asText() ?: continue
                    val type = subject["subjectType"]?.asInt() ?: 0
                    if (name.contains(title, ignoreCase = true) && (type == 1 || type == 2)) {
                        matchingIds.add(id)
                    }
                }
            }
            if (matchingIds.isEmpty()) return false

            var foundLinks = false

            for (id in matchingIds) {
                try {
                    val subjectUrl = "$movieBox/wefeed-mobile-bff/subject-api/get?subjectId=$id"
                    val subjectXToken = generateXClientToken()
                    val subjectXSign = generateXTrSignature(
                        "GET",
                        "application/json",
                        "application/json",
                        subjectUrl
                    )
                    val subjectHeaders = headers + mapOf(
                        "x-client-token" to subjectXToken,
                        "x-tr-signature" to subjectXSign
                    )
                    val subjectRes = app.get(subjectUrl, headers = subjectHeaders)
                    if (subjectRes.code != 200) continue

                    val subjectJson = mapper.readTree(subjectRes.body.string())
                    val subjectData = subjectJson["data"]
                    val subjectIds = mutableListOf<Pair<String, String>>()
                    var originalLanguageName = "Original"

                    // handle dubs
                    val dubs = subjectData?.get("dubs")
                    if (dubs != null && dubs.isArray) {
                        for (dub in dubs) {
                            val dubId = dub["subjectId"]?.asText()
                            val lanName = dub["lanName"]?.asText()
                            if (dubId != null && lanName != null) {
                                if (dubId == id) {
                                    originalLanguageName = lanName
                                } else {
                                    subjectIds.add(Pair(dubId, lanName))
                                }
                            }
                        }
                    }
                    subjectIds.add(0, Pair(id, originalLanguageName))

                    for ((subjectId, language) in subjectIds) {
                        val playUrl =
                            "$movieBox/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=${season ?: 0}&ep=${episode ?: 0}"
                        val token = generateXClientToken()
                        val sign = generateXTrSignature(
                            "GET",
                            "application/json",
                            "application/json",
                            playUrl
                        )
                        val playHeaders =
                            headers + mapOf("x-client-token" to token, "x-tr-signature" to sign)

                        val playRes = app.get(playUrl, headers = playHeaders)
                        if (playRes.code != 200) continue

                        val playRoot = mapper.readTree(playRes.body.string())
                        val streams = playRoot["data"]?.get("streams") ?: continue
                        if (!streams.isArray) continue

                        for (stream in streams) {
                            val streamId = stream["id"]?.asText() ?: "$subjectId|$season|$episode"
                            val subjectTitle =
                                subjectData?.get("title")?.asText() ?: "Unknown Title"
                            val format = stream["format"]?.asText() ?: ""
                            val signCookie =
                                stream["signCookie"]?.asText()?.takeIf { it.isNotEmpty() }

                            val resolutionNodes = stream["resolutionList"] ?: stream["resolutions"]

                            if (resolutionNodes != null && resolutionNodes.isArray) {
                                for (resNode in resolutionNodes) {
                                    val resUrl = resNode["resourceLink"]?.asText() ?: continue
                                    val quality = resNode["resolution"]?.asInt() ?: 0

                                    callback.invoke(
                                        newExtractorLink(
                                            source = "MovieBox (${language.capitalize()})",
                                            name = "MovieBox (${language.capitalize()}) [$subjectTitle]",
                                            url = resUrl,
                                            type = when {
                                                resUrl.startsWith(
                                                    "magnet:",
                                                    true
                                                ) -> ExtractorLinkType.MAGNET

                                                resUrl.endsWith(
                                                    ".mpd",
                                                    true
                                                ) -> ExtractorLinkType.DASH

                                                resUrl.endsWith(
                                                    ".torrent",
                                                    true
                                                ) -> ExtractorLinkType.TORRENT

                                                format.equals(
                                                    "HLS",
                                                    true
                                                ) || resUrl.endsWith(
                                                    ".m3u8",
                                                    true
                                                ) -> ExtractorLinkType.M3U8

                                                else -> INFER_TYPE
                                            }
                                        ) {
                                            this.headers = mapOf("Referer" to movieBox) +
                                                    (if (signCookie != null) mapOf("Cookie" to signCookie) else emptyMap())
                                            this.quality = getQualityFromName("$quality")
                                        }
                                    )
                                    foundLinks = true
                                }
                            } else {
                                // fallback single url
                                val singleUrl = stream["url"]?.asText() ?: continue
                                val resText = stream["resolutions"]?.asText() ?: ""

                                callback.invoke(
                                    newExtractorLink(
                                        source = "MovieBox (${language.capitalize()})",
                                        name = "MovieBox (${language.capitalize()}) [$subjectTitle]",
                                        url = singleUrl,
                                        type = when {
                                            singleUrl.startsWith(
                                                "magnet:",
                                                true
                                            ) -> ExtractorLinkType.MAGNET

                                            singleUrl.endsWith(
                                                ".mpd",
                                                true
                                            ) -> ExtractorLinkType.DASH

                                            singleUrl.endsWith(
                                                ".torrent",
                                                true
                                            ) -> ExtractorLinkType.TORRENT

                                            format.equals(
                                                "HLS",
                                                true
                                            ) || singleUrl.endsWith(
                                                ".m3u8",
                                                true
                                            ) -> ExtractorLinkType.M3U8

                                            else -> INFER_TYPE
                                        }
                                    ) {
                                        this.headers = mapOf("Referer" to movieBox) +
                                                (if (signCookie != null) mapOf("Cookie" to signCookie) else emptyMap())
                                        this.quality = getQualityFromName(resText)
                                    }
                                )
                                foundLinks = true
                            }

                            // subtitles
                            val subLinks = listOf(
                                "$movieBox/wefeed-mobile-bff/subject-api/get-stream-captions?subjectId=$subjectId&streamId=$streamId",
                                "$movieBox/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$subjectId&resourceId=$streamId&episode=${episode ?: 0}"
                            )

                            for (subLink in subLinks) {
                                val subToken = generateXClientToken()
                                val subSign = generateXTrSignature("GET", "", "", subLink)

                                val subHeaders = mapOf(
                                    "User-Agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
                                    "Accept" to "",
                                    "Content-Type" to "",
                                    "X-Client-Info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","sp_code":""}""",
                                    "X-Client-Status" to "0",
                                    "x-client-token" to subToken,
                                    "x-tr-signature" to subSign
                                )

                                val subRes = app.get(subLink, headers = subHeaders)
                                if (subRes.code != 200) continue

                                val subRoot = mapper.readTree(subRes.body.string())
                                val captions = subRoot["data"]?.get("extCaptions")
                                if (captions != null && captions.isArray) {
                                    for (caption in captions) {
                                        val captionUrl = caption["url"]?.asText() ?: continue
                                        val lang = caption["language"]?.asText()
                                            ?: caption["lanName"]?.asText()
                                            ?: caption["lan"]?.asText()
                                            ?: "Unknown"
                                        subtitleCallback.invoke(
                                            newSubtitleFile(
                                                url = captionUrl,
                                                lang = "$lang (${language.capitalize()})"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    continue
                }
            }

            return foundLinks
        } catch (_: Exception) {
            return false
        }
    }

    suspend fun invokemorph(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val rawTitle = title ?: return

        fun md5(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun generateABC(
            title: String,
            year: Int,
            season: Int,
            episode: Int,
            mType: Int,
            ts: String
        ): String {
            val salt = base64Decode("JkxDYnUzaVlDN2xuMjRLN1A=")
            val signatureString = if (mType == 1) {
                "$title&&$season&$episode&$ts$salt"
            } else {
                "$title&$year&$season&$episode&$ts$salt"
            }
            return md5(signatureString)
        }

        fun generateUrl(
            title: String,
            year: Int,
            season: Int,
            episode: Int,
            mType: Int
        ): String {
            val ts = (System.currentTimeMillis() / 1000).toString()
            val abc = generateABC(title, year, season, episode, mType, ts)

            val encodedTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20")

            return base64Decode("aHR0cHM6Ly90ZWxlLm1vcnBodHYuY2x1Yi9hcGkvc2VhcmNoPw==") +
                    "abc=$abc&year=$year&season=$season&episode=$episode" +
                    "&title=$encodedTitle&ts=$ts&mType=$mType"
        }

        val finalYear = year ?: 0
        val finalSeason = season ?: 0
        val finalEpisode = episode ?: 0

        val url = generateUrl(rawTitle, finalYear, finalSeason, finalEpisode, 0)

        app.get(url).parsedSafe<Morph>()?.data?.amap {
            loadSourceNameExtractor("Morph", it.link, "", subtitleCallback, callback)
        }
    }


    suspend fun invokeSoapy(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val players = listOf("juliet", "romio")

        players.forEach { selectedPlayer ->
            val url = if (season == null) {
                "$soapy/embed/movies.php?tmdbid=$tmdbId&player=$selectedPlayer"
            } else {
                "$soapy/embed/series.php?tmdbid=$tmdbId&season=$season&episode=$episode&player=$selectedPlayer"
            }
            val iframe = app.get(url).documentLarge.select("iframe").attr("src")
            loadSourceNameExtractor("Soapy", iframe, soapy, subtitleCallback, callback)
        }
    }


    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    suspend fun invokevidrock(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val type = if (season == null) "movie" else "tv"
        val encoded = vidrockEncode(tmdbId.toString(), type, season, episode)
        val response = app.get("$vidrock/api/$type/$encoded").text
        val sourcesJson = JSONObject(response)

        val vidrockHeaders = mapOf(
            "Origin" to vidrock
        )

        sourcesJson.keys().forEach { key ->
            val sourceObj = sourcesJson.optJSONObject(key) ?: return@forEach
            val rawUrl = sourceObj.optString("url", null)
            val lang = sourceObj.optString("language", "Unknown")
            if (rawUrl.isNullOrBlank() || rawUrl == "null") return@forEach

            // Decode only if encoded
            val safeUrl = if (rawUrl.contains("%")) {
                URLDecoder.decode(rawUrl, "UTF-8")
            } else rawUrl

            when {
                safeUrl.contains("/playlist/") -> {
                    val playlistResponse = app.get(safeUrl, headers = vidrockHeaders).text
                    val playlistArray = JSONArray(playlistResponse)
                    for (j in 0 until playlistArray.length()) {
                        val item = playlistArray.optJSONObject(j) ?: continue
                        val itemUrl = item.optString("url", null) ?: continue
                        val res = item.optInt("resolution", 0)

                        callback.invoke(
                            newExtractorLink(
                                source = "Vidrock",
                                name = "Vidrock $lang",
                                url = itemUrl,
                                type = INFER_TYPE
                            ) {
                                this.headers = vidrockHeaders
                                this.quality = getQualityFromName("$res")
                            }
                        )
                    }
                }

                // Handle MP4 direct file
                safeUrl.contains(".mp4", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            source = "Vidrock",
                            name = "Vidrock $lang MP4",
                            url = safeUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.headers = vidrockHeaders
                        }
                    )
                }

                // Handle HLS/m3u8
                safeUrl.contains(".m3u8", ignoreCase = true) -> {
                    M3u8Helper.generateM3u8(
                        source = "Vidrock",
                        streamUrl = safeUrl,
                        referer = "",
                        quality = Qualities.P1080.value,
                        headers = vidrockHeaders
                    ).forEach(callback)
                }

                // Catch-all (just in case)
                else -> {
                    callback.invoke(
                        newExtractorLink(
                            source = "Vidrock",
                            name = "Vidrock $lang",
                            url = safeUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.headers = vidrockHeaders
                        }
                    )
                }
            }
        }
    }

    suspend fun invokeCinemaOS(
        imdbId: String? = null,
        tmdbId: Int? = null,
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        try {
            val sourceHeaders = mapOf(
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9",
                "Connection" to "keep-alive",
                "Referer" to cinemaOSApi,
                "Host" to "cinemaos.tech",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36",
                "sec-ch-ua" to "\"Not;A=Brand\";v=\"99\", \"Google Chrome\";v=\"139\", \"Chromium\";v=\"139\"",
                "sec-ch-ua-mobile" to "?0",
                "sec-ch-ua-platform" to "\"Windows\"",
                "Content-Type" to "application/json"
            )

            val fixTitle = title?.replace(" ", "+")
            val cinemaOsSecretKeyRequest = CinemaOsSecretKeyRequest(tmdbId = tmdbId.toString(),imdbId= imdbId
                ?: "", seasonId = season?.toString() ?: "", episodeId = episode?.toString() ?: "")
            val secretHash = cinemaOSGenerateHash(cinemaOsSecretKeyRequest,season != null)
            val type = if(season == null) {"movie"}  else {"tv"}
            val sourceUrl = if(season == null) {"$cinemaOSApi/api/provider?type=$type&tmdbId=$tmdbId&imdbId=$imdbId&t=$fixTitle&ry=$year&secret=$secretHash"} else {"$cinemaOSApi/api/provider?type=$type&tmdbId=$tmdbId&imdbId=$imdbId&seasonId=$season&episodeId=$episode&t=$fixTitle&ry=$year&secret=$secretHash"}
            val sourceResponse = app.get(sourceUrl, headers = sourceHeaders,timeout = 60).parsedSafe<CinemaOSReponse>()
            val decryptedJson = cinemaOSDecryptResponse(sourceResponse?.data,)
            val json = parseCinemaOSSources(decryptedJson.toString())
            json.forEach {
                try {
                    val extractorLinkType = if(it["type"]?.contains("hls",true) ?: false) { ExtractorLinkType.M3U8} else if(it["type"]?.contains("dash",true) ?: false){ ExtractorLinkType.DASH} else if(it["type"]?.contains("mp4",true) ?: false){ ExtractorLinkType.VIDEO} else { INFER_TYPE}
                    val bitrateQuality = if(it["bitrate"]?.contains("fhd",true) ?: false) { Qualities.P1080.value } else if(it["bitrate"]?.contains("hd",true) ?: false){ Qualities.P720.value} else { Qualities.P1080.value}
                    val quality =  if(it["quality"]?.isNotEmpty() == true && it["quality"]?.toIntOrNull() !=null) getQualityFromName(it["quality"]) else if (it["quality"]?.isNotEmpty() == true)  if(it["quality"]?.contains("fhd",true) ?: false) { Qualities.P1080.value } else if(it["quality"]?.contains("hd",true) ?: false){ Qualities.P720.value} else { Qualities.P1080.value} else bitrateQuality
                    callback.invoke(
                        newExtractorLink(
                            "CinemaOS [${it["server"]}] ${it["bitrate"]}  ${it["speed"]}".replace("\\s{2,}".toRegex(), " ").trim(),
                            "CinemaOS [${it["server"]}] ${it["bitrate"]} ${it["speed"]}".replace("\\s{2,}".toRegex(), " ").trim(),
                            url = it["url"].toString(),
                            type = extractorLinkType
                        )
                        {
                            this.headers = mapOf("Referer" to cinemaOSApi) + M3U8_HEADERS
                            this.quality = quality
                        }
                    )
                } catch (_: Exception) {
                    TODO("Not yet implemented")
                }
            }
        } catch (_: Exception) {
            TODO("Not yet implemented")
        }
    }


    @SuppressLint("UseKtx")
    suspend fun invokeVidlink(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url =
            if (season == null) "$vidlink/movie/$tmdbId" else "$vidlink/tv/$tmdbId/$season/$episode"

        val jsToClickPlay = """
        (() => {
            const btn = document.querySelector('.jw-icon-display.jw-button-color.jw-reset');
            return btn ? (btn.click(), "clicked") : "button not found";
        })();
    """.trimIndent()

        val vidlinkRegex = Regex("""\.pro/api/b.*""")

        val apifetch = WebViewResolver(
            interceptUrl = vidlinkRegex,
            additionalUrls = listOf(vidlinkRegex),
            script = jsToClickPlay,
            scriptCallback = { result -> Log.d("Vidlink", "JS Result: $result") },
            useOkhttp = false,
            timeout = 15_000L
        )

        val iframe = app.get(url, interceptor = apifetch).url
        val jsonString = app.get(iframe).body.string()

        val mapper = jacksonObjectMapper()
        val root: Vidlink = mapper.readValue(jsonString)
        val playlistParts = root.stream.playlist.split("?")
        val rawM3u8Url = playlistParts[0]

        val finalM3u8Url = run {
            val uri = root.stream.playlist.toUri()
            val hostParam = uri.getQueryParameter("host")
            if (hostParam != null) {
                val path = rawM3u8Url
                path
            } else {
                rawM3u8Url
            }
        }

        val headers = mapOf(
            "referer" to vidlink,
        )

        M3u8Helper.generateM3u8(
            "Vidlink",
            finalM3u8Url,
            vidlink,
            headers = headers
        ).forEach(callback)

        root.stream.captions.forEach { caption ->
            subtitleCallback(
                newSubtitleFile(
                    caption.language,
                    caption.url
                )
            )
        }
    }

    //Thanks to https://github.com/yogesh-hacker/MediaVanced/blob/main/sites/hlscdn.py
    suspend fun invokeKisskhAsia(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val userAgent =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"

        val headers = mapOf(
            "Referer" to "https://hlscdn.xyz/",
            "User-Agent" to userAgent,
            "X-Requested-With" to "XMLHttpRequest"
        )

        if (tmdbId == null) return

        val url = when {
            season != null && season > 1 -> {
                val epStr = episode?.toString()?.padStart(2, '0') ?: ""
                "https://hlscdn.xyz/e/$tmdbId-$season-$epStr"
            }

            else -> {
                val epStr = episode?.toString()?.padStart(2, '0') ?: ""
                "https://hlscdn.xyz/e/$tmdbId-$epStr"
            }
        }

        val responseText = app.get(url, headers = headers).text

        val token = Regex("window\\.kaken=\"(.*?)\"")
            .find(responseText)
            ?.groupValues?.getOrNull(1)

        if (token.isNullOrBlank()) return

        val mediaType = "text/plain".toMediaType()
        val body = token.toRequestBody(mediaType)

        val apiResponse = app.post(
            "https://hlscdn.xyz/api",
            headers = headers,
            requestBody = body
        ).text

        val json = JSONObject(apiResponse)

        val sources = json.optJSONArray("sources")
        if (sources != null && sources.length() > 0) {
            for (i in 0 until sources.length()) {
                val srcObj = sources.getJSONObject(i)
                val videoUrl = srcObj.optString("file") ?: continue
                val label = srcObj.optString("label", "Source")

                callback.invoke(
                    newExtractorLink(
                        source = "KisskhAsia",
                        name = "KisskhAsia - $label",
                        url = videoUrl
                    ) {
                        this.referer = "https://hlscdn.xyz/"
                        this.quality = Qualities.P720.value
                    }
                )
            }
        }

        val tracks = json.optJSONArray("tracks")
        if (tracks != null && tracks.length() > 0) {
            for (i in 0 until tracks.length()) {
                val trackObj = tracks.getJSONObject(i)
                val subUrl = trackObj.optString("file") ?: continue
                val label = trackObj.optString("label", "Unknown")

                subtitleCallback.invoke(
                    newSubtitleFile(label, subUrl)
                )
            }
        }
    }

    suspend fun invokemp4hydra(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (title == null) return

        val slug = title.createSlug()
        val mediaType = if (season == null) "movie" else "tv"
        val jsonPayload = if (season == null) {
            "[{\"s\":\"$slug-$year\",\"t\":\"$mediaType\"}]"
        } else {
            "[{\"s\":\"$slug\",\"t\":\"$mediaType\",\"se\":\"${season}\",\"ep\":\"${episode ?: ""}\"}]"
        }

        val requestBody: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("z", jsonPayload)
            .build()
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Accept-Language" to "en-GB,en-US;q=0.9,en;q=0.8",
            "Origin" to mp4hydra,
            "Referer" to "$mp4hydra/$mediaType/$slug",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin"
        )

        val responseBody = app.post(
            url = "$mp4hydra/info2?v=8",
            headers = headers,
            requestBody = requestBody
        ).body.string()

        val json = JSONObject(responseBody)
        val servers = json.optJSONObject("servers") ?: JSONObject()
        val serverUrls = servers.keys().asSequence()
            .mapNotNull { key ->
                servers.optString(key).takeIf { it.startsWith("http") }?.let { key to it }
            }
            .toList()

        val playlist = json.optJSONArray("playlist") ?: return

        if (mediaType == "movie") {
            for (i in 0 until playlist.length()) {
                val video = playlist.getJSONObject(i)
                val src = video.optString("src")
                video.optString("label", "")

                serverUrls.forEach { (serverName, serverUrl) ->
                    val fullUrl =
                        if (src.startsWith("http")) src else serverUrl.trimEnd('/') + "/" + src.trimStart(
                            '/'
                        )

                    M3u8Helper.generateM3u8(
                        "MP4Hydra [$serverName]",
                        fullUrl,
                        ""
                    ).forEach(callback)

                    val subs = video.optJSONArray("subs")
                    if (subs != null) {
                        for (j in 0 until subs.length()) {
                            val sub = subs.getJSONObject(j)
                            val subLabel = getLanguage(sub.optString("label", ""))
                            val subSrc = sub.optString("src", "")
                            subtitleCallback(newSubtitleFile(subLabel, subSrc))
                        }
                    }
                }
            }
        } else {
            val targetCode = "S%02dE%02d".format(season, episode)
            val matchedVideo = (0 until playlist.length())
                .map { playlist.getJSONObject(it) }
                .firstOrNull { video ->
                    val title = video.optString("title", "").uppercase()
                    title.contains(targetCode)
                } ?: return

            val src = matchedVideo.optString("src")

            serverUrls.forEach { (serverName, serverUrl) ->
                val fullUrl =
                    if (src.startsWith("http")) src else serverUrl.trimEnd('/') + "/" + src.trimStart(
                        '/'
                    )
                M3u8Helper.generateM3u8(
                    "MP4Hydra [$serverName]",
                    fullUrl,
                    ""
                ).forEach(callback)

                val subs = matchedVideo.optJSONArray("subs")
                if (subs != null) {
                    for (j in 0 until subs.length()) {
                        val sub = subs.getJSONObject(j)
                        val subLabel = getLanguage(sub.optString("label", ""))
                        val subSrc = sub.optString("src", "")
                        subtitleCallback(newSubtitleFile(subLabel, "$serverUrl$subSrc"))
                    }
                }
            }
        }
    }


    suspend fun invokeVidFast(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val STATIC_PATH =
            "hezushon/ira/2264ec23bfa5e4891e26d563e5daac61bcb05688/b544e02b"
        val url =
            if (season == null) "$vidfastProApi/movie/$tmdbId" else "$vidfastProApi/tv/$tmdbId/$season/$episode"
        val headers = mapOf(
            "Accept" to "*/*",
            "Referer" to vidfastProApi,
            "X-Csrf-Token" to "iwwuf3C7tleIfqxlgG5NUxOrOROfn5d9",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "X-Requested-With" to "XMLHttpRequest"
        )
        val response = app.get(url, headers = headers, timeout = 20).text
        val regex = Regex("""\\"en\\":\\"(.*?)\\"""")
        val match = regex.find(response)
        val rawData = match?.groupValues?.get(1)
        if (rawData.isNullOrEmpty()) {
            return
        }
        // AES encryption setup
        val keyHex = "1f9b96f4e6604062c39f69f4c2edd92210d44d185434b0d569b077a72975bf08"
        val ivHex = "70ed610a03c6a59c7967abf77db57f71"
        val aesKey = hexStringToByteArray2(keyHex)
        val aesIv = hexStringToByteArray2(ivHex)

        // Encrypt raw data
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(aesIv))
        val paddedData = padData(rawData.toByteArray(Charsets.UTF_8), 16)
        val aesEncrypted = cipher.doFinal(paddedData)

        // XOR operation
        val xorKey = hexStringToByteArray2("d6f87ef72c")
        val xorResult = aesEncrypted.mapIndexed { i, byte ->
            (byte.toInt() xor xorKey[i % xorKey.size].toInt()).toByte()
        }.toByteArray()

        // Encode XORed data
        val encodedFinal = customEncode(xorResult)

        // Get servers
        val apiServers = "$vidfastProApi/$STATIC_PATH/wfPFjh__qQ/$encodedFinal"
        val serversResponse = app.get(
            apiServers,
            timeout = 20,
            interceptor = CloudflareKiller(),
            headers = headers
        ).text
        if (serversResponse.isEmpty()) return
        val servers = parseServers(serversResponse)
        val urlList = mutableMapOf<String, String>()
        servers.forEach {
            try {
                val apiStream = "$vidfastProApi/${STATIC_PATH}/AddlBFe5/${it.data}"
                val streamResponse = app.get(apiStream, timeout = 20, headers = headers).text
                if (streamResponse.isNotEmpty()) {
                    val jsonObject = JSONObject(streamResponse)
                    val url = jsonObject.getString("url")
                    urlList[it.name] = url
                }
            } catch (_: Exception) {
                TODO("Not yet implemented")
            }
        }

        urlList.forEach {
            callback.invoke(
                newExtractorLink(
                    "VidFastPro [${it.key}]",
                    "VidFastPro [${it.key}]",
                    url = it.value,
                )
                {
                    this.quality = Qualities.P1080.value
                }
            )
        }


    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun invokeVidPlus(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "Referer" to vidPlusApi,
            "Origin" to vidPlusApi,
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "X-Requested-With" to "XMLHttpRequest"
        )
        val data = mapOf(
            "id" to tmdbId,
            "key" to "cGxheWVyLnZpZHNyYy5jb19zZWNyZXRLZXk="
        )
        val encoded = base64Encode(data.toJson().toByteArray())
        val apiUrl = "$vidPlusApi/api/tmdb?params=cbc7.$encoded.9lu"
        val response = app.get(apiUrl, headers = headers).text
        val jsonObject = JSONObject(response)
        val dataJson = jsonObject.getJSONObject("data")
        // Get required data
        val imdbId = dataJson.getString("imdb_id")
        val title = dataJson.getString("title")
        val releaseDate = dataJson.getString("release_date")
        val releaseYear = releaseDate.split("-")[0]

        // Build request parameters and fetch encrypted response
        val requestArgs = listOf(title, releaseYear, imdbId).joinToString("*")
        val urlListMap = mutableMapOf<String, String>()
        val myMap = listOf(
            "Orion",
            "Minecloud",
            "Viet",
            "Crown",
            "Joker",
            "Soda",
            "Beta",
            "Gork",
            "Monk",
            "Fox",
            "Leo",
            "4K",
            "Adam",
            "Sun",
            "Maxi",
            "Indus",
            "Tor",
            "Hindi",
            "Delta",
            "Ben",
            "Pearl",
            "Tamil",
            "Ruby",
            "Tel",
            "Mal",
            "Kan",
            "Lava"
        )
        for ((index, entry) in myMap.withIndex()) {
            try {
                val serverId = index + 1
                val serverUrl =
                    if (season == null) "$vidPlusApi/api/server?id=$tmdbId&sr=$serverId&args=$requestArgs" else "$vidPlusApi/api/server?id=$tmdbId&sr=$serverId&ep=$episode&ss=$season&args=$requestArgs"

                val apiResponse = app.get(serverUrl, headers = headers, timeout = 20).text

                if (apiResponse.contains("\"data\"", ignoreCase = true)) {
                    val decodedPayload =
                        String(base64DecodeArray(JSONObject(apiResponse).getString("data")))
                    val payloadJson = JSONObject(decodedPayload)

                    val ciphertext = base64DecodeArray(payloadJson.getString("encryptedData"))
                    val password = payloadJson.getString("key")
                    val salt = hexStringToByteArray2(payloadJson.getString("salt"))
                    val iv = hexStringToByteArray2(payloadJson.getString("iv"))
                    val derivedKey = derivePbkdf2Key(password, salt, 1000, 32)
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        SecretKeySpec(derivedKey, "AES"),
                        IvParameterSpec(iv)
                    )
                    val decryptedText = unpadData(cipher.doFinal(ciphertext))
                    val decryptedString = String(decryptedText)

                    val regex = Regex("\"url\":\"(.*?)\",")
                    val match = regex.find(decryptedString)
                    val streamURl = match?.groupValues?.get(1)
                    if (!streamURl.isNullOrEmpty()) {
                        var finalStreamUrl = streamURl
                        if (!hasHost(streamURl)) {
                            finalStreamUrl = app.head(
                                "$vidPlusApi$streamURl",
                                headers = headers,
                                allowRedirects = false
                            ).headers.get("Location")
                        }


                        urlListMap[entry] = finalStreamUrl.toString()
                    }
                }
            } catch (_: Exception) {
                TODO("Not yet implemented")
            }
        }

        urlListMap.forEach {
            callback.invoke(
                newExtractorLink(
                    "VidPlus [${it.key}]",
                    "VidPlus [${it.key}]",
                    url = it.value,
                )
                {
                    this.quality = Qualities.P1080.value
                    this.headers = mapOf("Origin" to vidPlusApi)
                }
            )
        }
    }

    suspend fun invokeToonstream(
        title: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val toonStreamAPI = getDomains()?.toonstream ?: return

        val safeTitle = title?.takeIf { it.isNotBlank() } ?: return

        val url = buildString {
            append(toonStreamAPI)
            append(
                if (season == null) "/movies/${safeTitle.createSlug()}/"
                else "/episode/${safeTitle.createSlug()}-${season}x${episode}/"
            )
        }

        runCatching {
            val document = app.get(url, referer = toonStreamAPI).documentLarge
            document.selectFirst("div.video > iframe")?.attr("data-src")?.let { src ->
                val innerDoc = app.get(src).documentLarge
                innerDoc.select("div.Video > iframe").forEach { iframe ->
                    loadSourceNameExtractor(
                        "ToonStream",
                        iframe.attr("src"),
                        "",
                        subtitleCallback,
                        callback
                    )
                }
            }
        }.onFailure {
            Log.e("ToonStream", "Error loading ToonStream: ${it.message}")
        }
    }

    suspend fun invokeNuvioStreams(
        imdbId: String?,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (imdbId.isNullOrBlank()) return

        val api = buildString {
            append(BuildConfig.Nuviostreams)
            append("/stream/")
            if (season == null) {
                append("movie/$imdbId.json")
            } else {
                append("series/$imdbId:$season:$episode.json")
            }
        }

        val response = app.get(api).parsedSafe<NuvioStreams>() ?: return

        response.streams.forEach { stream ->
            callback.invoke(
                newExtractorLink(
                    name = "NuvioStreams ${stream.name}",
                    source = "NuvioStreams",
                    url = stream.url,
                    type = INFER_TYPE
                )
            )
        }
    }

    suspend fun invokeXDmovies(
        title: String? = null,
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Referer" to "$XDmoviesAPI/",
            "x-requested-with" to "XMLHttpRequest",
            "x-auth-token" to base64Decode("NzI5N3Nra2loa2Fqd25zZ2FrbGFrc2h1d2Q=")
        )
        val searchData = app.get(
            "$XDmoviesAPI/php/search_api.php?query=$title&fuzzy=true", headers
        ).parsedSafe<SearchData>() ?: return
        val matched = searchData.firstOrNull { it.tmdb_id == id } ?: return
        val document = app.get(XDmoviesAPI + matched.path).documentLarge

        if (season == null) {
            val link = document.select("div.download-item a").attr("href")
            val codec = extractCodec(document.select("div.download-item > div").text())
            loadSourceNameExtractor("XDmovies ", link, "", subtitleCallback, callback, null, codec)
        } else {
            val epRegex = Regex(
                "S${season.toString().padStart(2, '0')}E${
                    episode.toString().padStart(2, '0')
                }", RegexOption.IGNORE_CASE
            )

            val episodeCards = document.select("div.episode-card").filter { card ->
                epRegex.containsMatchIn(card.selectFirst(".episode-title")?.text().orEmpty())
            }

            if (episodeCards.isEmpty()) return

            for (card in episodeCards) {
                val title = card.selectFirst(".episode-title")?.text().orEmpty()
                val link = card.selectFirst("a.movie-download-btn[href], a.download-button[href]")
                    ?.attr("href")?.trim().orEmpty()

                if (link.isNotEmpty()) {
                    val codec = card.selectFirst(".codec-badge")?.text()?.trim().orEmpty()
                        .ifBlank { extractCodec(title) }
                    try {
                        loadSourceNameExtractor(
                            "XDmovies ",
                            link,
                            "",
                            subtitleCallback,
                            callback,
                            null,
                            codec
                        )
                    } catch (_: Throwable) {
                    }
                }
            }
        }

    }

    private fun extractCodec(title: String): String {
        val codecRegex = Regex("(?i)(av1|h\\.264|h\\.265|hdr|sdr)")
        val match = codecRegex.find(title)?.value ?: return ""
        return match
    }

    suspend fun invokeKimcartoon(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val doc = if (season == null || season == 1) {
            app.get("$kimcartoonAPI/Cartoon/$fixTitle").document
        } else {
            val res = app.get("$kimcartoonAPI/Cartoon/$fixTitle-Season-$season")
            if (res.url == "$kimcartoonAPI/") app.get("$kimcartoonAPI/Cartoon/$fixTitle-Season-0$season").document else res.document
        }

        val iframe = if (season == null) {
            doc.select("div.full.item_ep h3 a").firstNotNullOf { it.attr("href") }
        } else {
            doc.select("div.full.item_ep h3 a").find {
                it.attr("href").contains(Regex("(?i)Episode-0*$episode"))
            }?.attr("href")
        } ?: return
        val id = iframe.substringAfter("id=")
        val headers = mapOf(
            "referer" to "https://am.vidstream.vip",
            "Origin" to "https://am.vidstream.vip",
            "User-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"
        )
        app.get(iframe).document.select("#selectServer option").amap { s ->
            val server = s.attr("sv")
            val href = app.post(
                "${kimcartoonAPI}/ajax/anime/load_episodes_v2?s=$server",
                data = mapOf("episode_id" to id)
            ).document.selectFirst("iframe")?.attr("src")?.replace("\\\"", "") ?: ""
            if (href.contains("vidstream")) {
                val response = app.get(href, referer = kimcartoonAPI).toString()
                val m3u8 =
                    Regex("file\":\"(.*?m3u8.*?)\"").find(response)?.groupValues?.getOrNull(1)
                if (m3u8 != null) {
                    callback.invoke(
                        newExtractorLink(
                            "KimCartoon",
                            "KimCartoon ${server.uppercase()}",
                            url = m3u8,
                            INFER_TYPE
                        ) {
                            this.quality = Qualities.P1080.value
                            this.headers = headers
                        }
                    )
                } else {
                    ""
                }
            } else loadSourceNameExtractor(
                "KimCartoon ${server.uppercase()}",
                href,
                "https://am.vidstream.vip",
                subtitleCallback,
                callback
            )
        }
    }

    suspend fun invokeYflix(
        title: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (title.isNullOrBlank()) return

        val type = if (season == null) "movie" else "tv"
        val searchUrl = "$yFlix/browser?keyword=${title.trim().replace(" ", "-")}&type=$type"

        val matchedElement = runCatching {
            val doc = app.get(searchUrl).documentLarge
            val results = doc.select("div.item")
            results.firstOrNull { item ->
                val titleElement = item.selectFirst("div.info a")
                val name = titleElement?.text()?.trim() ?: return@firstOrNull false
                name.contains(title, ignoreCase = true)
            }?.selectFirst("div.info a")
        }.getOrNull() ?: return

        val href = yFlix + matchedElement.attr("href")
        val document = app.get(href).documentLarge

        val typee = if (season == null) TvType.Movie else TvType.TvSeries

        val keyword = href.substringAfter("/watch/").substringBefore(".")
        val dataid = document.select("#movie-rating").attr("data-id")
        val decoded = yflixDecode(dataid)

        val epRes = app
            .get("$yFlix/ajax/episodes/list?keyword=$keyword&id=$dataid&_=$decoded")
            .parsedSafe<YflixResponse>()
            ?.getDocument()

        val movieNode = epRes?.selectFirst("ul.episodes a")

        suspend fun fetchAndProcessServers(eid: String) {
            val decodetoken = try {
                yflixDecode(eid)
            } catch (e: Exception) {
                Log.d("Yflix", "Failed to decode eid token: ${e.message}")
                return
            }

            val listResp = runCatching {
                app.get("$yFlix/ajax/links/list?eid=$eid&_=$decodetoken")
                    .parsedSafe<YflixResponse>()
            }.getOrNull()

            val doc = listResp?.getDocument()
            if (doc == null) {
                Log.d("Yflix", "No document returned for links list (eid=$eid)")
                return
            }

            val servers = doc.select("li.server")
            if (servers.isEmpty()) {
                Log.d("Yflix", "No servers found in the links list (eid=$eid)")
                return
            }

            servers.forEach { serverNode ->
                try {
                    val lid = serverNode.attr("data-lid").trim()
                    if (lid.isBlank()) {
                        Log.d("Yflix", "Skipping server with empty lid (eid=$eid)")
                        return@forEach
                    }

                    val serverName =
                        serverNode.selectFirst("span")?.text()?.trim()?.ifEmpty { "Server" }

                    val decodelid = try {
                        yflixDecode(lid)
                    } catch (e: Exception) {
                        Log.d("Yflix", "Failed to decode lid ($lid): ${e.message}")
                        return@forEach
                    }

                    val viewResp = runCatching {
                        app.get("$yFlix/ajax/links/view?id=$lid&_=$decodelid")
                            .parsedSafe<YflixResponse>()
                    }.getOrNull()

                    val result = viewResp?.result
                    if (result.isNullOrBlank()) {
                        Log.d("Yflix", "Empty result for server $serverName (lid=$lid)")
                        return@forEach
                    }

                    val decodedIframePayload = try {
                        yflixDecodeReverse(result)
                    } catch (e: Exception) {
                        Log.d("Yflix", "Failed to decodeReverse for lid=$lid : ${e.message}")
                        return@forEach
                    }

                    val iframeUrl = try {
                        yflixextractVideoUrlFromJson(decodedIframePayload)
                    } catch (e: Exception) {
                        Log.d("Yflix", "Failed to extract video url for lid=$lid : ${e.message}")
                        null
                    }

                    if (iframeUrl.isNullOrBlank()) {
                        Log.d(
                            "Yflix",
                            "No iframe/video url extracted for server $serverName (lid=$lid)"
                        )
                        return@forEach
                    }

                    val displayName = "⌜ YFlix ⌟  |  $serverName"
                    loadExtractor(iframeUrl, displayName, subtitleCallback, callback)
                } catch (inner: Exception) {
                    Log.d("Yflix", "Error processing server node: ${inner.message}")
                }
            }
        }

        val eid = if (typee == TvType.TvSeries) {
            val targetSeason = season ?: return
            val targetEpisode = episode ?: return

            val seasonBlocks = epRes?.select("ul.episodes") ?: return
            var found: String? = null
            loop@ for (seasonBlock in seasonBlocks) {
                val seasonNumber = seasonBlock.attr("data-season").toIntOrNull() ?: 1
                if (seasonNumber != targetSeason) continue

                val eps = seasonBlock.select("a")
                for ((index, ep) in eps.withIndex()) {
                    val epNum = ep.attr("num").toIntOrNull() ?: (index + 1)
                    if (epNum == targetEpisode) {
                        found = ep.attr("eid")
                        break@loop
                    }
                }
            }
            found
        } else {
            movieNode?.attr("eid")
        } ?: return
        fetchAndProcessServers(eid)
    }

    suspend fun invokeMoviesApi(
        id: Int?,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        if (id == null) return

        val href =
            if (season == null) "$moviesClubApi/movie/$id" else "$moviesClubApi/tv/$id-$season-$episode"
        val pageDoc = runCatching { app.get(href).document }.getOrNull() ?: return
        val iframeElement = pageDoc.selectFirst("iframe[src], iframe[data-src]") ?: return
        val iframeSrc = iframeElement.attr("src").ifEmpty { iframeElement.attr("data-src") }
        if (iframeSrc.isEmpty()) return
        val iframeDoc = runCatching { app.get(iframeSrc).document }.getOrNull() ?: return
        val scriptData = iframeDoc.select("script")
            .firstOrNull { e ->
                val d = e.data()
                d.contains("function(p,a,c,k,e,d)")
            }?.data() ?: iframeDoc.selectFirst("script")?.data() ?: return
        val unPacked = runCatching { getAndUnpack(scriptData) }.getOrNull() ?: scriptData
        val m3u8 = Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)

        if (m3u8 != null) {
            M3u8Helper.generateM3u8(
                "MoviesApi Club",
                m3u8,
                iframeSrc,
                headers = mapOf("Referer" to iframeSrc)
            ).forEach(callback)
        }
    }

    suspend fun invokecinemacity(
        imdbId: String?,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (imdbId.isNullOrBlank()) return

        val headers = mapOf(
            "Cookie" to base64Decode("ZGxlX3VzZXJfaWQ9MzI3Mjk7IGRsZV9wYXNzd29yZD04OTQxNzFjNmE4ZGFiMThlZTU5NGQ1YzY1MjAwOWEzNTs=")
        )

        val searchUrl =
            "$cinemacity/index.php?do=search&subaction=search&search_start=1&full_search=0&story=$imdbId"

        val pageUrl = app.get(searchUrl, headers)
            .document
            .selectFirst("div.dar-short_item > a")
            ?.attr("href")
            ?: return

        val script = app.get(pageUrl, headers)
            .document
            .select("script:containsData(atob)")
            .getOrNull(1)
            ?.data()
            ?: return

        val playerJson = JSONObject(
            base64Decode(
                script.substringAfter("atob(\"").substringBefore("\")")
            ).substringAfter("new Playerjs(").substringBeforeLast(");")
        )


        val fileArray = JSONArray(playerJson.getString("file"))

        fun extractQuality(url: String): Int {
            return when {
                url.contains("2160p") -> Qualities.P2160.value
                url.contains("1440p") -> Qualities.P1440.value
                url.contains("1080p") -> Qualities.P1080.value
                url.contains("720p") -> Qualities.P720.value
                url.contains("480p") -> Qualities.P480.value
                url.contains("360p") -> Qualities.P360.value
                else -> Qualities.Unknown.value
            }
        }

        suspend fun emitExtractorLinks(files: String) {

            callback.invoke(
                newExtractorLink(
                    "CineCity",
                    "CineCity",
                    files,
                    INFER_TYPE
                ) {
                    referer = pageUrl
                    quality = extractQuality(files)
                }
            )
        }

        val first = fileArray.getJSONObject(0)

        // MOVIE
        if (!first.has("folder")) {
            emitExtractorLinks(
                files = first.getString("file")
            )
            return
        }

        // SERIES
        for (i in 0 until fileArray.length()) {
            val seasonJson = fileArray.getJSONObject(i)

            val seasonNumber = Regex("Season\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find(seasonJson.optString("title"))
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: continue

            if (season != null && seasonNumber != season) continue

            val episodes = seasonJson.getJSONArray("folder")
            for (j in 0 until episodes.length()) {
                val epJson = episodes.getJSONObject(j)

                val episodeNumber = Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(epJson.optString("title"))
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                    ?: continue

                if (episode != null && episodeNumber != episode) continue

                emitExtractorLinks(
                    files = epJson.getString("file")
                )
            }
        }
    }


    suspend fun invokeEmbedMaster(
        imdbId: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (imdbId.isNullOrBlank()) return

        val session_token_regex =
            Regex("""var\s+token\s*=\s*['"]([^'"]+)['"]""")

        val scrapemaster_headers = mapOf(
            "Accept" to "application/json",
            "Origin" to "https://embdmstrplayer.com",
            "Referer" to "https://embdmstrplayer.com/",
            "User-Agent" to USER_AGENT
        )


        val embedPageUrl = when (season) {
            null -> "$embedmaster/movie/$imdbId"
            else -> "$embedmaster/tv/$imdbId/$season/$episode"
        }

        val embedResponse = app.get(embedPageUrl)
        val embedDocument = embedResponse.document
        val embedBaseUrl = getBaseUrl(embedResponse.url)

        val watchActionPath = embedDocument
            .select("form[action]")
            .attr("action")
            .takeIf { it.isNotBlank() }
            ?: return

        val attestToken = embedDocument
            .selectFirst("input[name=attest]")
            ?.attr("value")
            ?: return

        val postBody = FormBody.Builder()
            .add("attest", attestToken)
            .build()

        val postHeaders = mapOf(
            "Origin" to embedBaseUrl,
            "Referer" to embedPageUrl,
            "User-Agent" to USER_AGENT,
            "Content-Type" to "application/x-www-form-urlencoded"
        )

        val watchPageText = app.post(
            embedBaseUrl + watchActionPath,
            requestBody = postBody,
            headers = postHeaders
        ).text

        val sessionToken = session_token_regex
            .find(watchPageText)
            ?.groupValues
            ?.get(1)
            ?: return

        val sourcesText = app.get(
            "https://scrapemaster.net/api/sources/$sessionToken",
            headers = scrapemaster_headers
        ).text

        val jsonMapper = jacksonObjectMapper()

        sourcesText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.first() == '{' && it.last() == '}' }
            .mapNotNull {
                runCatching {
                    jsonMapper.readValue<EmbedmasterSourceItem>(it)
                }.getOrNull()
            }
            .filter { it.type == "server" && it.sourceUrl.isNotBlank() }
            .forEach { source ->

                val playResponse = app.get(
                    "https://embdmstrplayer.com/play/${source.sourceUrl}"
                )

                val doc = playResponse.document
                val baseUrl = getBaseUrl(playResponse.url)

                val candidateUrls = mutableSetOf<String>()

                doc.select("iframe[src], a[href], source[src], video[src]").forEach { el ->
                    val attrName = if (el.hasAttr("src")) "src" else "href"
                    val rawValue = el.attr(attrName).trim()

                    if (rawValue.isEmpty()) return@forEach

                    val finalUrl = when {
                        rawValue.startsWith("http") -> rawValue
                        rawValue.startsWith("//") -> "https:$rawValue"
                        rawValue.startsWith("/") -> baseUrl + rawValue
                        else -> "$baseUrl/$rawValue"
                    }

                    candidateUrls.add(finalUrl)
                }

                candidateUrls.forEach { url ->
                    if (!url.contains(".php", ignoreCase = true)) {
                        loadExtractor(
                            url,
                            embedmaster,
                            subtitleCallback,
                            callback
                        )
                        return@forEach
                    }

                    val phpPageText = app.get(url).text
                    val playerFileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")

                    val realMediaUrl = playerFileRegex
                        .find(phpPageText)
                        ?.groupValues
                        ?.get(1)
                        ?: return@forEach

                    callback.invoke(
                        newExtractorLink(
                            "EmbedMaster",
                            "EmbedMaster ${source.sourceName}",
                            realMediaUrl,
                            ExtractorLinkType.M3U8
                        )
                        {
                            this.quality = Qualities.P1080.value
                        }
                    )

                }

            }
    }


    suspend fun invokeHexa(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {

        val key = generateHexKey32()
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Accept" to "plain/text",
            "X-Api-Key" to key
        )

        val api = if (season == null) {
            "$hexaSU/api/tmdb/movie/$tmdbId/images"
        } else {
            "$hexaSU/api/tmdb/tv/$tmdbId/season/$season/episode/$episode/images"
        }

        val encryptedstring = app.get(api, headers).text


        val jsonBody = """{"text":"$encryptedstring","key":"$key"}""".toRequestBody("application/json; charset=utf-8".toMediaType())

        val response = app.post(
            "https://enc-dec.app/api/dec-hexa",
            headers = mapOf("Content-Type" to "application/json"),
            requestBody = jsonBody
        ).text

        val root = JSONObject(response)
        if (root.optInt("status") != 200) return

        val sources = root
            .optJSONObject("result")
            ?.optJSONArray("sources")
            ?: return

        for (i in 0 until sources.length()) {
            val obj = sources.optJSONObject(i) ?: continue
            val server = obj.optString("server")
            val url = obj.optString("url")
            if (url.isNotEmpty()) {
                M3u8Helper.generateM3u8(
                    "HexaSU ${server.capitalize()}",
                    url,
                    "https://hexa.su",
                ).forEach(callback)
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun invokBidsrc(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {

        val headers = mapOf("referer" to bidSrc)

        val api = if (season == null) {
            "$bidSrc/api/movie/$tmdbId"
        } else {
            "$bidSrc/api/tv/$tmdbId/$season/$episode"
        }

        val response = app.get(api, timeout = 30, headers = headers).parsedSafe<BidSrcResponse>()
        response?.servers?.forEach { server ->
            val decodedUrl = String(Base64.getDecoder().decode(server.url)).reversed()
            val finalUrl = decodedUrl.substringAfter("/api/proxy?url=")
            val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                URLDecoder.decode(finalUrl, StandardCharsets.UTF_8)
            } else {
                TODO("VERSION.SDK_INT < TIRAMISU")
            }
            callback.invoke(
                newExtractorLink(
                    "Bidsrc",
                    "Bidsrc ${server.name}",
                    decoded,
                    if(decoded.contains("m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                )
                {
                    this.quality = Qualities.P1080.value
                    this.headers = if(server.headers != null) mapOf(
                                    "Referer" to server.headers.referer,
                                    "Origin" to server.headers.origin) else mapOf()
                }
            )
        }
    }

    suspend fun invokFlixindia(
        title: String?,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (title.isNullOrBlank()) return

        val searchTitle = if (season == null) "${title.replace(":","")} $year" else "${title.replace(":","")} S0${season}E0$episode"

        val (sessionId, csrfToken) = getSessionAndCsrfforFlixindia(flixindia) ?: return

        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "*/*",
            "Referer" to "$flixindia/",
            "Origin" to flixindia.removeSuffix("/"),
            "X-Requested-With" to "XMLHttpRequest",
            "Cookie" to "PHPSESSID=$sessionId"
        )

        val body = mapOf(
            "action" to "search",
            "csrf_token" to csrfToken,
            "q" to searchTitle
        )

        val results = app.post(
            url = "$flixindia/",
            data = body,
            headers = headers,
            timeout = 30
        ).parsedSafe<Flixindia>()?.results.orEmpty()

        results.forEach { item ->
            val href = item.url
            when {
                "hubcloud" in href -> HubCloud().getUrl(href, "FlixIndia", subtitleCallback, callback)
                "gdlink" in href -> GDFlix().getUrl(href, "FlixIndia", subtitleCallback, callback)
                else -> loadExtractor(href, "", subtitleCallback, callback)
            }
        }
    }

    suspend fun invokeHindmoviez(
        id: String?,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = getDomains()?.hindmoviez ?: return

        if (id.isNullOrBlank()) return

        val searchDoc = app.get("$api/?s=$id", timeout = 50L).document
        val entries = searchDoc.select("h2.entry-title > a")

        entries.amap { entry ->
            val pageDoc = app.get(entry.attr("href"), timeout = 50L).document
            val buttons = pageDoc.select("a.maxbutton")

            if (episode == null) {
                // Movie
                buttons.amap { btn ->
                    val intermediateDoc = app.get(btn.attr("href"), timeout = 50L).document
                    val link = intermediateDoc.selectFirst("a.get-link-btn")
                        ?.attr("href")
                        ?: return@amap

                    getHindMoviezLinks("Hindmoviez", link, callback)
                }
            } else {
                // TV Episode
                buttons.amap { btn ->
                    val headerText = btn.parent()
                        ?.parent()
                        ?.previousElementSibling()
                        ?.text()
                        .orEmpty()

                    if (!headerText.contains("Season $season", ignoreCase = true)) return@amap

                    val episodeDoc = app.get(btn.attr("href"), timeout = 50L).document
                    val episodeLink = episodeDoc
                        .select("h3 > a")
                        .getOrNull(episode - 1)
                        ?.attr("href")
                        ?: return@amap

                    getHindMoviezLinks("Hindmoviez", episodeLink, callback)
                }
            }
        }
    }


}





