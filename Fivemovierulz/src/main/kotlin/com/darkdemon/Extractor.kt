package com.darkdemon
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor

class Filelion : VidhideExtractor() {
    override var mainUrl = "https://vidhidepro.com"
}


class mivalyo : VidhideExtractor() {
    override var name = "Mivalyo"
    override var mainUrl = "https://mivalyo.com"
}

class StreamwishHG : StreamWishExtractor() {
    override val mainUrl = "https://hglink.to"
}