package com.phisher98

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.phisher98.UltimaMediaProvidersUtils.commonLinkLoader
import com.phisher98.UltimaMediaProvidersUtils.ServerName
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class VidsrcccProvider : MediaProvider() {
    override val name = "VidSrc CC"
    override val domain = "https://vidsrc.cc"
    override val categories = listOf(Category.MEDIA)

    override suspend fun loadContent(
        url: String,
        data: LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (data.season == null) {
            "$domain/v2/embed/movie/${data.tmdbId}?autoPlay=false"
        } else {
            "$domain/v2/embed/tv/${data.tmdbId}/${data.season}/${data.episode}?autoPlay=false"
        }
        val doc = app.get(url).document.toString()
        val regex = Regex("""var\s+(\w+)\s*=\s*(?:"([^"]*)"|(\w+));""")
        val variables = mutableMapOf<String, String>()

        regex.findAll(doc).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            variables[key] = value
        }
        val vvalue = variables["v"] ?: ""
        val userId = variables["userId"] ?: ""
        val imdbId = variables["imdbId"] ?: ""
        val movieId = variables["movieId"] ?: ""
        val movieType = variables["movieType"] ?: ""

        val vrf = generateVidsrcVrf(movieId,userId)
        val apiurl = if (data.season == null) {
            "${domain}/api/${data.tmdbId}/servers?id=${data.tmdbId}&type=$movieType&v=$vvalue=&vrf=$vrf&imdbId=$imdbId"
        } else {
            "${domain}/api/${data.tmdbId}/servers?id=${data.tmdbId}&type=$movieType&season=${data.season}&episode=${data.episode}&v=$vvalue&vrf=${vrf}&imdbId=$imdbId"
        }
        app.get(apiurl).parsedSafe<Vidsrcccservers>()?.data?.forEach {
            val servername = it.name
            val iframe = app.get("$domain/api/source/${it.hash}")
                .parsedSafe<Vidsrcccm3u8>()?.data?.source
            val sourceUrl = iframe?.let { iframeUrl ->
                val response = app.get(iframeUrl, referer = domain).text
                val urlregex = Regex("""var\s+source\s*=\s*"([^"]+)"""")
                val match = urlregex.find(response)
                match?.groups?.get(1)?.value?.replace("""\\/""".toRegex(), "/")
            }

            sourceUrl?.let { url->
                commonLinkLoader(
                    "⌜ Vidsrc ⌟ | [$servername]",
                    ServerName.Videostr,
                    url,
                    null,
                    null,
                    subtitleCallback,
                    callback
                )
            }
        }
    }

    private fun generateVidsrcVrf(movieId: String, userId: String): String {
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray())
        val key = SecretKeySpec(keyBytes, "AES")
        val iv = IvParameterSpec(ByteArray(16))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val plaintext = movieId.toByteArray()
        val ciphertext = cipher.doFinal(plaintext)
        val encoded = base64Encode(ciphertext)
        val urlSafe = encoded.replace('+', '-').replace('/', '_').replace("=", "")
        return urlSafe
    }

    data class Vidsrcccservers(
        val data: List<VidsrcccDaum>,
        val success: Boolean,
    )

    data class VidsrcccDaum(
        val name: String,
        val hash: String,
    )

    data class Vidsrcccm3u8(
        val data: VidsrcccData,
        val success: Boolean,
    )

    data class VidsrcccData(
        val type: String,
        val source: String,
    )
}