version = 61

cloudstream {
    language = "hi"
    authors = listOf("Hindi Provider")
    description = "Includes AnimeDekho,OnePace(DUB,SUB) and HindiSubAnime"
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

    iconUrl = "https://animedekho.app/wp-content/uploads/2023/07/AnimeDekho-Logo-300x-1.png"

    isCrossPlatform = true
}
