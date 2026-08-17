package be.saikono.poincare.data.story

data class StoryWordRange(
    val index: Int,
    val start: Int,
    val endExclusive: Int,
    val text: String
)

object StoryTextRanges {
    private val wordRegex = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’\\-][\\p{L}\\p{M}\\p{N}]+)*")

    fun tokenize(text: String): List<StoryWordRange> =
        wordRegex.findAll(text).mapIndexed { index, match ->
            StoryWordRange(
                index = index,
                start = match.range.first,
                endExclusive = match.range.last + 1,
                text = match.value
            )
        }.toList()

    fun wordAtRange(words: List<StoryWordRange>, start: Int, endExclusive: Int): Int? {
        if (start < 0 || endExclusive <= start) return null
        return words.firstOrNull { word ->
            word.start < endExclusive && word.endExclusive > start
        }?.index
    }

    fun estimatedWordDelayMillis(word: StoryWordRange, fullText: String, locale: String): Long {
        val trailing = fullText.substring(word.endExclusive).trimStart().firstOrNull()
        val punctuationPause = when (trailing) {
            '.', '!', '?' -> 180L
            ',', ';', ':' -> 90L
            else -> 0L
        }
        val base = if (locale.normalizedStoryLocale() == "nl-BE") 360L else 330L
        return base + punctuationPause
    }
}
