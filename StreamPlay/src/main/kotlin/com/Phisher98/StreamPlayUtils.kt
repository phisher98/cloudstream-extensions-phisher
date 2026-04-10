package com.phisher98

import androidx.core.net.toUri
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.amapIndexed
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.fixTitle
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.RequestBodyTypes
import com.phisher98.StreamPlay.Companion.anilistAPI
import com.phisher98.StreamPlay.Companion.fourthAPI
import com.phisher98.StreamPlay.Companion.thrirdAPI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min
import android.content.SharedPreferences

val sharedPref: SharedPreferences? = null
val appGlobalSemaphore = Semaphore(
    sharedPref?.getInt("provider_concurrency", 15)?.coerceIn(8, 50) ?: 20
)
private val extractorCallbackScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
private val sharedObjectMapper by lazy { ObjectMapper() }
private val sharedGson by lazy { Gson() }
private val tmdbDateFormatter = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }
}

private fun getTmdbDateFormatter(): SimpleDateFormat = tmdbDateFormatter.get()!!

suspend fun extractMovieAPIlinks(serverid: String, movieid: String, MOVIE_API: String): String {
    val link =
        app.get("$MOVIE_API/ajax/get_stream_link?id=$serverid&movie=$movieid").document.toString()
            .substringAfter("link\":\"").substringBefore("\",")
    return link
}


suspend fun bypassHrefli(url: String): String? {
    fun Document.getFormUrl(): String {
        return this.select("form#landing").attr("action")
    }

    fun Document.getFormData(): Map<String, String> {
        return this.select("form#landing input").associate { it.attr("name") to it.attr("value") }
    }

    val host = getBaseUrl(url)
    var res = app.get(url).document
    var formUrl = res.getFormUrl()
    var formData = res.getFormData()

    res = app.post(formUrl, data = formData).document
    formUrl = res.getFormUrl()
    formData = res.getFormData()

    res = app.post(formUrl, data = formData).document
    val skToken = res.selectFirst("script:containsData(?go=)")?.data()?.substringAfter("?go=")
        ?.substringBefore("\"") ?: return null
    val driveUrl = app.get(
        "$host?go=$skToken", cookies = mapOf(
            skToken to "${formData["_wp_http2"]}"
        )
    ).document.selectFirst("meta[http-equiv=refresh]")?.attr("content")?.substringAfter("url=")
    val path = app.get(driveUrl ?: return null).text.substringAfter("replace(\"")
        .substringBefore("\")")
    if (path == "/404") return null
    return fixUrl(path, getBaseUrl(driveUrl))
}


suspend fun String.haveDub(referer: String): Boolean {
    return app.get(this, referer = referer).text.contains("TYPE=AUDIO")
}

suspend fun convertTmdbToAnimeId(
    title: String?,
    date: String?,
    airedDate: String?,
    type: TvType
): AniIds {
    val sDate = date?.split("-")
    val sAiredDate = airedDate?.split("-")

    val year = sDate?.firstOrNull()?.toIntOrNull()
    val airedYear = sAiredDate?.firstOrNull()?.toIntOrNull()
    val season = getSeason(sDate?.get(1)?.toIntOrNull())
    val airedSeason = getSeason(sAiredDate?.get(1)?.toIntOrNull())

    return if (type == TvType.AnimeMovie) {
        tmdbToAnimeId(title, airedYear, "", type)
    } else {
        val ids = tmdbToAnimeId(title, year, season, type)
        if (ids.id == null && ids.idMal == null) tmdbToAnimeId(
            title,
            airedYear,
            airedSeason,
            type
        ) else ids
    }
}

suspend fun tmdbToAnimeId(title: String?, year: Int?, season: String?, type: TvType): AniIds {
    val query = """
        query (
          ${'$'}page: Int = 1
          ${'$'}search: String
          ${'$'}sort: [MediaSort] = [POPULARITY_DESC, SCORE_DESC]
          ${'$'}type: MediaType
          ${'$'}seasonYear: Int
          ${'$'}format: [MediaFormat]
        ) {
          Page(page: ${'$'}page, perPage: 20) {
            media(
              search: ${'$'}search
              sort: ${'$'}sort
              type: ${'$'}type
              seasonYear: ${'$'}seasonYear
              format_in: ${'$'}format
            ) {
              id
              idMal
            }
          }
        }
    """.trimIndent().trim()

    val variables = mapOf(
        "search" to title,
        "sort" to "SEARCH_MATCH",
        "type" to "ANIME",
        //"season" to season?.uppercase(),
        "seasonYear" to year,
        "format" to listOf(if (type == TvType.AnimeMovie) "MOVIE" else "TV", "ONA")
    ).filterValues { value -> value != null && value.toString().isNotEmpty() }
    val data = mapOf(
        "query" to query,
        "variables" to variables
    ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
    val res = app.post(anilistAPI, requestBody = data)
        .parsedSafe<AniSearch>()?.data?.Page?.media?.firstOrNull()
    return AniIds(res?.id, res?.idMal)

}

fun generateWpKey(r: String, m: String): String {
    val rList = r.split("\\x").toTypedArray()
    var n = ""
    val decodedM = String(base64Decode(m.split("").reversed().joinToString("")).toCharArray())
    for (s in decodedM.split("|")) {
        n += "\\x" + rList[Integer.parseInt(s) + 1]
    }
    return n
}


suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
    size: String = ""
) {
    val provider = source.trim().takeIf { it.isNotBlank() }
    val sizePart = size.trim().takeIf { it.isNotBlank() }

    loadExtractor(url, referer, subtitleCallback) { link ->
        extractorCallbackScope.launch {
            val label = buildString {
                provider?.let { append(it) }
                if (link.name.isNotEmpty()) {
                    if (isNotEmpty()) append(' ')
                    append(link.name)
                }
                sizePart?.let {
                    if (isNotEmpty()) append(' ')
                    append(it)
                }
            }

            callback(
                newExtractorLink(
                    link.source,
                    label,
                    link.url
                ) {
                    this.quality = quality ?: link.quality
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}


suspend fun loadDisplaySourceNameExtractor(
    sourceName: String?,
    displayName: String?,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        extractorCallbackScope.launch {
            callback.invoke(
                newExtractorLink(
                    sourceName ?: "",
                    displayName ?: "",
                    link.url,
                ) {
                    this.quality = quality ?: link.quality
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}

fun getSeason(month: Int?): String? {
    val seasons = arrayOf(
        "Winter", "Winter", "Spring", "Spring", "Spring", "Summer",
        "Summer", "Summer", "Fall", "Fall", "Fall", "Winter"
    )
    if (month == null) return null
    return seasons[month - 1]
}

fun getEpisodeSlug(
    season: Int? = null,
    episode: Int? = null,
): Pair<String, String> {
    val result = if (season == null && episode == null) {
        "" to ""
    } else {
        val seasonSlug = if (season!! < 10) "0$season" else "$season"
        val episodeSlug = if (episode!! < 10) "0$episode" else "$episode"
        seasonSlug to episodeSlug
    }
    return result
}

fun String?.createSlug(): String? {
    return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
        ?.trim()
        ?.replace("\\s+".toRegex(), "-")
        ?.lowercase()
}

fun getKisskhTitle(str: String?): String? {
    return str?.replace(Regex("[^a-zA-Z\\d]"), "-")
}

fun getIndexQualityTags(str: String?, fullTag: Boolean = false): String {
    return if (fullTag) Regex("(?i)(.*)\\.(?:mkv|mp4|avi)").find(str ?: "")?.groupValues?.get(1)
        ?.trim() ?: str ?: "" else Regex("(?i)\\d{3,4}[pP]\\.?(.*?)\\.(mkv|mp4|avi)").find(
        str ?: ""
    )?.groupValues?.getOrNull(1)
        ?.replace(".", " ")?.trim() ?: str ?: ""
}

fun getIndexQuality(str: String?): Int {
    return Regex("""\b(2160|1440|1080|720|576|540|480)\s*[pP]\b""").find(str.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}

suspend fun extractMdrive(url: String): List<String> {
    val regex = Regex("hubcloud|gdflix|gdlink", RegexOption.IGNORE_CASE)

    return try {
        app.get(url).document
            .select("a[href]")
            .mapNotNull { element ->
                val href = element.attr("href")
                if (regex.containsMatchIn(href)) {
                    href
                } else {
                    null
                }
            }
    } catch (e: Exception) {
        Log.e("Error Mdrive", "Error extracting links: ${e.localizedMessage}")
        emptyList()
    }
}

fun getBaseUrl(url: String): String {
    return URI(url).let {
        "${it.scheme}://${it.host}"
    }
}


fun String.getHost(): String {
    return fixTitle(URI(this).host.substringBeforeLast(".").substringAfterLast("."))
}

fun isUpcoming(dateString: String?): Boolean {
    return try {
        val format = getTmdbDateFormatter()
        val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
        unixTimeMS < dateTime
    } catch (t: Throwable) {
        logError(t)
        false
    }
}

fun getDate(): TmdbDate {
    val formatter = getTmdbDateFormatter()
    val calendar = Calendar.getInstance()

    // Today
    val today = formatter.format(calendar.time)

    // Next week
    calendar.add(Calendar.WEEK_OF_YEAR, 1)
    val nextWeek = formatter.format(calendar.time)

    // Last week's Monday
    calendar.time = Date()
    calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    calendar.add(Calendar.WEEK_OF_YEAR, -1)
    val lastWeekStart = formatter.format(calendar.time)

    // Start of current month
    calendar.time = Date()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    val monthStart = formatter.format(calendar.time)

    return TmdbDate(today, nextWeek, lastWeekStart, monthStart)
}

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return domain + url
        }
        return "$domain/$url"
    }
}

fun Int.toRomanNumeral(): String = Symbol.closestBelow(this)
    .let { symbol ->
        if (symbol != null) {
            "$symbol${(this - symbol.decimalValue).toRomanNumeral()}"
        } else {
            ""
        }
    }

private enum class Symbol(val decimalValue: Int) {
    I(1),
    IV(4),
    V(5),
    IX(9),
    X(10);

    companion object {
        fun closestBelow(value: Int) =
            entries.toTypedArray()
                .sortedByDescending { it.decimalValue }
                .firstOrNull { value >= it.decimalValue }
    }
}

/**
 * Conforming with CryptoJS AES method
 */
// see https://gist.github.com/thackerronak/554c985c3001b16810af5fc0eb5c358f
@Suppress("unused", "FunctionName", "SameParameterValue")
object CryptoJS {

    private const val KEY_SIZE = 256
    private const val IV_SIZE = 128
    private const val HASH_CIPHER = "AES/CBC/PKCS7Padding"
    private const val AES = "AES"
    private const val KDF_DIGEST = "MD5"

    // Seriously crypto-js, what's wrong with you?
    private const val APPEND = "Salted__"

    /**
     * Encrypt
     * @param password passphrase
     * @param plainText plain string
     */
    fun encrypt(password: String, plainText: String): String {
        val saltBytes = generateSalt(8)
        val key = ByteArray(KEY_SIZE / 8)
        val iv = ByteArray(IV_SIZE / 8)
        EvpKDF(password.toByteArray(), KEY_SIZE, IV_SIZE, saltBytes, key, iv)
        val keyS = SecretKeySpec(key, AES)
        val cipher = Cipher.getInstance(HASH_CIPHER)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keyS, ivSpec)
        val cipherText = cipher.doFinal(plainText.toByteArray())
        // Thanks kientux for this: https://gist.github.com/kientux/bb48259c6f2133e628ad
        // Create CryptoJS-like encrypted!
        val sBytes = APPEND.toByteArray()
        val b = ByteArray(sBytes.size + saltBytes.size + cipherText.size)
        System.arraycopy(sBytes, 0, b, 0, sBytes.size)
        System.arraycopy(saltBytes, 0, b, sBytes.size, saltBytes.size)
        System.arraycopy(cipherText, 0, b, sBytes.size + saltBytes.size, cipherText.size)
        val bEncode = base64Encode(b).encodeToByteArray()
        return String(bEncode)
    }

    /**
     * Decrypt
     * Thanks Artjom B. for this: http://stackoverflow.com/a/29152379/4405051
     * @param password passphrase
     * @param cipherText encrypted string
     */
    fun decrypt(password: String, cipherText: String): String {
        val ctBytes = base64DecodeArray(cipherText)
        val saltBytes = Arrays.copyOfRange(ctBytes, 8, 16)
        val cipherTextBytes = Arrays.copyOfRange(ctBytes, 16, ctBytes.size)
        val key = ByteArray(KEY_SIZE / 8)
        val iv = ByteArray(IV_SIZE / 8)
        EvpKDF(password.toByteArray(), KEY_SIZE, IV_SIZE, saltBytes, key, iv)
        val cipher = Cipher.getInstance(HASH_CIPHER)
        val keyS = SecretKeySpec(key, AES)
        cipher.init(Cipher.DECRYPT_MODE, keyS, IvParameterSpec(iv))
        val plainText = cipher.doFinal(cipherTextBytes)
        return String(plainText)
    }

    private fun EvpKDF(
        password: ByteArray,
        keySize: Int,
        ivSize: Int,
        salt: ByteArray,
        resultKey: ByteArray,
        resultIv: ByteArray
    ): ByteArray {
        return EvpKDF(password, keySize, ivSize, salt, 1, KDF_DIGEST, resultKey, resultIv)
    }

    @Suppress("NAME_SHADOWING")
    private fun EvpKDF(
        password: ByteArray,
        keySize: Int,
        ivSize: Int,
        salt: ByteArray,
        iterations: Int,
        hashAlgorithm: String,
        resultKey: ByteArray,
        resultIv: ByteArray
    ): ByteArray {
        val keySize = keySize / 32
        val ivSize = ivSize / 32
        val targetKeySize = keySize + ivSize
        val derivedBytes = ByteArray(targetKeySize * 4)
        var numberOfDerivedWords = 0
        var block: ByteArray? = null
        val hash = MessageDigest.getInstance(hashAlgorithm)
        while (numberOfDerivedWords < targetKeySize) {
            if (block != null) {
                hash.update(block)
            }
            hash.update(password)
            block = hash.digest(salt)
            hash.reset()
            // Iterations
            for (i in 1 until iterations) {
                block = hash.digest(block!!)
                hash.reset()
            }
            System.arraycopy(
                block!!, 0, derivedBytes, numberOfDerivedWords * 4,
                min(block.size, (targetKeySize - numberOfDerivedWords) * 4)
            )
            numberOfDerivedWords += block.size / 4
        }
        System.arraycopy(derivedBytes, 0, resultKey, 0, keySize * 4)
        System.arraycopy(derivedBytes, keySize * 4, resultIv, 0, ivSize * 4)
        return derivedBytes // key + iv
    }

    private fun generateSalt(length: Int): ByteArray {
        return ByteArray(length).apply {
            SecureRandom().nextBytes(this)
        }
    }
}


suspend fun invokeExternalSource(
    mediaId: Int? = null,
    type: Int? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit,
    token: String? = null,
) {
    val thirdAPI = thrirdAPI
    val fourthAPI = fourthAPI
    val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
    val headers = mapOf("Accept-Language" to "en")
    val shareKey =
        app.get("$fourthAPI/index/share_link?id=${mediaId}&type=$type", headers = headers)
            .parsedSafe<ER>()?.data?.link?.substringAfterLast("/") ?: return

    val shareRes = app.get("$thirdAPI/file/file_share_list?share_key=$shareKey", headers = headers)
        .parsedSafe<ExternalResponse>()?.data ?: return

    val fids = if (season == null) {
        shareRes.fileList
    } else {
        shareRes.fileList?.find {
            it.fileName.equals(
                "season $season",
                true
            )
        }?.fid?.let { parentId ->
            app.get(
                "$thirdAPI/file/file_share_list?share_key=$shareKey&parent_id=$parentId&page=1",
                headers = headers
            )
                .parsedSafe<ExternalResponse>()?.data?.fileList?.filter {
                    it.fileName?.contains("s${seasonSlug}e${episodeSlug}", true) == true
                }
        }
    } ?: return

    fids.amapIndexed { index, fileList ->
        val superToken = token ?: ""

        val player = app.get(
            "$thirdAPI/console/video_quality_list?fid=${fileList.fid}&share_key=$shareKey",
            headers = mapOf("Cookie" to superToken)
        ).text

        val json = try {
            JSONObject(player)
        } catch (e: Exception) {
            Log.e("Error:", "Invalid JSON response $e")
            return@amapIndexed
        }
        val htmlContent = json.optString("html", "")
        if (htmlContent.isEmpty()) return@amapIndexed

        val document: Document = Jsoup.parse(htmlContent)
        val sourcesWithQualities = mutableListOf<Triple<String, String, String>>() // url, quality, size
        document.select("div.file_quality").forEach { element ->
            val url = element.attr("data-url").takeIf { it.isNotEmpty() } ?: return@forEach
            val qualityAttr = element.attr("data-quality").takeIf { it.isNotEmpty() }
            val size = element.selectFirst(".size")?.text()?.takeIf { it.isNotEmpty() } ?: return@forEach

            val quality = if (qualityAttr.equals("ORG", ignoreCase = true)) {
                Regex("""(\d{3,4}p)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1) ?: "2160p"
            } else {
                qualityAttr ?: return@forEach
            }

            sourcesWithQualities.add(Triple(url, quality, size))
        }

        val sourcesJsonArray = JSONArray().apply {
            sourcesWithQualities.forEach { (url, quality, size) ->
                put(JSONObject().apply {
                    put("file", url)
                    put("label", quality)
                    put("type", "video/mp4")
                    put("size", size)
                })
            }
        }
        val jsonObject = JSONObject().put("sources", sourcesJsonArray)
        listOf(jsonObject.toString()).forEach {
            val parsedSources = tryParseJson<ExternalSourcesWrapper>(it)?.sources ?: return@forEach
            parsedSources.forEach org@{ source ->
                val format =
                    if (source.type == "video/mp4") ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
                val label = if (format == ExtractorLinkType.M3U8) "Hls" else "Mp4"
                if (!(source.label == "AUTO" || format == ExtractorLinkType.VIDEO)) return@org

                callback.invoke(
                    newExtractorLink(
                        "⌜ SuperStream ⌟",
                        "⌜ SuperStream ⌟ [Server ${index + 1}] ${source.size}",
                        source.file?.replace("\\/", "/") ?: return@org,
                        format
                    )
                    {
                        this.quality = getIndexQuality(if (format == ExtractorLinkType.M3U8) fileList.fileName else source.label)
                    }
                )
            }
        }
    }
}

fun parseJsonToEpisodes(json: String): List<EpisoderesponseKAA> {
    data class Response(val result: List<EpisoderesponseKAA>)
    val response = sharedGson.fromJson(json, Response::class.java)
    return response.result
}


fun getSignature(
    html: String,
    server: String,
    query: String,
    key: ByteArray
): Triple<String, String, String>? {
    // Define the order based on the server type
    val headers =
        mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
    val order = when (server) {
        "VidStreaming", "DuckStream" -> listOf(
            "IP",
            "USERAGENT",
            "ROUTE",
            "MID",
            "TIMESTAMP",
            "KEY"
        )

        "BirdStream" -> listOf("IP", "USERAGENT", "ROUTE", "MID", "KEY")
        else -> return null
    }

    // Parse the HTML using Jsoup
    val document = Jsoup.parse(html)
    val cidRaw = document.select("script:containsData(cid:)").firstOrNull()
        ?.html()?.substringAfter("cid: '")?.substringBefore("'")?.decodeHex()
        ?: return null
    val cid = String(cidRaw).split("|")

    // Generate timestamp
    val timeStamp = (System.currentTimeMillis() / 1000 + 60).toString()

    // Update route
    val route = cid[1].replace("player.php", "source.php")

    val signature = buildString {
        order.forEach {
            when (it) {
                "IP" -> append(cid[0])
                "USERAGENT" -> append(headers["User-Agent"] ?: "")
                "ROUTE" -> append(route)
                "MID" -> append(query)
                "TIMESTAMP" -> append(timeStamp)
                "KEY" -> append(String(key))
                "SIG" -> append(html.substringAfter("signature: '").substringBefore("'"))
                else -> {}
            }
        }
    }
    // Compute SHA-1 hash of the signature
    return Triple(sha1sum(signature), timeStamp, route)
}

// Helper function to decode a hexadecimal string

private fun sha1sum(value: String): String {
    return try {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(value.toByteArray())
        bytes.joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        throw Exception("Attempt to create the signature failed miserably.")
    }
}

fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}


object CryptoAES {
    private const val HASH_CIPHER = "AES/CBC/PKCS7PADDING"
    private const val HASH_CIPHER_FALLBACK = "AES/CBC/PKCS5PADDING"
    private const val AES = "AES"

    fun decrypt(cipherText: String, keyBytes: ByteArray, ivBytes: ByteArray): String {
        return try {
            val cipherTextBytes = base64DecodeArray(cipherText)
            decryptAES(cipherTextBytes, keyBytes, ivBytes)
        } catch (_: Exception) {
            ""
        }
    }

    private fun decryptAES(
        cipherTextBytes: ByteArray,
        keyBytes: ByteArray,
        ivBytes: ByteArray
    ): String {
        return try {
            val cipher = try {
                Cipher.getInstance(HASH_CIPHER)
            } catch (_: Throwable) {
                Cipher.getInstance(HASH_CIPHER_FALLBACK)
            }
            val keyS = SecretKeySpec(keyBytes, AES)
            cipher.init(Cipher.DECRYPT_MODE, keyS, IvParameterSpec(ivBytes))
            cipher.doFinal(cipherTextBytes).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}


val languageMap: Map<String, Set<String>> = mapOf(
    "Afrikaans"   to setOf("af", "afr"),
    "Albanian"    to setOf("sq", "sqi", "alb"),
    "Amharic"     to setOf("am", "amh"),
    "Arabic"      to setOf("ar", "ara"),
    "Armenian"    to setOf("hy", "hye", "arm"),
    "Azerbaijani" to setOf("az", "aze"),
    "Basque"      to setOf("eu", "eus", "baq"),
    "Belarusian"  to setOf("be", "bel"),
    "Bengali"     to setOf("bn", "ben"),
    "Bosnian"     to setOf("bs", "bos"),
    "Bulgarian"   to setOf("bg", "bul"),
    "Catalan"     to setOf("ca", "cat"),
    "Chinese"     to setOf("zh", "zho", "chi"),
    "Croatian"    to setOf("hr", "hrv", "scr"),
    "Czech"       to setOf("cs", "ces", "cze"),
    "Danish"      to setOf("da", "dan"),
    "Dutch"       to setOf("nl", "nld", "dut"),
    "English"     to setOf("en", "eng"),
    "Estonian"    to setOf("et", "est"),
    "Filipino"    to setOf("tl", "tgl"),
    "Finnish"     to setOf("fi", "fin"),
    "French"      to setOf("fr", "fra", "fre"),
    "Galician"    to setOf("gl", "glg"),
    "Georgian"    to setOf("ka", "kat", "geo"),
    "German"      to setOf("de", "deu", "ger"),
    "Greek"       to setOf("el", "ell", "gre"),
    "Gujarati"    to setOf("gu", "guj"),
    "Hebrew"      to setOf("he", "heb"),
    "Hindi"       to setOf("hi", "hin"),
    "Hungarian"   to setOf("hu", "hun"),
    "Icelandic"   to setOf("is", "isl", "ice"),
    "Indonesian"  to setOf("id", "ind"),
    "Italian"     to setOf("it", "ita"),
    "Japanese"    to setOf("ja", "jpn"),
    "Kannada"     to setOf("kn", "kan"),
    "Kazakh"      to setOf("kk", "kaz"),
    "Korean"      to setOf("ko", "kor"),
    "Latvian"     to setOf("lv", "lav"),
    "Lithuanian"  to setOf("lt", "lit"),
    "Macedonian"  to setOf("mk", "mkd", "mac"),
    "Malay"       to setOf("ms", "msa", "may"),
    "Malayalam"   to setOf("ml", "mal"),
    "Maltese"     to setOf("mt", "mlt"),
    "Marathi"     to setOf("mr", "mar"),
    "Mongolian"   to setOf("mn", "mon"),
    "Nepali"      to setOf("ne", "nep"),
    "Norwegian"   to setOf("no", "nor"),
    "Persian"     to setOf("fa", "fas", "per"),
    "Polish"      to setOf("pl", "pol"),
    "Portuguese"  to setOf("pt", "por"),
    "Punjabi"     to setOf("pa", "pan"),
    "Romanian"    to setOf("ro", "ron", "rum"),
    "Russian"     to setOf("ru", "rus"),
    "Serbian"     to setOf("sr", "srp", "scc"),
    "Sinhala"     to setOf("si", "sin"),
    "Slovak"      to setOf("sk", "slk", "slo"),
    "Slovenian"   to setOf("sl", "slv"),
    "Spanish"     to setOf("es", "spa"),
    "Swahili"     to setOf("sw", "swa"),
    "Swedish"     to setOf("sv", "swe"),
    "Tamil"       to setOf("ta", "tam"),
    "Telugu"      to setOf("te", "tel"),
    "Thai"        to setOf("th", "tha"),
    "Turkish"     to setOf("tr", "tur"),
    "Ukrainian"   to setOf("uk", "ukr"),
    "Urdu"        to setOf("ur", "urd"),
    "Uzbek"       to setOf("uz", "uzb"),
    "Vietnamese"  to setOf("vi", "vie"),
    "Welsh"       to setOf("cy", "cym", "wel"),
    "Yiddish"     to setOf("yi", "yid")
)

fun getLanguage(code: String): String {
    val lower = code.lowercase()
    return languageMap.entries.firstOrNull { lower in it.value }?.key ?: "UnKnown"
}


fun getAnidbEid(jsonString: String, episodeNumber: Int?): Int? {
    if (episodeNumber == null) return null

    return try {
        val jsonObject = JSONObject(jsonString)
        val episodes = jsonObject.optJSONObject("episodes") ?: return null

        episodes.optJSONObject(episodeNumber.toString())
            ?.optInt("anidbEid", -1)
            ?.takeIf { it != -1 }
    } catch (e: Exception) {
        e.printStackTrace() // Logs the error but prevents breaking the app
        null
    }
}

val decryptMethods: Map<String, (String) -> String> = mapOf(
    "TsA2KGDGux" to { inputString ->
        inputString.reversed().replace("-", "+").replace("_", "/").let {
            val decoded = String(android.util.Base64.decode(it, android.util.Base64.DEFAULT))
            decoded.map { ch -> (ch.code - 7).toChar() }.joinToString("")
        }
    },
    "ux8qjPHC66" to { inputString ->
        val reversed = inputString.reversed()
        val hexPairs = reversed.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        val key = "X9a(O;FMV2-7VO5x;Ao\u0005:dN1NoFs?j,"
        hexPairs.mapIndexed { i, ch -> (ch.code xor key[i % key.length].code).toChar() }
            .joinToString("")
    },
    "xTyBxQyGTA" to { inputString ->
        val filtered = inputString.reversed().filterIndexed { i, _ -> i % 2 == 0 }
        String(android.util.Base64.decode(filtered, android.util.Base64.DEFAULT))
    },
    "IhWrImMIGL" to { inputString ->
        val reversed = inputString.reversed()
        val rot13 = reversed.map { ch ->
            when {
                ch in 'a'..'m' || ch in 'A'..'M' -> (ch.code + 13).toChar()
                ch in 'n'..'z' || ch in 'N'..'Z' -> (ch.code - 13).toChar()
                else -> ch
            }
        }.joinToString("")
        String(android.util.Base64.decode(rot13.reversed(), android.util.Base64.DEFAULT))
    },
    "o2VSUnjnZl" to { inputString ->
        val substitutionMap =
            ("xyzabcdefghijklmnopqrstuvwXYZABCDEFGHIJKLMNOPQRSTUVW" zip "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ").toMap()
        inputString.map { substitutionMap[it] ?: it }.joinToString("")
    },
    "eSfH1IRMyL" to { inputString ->
        val reversed = inputString.reversed()
        val shifted = reversed.map { (it.code - 1).toChar() }.joinToString("")
        shifted.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
    },
    "Oi3v1dAlaM" to { inputString ->
        inputString.reversed().replace("-", "+").replace("_", "/").let {
            val decoded = String(android.util.Base64.decode(it, android.util.Base64.DEFAULT))
            decoded.map { ch -> (ch.code - 5).toChar() }.joinToString("")
        }
    },
    "sXnL9MQIry" to { inputString ->
        val xorKey = "pWB9V)[*4I`nJpp?ozyB~dbr9yt!_n4u"
        val hexDecoded = inputString.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        val decrypted =
            hexDecoded.mapIndexed { i, ch -> (ch.code xor xorKey[i % xorKey.length].code).toChar() }
                .joinToString("")
        val shifted = decrypted.map { (it.code - 3).toChar() }.joinToString("")
        String(android.util.Base64.decode(shifted, android.util.Base64.DEFAULT))
    },
    "JoAHUMCLXV" to { inputString ->
        inputString.reversed().replace("-", "+").replace("_", "/").let {
            val decoded = String(android.util.Base64.decode(it, android.util.Base64.DEFAULT))
            decoded.map { ch -> (ch.code - 3).toChar() }.joinToString("")
        }
    },
    "KJHidj7det" to { input ->
        val decoded = String(
            android.util.Base64.decode(
                input.drop(10).dropLast(16),
                android.util.Base64.DEFAULT
            )
        )
        val key = """3SAY~#%Y(V%>5d/Yg${'$'}G[Lh1rK4a;7ok"""
        decoded.mapIndexed { i, ch -> (ch.code xor key[i % key.length].code).toChar() }
            .joinToString("")
    },
    "playerjs" to { x ->
        try {
            var a = x.drop(2)
            val b1: (String) -> String = { str ->
                android.util.Base64.encodeToString(
                    str.toByteArray(),
                    android.util.Base64.NO_WRAP
                )
            }
            val b2: (String) -> String =
                { str -> String(android.util.Base64.decode(str, android.util.Base64.DEFAULT)) }
            val patterns = listOf(
                "*,4).(_)()", "33-*.4/9[6", ":]&*1@@1=&", "=(=:19705/", "%?6497.[:4"
            )
            patterns.forEach { k -> a = a.replace("/@#@/" + b1(k), "") }
            b2(a)
        } catch (e: Exception) {
            "Failed to decode: ${e.message}"
        }
    }
)

fun parseAnimeData(jsonString: String): MetaAnimeData? {
    return try {
        sharedObjectMapper.readValue(jsonString, MetaAnimeData::class.java)
    } catch (_: Exception) {
        null // Return null for invalid JSON instead of crashing
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaMappings(
    @JsonProperty("themoviedb_id") val themoviedbId: String? = null,
    @JsonProperty("thetvdb_id") val thetvdbId: Int? = null,
    @JsonProperty("imdb_id") val imdbId: String? = null,
    @JsonProperty("mal_id") val malId: Int? = null,
    @JsonProperty("anilist_id") val anilistId: Int? = null,
    @JsonProperty("kitsu_id") val kitsuid: String? = null,
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ImageData(
    @JsonProperty("coverType") val coverType: String?,
    @JsonProperty("url") val url: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaEpisode(
    @JsonProperty("episode") val episode: String?,
    @JsonProperty("airdate") val airdate: String?,
    @JsonProperty("airDateUtc") val airDateUtc: String?,
    @JsonProperty("length") val length: Int?,
    @JsonProperty("runtime") val runtime: Int?,
    @JsonProperty("image") val image: String?,
    @JsonProperty("title") val title: Map<String, String>?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("rating") val rating: String?,
    @JsonProperty("finaleType") val finaleType: String?
)


@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaAnimeData(
    @JsonProperty("titles") val titles: Map<String, String>? = null,
    @JsonProperty("images") val images: List<ImageData>? = null,
    @JsonProperty("episodes") val episodes: Map<String, MetaEpisode>? = null,
    @JsonProperty("mappings") val mappings: MetaMappings? = null
)

fun cleanTitle(title: String): String {

    val name = title.replace(Regex("\\.[a-zA-Z0-9]{2,4}$"), "")

    val patterns = listOf(
        Regex("(WEB[-_. ]?DL|WEB[-_. ]?RIP|BLURAY|HDRIP|BDRIP|BRRIP|DVDRIP|HDTV|CAM|TS)", RegexOption.IGNORE_CASE),
        Regex("(H[ .]?264|H[ .]?265|X264|X265|HEVC|AVC)", RegexOption.IGNORE_CASE),
        Regex("(DDP[ .]?[0-9]\\.[0-9]|DD[ .]?[0-9]\\.[0-9]|AAC[ .]?[0-9]\\.[0-9]|AC3|DTS|EAC3|FLAC|MP3)", RegexOption.IGNORE_CASE),
        Regex("(ATMOS|DUAL)", RegexOption.IGNORE_CASE),
        Regex("(HDR10\\+?|HDR|DV|DOLBY[ .]?VISION)", RegexOption.IGNORE_CASE),
        Regex("\\b(NF|AMZN|DSNP|HULU|CRAV|ATVP)\\b", RegexOption.IGNORE_CASE)
    )

    val results = linkedSetOf<String>()

    for (pattern in patterns) {
        pattern.findAll(name).forEach { match ->
            var value = match.value.uppercase()
            value = value
                .replace(Regex("WEB[-_. ]?DL"), "WEB-DL")
                .replace(Regex("WEB[-_. ]?RIP"), "WEBRIP")
                .replace(Regex("H[ .]?265"), "H265")
                .replace(Regex("H[ .]?264"), "H264")
                .replace(Regex("DOLBY[ .]?VISION"), "DOLBYVISION")
                .replace("2160P", "4K")
            results.add(value)
        }
    }
    return results.joinToString(" ")
}



//Anichi

fun String.fixUrlPath(): String {
    return if (this.contains(".json?")) "https://allanime.day$this"
    else "https://allanime.day" + URI(this).path + ".json?" + URI(this).query
}


fun decrypthex(inputStr: String): String {
    val hexString = if (inputStr.startsWith("-")) {
        inputStr.substringAfterLast("-")
    } else {
        inputStr
    }

    val bytes = ByteArray(hexString.length / 2) { i ->
        val hexByte = hexString.substring(i * 2, i * 2 + 2)
        (hexByte.toInt(16) and 0xFF).toByte()
    }

    return bytes.joinToString("") { (it.toInt() xor 56).toChar().toString() }
}

suspend fun getM3u8Qualities(
    m3u8Link: String,
    referer: String,
    qualityName: String,
): List<ExtractorLink> {
    return M3u8Helper.generateM3u8(
        qualityName,
        m3u8Link,
        referer
    )
}


suspend fun getRedirectLinks(url: String): String {
    val doc = app.get(url).toString()
    val regex = "s\\('o','([A-Za-z0-9+/=]+)'|ck\\('_wp_http_\\d+','([^']+)'".toRegex()
    val combinedString = buildString {
        regex.findAll(doc).forEach { matchResult ->
            val extractedValue = matchResult.groups[1]?.value ?: matchResult.groups[2]?.value
            if (!extractedValue.isNullOrEmpty()) append(extractedValue)
        }
    }
    return try {
        val decodedString = base64Decode(hdhubpen(base64Decode(base64Decode(combinedString))))
        val jsonObject = JSONObject(decodedString)
        val encodedurl = base64Decode(jsonObject.optString("o", "")).trim()
        val data = hdhubencode(jsonObject.optString("data", "")).trim()
        val wphttp1 = jsonObject.optString("blog_url", "").trim()
        val directlink = runCatching {
            app.get("$wphttp1?re=$data".trim()).document.select("body").text().trim()
        }.getOrDefault("").trim()

        encodedurl.ifEmpty { directlink }
    } catch (e: Exception) {
        Log.e("Error:", "Error processing links $e")
        "" // Return an empty string on failure
    }
}


fun hdhubencode(encoded: String): String {
    return String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
}


fun hdhubpen(value: String): String {
    return value.map {
        when (it) {
            in 'A'..'Z' -> ((it - 'A' + 13) % 26 + 'A'.code).toChar()
            in 'a'..'z' -> ((it - 'a' + 13) % 26 + 'a'.code).toChar()
            else -> it
        }
    }.joinToString("")
}


private fun md5(input: ByteArray): String {
    return MessageDigest.getInstance("MD5").digest(input)
        .joinToString("") { "%02x".format(it) }
}

private fun reverseString(input: String): String = input.reversed()

fun generateXClientToken(hardcodedTimestamp: Long? = null): String {
    val timestamp = (hardcodedTimestamp ?: System.currentTimeMillis()).toString()
    val reversed = reverseString(timestamp)
    val hash = md5(reversed.toByteArray())
    return "$timestamp,$hash"
}

fun generateXTrSignature(
    method: String,
    accept: String? = "application/json",
    contentType: String? = "application/json",
    url: String,
    body: String? = null,
    useAltKey: Boolean = false,
    hardcodedTimestamp: Long? = null
): String {
    val timestamp = hardcodedTimestamp ?: System.currentTimeMillis()

    val canonical = buildCanonicalString(
        method = method,
        accept = accept,
        contentType = contentType,
        url = url,
        body = body,
        timestamp = timestamp
    )
    val secretKey = if (useAltKey) {
        BuildConfig.MOVIEBOX_SECRET_KEY_ALT
    } else {
        BuildConfig.MOVIEBOX_SECRET_KEY_DEFAULT
    }
    val secretBytes = android.util.Base64.decode(secretKey, android.util.Base64.DEFAULT)
    val mac = Mac.getInstance("HmacMD5").apply {
        init(SecretKeySpec(secretBytes, "HmacMD5"))
    }
    val rawSignature = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
    val signatureBase64 = android.util.Base64.encodeToString(rawSignature, android.util.Base64.NO_WRAP)
    return "$timestamp|2|$signatureBase64"
}


private fun buildCanonicalString(
    method: String,
    accept: String?,
    contentType: String?,
    url: String,
    body: String?,
    timestamp: Long
): String {
    val parsed = url.toUri()
    val path = parsed.path ?: ""

    // Build query string with sorted parameters (if any)
    val query = if (parsed.queryParameterNames.isNotEmpty()) {
        parsed.queryParameterNames.sorted().joinToString("&") { key ->
            parsed.getQueryParameters(key).joinToString("&") { value ->
                "$key=$value"  // Don't URL encode here - Python doesn't do it
            }
        }
    } else ""

    val canonicalUrl = if (query.isNotEmpty()) "$path?$query" else path

    val bodyBytes = body?.toByteArray(Charsets.UTF_8)
    val bodyHash = if (bodyBytes != null) {
        val trimmed = if (bodyBytes.size > 102400) bodyBytes.copyOfRange(0, 102400) else bodyBytes
        md5(trimmed)
    } else ""

    val bodyLength = bodyBytes?.size?.toString() ?: ""
    return "${method.uppercase()}\n" +
            "${accept ?: ""}\n" +
            "${contentType ?: ""}\n" +
            "$bodyLength\n" +
            "$timestamp\n" +
            "$bodyHash\n" +
            canonicalUrl
}

fun vidrockEncode(
    tmdb: Int? = null,
    type: String,
    season: Int? = null,
    episode: Int? = null,
): String {
    val zw = base64Decode("eDdrOW1QcVQycld2WTh6QTViQzNuRjZoSjJsSzRtTjk=")
    val s = if (type == "tv" && season != null && episode != null) {
        "${tmdb}_${season}_${episode}"
    } else {
        tmdb
    }.toString()
    val keyBytes = zw.toByteArray(Charsets.UTF_8)
    val ivBytes = zw.substring(0, 16).toByteArray(Charsets.UTF_8)
    val keySpec = SecretKeySpec(keyBytes, "AES")
    val ivSpec = IvParameterSpec(ivBytes)
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

    val encrypted = cipher.doFinal(s.toByteArray(Charsets.UTF_8))

    val base64 = base64Encode(encrypted)
    return base64
        .replace("+", "-")
        .replace("/", "_")
        .replace("=", "")
}

fun cinemaOSGenerateHash(tmdbId: Int?, imdbId: String?, season: Int?, episode: Int?): String {
    val primary = "a7f3b9c2e8d4f1a6b5c9e2d7f4a8b3c6e1d9f7a4b2c8e5d3f9a6b4c1e7d2f8a5"
    val secondary = "d3f8a5b2c9e6d1f7a4b8c5e2d9f3a6b1c7e4d8f2a9b5c3e7d4f1a8b6c2e9d5f3"

    var message = "tmdbId:$tmdbId|imdbId:$imdbId"

    if (season != null && episode != null) {
        message += "|seasonId:$season|episodeId:$episode"
    }
    val firstHash = calculateHmacSha256(message, primary)
    return calculateHmacSha256(firstHash, secondary)
}


private fun calculateHmacSha256(data: String, key: String): String {
    val algorithm = "HmacSHA256"
    val secretKeySpec = SecretKeySpec(key.toByteArray(), algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(secretKeySpec)

    val bytes = mac.doFinal(data.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

// Helper function to convert byte array to hex string
fun bytesToHex(bytes: ByteArray): String {
    val hexChars = CharArray(bytes.size * 2)
    for (i in bytes.indices) {
        val v = bytes[i].toInt() and 0xFF
        hexChars[i * 2] = "0123456789abcdef"[v ushr 4]
        hexChars[i * 2 + 1] = "0123456789abcdef"[v and 0x0F]
    }
    return String(hexChars)
}


// Helper function to convert hex string to byte array

fun cinemaOSDecryptResponse(e: CinemaOSReponseData?): String? {

    if (e?.encrypted.isNullOrEmpty() || e.cin.isEmpty() || e.mao.isEmpty() || e.salt.isEmpty()) {
        return null
    }

    val encrypted = e.encrypted
    val cin = e.cin
    val mao = e.mao
    val salt = e.salt

    val passwordStr = "a1b2c3d4e4f6477658455678901477567890abcdef1234567890abcdef123456"

    val ivBytes = hexStringToByteArray(cin)
    val authTagBytes = hexStringToByteArray(mao)
    val encryptedBytes = hexStringToByteArray(encrypted)
    val saltBytes = hexStringToByteArray(salt)

    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(passwordStr.toCharArray(), saltBytes, 100000, 256)
    val tmp = factory.generateSecret(spec)
    val key = SecretKeySpec(tmp.encoded, "AES")

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val gcmSpec = GCMParameterSpec(128, ivBytes)
    cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

    val decryptedBytes = cipher.doFinal(encryptedBytes + authTagBytes)
    return String(decryptedBytes, Charsets.UTF_8)
}

fun hexStringToByteArray(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have even length" }
    return hex.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun parseCinemaOSSources(jsonString: String): List<Map<String, String>> {
    val json = JSONObject(jsonString)
    val sourcesObject = json.getJSONObject("sources")
    val sourcesList = mutableListOf<Map<String, String>>()

    val keys = sourcesObject.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val source = sourcesObject.getJSONObject(key)

        // Check if source has "qualities" object
        if (source.has("qualities")) {
            val qualities = source.getJSONObject("qualities")
            val qualityKeys = qualities.keys()

            while (qualityKeys.hasNext()) {
                val qualityKey = qualityKeys.next()
                val qualityObj = qualities.getJSONObject(qualityKey)

                val sourceMap = mutableMapOf<String, String>()
                sourceMap["server"] = source.optString("server", key)
                sourceMap["url"] = qualityObj.optString("url", "")
                sourceMap["type"] = qualityObj.optString("type", "")
                sourceMap["speed"] = source.optString("speed", "")
                sourceMap["bitrate"] = source.optString("bitrate", "")
                sourceMap["quality"] = qualityKey // Use the quality key (e.g., "480", "720")

                sourcesList.add(sourceMap)
            }
        } else {
            // Regular source with direct URL
            val sourceMap = mutableMapOf<String, String>()
            sourceMap["server"] = source.optString("server", key)
            sourceMap["url"] = source.optString("url", "")
            sourceMap["type"] = source.optString("type", "")
            sourceMap["speed"] = source.optString("speed", "")
            sourceMap["bitrate"] = source.optString("bitrate", "")
            sourceMap["quality"] = source.optString("quality", "")

            sourcesList.add(sourceMap)
        }
    }

    return sourcesList
}

// Hex → ByteArray
fun fromHex(hex: String): ByteArray {
    val cleanHex = hex.replace(Regex("[^0-9a-fA-F]"), "")
    require(cleanHex.length % 2 == 0) { "Invalid hex" }
    return ByteArray(cleanHex.length / 2) { i ->
        cleanHex.substring(2 * i, 2 * i + 2).toInt(16).toByte()
    }
}

// AES key validator
fun importKey(rawKey: ByteArray): SecretKeySpec {
    require(rawKey.size == 16 || rawKey.size == 32) { "AES_KEY must be 16 or 32 bytes" }
    return SecretKeySpec(rawKey, "AES")
}

// AES-CBC decryption
fun aesDecrypt(cipherHex: String, keyBytes: ByteArray, ivBytes: ByteArray): String {
    val decrypted = try {
        require(ivBytes.size == 16) { "AES_IV must be 16 bytes" }

        val cipherBytes = fromHex(cipherHex)
        val keySpec = importKey(keyBytes)
        val ivSpec = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

        cipher.doFinal(cipherBytes)
    } catch (e: Exception) {
        TODO("Not yet implemented")
    }
    return decrypted.toString(Charsets.UTF_8)
}


fun hexStringToByteArray2(hex: String): ByteArray {
    val result = ByteArray(hex.length / 2)
    for (i in hex.indices step 2) {
        val value = hex.substring(i, i + 2).toInt(16)
        result[i / 2] = value.toByte()
    }
    return result
}

fun derivePbkdf2Key(
    password: String,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int
): ByteArray {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLength * 8)
    return factory.generateSecret(spec).encoded
}


/**
 * Remove PKCS7 padding
 */
fun unpadData(data: ByteArray): ByteArray {
    val padding = data[data.size - 1].toInt() and 0xFF
    if (padding < 1 || padding > data.size) {
        return data
    }
    return data.copyOf(data.size - padding)
}

fun hasHost(url: String): Boolean {
    return try {
        val host = URL(url).host
        !host.isNullOrEmpty()
    } catch (_: Exception) {
        false
    }
}

/**
 * Run multiple suspend functions concurrently with a limit on simultaneous executions.
 *
 * @param concurrency Limit of concurrent tasks.
 * @param tasks Vararg of suspend functions to execute.
 */
suspend fun runLimitedAsync(
    concurrency: Int = 5,
    vararg tasks: suspend () -> Unit
) = coroutineScope {
    val semaphore = Semaphore(concurrency)

    tasks.map { task ->
        async(Dispatchers.IO) {
            semaphore.withPermit {
                try {
                    task()
                } catch (e: Exception) {
                    // Log error but continue
                    Log.e("runLimitedAsync", "Task failed: ${e.message}")
                }
            }
        }
    }.awaitAll()
}


fun decryptVidzeeUrl(encrypted: String, key: ByteArray): String {
    val decoded = base64Decode(encrypted)
    val parts = decoded.split(":")
    if (parts.size != 2) throw IllegalArgumentException("Invalid encrypted format")

    val iv = base64DecodeArray(parts[0])
    val cipherData = base64DecodeArray(parts[1])

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val secretKey = SecretKeySpec(key, "AES")
    cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

    val decryptedBytes = cipher.doFinal(cipherData)
    return decryptedBytes.toString(Charsets.UTF_8)
}

suspend fun yflixDecode(text: String?): String {
    return try {
        val res = app.get("${BuildConfig.YFXENC}?text=$text").text
        JSONObject(res).getString("result")
    } catch (_: Exception) {
        ""
    }
}

private val JSON = "application/json; charset=utf-8".toMediaType()

suspend fun yflixDecodeReverse(text: String): String {
    val jsonBody = """{"text":"$text"}""".toRequestBody(JSON)

    return try {
        val res = app.post(
            BuildConfig.YFXDEC,
            requestBody = jsonBody
        ).text
        JSONObject(res).getString("result")
    } catch (_: Exception) {
        ""
    }
}

fun yflixextractVideoUrlFromJson(jsonData: String): String {
    val jsonObject = JSONObject(jsonData)
    return jsonObject.getString("url")
}


fun String.fixSourceUrl(): String {
    return this.replace("/manifest.json", "").replace("stremio://", "https://")
}

fun generateHexKey32(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}



suspend fun fetchTmdbLogoUrl(
    tmdbAPI: String,
    apiKey: String,
    type: TvType,
    tmdbId: Int?,
    appLangCode: String?
): String? {

    if (tmdbId == null) return null

    val url = if (type == TvType.Movie)
        "$tmdbAPI/movie/$tmdbId/images?api_key=$apiKey"
    else
        "$tmdbAPI/tv/$tmdbId/images?api_key=$apiKey"

    val json = runCatching { JSONObject(app.get(url).text) }.getOrNull() ?: return null
    val logos = json.optJSONArray("logos") ?: return null
    if (logos.length() == 0) return null

    val lang = appLangCode?.trim()?.lowercase()?.substringBefore("-")

    fun path(o: JSONObject) = o.optString("file_path")
    fun isSvg(o: JSONObject) = path(o).endsWith(".svg", true)
    fun urlOf(o: JSONObject) = "https://image.tmdb.org/t/p/w500${path(o)}"

    // Language match
    var svgFallback: JSONObject? = null

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        val p = path(logo)
        if (p.isBlank()) continue

        val l = logo.optString("iso_639_1").trim().lowercase()
        if (l == lang) {
            if (!isSvg(logo)) return urlOf(logo)
            if (svgFallback == null) svgFallback = logo
        }
    }
    svgFallback?.let { return urlOf(it) }

    // Highest voted fallback
    var best: JSONObject? = null
    var bestSvg: JSONObject? = null

    fun voted(o: JSONObject) = o.optDouble("vote_average", 0.0) > 0 && o.optInt("vote_count", 0) > 0

    fun better(a: JSONObject?, b: JSONObject): Boolean {
        if (a == null) return true
        val aAvg = a.optDouble("vote_average", 0.0)
        val aCnt = a.optInt("vote_count", 0)
        val bAvg = b.optDouble("vote_average", 0.0)
        val bCnt = b.optInt("vote_count", 0)
        return bAvg > aAvg || (bAvg == aAvg && bCnt > aCnt)
    }

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        if (!voted(logo)) continue

        if (isSvg(logo)) {
            if (better(bestSvg, logo)) bestSvg = logo
        } else {
            if (better(best, logo)) best = logo
        }
    }

    best?.let { return urlOf(it) }
    bestSvg?.let { return urlOf(it) }

    // No language match & no voted logos
    return null
}

fun String?.fixTitle(): String? {
    return this?.replace(Regex("[!%:']|( &)"), "")?.replace(" ", "-")?.lowercase()
        ?.replace("-–-", "-")
}

suspend fun getHindMoviezLinks(
    source: String,
    url: String,
    callback: (ExtractorLink) -> Unit
) {
    val response = app.get(url, timeout = 10000L)
    val doc = response.document

    val name = doc.selectFirst("div.container p:contains(Name:)")
        ?.text()
        ?.substringAfter("Name:")
        ?.trim()
        .orEmpty()

    val fileSize = doc.selectFirst("div.container p:contains(Size:)")
        ?.text()
        ?.substringAfter("Size:")
        ?.trim()
        .orEmpty()

    val extractedSpecs = buildExtractedTitle(extractSpecs(name))
    val quality = getIndexQuality(name)

    runAllAsync(

        // Primary links
        {
            val redirectUrl = doc.selectFirst("a.btn-info")?.attr("href") ?: return@runAllAsync
            val redirectDoc = app.get(redirectUrl, referer = response.url, timeout = 10000L).document

            redirectDoc.select("a.button").map { btn ->
                callback(
                    newExtractorLink(
                        source,
                        "$source $extractedSpecs[$fileSize]",
                        btn.attr("href"),
                        ExtractorLinkType.VIDEO
                    ) {
                        this.quality = quality
                    }
                )
            }
        },

        // HCloud
        {
            val hCloudUrl = doc.selectFirst("a.btn-dark")?.attr("href") ?: return@runAllAsync

            callback(
                newExtractorLink(
                    "$source[HCloud]",
                    "$source[HCloud] $extractedSpecs[$fileSize]",
                    hCloudUrl,
                    ExtractorLinkType.VIDEO
                ) {
                    this.quality = quality
                }
            )
        }
    )
}

fun buildExtractedTitle(extracted: Map<String, List<String>>): String {
    val specs = ORDERED_SPEC_CATEGORIES
        .flatMap { extracted[it] ?: emptyList() }
        .distinct()
        .joinToString(" ")

    val size = extracted["size"]?.firstOrNull()

    return if (size != null) {
        "$specs [$size]"
    } else {
        specs
    }
}

private val ORDERED_SPEC_CATEGORIES = listOf("quality", "codec", "audio", "hdr", "language")
private val FILE_SIZE_REGEX = """(\d+(?:\.\d+)?\s?(?:MB|GB))""".toRegex(RegexOption.IGNORE_CASE)

val SPEC_OPTIONS = mapOf(
    "quality" to listOf(
        mapOf("value" to "BluRay", "label" to "BluRay"),
        mapOf("value" to "BluRay REMUX", "label" to "BluRay REMUX"),
        mapOf("value" to "BRRip", "label" to "BRRip"),
        mapOf("value" to "BDRip", "label" to "BDRip"),
        mapOf("value" to "WEB-DL", "label" to "WEB-DL"),
        mapOf("value" to "HDRip", "label" to "HDRip"),
        mapOf("value" to "DVDRip", "label" to "DVDRip"),
        mapOf("value" to "HDTV", "label" to "HDTV"),
        mapOf("value" to "CAM", "label" to "CAM"),
        mapOf("value" to "TeleSync", "label" to "TeleSync"),
        mapOf("value" to "SCR", "label" to "SCR"),
        mapOf("value" to "10bit", "label" to "10bit"),
        mapOf("value" to "8bit", "label" to "8bit"),
    ),
    "codec" to listOf(
        mapOf("value" to "x264", "label" to "x264"),
        mapOf("value" to "x265", "label" to "x265 (HEVC)"),
        mapOf("value" to "h.264", "label" to "H.264 (AVC)"),
        mapOf("value" to "h.265", "label" to "H.265 (HEVC)"),
        mapOf("value" to "hevc", "label" to "HEVC"),
        mapOf("value" to "avc", "label" to "AVC"),
        mapOf("value" to "mpeg-2", "label" to "MPEG-2"),
        mapOf("value" to "mpeg-4", "label" to "MPEG-4"),
        mapOf("value" to "vp9", "label" to "VP9")
    ),
    "audio" to listOf(
        mapOf("value" to "AAC", "label" to "AAC"),
        mapOf("value" to "AC3", "label" to "AC3 (Dolby Digital)"),
        mapOf("value" to "DTS", "label" to "DTS"),
        mapOf("value" to "DTS-HD MA", "label" to "DTS-HD MA"),
        mapOf("value" to "TrueHD", "label" to "Dolby TrueHD"),
        mapOf("value" to "Atmos", "label" to "Dolby Atmos"),
        mapOf("value" to "DD+", "label" to "DD+"),
        mapOf("value" to "Dolby Digital Plus", "label" to "Dolby Digital Plus"),
        mapOf("value" to "DTS Lossless", "label" to "DTS Lossless")
    ),
    "hdr" to listOf(
        mapOf("value" to "DV", "label" to "Dolby Vision"),
        mapOf("value" to "HDR10+", "label" to "HDR10+"),
        mapOf("value" to "HDR", "label" to "HDR"),
        mapOf("value" to "SDR", "label" to "SDR")
    ),
    "language" to listOf(
        mapOf("value" to "HIN", "label" to "Hindi🇮🇳"),
        mapOf("value" to "Hindi", "label" to "Hindi🇮🇳"),
        mapOf("value" to "Tamil", "label" to "Tamil🇮🇳"),
        mapOf("value" to "ENG", "label" to "English🇺🇸"),
        mapOf("value" to "English", "label" to "English🇺🇸"),
        mapOf("value" to "Korean", "label" to "Korean🇰🇷"),
        mapOf("value" to "KOR", "label" to "Korean🇰🇷"),
        mapOf("value" to "Japanese", "label" to "Japanese🇯🇵"),
        mapOf("value" to "Chinese", "label" to "Chinese🇨🇳"),
        mapOf("value" to "Telugu", "label" to "Telugu🇮🇳"),
    )
)

private val SPEC_REGEX_CACHE = SPEC_OPTIONS.mapValues { (_, options) ->
    options.map { option ->
        val value = option["value"] as String
        val label = option["label"] as String
        label to "\\b${Regex.escape(value)}\\b".toRegex(RegexOption.IGNORE_CASE)
    }
}

fun extractSpecs(inputString: String): Map<String, List<String>> {
    val results = mutableMapOf<String, List<String>>()

    SPEC_REGEX_CACHE.forEach { (category, options) ->
        val matches = options.asSequence()
            .filter { (_, regex) -> regex.containsMatchIn(inputString) }
            .map { (label, _) -> label }
            .toList()

        results[category] = matches
    }

    val sizeMatch = FILE_SIZE_REGEX.find(inputString)
    if (sizeMatch != null) {
        results["size"] = listOf(sizeMatch.groupValues[1])
    }

    return results.toMap()
}

private val BROWSER_FINGERPRINT by lazy {
    val raw = listOf(
        "1920x1080x24",
        "Asia/Kolkata",
        "en-US",
        "Win32",
        "8",
        "8",
        "canvas_stub_xdmovies",
        "ANGLE (NVIDIA)",
        "no_touch",
        "3",
        "true",
        "unset"
    ).joinToString("|||")
    MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(32)
}

fun generateBrowserFingerprint(): String {
    return BROWSER_FINGERPRINT
}


suspend fun bypassXD(url: String): String? {
    // Follow initial redirect to get actual bypass URL
    val redirect = app.get(url, allowRedirects = false)
        .headers["location"] ?: return null

    val baseUrl = getBaseUrl(redirect)
    val code = redirect.substringAfterLast("/").takeIf { it.isNotEmpty() } ?: return null
    val fingerprint = generateBrowserFingerprint()

    val mouseData = mapOf(
        "eventCount"    to 220,
        "moveCount"     to 185,
        "clickCount"    to 3,
        "totalDistance" to 3800,
        "hasMovement"   to true,
        "duration"      to 27000
    )

    val baseHeaders = mapOf(
        "User-Agent"      to USER_AGENT,
        "Accept"          to "*/*",
        "Origin"          to baseUrl,
        "Referer"         to "$baseUrl/r/$code",
        "sec-fetch-site"  to "same-origin",
        "sec-fetch-mode"  to "cors",
        "sec-fetch-dest"  to "empty"
    )

    // ── STEP 1: Create session ────────────────────────────────────────────────
    val sessionJson = try {
        JSONObject(
            app.post(
                "$baseUrl/api/session",
                json = mapOf(
                    "code"        to code,
                    "fingerprint" to fingerprint,
                    "mouseData"   to mouseData
                ),
                headers = baseHeaders
            ).text
        )
    } catch (_: Exception) { return null }

    val sessionId  = sessionJson.optString("sessionId").takeIf { it.isNotEmpty() } ?: return null

    val cookieHeaders = baseHeaders + mapOf("Cookie" to "sid=$sessionId")

    // ── STEP 2: Rebind (simulates step-2 page reload) ────────────────────────
    val rebindJson = try {
        JSONObject(
            app.post(
                "$baseUrl/api/session/rebind",
                json = mapOf("fingerprint" to fingerprint),
                headers = cookieHeaders
            ).text
        )
    } catch (_: Exception) { return null }

    val rebindToken = rebindJson.optString("token").takeIf { it.isNotEmpty() } ?: return null

    // ── STEP 3: WebSocket heartbeats ─────────────────────────────────────────
    // Server only advances visible-time counter when it receives
    // "heartbeat" events over the Socket.IO WebSocket while
    // visibility is "visible". A plain delay() does nothing.
    val wsBaseUrl = baseUrl
        .replace("https://", "wss://")
        .replace("http://",  "ws://")

    val visibleTimeDone = CompletableDeferred<Unit>()
    val okHttpClient    = OkHttpClient()

    val wsRequest = Request.Builder()
        .url("$wsBaseUrl/socket.io/?EIO=4&transport=websocket")
        .addHeader("Origin",     baseUrl)
        .addHeader("Cookie",     "sid=$sessionId")
        .addHeader("User-Agent", USER_AGENT)
        .build()

    var heartbeatJob: kotlinx.coroutines.Job? = null

    val webSocket = okHttpClient.newWebSocket(wsRequest, object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            // Socket.IO: connect to default namespace
            webSocket.send("40")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when {
                // Socket.IO ping → reply with pong to keep connection alive
                text == "2" -> webSocket.send("3")

                // Namespace connected → bind session + mark visible + start heartbeats
                text.startsWith("40") -> {
                    webSocket.send("""42["bind","$rebindToken"]""")
                    webSocket.send("""42["visibility","visible"]""")

                    heartbeatJob = extractorCallbackScope.launch {
                        var elapsed = 0
                        while (elapsed < 28) {
                            delay(1000)
                            elapsed++

                            webSocket.send("""42["heartbeat"]""")
                            webSocket.send(
                                """42["mouseActivity",${
                                    JSONObject(
                                        mouseData.toMutableMap().apply {
                                            put("duration", elapsed * 1000)
                                        }
                                    )
                                }]"""
                            )
                        }
                        visibleTimeDone.complete(Unit)
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            visibleTimeDone.completeExceptionally(t)
        }
    })

    // Wait for 28 heartbeats (≈ 28 seconds of visible time)
    try {
        withTimeout(40_000) { visibleTimeDone.await() }
    } catch (_: Exception) {
        return null
    } finally {
        heartbeatJob?.cancel()
        webSocket.close(1000, null)
        okHttpClient.dispatcher.executorService.shutdown()
    }

    // ── STEP 4: Complete session — retry until token returned ─────────────────
    var finalToken: String? = null

    repeat(5) { attempt ->
        if (finalToken != null) return@repeat
        try {
            val json = JSONObject(
                app.post(
                    "$baseUrl/api/session/complete",
                    json = mapOf(
                        "fingerprint" to fingerprint,
                        "mouseData"   to mouseData.toMutableMap().apply {
                            put("duration", 28000 + attempt * 2000)
                        },
                        "honeypot"    to ""   // must be empty — bots fill this
                    ),
                    headers = cookieHeaders
                ).text
            )
            json.optString("token").takeIf { it.isNotEmpty() }?.let { finalToken = it }
        } catch (_: Exception) { }

        if (finalToken == null) delay(2000)
    }

    val token = finalToken ?: return null

    // ── STEP 5: Final redirect ────────────────────────────────────────────────
    return app.get(
        "$baseUrl/go/$sessionId?t=$token",
        allowRedirects = false,
        headers = cookieHeaders
    ).headers["location"]
}

suspend fun safeGet(
    url: String,
    headers: Map<String, String>? = null,
    referer: String? = null,
    timeout: Long? = null,
    interceptor: Interceptor? = null,
    allowRedirects: Boolean = true, // kept for compatibility
    cacheTime: Int = 0              // default added
) = appGlobalSemaphore.withPermit {
    app.get(
        url = url,
        headers = headers ?: emptyMap(),
        referer = referer,
        timeout = timeout ?: 10000L,
        interceptor = interceptor,
        allowRedirects = allowRedirects,
        cacheTime = cacheTime
    )
}