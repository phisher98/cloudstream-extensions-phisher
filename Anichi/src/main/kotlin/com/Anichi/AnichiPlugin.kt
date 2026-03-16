package com.Anichi

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnichiPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Anichi())
        registerExtractorAPI(swiftplayers())
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(FilemoonV2())
        registerExtractorAPI(Allanimeups())
    }
}
