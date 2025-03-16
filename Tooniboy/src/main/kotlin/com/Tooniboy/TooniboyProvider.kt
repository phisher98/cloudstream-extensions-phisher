package com.Tooniboy

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Vidmolyme


@CloudstreamPlugin
class TooniboyProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Tooniboy())
        registerExtractorAPI(StreamSB8())
        registerExtractorAPI(Vidmolyme())
        registerExtractorAPI(StreamRuby())
        registerExtractorAPI(Vidstreamxyz())
        registerExtractorAPI(D000d())
        registerExtractorAPI(vidhidevip())
        registerExtractorAPI(Cdnwish())
        registerExtractorAPI(FileMoonnl())
        registerExtractorAPI(GDMirrorbot())
    }
}