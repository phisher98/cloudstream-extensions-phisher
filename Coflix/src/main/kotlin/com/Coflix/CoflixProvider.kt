package com.Coflix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Vidguardto2
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.extractors.Wishonly

@CloudstreamPlugin
class CoflixProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Coflix())
        registerExtractorAPI(Voe())
        registerExtractorAPI(Wishonly())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(waaw())
        registerExtractorAPI(VidHideplus())
        registerExtractorAPI(darkibox())
        registerExtractorAPI(Vidguardto2())
        registerExtractorAPI(Vidmoly())
        registerExtractorAPI(Videzz())
    }
}