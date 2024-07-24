// use an integer for version numbers
version = 1


cloudstream {
    language = "hi"
    // All of these properties are optional, you can safely remove them

     description = "Includes: Watchomovies(NS*W), Max resolution is 720p in both extensions."
     authors = listOf("Hexated")

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

    iconUrl = "https://www.google.com/s2/favicons?domain=yomovies.rest&sz=%size%"
}
