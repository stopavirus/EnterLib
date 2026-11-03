package app.enterlib.core

object Classifier {
    fun categorize(genres: List<String>, language: String?): Category {
        val g = genres.map { it.trim().lowercase() }
        fun has(vararg k: String) = k.any { it in g }
        return when {
            has("documentary", "documentaries") -> Category.DOCUMENTARY
            has("anime") -> Category.ANIME
            has("animation") && language.equals("Japanese", ignoreCase = true) -> Category.ANIME
            has("kids", "children", "family") -> Category.KIDS
            has("animation") -> Category.ANIMATION
            has("short", "short film") -> Category.SHORT
            has("music", "concert", "musical") -> Category.MUSIC
            else -> Category.GENERAL
        }
    }
}
