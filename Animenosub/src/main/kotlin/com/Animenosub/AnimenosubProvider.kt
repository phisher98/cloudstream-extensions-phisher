package com.Animenosub

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimenosubProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Animenosub())
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(waaw())
        registerExtractorAPI(Filemoonsxx())
        registerExtractorAPI(wishfast())
    }
}