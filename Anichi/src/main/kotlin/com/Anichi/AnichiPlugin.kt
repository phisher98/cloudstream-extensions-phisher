package com.Anichi

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Vidguardto2
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnichiPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Anichi())
        registerExtractorAPI(swiftplayers())
        registerExtractorAPI(com.Anichi.StreamWishExtractor())
        registerExtractorAPI(FilemoonV2())
        registerExtractorAPI(Vidguardto2())
    }
}
