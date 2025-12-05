package com.Goojara

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class GoojaraProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Goojara())
        registerExtractorAPI(Wootly())
        registerExtractorAPI(Stre4mpay())
        registerExtractorAPI(Streamplay())
    }
}