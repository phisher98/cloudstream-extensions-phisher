package com.Streamblasters

import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor


class D000d : DoodLaExtractor() {
    override var mainUrl = "https://dood.li"
}

class swhoi : StreamWishExtractor() {
    override var mainUrl = "https://swhoi.com"
}

class wishonly : StreamWishExtractor() {
    override var mainUrl = "https://wishonly.site"
}

class vidhidevip : VidhideExtractor() {
    override var mainUrl = "https://vidhidevip.com"
}

class vidhidepre : VidhideExtractor() {
    override var mainUrl = "https://vidhidepre.com"
}

class jodwish : StreamWishExtractor() {
    override var mainUrl = "https://jodwish.com"
}

class asnwish : StreamWishExtractor() {
    override var mainUrl = "https://asnwish.com"
}
