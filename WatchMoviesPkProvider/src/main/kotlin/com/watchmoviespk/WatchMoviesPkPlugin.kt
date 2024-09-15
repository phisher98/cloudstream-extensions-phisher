package com.watchmoviespk

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class WatchMoviesPkPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(WatchMoviesPkProvider())
        registerExtractorAPI(EmbedPk())
        registerExtractorAPI(TapeAdvertisement())
    }
}
