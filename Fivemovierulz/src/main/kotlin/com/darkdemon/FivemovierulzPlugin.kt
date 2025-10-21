package com.darkdemon

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class FivemovierulzPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerExtractorAPI(Filelion())
        registerMainAPI(FivemovierulzProvider())
        registerExtractorAPI(StreamwishHG())
        registerExtractorAPI(mivalyo())
    }
}
