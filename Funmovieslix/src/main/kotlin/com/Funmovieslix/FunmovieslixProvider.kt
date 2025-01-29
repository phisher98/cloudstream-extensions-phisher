package com.Funmovieslix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.FileMoonIn

@CloudstreamPlugin
class FunmovieslixProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Funmovieslix())
        registerExtractorAPI(Ryderjet())
        registerExtractorAPI(FilemoonV2())
        registerExtractorAPI(Dhtpre())
        registerExtractorAPI(FileMoonIn())
        registerExtractorAPI(Vidhideplus())
    }
}