package com.Animecloud

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimecloudProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Animecloud())
        registerExtractorAPI(AnimeCloudProxy())
    }
}