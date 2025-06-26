// use an integer for version numbers
version = 4


cloudstream {
    language = "hi"
    // All of these properties are optional, you can safely remove them

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
        "TvSeries"
    )

    iconUrl = "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://desicinemas.to&size=%size%"

    isCrossPlatform = true
}
