version = 2


cloudstream {
    language = "hi"
    // All of these properties are optional, you can safely remove them

    description = "This website support English/Hindi/Kannada/Malayalam/Tamil/Telugu live channels and Hindi Old Movies/Malayalam Movies \n For language in Sports: Please check background image[country flag] of the episode"
    authors = listOf("HindiProviders")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Live"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=mhdmaxtv.com&sz=%size%"
}
