package com.Toonstream

import com.lagradost.cloudstream3.extractors.EmturbovidExtractor
import com.lagradost.cloudstream3.extractors.GDMirrorbot
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Vidmolyme

@CloudstreamPlugin
class ToonstreamProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Toonstream())
        registerExtractorAPI(StreamSB8())
        registerExtractorAPI(Vidmolyme())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(D000d())
        registerExtractorAPI(vidhidevip())
        registerExtractorAPI(Cdnwish())
        registerExtractorAPI(FileMoonnl())
        registerExtractorAPI(Cloudy())
        registerExtractorAPI(GDMirrorbot())
        registerExtractorAPI(EmturbovidExtractor())
        registerExtractorAPI(Zephyrflick())
    }
}