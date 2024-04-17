package com.Animenosub

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.FileMoonSx
import com.lagradost.cloudstream3.extractors.Vidmoly

@CloudstreamPlugin
class AnimenosubProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Animenosub())
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(Vidmoly())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(wishfast())
    }
}