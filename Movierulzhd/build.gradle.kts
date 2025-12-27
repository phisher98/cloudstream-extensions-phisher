// use an integer for version numbers
version = 153


cloudstream {
    language = "hi"
    // All of these properties are optional, you can safely remove them

     description = "Includes: Hdmovie2,hdmovie6"
     authors = listOf("Phisher98,hexated")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries",
        "Movie",
    )

    iconUrl = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/master/Movierulzhd/faviconV2.png"

    isCrossPlatform = true
}
