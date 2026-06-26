package com.zeerqi27.etoilebridge.core

import java.io.File

data class DifficultyInfo(
    val ratingClass: Int,
    val fileName: String,
    val label: String,
)

object DifficultyMapper {
    private val labels = mapOf(
        0 to "Past",
        1 to "Present",
        2 to "Future",
        3 to "Beyond",
        4 to "Eternal",
    )

    fun fromAffFile(file: File): DifficultyInfo? {
        val ratingClass = file.nameWithoutExtension.toIntOrNull() ?: return null
        return if (ratingClass in 0..4 && file.extension.equals("aff", ignoreCase = true)) {
            DifficultyInfo(ratingClass, file.name, labelFor(ratingClass))
        } else {
            null
        }
    }

    fun labelFor(ratingClass: Int): String = labels[ratingClass] ?: "Future"

    fun displayName(ratingClass: Int, rating: Int?, ratingPlus: Boolean?): String {
        val prefix = labelFor(ratingClass)
        if (rating == null || rating <= 0) return "$prefix ?"
        return "$prefix $rating${if (ratingPlus == true) "+" else ""}"
    }

    fun chartConstant(rating: Int?, ratingPlus: Boolean?): Float? {
        if (rating == null || rating < 0) return null
        return rating + if (ratingPlus == true) 0.7f else 0f
    }
}
