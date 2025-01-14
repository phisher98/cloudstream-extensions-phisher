package com.BanglaPlex

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.utils.extractorApis

@CloudstreamPlugin
class BanglaPlexProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Banglaplex())
        registerExtractorAPI(Vectorx())
        registerExtractorAPI(Chillx())
        registerExtractorAPI(Iplayerhls())
        registerExtractorAPI(StreamTape())
        val myOwnExtractors = listOf(Boosterx(),Chillx())

        extractorApis.removeAll { builtInExtractor ->
            builtInExtractor.name == "Chillx" || builtInExtractor.name == "Boosterx"
                    myOwnExtractors.any { ownExtractor -> ownExtractor.mainUrl == builtInExtractor.mainUrl }
        }
        myOwnExtractors.forEach { ownExtractor ->
            registerExtractorAPI(ownExtractor)
        }
    }
}