package com.dramafull

import com.fasterxml.jackson.annotation.JsonProperty

data class Home(
    @JsonProperty("current_page")
    val currentPage: Long,
    val data: List<Daum>,
    @JsonProperty("first_page_url")
    val firstPageUrl: String,
    val from: Long,
    @JsonProperty("last_page")
    val lastPage: Long,
    @JsonProperty("last_page_url")
    val lastPageUrl: String,
    val links: List<Link>,
    @JsonProperty("next_page_url")
    val nextPageUrl: String,
    val path: String,
    @JsonProperty("per_page")
    val perPage: Long,
    @JsonProperty("prev_page_url")
    val prevPageUrl: Any?,
    val to: Long,
    val total: Long,
)

data class Daum(
    @JsonProperty("is_adult")
    val isAdult: Long,
    val name: String,
    val slug: String,
    val image: String,
)

data class Link(
    val url: String?,
    val label: String,
    val active: Boolean,
)


data class Search(
    val data: List<Daum>,
    val success: Boolean,
)