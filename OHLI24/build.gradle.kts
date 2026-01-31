// use an integer for version numbers
version = 3

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Anime and movies with Korean subtitles only (no Korean audio)"
    authors = listOf("Phisher98")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Movie",
    )

    requiresResources = true
    language = "ko"

    iconUrl = "https://ani.ohli24.com/img/logo@2x.png"

    isCrossPlatform = false
}