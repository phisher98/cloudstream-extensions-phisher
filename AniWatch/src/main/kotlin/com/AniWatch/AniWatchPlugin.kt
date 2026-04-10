package com.AniWatch

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AniWatchPlugin : Plugin() {
    override fun load() {
        registerMainAPI(AniWatch())
        registerExtractorAPI(Rapid())
    }
}
