package com.phisher98

import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.AsianLoadExtractor

class dlions : VidhideExtractor() {
    override var name = "Dlions"
    override var mainUrl = "https://dlions.pro"
}

class MixDropSi : MixDrop() {
    override var mainUrl = "https://mixdrop.si"
}

class DramacoolExtractor : StreamWishExtractor() {
    override var name = "Dramacool"
    override var mainUrl = "https://dramacool.men"
}

class asianload : AsianLoadExtractor() {
    override var name = "AsianLoad"
    override var mainUrl = "https://asianload.cfd/"
}

class dhtpre : StreamWishExtractor() {
    override var name = "EarnVids"
    override var mainUrl = "https://dhtpre.com"
}

class nikaplayerr : StreamWishExtractor() {
    override var name = "EarnVids"
    override var mainUrl = "https://nikaplayerr.com"
}

class peytonepre : StreamWishExtractor() {
    override var name = "EarnVids"
    override var mainUrl = "https://peytonepre.com"
}
