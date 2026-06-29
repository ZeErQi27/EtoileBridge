package com.zeerqi27.etoilebridge.core

data class MissingMetadata(
    val reason: String,
    val requiredFields: List<String> = emptyList(),
    val optionalFields: List<String> = emptyList(),
    val candidateSongIds: List<String> = emptyList(),
)
