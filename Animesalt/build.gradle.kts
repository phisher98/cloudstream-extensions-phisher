version = 1


cloudstream {
    language = "hi"
    // All of these properties are optional, you can safely remove them

    description = "Anime/Cartoon in Hindi"
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
        "AnimeMovie",
        "Anime",
        "Cartoon"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=animesalt.cc&sz=%size%"

    isCrossPlatform = true
}
