package com.IndianTV

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.getRhinoContext
import org.mozilla.javascript.Scriptable
import android.util.Base64
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.Coroutines.mainWork
import org.jsoup.nodes.Document
import java.nio.charset.StandardCharsets

class IndianTVPlugin : MainAPI() {
    override var mainUrl = "https://madplay.live/hls/tata"
    override var name = "Indian TV"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasQuickSearch = false
    override val hasDownloadSupport = false
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "TATA",
        "https://madplay.live/hls/airtel" to "Airtel",
        "https://madstream.one/pages/jiotvplus.php" to "Jio TV",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
           //val document = app.get(request.data).document
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
        val documentTata = app.get("$mainUrl/").document
        val documentAirtel = app.get("https://madplay.live/hls/airtel").document
        val documentJiotv = app.get("https://madstream.one/pages/jiotvplus.php").document
        val mergedDocument = Document.createShell("")
        mergedDocument.body().append(documentTata.body().html())
        mergedDocument.body().append(documentAirtel.body().html())
        mergedDocument.body().append(documentJiotv.body().html())

            return mergedDocument.select("div#listContainer div.box1:contains($query), div#listContainer div.box1:contains($query)")
                .mapNotNull {
                    it.toSearchResult()
                }
    }

    override suspend fun load(url: String): LoadResponse {
        if (url.contains("jio"))
        {
            val title ="JioTV"
            val poster ="https://i0.wp.com/www.smartprix.com/bytes/wp-content/uploads/2021/08/JioTV-on-smart-TV.png?fit=1200%2C675&ssl=1"
            val showname ="JioTV"

            return newMovieLoadResponse(title, url, TvType.Live, url) {
                this.posterUrl = poster
                this.plot = showname
            }
        }
            val document = app.get(url).document
            val title =document.selectFirst("div.program-info > span.channel-name")?.text()?.trim().toString()
            val poster =fixUrl(document.select("div.program-info > img").attr("src").toString())
            val showname =document.selectFirst("div.program-info > div.program-name")?.text()?.trim().toString()
            //val description = document.selectFirst("div.program-info > div.program-description")?.text()?.trim().toString()

            return newMovieLoadResponse(title, url, TvType.Live, url) {
                this.posterUrl = poster
                this.plot = showname
            }
    }

    // Define a nullable global variable to store globalArgument

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        if (data.contains("jio")) {
            val file =document.select("script:containsData(file)").toString().substringAfter("file\": \"").substringBefore("\",")
            Log.d("Test",file)
            callback.invoke(
                ExtractorLink(
                    source = "TATA SKY",
                    name = "TATA SKY",
                    url = file,
                    referer = "https://madplay.live/",
                    quality = Qualities.Unknown.value,
                    isM3u8 = true,
                )
            )
        } else {
            val scripts = document.select("script")
            var globalArgument: Any? = null
            // List to hold all the extracted links

            scripts.map { script ->
                val finalScriptRaw = script.data().toString()
                mainWork {
                    if (finalScriptRaw.contains("split")) {
                        val startJs =
                            """
                var globalArgument = null;
                function jwplayer() {
                    return {
                        id: null,
                        setup: function(arg) {
                            globalArgument = arg;
                        }
                    };
                };
                """
                        val rhino = getRhinoContext()
                        val scope: Scriptable = rhino.initSafeStandardObjects()
                        rhino.evaluateString(scope, startJs + finalScriptRaw, "JavaScript", 1, null)
                        globalArgument = scope.get("globalArgument", scope)
                    }
                }

                // Access globalArgument outside mainWork block
                val rhinout = globalArgument?.toJson() ?: ""
                Log.d("Rhinoout", rhinout)

                val pattern = """"file":"(.*?)".*?"keyId":"(.*?)".*?"key":"(.*?)"""".toRegex()
                val matchResult = pattern.find(rhinout)
                val file: String?
                val keyId: String?
                val key: String?
                if (matchResult != null) {
                    file = matchResult.groupValues[1]
                    keyId = matchResult.groupValues[2]
                    key = matchResult.groupValues[3]

                    Log.d("Test", key)
                    Log.d("Test", keyId)
                    val newkeyId = keyId.toString()
                    val newkey = key.toString()

                    val link = file.toString()
                    val finalkey = decodeHex(newkey)
                    val finalkeyid = decodeHex(newkeyId)
                    Log.d("Test", finalkey)
                    Log.d("Test", finalkeyid)

                    callback.invoke(
                        DrmExtractorLink(
                            source = "TATA SKY",
                            name = "TATA SKY",
                            url = link,
                            referer = "madplay.live",
                            quality = Qualities.Unknown.value,
                            type = INFER_TYPE,
                            kid = finalkeyid,
                            key = finalkey,
                        )
                    )
                }
            }
        }
        return true
    }


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
