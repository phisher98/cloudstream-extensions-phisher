// use an integer for version numbers
version = 3


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Animes"
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
    iconUrl = "https://anidb.app/images/fav-512.png"
    isCrossPlatform = true
}
