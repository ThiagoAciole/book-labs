package com.example.booklabs.model

data class File(
    val id: String,
    val title: String,
    val fileName: String,
    val coverUrl: String?,
    val path: String,
    val type: String,
    val favorite: Boolean,
    val markPage: Int
)
