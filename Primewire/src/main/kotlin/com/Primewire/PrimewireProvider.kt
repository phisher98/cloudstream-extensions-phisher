package com.Primewire

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class PrimewireProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Primewire())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(MixDropag())
    }
}
