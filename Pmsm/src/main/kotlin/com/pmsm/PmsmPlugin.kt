package com.pmsm

import android.content.Context
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class PmsmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Pmsm())
        registerExtractorAPI(DhtprePmsm())
        registerExtractorAPI(NetuPmsm())
        registerExtractorAPI(Playerxupns())
        registerExtractorAPI(Playerxp2p())
        registerExtractorAPI(Playerxseek())
        registerExtractorAPI(Playerxrpms())
        registerExtractorAPI(Player4me())
        registerExtractorAPI(Ezplayer())
        registerExtractorAPI(YandexcdnPmsm())
        registerExtractorAPI(Larhu())
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
            val pencurimoviesubmalay: String,
        )
    }
}
