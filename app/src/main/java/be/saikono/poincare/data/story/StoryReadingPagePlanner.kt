package be.saikono.poincare.data.story

import kotlin.math.abs

data class StoryTextPagePart(
    val text: String,
    val startOffset: Int,
    val endOffset: Int
)

data class StoryImageTextWeights(
    val image: Float,
    val text: Float
)

/**
 * Plans child-facing reading pages before Compose renders them.
 *
 * Long source text may become two consecutive display pages only when the
 * continuation can remain illustration-free. If the next source page already
 * needs an illustration, the source page stays intact and the illustration is
 * given less space instead.
 */
object StoryReadingPagePlanner {
    fun paginate(
        text: String,
        grade: Int,
        nextPageHasIllustration: Boolean
    ): List<StoryTextPagePart> {
        if (text.isBlank()) return listOf(StoryTextPagePart(text, 0, text.length))
        val capacity = singlePageCharacterCapacity(grade)
        if (text.length <= capacity || nextPageHasIllustration) {
            return listOf(StoryTextPagePart(text, 0, text.length))
        }

        val split = balancedBreak(text)
        val first = trimmedPart(text, 0, split)
        val second = trimmedPart(text, split, text.length)
        return if (first.text.isBlank() || second.text.isBlank()) {
            listOf(StoryTextPagePart(text, 0, text.length))
        } else {
            listOf(first, second)
        }
    }

    fun imageTextWeights(
        grade: Int,
        visibleTextLength: Int,
        landscape: Boolean
    ): StoryImageTextWeights {
        val pressure = (visibleTextLength.toFloat() / singlePageCharacterCapacity(grade))
            .coerceIn(0f, 1f)
        val maximumImageShare = if (landscape) .52f else .62f
        val minimumImageShare = if (landscape) .30f else .34f
        val imageShare = maximumImageShare - (maximumImageShare - minimumImageShare) * pressure
        return StoryImageTextWeights(image = imageShare, text = 1f - imageShare)
    }

    fun singlePageCharacterCapacity(grade: Int): Int = when (grade) {
        1 -> 150
        2 -> 360
        3 -> 480
        else -> 620
    }

    private fun balancedBreak(text: String): Int {
        val target = text.length / 2
        val lower = (text.length * .35f).toInt()
        val upper = (text.length * .65f).toInt()
        val sentenceBreaks = (lower until upper).filter { index ->
            text[index] in setOf('.', '!', '?') && text.getOrNull(index + 1)?.isWhitespace() == true
        }.map { it + 1 }
        sentenceBreaks.minByOrNull { abs(it - target) }?.let { return it }

        val paragraphBreaks = (lower until upper).filter { index ->
            text[index] == '\n'
        }
        paragraphBreaks.minByOrNull { abs(it - target) }?.let { return it }

        val wordBreaks = (lower until upper).filter { index -> text[index].isWhitespace() }
        return wordBreaks.minByOrNull { abs(it - target) } ?: target
    }

    private fun trimmedPart(text: String, rawStart: Int, rawEnd: Int): StoryTextPagePart {
        var start = rawStart.coerceIn(0, text.length)
        var end = rawEnd.coerceIn(start, text.length)
        while (start < end && text[start].isWhitespace()) start += 1
        while (end > start && text[end - 1].isWhitespace()) end -= 1
        return StoryTextPagePart(text.substring(start, end), start, end)
    }
}
