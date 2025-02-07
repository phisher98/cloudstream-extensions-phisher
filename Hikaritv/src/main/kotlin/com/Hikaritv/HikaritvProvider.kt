package com.hikaritv

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnisagaProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Hikaritv())
        registerExtractorAPI(Ghbrisk())
        registerExtractorAPI(Swishsrv())
        registerExtractorAPI(FilemoonV2())
        registerExtractorAPI(Boosterx())
        registerExtractorAPI(Chillx())
    }
}