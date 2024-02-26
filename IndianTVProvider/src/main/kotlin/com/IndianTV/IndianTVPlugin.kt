package com.IndianTV


import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.getRhinoContext
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.Coroutines.mainWork
import org.apache.commons.codec.DecoderException
import org.mozilla.javascript.Scriptable
import org.apache.commons.codec.binary.*


fun convertHexToBase64(hexString: String): String {
    // Decode the hex string to byte array
    val decodedHex = Hex.decodeHex(hexString.toCharArray())

    // Encode the decoded byte array to base64
    val encodedHexB64 = Base64.encodeBase64(decodedHex)

    // Convert the byte array to a string
    var base64String = String(encodedHexB64)

    // Remove trailing '=' characters
    base64String = base64String.trimEnd('=')

    return base64String
}

class IndianTVPlugin : MainAPI() {
    override var mainUrl = "https://madplay.live/hls/tata"
    override var name = "TATA Sky"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "TATA",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home =
            document.select("div#listContainer > div.box1").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("h2.text-center").text()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        //val category = this.select("p").text()

        return newMovieSearchResponse(title, href, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/").document
        return document.select("div#listContainer div.box1:contains($query), div#listContainer div.box1:contains($query)")
            .mapNotNull {
                it.toSearchResult()
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title =
            document.selectFirst("div.program-info > span.channel-name")?.text()?.trim().toString()
        val poster =
            fixUrl("https://raw.githubusercontent.com/phisher98/HindiProviders/master/TATATVProvider/src/main/kotlin/com/lagradost/0-compressed-daf4.jpg")
        val showname =
            document.selectFirst("div.program-info > div.program-name")?.text()?.trim().toString()
        //val description = document.selectFirst("div.program-info > div.program-description")?.text()?.trim().toString()


        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot = showname
        }
    }



    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        //Log.d("data",data)
        //Log.d("document",document.toString())
        val scripts = document.select("script")
        //Log.d("Kingscript","$scripts")
        scripts.map { script ->
            val finalScriptRaw = script.data().toString()
            if (finalScriptRaw.contains("split")) {
                val js =
                    """
        // Add this at the start!
        var globalArgument = null;
        function jwplayer() {
            return {
                id: null,
                setup: function(arg) {
                    globalArgument = arg;
                }
            };
        };"""
                mainWork {
                    val rhino = getRhinoContext()
                    val scope: Scriptable = rhino.initSafeStandardObjects()
                    rhino.evaluateString(scope, js + finalScriptRaw, "JavaScript", 1, null)

                    //println("output ${scope.get("globalArgument", scope).toJson()}")
                    val outputRhino = scope.get("globalArgument", scope).toJson()
                    Log.d("output", outputRhino)
                    val pattern = """"file":"(.*?)".*?"keyId":"(.*?)".*?"key":"(.*?)"""".toRegex()
                    val matchResult = pattern.find(outputRhino)
                    var link: String? = null
                    var keyId: String? = null
                    var key: String? = null
                    if (matchResult != null && matchResult.groupValues.size == 4) {
                        link = matchResult.groupValues[1]
                        keyId= matchResult.groupValues[2]
                        key = matchResult.groupValues[3]
                    } else {
                        println("File, KeyId, or Key not found.")
                    }
                        val base64String1 = convertHexToBase64("$key")
                        val base64String2 = convertHexToBase64("$keyId")
                        Log.d("Key","$base64String1")
                        Log.d("Key","$base64String2")
                    callback.invoke(
                    DrmExtractorLink(
                        source = it.name,
                        name = it.name,
                        url = "$link",
                        referer = "mad-play.live",
                        type = INFER_TYPE,
                        quality = Qualities.Unknown.value,
                        kid = "$base64String1",
                        key = "$base64String2",
                    )
                )
                }
            }

        }
        return true
    }
}
    

