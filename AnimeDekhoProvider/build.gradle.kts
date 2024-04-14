version = 5

cloudstream {
    language = "hi"
    authors = listOf("anon")
    description = "YOU HAVE SKIP ADS on the SITE each 24 hours, Hindi dubbed cartoons if No Links Error"
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
