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
import kotlinx.coroutines.yield
import java.nio.charset.StandardCharsets

/*fun hexStringToBase64(hexString: String): String {
    val length = hexString.length
    val byteArray = ByteArray(length / 2)

    for (i in 0 until length step 2) {
        byteArray[i / 2] = ((Character.digit(hexString[i], 16) shl 4) +
                Character.digit(hexString[i + 1], 16)).toByte()
    }

    val base64ByteArray = Base64.encode(byteArray, Base64.NO_PADDING)
    return String(base64ByteArray, StandardCharsets.UTF_8)
}
*/



fun hexStringToByteArray(hexString: String): ByteArray {
    val length = hexString.length
    val byteArray = ByteArray(length / 2)

    for (i in 0 until length step 2) {
        byteArray[i / 2] = ((Character.digit(hexString[i], 16) shl 4) +
                Character.digit(hexString[i + 1], 16)).toByte()
    }
    return byteArray
}

fun byteArrayToBase64(byteArray: ByteArray): String {
    val base64ByteArray = Base64.encode(byteArray, Base64.NO_PADDING)
    return String(base64ByteArray, StandardCharsets.UTF_8)
}


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
    private var globalArgument: Any? = null

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val scripts = document.select("script")

        // Introduce a flag to track whether the script has been processed
        var scriptProcessed = false

        scripts.forEach { script ->
            if (!scriptProcessed && script.data().toString().contains("split")) {
                val finalScriptRaw = script.data().toString()
                mainWork {
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
                    scriptProcessed = true // Set the flag to indicate that the script has been processed
                }
                yield()
            }
        }

        // Access globalArgument outside mainWork block
        val rhinout = globalArgument?.toJson() ?: ""
        Log.d("Rhinoout", rhinout)

        val pattern = """"file":"(.*?)".*?"keyId":"(.*?)".*?"key":"(.*?)"""".toRegex()
        val matchResult = pattern.find(rhinout)
        val link: String
        val keyId: String
        val key: String
        if (matchResult != null && matchResult.groupValues.size == 4) {
            link = matchResult.groupValues[1]
            keyId = matchResult.groupValues[2]
            key = matchResult.groupValues[3]

            if (keyId.length > 6 && key.length > 6) {
                // Convert hex strings to byte arrays
                val keyIdBytes = hexStringToByteArray(keyId)
                val keyBytes = hexStringToByteArray(key)

                // Convert byte arrays to Base64 strings
                val finalkeyid = byteArrayToBase64(keyIdBytes)
                Log.d("finalkeyid", "Base64 Encoded String: $finalkeyid")

                val finalkey = byteArrayToBase64(keyBytes)
                Log.d("finalkey", "Base64 Encoded String: $finalkey")

                // Ensure the keys are valid
                if (finalkeyid.isNotEmpty() && finalkey.isNotEmpty()) {
                    // Invoke callback with the extracted values
                    callback.invoke(
                        DrmExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = link,
                            referer = "",
                            quality = Qualities.Unknown.value,
                            type = INFER_TYPE,
                            kid = finalkeyid,
                            key = finalkey,
                        )
                    )
                } else {
                    // Handle case where keys are not valid
                    Log.e("loadLinks", "Invalid keys: keyId=$finalkeyid, key=$finalkey")
                }
            }
        }

        return true
    }

}

    

