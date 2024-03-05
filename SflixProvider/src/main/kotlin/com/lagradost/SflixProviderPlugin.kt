package com.lagradost

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class SflixProviderPlugin : Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(SflixProvider())
        registerMainAPI(SolarmovieProvider())
        registerMainAPI(DopeboxProvider())
        registerMainAPI(ZoroProvider())
        registerMainAPI(HDTodayProvider())
        //registerMainAPI(TwoEmbedProvider())
        //2embed is in our memories now , fu*k a.c.e
    }
}