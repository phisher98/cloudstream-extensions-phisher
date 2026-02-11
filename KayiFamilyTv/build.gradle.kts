version = 1

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    authors = listOf("KaifTaufiq")

    description = "KayiFamilyTv has Turkish Drama and Documentaries with English / Spanish Subtitles."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie", "TvSeries", "Documentary")

    isCrossPlatform = true
}