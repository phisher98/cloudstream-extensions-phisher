// use an integer for version numbers
version = 2


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
    iconUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/Cinemacity.png"

    isCrossPlatform = false
}
