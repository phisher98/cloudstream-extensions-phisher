// use an integer for version numbers
version = 8


cloudstream {
    language = "de"
    // All of these properties are optional, you can safely remove them

    description = "Include: Serienstream"
    authors = listOf("Phisher98,Hexated")

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
        "OVA",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=aniworld.to&sz=%size%"
}