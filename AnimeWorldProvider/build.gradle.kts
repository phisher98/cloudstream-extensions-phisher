version = 1


cloudstream {
    language = "hi"
    // All of these properties are optional, you can safely remove them

    description = "Please wait for 10seconds to bypass the ads"
    authors = listOf("darkdemon, likdev256")

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
        "Anime",
        "Cartoon"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=anime-world.in&sz=%size%"
}
