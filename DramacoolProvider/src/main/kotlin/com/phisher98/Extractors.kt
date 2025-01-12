package com.phisher98

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.helper.GogoHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor

class dlions : VidhideExtractor() {
    override var name = "Dlions"
    override var mainUrl = "https://dlions.pro"
}

class MixDropSi : MixDrop() {
    override var mainUrl = "https://mixdrop.si"
}


class Dramacool : StreamWishExtractor() {
    override var name = "Dramacool"
    override var mainUrl = "https://dramacool.men"
}

class dhtpre : StreamWishExtractor() {
    override var mainUrl = "https://dhtpre.com"
}

class nikaplayerr : StreamWishExtractor() {
    override var mainUrl = "https://nikaplayerr.com"
}

class peytonepre : StreamWishExtractor() {
    override var mainUrl = "https://peytonepre.com"
}
