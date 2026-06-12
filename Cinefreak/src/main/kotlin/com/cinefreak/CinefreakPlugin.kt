package com.cinefreak

import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.app


@CloudstreamPlugin
class CinefreakPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Cinefreak())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(HubCloud())
        registerExtractorAPI(Hubdrive())
        registerExtractorAPI(HUBCDN())
        registerExtractorAPI(PixelDrainDev())
        registerExtractorAPI(Hubcloudone())
        registerExtractorAPI(Neodrive())
        registerExtractorAPI(CineCloud())
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
            val hubcloud: String,
            val cinefreak: String,
        )
    }
}