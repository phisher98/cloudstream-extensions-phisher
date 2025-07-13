// use an integer for version numbers
version = 1


cloudstream {
    language = "hi"
    // All of these properties are optional, you can safely remove them
    description = "Indian Movies/Series/Kdrama(Hindi Dubbed)"
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

    iconUrl = "https://www.google.com/s2/favicons?domain=www.mxplayer.in&sz=%size%"

    isCrossPlatform = true
}
