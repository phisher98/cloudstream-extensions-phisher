package com.kissasian

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.extractorApis

@CloudstreamPlugin
class KissasianProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Kissasian())
        registerMainAPI(Kissasianv2())
        registerMainAPI(Dramacool())
        registerExtractorAPI(embedwish())
        registerExtractorAPI(dwish())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(Kswplayer())
        registerExtractorAPI(Plcool1())
        registerExtractorAPI(MixDropPs())
    }
}
