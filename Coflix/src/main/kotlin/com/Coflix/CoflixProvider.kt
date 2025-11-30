package com.Coflix

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.extractors.Voe

@CloudstreamPlugin
class CoflixProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Coflix())
        registerExtractorAPI(Voe())
        registerExtractorAPI(wishonly())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(waaw())
        registerExtractorAPI(VidHideplus())
        registerExtractorAPI(darkibox())
        registerExtractorAPI(Vidmoly())
        registerExtractorAPI(Videzz())
        registerExtractorAPI(Uqload())
        registerExtractorAPI(Vidguardto2())
        registerExtractorAPI(CoflixUPN())
        registerExtractorAPI(Mivalyo())
        registerExtractorAPI(Voe())
    }
}