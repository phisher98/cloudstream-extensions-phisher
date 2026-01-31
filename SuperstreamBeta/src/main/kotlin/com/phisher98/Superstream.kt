package com.phisher98

import android.content.SharedPreferences
import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.getQualityFromString
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.SuperStreamExtractor.invokeExternalM3u8Source
import com.phisher98.SuperStreamExtractor.invokeExternalSource
import com.phisher98.SuperStreamExtractor.invokeInternalSource
import com.phisher98.SuperStreamExtractor.invokeOpenSubs
import com.phisher98.SuperStreamExtractor.invokeWatchsomuch
import com.phisher98.Superstream.CipherUtils.getVerify
import okhttp3.FormBody
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.Cipher.ENCRYPT_MODE
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

open class Superstream(sharedPref: SharedPreferences?=null) : MainAPI() {
    override var name = "SuperStream Beta"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val instantLinkLoading = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
    )
    companion object
    {
        private const val Supertoken = BuildConfig.SuperToken
    }
    val uitoken = sharedPref?.getString("token", null)?.substringAfter("ui=")
    enum class ResponseTypes(val value: Int) {
        Series(2),
        Movies(1);

        fun toTvType(): TvType {
            return if (this == Series) TvType.TvSeries else TvType.Movie
        }
        companion object {
            fun getResponseType(value: Int?): ResponseTypes {
                return entries.firstOrNull { it.value == value } ?: Movies
            }
        }
    }
    private val headers = mapOf(
        "Platform" to "android",
        "Accept" to "charset=utf-8",
        "Cookie" to "ci=168aec549ca68e",
    )

    private class UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            return chain.proceed(
                chain.request()
                    .newBuilder()
                    .removeHeader("user-agent")
                    .addHeader(
                        "user-agent",
                        value = "okhttp/3.12.6"
                    ).build()
            )
        }
    }

    // Random 32 length string
    private fun randomToken(): String {
        return (0..31).joinToString("") {
            (('0'..'9') + ('a'..'f')).random().toString()
        }
    }

    private val token = randomToken()
    private val cinemeta_url = "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb/meta"
    private object CipherUtils {
        private const val ALGORITHM = "DESede"
        private const val TRANSFORMATION = "DESede/CBC/PKCS5Padding"
        fun encrypt(str: String, key: String, iv: String): String? {
            return try {
                val cipher: Cipher = Cipher.getInstance(TRANSFORMATION)
                val bArr = ByteArray(24)
                val bytes: ByteArray = key.toByteArray()
                var length = if (bytes.size <= 24) bytes.size else 24
                System.arraycopy(bytes, 0, bArr, 0, length)
                while (length < 24) {
                    bArr[length] = 0
                    length++
                }
                cipher.init(
                    ENCRYPT_MODE,
                    SecretKeySpec(bArr, ALGORITHM),
                    IvParameterSpec(iv.toByteArray())
                )

                String(Base64.encode(cipher.doFinal(str.toByteArray()), 2), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun md5(str: String): String? {
            return MD5Util.md5(str)?.let { HexDump.toHexString(it).lowercase() }
        }

        fun getVerify(str: String?, str2: String, str3: String): String? {
            if (str != null) {
                return md5(md5(str2) + str3 + str)
            }
            return null
        }
    }

    private object HexDump {
        private val HEX_DIGITS = charArrayOf(
            '0',
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            'A',
            'B',
            'C',
            'D',
            'E',
            'F'
        )

        @JvmOverloads
        fun toHexString(bArr: ByteArray, i: Int = 0, i2: Int = bArr.size): String {
            val cArr = CharArray(i2 * 2)
            var i3 = 0
            for (i4 in i until i + i2) {
                val b = bArr[i4].toInt()
                val i5 = i3 + 1
                val cArr2 = HEX_DIGITS
                cArr[i3] = cArr2[b ushr 4 and 15]
                i3 = i5 + 1
                cArr[i5] = cArr2[b and 15]
            }
            return String(cArr)
        }
    }

    private object MD5Util {
        fun md5(str: String): ByteArray? {
            return md5(str.toByteArray())
        }

        fun md5(bArr: ByteArray?): ByteArray? {
            return try {
                val digest = MessageDigest.getInstance("MD5")
                digest.update(bArr ?: return null)
                digest.digest()
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
                null
            }
        }
    }

// ================== EMBED CERTS HERE ==================

    private val CLIENT_CERT_PEM = """
-----BEGIN CERTIFICATE-----
MIIEFTCCAv2gAwIBAgIUCrILmXOevO03gUhhbEhG/wZb2uAwDQYJKoZIhvcNAQEL
BQAwgagxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQH
Ew1TYW4gRnJhbmNpc2NvMRkwFwYDVQQKExBDbG91ZGZsYXJlLCBJbmMuMRswGQYD
VQQLExJ3d3cuY2xvdWRmbGFyZS5jb20xNDAyBgNVBAMTK01hbmFnZWQgQ0EgM2Q0
ZDQ4ZTQ2ZmI3MGM1NzgxZmI0N2VhNzk4MjMxZDMwHhcNMjQwNjA0MDkxMTAwWhcN
MzkwNjAxMDkxMTAwWjAiMQswCQYDVQQGEwJVUzETMBEGA1UEAxMKQ2xvdWRmbGFy
ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJhpMlr/+IatuBqpuZuA
6QvqdI2QiFb1UMVujb/xiaBC/vqJMlMenLSDysk8xd4fLeC+GC8AyWf1IMJIz6d9
rBjOhN4D+MxvgphufkdIVqs63SqKcrr/ZL0JaRpxxEg/pKqSjH55Ik71keB8tt0m
mQ76WK1swMydOAqn6DIKVAi7wF9acWyX/6Ly+cmxfueLDZvkLigXl3gMHbuoa5Y+
CadqKl2qlijhnvjpuEbAvyDyXWe838TUi0PYMMVuOu7PV4By2LINsm+gKv83od4k
RCSWTrLKlgfqneqnudMrqeWckNUHGVB+3Lruw1ebB/Rs4gJ59VhJYpbNmM2mYT0r
VQkCAwEAAaOBuzCBuDATBgNVHSUEDDAKBggrBgEFBQcDAjAMBgNVHRMBAf8EAjAA
MB0GA1UdDgQWBBSF9Jkz4ZkbS5+LANO3YGWZRuX/PDAfBgNVHSMEGDAWgBTj01Q6
MJPAjpPqCEcv8rjxAUTO9jBTBgNVHR8ETDBKMEigRqBEhkJodHRwOi8vY3JsLmNs
b3VkZmxhcmUuY29tL2U1YTYzNzc5LTQ3NWQtNGI5OS04YzQxLTIwMjE5MmZhNjNj
ZC5jcmwwDQYJKoZIhvcNAQELBQADggEBALD+9MsfANm7fbzYH5/lXl07hwn2KSN8
PH7zxyo87ED62IL9U7YOnhb3rqLS1RXUzyHEmb9kzYgzKzzNrELdKH77vNk172Vk
iRQwGD0MZiYNERWhmmBtjV1oxllz74fL4+aZTYAespIbOekmFn9NZJ+XSdyF9RqS
fzDiz27GP5ZSHHI6xwdUP+a87N/RnfI4UwGxyXvPpHfoAZWjoXDqLKKwEL36/Sqi
nGcp970y0gnZ2zI2ehqivsF7BATMZqvU+LJKCH8NEE2bnbCJ6qlPHZWZFNKYWBOe
I1Crf0gNAWD/q3HKGMVZiyxlhU6SsQS4/08tDXXQjWYfl6i3oviexSk=
-----END CERTIFICATE-----
"""

    private val CLIENT_KEY_PEM = """
-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCYaTJa//iGrbga
qbmbgOkL6nSNkIhW9VDFbo2/8YmgQv76iTJTHpy0g8rJPMXeHy3gvhgvAMln9SDC
SM+nfawYzoTeA/jMb4KYbn5HSFarOt0qinK6/2S9CWkaccRIP6Sqkox+eSJO9ZHg
fLbdJpkO+litbMDMnTgKp+gyClQIu8BfWnFsl/+i8vnJsX7niw2b5C4oF5d4DB27
qGuWPgmnaipdqpYo4Z746bhGwL8g8l1nvN/E1ItD2DDFbjruz1eActiyDbJvoCr/
N6HeJEQklk6yypYH6p3qp7nTK6nlnJDVBxlQfty67sNXmwf0bOICefVYSWKWzZjN
pmE9K1UJAgMBAAECggEAQFvnxjKiJWkVPbkfJjHU91GtnxwB3sqfrYdmN0ANUE4K
MwydYikinj2q87iEi6wZ6PYM60hHRG1oRHKPsZgphJ4s0D3YIagS+0Bpdbtv0cW9
IBovoZR4WzUum1qgOqwZYmgZCM0pNjOPwr6XT6Ldbkw8BxvN/HmFcUZ/ECZ5XugW
cKqKoy0HSlxwXT4PUAgLVfL4KvWy4A4yJJF24zgRKE4QYveOR4nUFvoRdxhuAyYW
xsajItj6sc6Jyr9FJzdw5Ra9EFwcWFM4uDdjHoaQrjwKId9fkCA+9eUCERWKTxCR
P8mU4p2cAJYO+ME9fZfs8H2uqGNj13XUzoT6JzM8UwKBgQDUFZWcfmlgCM2BjU9c
8qhYjD2egT3qxWJLYSUTUZfdOGgB6lxTqnOhsy93xYmVInz6r9XEZsLVoQj/wcZk
p7y+MxjiWNcBcUmviwHee42fe6BQZHaYlAFtlAKNSiHumfq6AtXpZvkQZJWTSRyW
lI4LBEL6fSuqpk88EH9FXJbChwKBgQC3+F/1Qi3EoeohhWD+jMO0r8IblBd7jYbp
2zs17KQsCEyc1qyIaE+a8Ud8zUqsECKWBuSFsQ2qrR3jZW6DZOw8hmp1foYC+Jjr
C/BHyWsyYxrCoxpvSJMXCY6ulyFHjIZboopRVi/jgfowteMW6WyxvOMqVAqZtxRW
HyFbsa+/7wKBgQCGHRwd+SZjr01dZmHQcjaYwB5bNHlWE/nDlyvd2pQBNaE3zN8T
nU8/6tLSl50YLNYBpN22NBFzDEFnkj8F+bh2QlOzFuDnrZ8eHfZRnaoCNyg6jj0c
4UNB6v3uIPnyK3cM16wzy4Umo6SenfYxFsH4H3rHcg4B/OdQIVKKJzHC0wKBgQCj
QxhlX0WeqtJMzUE2pVVIlHF+Z/4u93ozLwts34USTosu5JRYublrl5QJfWY3LFqF
KbjDrEykmt1bYDijAn1jeSYg/xeOq2+JqB6klms7XBfzgyuCdrWSTDkDV7uA84SI
7cYySHpXPJH7iG7vdlevpCE0/0ApCgBSLW49IYMGoQKBgAxVRqAhLdA0RO+nTAC/
whOL5RGy5M2oXKfqNkzEt2k5og7xXY7ZoYTye5Byb3+wLpEJXW+V8FlfXk/u5ZI7
oFuZne+lYcCPMNDXdku6wKdf9gSnOSHOGMu8TvHcud4uIDYmFH5qabJL5GDoQi7Q
12XvK21e6GNOEaRRlTHz0qUB
-----END PRIVATE KEY-----
"""

    // Helper: convert PEM → X509Certificate
    fun loadCertificateFromPem(pem: String): java.security.cert.X509Certificate {
        val cleanPem = pem.replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replace("\\s+".toRegex(), "")
        val decoded = base64DecodeArray(cleanPem)
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(decoded.inputStream()) as java.security.cert.X509Certificate
    }

    // Helper: convert PEM → PrivateKey
    fun loadPrivateKeyFromPem(pem: String): java.security.PrivateKey {
        val cleanPem = pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s+".toRegex(), "")
        val decoded = base64DecodeArray(cleanPem)
        val keySpec = PKCS8EncodedKeySpec(decoded)
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }

    fun buildClientWithCert(): OkHttpClient {
        val cert = loadCertificateFromPem(CLIENT_CERT_PEM)
        val key = loadPrivateKeyFromPem(CLIENT_KEY_PEM)

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry("client", key, "".toCharArray(), arrayOf(cert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, "".toCharArray())

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, tmf.trustManagers, SecureRandom())

        val trustManager = tmf.trustManagers[0] as X509TrustManager

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .build()
    }


    fun queryApi(query: String, useAlternativeApi: Boolean): String {
        val encryptedQuery = CipherUtils.encrypt(query, key, iv)!!
        val appKeyHash = CipherUtils.md5(appKey)!!
        val newBody =
            """{"app_key":"$appKeyHash","verify":"${getVerify(encryptedQuery, appKey, key)}","encrypt_data":"$encryptedQuery"}"""
        val base64Body = base64Encode(newBody.toByteArray())

        val data = mapOf(
            "data" to base64Body,
            "appid" to "27",
            "platform" to "android",
            "version" to appVersionCode,
            "medium" to "Website",
            "token" to token
        )

        val url = if (useAlternativeApi) secondAPI else firstAPI

        val client = buildClientWithCert().newBuilder()
            .addInterceptor(UserAgentInterceptor())
            .build()
        val formBody = FormBody.Builder().apply {
            data.forEach { (k, v) -> add(k, v) }
        }.build()

        val request = Request.Builder()
            .url(url)
            .headers(headers.toHeaders())
            .post(formBody)
            .build()

        client.newCall(request).execute().use { resp ->
            return resp.body.string()
        }
    }


    inline fun <reified T : Any> queryApiParsed(query: String): T {
        return try {
            val json = queryApi(query, false)
            Gson().fromJson(json, T::class.java)
        } catch (_: Exception) {
            val jsonAlt = queryApi(query, true)
            Gson().fromJson(jsonAlt, T::class.java)
        }
    }


    fun getExpiryDate(): Long {
        // Current time + 12 hours
        return unixTime + 60 * 60 * 12
    }

    private data class PostJSON(
        @SerializedName("id") val id: Int? = null,
        @SerializedName("title") val title: String? = null,
        @SerializedName("banner_mini") val bannerMini: String? = null,
        @SerializedName("poster") val poster: String? = null,
        @SerializedName("poster_2") val poster2: String? = null,
        @SerializedName("box_type") val boxType: Int? = null,
        @SerializedName("imdb_rating") val imdbRating: String? = null,
        @SerializedName("season_episode") val seasonEpisode: String? = null,
        @SerializedName("update_title") val updateTitle: String? = null,
        @SerializedName("quality_tag") val qualityTag: String? = null,
        @SerializedName("3d") val is3D: Int? = null // Some movie items have "3d"
    )

    private data class ListJSON(
        @SerializedName("code") val code: Int? = null,
        @SerializedName("type") val type: String? = null,
        @SerializedName("name") val name: String? = null,
        @SerializedName("ismore") val isMore: Int? = null,
        @SerializedName("box_type") val boxType: Int? = null,
        @SerializedName("cache") val cache: Boolean? = null,
        @SerializedName("cache_key") val cacheKey: String? = null,
        @SerializedName("list") val list: ArrayList<PostJSON> = arrayListOf()
    )

    private data class DataJSON(
        @SerializedName("code") val code: Int? = null,
        @SerializedName("msg") val msg: String? = null,
        @SerializedName("data") val data: ArrayList<ListJSON> = arrayListOf()
    )

    // We do not want content scanners to notice this scraping going on so we've hidden all constants
    // The source has its origins in China so I added some extra security with banned words
    // Mayhaps a tiny bit unethical, but this source is just too good :)
    // If you are copying this code please use precautions so they do not change their api.

    // Free Tibet, The Tienanmen Square protests of 1989
    private val iv = base64Decode("d0VpcGhUbiE=")
    private val key = base64Decode("MTIzZDZjZWRmNjI2ZHk1NDIzM2FhMXc2")

    private val firstAPI = base64Decode("aHR0cHM6Ly9zaG93Ym94c3NsLnNoZWd1Lm5ldC9hcGkvYXBpX2NsaWVudC8=")

    // Another url because the first one sucks at searching
    // This one was revealed to me in a dream
    val secondAPI = base64Decode("aHR0cHM6Ly9zaG93Ym94YXBpc3NsLnN0c29zby5jb20vYXBpL2FwaV9jbGllbnQv")

    val thirdAPI = base64Decode("aHR0cHM6Ly93d3cuZmViYm94LmNvbQ==")

    val watchSomuchAPI = "https://watchsomuch.tv"
    val openSubAPI = "https://opensubtitles-v3.strem.io"

    private val appKey = base64Decode("bW92aWVib3g=")
    val appId = base64Decode("Y29tLnRkby5zaG93Ym94")
    private val appIdSecond = base64Decode("Y29tLnRkby5zaG93Ym94")
    private val appVersion = "11.7"
    private val appVersionCode = "131"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val hideNsfw = if (settingsForProvider.enableAdult) 0 else 1
        val data = queryApiParsed<DataJSON>(
            """{"childmode":"$hideNsfw","app_version":"$appVersion","appid":"$appIdSecond","module":"Home_list_type_v2","channel":"Website","page":"$page","lang":"en","type":"all","pagelimit":"20","expired_date":"${getExpiryDate()}","platform":"android"}
            """.trimIndent()
        )
        // Cut off the first row (featured)
        val pages = data.data.let { it.subList(minOf(it.size, 1), it.size) }
            .mapNotNull {
                var name = it.name
                if (name.isNullOrEmpty()) name = "Featured"
                val postList = it.list.mapNotNull second@{ post ->
                    val type = if (post.boxType == 1) TvType.Movie else TvType.TvSeries
                    val normalizedQuality = post.qualityTag?.let { if (it.contains("blu-ray", ignoreCase = true)) "Blueray" else it } ?: ""
                    newMovieSearchResponse(
                        name = post.title ?: return@second null,
                        url = LoadData(post.id ?: return@mapNotNull null, post.boxType).toJson(),
                        type = type,
                        fix = false
                    ) {
                        posterUrl = post.poster ?: post.poster2
                        quality = getQualityFromString(normalizedQuality)
                        this.score= Score.from10(post.imdbRating)
                    }
                }
                if (postList.isEmpty()) return@mapNotNull null
                HomePageList(name, postList)
            }
        return newHomePageResponse(pages, hasNext = !pages.any { it.list.isEmpty() })
    }
    private data class Data(
        @SerializedName("id") val id: Int? = null,
        @SerializedName("mid") val mid: Int? = null,
        @SerializedName("box_type") val boxType: Int? = null,
        @SerializedName("title") val title: String? = null,
        @SerializedName("poster_org") val posterOrg: String? = null,
        @SerializedName("poster") val poster: String? = null,
        @SerializedName("cats") val cats: String? = null,
        @SerializedName("year") val year: Int? = null,
        @SerializedName("imdb_rating") val imdbRating: String? = null,
        @SerializedName("quality_tag") val qualityTag: String? = null
    ) {
        fun toSearchResponse(api: MainAPI): MovieSearchResponse? {
            val actualBoxType = this.boxType ?: ResponseTypes.Movies.value
            return api.newMovieSearchResponse(
                this.title ?: "",
                LoadData(
                    this.id ?: this.mid ?: return null,
                    actualBoxType
                ).toJson(),
                ResponseTypes.getResponseType(actualBoxType).toTvType(),
                false
            ) {
                posterUrl = if (!this@Data.posterOrg.isNullOrEmpty()) this@Data.posterOrg else this@Data.poster
                year = this@Data.year ?: 0
                quality = getQualityFromString(this@Data.qualityTag?.replace("-", "") ?: "")
            }
        }
    }

    private data class MainData(
        @SerializedName("code") val code: Int,
        @SerializedName("msg") val msg: String,
        @SerializedName("data") val data: List<Data>
    )


    override suspend fun search(query: String): List<SearchResponse> {
        val hideNsfw = if (settingsForProvider.enableAdult) 0 else 1
        val apiQuery =
            // Originally 8 pagelimit
            """{"childmode":"$hideNsfw","app_version":"$appVersion","module":"Search3","channel":"Website","page":"1","lang":"en","type":"all","keyword":"$query","pagelimit":"15","expired_date":"${getExpiryDate()}","platform":"android","appid":"$appId"}"""
        val searchResponse = queryApiParsed<MainData>(apiQuery).data.mapNotNull {
            it.toSearchResponse(this)
        }
        return searchResponse
    }

    private data class LoadData(
        val id: Int,
        val box_type: Int?
    )

    private data class MovieData(
        @SerializedName("id") val id: Int? = null,
        @SerializedName("title") var title: String? = null,
        @SerializedName("director") val director: String? = null,
        @SerializedName("writer") val writer: String? = null,
        @SerializedName("actors") val actors: String? = null,
        @SerializedName("runtime") val runtime: Int? = null,
        @SerializedName("poster") val poster: String? = null,
        @SerializedName("description") val description: String? = null,
        @SerializedName("cats") val cats: String? = null,
        @SerializedName("year") val year: Int? = null,
        @SerializedName("imdb_id") val imdbId: String? = null,
        @SerializedName("imdb_rating") val imdbRating: String? = null,
        @SerializedName("trailer") val trailer: String? = null,
        @SerializedName("released") val released: String? = null,
        @SerializedName("content_rating") val contentRating: String? = null,
        @SerializedName("tmdb_id") val tmdbId: Int? = null,
        @SerializedName("tomato_meter") val tomatoMeter: Int? = null,
        @SerializedName("poster_org") val posterOrg: String? = null,
        @SerializedName("trailer_url") val trailerUrl: String? = null,
        @SerializedName("imdb_link") val imdbLink: String? = null,
        @SerializedName("box_type") val boxType: Int? = null,
        @SerializedName("recommend") val recommend: List<Data> = listOf()
    )

    private data class MovieDataProp(
        @SerializedName("data") val data: MovieData? = MovieData()
    )


    private data class SeriesDataProp(
        @SerializedName("code") val code: Int? = null,
        @SerializedName("msg") val msg: String? = null,
        @SerializedName("data") val data: SeriesData? = SeriesData()
    )

    private data class SeriesSeasonProp(
        @JsonProperty("code") val code: Int? = null,
        @JsonProperty("msg") val msg: String? = null,
        @JsonProperty("data") val data: ArrayList<SeriesEpisode>? = arrayListOf()
    )

    private data class SeriesEpisode(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("tid") val tid: Int? = null,
        @JsonProperty("mb_id") val mbId: Int? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("imdb_id_status") val imdbIdStatus: Int? = null,
        @JsonProperty("srt_status") val srtStatus: Int? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("state") val state: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("thumbs") val thumbs: String? = null,
        @JsonProperty("thumbs_bak") val thumbsBak: String? = null,
        @JsonProperty("thumbs_original") val thumbsOriginal: String? = null,
        @JsonProperty("poster_imdb") val posterImdb: Int? = null,
        @JsonProperty("synopsis") val synopsis: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("view") val view: Int? = null,
        @JsonProperty("download") val download: Int? = null,
        @JsonProperty("source_file") val sourceFile: Int? = null,
        @JsonProperty("code_file") val codeFile: Int? = null,
        @JsonProperty("add_time") val addTime: Int? = null,
        @JsonProperty("update_time") val updateTime: Int? = null,
        @JsonProperty("released") val released: String? = null,
        @JsonProperty("released_timestamp") val releasedTimestamp: Long? = null,
        @JsonProperty("audio_lang") val audioLang: String? = null,
        @JsonProperty("quality_tag") val qualityTag: String? = null,
        @JsonProperty("3d") val _3d: Int? = null,
        @JsonProperty("remark") val remark: String? = null,
        @JsonProperty("pending") val pending: String? = null,
        @JsonProperty("imdb_rating") val imdbRating: String? = null,
        @JsonProperty("display") val display: Int? = null,
        @JsonProperty("sync") val sync: Int? = null,
        @JsonProperty("tomato_meter") val tomatoMeter: Int? = null,
        @JsonProperty("tomato_meter_count") val tomatoMeterCount: Int? = null,
        @JsonProperty("tomato_audience") val tomatoAudience: Int? = null,
        @JsonProperty("tomato_audience_count") val tomatoAudienceCount: Int? = null,
        @JsonProperty("thumbs_min") val thumbsMin: String? = null,
        @JsonProperty("thumbs_org") val thumbsOrg: String? = null,
        @JsonProperty("imdb_link") val imdbLink: String? = null,
    )

    private data class SeriesLanguage(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("lang") val lang: String? = null
    )

    private data class SeriesData(
        @SerializedName("id") val id: Int? = null,
        @SerializedName("mb_id") val mbId: Int? = null,
        @SerializedName("title") val title: String? = null,
        @SerializedName("display") val display: Int? = null,
        @SerializedName("state") val state: Int? = null,
        @SerializedName("vip_only") val vipOnly: Int? = null,
        @SerializedName("code_file") val codeFile: Int? = null,
        @SerializedName("director") val director: String? = null,
        @SerializedName("writer") val writer: String? = null,
        @SerializedName("actors") val actors: String? = null,
        @SerializedName("add_time") val addTime: Int? = null,
        @SerializedName("poster") val poster: String? = null,
        @SerializedName("poster_imdb") val posterImdb: Int? = null,
        @SerializedName("banner_mini") val bannerMini: String? = null,
        @SerializedName("description") val description: String? = null,
        @SerializedName("imdb_id") val imdbId: String? = null,
        @SerializedName("cats") val cats: String? = null,
        @SerializedName("year") val year: Int? = null,
        @SerializedName("collect") val collect: Int? = null,
        @SerializedName("view") val view: Int? = null,
        @SerializedName("download") val download: Int? = null,
        @SerializedName("update_time") val updateTime: String? = null,
        @SerializedName("released") val released: String? = null,
        @SerializedName("released_timestamp") val releasedTimestamp: Int? = null,
        @SerializedName("episode_released") val episodeReleased: String? = null,
        @SerializedName("episode_released_timestamp") val episodeReleasedTimestamp: Int? = null,
        @SerializedName("max_season") val maxSeason: Int? = null,
        @SerializedName("max_episode") val maxEpisode: Int? = null,
        @SerializedName("remark") val remark: String? = null,
        @SerializedName("imdb_rating") val imdbRating: String? = null,
        @SerializedName("content_rating") val contentRating: String? = null,
        @SerializedName("tmdb_id") val tmdbId: Int? = null,
        @SerializedName("tomato_url") val tomatoUrl: String? = null,
        @SerializedName("tomato_meter") val tomatoMeter: Int? = null,
        @SerializedName("tomato_meter_count") val tomatoMeterCount: Int? = null,
        @SerializedName("tomato_meter_state") val tomatoMeterState: String? = null,
        @SerializedName("reelgood_url") val reelgoodUrl: String? = null,
        @SerializedName("audience_score") val audienceScore: Int? = null,
        @SerializedName("audience_score_count") val audienceScoreCount: Int? = null,
        @SerializedName("no_tomato_url") val noTomatoUrl: Int? = null,
        @SerializedName("order_year") val orderYear: Int? = null,
        @SerializedName("episodate_id") val episodateId: String? = null,
        @SerializedName("weights_day") val weightsDay: Double? = null,
        @SerializedName("poster_min") val posterMin: String? = null,
        @SerializedName("poster_org") val posterOrg: String? = null,
        @SerializedName("banner_mini_min") val bannerMiniMin: String? = null,
        @SerializedName("banner_mini_org") val bannerMiniOrg: String? = null,
        @SerializedName("trailer_url") val trailerUrl: String? = null,
        @SerializedName("years") val years: List<Int> = listOf(),
        @SerializedName("season") val season: List<Int> = listOf(),
        @SerializedName("history") val history: List<String> = listOf(),
        @SerializedName("imdb_link") val imdbLink: String? = null,
        @SerializedName("episode") val episode: List<SeriesEpisode> = listOf(),
        @SerializedName("language") val language: List<SeriesLanguage> = listOf(),
        @SerializedName("box_type") val boxType: Int? = null,
        @SerializedName("year_year") val yearYear: String? = null,
        @SerializedName("season_episode") val seasonEpisode: String? = null
    )


    override suspend fun load(url: String): LoadResponse {
        val loadData = parseJson<LoadData>(url)
        val isMovie = loadData.box_type == ResponseTypes.Movies.value
        val hideNsfw = if (settingsForProvider.enableAdult) 0 else 1
        if (isMovie) { // 1 = Movie
            val apiQuery =
                """{"childmode":"$hideNsfw","uid":"","app_version":"$appVersion","appid":"$appIdSecond","module":"Movie_detail","channel":"Website","mid":"${loadData.id}","lang":"en","expired_date":"${getExpiryDate()}","platform":"android","oss":"","group":""}"""
            val data = (queryApiParsed<MovieDataProp>(apiQuery)).data
                ?: throw RuntimeException("API error")
            val responseData = if (!data.imdbId.isNullOrEmpty()) {
                val jsonResponse = app.get("$cinemeta_url/movie/${data.imdbId}.json").text
                if (jsonResponse.isNotEmpty() && jsonResponse.startsWith("{")) {
                    Gson().fromJson(jsonResponse, ResponseData::class.java)
                } else null
            } else null

            val cast: List<String> = responseData?.meta?.cast ?: emptyList()
            val background: String? = responseData?.meta?.background
            val genre: List<String>? = responseData?.meta?.genre

            return newMovieLoadResponse(
                data.title ?: "",
                url,
                TvType.Movie,
                LinkData(
                    data.id ?: throw RuntimeException("No movie ID"),
                    ResponseTypes.Movies.value,
                    null,
                    null,
                    data.id,
                    data.imdbId
                ),
            ) {
                this.recommendations = data.recommend.mapNotNull { it.toSearchResponse(this@Superstream) }
                this.posterUrl = data.posterOrg ?: data.poster
                this.backgroundPosterUrl = background ?: data.posterOrg ?: data.poster
                        this.year = data.year
                addActors(cast)
                this.plot = data.description
                this.tags = genre ?: data.cats?.split(",")?.map { it.capitalize() }
                this.score = Score.from10(data.imdbRating)
                addTrailer(data.trailerUrl)
                this.addImdbId(data.imdbId)
            }
        } else { // 2 Series
            val apiQuery =
                """{"childmode":"$hideNsfw","uid":"","app_version":"$appVersion","appid":"$appIdSecond","module":"TV_detail_1","display_all":"1","channel":"Website","lang":"en","expired_date":"${getExpiryDate()}","platform":"android","tid":"${loadData.id}"}"""
            val data = queryApiParsed<SeriesDataProp>(apiQuery).data
                ?: throw RuntimeException("API error")
            val responseData = if (!data.imdbId.isNullOrEmpty()) {
                val jsonResponse = app.get("$cinemeta_url/series/${data.imdbId}.json").text
                if (jsonResponse.isNotEmpty() && jsonResponse.startsWith("{")) {
                    Gson().fromJson(jsonResponse, ResponseData::class.java)
                } else null
            } else null

            val cast: List<String> = responseData?.meta?.cast ?: emptyList()
            val background: String? = responseData?.meta?.background
            val genre: List<String>? = responseData?.meta?.genre
            val allEpisodes = mutableListOf<Episode>()
            data.season.forEach { seasonNumber ->
                val seasonApiQuery =
                    """{"childmode":"$hideNsfw","uid":"","app_version":"$appVersion","appid":"$appIdSecond","module":"TV_episode","display_all":"1","season":"$seasonNumber","channel":"Website","lang":"en","expired_date":"${getExpiryDate()}","platform":"android","tid":"${loadData.id}"}"""

                val seasonData = queryApiParsed<SeriesSeasonProp>(seasonApiQuery).data
                    ?: return@forEach

                seasonData.forEach { ep ->
                    allEpisodes.add(
                        newEpisode(
                            LinkData(ep.tid ?: ep.id ?: throw RuntimeException("No Series ID"),
                            ResponseTypes.Series.value,
                            ep.season,
                            ep.episode,
                            data.id,
                            data.imdbId
                        ).toJson()
                        ) {
                            name = ep.title
                            season = ep.season
                            episode = ep.episode
                            posterUrl = ep.thumbsOriginal ?: ep.thumbsBak ?: ep.thumbsMin ?: ep.thumbs ?: ep.thumbsOrg
                            description = ep.synopsis
                            date = ep.releasedTimestamp
                            runTime = ep.runtime
                            score=Score.from10(ep.imdbRating)
                        }
                    )
                }
            }
            return newTvSeriesLoadResponse(
                data.title ?: "",
                url,
                TvType.TvSeries,
                allEpisodes
            ) {
                year = data.year
                plot = data.description
                addActors(cast)
                this.posterUrl = data.posterOrg ?: data.poster
                backgroundPosterUrl = background ?: data.posterOrg ?: data.poster
                score = Score.from10(data.imdbRating)
                tags = genre ?: data.cats?.split(",")?.map { it.capitalize() }
                addImdbId(data.imdbId)
                addImdbUrl(data.imdbLink)
            }
        }
    }

    private data class LinkData(
        val id: Int,
        val type: Int,
        val season: Int?,
        val episode: Int?,
        val mediaId: Int?,
        val imdbId: String?,
    )

    data class LinkDataProp(
        @SerializedName("code") val code: Int? = null,
        @SerializedName("msg") val msg: String? = null,
        @SerializedName("data") val data: ParsedLinkData? = ParsedLinkData()
    )

    data class LinkList(
        @SerializedName("path") val path: String? = null,
        @SerializedName("quality") val quality: String? = null,
        @SerializedName("real_quality") val realQuality: String? = null,
        @SerializedName("format") val format: String? = null,
        @SerializedName("size") val size: String? = null,
        @SerializedName("size_bytes") val sizeBytes: Long? = null,
        @SerializedName("count") val count: Int? = null,
        @SerializedName("dateline") val dateline: Long? = null,
        @SerializedName("fid") val fid: Int? = null,
        @SerializedName("mmfid") val mmfid: Int? = null,
        @SerializedName("h265") val h265: Int? = null,
        @SerializedName("hdr") val hdr: Int? = null,
        @SerializedName("filename") val filename: String? = null,
        @SerializedName("original") val original: Int? = null,
        @SerializedName("colorbit") val colorbit: Int? = null,
        @SerializedName("success") val success: Int? = null,
        @SerializedName("timeout") val timeout: Int? = null,
        @SerializedName("vip_link") val vipLink: Int? = null,
        @SerializedName("fps") val fps: Int? = null,
        @SerializedName("bitstream") val bitstream: String? = null,
        @SerializedName("width") val width: Int? = null,
        @SerializedName("height") val height: Int? = null
    )

    data class ParsedLinkData(
        @JsonProperty("seconds") val seconds: Int? = null,
        @JsonProperty("quality") val quality: ArrayList<String> = arrayListOf(),
        @JsonProperty("list") val list: ArrayList<LinkList> = arrayListOf()
    )

    data class SubtitleDataProp(
        @JsonProperty("code") val code: Int? = null,
        @JsonProperty("msg") val msg: String? = null,
        @JsonProperty("data") val data: PrivateSubtitleData? = PrivateSubtitleData()
    )

    data class Subtitles(
        @JsonProperty("sid") val sid: Int? = null,
        @JsonProperty("mid") val mid: String? = null,
        @JsonProperty("file_path") val filePath: String? = null,
        @JsonProperty("lang") val lang: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("delay") val delay: Int? = null,
        @JsonProperty("point") val point: String? = null,
        @JsonProperty("order") val order: Int? = null,
        @JsonProperty("support_total") val support_total: Int? = null,
        @JsonProperty("admin_order") val adminOrder: Int? = null,
        @JsonProperty("myselect") val myselect: Int? = null,
        @JsonProperty("add_time") val addTime: Long? = null,
        @JsonProperty("count") val count: Int? = null
    )

    data class SubtitleList(
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("subtitles") val subtitles: ArrayList<Subtitles> = arrayListOf()
    )

    data class PrivateSubtitleData(
        @JsonProperty("select") val select: ArrayList<String> = arrayListOf(),
        @JsonProperty("list") val list: ArrayList<SubtitleList> = arrayListOf()
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsed = parseJson<LinkData>(data)

        runAllAsync(
            {
                invokeExternalSource(
                    parsed.mediaId,
                    parsed.type,
                    parsed.season,
                    parsed.episode,
                    uitoken,
                    callback
                )
            },
            {
                invokeInternalSource(
                    parsed.id,
                    parsed.type,
                    parsed.season,
                    parsed.episode,
                    Supertoken,
                    subtitleCallback,
                    callback
                )
            },
            {
                invokeExternalM3u8Source(
                    parsed.mediaId,
                    parsed.type,
                    parsed.season,
                    parsed.episode,
                    uitoken,
                    callback
                )
            },
            {
                invokeOpenSubs(
                    parsed.imdbId,
                    parsed.season,
                    parsed.episode,
                    subtitleCallback
                )
            },
            {
                invokeWatchsomuch(
                    parsed.imdbId,
                    parsed.season,
                    parsed.episode,
                    subtitleCallback
                )
            }
        )

        return true
    }

    data class ExternalResponse(
        @JsonProperty("data") val data: Data? = null,
    ) {
        data class Data(
            @JsonProperty("link") val link: String? = null,
            @JsonProperty("share_link") val shareLink: String? = null, // add this
            @JsonProperty("file_list") val file_list: ArrayList<FileList>? = arrayListOf(),
        ) {
            data class FileList(
                @JsonProperty("fid") val fid: Long? = null,
                @JsonProperty("file_name") val file_name: String? = null,
                @JsonProperty("oss_fid") val oss_fid: Long? = null,
            )
        }
    }

    data class WatchsomuchTorrents(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("movieId") val movieId: Int? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
    )

    data class WatchsomuchMovies(
        @JsonProperty("torrents") val torrents: ArrayList<WatchsomuchTorrents>? = arrayListOf(),
    )

    data class WatchsomuchResponses(
        @JsonProperty("movie") val movie: WatchsomuchMovies? = null,
    )

    data class WatchsomuchSubtitles(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class WatchsomuchSubResponses(
        @JsonProperty("subtitles") val subtitles: ArrayList<WatchsomuchSubtitles>? = arrayListOf(),
    )

    data class OsSubtitles(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("lang") val lang: String? = null,
    )

    data class OsResult(
        @JsonProperty("subtitles") val subtitles: ArrayList<OsSubtitles>? = arrayListOf(),
    )

}

