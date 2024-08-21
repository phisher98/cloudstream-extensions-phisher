package com.Anplay

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

class AnimesagaStream : Chillx() {
    override val name = "AnimesagaStream"
    override val mainUrl = "https://stream.anplay.in"
}

class Moviesapi : Chillx() {
    override val name = "Moviesapi"
    override val mainUrl = "https://w1.moviesapi.club"
}

class Bestx : Chillx() {
    override val name = "Bestx"
    override val mainUrl = "https://bestx.stream"
}

class Watchx : Chillx() {
    override val name = "Watchx"
    override val mainUrl = "https://watchx.top"
}

/*

open class Chillx : ExtractorApi() {
    override val name = "Chillx"
    override val mainUrl = "https://chillx.top"
    override val requiresReferer = true

    companion object {
        private var key: String? = null

        suspend fun fetchKey(): String {
            return if (key != null) {
                key!!
            } else {
                val fetch = app.get("https://rowdy-avocado.github.io/multi-keys").parsedSafe<Keys>()?.key?.get(0) ?: throw ErrorLoadingException("Unable to get key")
                key = fetch
                key!!
            }
        }
    }


    @Suppress("NAME_SHADOWING")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val master = Regex("""JScript[\w+]?\s*=\s*'([^']+)""").find(
            app.get(
                url,
                referer = url,
            ).text
        )?.groupValues?.get(1)
        val key = "1FHuaQhhcsKgpTRB"
        val decrypt = cryptoAESHandler(master ?: "", key.toByteArray(), false)?.replace("\\", "") ?: throw ErrorLoadingException("failed to decrypt")
        val source = Regex(""""?file"?:\s*"([^"]+)""").find(decrypt)?.groupValues?.get(1)
        val subtitles = Regex("""subtitle"?:\s*"([^"]+)""").find(decrypt)?.groupValues?.get(1)
        val subtitlePattern = """\[(.*?)](https?://[^\s,]+)""".toRegex()
        val matches = subtitlePattern.findAll(subtitles ?: "")
        val languageUrlPairs = matches.map { matchResult ->
            val (language, url) = matchResult.destructured
            decodeUnicodeEscape(language) to url
        }.toList()

        languageUrlPairs.forEach{ (name, file) ->
            subtitleCallback.invoke(
                SubtitleFile(
                    name,
                    file
                )
            )
        }
        // required
        val headers = mapOf(
         "Accept" to "*//*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
        )

        M3u8Helper.generateM3u8(
            name,
            source ?: return,
            "$mainUrl/",
            headers = headers
        ).forEach(callback)
    }*/

    open class Chillx : ExtractorApi() {
    override val name = "Chillx"
    override val mainUrl = "https://chillx.top"
    override val requiresReferer = true
	private var key: String? = null

    @Suppress("NAME_SHADOWING")
	override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {		
		val master = Regex("\\s*=\\s*'([^']+)").find(
            app.get(
                url,
                referer = referer ?: "",
                headers = mapOf(
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                    "Accept-Language" to "en-US,en;q=0.5",
                )
            ).text
        )?.groupValues?.get(1)

		val key = fetchKey() ?: throw ErrorLoadingException("Unable to get key")

        val decrypt = cryptoAESHandler(master ?: return, key.toByteArray(), false)
            ?.replace("\\", "")
            ?: throw ErrorLoadingException("failed to decrypt")

        val source = Regex(""""?file"?:\s*"([^"]+)""").find(decrypt)?.groupValues?.get(1)
        val name = url.getHost()

        val subtitles = Regex("""subtitle"?:\s*"([^"]+)""").find(decrypt)?.groupValues?.get(1)
        val subtitlePattern = """\[(.*?)\](https?://[^\s,]+)""".toRegex()
        val matches = subtitlePattern.findAll(subtitles ?: "")
        val languageUrlPairs = matches.map { matchResult ->
            val (language, url) = matchResult.destructured
	@@ -79,28 +122,51 @@ open class Chillx : ExtractorApi() {
                )
            )
        }

        val header =
            mapOf(
                "accept" to "*/*",
                "accept-language" to "en-US,en;q=0.5",
                "Origin" to mainUrl,
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "user-agent" to USER_AGENT,
            )

		callback.invoke(
            ExtractorLink(
                name,
                name,
                url = source ?: return,
                referer = "$mainUrl/",
                quality = Qualities.Unknown.value,
				INFER_TYPE,
                headers = header,
            )
        )
    }	

    private suspend fun fetchKey(): String? {
        return app.get("https://raw.githubusercontent.com/Rowdy-Avocado/multi-keys/keys/index.html")
            .parsedSafe<Keys>()?.key?.get(0)?.also { key = it }
    }
	
	private fun decodeUnicodeEscape(input: String): String {
        val regex = Regex("u([0-9a-fA-F]{4})")
        return regex.replace(input) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
    }

	private fun String.getHost(): String {
		return fixTitle(URI(this).host.substringBeforeLast(".").substringAfterLast("."))
	}

	data class Keys(
        @JsonProperty("chillx") val key: List<String>
    )

}

    private fun decodeUnicodeEscape(input: String): String {
        val regex = Regex("u([0-9a-fA-F]{4})")
        return regex.replace(input) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
    }


}
