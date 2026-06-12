package com.Fibwatch

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin


@CloudstreamPlugin
class FibwatchPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Fibwatch())
        registerMainAPI(Fibwatchdrama())
        registerMainAPI(Fibtoon())
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
            @param:JsonProperty("fibwatch")
            val fibwatch: String,
            @param:JsonProperty("fibtoon")
            val fibtoon: String,
            @param:JsonProperty("fibdrama")
            val fibdrama: String,
        )
    }
}
