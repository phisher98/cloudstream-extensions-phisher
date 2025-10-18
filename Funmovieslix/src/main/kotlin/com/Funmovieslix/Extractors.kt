package com.Funmovieslix

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject


class Ryderjet : VidhideExtractor() {
    override var mainUrl = "https://ryderjet.com"
}

class Dhtpre : VidhideExtractor() {
    override var mainUrl = "https://dhtpre.com"
}


class Vidhideplus : VidhideExtractor() {
    override var mainUrl = "https://vidhideplus.com"
}


//Thanks to https://github.com/VVytai/jdownloader_mirror/blob/main/svn_trunk/src/jd/plugins/hoster/LixstreamCom.java
class VideyV2 : ExtractorApi() {
    override var name = "Videy"
    override var mainUrl = "https://videy.tv"
    override val requiresReferer = false

    private val apiBase = "https://api.lixstreamingcaio.com/v2"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val sid = url.substringAfterLast("/")
        Log.d("Phisher", "sid=$sid")

        val infoRes = app.post(
            "$apiBase/s/home/resources/$sid",
            data = mapOf(),
            headers = mapOf("Content-Type" to "application/json")
        ).text

        val info = runCatching { JSONObject(infoRes) }.getOrNull() ?: return
        val suid = info.optString("suid") ?: return
        val files = info.optJSONArray("files") ?: return
        if (files.length() == 0) return
        val file = files.optJSONObject(0) ?: return
        val fid = file.optString("id") ?: return
        val assetRes = app.get("$apiBase/s/assets/f?id=$fid&uid=$suid").text
        val asset = runCatching { JSONObject(assetRes) }.getOrNull() ?: return
        val encryptedUrl = asset.optString("url")
        if (encryptedUrl.isNullOrEmpty()) {
            Log.e("Error:", "No encrypted url found")
            return
        }
        val key = "GNgN1lHXIFCQd8hSEZIeqozKInQTFNXj".toByteArray(Charsets.UTF_8)
        val iv = "2Xk4dLo38c9Z2Q2a".toByteArray(Charsets.UTF_8)
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.IvParameterSpec(iv)
        )
        val decrypted = runCatching {
            val decoded = base64DecodeArray(encryptedUrl)
            String(cipher.doFinal(decoded), Charsets.UTF_8)
        }.getOrNull() ?: return
        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                decrypted,
                if (decrypted.endsWith(".mp4")) ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
            )
            {
                this.referer = url
                this.quality = Qualities.P1080.value
            }
        )
    }
}
