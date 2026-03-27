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
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newSubtitleFile
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.select.Elements
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.mapOf
import kotlin.collections.orEmpty
import kotlin.math.max


val session = Session(Requests().baseClient)
val globalSemaphore = Semaphore(5)

val webMutex = Mutex()
private val streamPlayExtractorGson by lazy { Gson() }
private val streamPlayExtractorMapper by lazy { jacksonObjectMapper() }
private val nonWordSplitRegex = Regex("\\W+")
private val normalizeAlphaNumSpaceRegex = Regex("[^a-z0-9 ]")
private val normalizeAlphaNumRegex = Regex("[^a-z0-9]")
private val wyzieSubListType by lazy { object : TypeToken<List<WyZIESUB>>() {}.type }

object StreamPlayExtractor : StreamPlay() {

    private val cloudflareKiller by lazy { CloudflareKiller() }

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

        val headers = mapOf(
            "Content-Type" to "application/x-www-form-urlencoded",
            "Referer" to url,
        )

        val framesrc = app.post(
            url = url,
            data = mapOf("pls" to "pls"),
            headers = headers
        ).document.selectFirst("iframe#iframesrc")?.attr("data-src") ?: return

        val ref = getBaseUrl(framesrc)
        val id = framesrc.substringAfter("id=", "")
            .substringBefore("&")
            .takeIf { it.isNotBlank() } ?: return

        loadExtractor(
            "https://uqloads.xyz/e/$id",
            "$ref/",
            subtitleCallback,
            callback
        )
    }

    suspend fun invokeMultiEmbed(
        imdbId: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (imdbId.isNullOrBlank()) return

        val userAgent =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"

        val headers = mapOf(
            "User-Agent" to userAgent,
            "Referer" to MultiEmbedAPI,
            "X-Requested-With" to "XMLHttpRequest"
        )

        val baseUrl = buildString {
            append(MultiEmbedAPI)
            append("/?video_id=")
            append(imdbId)
            if (season != null && episode != null) {
                append("&s=$season&e=$episode")
            }
        }

        val resolvedUrl = safeGet(baseUrl, headers = headers).url

        val postData = mapOf(
            "button-click" to "ZEhKMVpTLVF0LVBTLVF0LVAtMGs1TFMtUXpPREF0TC0wLVYzTi0wVS1RTi0wQTFORGN6TmprLTU=",
            "button-referer" to ""
        )

        val pageHtml = app.post(resolvedUrl, data = postData, headers = headers).text

        val token = Regex("""load_sources\("([^"]+)"\)""")
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
        val semaphore = Semaphore(3)
        sourcesDoc.select("li").amap { server ->

            semaphore.withPermit {

                val serverId = server.attr("data-server")
                val videoId = server.attr("data-id")

                if (serverId.isBlank() || videoId.isBlank()) return@amap

                val playUrl =
                    "https://streamingnow.mov/playvideo.php" +
                            "?video_id=${videoId.substringBefore("=")}" +
                            "&server_id=$serverId" +
                            "&token=$token" +
                            "&init=1"

                runCatching {
                    val playHtml = safeGet(playUrl, headers = headers).text
                    val iframeUrl = Jsoup.parse(playHtml)
                        .selectFirst("iframe.source-frame.show")
                        ?.attr("src")
                        ?: return@runCatching

                    val iframeHtml = safeGet(iframeUrl, headers = headers).text

                    val fileUrl =
                        Jsoup.parse(iframeHtml)
                            .selectFirst("iframe.source-frame.show")
                            ?.attr("src")
                            ?: Regex("""file:"(https?://[^"]+)"""")
                                .find(iframeHtml)
                                ?.groupValues
                                ?.get(1)
                            ?: return@runCatching

                    when {
                        fileUrl.contains(".m3u8", ignoreCase = true) || fileUrl.contains(
                            ".json",
                            ignoreCase = true
                        ) -> {

                            M3u8Helper.generateM3u8(
                                "MultiEmbed VIP",
                                fileUrl,
                                MultiEmbedAPI
                            ).forEach(callback)
                        }

                        else -> {
                            val url = fileUrl.lowercase()
                            when {
                                "vidsrc" in url -> return@amap

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
    }


    suspend fun invokeMultimovies(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val multimoviesApi = getDomains()?.multiMovies ?: return
        if (title.isNullOrBlank()) return

        val fixTitle = title.createSlug() ?: return

        val url = if (season == null) {
            "$multimoviesApi/movies/$fixTitle"
        } else {
            "$multimoviesApi/episodes/$fixTitle-${season}x${episode}"
        }

        val response = webMutex.withLock {
            safeGet(url, interceptor = cloudflareKiller)
        }

        if (response.code != 200) return

        val req = response.document
        if (req.text().contains("Just a moment", ignoreCase = true)) return

        val playerOptions = req.select("ul#playeroptionsul li").map {
            Triple(
                it.attr("data-post"),
                it.attr("data-nume"),
                it.attr("data-type")
            )
        }

        playerOptions.amap { (postId, nume, type) ->
            if (nume.contains("trailer", ignoreCase = true)) return@amap

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
                if (postResponse.code != 200) return@runCatching

                val responseData = tryParseJson<ResponseHash>(postResponse.text) ?: return@runCatching
                val embedUrl = responseData.embed_url

                val link = embedUrl
                    .trim()
                    .removeSurrounding("\"")
                    .takeIf { it.startsWith("http") }
                    ?: return@runCatching

                if (!link.contains("youtube", ignoreCase = true)) {
                    loadSourceNameExtractor(
                        "Multimovies",
                        link,
                        "$multimoviesApi/",
                        subtitleCallback,
                        callback
                    )
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
        val fixTitle = title?.createSlug() ?: return
        val url = if (season == null) {
            "$zshowAPI/movie/$fixTitle-$year"
        } else {
            "$zshowAPI/episode/$fixTitle-season-$season-episode-$episode"
        }
        val response = safeGet(url)
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

        val res = safeGet(
            url ?: return,
            interceptor = if (hasCloudflare) interceptor else null
        )
        val referer = getBaseUrl(res.url)
        val document = res.document
        document.select("ul#playeroptionsul > li").map {
            Triple(it.attr("data-post"), it.attr("data-nume"), it.attr("data-type"))
        }.amap { (id, nume, type) ->
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
                        )?.fixBloat() ?: return@amap
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
        val response = safeGet(url)
        if (response.code != 200) return
        val movieid =
            response.document.selectFirst("#embed-player")?.attr("data-movie-id") ?: return
        response.document.select("a.server.dropdown-item").amap {
            val dataid = it.attr("data-id")
            val link = extractMovieAPIlinks(dataid, movieid, MOVIE_API)
            if (link.contains(".stream"))
                loadExtractor(link, referer = MOVIE_API, subtitleCallback, callback)
        }
    }

    suspend fun invokeTokyoInsider(
        jptitle: String? = null,
        title: String? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        dubtype: String? = null
    ) {
        if (dubtype == null || (!dubtype.equals(
                "SUB",
                ignoreCase = true
            ) && !dubtype.equals("Movie", ignoreCase = true))
        ) return

        fun formatString(input: String?) = input?.replace(" ", "_").orEmpty()

        val jpFixTitle = formatString(jptitle)
        val fixTitle = formatString(title)
        val ep = episode ?: ""

        if (jpFixTitle.isBlank() && fixTitle.isBlank()) return

        var response =
            safeGet("https://www.tokyoinsider.com/anime/S/${jpFixTitle}_(TV)/episode/$ep")
        if (response.code != 200) return
        var doc = response.document

        if (doc.select("div.c_h2").text().contains("We don't have any files for this episode")) {
            response = safeGet("https://www.tokyoinsider.com/anime/S/${fixTitle}_(TV)/episode/$ep")
            if (response.code != 200) return
            doc = response.document
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
        engtitle: String? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) {
        if (dubtype == null || (!dubtype.equals(
                "SUB",
                ignoreCase = true
            ) && !dubtype.equals("Movie", ignoreCase = true))
        ) return

        val searchResponse = safeGet("https://anizone.to/anime?search=${jptitle}")
        if (searchResponse.code != 200) return

        val href = searchResponse.document
            .select("div.h-6.inline.truncate a")
            .firstOrNull {
                it.text().equals(jptitle, ignoreCase = true)
            }?.attr("href")
            ?: run {
                if (engtitle.isNullOrBlank()) null
                else {
                    val fallback = safeGet("https://anizone.to/anime?search=${engtitle}")
                    if (fallback.code != 200) null
                    else fallback.document
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
        val episodeResponse = safeGet("$href/$episode")
        if (episodeResponse.code != 200) return
        val m3u8 = episodeResponse.document.select("media-player").attr("src")
        if (m3u8.isBlank()) return
        callback.invoke(
            newExtractorLink(
                "Anizone",
                "⌜ Anizone ⌟",
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

        val searchResponse = safeGet(
            "$kissKhAPI/api/DramaList/Search?q=$title&type=$type",
            referer = "$kissKhAPI/"
        )
        if (searchResponse.code != 200) return

        val res = tryParseJson<ArrayList<KisskhResults>>(searchResponse.text) ?: return
        if (res.isEmpty()) return

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

        if (id == null) return

        val detailResponse = safeGet(
            "$kissKhAPI/api/DramaList/Drama/$id?isq=false",
            referer = "$kissKhAPI/Drama/${getKisskhTitle(contentTitle)}?id=$id"
        )
        if (detailResponse.code != 200) return

        val resDetail = detailResponse.parsedSafe<KisskhDetail>() ?: return
        val epsId = if (season == null) {
            resDetail.episodes?.firstOrNull()?.id
        } else {
            resDetail.episodes?.find { it.number == episode }?.id
        } ?: return

        val (kkey, kkey1) = coroutineScope {
            val videoKeyDeferred = async {
                try {
                    safeGet("${BuildConfig.KissKh}$epsId&version=2.8.10", timeout = 10000)
                        .parsedSafe<KisskhKey>()?.key
                } catch (_: Exception) {
                    null
                }
            }

            val subtitleKeyDeferred = async {
                try {
                    safeGet("${BuildConfig.KisskhSub}$epsId&version=2.8.10", timeout = 10000)
                        .parsedSafe<KisskhKey>()?.key
                } catch (_: Exception) {
                    null
                }
            }

            videoKeyDeferred.await() to subtitleKeyDeferred.await()
        }

        if (kkey == null || kkey1 == null) return

        val (sourcesData, subResponse) = coroutineScope {
            val sourcesDeferred = async {
                try {
                    safeGet(
                        "$kissKhAPI/api/DramaList/Episode/$epsId.png?err=false&ts=&time=&kkey=$kkey",
                        referer = "$kissKhAPI/Drama/${getKisskhTitle(contentTitle)}/Episode-${episode ?: 0}?id=$id&ep=$epsId&page=0&pageSize=100"
                    ).parsedSafe<KisskhSources>()
                } catch (_: Exception) {
                    null
                }
            }

            val subDeferred = async {
                try {
                    tryParseJson<List<KisskhSubtitle>>(safeGet("$kissKhAPI/api/Sub/$epsId&kkey=$kkey1").text)
                } catch (_: Exception) {
                    null
                }
            }

            sourcesDeferred.await() to subDeferred.await()
        }

        sourcesData?.let { source ->
            listOf(source.video, source.thirdParty).forEach { link ->
                val safeLink = link ?: return@forEach
                when {
                    safeLink.contains(".m3u8") || safeLink.contains(".mp4") -> {
                        val safe = safeLink.takeIf { it.startsWith("http") } ?: return@forEach

                        callback.invoke(
                            newExtractorLink(
                                "Kisskh",
                                "Kisskh",
                                safe,
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
                            ?: return@forEach
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

        subResponse?.forEach { sub ->
            val lang = getLanguage(sub.label ?: "UnKnown")
            subtitleCallback.invoke(newSubtitleFile(lang, sub.src ?: return@forEach))
        }
    }

    // Shared data class to hold pre-fetched anime metadata
    data class AnimeResolvedIds(
        val malId: Int?,
        val anidbEid: Int,
        val zoroIds: List<String>? = null,
        val zoroTitle: String?,
        val aniXL: String?,
        val kaasSlug: String?,
        val animepaheUrl: String?,
        val tmdbYear: Int?,
    )

    suspend fun resolveAnimeIds(
        title: String?,
        date: String?,
        airedDate: String?,
        season: Int?,
        episode: Int?,
    ): AnimeResolvedIds {
        val (_, malId) = convertTmdbToAnimeId(
            title, date, airedDate, if (season == null) TvType.AnimeMovie else TvType.Anime
        )

        var anijson: String? = null
        try {
            anijson = safeGet("https://api.ani.zip/mappings?mal_id=$malId").toString()
        } catch (e: Exception) {
            println("Error fetching mapping: ${e.message}")
        }

        val anidbEid = getAnidbEid(anijson ?: "{}", episode ?: 1) ?: 0

        val malsync = malId?.let {
            runCatching {
                safeGet("$malsyncAPI/mal/anime/$it").parsedSafe<MALSyncResponses>()?.sites
            }.getOrNull()
        }

        return AnimeResolvedIds(
            malId = malId,
            anidbEid = anidbEid,
            zoroIds = malsync?.zoro?.keys?.toList()?.filterNotNull(),
            zoroTitle = malsync?.zoro?.values?.firstNotNullOfOrNull { it["title"] }
                ?.replace(":", " "),
            aniXL = malsync?.AniXL?.values?.firstNotNullOfOrNull { it["url"] },
            kaasSlug = malsync?.KickAssAnime?.values?.firstNotNullOfOrNull { it["identifier"] },
            animepaheUrl = malsync?.animepahe?.values?.firstNotNullOfOrNull { it["url"] },
            tmdbYear = date?.substringBefore("-")?.toIntOrNull(),
        )
    }

    /*
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
            anijson = safeGet("https://api.ani.zip/mappings?mal_id=$malId").toString()
        } catch (e: Exception) {
            println("Error fetching or parsing mapping: ${e.message}")
        }

        val anidbEid = getAnidbEid(anijson ?: "{}", episode ?: 1) ?: 0

        val malsync = malId?.let {
            runCatching {
                safeGet("$malsyncAPI/mal/anime/$it").parsedSafe<MALSyncResponses>()?.sites
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
            { invokeKaido(zoroIds, episode, subtitleCallback, callback, dubStatus) },
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
            },
            {
                invokekuudere(
                    title,
                    season,
                    episode,
                    subtitleCallback,
                    callback,
                    dubStatus
                    )
            },
            )
    }

     */

    suspend fun invokeAniXL(
        url: String,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?
    ) {
        val baseurl = getBaseUrl(url)
        val response = safeGet(url)
        if (response.code != 200) return
        val document = response.document

        val episodeLink = baseurl + (document
            .select("a.btn")
            .firstOrNull { it.text().trim() == episode?.toString() }
            ?.attr("href") ?: return)

        val episodeResponse = safeGet(episodeLink)
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

    fun normalizeTitle(title: String?): String? {
        return title
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9 ]"), "")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
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
        val ephash = "d405d0edd690624b66baba3068e0edc3ac90f1597d898a1ec8db4e5c43c00fec"
        val queryhash = "a24c500a1b765c68ae1d8dd85174931f661c71369c89b92b88b75a725afc471c"
        val type = if (episode == null) "Movie" else "TV"

        val normalizedName = normalizeTitle(name)
        val normalizedEngTitle = normalizeTitle(engtitle)

        val variables = if (isMovie) {
            """{"search":{"query":"$name"},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}"""
        } else {
            """{"search":{"types":["$type"],"year":$year,"query":"$name"},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}"""
        }

        val query =
            "${BuildConfig.ANICHI_API}?variables=$variables&extensions={\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"$queryhash\"}}"
        val response = safeGet(query, referer = privatereferer)
            .parsedSafe<AnichiRoot>()
            ?.data?.shows?.edges ?: return

        val matched = response.find { item ->
            val itemName = normalizeTitle(item.name)
            val itemEnglishName = normalizeTitle(item.englishName)

            (normalizedName != null && (itemName == normalizedName || itemEnglishName == normalizedName)) ||
                    (normalizedEngTitle != null && (itemName == normalizedEngTitle || itemEnglishName == normalizedEngTitle))
        } ?: return

        val id = matched.id
        val langTypes = listOf("sub", "dub")

        langTypes.amap { lang ->
            if (isMovie || (dubtype != null && lang.contains(dubtype, ignoreCase = true))) {
                val epQuery =
                    """${BuildConfig.ANICHI_API}?variables={"showId":"$id","translationType":"$lang","episodeString":"${episode ?: 1}"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$ephash"}}"""
                val episodeLinks = safeGet(epQuery, referer = privatereferer)
                    .parsedSafe<AnichiEP>()
                    ?.data?.episode?.sourceUrls ?: return@amap

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

                        if (URI(sourceUrl).isAbsolute || sourceUrl.startsWith("//")) {
                            val fixedLink =
                                if (sourceUrl.startsWith("//")) "https:$sourceUrl" else sourceUrl
                            val host = fixedLink.getHost()

                            loadDisplaySourceNameExtractor(
                                "Allanime",
                                "⌜ Allanime ⌟ | $host | [${lang.uppercase()}]",
                                fixedLink,
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
                            safeGet(fixedLink, headers = headers)
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
                                    ).forEach(callback)
                                }


                                source.sourceName.contains("Uns") -> {
                                    loadDisplaySourceNameExtractor(
                                        "Allanime VidStack",
                                        "⌜ Allanime VidStack ⌟ | $host | [${lang.uppercase()}]",
                                        sourceUrl,
                                        "",
                                        subtitleCallback,
                                        callback
                                    )
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

        val id = safeGet("https://animepaheproxy.phisheranimepahe.workers.dev/?url=$url", headers)
            .document.selectFirst("meta[property=og:url]")
            ?.attr("content").toString().substringAfterLast("/")

        val animeData = safeGet(
            "https://animepaheproxy.phisheranimepahe.workers.dev/?url=$animepaheAPI/api?m=release&id=$id&sort=episode_desc&page=1",
            headers
        ).parsedSafe<animepahe>()?.data.orEmpty()

        val reversedData = animeData.reversed()

        val targetIndex = (episode ?: 1) - 1
        if (targetIndex !in reversedData.indices) return
        val session = reversedData[targetIndex].session

        val document = safeGet(
            "https://animepaheproxy.phisheranimepahe.workers.dev/?url=$animepaheAPI/play/$id/$session",
            headers
        ).document

        document.select("#resolutionMenu button").amap {
            val dubText = it.select("span").text().lowercase()
            val type = if ("eng" in dubText) "DUB" else "SUB"

            val qualityRegex = Regex("""(.+?)\s+·\s+(\d{3,4}p)""")
            val text = it.text()
            val match = qualityRegex.find(text)

            val source = match?.groupValues?.getOrNull(1)?.trim() ?: "Unknown"
            val quality = match?.groupValues?.getOrNull(2)?.substringBefore("p")?.toIntOrNull()
                ?: Qualities.Unknown.value

            val href = it.attr("data-src")
            if ("kwik" in href && (isMovie || (dubtype != null && type.contains(
                    dubtype,
                    ignoreCase = true
                )))
            ) {
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

    private val HOST_REGEX =
        Regex("""KrakenFiles|GoFile|AkiraBox|BuzzHeavier""", RegexOption.IGNORE_CASE)

    private val RES_REGEX = Regex("""(4320|2160|1440|1080|720|480)p""", RegexOption.IGNORE_CASE)

    private val CODEC_REGEX =
        Regex("""AV1|HEVC|H\.?265|x265|H\.?264|x264""", RegexOption.IGNORE_CASE)

    private val PLATFORM_REGEX = Regex("""AMZN|NF|CR|BILI|IQIYI""", RegexOption.IGNORE_CASE)

    private val SOURCE_REGEX = Regex("""WEB[- .]?DL|WEB[- .]?Rip""", RegexOption.IGNORE_CASE)

    private val AUDIO_REGEX =
        Regex("""Dual[- ]?Audio|Eng(lish)?[- ]?Dub|Japanese""", RegexOption.IGNORE_CASE)

    private val AUDIO_CODEC_REGEX =
        Regex("""AAC(\d\.\d)?|DDP(\d\.\d)?|OPUS|FLAC""", RegexOption.IGNORE_CASE)

    private val BIT_DEPTH_REGEX = Regex("""10[- ]?bit|10bits|8[- ]?bit""", RegexOption.IGNORE_CASE)

    private val SUB_REGEX = Regex(
        """Multi[- ]?Subs?|MultiSub|Multiple Subtitles|Eng(lish)?[- ]?Sub|ESub|Subbed""",
        RegexOption.IGNORE_CASE
    )


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
            .get("$jikanAPI/anime/$malId/full", timeout = 10000L)
            .parsedSafe<JikanResponse>()
            ?.data
            ?: return

        val slug = jikan.title.createSlug()
        val url = "$animetoshoAPI/episode/$slug-$episode.$anidbEid"

        val res = safeGet(url).document

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
    suspend fun invokeAnimeKai(
        jptitle: String? = null,
        title: String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?
    ) {
        if (jptitle.isNullOrBlank() || title.isNullOrBlank()) return

        val isMovie = dubtype == "Movie"

        suspend fun decode(text: String?): String {
            if (text.isNullOrBlank()) return ""
            return try {
                val res = safeGet("${BuildConfig.KAIENC}?text=$text").text
                JSONObject(res).getString("result")
            } catch (_: Exception) {
                safeGet("${BuildConfig.KAISVA}/?f=e&d=$text").text
            }
        }

        val json = "application/json; charset=utf-8".toMediaType()

        suspend fun decodeReverse(text: String): String {
            if (text.isBlank()) return ""
            val jsonBody = """{"text":"$text"}""".toRequestBody(json)

            return try {
                val res = app.post(
                    BuildConfig.KAIDEC,
                    requestBody = jsonBody
                ).text
                JSONObject(res).getString("result")
            } catch (_: Exception) {
                safeGet("${BuildConfig.KAISVA}/?f=d&d=$text").text
            }
        }
        val shuffledApis = animekaiAPIs.shuffled().toMutableList()

        while (shuffledApis.isNotEmpty()) {
            val animeKaiUrl = shuffledApis.removeAt(0)
            try {
                val searchEnglish = safeGet(
                    "$animeKaiUrl/ajax/anime/search?keyword=${
                        withContext(Dispatchers.IO) {
                            URLEncoder.encode(
                                title,
                                "UTF-8"
                            )
                        }
                    }"
                ).text
                val searchRomaji = safeGet(
                    "$animeKaiUrl/ajax/anime/search?keyword=${
                        withContext(Dispatchers.IO) {
                            URLEncoder.encode(
                                jptitle,
                                "UTF-8"
                            )
                        }
                    }"
                ).text

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
                        safeGet(href).document.selectFirst("div.rate-box")?.attr("data-id")
                    val decoded = decode(animeId)
                    val epRes =
                        safeGet("$animeKaiUrl/ajax/episodes/list?ani_id=$animeId&_=$decoded")
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
                            safeGet("$animeKaiUrl/ajax/links/list?token=$token&_=$decodedtoken")
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
                                safeGet("$animeKaiUrl/ajax/links/view?id=$lid&_=$decodelid")
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
        val tokensA = a.lowercase().split(nonWordSplitRegex).toSet()
        val tokensB = b.lowercase().split(nonWordSplitRegex).toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val intersection = tokensA.intersect(tokensB).size
        return intersection.toDouble() / max(tokensA.size, tokensB.size)
    }

    data class AnimeKaiSearchResult(
        val id: String,
        val title: String,
        val japaneseTitle: String? = null
    )

    suspend fun invokeHianime(
        animeIds: List<String?>?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) = invokeHiAnimeAnimeSource(
        name = "HiAnime",
        apis = hianimeAPIs,
        ajax = "/ajax/v2",
        animeIds = animeIds,
        episode = episode,
        subtitleCallback = subtitleCallback,
        callback = callback,
        dubtype = dubtype
    )

    suspend fun invokeKaido(
        animeIds: List<String?>?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) = invokeHiAnimeAnimeSource(
        name = "Kaido",
        apis = listOf("https://kaido.to"),
        ajax = "/ajax",
        animeIds = animeIds,
        episode = episode,
        subtitleCallback = subtitleCallback,
        callback = callback,
        dubtype = dubtype
    )

    private suspend fun invokeHiAnimeAnimeSource(
        name: String,
        apis: List<String>,
        ajax: String,
        animeIds: List<String?>?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) = coroutineScope {

        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val episodeNumber = (episode ?: 1).toString()
        val isMovie = dubtype == "Movie"
        val api = apis.random()

        val globalLimit = 3
        val semaphore = Semaphore(globalLimit)

        animeIds?.mapNotNull { it }?.map { animeId ->
            async {
                semaphore.withPermit {
                    try {
                        val (episodeId, servers) = coroutineScope {

                            val episodeDeferred = async {
                                runCatching {
                                    withTimeoutOrNull(10000) {
                                        safeGet(
                                            "$api$ajax/episode/list/$animeId",
                                            headers = headers
                                        ).parsedSafe<HianimeResponses>()?.html
                                            ?.let { Jsoup.parse(it) }
                                            ?.select("div.ss-list a")
                                            ?.find { it.attr("data-number") == episodeNumber }
                                            ?.attr("data-id")
                                    }
                                }.getOrNull()
                            }

                            val serversDeferred = async {
                                val eid = episodeDeferred.await() ?: return@async null
                                runCatching {
                                    withTimeoutOrNull(10000) {
                                        safeGet(
                                            "$api$ajax/episode/servers?episodeId=$eid",
                                            headers = headers
                                        ).parsedSafe<HianimeResponses>()?.html
                                            ?.let { Jsoup.parse(it) }
                                            ?.select("div.item.server-item")
                                            ?.map {
                                                Triple(
                                                    it.text(),
                                                    it.attr("data-id"),
                                                    it.attr("data-type")
                                                )
                                            }
                                    }
                                }.getOrNull()
                            }

                            episodeDeferred.await() to serversDeferred.await()
                        }

                        if (episodeId == null || servers.isNullOrEmpty()) return@async

                        val sourceResults = coroutineScope {
                            servers.map { (label, serverId, type) ->
                                async {
                                    semaphore.withPermit {
                                        try {
                                            val resolvedType =
                                                if (type.equals("raw", true)) "SUB" else type

                                            val allow = when {
                                                isMovie -> true
                                                dubtype == null -> false
                                                else -> resolvedType.contains(dubtype, true)
                                            }

                                            if (!allow) return@async null

                                            val sourceUrl = withTimeoutOrNull(15000) {
                                                safeGet(
                                                    "$api$ajax/episode/sources?id=$serverId",
                                                    headers = headers
                                                ).parsedSafe<EpisodeServers>()?.link
                                            }

                                            if (!sourceUrl.isNullOrBlank()) {
                                                Triple(label, sourceUrl, resolvedType)
                                            } else null

                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                }
                            }.awaitAll().filterNotNull()
                        }

                        sourceResults.forEach { (label, sourceUrl, resolvedType) ->
                            loadDisplaySourceNameExtractor(
                                name,
                                "⌜ $name ⌟ | ${label.uppercase()} | ${resolvedType.uppercase()}",
                                sourceUrl,
                                "",
                                subtitleCallback,
                                callback
                            )
                        }

                    } catch (_: Exception) {
                    }
                }
            }
        }?.awaitAll()
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

        locales.amap { locale ->
            val json = safeGet(
                "$KickassAPI/api/show/$slug/episodes?ep=1&lang=$locale",
                timeout = 5000L
            ).toString()

            val jsonresponse = parseJsonToEpisodes(json)

            val matchedSlug = jsonresponse.firstOrNull {
                it.episode_number.toString()
                    .substringBefore(".")
                    .toIntOrNull() == episode
            }?.slug ?: return@amap

            val href = "$KickassAPI/api/show/$slug/episode/ep-$episode-$matchedSlug"
            val servers = safeGet(href).parsedSafe<ServersResKAA>()?.servers ?: return@amap

            servers.amap { server ->
                if (server.name.contains("VidStreaming")) {
                    val host = getBaseUrl(server.src)
                    val headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                    )

                    val key = "e13d38099bf562e8b9851a652d2043d3".toByteArray()
                    val query = server.src.substringAfter("?id=").substringBefore("&")
                    val html = safeGet(server.src).toString()

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
                        ?: return@amap
                    val sourceUrl = "$host$route?id=$query&e=$timeStamp&s=$sig"

                    val encJson =
                        safeGet(sourceUrl, headers = headers).parsedSafe<EncryptedKAA>()?.data
                            ?: return@amap

                    val (encryptedData, ivHex) = encJson
                        .substringAfter(":\"")
                        .substringBefore('"')
                        .split(":")
                    val decrypted = tryParseJson<m3u8KAA>(
                        CryptoAES.decrypt(encryptedData, key, ivHex.decodeHex()).toJson()
                    ) ?: return@amap

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

                    val res = safeGet(
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


    internal suspend fun invokeSudatchi(
        animeId: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (animeId == null) return
        val ep = episode ?: 1
        try {
            val meta = JSONObject(safeGet("$sudatchi/api/anime/$animeId").text)

            val episodes = meta.optJSONArray("episodes")
                ?: run {
                    Log.d("Sudatchi", "No episodes array")
                    return
                }

            if (episodes.length() == 0) {
                Log.d("Sudatchi", "No episodes array")
                return
            }

            val epObj = episodes.optJSONObject(ep - 1)
                ?: run {
                    Log.d("Sudatchi", "Episode not found: $ep")
                    return
                }

            val episodeId = epObj.optInt("id")
            if (episodeId == 0) {
                Log.d("Sudatchi", "Invalid episodeId")
                return
            }

            callback(
                newExtractorLink(
                    "Sudatchi",
                    "Sudatchi",
                    "$sudatchi/api/streams?episodeId=$episodeId",
                    ExtractorLinkType.M3U8
                ) {
                    this.quality = Qualities.P1080.value
                }
            )

            epObj.optJSONArray("Subtitles")?.let { subs ->
                for (i in 0 until subs.length()) {
                    subs.optJSONObject(i)?.let { s ->

                        val url = s.optString("url")
                        if (url.isBlank()) return@let

                        val name = s
                            .optJSONObject("SubtitlesName")
                            ?.optString("name")
                            ?.ifBlank { null }
                            ?: ""

                        subtitleCallback(
                            newSubtitleFile(
                                name.capitalize(),
                                "$sudatchi$url"
                            )
                        )
                    }
                }
            }


        } catch (_: Exception) {
            Log.e("Sudatchi", "Failed")
        }
    }


    suspend fun invokeUhdmovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val domain = getDomains()?.uhdmovies ?: return

        val query = title
            ?.replace("-", " ")
            ?.replace(":", " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return

        val searchUrl = "$domain/search/${
            withContext(Dispatchers.IO) {
                URLEncoder.encode("$query $year", "UTF-8")
            }
        }"

        val redirectRegex = Regex("""window\.location\.replace\(["'](.*?)["']\)""")

        val pageUrl = safeGet(searchUrl).document
            .selectFirst("article div.entry-image a")
            ?.attr("href")
            ?: return

        val doc = safeGet(pageUrl).document

        val selector = if (season == null) {
            "div.entry-content p:matches($year)"
        } else {
            "div.entry-content p:matches((?i)(S0?$season|Season 0?$season))"
        }

        val epSelector = if (season == null) {
            "a:matches((?i)Download)"
        } else {
            "a:matches((?i)Episode $episode)"
        }

        val links = doc.select(selector)
            .asSequence()
            .mapNotNull {
                it.nextElementSibling()
                    ?.select(epSelector)
                    ?.attr("href")
            }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        links.amap { link ->
            val driveLink = try {
                if (link.contains("driveleech", true) || link.contains("driveseed", true)) {
                    val text = safeGet(link).text
                    val fileId = redirectRegex.find(text)?.groupValues?.getOrNull(1)
                        ?: return@amap
                    getBaseUrl(link) + fileId
                } else {
                    bypassHrefli(link) ?: return@amap
                }
            } catch (_: Exception) {
                return@amap
            }

            loadSourceNameExtractor(
                "UHDMovies",
                driveLink,
                "",
                subtitleCallback,
                callback
            )
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
        val response = safeGet(url, headers = headers, timeout = 10000L)
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

        val response = safeGet(url)
        if (response.code != 200) return

        val subtitles = runCatching {
            streamPlayExtractorGson.fromJson<List<WyZIESUB>>(
                response.toString(),
                wyzieSubListType
            )
        }.getOrElse { emptyList() }

        subtitles.forEach {
            val language = it.display.replaceFirstChar { ch -> ch.titlecase(Locale.getDefault()) }
            subtitleCallback(newSubtitleFile(language, it.url))
        }
    }


    suspend fun invokeVideasy(
        title: String? = null,
        tmdbId: Int? = null,
        imdbId: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        fun quote(text: String): String {
            return URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
                .replace("+", "%20")
        }

        var headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Connection" to "keep-alive",
            "Origin" to "https://player.videasy.net",
        )

        val servers = listOf(
            "myflixerzupcloud",
            "1movies",
            "moviebox",
            "primewire",
            "m4uhd",
            "hdmovie",
            "cdn",
            "primesrcme"
        )

        if (title == null) return

        val firstPass = quote(title)
        val encTitle = quote(firstPass)

        servers.amap { server ->
            val url = if (season == null) {
                "$videasyAPI/$server/sources-with-title?title=$encTitle&mediaType=movie&year=$year&tmdbId=$tmdbId&imdbId=$imdbId"
            } else {
                "$videasyAPI/$server/sources-with-title?title=$encTitle&mediaType=tv&year=$year&tmdbId=$tmdbId&episodeId=$episode&seasonId=$season&imdbId=$imdbId"
            }

            val enc_data = safeGet(url, headers = headers).text

            val jsonBody = """{"text":"$enc_data","id":"$tmdbId"}"""
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            val response = app.post(
                "https://enc-dec.app/api/dec-videasy",
                requestBody = requestBody
            )

            if (response.isSuccessful) {
                val json = response.text
                val result = JSONObject(json).getJSONObject("result")

                val sourcesArray = result.getJSONArray("sources")
                for (i in 0 until sourcesArray.length()) {
                    val obj = sourcesArray.getJSONObject(i)
                    val quality = obj.getString("quality")
                    val source = obj.getString("url")
                    var type = INFER_TYPE

                    if (source.contains(".m3u8")) {
                        headers = headers + mapOf(
                            "Accept" to "application/vnd.apple.mpegurl,application/x-mpegURL,*/*",
                            "Referer" to "$videasyAPI/"
                        )
                        type = ExtractorLinkType.M3U8
                    } else if (source.contains(".mp4")) {
                        headers = headers + mapOf(
                            "Accept" to "video/mp4,*/*",
                            "Range" to "bytes=0-",
                        )
                        type = ExtractorLinkType.VIDEO
                    } else if (source.contains(".mkv")) {
                        headers = headers + mapOf(
                            "Accept" to "video/x-matroska,*/*",
                            "Range" to "bytes=0-",
                        )
                        type = ExtractorLinkType.VIDEO
                    }

                    callback.invoke(
                        newExtractorLink(
                            "Videasy[${server.uppercase()}]",
                            "Videasy[${server.uppercase()}] $quality",
                            source,
                            type
                        ) {
                            this.quality = getIndexQuality(quality)
                            this.headers = headers
                        }
                    )
                }

                val subtitlesArray = result.getJSONArray("subtitles")
                for (i in 0 until subtitlesArray.length()) {
                    val obj = subtitlesArray.getJSONObject(i)
                    val source = obj.getString("url")
                    val language = obj.getString("language")

                    subtitleCallback.invoke(
                        newSubtitleFile(
                            getLanguage(language),
                            source
                        )
                    )
                }
            }
        }
    }

    suspend fun invokeMapple(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        if (tmdbId == null) return
        var mediaType: String
        var tv_slug = ""
        var url: String

        if (season == null) {
            mediaType = "movie"
            url = "$mappleAPI/watch/movie/$tmdbId"
        } else {
            mediaType = "tv"
            tv_slug = "$season-$episode"
            url = "$mappleAPI/watch/tv/$tmdbId/$season-$episode"
        }

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Referer" to "$mappleAPI/",
        )

        val text = safeGet(url, headers = headers).text
        val regex = Regex("""window\.__REQUEST_TOKEN__\s*=\s*"([^"]+)"""")
        val match = regex.find(text)
        val token = match?.groupValues?.get(1) ?: return

        val sources = listOf(
            "mapple", "sakura", "oak", "willow",
            "cherry", "pines", "magnolia", "sequoia"
        )

        sources.amap { source ->
            globalSemaphore.withPermit {
                try {
                    val jsonBody = """
                    {
                        "data": {
                            "mediaId": $tmdbId,
                            "mediaType": "$mediaType",
                            "tv_slug": "$tv_slug",
                            "source": "$source"
                        },
                        "endpoint": "stream-encrypted"
                    }
                """.trimIndent()

                    val encryptResText = app.post(
                        "$mappleAPI/api/encrypt",
                        json = jsonBody,
                        headers = headers
                    ).text

                    val encryptRes = JSONObject(encryptResText)
                    val streamPath = encryptRes.getString("url")
                    val finalUrl = "$mappleAPI$streamPath&requestToken=$token"

                    val streamsDataText = safeGet(
                        finalUrl,
                        headers = headers
                    ).text

                    val streamsData = JSONObject(streamsDataText)

                    if (streamsData.optBoolean("success")) {
                        val data = streamsData.getJSONObject("data")
                        val streamUrl = data.optString("stream_url")

                        if (streamUrl.isNotEmpty()) {
                            M3u8Helper.generateM3u8(
                                "Mapple [${source.uppercase()}]",
                                streamUrl,
                                "$mappleAPI/",
                                headers = headers
                            ).forEach(callback)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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

        (1..8).toList().amap { sr ->
            try {
                val apiUrl = if (season == null) {
                    "https://player.vidzee.wtf/api/server?id=$id&sr=$sr"
                } else {
                    "https://player.vidzee.wtf/api/server?id=$id&sr=$sr&ss=$season&ep=$episode"
                }

                val response = safeGet(apiUrl).text
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
                                if (type.equals("hls", ignoreCase = true))
                                    ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.referer = referer
                                this.headers = headersMap
                                this.quality = Qualities.P1080.value
                            }
                        )
                    }
                }

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
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val topmoviesAPI = getDomains()?.topMovies ?: return

        imdbId ?: return

        val url = if (season == null) {
            "$topmoviesAPI/search/${imdbId}"
        } else {
            "$topmoviesAPI/search/${imdbId} Season $season"
        }

        val hrefpattern = runCatching {
            safeGet(url).document.select("#content_box article a")
                .firstOrNull()?.attr("href")?.takeIf(String::isNotBlank)
        }.getOrNull() ?: return

        val res = runCatching {
            safeGet(
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

            detailPageUrls.amap { detailPageUrl ->
                val detailPageDocument =
                    runCatching { safeGet(detailPageUrl).document }.getOrNull() ?: return@amap

                val driveLinks = detailPageDocument.select("a.maxbutton-fast-server-gdrive")
                    .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }

                driveLinks.amap { driveLink ->
                    val finalLink = if (driveLink.contains("unblockedgames")) {
                        bypassHrefli(driveLink) ?: return@amap
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

            detailPageUrls.amap { detailPageUrl ->
                val detailPageDocument =
                    runCatching { safeGet(detailPageUrl).document }.getOrNull() ?: return@amap

                val episodeLink = detailPageDocument.select("span strong")
                    .firstOrNull {
                        it.text().matches(Regex(".*Episode\\s+$episode.*", RegexOption.IGNORE_CASE))
                    }
                    ?.parent()?.closest("a")?.attr("href")
                    ?.takeIf(String::isNotBlank) ?: return@amap

                val finalLink = if (episodeLink.contains("unblockedgames")) {
                    bypassHrefli(episodeLink) ?: return@amap
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
        title: String? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val MoviesmodAPI = getDomains()?.moviesmod ?: return
        invokeModflix(
            title = title,
            imdbId = imdbId,
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
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        api: String
    ) {
        fun buildSearch(query: String?) =
            query?.takeIf { it.isNotBlank() }?.let {
                "$api/search/${URLEncoder.encode(
                    if (season == null) it else "$it Season $season",
                    "UTF-8"
                )}"
            }

        val searchUrls = listOfNotNull(
            buildSearch(imdbId),
            buildSearch(title)
        )

        val href = searchUrls
            .amap { url ->
                runCatching {
                    safeGet(url).document
                        .selectFirst("#content_box article a")
                        ?.attr("href")
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
            }
            .firstOrNull { it != null }
            ?: return Log.e("Modflix", "No valid result found")

        val document = runCatching {
            safeGet(
                href,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0"
                ),
                interceptor = wpRedisInterceptor
            ).document
        }.getOrElse {
            Log.e("Modflix", "Page load failed: ${it.message}")
            return
        }

        if (season == null) {
            document.select("a[class*=maxbutton][href]")
                .map { it.attr("href") }
                .filter { it.isNotBlank() }
                .distinct()
                .amap { url ->
                    val detailDoc = runCatching { safeGet(url).document }.getOrNull() ?: return@amap

                    detailDoc.select("a.maxbutton-fast-server-gdrive")
                        .map { it.attr("href") }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .forEach { link ->
                            val final = if (link.contains("unblockedgames", true)) {
                                bypassHrefli(link) ?: return@forEach
                            } else link

                            loadSourceNameExtractor(
                                "MoviesMod",
                                final,
                                "$api/",
                                subtitleCallback,
                                callback
                            )
                        }
                }
            return
        }

        val seasonRegex = Regex("Season\\s+$season\\b", RegexOption.IGNORE_CASE)
        val episodeText = "Episode $episode"

        document.select("div.thecontent").forEach { content ->
            val seasonNode = content.select("h3")
                .firstOrNull { seasonRegex.containsMatchIn(it.text()) }
                ?: return@forEach

            generateSequence(seasonNode.nextElementSibling()) { it.nextElementSibling() }
                .takeWhile { it.tagName() != "h3" } // stop at next section
                .flatMap { it.select("a.maxbutton-episode-links").asSequence() }
                .map { it.attr("href") }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
                .amap { url ->
                    val detailDoc = runCatching { safeGet(url).document }.getOrNull() ?: return@amap

                    val link = detailDoc.select("span strong")
                        .firstOrNull { it.text().contains(episodeText, true) }
                        ?.parent()
                        ?.closest("a")
                        ?.attr("href")
                        ?.takeIf { it.isNotBlank() }
                        ?: return@amap

                    val final = if (link.contains("unblockedgames", true)) {
                        bypassHrefli(link) ?: return@amap
                    } else link

                    loadSourceNameExtractor(
                        "MoviesMod",
                        final,
                        "$api/",
                        subtitleCallback,
                        callback
                    )
                }
        }
    }

    private val vegaHeaders by lazy {
        mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language" to "en-US,en;q=0.9",
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache",
            "Upgrade-Insecure-Requests" to "1",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Sec-Fetch-User" to "?1",
            "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Microsoft Edge\";v=\"120\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Linux\"",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "cookie" to "xla=s4t"
        )
    }

    suspend fun invokeVegamovies(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = getDomains()?.vegamovies ?: return
        val imdb = id ?: return

        val headers = vegaHeaders // move to top-level val if reused

        val searchUrl = "$api/search.php?q=$imdb"

        val match = safeGet(searchUrl, referer = api, headers = headers)
            .parsedSafe<VegamoviesResponse>()?.hits
            ?.asSequence()
            ?.mapNotNull { it.document }
            ?.firstOrNull { it.imdb_id.equals(imdb, true) }
            ?: return

        val permalink = match.permalink ?: return
        val mainDoc = safeGet(api + permalink, referer = api, headers = headers).document

        if (season == null) {
            mainDoc.select("button.dwd-button")
                .mapNotNull { it.parent()?.attr("href") }
                .filter { it.isNotBlank() }
                .distinct()
                .amap { page ->
                    val doc = runCatching {
                        safeGet(page, referer = api, headers = headers).document
                    }.getOrNull() ?: return@amap

                    val source = doc.selectFirst("button.btn:matches((?i)(V-Cloud|G-Direct))")
                        ?.parent()
                        ?.attr("href")
                        ?: return@amap

                    loadSourceNameExtractor(
                        "VegaMovies",
                        source,
                        "",
                        subtitleCallback,
                        callback
                    )
                }
            return
        }

        val seasonRegex = Regex("(?i)Season $season")
        val episodeText = "Episode $episode"

        mainDoc.select("h3, h5")
            .asSequence()
            .filter { it.text().contains(seasonRegex) }
            .flatMap {
                generateSequence(it.nextElementSibling()) { el -> el.nextElementSibling() }
                    .takeWhile { el -> el.tagName() !in listOf("h3", "h5") }
                    .flatMap { el ->
                        el.select("a:matches((?i)(V-Cloud|Single|Episode|G-Direct))").asSequence()
                    }
            }
            .map { it.attr("href") }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
            .amap { page ->
                val doc = runCatching {
                    safeGet(page, referer = api, headers = headers).document
                }.getOrNull() ?: return@amap

                val epNode = doc.select("h4")
                    .firstOrNull { it.text().contains(episodeText, true) }
                    ?: return@amap

                val link = epNode.nextElementSibling()
                    ?.selectFirst("a:matches((?i)(V-Cloud|Single|Episode|G-Direct))")
                    ?.attr("href")
                    ?: return@amap

                loadSourceNameExtractor(
                    "VegaMovies",
                    link,
                    "",
                    subtitleCallback,
                    callback
                )
            }
    }


    suspend fun invokeRogmovies(
        title: String? = null,
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = getDomains()?.rogmovies ?: return
        val headers = vegaHeaders

        suspend fun search(query: String): List<VegamoviesDocument> {
            return safeGet("$api/search.php?q=$query", referer = api, headers = headers)
                .parsedSafe<VegamoviesResponse>()?.hits
                ?.mapNotNull { it.document }
                ?: emptyList()
        }

        val results = when {
            !id.isNullOrBlank() -> search(id)
            !title.isNullOrBlank() -> search(title)
            else -> return
        }

        if (results.isEmpty()) return

        val keywords = title
            ?.lowercase()
            ?.replace(normalizeAlphaNumSpaceRegex, "")
            ?.split(" ")
            ?.filter { it.length > 2 }
            ?: emptyList()

        val match = results.firstOrNull {
            !id.isNullOrBlank() && it.imdb_id.equals(id, true)
        } ?: results.firstOrNull { doc ->
            val docTitle = doc.post_title
                ?.lowercase()
                ?.replace(normalizeAlphaNumSpaceRegex, "")
                ?: return@firstOrNull false

            keywords.any { docTitle.contains(it) }
        } ?: results.firstOrNull() ?: return

        val permalink = match.permalink ?: return
        val mainDoc = safeGet(api + permalink, referer = api, headers = headers).document

        if (season == null) {
            mainDoc.select("button.dwd-button")
                .mapNotNull { it.parent()?.attr("href") }
                .filter { it.isNotBlank() }
                .distinct()
                .amap { page ->
                    val doc = runCatching {
                        safeGet(page, referer = api, headers = headers).document
                    }.getOrNull() ?: return@amap

                    val source = doc.selectFirst("button.btn:matches((?i)(V-Cloud|G-Direct))")
                        ?.parent()
                        ?.attr("href")
                        ?: return@amap

                    loadSourceNameExtractor("RogMovies", source, "", subtitleCallback, callback)
                }
            return
        }

        val seasonRegex = Regex("(?i)Season $season")
        val episodeText = "Episode $episode"

        mainDoc.select("h3, h5")
            .asSequence()
            .filter { it.text().contains(seasonRegex) }
            .flatMap {
                generateSequence(it.nextElementSibling()) { el -> el.nextElementSibling() }
                    .takeWhile { it.tagName() !in listOf("h3", "h5") }
                    .flatMap {
                        it.select("a:matches((?i)(V-Cloud|Single|Episode|G-Direct))").asSequence()
                    }
            }
            .map { it.attr("href") }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
            .amap { page ->
                val doc = runCatching {
                    safeGet(page, referer = api, headers = headers).document
                }.getOrNull() ?: return@amap

                val epNode = doc.select("h4")
                    .firstOrNull { it.text().contains(episodeText, true) }
                    ?: return@amap

                val link = epNode.nextElementSibling()
                    ?.selectFirst("a:matches((?i)(V-Cloud|Single|Episode|G-Direct))")
                    ?.attr("href")
                    ?: return@amap

                loadSourceNameExtractor("RogMovies", link, "", subtitleCallback, callback)
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

        val html = safeGet(url).document.toString()
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

        val vrf = safeGet("https://enc-dec.app/api/enc-vidsrc?user_id=$userId&movie_id=$movieId")
            .parsedSafe<VIDSRC>()?.result ?: return

        val apiUrl = if (season == null) {
            "$vidsrctoAPI/api/$id/servers?id=$id&type=$movieType&v=$v&vrf=$vrf&imdbId=$imdbId"
        } else {
            "$vidsrctoAPI/api/$id/servers?id=$id&type=$movieType&season=$season&episode=$episode&v=$v&vrf=$vrf&imdbId=$imdbId"
        }

        val serversText = safeGet(apiUrl).text
        val serversJson = JSONObject(serversText)

        if (!serversJson.optBoolean("success")) return

        val servers = serversJson.optJSONArray("data") ?: return

        (0 until servers.length()).toList().amap { i ->
            val server = servers.optJSONObject(i) ?: return@amap

            val name = server.optString("name")
            val hash = server.optString("hash")
            if (hash.isEmpty()) return@amap


            val sourceText = safeGet("$vidsrctoAPI/api/source/$hash").text
            if (sourceText.startsWith("<")) return@amap

            val sourceJson = JSONObject(sourceText)
            if (!sourceJson.optBoolean("success")) return@amap

            val data = sourceJson.optJSONObject("data") ?: return@amap
            val source = data.optString("source")
            if (source.isEmpty() || source.contains(".vidbox")) return@amap

            callback.invoke(
                newExtractorLink(
                    "Vidsrc",
                    "⌜ Vidsrc ⌟ | [$name]",
                    source,
                ) {
                    quality = if (name.contains(
                            "4K",
                            true
                        )
                    ) Qualities.P2160.value else Qualities.P1080.value
                    referer = vidsrctoAPI
                }
            )
        }
    }

    data class VIDSRC(
        val status: Long,
        val result: String,
    )


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
            val res = safeGet(
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
            val pageRes = safeGet(fixUrl(fullMediaUrl, nepuAPI))
            if (pageRes.code != 200) return
            pageRes.document.selectFirst("a[data-embed]")?.attr("data-embed")
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
            val mediaId = webMutex.withLock {
                safeGet(loaderUrl, referer = "$moflixAPI/", interceptor = cloudflareKiller)
            }.parsedSafe<MoflixResponse>()?.title?.id

            "$moflixAPI/api/v1/titles/$mediaId/seasons/$season/episodes/$episode?loader=episodePage"
        }

        val response = webMutex.withLock {
            safeGet(url, referer = "$moflixAPI/", interceptor = cloudflareKiller)
        }

        if (response.code != 200) return
        val res = response.parsedSafe<MoflixResponse>()
        (res?.episode ?: res?.title)?.videos?.filter {
            it.category.equals(
                "full",
                true
            )
        }?.amap { iframe ->
            val response =
                safeGet(iframe.src ?: return@amap, referer = "$moflixAPI/")
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

        safeGet(subUrl).parsedSafe<WatchsomuchSubResponses>()?.subtitles?.map { sub ->
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
        val request = safeGet(url, timeout = 60L)
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

        paths.forEach {
            val quality = getIndexQuality(it.first)
            val tags = getIndexQualityTags(it.first)
            val href =
                if (it.second.contains(dahmerMoviesAPI)) it.second else (dahmerMoviesAPI + it.second)

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

        val response = safeGet(url)
        if (response.code != 200) return

        val id =
            response.document.selectFirst("div#show_player_ajax")?.attr("movie-id") ?: return

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

        val server = postResponse.document.selectFirst("ul.nav a:contains(Filemoon)")
            ?.attr("data-server") ?: return

        val serverResponse = safeGet(server, referer = "$zoechipAPI/")
        if (serverResponse.code != 200) return

        val host = getBaseUrl(serverResponse.url)
        val script =
            serverResponse.document.select("script:containsData(function(p,a,c,k,e,d))").last()
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

        val response = safeGet(url, referer = "https://pressplay.top/")
        if (response.code != 200) return
        val iframe = response.document.selectFirst("iframe")?.attr("src") ?: return
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
            safeGet("$ridomoviesAPI/core/api/search?q=$imdbId", interceptor = wpRedisInterceptor)
        if (searchResponse.code != 200) return  // Early return if not 200

        val mediaSlug = searchResponse.parsedSafe<RidoSearch>()
            ?.data?.items?.find { it.contentable?.tmdbId == tmdbId || it.contentable?.imdbId == imdbId }
            ?.slug ?: return

        val id = season?.let {
            val episodeUrl = "$ridomoviesAPI/tv/$mediaSlug/season-$it/episode-$episode"
            val episodeResponse = safeGet(episodeUrl, interceptor = wpRedisInterceptor)
            if (episodeResponse.code != 200) return@let null  // Early return if not 200

            episodeResponse.text.substringAfterLast("""postid\":\"""").substringBefore("\"")
        } ?: mediaSlug

        val url =
            "$ridomoviesAPI/core/api/${if (season == null) "movies" else "episodes"}/$id/videos"
        val videoResponse = safeGet(url, interceptor = wpRedisInterceptor)
        if (videoResponse.code != 200) return  // Early return if not 200

        videoResponse.parsedSafe<RidoResponses>()?.data?.amap { link ->
            val iframe = Jsoup.parse(link.url ?: return@amap).select("iframe").attr("data-src")
            if (iframe.startsWith("https://closeload.top")) {
                val unpacked = getAndUnpack(
                    safeGet(
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
            val playerResponse = safeGet("https://allmovieland.link/player.js?v=60%20128")
            if (playerResponse.code != 200) return@runCatching
            val playerScript = playerResponse.toString()
            val domainRegex = Regex("const AwsIndStreamDomain.*'(.*)';")
            val host =
                domainRegex.find(playerScript)?.groupValues?.getOrNull(1) ?: return@runCatching

            val resResponse = safeGet("$host/play/$imdbId", referer = "$allmovielandAPI/")
            if (resResponse.code != 200) return@runCatching
            val resData =
                resResponse.document.selectFirst("script:containsData(playlist)")?.data()
                    ?.substringAfter("{")?.substringBefore(";")?.substringBefore(")")
                    ?: return@runCatching

            val json = tryParseJson<AllMovielandPlaylist>("{$resData}") ?: return@runCatching
            val headers = mapOf("X-CSRF-TOKEN" to "${json.key}")
            val jsonfile =
                if (json.file?.startsWith("http") == true) json.file else host + json.file

            val serverResponse = safeGet(jsonfile, headers = headers, referer = "$allmovielandAPI/")
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
        val fixTitle = title?.createSlug() ?: return
        val url = if (season == null) {
            "$PlaydesiAPI/$fixTitle"
        } else {
            "$PlaydesiAPI/$fixTitle-season-$season-episode-$episode-watch-online"
        }

        val response = safeGet(url)
        if (response.code != 200) return
        val document = response.document

        document.select("div.entry-content > p a").amap {
            val link = it.attr("href")
            val iframeResponse = safeGet(link)
            if (iframeResponse.code != 200) return@amap
            val trueUrl = iframeResponse.document.selectFirst("iframe")?.attr("src").orEmpty()
            if (trueUrl.isNotBlank()) {
                loadExtractor(trueUrl, subtitleCallback, callback)
            }
        }
    }

    suspend fun invokeMoviesdrive(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val domain = getDomains()?.moviesdrive ?: return
        val id = imdbId ?: return

        val searchUrl = "$domain/searchapi.php?q=$id"
        val root = runCatching {
            JSONObject(safeGet(searchUrl, interceptor = wpRedisInterceptor).text)
        }.getOrNull() ?: return

        val hits = root.optJSONArray("hits") ?: return

        // Find match early (avoid full loop)
        val match = (0 until hits.length())
            .asSequence()
            .mapNotNull { hits.optJSONObject(it)?.optJSONObject("document") }
            .firstOrNull { it.optString("imdb_id") == id }
            ?: return

        val permalink = match.optString("permalink")
        if (permalink.isBlank()) return

        val mainDoc = safeGet(domain + permalink, interceptor = wpRedisInterceptor).document

        if (season == null) {
            mainDoc.select("h5 > a")
                .map { it.attr("href") }
                .filter { it.isNotBlank() }
                .distinct()
                .amap { href ->
                    val servers = runCatching { extractMdrive(href) }.getOrNull() ?: return@amap
                    servers.forEach { server ->
                        loadSourceNameExtractor("MoviesDrive", server, "", subtitleCallback, callback)
                    }
                }
            return
        }

        val seasonRegex = Regex("(?i)Season $season|S0$season")
        val episodeRegex = Regex("(?i)Ep0$episode|Ep$episode")

        mainDoc.select("h5")
            .asSequence()
            .filter { it.text().contains(seasonRegex) }
            .mapNotNull { it.nextElementSibling()?.selectFirst("a")?.attr("href") }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
            .amap { seasonPage ->
                val doc = safeGet(seasonPage).document

                val epNode = doc.select("h5").firstOrNull { it.text().contains(episodeRegex) }
                    ?: return@amap

                val links = listOfNotNull(
                    epNode.nextElementSibling()?.selectFirst("a")?.attr("href"),
                    epNode.nextElementSibling()?.nextElementSibling()?.selectFirst("a")?.attr("href")
                )

                links.forEach { link ->
                    loadSourceNameExtractor("MoviesDrive", link, "", subtitleCallback, callback)
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

        val query = buildString {
            append("$bollyflixAPI/search/${id ?: return}")
            if (season != null) append(" $season")
        }

        val res1 = safeGet(query, timeout = 10000L).let {
            if (
                it.text.contains("Just a moment", true)
            ) safeGet(query, interceptor = cloudflareKiller)
            else it
        }.document

        val url = res1.selectFirst("div > article > a")?.attr("href") ?: return

        val res = safeGet(url, timeout = 10000L).let {
            if (
                it.text.contains("Just a moment", true)
            ) safeGet(url, interceptor = cloudflareKiller)
            else it
        }.document

        val hTag = if (season == null) "h5" else "h4"
        val sTag = if (season == null) "" else "Season $season"
        val entries =
            res.select("div.thecontent.clearfix > $hTag:matches((?i)$sTag.*(480p|720p|1080p|2160p))")
                .filter { element -> !element.text().contains("Download", true) }.takeLast(4)
        entries.forEach {
            var href = it.nextElementSibling()?.select("a")?.attr("href") ?: return@forEach

            if(href.contains("id=")) {
                val token = href.substringAfter("id=")
                val encodedurl =
                    safeGet("https://web.sidexfee.com/?id=$token").text.substringAfter("link\":\"")
                        .substringBefore("\"};")
                href = base64Decode(encodedurl.replace("\\/", "/"))
            }

            if (season == null) {
                loadSourceNameExtractor("Bollyflix", href , "", subtitleCallback, callback)
            } else {
                val episodeText = "Episode " + episode.toString().padStart(2, '0')
                val link =
                    safeGet(href).document.selectFirst("article h3 a:contains($episodeText)")!!
                        .attr("href")
                loadSourceNameExtractor("Bollyflix", link , "", subtitleCallback, callback)
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
            val doc = safeGet(searchUrl, timeout = 120L).document
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
                safeGet("$Watch32/ajax/season/list/$infoId").document.select("div.dropdown-menu a")
            }.getOrNull() ?: return

            val matchedSeason = seasonLinks.firstOrNull {
                it.text().contains("Season $season", ignoreCase = true)
            } ?: return

            val seasonId = matchedSeason.attr("data-id")

            val episodeLinks = runCatching {
                safeGet("$Watch32/ajax/season/episodes/$seasonId").document.select("li.nav-item a")
            }.getOrNull() ?: return

            val matchedEpisode = episodeLinks.firstOrNull {
                it.text().contains("Eps $episode:", ignoreCase = true)
            } ?: return

            val dataId = matchedEpisode.attr("data-id")

            val serverDoc = runCatching {
                safeGet("$Watch32/ajax/episode/servers/$dataId").document
            }.getOrNull() ?: return

            val sourceButtons = serverDoc.select("li.nav-item a")
            sourceButtons.toList().amap { source ->
                val sourceId = source.attr("data-id")

                val iframeUrl = runCatching {
                    safeGet("$Watch32/ajax/episode/sources/$sourceId")
                        .parsedSafe<Watch32>()?.link
                }.getOrNull() ?: return@amap
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
                safeGet("$Watch32/ajax/episode/list/$infoId")
                    .document
                    .select("li.nav-item a")
            }.getOrNull() ?: return
            episodeLinks.amap { ep ->
                val dataId = ep.attr("data-id")
                if (dataId.isBlank()) return@amap

                val iframeUrl = runCatching {
                    safeGet("$Watch32/ajax/episode/sources/$dataId")
                        .parsedSafe<Watch32>()?.link
                }.getOrNull()

                if (iframeUrl.isNullOrBlank()) return@amap

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
        val sourceList = retry { safeGet(sourceApiUrl, headers).parsedSafe<RiveStreamSource>() }

        val document =
            retry { safeGet(RiveStreamAPI, headers, timeout = 20).document } ?: return
        val appScript = document.select("script")
            .firstOrNull { it.attr("src").contains("_app") }?.attr("src") ?: return

        val js = retry { safeGet("$RiveStreamAPI$appScript").text } ?: return
        val keyList = Regex("""let\s+c\s*=\s*(\[[^]]*])""")
            .findAll(js).firstOrNull { it.groupValues[1].length > 2 }?.groupValues?.get(1)
            ?.let { array ->
                Regex("\"([^\"]+)\"").findAll(array).map { it.groupValues[1] }.toList()
            } ?: emptyList()

        val secretKey = retry {
            safeGet(
                "https://rivestream.supe2372.workers.dev/?input=$id&cList=${keyList.joinToString(",")}"
            ).text
        } ?: return

        sourceList?.data?.amap { source ->
            try {
                val streamUrl = if (season == null) {
                    "$RiveStreamAPI/api/backendfetch?requestID=movieVideoProvider&id=$id&service=$source&secretKey=$secretKey"
                } else {
                    "$RiveStreamAPI/api/backendfetch?requestID=tvVideoProvider&id=$id&season=$season&episode=$episode&service=$source&secretKey=$secretKey"
                }

                val responseString = retry {
                    safeGet(streamUrl, headers, timeout = 10).text
                } ?: return@amap

                try {
                    val json = JSONObject(responseString)
                    val sourcesArray =
                        json.optJSONObject("data")?.optJSONArray("sources") ?: return@amap

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
            safeGet(url).document.select("iframe").attr("src")
        ).takeIf { it.isNotEmpty() }
    }

    private suspend fun extractProrcpUrl(iframeUrl: String): String? {
        val doc = safeGet(iframeUrl, referer = iframeUrl).document
        val regex = Regex("src:\\s+'(.*?)'")
        val matchedSrc = regex.find(doc.html())?.groupValues?.get(1) ?: return null
        val host = getBaseUrl(iframeUrl)
        return host + matchedSrc
    }

    private suspend fun extractAndDecryptSource(
        prorcpUrl: String,
        referer: String
    ): List<Pair<String, String>>? {
        val responseText = safeGet(prorcpUrl, referer = referer).text
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
            safeGet(url, timeout = 30, headers = headers).parsedSafe<PrimeSrcServerList>()
        serverList?.servers?.amap {
            val rawServerJson =
                safeGet("$PrimeSrcApi/api/v1/l?key=${it.key}", timeout = 30, headers = headers).text
            val jsonObject = JSONObject(rawServerJson)
            loadSourceNameExtractor(
                "PrimeWire",
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
                    safeGet("$mainUrl/?s=$fixTitle", cacheTime = 60, timeout = 30).document
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
                    url?.let { safeGet("$proxyUrl$it", cacheTime = 60, timeout = 30).document }
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
                        ).document
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
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val safeToken = token?.takeIf { it.isNotBlank() } ?: return
        val encodedToken = withContext(Dispatchers.IO) {
            URLEncoder.encode(safeToken, "UTF-8")
        }

        val url = if (season == null) {
            "$NuvFeb/api/media/movie/$id?cookie=$encodedToken"
        } else {
            "$NuvFeb/api/media/tv/$id/$season/$episode?cookie=$encodedToken"
        }

        val parsed = retryFetch(url) ?: return

        parsed.versions.orEmpty().forEach { version ->
            val baseTitle = version.name
                ?.takeIf { it.isNotBlank() }
                ?.let(::cleanTitle)
                ?: "Stream"

            version.links.orEmpty().forEach { link ->
                val streamUrl = link.url ?: return@forEach
                val qualityName = link.quality.orEmpty()

                val name = if (qualityName.equals("ORG", true)) {
                    "SuperStream • $baseTitle • ORG"
                } else {
                    "SuperStream • $baseTitle"
                }

                callback(
                    newExtractorLink(
                        source = "SuperStream",
                        name = name,
                        url = streamUrl,
                        type = INFER_TYPE
                    ) {
                        quality = getQualityFromName(qualityName)
                    }
                )
            }
        }
    }

    private suspend fun retryFetch(url: String): FebResponse? {
        repeat(3) { _ ->
            val res = safeGet(url, timeout = 10000L)

            if (res.code == 500) {
                delay(2500L)
            } else {
                return res.parsedSafe()
            }
        }
        return null
    }


    suspend fun invoke4khdhub(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val domain = getDomains()?.n4khdhub ?: return
        val query = title?.takeIf { it.isNotBlank() } ?: return

        val searchDoc = safeGet("$domain/?s=${
            withContext(Dispatchers.IO) {
                URLEncoder.encode(query, "UTF-8")
            }
        }").document
        val normalizedTitle = query.lowercase().trim()
        val yearStr = year?.toString()

        val elements = searchDoc.select("div.card-grid > a.movie-card")

        fun extractContent(el: Element): String {
            return el.selectFirst("div.movie-card-content")
                ?.text()
                ?.lowercase()
                .orEmpty()
        }

        val matched = elements.firstOrNull { el ->
            val content = extractContent(el)
            content.contains(normalizedTitle) &&
                    (yearStr == null || content.contains(yearStr))
        } ?: elements.firstOrNull { el ->
            extractContent(el).contains(normalizedTitle)
        } ?: return

        val link = matched.attr("href")
        val url = if (link.startsWith("http")) link else "$domain$link"

        val doc = safeGet(url).document

        if (season == null) {
            doc.select("div.download-item a")
                .map { it.attr("href") }
                .distinct()
                .amap { href ->
                    val source = runCatching { getRedirectLinks(href) }.getOrNull() ?: return@amap
                    loadSourceNameExtractor("4Khdhub", source, "", subtitleCallback, callback)
                }
            return
        }

        val seasonText = "S${season.toString().padStart(2, '0')}"
        val episodeText = episode?.let { "E${it.toString().padStart(2, '0')}" }

        doc.select("div.episode-download-item")
            .asSequence()
            .filter {
                val text = it.text()
                text.contains(seasonText, true) &&
                        (episodeText == null || text.contains(episodeText, true))
            }
            .flatMap { it.select("div.episode-links > a").asSequence() }
            .map { it.attr("href") }
            .distinct()
            .toList()
            .amap { href ->
                val source = runCatching { getRedirectLinks(href) }.getOrNull() ?: return@amap
                loadSourceNameExtractor("4KHDHub", source, "", subtitleCallback, callback)
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
        val baseUrl = getDomains()?.hdhub4u
        if (title.isNullOrBlank()) return

        val response = safeGet(
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

        val normalizedTitle = title.lowercase().replace(normalizeAlphaNumRegex, "")
        val seasonText = season?.let { "season $it" }

        val posts = mutableListOf<String>()

        for (i in 0 until hits.length()) {
            val document = hits.optJSONObject(i)
                ?.optJSONObject("document")
                ?: continue

            val postTitle = document.optString("post_title").lowercase()
            val permalink = baseUrl + document.optString("permalink")

            if (postTitle.isBlank() || permalink.isBlank()) continue

            val cleanTitle = postTitle.replace(normalizeAlphaNumRegex, "")

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
                val postDoc = safeGet(postUrl).document
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


        matchedPosts.amap { el ->
            val doc = safeGet(el).document

            if (season == null) {
                val qualityLinks =
                    doc.select("h3 a:matches(480|720|1080|2160|4K), h4 a:matches(480|720|1080|2160|4K)")
                for (linkEl in qualityLinks) {
                    val resolvedLink = linkEl.attr("href")
                    val resolvedWatch =
                        if ("id=" in resolvedLink) getRedirectLinks(resolvedLink) else resolvedLink
                    loadSourceNameExtractor(
                        "HDhub4u",
                        resolvedWatch,
                        "",
                        subtitleCallback,
                        callback
                    )
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
                            val resolved = if ("id=" in href) getRedirectLinks(href) else href
                            val episodeDoc =
                                runCatching { safeGet(resolved).document }.getOrNull()
                                    ?: return@let

                            episodeDoc.select("h3 a[href], h4 a[href], h5 a[href]")
                                .mapNotNull { it.absUrl("href").takeIf { url -> url.isNotBlank() } }
                                .forEach { link ->
                                    val resolvedWatch =
                                        if ("id=" in link) getRedirectLinks(link) else link
                                    loadSourceNameExtractor(
                                        "HDhub4u",
                                        resolvedWatch,
                                        "",
                                        subtitleCallback,
                                        callback
                                    )
                                }
                        }

                        watchLink?.absUrl("href")?.let { watchHref ->
                            val resolvedWatch =
                                if ("id=" in watchHref) getRedirectLinks(watchHref) else watchHref
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

        val document = safeGet(url, headers = headers, allowRedirects = true).document
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
                .firstOrNull {
                    it.text().contains("v2", ignoreCase = true) || it.text()
                        .contains("v3", ignoreCase = true)
                }
                ?.let { mv ->
                    val post = mv.attr("data-post")
                    val nume = mv.attr("data-nume")
                    link = fetchSource(post, nume, "movie")
                }

        }

        // If ajax link failed, fallback to legacy anchors
        if (link.isNullOrEmpty()) {
            val type = if (episode != null) "(Combined)" else ""
            document.select("a[href*=dwo]").amap { anchor ->
                val innerDoc = safeGet(anchor.attr("href")).document
                innerDoc.select("div > p > a").amap {
                    val href = it.attr("href")
                    if (href.contains("GDFlix")) {
                        val redirectedUrl = (1..10).firstNotNullOfOrNull {
                            safeGet(href, allowRedirects = false).headers["location"]
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

            val mapper = streamPlayExtractorMapper
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

            matchingIds.amap { id ->
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
                    val subjectRes = safeGet(subjectUrl, headers = subjectHeaders)
                    if (subjectRes.code != 200) return@amap

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

                        val playRes = safeGet(playUrl, headers = playHeaders)
                        if (playRes.code != 200) continue

                        val playRoot = mapper.readTree(playRes.body.string())
                        val streams = playRoot["data"]?.get("streams") ?: continue
                        if (!streams.isArray) continue

                        for (stream in streams) {
                            val streamId = stream["id"]?.asText() ?: "$subjectId|$season|$episode"
                            //val subjectTitle = subjectData?.get("title")?.asText() ?: "Unknown Title"
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
                                            source = "MovieBox ${language.replace("dub", "Audio")}",
                                            name = "MovieBox (${language.replace("dub", "Audio")})",
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
                                        source = "MovieBox ${language.replace("dub", "Audio")}",
                                        name = "MovieBox (${language.replace("dub", "Audio")})",
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

                                val subRes = safeGet(subLink, headers = subHeaders)
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
                                                lang = "$lang (${language.replace("dub", "Audio")})"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    return@amap
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

        safeGet(url).parsedSafe<Morph>()?.data?.amap {
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

        players.amap { selectedPlayer ->
            val url = if (season == null) {
                "$soapy/embed/movies.php?tmdbid=$tmdbId&player=$selectedPlayer"
            } else {
                "$soapy/embed/series.php?tmdbid=$tmdbId&season=$season&episode=$episode&player=$selectedPlayer"
            }
            val iframe = safeGet(url).document.select("iframe").attr("src")
            loadSourceNameExtractor("Soapy", iframe, soapy, subtitleCallback, callback)
        }
    }


    suspend fun invokevidrock(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val type = if (season == null) "movie" else "tv"
        val encoded = vidrockEncode(tmdbId, type, season, episode)
        val response = safeGet("$vidrock/api/$type/$encoded").text
        val sourcesJson = JSONObject(response)

        val vidrockHeaders = mapOf(
            "Origin" to vidrock
        )

        sourcesJson.keys().asSequence().toList().amap { key ->
            val sourceObj = sourcesJson.optJSONObject(key) ?: return@amap

            val rawUrl = sourceObj.optString("url", "")
            val lang = sourceObj.optString("language", "Unknown")
            if (rawUrl.isNullOrBlank() || rawUrl == "null") return@amap

            val safeUrl = if (rawUrl.contains("%")) {
                URLDecoder.decode(rawUrl, "UTF-8")
            } else rawUrl

            val displayName = "Vidrock [$key] $lang"

            when {
                safeUrl.contains("/playlist/") -> {
                    val playlistResponse = safeGet(safeUrl, headers = vidrockHeaders).text
                    val playlistArray = JSONArray(playlistResponse)

                    for (j in 0 until playlistArray.length()) {
                        val item = playlistArray.optJSONObject(j) ?: continue
                        val itemUrl = item.optString("url", "") ?: continue
                        val res = item.optInt("resolution", 0)

                        callback.invoke(
                            newExtractorLink(
                                source = "Vidrock-$key",
                                name = displayName,
                                url = itemUrl,
                                type = INFER_TYPE
                            ) {
                                this.headers = vidrockHeaders
                                this.quality = getQualityFromName("$res")
                            }
                        )
                    }
                }

                safeUrl.contains(".mp4", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            source = "Vidrock-$key",
                            name = "$displayName MP4",
                            url = safeUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.headers = vidrockHeaders
                        }
                    )
                }

                safeUrl.contains(".m3u8", ignoreCase = true) -> {
                    M3u8Helper.generateM3u8(
                        source = "Vidrock-$key",
                        streamUrl = safeUrl,
                        referer = "",
                        quality = Qualities.P1080.value,
                        headers = vidrockHeaders
                    ).forEach(callback)
                }

                else -> {
                    callback.invoke(
                        newExtractorLink(
                            source = "Vidrock-$key",
                            name = displayName,
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
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf(
            "Origin" to cinemaOSApi,
            "Referer" to "$cinemaOSApi/",
            "User-Agent" to USER_AGENT,
        )

        val secretHash = cinemaOSGenerateHash(tmdbId, imdbId, season, episode)
        val type = if (season == null) {
            "movie"
        } else {
            "tv"
        }
        val sourceUrl = if (season == null) {
            "$cinemaOSApi/api/providerv3?type=$type&tmdbId=$tmdbId&imdbId=$imdbId&t=&ry=&secret=$secretHash"
        } else {
            "$cinemaOSApi/api/providerv3?type=$type&tmdbId=$tmdbId&imdbId=$imdbId&seasonId=$season&episodeId=$episode&t=&ry=&secret=$secretHash"
        }
        val sourceResponse =
            safeGet(sourceUrl, headers = headers, timeout = 60).parsedSafe<CinemaOSReponse>()
        val decryptedJson = cinemaOSDecryptResponse(sourceResponse?.data)

        if (decryptedJson.isNullOrEmpty()) return

        val json = parseCinemaOSSources(decryptedJson)

        json.amap {
            val extractorLinkType = if (it["type"]?.contains("hls", true) ?: false) {
                ExtractorLinkType.M3U8
            } else if (it["type"]?.contains("dash", true) ?: false) {
                ExtractorLinkType.DASH
            } else if (it["type"]?.contains("mp4", true) ?: false) {
                ExtractorLinkType.VIDEO
            } else {
                INFER_TYPE
            }

            callback.invoke(
                newExtractorLink(
                    "CinemaOS [${it["server"]}]".replace("\\s{2,}".toRegex(), " ").trim(),
                    "CinemaOS [${it["server"]}]".replace("\\s{2,}".toRegex(), " ").trim(),
                    url = it["url"].toString(),
                    type = extractorLinkType
                )
                {
                    this.headers = headers
                    this.quality = Qualities.P1080.value
                }
            )
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
    const clickPlay = () => {
        const btn = document.querySelector('.jw-icon-display.jw-button-color.jw-reset');
        if (btn) {
            btn.click();
            return "clicked";
        }
        return "no button";
    };

    // Try immediately
    let result = clickPlay();

    // Retry a few times (handles delayed UI render)
    let attempts = 0;
    const interval = setInterval(() => {
        if (attempts++ > 10) {
            clearInterval(interval);
            return;
        }
        const r = clickPlay();
        if (r === "clicked") {
            clearInterval(interval);
        }
    }, 500);

    // Hook fetch/XHR to ensure we catch autoplay API calls
    const origFetch = window.fetch;
    window.fetch = async (...args) => {
        const res = await origFetch(...args);
        return res;
    };

    const origOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (...args) {
        return origOpen.apply(this, args);
    };

    return result;
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

        val iframe = safeGet(url, interceptor = apifetch).url
        val jsonString = safeGet(iframe).body.string()

        val root: Vidlink = streamPlayExtractorMapper.readValue(jsonString)
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

        root.stream.captions.amap { caption ->
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

        val responseText = safeGet(url, headers = headers).text

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

                serverUrls.amap { (serverName, serverUrl) ->
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

            serverUrls.amap { (serverName, serverUrl) ->
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
        if (tmdbId == null) {
            println("VidFast: tmdbId is null")
            return
        }

        val requestUrl = if (season == null) {
            "$vidfastProApi/movie/$tmdbId"
        } else {
            "$vidfastProApi/tv/$tmdbId/$season/$episode"
        }

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Referer" to "$vidfastProApi/",
            "X-Requested-With" to "XMLHttpRequest"
        )

        val pageText = safeGet(requestUrl, headers = headers, timeout = 20).text

        val encodedText = Regex("""\\"en\\":\\"(.*?)\\"""")
            .find(pageText)
            ?.groupValues
            ?.getOrNull(1)

        if (encodedText.isNullOrBlank()) {
            println("VidFast: Failed to extract 'en' value")
            return
        }

        val decodeApiUrl = "https://enc-dec.app/api/enc-vidfast?text=$encodedText"
        val decodeResponse = safeGet(decodeApiUrl, timeout = 20).text

        val apiJson = runCatching { JSONObject(decodeResponse) }.getOrNull()
        if (apiJson == null) {
            println("VidFast: Failed to parse API JSON")
            return
        }

        val result = apiJson.optJSONObject("result")
        if (result == null) {
            println("VidFast: API result is null")
            return
        }

        val serversUrl = result.optString("servers")
        val streamBaseUrl = result.optString("stream")
        val token = result.optString("token")

        if (serversUrl.isBlank() || streamBaseUrl.isBlank()) {
            println("VidFast: Servers or streamBase is blank")
            return
        }

        val serverHeaders = mapOf(
            "User-Agent" to headers["User-Agent"].orEmpty(),
            "Referer" to "$vidfastProApi/",
            "X-Requested-With" to "XMLHttpRequest",
            "X-CSRF-Token" to token
        )

        val serversText = safeGet(serversUrl, headers = serverHeaders, timeout = 20).text
        val serversArray = runCatching { JSONArray(serversText) }.getOrNull()
        if (serversArray == null) {
            println("VidFast: Failed to parse servers JSON")
            return
        }

        val quality = Qualities.P1080.value

        (0 until serversArray.length()).mapNotNull { i ->

            val serverObj = serversArray.getJSONObject(i)
            val serverName = serverObj.optString("name", "Server ${i + 1}")
            val serverData = serverObj.optString("data")

            if (serverData.isBlank()) {
                println("VidFast: Server data blank, skipping")
                return@mapNotNull
            }

            val streamUrl = "$streamBaseUrl/$serverData"
            val streamText = safeGet(streamUrl, headers = serverHeaders, timeout = 20).text

            val streamJson = runCatching { JSONObject(streamText) }.getOrNull()
            if (streamJson == null) {
                println("VidFast: Failed to parse stream JSON")
                return@mapNotNull
            }

            val finalUrl = streamJson.optString("url")

            if (finalUrl.isBlank()) {
                println("VidFast: Final URL blank, trying next server")
                return@mapNotNull
            }

            val subtitles = mutableListOf<SubtitleFile>()
            val seen = mutableSetOf<String>()
            val tracksArray = streamJson.optJSONArray("tracks")

            if (tracksArray != null) {
                for (j in 0 until tracksArray.length()) {
                    val track = tracksArray.getJSONObject(j)
                    val file = track.optString("file")
                    val label = track.optString("label")

                    if (file.isNotBlank() && label.isNotBlank() && seen.add(file)) {
                        subtitles.add(newSubtitleFile(label, file))
                    }
                }
            }

            callback.invoke(
                newExtractorLink(
                    "VidFastPro",
                    "VidFastPro [$serverName]",
                    finalUrl
                ) {
                    this.referer = "$vidfastProApi/"
                    this.quality = quality
                }
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun invokeVidPlus(
        tmdbId: Int? = null,
        title: String? = null,
        imdbId: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "Referer" to vidPlusApi,
            "Origin" to vidPlusApi,
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        )

        val requestArgs = listOf(title, year, imdbId).joinToString("*")
        val urlListMap = mutableMapOf<String, String>()
        val myMap = listOf(
            "Orion", "Minecloud", "Viet", "Crown", "Joker", "Soda", "Beta", "Gork",
            "Monk", "Fox", "Leo", "4K", "Adam", "Sun", "Maxi", "Indus", "Tor",
            "Hindi", "Delta", "Ben", "Pearl", "Tamil", "Ruby", "Tel", "Mal", "Kan", "Lava"
        )

        myMap.mapIndexed { index, entry -> index to entry }.amap { (index, entry) ->
            try {
                val serverId = index + 1
                val serverUrl =
                    if (season == null) "$vidPlusApi/api/server?id=$tmdbId&sr=$serverId&args=$requestArgs"
                    else "$vidPlusApi/api/server?id=$tmdbId&sr=$serverId&ep=$episode&ss=$season&args=$requestArgs"
                val apiResponse = safeGet(serverUrl, headers = headers, timeout = 20).text

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
                            ).headers["Location"]
                        }
                        synchronized(urlListMap) {
                            urlListMap[entry] = finalStreamUrl.toString()
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        urlListMap.amap {
            callback.invoke(
                newExtractorLink(
                    "VidPlus [${it.key}]",
                    "VidPlus [${it.key}]",
                    url = it.value,
                ) {
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
            val document = safeGet(url, referer = toonStreamAPI).document
            document.selectFirst("div.video > iframe")?.attr("data-src")?.let { src ->
                val innerDoc = safeGet(src).document
                innerDoc.select("div.Video > iframe").amap { iframe ->
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
        token: String,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (imdbId.isNullOrBlank()) return
        val api = buildString {
            append(BuildConfig.Nuviostreams)
            token.substringAfter("ui=")
                .takeIf(String::isNotBlank)
                ?.let {
                    append("/cookie=${URLEncoder.encode(it, "UTF-8")}/region=USA7")
                }
            append("/stream/")
            append(
                if (season == null)
                    "movie/$imdbId.json"
                else
                    "series/$imdbId:$season:$episode.json"
            )
        }

        val response = safeGet(api, timeout = 10000L).parsedSafe<NuvioStreams>() ?: return

        response.streams.amap { stream ->
            callback.invoke(
                newExtractorLink(
                    stream.name.substringAfter("[").substringBefore("]").substringBefore("-"),
                    stream.name.substringBefore("-"),
                    stream.url,
                    INFER_TYPE
                )
                {
                    this.quality = getIndexQuality(stream.name)
                }
            )
        }
    }

    suspend fun invokeWebStreamr(
        imdbId: String?,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (imdbId.isNullOrBlank()) return
        val api = if (season == null) {
            "$webStreamrAPI/stream/movie/$imdbId.json"
        } else {
            "$webStreamrAPI/stream/series/$imdbId:$season:$episode.json"
        }

        val response = safeGet(api, timeout = 10000L).parsedSafe<webStreamr>() ?: return

        response.streams.amap { stream ->
            val name = stream.name.replace(
                Regex(
                    """\s*(2160p|1440p|1080p|720p|480p|360p|240p|4K)\b""",
                    RegexOption.IGNORE_CASE
                ), ""
            ).trim()
            val headers = mutableMapOf<String, String>()

            stream.behaviorHints.proxyHeaders?.request?.let { req ->
                req.referer?.let { headers["Referer"] = it }
                req.origin?.let { headers["Origin"] = it }
                req.userAgent?.let { headers["User-Agent"] = it }
            }

            callback.invoke(
                newExtractorLink(
                    "WebStreamr",
                    name,
                    stream.url,
                    INFER_TYPE
                ) {
                    this.quality = getIndexQuality(stream.name)
                    this.headers = headers
                }
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
        val searchData = safeGet("$XDmoviesAPI/php/search_api.php?query=$title&fuzzy=true", headers, timeout = 1000L)
            .parsedSafe<SearchData>() ?: return
        val matched = searchData.firstOrNull { it.tmdb_id == id } ?: return
        val url = XDmoviesAPI + matched.path
        val response = safeGet(url).let {
            if (it.text.contains("Just a moment", true)) {
                webMutex.withLock {
                    safeGet(url, interceptor = cloudflareKiller)
                }
            } else it
        }
        val document = response.document
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
        val fixTitle = title?.createSlug() ?: return
        val doc = if (season == null || season == 1) {
            safeGet("$kimcartoonAPI/Cartoon/$fixTitle").document
        } else {
            val res = safeGet("$kimcartoonAPI/Cartoon/$fixTitle-Season-$season")
            if (res.url == "$kimcartoonAPI/") safeGet("$kimcartoonAPI/Cartoon/$fixTitle-Season-0$season").document else res.document
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
        safeGet(iframe).document.select("#selectServer option").amap { s ->
            val server = s.attr("sv")
            val href = app.post(
                "${kimcartoonAPI}/ajax/anime/load_episodes_v2?s=$server",
                data = mapOf("episode_id" to id)
            ).document.selectFirst("iframe")?.attr("src")?.replace("\\\"", "") ?: ""
            if (href.contains("vidstream")) {
                val response = safeGet(href, referer = kimcartoonAPI).toString()
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
            val doc = safeGet(searchUrl).document
            val results = doc.select("div.item")
            results.firstOrNull { item ->
                val titleElement = item.selectFirst("div.info a")
                val name = titleElement?.text()?.trim() ?: return@firstOrNull false
                name.contains(title, ignoreCase = true)
            }?.selectFirst("div.info a")
        }.getOrNull() ?: return

        val href = yFlix + matchedElement.attr("href")
        val document = safeGet(href).document

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
                safeGet("$yFlix/ajax/links/list?eid=$eid&_=$decodetoken")
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

            servers.amap { serverNode ->
                try {
                    val lid = serverNode.attr("data-lid").trim()
                    if (lid.isBlank()) {
                        Log.d("Yflix", "Skipping server with empty lid (eid=$eid)")
                        return@amap
                    }

                    val serverName =
                        serverNode.selectFirst("span")?.text()?.trim()?.ifEmpty { "Server" }

                    val decodelid = try {
                        yflixDecode(lid)
                    } catch (e: Exception) {
                        Log.d("Yflix", "Failed to decode lid ($lid): ${e.message}")
                        return@amap
                    }

                    val viewResp = runCatching {
                        safeGet("$yFlix/ajax/links/view?id=$lid&_=$decodelid")
                            .parsedSafe<YflixResponse>()
                    }.getOrNull()

                    val result = viewResp?.result
                    if (result.isNullOrBlank()) {
                        Log.d("Yflix", "Empty result for server $serverName (lid=$lid)")
                        return@amap
                    }

                    val decodedIframePayload = try {
                        yflixDecodeReverse(result)
                    } catch (e: Exception) {
                        Log.d("Yflix", "Failed to decodeReverse for lid=$lid : ${e.message}")
                        return@amap
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
                        return@amap
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
        val pageDoc = runCatching { safeGet(href).document }.getOrNull() ?: return
        val iframeElement = pageDoc.selectFirst("iframe[src], iframe[data-src]") ?: return
        val iframeSrc = iframeElement.attr("src").ifEmpty { iframeElement.attr("data-src") }
        if (iframeSrc.isEmpty()) return
        val iframeDoc = runCatching { safeGet(iframeSrc).document }.getOrNull() ?: return
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

        val pageUrl = safeGet(searchUrl, headers)
            .document
            .selectFirst("div.dar-short_item > a")
            ?.attr("href")
            ?: return

        val script = safeGet(pageUrl, headers)
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
                    "CineCity Multi-Audio",
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

        val embedResponse = safeGet(embedPageUrl)
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

        val sourcesText = safeGet(
            "https://scrapemaster.net/api/sources/$sessionToken",
            headers = scrapemaster_headers
        ).text

        sourcesText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.first() == '{' && it.last() == '}' }
            .mapNotNull {
                runCatching {
                    streamPlayExtractorMapper.readValue<EmbedmasterSourceItem>(it)
                }.getOrNull()
            }.filter { it.type == "server" && it.sourceUrl.isNotBlank() }.toList().amap { source ->
                val playResponse = safeGet("https://embdmstrplayer.com/play/${source.sourceUrl}")
                val doc = playResponse.document
                val baseUrl = getBaseUrl(playResponse.url)
                val candidateUrls = mutableSetOf<String>()

                doc.select("iframe[src], a[href], source[src], video[src]").amap { el ->
                    val attrName = if (el.hasAttr("src")) "src" else "href"
                    val rawValue = el.attr(attrName).trim()

                    if (rawValue.isEmpty()) return@amap

                    val finalUrl = when {
                        rawValue.startsWith("http") -> rawValue
                        rawValue.startsWith("//") -> "https:$rawValue"
                        rawValue.startsWith("/") -> baseUrl + rawValue
                        else -> "$baseUrl/$rawValue"
                    }

                    candidateUrls.add(finalUrl)
                }

                candidateUrls.toList().amap { url ->
                    if (!url.contains(".php", ignoreCase = true)) {
                        loadExtractor(
                            url,
                            embedmaster,
                            subtitleCallback,
                            callback
                        )
                        return@amap
                    }

                    val phpPageText = safeGet(url).text
                    val playerFileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")

                    val realMediaUrl = playerFileRegex
                        .find(phpPageText)
                        ?.groupValues
                        ?.get(1)
                        ?: return@amap

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
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit
    ) {
        val key = generateHexKey32()

        val baseHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Referer" to "https://hexa.su/",
            "Accept" to "text/plain",
            "X-Fingerprint-Lite" to "e9136c41504646444",
            "X-Api-Key" to key
        )

        val apiBase = "https://enc-dec.app/api"

        val token = safeGet("$apiBase/enc-hexa", headers = baseHeaders).parsedSafe<HexaEn>()?.result?.token ?: return

        val headers = baseHeaders + mapOf(
            "X-Cap-Token" to token
        )

        val url = if (season == null) {
            "$hexaSU/api/tmdb/movie/$tmdbId/images"
        } else {
            "$hexaSU/api/tmdb/tv/$tmdbId/season/$season/episode/$episode/images"
        }

        val encrypted = safeGet(url, headers).text
        if (encrypted.isEmpty()) return

        val jsonBody = """
        {
            "text": "$encrypted",
            "key": "$key"
        }
    """.trimIndent().toRequestBody("application/json".toMediaType())

        val decryptRes = app.post(
            "$apiBase/dec-hexa",
            headers = mapOf("Content-Type" to "application/json"),
            requestBody = jsonBody
        ).parsedSafe<HexaResponse>() ?: return

        if (decryptRes.status != 200) return

        val sources = decryptRes.result?.sources ?: return

        for (src in sources) {
            val server = src.server ?: continue
            val link = src.url ?: continue

            if (link.isEmpty()) continue

            val name = server.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }

            M3u8Helper.generateM3u8(
                "HexaSU $name",
                link,
                "https://hexa.su/"
            ).forEach(callback)
        }
    }

    suspend fun invokFlixindia(
        title: String?,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanTitle = title?.replace(":", "")?.trim() ?: return

        val res = safeGet(flixindia)
        val sessionId = res.cookies["PHPSESSID"] ?: return

        val csrfToken = Regex("""window\.CSRF_TOKEN\s*=\s*['"]([a-f0-9]{64})['"]""")
            .find(res.text)
            ?.groupValues?.getOrNull(1)
            ?: return

        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)

        val query = if (season == null) {
            "$cleanTitle ${year ?: ""}".trim()
        } else {
            "$cleanTitle S${seasonSlug}E${episodeSlug}"
        }

        val baseUrl = flixindia.removeSuffix("/")

        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "*/*",
            "Referer" to "$flixindia/",
            "Origin" to baseUrl,
            "X-Requested-With" to "XMLHttpRequest",
            "Cookie" to "PHPSESSID=$sessionId"
        )

        val results = app.post(
            url = "$flixindia/",
            data = mapOf(
                "action" to "search",
                "csrf_token" to csrfToken,
                "q" to query
            ),
            headers = headers,
            timeout = 30
        ).parsedSafe<Flixindia>()?.results
            ?.map { it.url }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: return

        results.amap { raw ->
            val resolved = runCatching {
                if ("id=" in raw) getRedirectLinks(raw) else raw
            }.getOrElse {
                return@amap
            }

            if (resolved.isBlank()) return@amap

            try {
                when {
                    resolved.contains("hubcloud", true) -> {
                        HubCloud().getUrl(resolved, "FlixIndia", subtitleCallback, callback)
                    }

                    resolved.contains("gdflix", true) -> {
                        GDFlix().getUrl(resolved, "FlixIndia", subtitleCallback, callback)
                    }

                    else -> {
                        loadSourceNameExtractor(
                            "FlixIndia",
                            resolved,
                            "",
                            subtitleCallback,
                            callback
                        )
                    }
                }
            } catch (_: Exception) {
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
        val url = "$api/?s=$id"
        var response = safeGet(url, timeout = 5000L)
        if (response.text.contains("Just a moment", true)) {
            response = webMutex.withLock {
                safeGet(
                    url,
                    timeout = 5000L,
                    interceptor = cloudflareKiller
                )
            }
        }

        val searchDoc = response.document
        val entries = searchDoc.select("h2.entry-title > a")

        entries.amap { entry ->
            val pageDoc = safeGet(entry.attr("href"), timeout = 5000L).document
            val buttons = pageDoc.select("a.maxbutton")

            if (episode == null) {
                // Movie
                buttons.amap { btn ->
                    val intermediateDoc = safeGet(btn.attr("href"), timeout = 5000L).document
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

                    val episodeDoc = safeGet(btn.attr("href"), timeout = 5000L).document
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

    suspend fun invokeMovies4u(
        id: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val searchQuery = "$title $year".trim()
        val movies4uAPI = getDomains()?.movies4u ?: return
        val searchUrl = "$movies4uAPI/?s=$searchQuery"

        val searchDoc = safeGet(searchUrl).document
        val links = searchDoc.select("article h3 a")

        links.amap { element ->
            val postUrl = element.attr("href")
            val postDoc = safeGet(postUrl).document
            val imdbId = postDoc.select("p a:contains(IMDb Rating)").attr("href")
                .substringAfter("title/").substringBefore("/")

            if (imdbId != id.toString()) {
                return@amap
            }

            if (season == null) {
                val innerUrl = postDoc.select("div.download-links-div a.btn").attr("href")
                val innerDoc = safeGet(innerUrl).document
                val sourceButtons = innerDoc.select("div.downloads-btns-div a.btn")
                sourceButtons.amap { sourceButton ->
                    val sourceLink = sourceButton.attr("href")
                    loadSourceNameExtractor(
                        "Movies4u",
                        sourceLink,
                        "",
                        subtitleCallback,
                        callback
                    )
                }
            } else {
                val seasonBlocks = postDoc.select("div.downloads-btns-div")
                seasonBlocks.amap { block ->
                    val headerText = block.previousElementSibling()?.text().orEmpty()
                    if (headerText.contains("Season $season", ignoreCase = true)) {
                        val seasonLink = block.selectFirst("a.btn")?.attr("href") ?: return@amap

                        val episodeDoc = safeGet(seasonLink).document
                        val episodeBlocks = episodeDoc.select("div.downloads-btns-div")

                        if (episode != null && episode in 1..episodeBlocks.size) {
                            val episodeBlock = episodeBlocks[episode - 1]
                            val episodeLinks = episodeBlock.select("a.btn")

                            episodeLinks.amap { epLink ->
                                val sourceLink = epLink.attr("href")
                                loadSourceNameExtractor(
                                    "Movies4u",
                                    sourceLink,
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
    }

    suspend fun invokeM4uhd(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val safeTitle = title?.fixTitle() ?: return

            val res = safeGet(
                "$m4uhdAPI/search/$safeTitle",
                timeout = 5000L
            ).document

            val scriptData = res.select("div.item").mapNotNull { element ->
                val href = element.selectFirst("a")?.attr("href")
                if (href.isNullOrBlank()) return@mapNotNull null

                Triple(
                    element.selectFirst("img.imagecover")?.attr("alt"),
                    element.selectFirst(".jt-info")?.text(),
                    href
                )
            }

            if (scriptData.isEmpty()) {
                Log.e("M4uhd", "No search results")
                return
            }

            val cleanTitle = title
                .lowercase()
                .replace(Regex("[^a-z0-9]"), "")

            val script = scriptData.firstOrNull { triple ->

                val siteTitle = triple.first
                    ?.lowercase()
                    ?.replace(Regex("[^a-z0-9]"), "")
                    ?: return@firstOrNull false

                siteTitle.contains(cleanTitle)

            } ?: scriptData.firstOrNull()

            val scriptUrl = script?.third
            if (scriptUrl.isNullOrBlank()) {
                Log.e("M4uhd", "Script URL null")
                return
            }

            val link = fixUrl(scriptUrl, m4uhdAPI)

            val request = safeGet(link)
            val doc = request.document

            // direct iframe
            val directIframe = doc
                .selectFirst("#myplayer iframe")
                ?.attr("src")

            if (!directIframe.isNullOrBlank()) {
                loadExtractor(
                    fixUrl(directIframe, link),
                    m4uhdAPI,
                    subtitleCallback,
                    callback
                )
                return
            }

            val cookies = request.cookies

            val token = doc
                .selectFirst("meta[name=csrf-token]")
                ?.attr("content")

            if (token.isNullOrBlank()) {
                Log.e("M4uhd", "Token missing")
                return
            }

            val m4uData = if (season == null) {

                doc.selectFirst("span.singlemv.active, span#fem")
                    ?.attr("data")

            } else {

                val epCode = "S%02d-E%02d".format(season, episode ?: return)

                val episodeBtn = doc.select("button.episode")
                    .firstOrNull {
                        it.text().trim().equals(epCode, true)
                    } ?: return

                val idepisode = episodeBtn.attr("idepisode")

                if (idepisode.isBlank()) return

                val embed = app.post(
                    "$m4uhdAPI/ajaxtv",
                    data = mapOf(
                        "idepisode" to idepisode,
                        "_token" to token
                    ),
                    referer = link,
                    headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest"
                    ),
                    cookies = cookies
                ).document

                embed.selectFirst("span.singlemv.active, span#fem")
                    ?.attr("data")
            }

            if (m4uData.isNullOrBlank()) {
                Log.e("M4uhd", "m4uData missing")
                return
            }

            val iframe = app.post(
                "$m4uhdAPI/ajax",
                data = mapOf(
                    "m4u" to m4uData,
                    "_token" to token
                ),
                referer = link,
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest"
                ),
                cookies = cookies
            ).document
                .selectFirst("iframe")
                ?.attr("src")

            if (iframe.isNullOrBlank()) {
                Log.e("M4uhd", "iframe missing")
                return
            }

            loadSourceNameExtractor(
                "M4uhd",
                fixUrl(iframe, link),
                m4uhdAPI,
                subtitleCallback,
                callback
            )

        } catch (_: Exception) {
            Log.e("M4uhd", "invokeM4uhd crash")
        }
    }

    suspend fun invokeCineVood(
        imdbId: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val baseUrl = getDomains()?.cinevood ?: return
        if (imdbId == null) return

        val searchRes = safeGet("$baseUrl/?s=$imdbId")

        val searchDoc = if (searchRes.text.contains("Just a moment", true)) {
            webMutex.withLock {
                safeGet("$baseUrl/?s=$imdbId", interceptor = cloudflareKiller)
            }.document
        } else {
            searchRes.document
        }

        searchDoc.select("article a[href]")
            .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }
            .distinct()
            .amap { postUrl ->

                val postRes = safeGet(postUrl)
                val postDoc = if (postRes.text.contains("Just a moment", true)) {
                    webMutex.withLock {
                        safeGet(postUrl, interceptor = cloudflareKiller)
                    }.document
                } else {
                    postRes.document
                }

                postDoc.select("a.maxbutton[href]")
                    .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }
                    .amap { link ->
                        loadSourceNameExtractor("CineVood", link, "", subtitleCallback, callback)
                    }
            }
    }

    suspend fun invokeFilmyfiy(
        title: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val baseUrl = getDomains()?.filmyfiy ?: return
        val rawQuery = title?.trim() ?: return
        val query = rawQuery.lowercase().replace(Regex("[^a-z0-9]"), "")
        val searchDoc = safeGet(
            "$baseUrl/site-1.html?to-search=${
                withContext(Dispatchers.IO) {
                    URLEncoder.encode(
                        rawQuery,
                        "UTF-8"
                    )
                }
            }"
        ).document
        searchDoc.select("div.A2 > a:nth-child(2)[href]").mapNotNull { a ->
            val href = a.attr("href").takeIf(String::isNotBlank)
                ?.let { if (it.startsWith("http")) it else baseUrl + it } ?: return@mapNotNull null
            val text = a.text().lowercase().replace(Regex("[^a-z0-9]"), "")
            if (text.contains(query)) href else null
        }.distinct().amap { postUrl ->
            val postDoc = safeGet(postUrl).document
            postDoc.select("div.dlbtn a[href]")
                .mapNotNull { it.absUrl("href").takeIf(String::isNotBlank) }.distinct()
                .amap { dlBtnUrl ->
                    val dlDoc = safeGet(dlBtnUrl).document
                    dlDoc.select("div.dlink a[href]")
                        .mapNotNull { it.absUrl("href").takeIf(String::isNotBlank) }.distinct()
                        .amap { finalUrl ->
                            loadExtractor(finalUrl, baseUrl, subtitleCallback, callback)
                        }
                }
        }
    }

    suspend fun invokeDooflix(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseApi = "https://panel.watchkaroabhi.com"
        val apiKey = "qNhKLJiZVyoKdi9NCQGz8CIGrpUijujE"

        if (tmdbId == null) return

        val requestUrl = if (season == null) {
            "$baseApi/api/3/movie/$tmdbId/links?api_key=$apiKey"
        } else {
            "$baseApi/api/3/tv/$tmdbId/season/$season/episode/$episode/links?api_key=$apiKey"
        }

        val headers = mapOf(
            base64Decode("WC1QYWNrYWdlLU5hbWU=") to base64Decode("Y29tLmtpbmcubW9qYQ=="),
            base64Decode("VXNlci1BZ2VudA==") to base64Decode("ZG9vZmxpeA=="),
            base64Decode("WC1BcHAtVmVyc2lvbg==") to base64Decode("MzA1")
        )

        val response = safeGet(requestUrl, headers).parsedSafe<Dooflix>() ?: return
        val links = response.links

        links.amap { link ->
            val streamurl = safeGet(
                link.url,
                referer = "https://molop.art/",
                allowRedirects = false
            ).headers["location"] ?: return@amap
            callback.invoke(
                newExtractorLink(
                    "Dooflix",
                    link.host,
                    streamurl
                )
                {
                    this.referer = "https://molop.art/"
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }

    private fun normalizeTitleFast(title: String?): String {
        if (title == null) return ""
        val sb = StringBuilder(title.length)
        for (c in title.lowercase()) {
            if (c.isLetterOrDigit() || c == ' ') sb.append(c)
        }
        return sb.toString().trim()
    }

    suspend fun invokekuudere(
        title: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?
    ) {
        val isMovie = dubtype == "Movie"

        val query = title?.replace(" ", "%20") ?: return
        val api = "$kuudere/api/search?q=$query"

        val res = safeGet(api).parsedSafe<KuudereSearch>() ?: return
        val results = res.results ?: return

        val normalizedQuery = normalizeTitleFast(title)

        val match = results
            .firstOrNull { result ->
                val normalized = normalizeTitleFast(result.title)

                if (season != null) {
                    normalized.contains(normalizedQuery) &&
                            normalized.contains("season $season")
                } else {
                    normalized.contains(normalizedQuery)
                }
            } ?: results.firstOrNull() ?: return

        val animeId = match.id ?: return

        val watchRes =
            safeGet("$kuudere/api/watch/$animeId/$episode").parsedSafe<KuudereWatch>() ?: return

        watchRes.episode_links?.forEach { link ->

            val allow = when {
                isMovie -> true
                dubtype == null -> false
                else -> link.dataType?.contains(dubtype, ignoreCase = true) ?: return@forEach
            }

            if (!allow) return@forEach
            val url = link.dataLink ?: return@forEach
            val name = "⌜ Kuudere ⌟ | ${link.dataType?.replaceFirstChar { it.uppercase() }}"

            val server = link.serverName?.lowercase() ?: return@forEach

            when {
                server.contains("hide") ->
                    VidHidePro().getUrl(url, kuudere, subtitleCallback, callback)

                server.contains("wish") ->
                    StreamWishExtractor().getUrl(url, kuudere, subtitleCallback, callback)

                server.contains("kumi") ->
                    KumiUns().getUrl(url, kuudere, subtitleCallback, callback)

                else ->
                    loadExtractor(url, name, subtitleCallback, callback)
            }
        }
    }

    suspend fun invokeLevidia(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val safeTitle = title?.let { URLEncoder.encode(it, "utf-8") } ?: return
        val yearVal = year ?: return

        val searchType = if (season == null) "movies" else "episodes"
        val searchUrl = "$levidia/search.php?q=$safeTitle+$yearVal&v=$searchType"

        val res = safeGet(searchUrl)

        val sessionId = res.cookies["PHPSESSID"] ?: return

        val (value1, value2) = Regex("""_3chk\(['"]([^'"]+)['"],\s*['"]([^'"]+)['"]\)""")
            .find(res.text)
            ?.destructured
            ?: return

        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$levidia/",
            "Cookie" to "PHPSESSID=$sessionId;$value1=$value2"
        )

        val href = res.document
            .select("li.mlist div.mainlink a")
            .firstNotNullOfOrNull { a ->
                val parsedTitle = a.selectFirst("strong")?.text()?.trim() ?: return@firstNotNullOfOrNull null
                val parsedYear = a.ownText().filter(Char::isDigit).toIntOrNull()

                if (parsedTitle.equals(title, true) && parsedYear == yearVal) {
                    a.attr("href")
                } else null
            } ?: return

        suspend fun extractLinks(document: org.jsoup.nodes.Document) {
            document.select("a.xxx").amap { el ->
                try {
                    val embedUrl = safeGet(
                        el.attr("href"),
                        headers = headers,
                        allowRedirects = false
                    ).headers["Location"] ?: return@amap
                    Log.d("Phisher",embedUrl)
                    loadSourceNameExtractor(
                        "Levidia",
                        embedUrl,
                        "$levidia/",
                        subtitleCallback,
                        callback
                    )
                } catch (_: Throwable) {
                    // ignore individual failures
                }
            }
        }

        val mainDoc = safeGet(href, headers = headers).document

        if (season == null) {
            extractLinks(mainDoc)
            return
        }

        val epRegex = Regex("""(?i)[^a-z]s0?$season e0?$episode[^0-9]""".replace(" ", ""))

        val episodePath = mainDoc
            .select("li.mlist.links b a")
            .firstNotNullOfOrNull { a ->
                a.attr("href").takeIf { epRegex.containsMatchIn(it) }
            } ?: return

        val episodeDoc = safeGet("$levidia/$episodePath", headers = headers).document
        extractLinks(episodeDoc)
    }

}





