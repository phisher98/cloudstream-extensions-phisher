version = 7

cloudstream {
    language = "hi"
    authors = listOf("Hindi Provider")
    description = "Anime Dekho and Animedekho 2 (if 1st one didn't work second need to go to website to bypass ad to work for 24 hrs after that)"
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
        "Anime",
        "Cartoon"
    )

    iconUrl = "https://animedekho.com/wp-content/uploads/2023/07/AnimeDekho-Logo-300x-1.png"
}
