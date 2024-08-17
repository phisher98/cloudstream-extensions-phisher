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
        addExtractor(Vidstream2("https://asianembed.io"))
    }
    private fun addExtractor(element: ExtractorApi) {
        element.sourcePlugin = __filename
        extractorApis.add(0, element)
    }
}
