package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.XStreamCdn
import com.lagradost.cloudstream3.extractors.VidHidePro5
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.app


@CloudstreamPlugin
class MultiMoviesProviderPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(MultiMoviesProvider())
        registerExtractorAPI(VidHidePro5())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(XStreamCdn())
        registerExtractorAPI(DoodLaExtractor())
        registerExtractorAPI(Animezia())
        registerExtractorAPI(server2())
        registerExtractorAPI(MultimoviesAIO())
        registerExtractorAPI(GDMirrorbot())
        registerExtractorAPI(Asnwish())
        registerExtractorAPI(CdnwishCom())
        registerExtractorAPI(Strwishcom())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Streamcasthub())
        registerExtractorAPI(Dhcplay())
        registerExtractorAPI(server1())
        registerExtractorAPI(Techinmind())
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
            @JsonProperty("MultiMovies")
            val MultiMovies: String,
        )
    }
}
