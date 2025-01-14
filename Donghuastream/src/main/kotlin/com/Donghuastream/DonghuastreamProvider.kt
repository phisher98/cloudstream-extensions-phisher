package com.Donghuastream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Dailymotion

@CloudstreamPlugin
class DonghuastreamProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Donghuastream())
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(waaw())
        registerExtractorAPI(wishfast())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(Dailymotion())
        registerExtractorAPI(Ultrahd())
        registerExtractorAPI(Rumble())
    }
}