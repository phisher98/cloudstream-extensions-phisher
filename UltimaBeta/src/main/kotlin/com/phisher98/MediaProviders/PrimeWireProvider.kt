package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class PrimeWireProvider : MediaProvider() {
    override val name = "PrimeWireProvider"
    override val domain = "https://www.primewire.tf"
    override val categories = listOf(UltimaUtils.Category.MEDIA)

    override suspend fun loadContent(
        url: String,
        data: UltimaUtils.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val apiurl = if (data.season == null) {
            "$domain/embed/movie?imdb=${data.imdbId}"
        } else {
            "$domain/embed/tv?imdb=${data.imdbId}&season=${data.season}&episode=${data.episode}"
        }

        val doc = app.get(apiurl, timeout = 10).document
        val userData = doc.select("#user-data")
        val decryptedLinks = decryptLinks(userData.attr("v"))
        for (link in decryptedLinks) {
            val href = "$domain/links/gos/$link"
            val token= app.get("https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Primetoken.txt").text
            val oUrl = app.get(href, timeout = 10)
            val iframeurl= app.get("${oUrl.url.replace("/gos/","/go/")}?token=$token").parsedSafe<Root>()?.link
            if (iframeurl != null) {
                loadSourceNameExtractor(
                    "Primewire ",
                    iframeurl,
                    "",
                    subtitleCallback,
                    callback,
                    quality = getQualityFromName("")
                )
            }
        }
    }

    private fun decryptLinks(data: String): List<String> {
        val key = data.substring(data.length - 10)
        val ct = data.substring(0, data.length - 10)
        val pt = decryptBase64BlowfishEbc(ct, key)
        return pt.chunked(5)
    }

    private fun decryptBase64BlowfishEbc(base64Encrypted: String, key: String): String {
        try {
            val encryptedBytes = base64DecodeArray(base64Encrypted)
            val secretKeySpec = SecretKeySpec(key.toByteArray(), "Blowfish")
            val cipher = Cipher.getInstance("Blowfish/ECB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            return "Decryption failed: ${e.message}"
        }
    }

    private suspend fun loadSourceNameExtractor(
        source: String,
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
                        "$source[${link.source}]",
                        "$source[${link.source}]",
                        link.url,
                    ) {
                        this.quality = link.quality
                        this.type = link.type
                        this.referer = link.referer
                        this.headers = link.headers
                        this.extractorData = link.extractorData
                    }
                )
            }
        }
    }
    
    data class Root(
        val link: String,
        @JsonProperty("host_id")
        val hostId: Long,
        val host: String,
    )
}