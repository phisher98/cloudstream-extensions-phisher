package com.phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
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
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.RequestBodyTypes
import com.phisher98.StreamPlay.Companion.anilistAPI
import com.phisher98.StreamPlay.Companion.fourthAPI
import com.phisher98.StreamPlay.Companion.thrirdAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
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
import java.util.Base64
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
import kotlin.experimental.xor
import kotlin.math.min


val mimeType = arrayOf(
    "video/x-matroska",
    "video/mp4",
    "video/x-msvideo"
)

val M3U8_HEADERS = mapOf(
    "User-Agent" to "Mozilla/5.0 (Android) ExoPlayer",
    "Accept" to "*/*",
    "Accept-Encoding" to "identity",
    "Connection" to "keep-alive",
)

suspend fun extractMovieAPIlinks(serverid: String, movieid: String, MOVIE_API: String): String {
    val link =
        app.get("$MOVIE_API/ajax/get_stream_link?id=$serverid&movie=$movieid").documentLarge.toString()
            .substringAfter("link\":\"").substringBefore("\",")
    return link
}

suspend fun getDirectGdrive(url: String): String {
    val fixUrl = if (url.contains("&export=download")) {
        url
    } else {
        "https://drive.google.com/uc?id=${
            Regex("(?:\\?id=|/d/)(\\S+)/").find("$url/")?.groupValues?.get(1)
        }&export=download"
    }

    val doc = app.get(fixUrl).documentLarge
    val form = doc.select("form#download-form").attr("action")
    val uc = doc.select("input#uc-download-link").attr("value")
    return app.post(
        form, data = mapOf(
            "uc-download-link" to uc
        )
    ).url

}

suspend fun bypassHrefli(url: String): String? {
    fun Document.getFormUrl(): String {
        return this.select("form#landing").attr("action")
    }

    fun Document.getFormData(): Map<String, String> {
        return this.select("form#landing input").associate { it.attr("name") to it.attr("value") }
    }

    val host = getBaseUrl(url)
    var res = app.get(url).documentLarge
    var formUrl = res.getFormUrl()
    var formData = res.getFormData()

    res = app.post(formUrl, data = formData).documentLarge
    formUrl = res.getFormUrl()
    formData = res.getFormData()

    res = app.post(formUrl, data = formData).documentLarge
    val skToken = res.selectFirst("script:containsData(?go=)")?.data()?.substringAfter("?go=")
        ?.substringBefore("\"") ?: return null
    val driveUrl = app.get(
        "$host?go=$skToken", cookies = mapOf(
            skToken to "${formData["_wp_http2"]}"
        )
    ).documentLarge.selectFirst("meta[http-equiv=refresh]")?.attr("content")?.substringAfter("url=")
    val path = app.get(driveUrl ?: return null).text.substringAfter("replace(\"")
        .substringBefore("\")")
    if (path == "/404") return null
    return fixUrl(path, getBaseUrl(driveUrl))
}

suspend fun cinematickitBypass(url: String): String? {
    return try {
        val cleanedUrl = url.replace("&#038;", "&")
        val encodedLink = cleanedUrl.substringAfter("safelink=").substringBefore("-")
        if (encodedLink.isEmpty()) return null
        val decodedUrl = base64Decode(encodedLink)
        val doc = app.get(decodedUrl).documentLarge
        val goValue = doc.select("form#landing input[name=go]").attr("value")
        if (goValue.isBlank()) return null
        val decodedGoUrl = base64Decode(goValue).replace("&#038;", "&")
        val responseDoc = app.get(decodedGoUrl).documentLarge
        val script = responseDoc.select("script").firstOrNull { it.data().contains("window.location.replace") }?.data() ?: return null
        val regex = Regex("""window\.location\.replace\s*\(\s*["'](.+?)["']\s*\)\s*;?""")
        val match = regex.find(script) ?: return null
        val redirectPath = match.groupValues[1]
        return if (redirectPath.startsWith("http")) redirectPath else URI(decodedGoUrl).let { "${it.scheme}://${it.host}$redirectPath" }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


@RequiresApi(Build.VERSION_CODES.O)
suspend fun cinematickitloadBypass(url: String): String? {
    return try {
        val cleanedUrl = url.replace("&#038;", "&")
        val encodedLink = cleanedUrl.substringAfter("safelink=").substringBefore("-")
        if (encodedLink.isEmpty()) return null
        val decodedUrl = base64Decode(encodedLink)
        val doc = app.get(decodedUrl).documentLarge
        val goValue = doc.select("form#landing input[name=go]").attr("value")
        return base64Decode(goValue)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
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
    val fixSize = if(size.isNotEmpty()) " $size" else ""
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            val label = if (fixSize.isBlank()) {
                "$source [${link.source}]"
            } else {
                "$source [${link.source} $fixSize]"
            }

            callback.invoke(
                newExtractorLink(
                    source,
                    label,
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
        CoroutineScope(Dispatchers.IO).launch {
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

fun getTitleSlug(title: String? = null): Pair<String?, String?> {
    val slug = title.createSlug()
    return slug?.replace("-", "\\W") to title?.replace(" ", "_")
}

fun getIndexQuery(
    title: String? = null,
    year: Int? = null,
    season: Int? = null,
    episode: Int? = null
): String {
    val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
    return (if (season == null) {
        "$title ${year ?: ""}"
    } else {
        "$title S${seasonSlug}E${episodeSlug}"
    }).trim()
}

fun searchIndex(
    title: String? = null,
    season: Int? = null,
    episode: Int? = null,
    year: Int? = null,
    response: String,
    isTrimmed: Boolean = true,
): List<IndexMedia>? {
    val files = tryParseJson<IndexSearch>(response)?.data?.files?.filter { media ->
        matchingIndex(
            media.name ?: return null,
            media.mimeType ?: return null,
            title ?: return null,
            year,
            season,
            episode
        )
    }?.distinctBy { it.name }?.sortedByDescending { it.size?.toLongOrNull() ?: 0 } ?: return null

    return if (isTrimmed) {
        files.let { file ->
            listOfNotNull(
                file.find { it.name?.contains("2160p", true) == true },
                file.find { it.name?.contains("1080p", true) == true }
            )
        }
    } else {
        files
    }
}

fun matchingIndex(
    mediaName: String?,
    mediaMimeType: String?,
    title: String?,
    year: Int?,
    season: Int?,
    episode: Int?,
    include720: Boolean = false
): Boolean {
    val (wSlug, dwSlug) = getTitleSlug(title)
    val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
    return (if (season == null) {
        mediaName?.contains(Regex("(?i)(?:$wSlug|$dwSlug).*$year")) == true
    } else {
        mediaName?.contains(Regex("(?i)(?:$wSlug|$dwSlug).*S${seasonSlug}.?E${episodeSlug}")) == true
    }) && mediaName?.contains(
        if (include720) Regex("(?i)(2160p|1080p|720p)") else Regex("(?i)(2160p|1080p)")
    ) == true && ((mediaMimeType in mimeType) || mediaName.contains(Regex("\\.mkv|\\.mp4|\\.avi")))
}

fun decodeIndexJson(json: String): String {
    val slug = json.reversed().substring(24)
    return base64Decode(slug.substring(0, slug.length - 20))
}

fun String?.createSlug(): String? {
    return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
        ?.trim()
        ?.replace("\\s+".toRegex(), "-")
        ?.lowercase()
}

fun bytesToGigaBytes(number: Double): Double = number / 1024000000

fun getKisskhTitle(str: String?): String? {
    return str?.replace(Regex("[^a-zA-Z\\d]"), "-")
}

fun String.getFileSize(): Float? {
    val size = Regex("(?i)(\\d+\\.?\\d+\\sGB|MB)").find(this)?.groupValues?.get(0)?.trim()
    val num = Regex("(\\d+\\.?\\d+)").find(size ?: return null)?.groupValues?.get(0)?.toFloat()
        ?: return null
    return when {
        size.contains("GB") -> num * 1000000
        else -> num * 1000
    }
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
        app.get(url).documentLarge
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


fun getQuality(str: String): Int {
    return when (str) {
        "360p" -> Qualities.P240.value
        "480p" -> Qualities.P360.value
        "720p" -> Qualities.P480.value
        "1080p" -> Qualities.P720.value
        "1080p Ultra" -> Qualities.P1080.value
        else -> getQualityFromName(str)
    }
}

fun String.encodeUrl(): String {
    val url = URL(this)
    val uri = URI(url.protocol, url.userInfo, url.host, url.port, url.path, url.query, url.ref)
    return uri.toURL().toString()
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
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
        unixTimeMS < dateTime
    } catch (t: Throwable) {
        logError(t)
        false
    }
}

fun getDate(): TmdbDate {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
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


private fun getVideoQuality(string: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(string ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
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
                .parsedSafe<ExternalResponse>()?.data?.fileList?.filterNotNull()?.filter {
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
    val gson = Gson()
    data class Response(val result: List<EpisoderesponseKAA>)
    val response = gson.fromJson(json, Response::class.java)
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

    private const val KEY_SIZE = 32 // 256 bits
    private const val IV_SIZE = 16 // 128 bits
    private const val SALT_SIZE = 8 // 64 bits
    private const val HASH_CIPHER = "AES/CBC/PKCS7PADDING"
    private const val HASH_CIPHER_FALLBACK = "AES/CBC/PKCS5PADDING"
    private const val AES = "AES"
    private const val KDF_DIGEST = "MD5"

    /**
     * Decrypt using CryptoJS defaults compatible method.
     * Uses KDF equivalent to OpenSSL's EVP_BytesToKey function
     *
     * http://stackoverflow.com/a/29152379/4405051
     * @param cipherText base64 encoded ciphertext
     * @param password passphrase
     */
    fun decrypt(cipherText: String, password: String): String {
        return try {
            val ctBytes = base64DecodeArray(cipherText)
            val saltBytes = Arrays.copyOfRange(ctBytes, SALT_SIZE, IV_SIZE)
            val cipherTextBytes = Arrays.copyOfRange(ctBytes, IV_SIZE, ctBytes.size)
            val md5 = MessageDigest.getInstance("MD5")
            val keyAndIV = generateKeyAndIV(
                KEY_SIZE,
                IV_SIZE,
                1,
                saltBytes,
                password.toByteArray(Charsets.UTF_8),
                md5
            )
            decryptAES(
                cipherTextBytes,
                keyAndIV?.get(0) ?: ByteArray(KEY_SIZE),
                keyAndIV?.get(1) ?: ByteArray(IV_SIZE),
            )
        } catch (e: Exception) {
            ""
        }
    }

    fun decryptWithSalt(cipherText: String, salt: String, password: String): String {
        return try {
            val ctBytes = base64DecodeArray(cipherText)
            val md5: MessageDigest = MessageDigest.getInstance("MD5")
            val keyAndIV = generateKeyAndIV(
                KEY_SIZE,
                IV_SIZE,
                1,
                salt.decodeHex(),
                password.toByteArray(Charsets.UTF_8),
                md5,
            )
            decryptAES(
                ctBytes,
                keyAndIV?.get(0) ?: ByteArray(KEY_SIZE),
                keyAndIV?.get(1) ?: ByteArray(IV_SIZE),
            )
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Decrypt using CryptoJS defaults compatible method.
     *
     * @param cipherText base64 encoded ciphertext
     * @param keyBytes key as a bytearray
     * @param ivBytes iv as a bytearray
     */
    fun decrypt(cipherText: String, keyBytes: ByteArray, ivBytes: ByteArray): String {
        return try {
            val cipherTextBytes = base64DecodeArray(cipherText)
            decryptAES(cipherTextBytes, keyBytes, ivBytes)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Encrypt using CryptoJS defaults compatible method.
     *
     * @param plainText plaintext
     * @param keyBytes key as a bytearray
     * @param ivBytes iv as a bytearray
     */
    fun encrypt(plainText: String, keyBytes: ByteArray, ivBytes: ByteArray): String {
        return try {
            val cipherTextBytes = plainText.toByteArray()
            encryptAES(cipherTextBytes, keyBytes, ivBytes)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Decrypt using CryptoJS defaults compatible method.
     *
     * @param cipherTextBytes encrypted text as a bytearray
     * @param keyBytes key as a bytearray
     * @param ivBytes iv as a bytearray
     */
    private fun decryptAES(
        cipherTextBytes: ByteArray,
        keyBytes: ByteArray,
        ivBytes: ByteArray
    ): String {
        return try {
            val cipher = try {
                Cipher.getInstance(HASH_CIPHER)
            } catch (e: Throwable) {
                Cipher.getInstance(HASH_CIPHER_FALLBACK)
            }
            val keyS = SecretKeySpec(keyBytes, AES)
            cipher.init(Cipher.DECRYPT_MODE, keyS, IvParameterSpec(ivBytes))
            cipher.doFinal(cipherTextBytes).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Encrypt using CryptoJS defaults compatible method.
     *
     * @param plainTextBytes encrypted text as a bytearray
     * @param keyBytes key as a bytearray
     * @param ivBytes iv as a bytearray
     */
    private fun encryptAES(
        plainTextBytes: ByteArray,
        keyBytes: ByteArray,
        ivBytes: ByteArray
    ): String {
        return try {
            val cipher = try {
                Cipher.getInstance(HASH_CIPHER)
            } catch (e: Throwable) {
                Cipher.getInstance(HASH_CIPHER_FALLBACK)
            }
            val keyS = SecretKeySpec(keyBytes, AES)
            cipher.init(Cipher.ENCRYPT_MODE, keyS, IvParameterSpec(ivBytes))
            base64Encode(cipher.doFinal(plainTextBytes))
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Generates a key and an initialization vector (IV) with the given salt and password.
     *
     * https://stackoverflow.com/a/41434590
     * This method is equivalent to OpenSSL's EVP_BytesToKey function
     * (see https://github.com/openssl/openssl/blob/master/crypto/evp/evp_key.c).
     * By default, OpenSSL uses a single iteration, MD5 as the algorithm and UTF-8 encoded password data.
     *
     * @param keyLength the length of the generated key (in bytes)
     * @param ivLength the length of the generated IV (in bytes)
     * @param iterations the number of digestion rounds
     * @param salt the salt data (8 bytes of data or `null`)
     * @param password the password data (optional)
     * @param md the message digest algorithm to use
     * @return an two-element array with the generated key and IV
     */
    private fun generateKeyAndIV(
        keyLength: Int,
        ivLength: Int,
        iterations: Int,
        salt: ByteArray,
        password: ByteArray,
        md: MessageDigest,
    ): Array<ByteArray?>? {
        val digestLength = md.digestLength
        val requiredLength = (keyLength + ivLength + digestLength - 1) / digestLength * digestLength
        val generatedData = ByteArray(requiredLength)
        var generatedLength = 0
        return try {
            md.reset()

            // Repeat process until sufficient data has been generated
            while (generatedLength < keyLength + ivLength) {
                // Digest data (last digest if available, password data, salt if available)
                if (generatedLength > 0) md.update(
                    generatedData,
                    generatedLength - digestLength,
                    digestLength
                )
                md.update(password)
                md.update(salt, 0, SALT_SIZE)
                md.digest(generatedData, generatedLength, digestLength)

                // additional rounds
                for (i in 1 until iterations) {
                    md.update(generatedData, generatedLength, digestLength)
                    md.digest(generatedData, generatedLength, digestLength)
                }
                generatedLength += digestLength
            }

            // Copy key and IV into separate byte arrays
            val result = arrayOfNulls<ByteArray>(2)
            result[0] = generatedData.copyOfRange(0, keyLength)
            if (ivLength > 0) result[1] = generatedData.copyOfRange(keyLength, keyLength + ivLength)
            result
        } catch (e: Exception) {
            throw e
        } finally {
            // Clean out temporary data
            Arrays.fill(generatedData, 0.toByte())
        }
    }

    // Stolen from AnimixPlay(EN) / GogoCdnExtractor
    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
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


suspend fun getPlayer4uUrl(
    name: String,
    selectedQuality: Int,
    url: String,
    referer: String?,
    callback: (ExtractorLink) -> Unit
) {
    val response = app.get(url, referer = referer)
    var script = getAndUnpack(response.text).takeIf { it.isNotEmpty() }
        ?: response.documentLarge.selectFirst("script:containsData(sources:)")?.data()
    if (script == null) {
        val iframeUrl =
            Regex("""<iframe src="(.*?)"""").find(response.text)?.groupValues?.getOrNull(1)
                ?: return
        val iframeResponse = app.get(
            iframeUrl,
            referer = null,
            headers = mapOf("Accept-Language" to "en-US,en;q=0.5")
        )
        script = getAndUnpack(iframeResponse.text).takeIf { it.isNotEmpty() } ?: return
    }

    val m3u8 = Regex("\"hls2\":\\s*\"(.*?m3u8.*?)\"").find(script)?.groupValues?.getOrNull(1).orEmpty()
    callback(newExtractorLink(name, name, m3u8) {
        this.quality=selectedQuality
        this.type=ExtractorLinkType.M3U8
    })

}

fun getPlayer4UQuality(quality: String): Int {
    return when (quality) {
        "4K", "2160P" -> Qualities.P2160.value
        "FHD", "1080P" -> Qualities.P1080.value
        "HQ", "HD", "720P", "DVDRIP", "TVRIP", "HDTC", "PREDVD" -> Qualities.P720.value
        "480P" -> Qualities.P480.value
        "360P", "CAM" -> Qualities.P360.value
        "DS" -> Qualities.P144.value
        "SD" -> Qualities.P480.value
        "WEBRIP" -> Qualities.P720.value
        "BLURAY", "BRRIP" -> Qualities.P1080.value
        "HDRIP" -> Qualities.P1080.value
        "TS" -> Qualities.P480.value
        "R5" -> Qualities.P480.value
        "SCR" -> Qualities.P480.value
        "TC" -> Qualities.P480.value
        else -> Qualities.Unknown.value
    }
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

@RequiresApi(Build.VERSION_CODES.O)
fun generateVrfAES(movieId: String, userId: String): String {
    // Step 1: Derive key = SHA-256("hack_" + userId)
    val driveKey = base64Decode("MmpFWUwzSlJ4Qg==")
    val keyData = "${driveKey}_$userId".toByteArray(Charsets.UTF_8)
    val keyBytes = MessageDigest.getInstance("SHA-256").digest(keyData)
    val keySpec = SecretKeySpec(keyBytes, "AES")
    val ivSpec = IvParameterSpec(ByteArray(16))
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
    val encrypted = cipher.doFinal(movieId.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(encrypted)
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
        val objectMapper = ObjectMapper()
        objectMapper.readValue(jsonString, MetaAnimeData::class.java)
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

    val normalized = name
        .replace(Regex("WEB[-_. ]?DL", RegexOption.IGNORE_CASE), "WEB-DL")
        .replace(Regex("WEB[-_. ]?RIP", RegexOption.IGNORE_CASE), "WEBRIP")
        .replace(Regex("H[ .]?265", RegexOption.IGNORE_CASE), "H265")
        .replace(Regex("H[ .]?264", RegexOption.IGNORE_CASE), "H264")
        .replace(Regex("DDP[ .]?([0-9]\\.[0-9])", RegexOption.IGNORE_CASE), "DDP$1")

    val parts = normalized.split(" ", "_", ".")

    val sourceTags = setOf(
        "WEB-DL", "WEBRIP", "BLURAY", "HDRIP",
        "DVDRIP", "HDTV", "CAM", "TS", "BRRIP", "BDRIP"
    )

    val codecTags = setOf("H264", "H265", "X264", "X265", "HEVC", "AVC")

    val audioTags = setOf("AAC", "AC3", "DTS", "MP3", "FLAC", "DD", "DDP", "EAC3")

    val audioExtras = setOf("ATMOS")

    val hdrTags = setOf("SDR","HDR", "HDR10", "HDR10+", "DV", "DOLBYVISION")

    val filtered = parts.mapNotNull { part ->
        val p = part.uppercase()

        when {
            sourceTags.contains(p) -> p
            codecTags.contains(p) -> p
            audioTags.any { p.startsWith(it) } -> p
            audioExtras.contains(p) -> p
            hdrTags.contains(p) -> {
                when (p) {
                    "DV", "DOLBYVISION" -> "DOLBYVISION"
                    else -> p
                }
            }
            p == "NF" || p == "CR" -> p
            else -> null
        }
    }

    return filtered.distinct().joinToString(" ")
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


suspend fun <T> retryIO(
    times: Int = 3,
    delayTime: Long = 1000,
    block: suspend () -> T
): T {
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            delay(delayTime)
        }
    }
    return block() // last attempt, let it throw
}

suspend fun elevenMoviesToken(rawData: String): String {
    // AES setup
    val jsonUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/main/output.json"
    val jsonString = app.get(jsonUrl).text
    val gson = Gson()
    val json: Elevenmoviesjson = gson.fromJson(jsonString, Elevenmoviesjson::class.java)
    val keyHex = json.key_hex
    val ivHex = json.iv_hex
    val aesKey = SecretKeySpec(keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), "AES")
    val aesIv = IvParameterSpec(ivHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())

    // AES encrypt
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)
    val aesEncrypted = cipher.doFinal(rawData.toByteArray())
    val aesHex = aesEncrypted.joinToString("") { "%02x".format(it) }

    // XOR operation
    val xorKeyHex = json.xor_key
    val xorKey = xorKeyHex.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    val xorResult = aesHex.mapIndexed { index, char ->
        ((char.code.toByte() xor xorKey[index % xorKey.size]).toInt()).toChar()
    }.joinToString("")


    val src = json.src
    val dst = json.dst

    val b64 = base64Encode(xorResult.toByteArray())
        .replace("+", "-")
        .replace("/", "_")
        .replace("=", "")

    return b64.map { char ->
        val index = src.indexOf(char)
        if (index != -1) dst[index] else char
    }.joinToString("")
}


suspend fun hdhubgetRedirectLinks(url: String): String {
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
            app.get("$wphttp1?re=$data".trim()).documentLarge.select("body").text().trim()
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

suspend fun dispatchToExtractor(
    link: String,
    source: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) {
    when {
        link.contains("hubdrive", ignoreCase = true) -> Hubdrive().getUrl(link, source, subtitleCallback, callback)
        link.contains("hubcloud", ignoreCase = true) -> HubCloud().getUrl(link, source, subtitleCallback, callback)
        link.contains("hubcdn", ignoreCase = true) -> HUBCDN().getUrl(link, source, subtitleCallback, callback)
        else -> loadSourceNameExtractor(source, link, "", subtitleCallback, callback)
    }
}


fun customBase64EncodeVidfast(input: ByteArray): String {
    val sourceChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
    val targetChars = "7EkRi2WnMSlgLbXm_jy1vtO69ehrAV0-saUB5FGpoq3QuNIZ8wJ4PfdHxzTDKYCc"

    // Standard Base64 URL-safe encode, no padding or wrap
    val base64 = android.util.Base64.encodeToString(
        input,
        android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
    )

    // Translate characters to custom charset
    val translationMap = sourceChars.zip(targetChars).toMap()
    return base64.map { translationMap[it] ?: it }.joinToString("")
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

fun vidrockEncode(tmdb: String, type: String, season: Int? = null, episode: Int? = null): String {
    val base = if (type == "tv" && season != null && episode != null) {
        "$tmdb-$season-$episode"
    } else {
        val map = mapOf(
            '0' to 'a', '1' to 'b', '2' to 'c', '3' to 'd', '4' to 'e',
            '5' to 'f', '6' to 'g', '7' to 'h', '8' to 'i', '9' to 'j'
        )
        tmdb.map { map[it] ?: it }.joinToString("")
    }
    val reversed = base.reversed()
    val firstEncode = base64Encode(reversed.toByteArray())
    val doubleEncode = base64Encode(firstEncode.toByteArray())

    return doubleEncode
}

fun cinemaOSGenerateHash(t: CinemaOsSecretKeyRequest,isSeries: Boolean): String {
    val primary = "a7f3b9c2e8d4f1a6b5c9e2d7f4a8b3c6e1d9f7a4b2c8e5d3f9a6b4c1e7d2f8a5"
    val secondary = "d3f8a5b2c9e6d1f7a4b8c5e2d9f3a6b1c7e4d8f2a9b5c3e7d4f1a8b6c2e9d5f3"


    // Create content identifier string
    val contentString = createContentString(t)

    // First HMAC with primary key
    val firstHash = calculateHmacSha256(contentString, primary)

    // Second HMAC with secondary key
    return calculateHmacSha256(firstHash, secondary)

}


private fun createContentString(info: CinemaOsSecretKeyRequest): String {
    val parts = mutableListOf<String>()

    info.tmdbId.let { parts.add("tmdbId:$it") }
    info.imdbId.let { parts.add("imdbId:$it") }
    info.seasonId.takeIf { it.isNotEmpty() }?.let { parts.add("seasonId:$it") }
    info.episodeId.takeIf { it.isNotEmpty() }?.let { parts.add("episodeId:$it") }

    return parts.joinToString("|")
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


fun cinemaOSDecryptResponse(e: CinemaOSReponseData?): Any {
    val encrypted = e?.encrypted
    val cin = e?.cin
    val mao = e?.mao
    val salt = e?.salt

    val keyBytes =  "a1b2c3d4e4f6477658455678901477567890abcdef1234567890abcdef123456".toByteArray()
    val ivBytes = hexStringToByteArray(cin.toString())
    val authTagBytes = hexStringToByteArray(mao.toString())
    val encryptedBytes =hexStringToByteArray(encrypted.toString())
    val saltBytes = hexStringToByteArray(salt.toString())

    // Derive key with PBKDF2-HMAC-SHA256
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(keyBytes.map { it.toInt().toChar() }.toCharArray(), saltBytes, 100000, 256)
    val tmp = factory.generateSecret(spec)
    val key = SecretKeySpec(tmp.encoded, "AES")

    // AES-256-GCM decrypt
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val gcmSpec = GCMParameterSpec(128, ivBytes)
    cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
    val decryptedBytes = cipher.doFinal(encryptedBytes + authTagBytes)
    val decryptedData = String(decryptedBytes)

    return decryptedData // Use your JSON parser
}


// Helper function to convert hex string to byte array
fun hexStringToByteArray(hex: String): ByteArray {
    val len = hex.length
    require(len % 2 == 0) { "Hex string must have even length" }

    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        i += 2
    }
    return data
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



fun toHex(bytes: ByteArray): String {
    return bytes.joinToString("") { "%02x".format(it) }
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

/**
 * PKCS7 padding implementation
 */
fun padData(data: ByteArray, blockSize: Int): ByteArray {
    val padding = blockSize - (data.size % blockSize)
    val result = ByteArray(data.size + padding)
    System.arraycopy(data, 0, result, 0, data.size)
    for (i in data.size until result.size) {
        result[i] = padding.toByte()
    }
    return result
}

fun customEncode(input: ByteArray): String {
    val sourceChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
    val targetChars = "4stjqN6BT05-L8rQe_HxWmAVv9icYKaCDzIP1fZ7kwXRyFhd2GEng3SMJlUubOop"

    val translationMap = sourceChars.zip(targetChars).toMap()
    val encoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Base64.getUrlEncoder().withoutPadding().encodeToString(input)
    } else {
        TODO("VERSION.SDK_INT < O")
    }

    return encoded.map { char ->
        translationMap[char] ?: char
    }.joinToString("")
}

fun parseServers(jsonString: String): List<VidFastServer> {
    val servers = mutableListOf<VidFastServer>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val server = VidFastServer(
                name = jsonObject.getString("name"),
                description = jsonObject.getString("description"),
                image = jsonObject.getString("image"),
                data = jsonObject.getString("data")
            )
            servers.add(server)
        }
    } catch (e: Exception) {
        Log.e("salman731", "Manual parsing failed: ${e.message}")
    }
    return servers
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
    } catch (e: Exception) {
        false
    }
}

fun generateKeyIv(keySize: Int = 32): KeyIvResult {

    val secureRandom = SecureRandom()

    val keyBytes = ByteArray(keySize)
    secureRandom.nextBytes(keyBytes)

    val ivBytes = ByteArray(16) // 16 bytes for AES IV
    secureRandom.nextBytes(ivBytes)

    return KeyIvResult(
        keyBytes = keyBytes,
        ivBytes = ivBytes,
        keyHex = toHex(keyBytes),
        ivHex = toHex(ivBytes)
    )
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


suspend fun <T> retry(
    times: Int = 3,
    delayMillis: Long = 1000,
    block: suspend () -> T
): T? {
    repeat(times) { attempt ->
        try {
            return block()
        } catch (_: Throwable) {
            if (attempt < times - 1) delay(delayMillis)
        }
    }
    return null
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

    val appLang = appLangCode?.substringBefore("-")?.lowercase()
    val url = if (type == TvType.Movie) {
        "$tmdbAPI/movie/$tmdbId/images?api_key=$apiKey"
    } else {
        "$tmdbAPI/tv/$tmdbId/images?api_key=$apiKey"
    }

    val json = runCatching { JSONObject(app.get(url).text) }.getOrNull() ?: return null
    val logos = json.optJSONArray("logos") ?: return null
    if (logos.length() == 0) return null

    fun logoUrlAt(i: Int): String = "https://image.tmdb.org/t/p/w500${logos.getJSONObject(i).optString("file_path")}"
    fun isSvg(i: Int): Boolean = logos.getJSONObject(i).optString("file_path").endsWith(".svg", ignoreCase = true)

    if (!appLang.isNullOrBlank()) {
        var svgFallback: String? = null
        for (i in 0 until logos.length()) {
            val logo = logos.optJSONObject(i) ?: continue
            if (logo.optString("iso_639_1") == appLang) {
                if (isSvg(i)) {
                    if (svgFallback == null) svgFallback = logoUrlAt(i)
                } else {
                    return logoUrlAt(i)
                }
            }
        }
        if (svgFallback != null) return svgFallback
    }

    var enSvgFallback: String? = null
    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        if (logo.optString("iso_639_1") == "en") {
            if (isSvg(i)) {
                if (enSvgFallback == null) enSvgFallback = logoUrlAt(i)
            } else {
                return logoUrlAt(i)
            }
        }
    }
    if (enSvgFallback != null) return enSvgFallback

    for (i in 0 until logos.length()) {
        if (!isSvg(i)) return logoUrlAt(i)
    }

    return logoUrlAt(0)
}

private val QUALITY_REGEX_MAP = listOf(
    Regex("""\b(4k|2160p?|2160)\b""", RegexOption.IGNORE_CASE) to Qualities.P2160.value,
    Regex("""\b1440p?|1440\b""", RegexOption.IGNORE_CASE)     to Qualities.P1440.value,
    Regex("""\b1080p?|1080\b""", RegexOption.IGNORE_CASE)     to Qualities.P1080.value,
    Regex("""\b720p?|720\b""", RegexOption.IGNORE_CASE)      to Qualities.P720.value,
    Regex("""\b480p?|480\b""", RegexOption.IGNORE_CASE)      to Qualities.P480.value
)
suspend fun getSessionAndCsrfforFlixindia(baseUrl: String): Pair<String, String>? {
    val res = app.get(baseUrl)

    val sessionId = res.cookies["PHPSESSID"] ?: return null

    val csrf = Regex(
        """window\.CSRF_TOKEN\s*=\s*['"]([a-f0-9]{64})['"]"""
    ).find(res.text)?.groupValues?.get(1) ?: return null

    return sessionId to csrf
}

suspend fun getHindMoviezLinks(
    source: String,
    url: String,
    callback: (ExtractorLink) -> Unit
) {
    val response = app.get(url)
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
            val redirectDoc = app.get(redirectUrl, referer = response.url).document

            redirectDoc.select("a.button").forEach { btn ->
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
    val orderedCategories = listOf("quality", "codec", "audio", "hdr", "language")

    val specs = orderedCategories
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

fun extractSpecs(inputString: String): Map<String, List<String>> {
    val results = mutableMapOf<String, List<String>>()

    SPEC_OPTIONS.forEach { (category, options) ->
        val matches = options.filter { option ->
            val value = option["value"] as String
            val regexPattern = "\\b${Regex.escape(value)}\\b".toRegex(RegexOption.IGNORE_CASE)
            regexPattern.containsMatchIn(inputString)
        }.map { it["label"] as String }

        results[category] = matches
    }

    val fileSizeRegex = """(\d+(?:\.\d+)?\s?(?:MB|GB))""".toRegex(RegexOption.IGNORE_CASE)
    val sizeMatch = fileSizeRegex.find(inputString)
    if (sizeMatch != null) {
        results["size"] = listOf(sizeMatch.groupValues[1])
    }

    return results.toMap()
}

