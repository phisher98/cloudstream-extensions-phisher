package com.phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor


class ElevenmoviesProvider : MediaProvider() {
    override val name = "Elevenmovies"
    override val domain = "https://111movies.com"
    override val categories = listOf(UltimaUtils.Category.MEDIA)

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadContent(
        url: String,
        data: UltimaUtils.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val Elevenmovies =domain
        val apiurl = if (data.season == null) {
            "$domain/movie/${data.tmdbId}"
        } else {
            "$domain/tv/${data.tmdbId}/${data.season}/${data.episode}"
        }

        val encodedToken = app.get(apiurl).documentLarge.selectFirst("script[type=application/json]")
            ?.data()
            ?.substringAfter("{\"data\":\"")
            ?.substringBefore("\",")
        if (encodedToken == null) return
        val jsonString = app.get("https://raw.githubusercontent.com/phisher98/TVVVV/main/output.json").text
        val gson = Gson()

        val json: Elevenmoviesjson? = try {
            gson.fromJson(jsonString, Elevenmoviesjson::class.java)
        } catch (e: JsonSyntaxException) {
            e.printStackTrace()
            null
        }
        requireNotNull(json) { "Failed to parse Elevenmovies JSON" }
        val token = elevenMoviesTokenV2(encodedToken)

        val staticPath = json.staticPath
        val apiServerUrl = "$Elevenmovies/$staticPath/$token/sr"
        val headers = mapOf(
            "Referer" to Elevenmovies,
            "User-Agent" to USER_AGENT,
            "Content-Type" to json.contentTypes,
            "X-Requested-With" to "XMLHttpRequest"
        )

        val responseString = try {
            if (json.httpMethod.contains("GET")) {
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
        val serverList: List<ElevenmoviesServerEntry> = Gson().fromJson(responseString, listType)

        for (entry in serverList) {
            val serverToken = entry.data
            val serverName = entry.name

            val streamApiUrl = "$Elevenmovies/$staticPath/$serverToken"
            val streamResponseString = if (json.httpMethod == "GET") {
                app.get(streamApiUrl, headers = headers).body.string()
            } else {
                val postHeaders = mapOf(
                    "Referer" to Elevenmovies,
                    "Content-Type" to "application/vnd.api+json",
                    "X-CSRF-Token" to json.csrfToken,
                    "X-Requested-With" to "XMLHttpRequest"
                )
                val mediaType = "application/vnd.api+json".toMediaType()
                val requestBody = "".toRequestBody(mediaType)
                app.post(
                    streamApiUrl,
                    headers = postHeaders,
                    requestBody = requestBody
                ).body.string()
            }
            val streamRes =
                Gson().fromJson(streamResponseString, ElevenmoviesStreamResponse::class.java)
                    ?: continue
            val videoUrl = streamRes.url ?: continue

            M3u8Helper.generateM3u8(
                "Eleven Movies $serverName",
                videoUrl,
                ""
            ).forEach(callback)

            streamRes.tracks?.forEach { sub ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        sub.label ?: return@forEach,
                        sub.file ?: return@forEach
                    )
                )
            }
        }
    }

    // #region - Encryption and Decryption handlers
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun elevenMoviesTokenV2(rawData: String): String {
        val jsonUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/main/output.json"
        val jsonString = app.get(jsonUrl).text
        val gson = Gson()
        val json: Elevenmoviesjson = gson.fromJson(jsonString, Elevenmoviesjson::class.java)
        val keyHex = json.keyHex
        val ivHex = json.ivHex
        val aesKey = SecretKeySpec(keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), "AES")
        val aesIv = IvParameterSpec(ivHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())

        // AES encrypt
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)
        val aesEncrypted = cipher.doFinal(rawData.toByteArray())
        val aesHex = aesEncrypted.joinToString("") { "%02x".format(it) }

        // XOR operation
        val xorKeyHex = json.xorKey
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

    // #endregion - Encryption and Decryption handlers

    // #region - Data classes

    data class ElevenmoviesServerEntry(
        val name: String,
        val description: String,
        val image: String,
        val data: String,
    )

    data class ElevenmoviesStreamResponse(
        val url: String?,
        val tracks: List<ElevenmoviesSubtitle>?
    )

    data class ElevenmoviesSubtitle(
        val label: String?,
        val file: String?
    )

    data class Elevenmoviesjson(
        val src: String,
        val dst: String,

        @SerializedName("static_path")
        val staticPath: String,

        @SerializedName("http_method")
        val httpMethod: String,

        @SerializedName("key_hex")
        val keyHex: String,

        @SerializedName("iv_hex")
        val ivHex: String,

        @SerializedName("xor_key")
        val xorKey: String,

        @SerializedName("csrf_token")
        val csrfToken: String,

        @SerializedName("content_types")
        val contentTypes: String
    )
    // #endregion - Data classes

}