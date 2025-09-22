package com.RingZ

import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class RingZProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(RingZ())
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(waaw())
        registerExtractorAPI(wishfast())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(FileMoon())
    }
}