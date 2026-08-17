package be.saikono.poincare.data.story

enum class StoryPageLayout { PORTRAIT_STACK, LANDSCAPE_SPLIT }

object StoryResponsiveLayout {
    fun pageLayout(widthDp: Int, heightDp: Int): StoryPageLayout =
        if (widthDp > heightDp) StoryPageLayout.LANDSCAPE_SPLIT else StoryPageLayout.PORTRAIT_STACK

    fun libraryUsesCompactCards(widthDp: Int): Boolean = widthDp < 560
}
