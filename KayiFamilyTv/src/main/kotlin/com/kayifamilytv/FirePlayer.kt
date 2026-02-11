package com.kayifamilytv

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class FirePlayerX : FirePlayer() {
    override val name = "WakeUpUmmah"
    override val mainUrl = "https://wakeupummah.com"
}

/**
 * FirePlayer Extractor
 */
abstract class FirePlayer : ExtractorApi() {
    override val name = "FirePlayer"
    override val requiresReferer = true
    override val mainUrl = "https://fireplayer.com/"

    private fun extractVideoId(url: String): String? {
        val regex = Regex("/fireplayer/video/([a-f0-9]{32})")
        return regex.find(url)?.groupValues?.get(1)
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoId = extractVideoId(url) ?: return
        val sources = extractAllSources(videoId, referer ?: "https://kayifamilytv.com/v18")

        sources
            .distinctBy { it.url }
            .forEach { source ->

                val src = source.url.lowercase()

                when {
                    src.contains(".m3u8") -> {
                        callback.invoke(
                            newExtractorLink(
                                source.name,
                                source.name,
                                source.url,
                                ExtractorLinkType.M3U8,
                            ) {
                                this.quality = Qualities.Unknown.value
                            }
                        )
                    }

                    src.endsWith(".mp4") || src.contains("yandex") -> {
                        callback.invoke(
                            newExtractorLink(
                                source.name,
                                source.name,
                                source.url,
                                ExtractorLinkType.VIDEO
                            ) {
                                this.quality = Qualities.Unknown.value
                            }
                        )
                    }

                    src.contains("namasha.com/embed") -> {
                        extractNamasha(source.url, callback)
                    }

                    else -> {
                        loadExtractor(source.url, "", subtitleCallback, callback)
                    }
                }
            }
    }

    /**
     * Extract all sources from FirePlayer
     */
    private suspend fun extractAllSources(videoId: String, referrer: String): MutableList<ExtractedSource> {
        val sources = mutableListOf<ExtractedSource>()
        val baseUrl = "$mainUrl/fireplayer/video/"
        val extractUrl = "${baseUrl}${videoId}?do=getVideo"
        val headers = mapOf(
            "Referer" to referrer,
            "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (HTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "X-Requested-With" to "XMLHttpRequest"
        )

        // Initial request to get sourceList
        val initialData = mapOf(
            "hash" to videoId,
            "r" to referrer,
            "s" to ""
        )

        val initialResponse = app.post(extractUrl, data = initialData, headers = headers)
        val initialJson = JSONObject(initialResponse.text)

        val sourceList = initialJson.optJSONObject("sourceList")
        if (sourceList != null) {
            // Extract each source
            val sourceKeys = sourceList.keys()
            while (sourceKeys.hasNext()) {
                val sIndex = sourceKeys.next()
                val sourceName = sourceList.getString(sIndex)

                try {
                    val sourceData = mapOf(
                        "hash" to videoId,
                        "r" to referrer,
                        "s" to sIndex
                    )

                    val sourceResponse = app.post(extractUrl, data = sourceData, headers = headers)
                    val rawText = sourceResponse.text.trim()

                    // Skip non-json responses
                    if (!rawText.startsWith("{")) {
                        android.util.Log.w(
                            "FirePlayer",
                            "Skipping invalid response for $sIndex ($sourceName): $rawText"
                        )
                        continue
                    }

                    val sourceJson = JSONObject(rawText)

                    // Check for direct video sources
                    val videoSources = sourceJson.optJSONArray("videoSources")
                    if (videoSources != null) {
                        for (i in 0 until videoSources.length()) {
                            val videoSource = videoSources.getJSONObject(i)
                            sources.add(
                                ExtractedSource(
                                    name = sourceName,
                                    url = videoSource.getString("file"),
                                    type = "direct",
                                    quality = videoSource.optString("label", "HD"),
                                    format = videoSource.optString("type", "mp4")
                                )
                            )
                        }
                    }

                    // Check for embed sources
                    val videoSrc = sourceJson.optString("videoSrc")
                    if (videoSrc.isNotEmpty()) {
                        sources.add(
                            ExtractedSource(
                                name = sourceName,
                                url = videoSrc,
                                type = "embed",
                                quality = "Unknown"
                            )
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e(
                        "FirePlayer",
                        "Source parse failed → sIndex=$sIndex name=$sourceName\n${e.stackTraceToString()}"
                    )
                }
            }
        }

        return sources
    }

    private suspend fun extractNamasha(
        url: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val page = app.get("$url?autoplay=true").text

        val regex = Regex(
            """['"]file['"]\s*:\s*['"]([^'"]+)['"][^}]*?['"]label['"]\s*:\s*['"]([^'"]+)['"]"""
        )

        regex.findAll(page).forEach { match ->
            val videoUrl = match.groupValues[1]
            val label = match.groupValues[2]

            callback.invoke(
                newExtractorLink(
                    "Namasha",
                    "Namasha",
                    videoUrl,
                    ExtractorLinkType.VIDEO
                ) {
                    referer = url
                    quality = getQualityFromName(label)
                }
            )
        }
    }

    data class ExtractedSource(
        val name: String,
        val url: String,
        val type: String,
        val quality: String? = null,
        val format: String? = null
    )
}