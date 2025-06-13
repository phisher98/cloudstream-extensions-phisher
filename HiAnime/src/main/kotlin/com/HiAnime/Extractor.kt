package com.HiAnime

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.json.JSONArray
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Megacloud : ExtractorApi() {
    override val name = "Megacloud"
    override val mainUrl = "https://megacloud.blog"
    override val requiresReferer = false

    @SuppressLint("NewApi")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfterLast("/").substringBefore("?")
        val apiUrl = "$mainUrl/embed-2/v2/e-1/getSources?id=$id"
        val response = app.get(apiUrl, referer = url).parsedSafe<MegacloudResponse>() ?: return
        response.sources.let { encoded ->
            val key = app.get("https://raw.githubusercontent.com/superbillgalaxy/megacloud-keys/refs/heads/main/api.json")
                .parsedSafe<Megakey>()?.megacloud
            val decoded = key?.let { decryptOpenSSL(encoded, it) }
            val m3u8 = decoded?.let {
                val sourceList = parseSourceJson(it)
                sourceList.firstOrNull()?.file
            }
            if (m3u8 != null) {
                val m3u8headers = mapOf(
                    "Referer" to "https://megacloud.club/",
                    "Origin" to "https://megacloud.club/"
                )

                M3u8Helper.generateM3u8(
                    name,
                    m3u8,
                    mainUrl,
                    headers = m3u8headers
                ).forEach(callback)

            }
        }


        response.tracks.forEach { track ->
            if (track.kind == "captions" || track.kind == "subtitles") {
                subtitleCallback(
                    SubtitleFile(
                        track.label,
                        track.file
                    )
                )
            }
        }
    }

    data class MegacloudResponse(
        val sources: String,
        val tracks: List< MegacloudTrack>,
        val encrypted: Boolean,
        val intro:  MegacloudIntro,
        val outro:  MegacloudOutro,
        val server: Long,
    )

    data class MegacloudTrack(
        val file: String,
        val label: String,
        val kind: String,
        val default: Boolean?,
    )

    data class MegacloudIntro(
        val start: Long,
        val end: Long,
    )

    data class  MegacloudOutro(
        val start: Long,
        val end: Long,
    )

    data class Megakey(
        val megacloud: String,
        val modifiedAt: String,
        val rabbitstream: String,
    )

    data class Source2(
        val file: String,
        val type: String,
    )

    private fun parseSourceJson(json: String): List<Source2> {
        val list = mutableListOf<Source2>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val file = obj.getString("file")
                val type = obj.getString("type")
                list.add(Source2(file, type))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun opensslKeyIv(password: ByteArray, salt: ByteArray, keyLen: Int = 32, ivLen: Int = 16): Pair<ByteArray, ByteArray> {
        var d = ByteArray(0)
        var d_i = ByteArray(0)
        while (d.size < keyLen + ivLen) {
            val md = MessageDigest.getInstance("MD5")
            d_i = md.digest(d_i + password + salt)
            d += d_i
        }
        return Pair(d.copyOfRange(0, keyLen), d.copyOfRange(keyLen, keyLen + ivLen))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun decryptOpenSSL(encBase64: String, password: String): String {
        try {
            val data = java.util.Base64.getDecoder().decode(encBase64)
            require(data.copyOfRange(0, 8).contentEquals("Salted__".toByteArray()))
            val salt = data.copyOfRange(8, 16)
            val (key, iv) = opensslKeyIv(password.toByteArray(), salt)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decrypted = cipher.doFinal(data.copyOfRange(16, data.size))
            return String(decrypted)
        } catch (e: Exception) {
            Log.e("DecryptOpenSSL", "Decryption failed: ${e.message}")
            return "Decryption Error"
        }
    }

}