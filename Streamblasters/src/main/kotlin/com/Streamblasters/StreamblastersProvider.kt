package com.Streamblasters

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class StreamblastersProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Streamblasters())
        registerExtractorAPI(D000d())
        registerExtractorAPI(jodwish())
        registerExtractorAPI(asnwish())
        registerExtractorAPI(vidhidevip())
        registerExtractorAPI(swhoi())
        registerExtractorAPI(strwish())
    }
}
