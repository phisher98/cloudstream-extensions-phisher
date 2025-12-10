// use an integer for version numbers
version = 2

cloudstream {
    // All of these properties are optional, you can safely remove any of them.

    language = "hi"
    authors = listOf("Phisher98")

    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta-only
     **/
    status = 1 // Will be 3 if unspecified

    tvTypes = listOf(
        "Movie",
        "TvSeries"

    )
    iconUrl = "https://f.pondit.xyz/fibwatch-logo.png"

    isCrossPlatform = false
}
