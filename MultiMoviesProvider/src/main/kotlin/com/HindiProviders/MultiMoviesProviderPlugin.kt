package com.Phisher98

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.XStreamCdn
import com.lagradost.cloudstream3.extractors.VidHidePro5
import com.lagradost.cloudstream3.extractors.VidHidePro6

@CloudstreamPlugin
class MultiMoviesProviderPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(MultiMoviesProvider())
        registerExtractorAPI(VidHidePro5())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(XStreamCdn())
        registerExtractorAPI(DoodLaExtractor())
        registerExtractorAPI(Animezia())
        registerExtractorAPI(server2())
        registerExtractorAPI(MultimoviesAIO())
        //registerExtractorAPI(GDMirrorbot())
        //registerExtractorAPI(VidhideExtractor())
        registerExtractorAPI(Asnwish())
        registerExtractorAPI(CdnwishCom())
        registerExtractorAPI(Strwishcom())
        registerExtractorAPI(VidHidePro6())
        }
}
