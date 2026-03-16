package com.Kaido

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class KaidoPlugin : Plugin() {
    override fun load() {
        registerMainAPI(Kaido())
        registerExtractorAPI(Rapid())
    }
}
