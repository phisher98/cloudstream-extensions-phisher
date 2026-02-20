package com.phisher98

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class VidFlastProvider : MediaProvider() {
    override val name = "VidFast"
    override val domain = "https://www.vidfast.pro"
    override val categories = listOf(Category.MEDIA)

    override suspend fun loadContent(
        url: String,
        data: LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val apiurl = if (data.season == null) {
            "$domain/movie/${data.imdbId}"
        } else {
            "$domain/tv/${data.imdbId}/${data.season}/${data.episode}"
        }

        val regexData = app.get(apiurl).text
        val regex = Regex("""\\"en\\":\\"(.*?)\\""")

        val rawData = regex.find(regexData)?.groupValues?.getOrNull(1)
            ?: return println("❌ No match found.")

        val keyHex = "13346e2c05211f72e46a465e953fd5410826715e28d927f92ea5f4daee985c8b"
        val ivHex = "b83bcd42b90f5364e9b95c398264bca4"
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

        val xorKey = "3fc3e051ddc9dd8a74".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val xorResult = ByteArray(encrypted.size) { i ->
            (encrypted[i].toInt() xor xorKey[i % xorKey.size].toInt()).toByte()
        }
        val headers = mapOf(
            "Accept" to "*/*",
            "Referer" to domain,
            "x-session" to "",
            "X-Requested-With" to "XMLHttpRequest",
            "X-Csrf-Token" to "lVx7BVtk9c3SMNbF49PNvUc7GV5zzdem"
        )

        val encodedFinal = customBase64EncodeVidfast(xorResult)
        val staticPath = "rebivol/ad/w/2c7998b18129848378021254f87db35df8f562b2/2cf30a7c/APA91nNHHa3xbnvasl8ciswLATkt2fIiVFciF5RLarK4oR7nrTpEDSBjO_kRoBJD730BWfo6bQZIpxCr-PAlSGc8GAAxueegNH5gNzrcqhPDliciuUDv0GTqb_2t1ik9pIAXpVaZ8inm6ey56Qf44wrOOPUfZYlkKuKs18mNKqBluBYTB5lBXWF/775d49bf3b9b4d082f5156cd9f36e21d42014547cd9282b1fe62ccbe3d09f66b/1000094661747536"
        val apiServersUrl = "https://vidfast.pro/$staticPath/k33a7dwPZst1/$encodedFinal"
        val gson = Gson()
        val jsonMedia = "{}".toRequestBody("application/json".toMediaTypeOrNull())
        val responseBody = app.post(apiServersUrl, headers = headers, requestBody = jsonMedia).body.string()
        val type = object : TypeToken<List<VidfastServerData>>() {}.type
        val apiResponse: List<VidfastServerData>? = gson.fromJson(responseBody, type)

        if (apiResponse != null) {
            for (item in apiResponse) {
                val serverName = item.name
                val serverData = item.data ?: continue
                val apiStream = "https://vidfast.pro/$staticPath/p6PWA5s/$serverData"

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
                            M3u8Helper.generateM3u8("Vidfast [$serverName]", streamUrl, domain, headers = m3u8headers)
                                .forEach(callback)
                        } else {
                            callback.invoke(
                                newExtractorLink(
                                    "Vidfast",
                                    "Vidfast D [$serverName]",
                                    streamUrl,
                                    INFER_TYPE
                                ) {
                                    referer = domain
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


    private fun customBase64EncodeVidfast(input: ByteArray): String {
        val sourceChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        val targetChars = "Ju9Egn27FZNe-kaMUtOBAmf0qp3xDYlTX6PhiL5SRjzQIsHvoVw_WC4dGc1Ky8rb"

        // Standard Base64 URL-safe encode, no padding or wrap
        val base64 = android.util.Base64.encodeToString(
            input,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )

        // Translate characters to custom charset
        val translationMap = sourceChars.zip(targetChars).toMap()
        return base64.map { translationMap[it] ?: it }.joinToString("")
    }


    // #region - Data classes

    data class VidfastServerData(
        val name: String,
        val description: String,
        val image: String,
        val data: String?
    )

    // #endregion - Data classes

}