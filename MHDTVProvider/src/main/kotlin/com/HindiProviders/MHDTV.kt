package com.HindiProviders

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.nio.charset.StandardCharsets

class MHDTV : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://nowmaxtv.com"
    override var name = "MHDTV"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.Live
    )

    override val mainPage = mainPageOf(
        "channel/sports" to "Sports",
        "channel/english" to "English",
        "channel/hindi" to "Hindi",
        "channel/sony-liv" to "Sony Liv",
        "channel/marathi" to "Marathi",
        "channel/tamil" to "Tamil",
        "channel/telugu" to "Telugu",
        "channel/malayalam" to "Malayalam",
        "channel/malayalam-news" to "Malayalam News",
        "channel/kannada" to "Kannada",
        "channel/punjabi" to "Punjabi",
        "channel/bangla" to "Bangla",
        "channel/pakistani" to "Pakistani TV",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").document
        val home = document.select("article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("img")?.attr("alt")?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }

    private suspend fun getPostUrl(url: String, post: String, nume: String, type: String): String {
        return app.post(
            url = "$mainUrl/wp-admin/admin-ajax.php",
            data = mapOf(
                "action" to "doo_player_ajax",
                "post" to post,
                "nume" to nume,
                "type" to type
            ),
            referer = url,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsed<ResponseHash>().embed_url
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.sheader div.poster img")?.attr("src"))
        val episodes = document.select("ul#playeroptionsul li").mapIndexedNotNull { index, it ->
            val name = it.selectFirst(".title")?.text()?.trim()
            val post = it.attr("data-post")
            val nume = it.attr("data-nume")
            val type = it.attr("data-type")
            val thumbs = it.select(".flag img").attr("src")
            val href = getPostUrl(url, post, nume, type)
            val link =
                if (href.contains("video")) Jsoup.parse(href).select("source").attr("src")
                else if (href.contains("iframe")) Jsoup.parse(href).select("iframe").attr("src")
                else if (href.startsWith("/delta")) "$mainUrl$href"
                else href
            Episode(
                link,
                name,
                season = 1,
                episode = index + 1,
                posterUrl = thumbs
            )
        }
        return if (document.select(".sgeneros a:contains(Movies)").isNullOrEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
            }
        } else {
            val doc = document.select("ul#playeroptionsul li").mapIndexedNotNull { _, it ->
                val post = it.attr("data-post")
                val nume = it.attr("data-nume")
                val type = it.attr("data-type")
                getPostUrl(url, post, nume, type)
            }
            val trailer = if (doc.size == 1) "" else doc[0]
            val href = if (doc.size == 1) doc[0] else doc[1]
            val link = if (href.startsWith("/delta")) "$mainUrl$href" else href
            return newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = poster
                addTrailer(trailer)
            }
        }
    }

    private fun decode(input: String): String = java.net.URLDecoder.decode(input, "utf-8")

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Phisher Test data",data)
        val document = app.get(url = data, referer = "$mainUrl/", headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0")).document
        if (data.startsWith("$mainUrl/jwplayer/")) {
            val decoded = decode(data)
            val sourceWithId = decoded.substringAfter("source=")
            val sourceWithoutId = decoded.substringAfter("source=").substringBefore("&id")
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    url = sourceWithoutId,
                    referer = "",
                    quality = Qualities.Unknown.value,
                    type = INFER_TYPE,
                )
            )
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    url = sourceWithId,
                    referer = "",
                    quality = Qualities.Unknown.value,
                    type = INFER_TYPE,
                )
            )
        }  else if (data.startsWith("https://fakestream.co.in")) {
            val channel=data.substringAfterLast("id=")
            val jsonString=document.toString().substringAfter(channel).substringBefore("},")
            val srcRegex = Regex("""url:\s"(.*?)"""")
            val source =srcRegex.find(jsonString)?.groupValues?.getOrNull(1).toString()
            val keyraw =jsonString.substringAfter("k2: \"").substringBefore("\"")
            val keyidraw =jsonString.substringAfter("k1: \"").substringBefore("\"")
            val key= decodeHex(keyraw)
            val keyid=decodeHex(keyidraw)
            Log.d("Phisher Json",key)
            Log.d("Phisher Json",keyid)
            callback.invoke(
                DrmExtractorLink(
                    this.name,
                    this.name,
                    url = source,
                    referer = "",
                    quality = Qualities.Unknown.value,
                    kid=keyid,
                    key=key,
                    type= INFER_TYPE,
                )
            )
        } else if (data.startsWith("https://otttv.co.in")) {
                val doc=document.toString()
                val regex="""source:.'(.*?)'""".toRegex()
                val url =
                    regex.find(doc)?.groupValues?.get(1) ?:""
                val source="https://otttv.co.in$url"
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        url = source,
                        referer = data,
                        quality = Qualities.Unknown.value,
                        type= INFER_TYPE,
                    )
                )
            }
            else if (data.startsWith("https://mhdtvweb.com/sony/")) {
                val doc=document.toString()
                val regex="""source:.'(.*?)'""".toRegex()
                val source =regex.find(doc)?.groupValues?.get(1) ?:""
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        url = source,
                        referer = data,
                        quality = Qualities.Unknown.value,
                        isM3u8 = true,
                    )
                )
            }else if (data.startsWith("https://mhdtv.co.in/jio")) {
            val doc=document.toString()
            val regex="""source:.'(.*?)'""".toRegex()
            val url =regex.find(doc)?.groupValues?.get(1) ?:""
            val source="https://mhdtv.co.in$url"
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    url = source,
                    referer = data,
                    quality = Qualities.Unknown.value,
                    isM3u8 = true,
                )
            )
        }
        else if (data.startsWith("https://mhdmaxtv.me")) {
            val source=data.substringAfter("https://mhdmaxtv.me/play.php?c=")
            loadExtractor(source,subtitleCallback, callback)
        }
        else if (data.startsWith("https://colorsscreen.com")) {
            val regex="""source:.'(.*?)'""".toRegex()
            val url =regex.find(document.toString())?.groupValues?.get(1) ?:""
            val trueurl="https://colorsscreen.com$url"
            val source=trueurl.replace("php","m3u8")
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    source,
                    referer = data,
                    quality = getQualityFromName(""),
                    isM3u8 = true
                )
            )
        }
        else if (data.startsWith("https://mhdtvweb.com/jc/play.php")) {
            val regex="""source:.'(.*?)'""".toRegex()
            val url =regex.find(document.toString())?.groupValues?.get(1) ?:""
            val trueurl="https://mhdtvweb.com$url"
            val source=trueurl.replace("php","m3u8")
            callback.invoke(
                ExtractorLink(
                    "Mhdtvweb",
                    this.name,
                    source,
                    referer = data,
                    quality = getQualityFromName(""),
                    isM3u8 = true
                )
            )
        }
        else if (data.startsWith("https://keralamaxtv.com")) {
            val regex="""source:.'(.*?)'""".toRegex()
            val url =regex.find(document.toString())?.groupValues?.get(1) ?:""
            val source="https://keralamaxtv.com/$url"
            callback.invoke(
                ExtractorLink(
                    "Mhdtvweb",
                    this.name,
                    source,
                    referer = data,
                    quality = getQualityFromName(""),
                    isM3u8 = true
                )
            )
        }
        return true
    }

    private fun fixUrl(url: String): String {
        if (url.isEmpty()) return ""

        if (url.startsWith("//")) {
            return "http:$url"
        }
        if (!url.startsWith("http")) {
            return "http://$url"
        }
        return url
    }

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("type") val type: String?,
    )

    private fun decodeHex(hexString: String):String {
        //hexStringToByteArray
        val length = hexString.length
        val byteArray = ByteArray(length / 2)

        for (i in 0 until length step 2) {
            byteArray[i / 2] = ((Character.digit(hexString[i], 16) shl 4) +
                    Character.digit(hexString[i + 1], 16)).toByte()
        }
        //byteArrayToBase64
        val base64ByteArray = Base64.encode(byteArray, Base64.NO_PADDING)
        return String(base64ByteArray, StandardCharsets.UTF_8).trim()
    }
}
