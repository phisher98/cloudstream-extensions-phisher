package com.likdev256

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class HDMovieProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Hdmovie())
        registerMainAPI(movierulz())
    }
}