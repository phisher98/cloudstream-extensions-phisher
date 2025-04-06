// use an integer for version numbers
version = 3


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "KimCartoon - Watch cartoons in high quality"
    authors = listOf("HindiProviders")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf(
        "Cartoon"
    )

    requiresResources = true
    language = "en"

    iconUrl = "https://img.cartooncdn.xyz/themes/kim/images/kimcartoon.png"

    isCrossPlatform = true
}
