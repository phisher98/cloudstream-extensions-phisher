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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
                    scriptProcessed = true
                }
                yield()
            }
        }

        val rhinout = globalArgument?.toJson() ?: ""
        Log.d("Rhinoout", rhinout)

        val pattern = """"file":"(.*?)".*?"keyId":"(.*?)".*?"key":"(.*?)"""".toRegex()
        val matchResult = pattern.find(rhinout)
        var link: String? = null
        var keyId: String? = null
        var key: String? = null

        matchResult?.let {
            if (it.groupValues.size == 4) {
                link = it.groupValues[1]
                keyId = it.groupValues[2]
                key = it.groupValues[3]
            }
        }

        if (!link.isNullOrEmpty() && !keyId.isNullOrEmpty() && !key.isNullOrEmpty()) {
            try {
                // Convert keyId and key to Base64 asynchronously
                val finalKeyId = convertToBase64Async(keyId!!)
                val finalKey = convertToBase64Async(key!!)

                // Wait for the conversion to complete
                val (convertedKeyId, convertedKey) = finalKeyId.await() to finalKey.await()

                // Playback logic using the obtained keys
                callback.invoke(
                    DrmExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = link!!,
                        referer = "",
                        quality = Qualities.Unknown.value,
                        type = INFER_TYPE,
                        kid = convertedKeyId,
                        key = convertedKey
                    )
                )
            } catch (e: Exception) {
                Log.e("PlaybackError", "Error during playback: ${e.message}", e)
            }
        } else {
            Log.e("LoadLinksError", "Failed to extract link, keyId, or key from the script data.")
        }

        return true
    }

    private fun convertToBase64Async(input: String): Deferred<String> = CoroutineScope(Dispatchers.IO).async {
        // Simulate conversion to Base64 asynchronously
        delay(1000) // Replace this with your actual conversion logic

        // For demonstration purposes, return the Base64-encoded input
        val base64Encoded = byteArrayToBase64(hexStringToByteArray(input))
        base64Encoded
    }


}

    

