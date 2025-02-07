package com.Streamblasters

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class StreamblastersProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Streamblasters())
        registerExtractorAPI(D000d())
        registerExtractorAPI(jodwish())
        registerExtractorAPI(asnwish())
        registerExtractorAPI(vidhidevip())
        registerExtractorAPI(vidhidepre())
        registerExtractorAPI(swhoi())
        registerExtractorAPI(wishonly())
    }
}
