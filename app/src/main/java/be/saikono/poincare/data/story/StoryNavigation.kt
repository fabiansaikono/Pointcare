package be.saikono.poincare.data.story

object StoryNavigation {
    fun completionPage(): Int = 0

    fun previousPage(current: Int, pageCount: Int): Int =
        if (pageCount <= 0) 0 else (current - 1).coerceIn(0, pageCount - 1)

    fun nextPage(current: Int, pageCount: Int): Int =
        if (pageCount <= 0) 0 else (current + 1).coerceIn(0, pageCount - 1)

    fun clampPage(page: Int, pageCount: Int): Int =
        if (pageCount <= 0) 0 else page.coerceIn(0, pageCount - 1)
}
