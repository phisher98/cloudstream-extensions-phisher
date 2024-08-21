package com.HindiProvider


//import android.util.Log
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

open class Streamruby : ExtractorApi() {
    override var name = "Streamruby"
    override var mainUrl = "streamruby.com"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        if (url.contains("/e/"))
        {
            val newurl=url.replace("/e","")
            val txt = app.get(newurl).text
            val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(txt)?.groupValues?.getOrNull(1).toString()
            return listOf(
                ExtractorLink(
                    this.name,
                    this.name,
                    m3u8,
                    mainUrl,
                    Qualities.Unknown.value,
                    type = INFER_TYPE
                )
            )
        }
        else
        {
            val txt = app.get(url).text
            val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(txt)?.groupValues?.getOrNull(1).toString()
            return listOf(
                ExtractorLink(
                    this.name,
                    this.name,
                    m3u8,
                    mainUrl,
                    Qualities.Unknown.value,
                    type = INFER_TYPE
                )
            )
        }
    }
}


open class VidStream : ExtractorApi() {
    override var name = "VidStream"
    override var mainUrl = "https://vidstreamnew.xyz"
    override val requiresReferer = false
    private val serverUrl = "https://vidxstream.xyz"
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


class Vidmolynet : Vidmoly() {
    override val mainUrl = "https://vidmoly.net"
}

class Cdnwish : StreamWishExtractor() {
    override var name = "Streamwish"
    override var mainUrl = "https://cdnwish.com"
}

open class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = false
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit) {
        app.get(url).document.select("ul#videoLinks li").map {
            val link=it.attr("data-link")
            loadExtractor(link,subtitleCallback, callback)
        }
    }
}


fun Extractvidlink(url: String): String {
    val file=url.substringAfter("sources: [{\"file\":\"").substringBefore("\",\"")
    return file
}

fun Extractvidsub(url: String): String {
    val file=url.substringAfter("tracks: [{\"file\":\"").substringAfter("file\":\"").substringBefore("\",\"")
    return file
}

data class Media(val url: String, val poster: String? = null, val mediaType: Int? = null)
data class Keys(
    @JsonProperty("chillx") val key: List<String>
)
