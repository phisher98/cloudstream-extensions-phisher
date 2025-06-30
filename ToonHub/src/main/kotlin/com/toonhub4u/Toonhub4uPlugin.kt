package com.toonhub4u

import com.lagradost.cloudstream3.extractors.GDMirrorbot
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class Toonhub4uPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Toonhub4u())
        registerExtractorAPI(GDMirrorbot())
        registerExtractorAPI(MultimoviesAIO())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(Animezia())
        registerExtractorAPI(server2())
        registerExtractorAPI(Asnwish())
        registerExtractorAPI(CdnwishCom())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(Dhtpre())
        registerExtractorAPI(Multimoviesshg())
    }
}