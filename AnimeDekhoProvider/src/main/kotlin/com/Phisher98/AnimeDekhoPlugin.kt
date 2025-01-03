package com.Phisher98

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.Krakenfiles
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.Voe

@CloudstreamPlugin
class AnimeDekhoPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimeDekhoProvider())
        registerMainAPI(OnepaceProvider())
        registerMainAPI(HindiSubAnime())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(VidStream())
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
    }
}
