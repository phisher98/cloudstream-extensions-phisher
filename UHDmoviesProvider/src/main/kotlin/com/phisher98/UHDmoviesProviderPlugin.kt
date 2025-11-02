package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.app


@CloudstreamPlugin
class UHDmoviesProviderPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(UHDmoviesProvider())
        registerExtractorAPI(Driveleech())
        registerExtractorAPI(Driveseed())
        registerExtractorAPI(UHDMovies())
        registerExtractorAPI(DriveleechPro())
        registerExtractorAPI(DriveleechNet())
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

        suspend fun getDynamicDomains(): Domains? {
            try {
                val redirectUrl = "https://mmodlist.com/?type=uhdmovies"
                val response = app.get(redirectUrl).text

                val regex = Regex("""url=(https?://[^"']+)""", RegexOption.IGNORE_CASE)
                val latestDomain = regex.find(response)?.groupValues?.get(1)

                if (latestDomain != null) {
                    cachedDomains = Domains(UHDMovies = latestDomain)
                } else {
                    // println("⚠️ [UHDmovies] No redirect URL found in response.")
                }
                return cachedDomains
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        data class Domains(
            @JsonProperty("UHDMovies")
            val UHDMovies: String,
        )
    }
}
