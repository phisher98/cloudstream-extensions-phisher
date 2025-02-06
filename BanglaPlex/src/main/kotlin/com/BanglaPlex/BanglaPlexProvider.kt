package com.BanglaPlex

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class BanglaPlexProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Banglaplex())
        registerExtractorAPI(Vectorx())
        registerExtractorAPI(Boosterx())
        registerExtractorAPI(Iplayerhls())
        registerExtractorAPI(StreamTape()) }
}