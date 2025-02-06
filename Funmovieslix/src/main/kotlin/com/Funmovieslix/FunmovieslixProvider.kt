package com.Funmovieslix

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.FileMoonIn

@CloudstreamPlugin
class FunmovieslixProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Funmovieslix())
        registerExtractorAPI(Ryderjet())
        registerExtractorAPI(FilemoonV2())
        registerExtractorAPI(Dhtpre())
        registerExtractorAPI(FileMoonIn())
        registerExtractorAPI(Vidhideplus())
    }
}