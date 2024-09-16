version = 20

cloudstream {
    language = "hi"
    authors = listOf("Hindi Provider")
    description = "It contains AnimeDekho,Onepace and HindisubAnime"
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

    iconUrl = "https://animedekho.net/wp-content/uploads/2023/07/AnimeDekho-Logo-300x-1.png"
}
