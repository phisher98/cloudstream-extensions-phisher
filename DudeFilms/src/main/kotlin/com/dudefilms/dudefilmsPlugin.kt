package com.dudefilms

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.app


@CloudstreamPlugin
class DudefilmsPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Dudefilms())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(HUBCDN())
        registerExtractorAPI(Hubstream())
        registerExtractorAPI(Hubcdnn())
        registerExtractorAPI(HubCloud())
        registerExtractorAPI(PixelDrainDev())
        registerExtractorAPI(Hubdrive())
    }

    companion object {
        private const val DOMAINS_URL =
            "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
        var cachedDomains: Domains? = null

        suspend fun getDomains(forceRefresh: Boolean = false): Domains? {
            if (cachedDomains == null || forceRefresh) {
                try {
                    cachedDomains = app.get(DOMAINS_URL).parsedSafe<Domains>()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }
            return cachedDomains
        }

        data class Domains(
            @JsonProperty("HDHUB4u")
            val HDHUB4u: String,
        )
    }
}