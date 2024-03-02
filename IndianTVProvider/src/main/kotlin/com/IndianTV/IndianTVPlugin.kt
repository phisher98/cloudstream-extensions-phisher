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
import java.nio.charset.StandardCharsets


class IndianTVPlugin : MainAPI() {
    override var mainUrl = "https://madplay.live/hls/tata"
    override var name = "Indian TV"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "TATA",
        "https://madplay.live/hls/airtel" to "Airtel",
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

    // Define a nullable global variable to store globalArgument

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val scripts = document.select("script")
        var globalArgument: Any? = null
        // List to hold all the extracted links
        val links = mutableListOf<ExtractorLink>()

        // Counter to keep track of completed asynchronous operations
        var completedCount = 0

        // Function to invoke callback after all asynchronous operations have completed
        fun invokeCallback() {
            if (completedCount == scripts.size) {
                // All operations completed, now invoke the callback with all extracted links
                links.forEach { callback.invoke(it) }
            }
        }

        scripts.forEach { script ->
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
            if (matchResult != null && matchResult.groupValues.size == 4) {
                file = matchResult.groupValues[1]
                keyId = matchResult.groupValues[2]
                key = matchResult.groupValues[3]

                if (keyId.length > 6 && key.length > 6) {
                    val newkeyId = keyId.toString()
                    val newkey = key.toString()

                    val link = file.toString()
                    val finalkey = decodeHex(newkey)
                    val finalkeyid = decodeHex(newkeyId)

                    // Add the extracted link to the list
                    links.add(
                        DrmExtractorLink(
                            source = "TATA Sky",
                            name = "TATA SKy",
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

            // Increment the completed count and check if all operations are done
            completedCount++
            invokeCallback()
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
