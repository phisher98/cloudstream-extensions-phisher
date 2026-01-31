package com.ohli24

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf

class Korebishi : OHLI24() {
    override var mainUrl = "https://m131.kotbc2.com"
    override var name = "Korebishi"
    override var lang = "ko"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie,TvType.TvSeries,TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "bbs/board.php?bo_table=drama" to "드라마",
        "bbs/board.php?bo_table=movie" to "영화",
        "bbs/board.php?bo_table=enter" to "예능/시사",
    )
}