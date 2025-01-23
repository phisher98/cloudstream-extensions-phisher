package com.hikaritv

data class HomePage(
    val count: Long,
    val page: Page,
    val html: String,
)

data class Page(
    val status: Boolean,
    val totalPages: Long,
)

data class Load(
    val status: Boolean,
    val html: String,
    val totalItems: Long,
)


data class TypeRes(
    val status: Boolean,
    val embedFirst: String,
    val html: String,
)

