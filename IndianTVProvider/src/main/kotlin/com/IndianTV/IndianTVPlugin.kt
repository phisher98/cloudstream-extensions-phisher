package com.IndianTV

import android.annotation.SuppressLint
//import android.util.Log
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
    override var name = "Indian TV"
    override val hasMainPage = true
    override var lang = "hi"
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        INDIANTATAAPI to "TATA",
        INDIANAirtelAPI to "Airtel",
        INDIANJIOAPI to "Jio TV",
        INDIANDiscoveryAPI to "Discovery"
    )

    companion object {
        const val INDIANJIOAPI = BuildConfig.INDIANTV_JIO_API
        const val INDIANTATAAPI = BuildConfig.INDIANTV_TATA_API
        const val INDIANDiscoveryAPI = BuildConfig.INDIANTV_Discovery_API
        const val INDIANAirtelAPI = BuildConfig.INDIANTV_Airtel_API

        val Useragent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data,headers = mapOf("User-Agent" to Useragent)).document
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
        val documentTata = app.get(INDIANTATAAPI,headers = mapOf("User-Agent" to Useragent)).document
        val documentAirtel = app.get(INDIANAirtelAPI,headers = mapOf("User-Agent" to Useragent)).document
        val documentJiotv = app.get(INDIANJIOAPI,headers = mapOf("User-Agent" to Useragent)).document
        val documentdiscovery = app.get(INDIANDiscoveryAPI,headers = mapOf("User-Agent" to Useragent)).document
        val mergedDocument = Document.createShell("")
        mergedDocument.body().append(documentTata.body().html())
        mergedDocument.body().append(documentdiscovery.body().html())
        mergedDocument.body().append(documentAirtel.body().html())
        mergedDocument.body().append(documentJiotv.body().html())

            return mergedDocument.select("div#listContainer div.box1:contains($query), div#listContainer div.box1:contains($query)")
                .mapNotNull {
                    it.toSearchResult()
                }
    }

    override suspend fun load(url: String): LoadResponse {
        if (url.contains("m3u8"))
        {
            val title ="JioTV"
            val poster ="https://i0.wp.com/www.smartprix.com/bytes/wp-content/uploads/2021/08/JioTV-on-smart-TV.png?fit=1200%2C675&ssl=1"
            val showname ="JioTV"

            return newMovieLoadResponse(title, url, TvType.Live, url) {
                this.posterUrl = poster
                this.plot = showname
            }
        }
            val document = app.get(url, headers = mapOf("User-Agent" to Useragent)).document
            val title =document.selectFirst("div.program-info > span.channel-name")?.text()?.trim().toString()
            val poster ="https://cdn.mos.cms.futurecdn.net/iYdoTcTScdApk3JV5GfEAT-1920-80.jpg"
            val showname =document.selectFirst("div.program-info > div.program-name")?.text()?.trim().toString()
            //val description = document.selectFirst("div.program-info > div.program-description")?.text()?.trim().toString()

            return newMovieLoadResponse(title, url, TvType.Live, url) {
                this.posterUrl = poster
                this.plot = showname
            }
    }

    // Define a nullable global variable to store globalArgument

    @SuppressLint("SuspiciousIndentation")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data,headers = mapOf("User-Agent" to Useragent)).document
        if (data.contains("jio")) {
            val scripts = document.select("script")
            var globalArgument: Any? = null

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
                val link=rhinout.substringAfter("file\":\"").substringBefore("\",")
                    callback.invoke(
                        ExtractorLink(
                            source = "INDIAN TV",
                            name = "INDIAN TV",
                            url = link,
                            referer = "",
                            quality = Qualities.Unknown.value,
                            type = INFER_TYPE,
                        )
                    )
            }
        } else
            if (data.contains("tata")) {
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
                //Log.d("Rhinoout", rhinout)

                val pattern = """"file":"(.*?)".*?"keyId":"(.*?)".*?"key":"(.*?)"""".toRegex()
                val matchResult = pattern.find(rhinout)
                val file: String?
                val keyId: String?
                val key: String?
                if (matchResult != null) {
                    file = matchResult.groupValues[1]
                    keyId = matchResult.groupValues[2]
                    key = matchResult.groupValues[3]

                    val newkeyId = keyId.toString()
                    val newkey = key.toString()

                    val link = file.toString()
                    val finalkey = decodeHex(newkey)
                    val finalkeyid = decodeHex(newkeyId)

                    callback.invoke(
                        DrmExtractorLink(
                            source = "INDIAN TV",
                            name = "INDIAN TV",
                            url = link,
                            referer = "",
                            quality = Qualities.Unknown.value,
                            type = INFER_TYPE,
                            kid = finalkeyid,
                            key = finalkey,
                        )
                    )
                }
            }
        }
        else if (data.contains("discovery"))
            {
                val link=data.substringAfter("jwplayer.php?")
                callback.invoke(
                    ExtractorLink(
                        source = "INDIAN TV",
                        name = "INDIAN TV",
                        url = link,
                        referer = "",
                        quality = Qualities.Unknown.value,
                        type = INFER_TYPE,
                    )
                )
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
