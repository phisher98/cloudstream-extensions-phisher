dependencies {
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
}
// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "KimCartoon - Watch cartoons in high quality"
    authors = listOf("HindiProviders,int3debug")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 0

    tvTypes = listOf(
        "Cartoon"
    )

    requiresResources = true
    language = "en"

    // random cc logo i found
    iconUrl = "https://kimcartoon.li/Content/images/logo.png"
}

android {
    buildFeatures {
        viewBinding = true
    }
}
