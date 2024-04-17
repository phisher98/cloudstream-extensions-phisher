// use an integer for version numbers
version = 2


cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

//    description = "Lorem Ipsum"
    authors = listOf("Horis")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AsianDrama",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=kshow123online.com&sz=%size%"
}
