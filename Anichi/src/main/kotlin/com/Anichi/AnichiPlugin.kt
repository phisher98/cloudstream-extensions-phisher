package com.Anichi

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnichiPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Anichi())
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(swiftplayers())
    }
}
