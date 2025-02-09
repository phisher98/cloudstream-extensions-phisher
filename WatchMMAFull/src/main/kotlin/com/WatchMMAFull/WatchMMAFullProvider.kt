package com.WatchMMAFull

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class WatchMMAFullProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(WatchMMAFull())
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(waaw())
        registerExtractorAPI(wishfast())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(Spcdn())
        registerExtractorAPI(SuiSports())
    }
}