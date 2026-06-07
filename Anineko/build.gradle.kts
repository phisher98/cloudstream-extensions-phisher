// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Anime from Anineko"
    authors = listOf("Phisher98")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1
    tvTypes = listOf("Anime", "AnimeMovie", "OVA")
    language = "en"
    iconUrl = "https://anineko.to/img/logo.png?v=4"
    isCrossPlatform = true
}
