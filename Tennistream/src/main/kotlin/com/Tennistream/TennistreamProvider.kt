package com.Tennistream

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class CloudyProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Tennistream())
        registerExtractorAPI(Quest4play())
        registerExtractorAPI(Vaguedinosaurs())
        registerExtractorAPI(Choosingnothing())
    }
}
