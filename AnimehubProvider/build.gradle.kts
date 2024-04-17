// use an integer for version numbers
version = 3


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
        "AnimeMovie",
        "Anime",
        "OVA",
    )

    iconUrl = "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=http://123animehub.cc&size=%size%"
}
