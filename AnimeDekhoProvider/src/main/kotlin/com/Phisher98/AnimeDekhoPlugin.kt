package com.phisher98

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.FilemoonV2
import com.lagradost.cloudstream3.extractors.GDMirrorbot
import com.lagradost.cloudstream3.extractors.Krakenfiles
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.Voe

@CloudstreamPlugin
class AnimeDekhoPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(AnimeDekhoProvider())
        //registerMainAPI(OnepaceProvider())
        registerMainAPI(HindiSubAnime())
        registerExtractorAPI(StreamRuby())
        registerExtractorAPI(Vidmolynet())
        registerExtractorAPI(GDMirrorbot())
        registerExtractorAPI(Cdnwish())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(FileMoonNL())
        registerExtractorAPI(Krakenfiles())
        registerExtractorAPI(Voe())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(FilemoonV2())
        registerExtractorAPI(Animezia())
        registerExtractorAPI(Cloudy())
        registerExtractorAPI(vidcloudupns())
        registerExtractorAPI(Animedekhoco())
        registerExtractorAPI(Blakiteapi())
        registerExtractorAPI(ascdn21())
    }
}
