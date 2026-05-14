package com.zinkmovies

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.delay
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.nodes.Element

fun cleanTitle(raw: String): String {
    val name = raw.substringBefore("(").trim()
        .replace(Regex("""\s+"""), " ") // collapse extra spaces
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    val seasonRegex = Regex("""Season\s*\d+""", RegexOption.IGNORE_CASE)
    val yearRegex = Regex("""\b(19|20)\d{2}\b""")

    val season = seasonRegex.find(raw)?.value?.replaceFirstChar { it.uppercase() }
    val year = yearRegex.find(raw)?.value

    val parts = mutableListOf<String>()
    if (season != null) parts += season
    if (year != null) parts += year

    return if (parts.isEmpty()) {
        name
    } else {
        name + parts.joinToString("") { " ($it)" }
    }
}


data class ResponseDataLocal(val meta: MetaLocal?)

data class MetaLocal(
    val name: String? = null,
    val description: String? = null,
    val actorsData: List<ActorData>? = null,
    val year: String? = null,
    val background: String? = null,
    val genres: List<String>? = null,
    val videos: List<VideoLocal>? = null,
    val rating: Score?,
    val logo: String?,
    val imdbId: String?
)
data class VideoLocal(
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
    val released: String? = null,
    val rating: Score?
)

data class ZinkLink(
    val name: String,
    val url: String
)

data class GenerateTokenResponse(
    @JsonProperty("status")
    val status: String? = null,

    @JsonProperty("token")
    val token: String? = null
)

private val RANDOM_ID_REGEX =
    Regex("""generateDownloadLink\(['"]([^'"]+)""")

private val AJAX_REGEX =
    Regex("""https://[^"'\\s]+ajax_generate_token\.php""")

private val DL_REGEX =
    Regex("""https://[^"'\\s]+/dl/""")

private val SERVER_HANDLER_REGEX =
    Regex("""SERVER_HANDLER_URL\s*=\s*["']([^"']+)""")

private val WORKER_REGEX =
    Regex("""handleServerRequest\(['"]worker['"]\s*,\s*['"]([^'"]+)""")

suspend fun generateZinkLinks(url: String): List<ZinkLink> {
    return runCatching {

        val firstDoc = app.get(url).document
        val firstHtml = firstDoc.html()

        val randomId = RANDOM_ID_REGEX
            .find(firstHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?: return emptyList()

        val ajaxEndpoint = AJAX_REGEX
            .find(firstHtml)
            ?.value
            ?: return emptyList()

        val downloadBase = DL_REGEX
            .find(firstHtml)
            ?.value
            ?: return emptyList()

        val token = retry  { app.post(
            url = "$ajaxEndpoint?random_id=$randomId",
            data = mapOf(
                "random_id" to randomId
            ),
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).parsedSafe<GenerateTokenResponse>()
            ?.token

        } ?: return emptyList()

        val generatedUrl = downloadBase + token

        val generatedDoc = app.get(generatedUrl).document

        val results = generatedDoc
            .select("#mirror-buttons a[href]")
            .mapNotNull { element ->

                val href = element.attr("href").trim()

                if (href.isBlank()) return@mapNotNull null

                ZinkLink(
                    name = element.text()
                        .replace("Generate", "", true)
                        .trim(),
                    url = href
                )
            }
            .toMutableList()

        generatedDoc.selectFirst("#worker-btn")?.let { btn: Element ->

            val workerId = WORKER_REGEX
                .find(btn.attr("onclick"))
                ?.groupValues
                ?.getOrNull(1)

            val serverHandler = SERVER_HANDLER_REGEX
                .find(generatedDoc.html())
                ?.groupValues
                ?.getOrNull(1)

            if (
                !workerId.isNullOrBlank() &&
                !serverHandler.isNullOrBlank()
            ) {

                runCatching {

                    val workerJson = JSONObject(
                        app.post(
                            url = serverHandler,
                            requestBody = """
                                {
                                    "server":"worker",
                                    "random_id":"$workerId"
                                }
                            """.trimIndent().toRequestBody(),
                            headers = mapOf(
                                "X-Requested-With" to "XMLHttpRequest",
                                "Content-Type" to "application/json",
                                "Origin" to generatedUrl.substringBefore("/dl/"),
                                "Referer" to generatedUrl
                            )
                        ).text
                    )

                    workerJson.optString("url")
                        .ifBlank {
                            workerJson.optString("download")
                        }
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            results += ZinkLink(
                                name = "WORKER",
                                url = it
                            )
                        }

                }
            }
        }

        results.distinctBy { it.url }

    }.getOrElse {
        emptyList()
    }
}

private suspend fun <T> retry(
    times: Int = 3,
    delayMs: Long = 1000,
    block: suspend () -> T?
): T? {

    repeat(times - 1) {
        runCatching {
            block()
        }.getOrNull()?.let {
            return it
        }

        delay(delayMs)
    }

    return runCatching {
        block()
    }.getOrNull()
}

fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}