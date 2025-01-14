package com.Animekhor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.EmturbovidExtractor
import com.lagradost.cloudstream3.extractors.Mp4Upload

@CloudstreamPlugin
class AnimenosubProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Animekhor())
        registerExtractorAPI(embedwish())
        registerExtractorAPI(Filelions())
        registerExtractorAPI(VidHidePro5())
        registerExtractorAPI(Swhoi())
        registerExtractorAPI(EmturbovidExtractor())
        registerExtractorAPI(Dailymotion())
        registerExtractorAPI(Rumble())
        registerExtractorAPI(Mp4Upload())
    }
}