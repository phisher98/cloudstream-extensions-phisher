// use an integer for version numbers
version = 8


cloudstream {
    description = "Watch Movies & TvSeries (Multi-Lang/Audio)"
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
        "Movie",
        "TvSeries",
    )
    language = "en"
    iconUrl= "https://www.google.com/s2/favicons?domain=cinemacity.cc&sz=%size%"

    isCrossPlatform = false
}
