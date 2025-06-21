package com.phisher98

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.extractors.Rabbitstream
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import org.json.JSONArray
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.toByteArray
import kotlin.emptyArray
import kotlin.math.roundToInt

class Videostr : ExtractorApi() {
    override val name = "Videostr"
    override val mainUrl = "https://videostr.net"
    override val requiresReferer = false

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to mainUrl,
            "User-Agent" to USER_AGENT
        )

        val id = url.substringAfterLast("/").substringBefore("?")
        val apiUrl = "$mainUrl/embed-1/v2/e-1/getSources?id=$id"

        val json = app.get(apiUrl, headers = headers).text
        val response = Gson().fromJson(json, MediaData::class.java)

        val key = app.get("https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json")
            .parsedSafe<Megakey>()?.vidstr ?: return

        val decryptedJson = decryptOpenSSL(response.sources, key)
        val m3u8Url = parseSourceJson(decryptedJson).firstOrNull()?.file ?: return

        val m3u8Headers = mapOf("Referer" to mainUrl, "Origin" to mainUrl)
        generateM3u8(name, m3u8Url, mainUrl, headers = m3u8Headers).forEach(callback)

        response.tracks
            .filter { it.kind in listOf("captions", "subtitles") }
            .forEach { track ->
                subtitleCallback(SubtitleFile(track.label, track.file))
            }
    }

    data class MediaData(
        val sources: String,
        val tracks: List<Track>,
        val encrypted: Boolean,
        @SerializedName("_f") val f: String,
        val server: Int
    )

    data class Track(
        val file: String,
        val label: String,
        val kind: String,
        @SerializedName("default") val isDefault: Boolean = false
    )

    data class Megakey(val mega: String, val rabbit: String,val vidstr: String)
    data class Source2(val file: String, val type: String)

    private fun parseSourceJson(json: String): List<Source2> = runCatching {
        val jsonArray = JSONArray(json)
        List(jsonArray.length()) {
            val obj = jsonArray.getJSONObject(it)
            Source2(obj.getString("file"), obj.getString("type"))
        }
    }.getOrElse {
        Log.e("parseSourceJson", "Failed to parse JSON: ${it.message}")
        emptyList()
    }

    private fun opensslKeyIv(password: ByteArray, salt: ByteArray, keyLen: Int = 32, ivLen: Int = 16): Pair<ByteArray, ByteArray> {
        var d = ByteArray(0)
        var d_i = ByteArray(0)
        while (d.size < keyLen + ivLen) {
            d_i = MessageDigest.getInstance("MD5").digest(d_i + password + salt)
            d += d_i
        }
        return d.copyOfRange(0, keyLen) to d.copyOfRange(keyLen, keyLen + ivLen)
    }

    @SuppressLint("NewApi")
    private fun decryptOpenSSL(encBase64: String, password: String): String {
        return runCatching {
            val data = Base64.getDecoder().decode(encBase64)
            require(data.copyOfRange(0, 8).contentEquals("Salted__".toByteArray()))
            val salt = data.copyOfRange(8, 16)
            val (key, iv) = opensslKeyIv(password.toByteArray(), salt)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            }

            String(cipher.doFinal(data.copyOfRange(16, data.size)))
        }.getOrElse {
            Log.e("decryptOpenSSL", "Decryption failed: ${it.message}")
            ""
        }
    }
}