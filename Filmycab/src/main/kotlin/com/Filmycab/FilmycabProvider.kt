package com.Filmycab

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class FilmycabProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Filmycab())
        registerExtractorAPI(Filesdl())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(HubCloud())
        registerExtractorAPI(Gofile())
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
            @JsonProperty("Filmycab")
            val Filmycab: String,
        )
    }
}