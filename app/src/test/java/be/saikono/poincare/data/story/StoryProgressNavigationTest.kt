package be.saikono.poincare.data.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StoryProgressNavigationTest {
    @Test
    fun identity_is_profile_story_and_normalized_locale_specific() {
        val base = storyProgressIdentity("profile-a", "story-a", "nl_BE")
        assertEquals("profile-a::story-a::nl-BE", base)
        assertNotEquals(base, storyProgressIdentity("profile-b", "story-a", "nl-BE"))
        assertNotEquals(base, storyProgressIdentity("profile-a", "story-b", "nl-BE"))
        assertNotEquals(base, storyProgressIdentity("profile-a", "story-a", "fr-BE"))
    }

    @Test
    fun navigation_never_leaves_page_bounds() {
        assertEquals(0, StoryNavigation.completionPage())
        assertEquals(0, StoryNavigation.previousPage(0, 9))
        assertEquals(8, StoryNavigation.nextPage(8, 9))
        assertEquals(0, StoryNavigation.clampPage(-20, 9))
        assertEquals(8, StoryNavigation.clampPage(20, 9))
        assertEquals(0, StoryNavigation.clampPage(4, 0))
    }

    @Test
    fun responsive_layout_switches_for_phone_and_tablet_orientations() {
        assertEquals(StoryPageLayout.PORTRAIT_STACK, StoryResponsiveLayout.pageLayout(412, 915))
        assertEquals(StoryPageLayout.LANDSCAPE_SPLIT, StoryResponsiveLayout.pageLayout(915, 412))
        assertEquals(StoryPageLayout.PORTRAIT_STACK, StoryResponsiveLayout.pageLayout(800, 1280))
        assertEquals(StoryPageLayout.LANDSCAPE_SPLIT, StoryResponsiveLayout.pageLayout(1280, 800))
        assertEquals(true, StoryResponsiveLayout.libraryUsesCompactCards(412))
        assertEquals(false, StoryResponsiveLayout.libraryUsesCompactCards(800))
    }

    @Test
    fun long_text_uses_two_balanced_pages_when_the_continuation_has_no_image() {
        val sentence = "Robbie zoekt een veilig pad door het doolhof. "
        val text = sentence.repeat(24).trim()

        val parts = StoryReadingPagePlanner.paginate(
            text = text,
            grade = 4,
            nextPageHasIllustration = false
        )

        assertEquals(2, parts.size)
        assertTrue(parts.all { it.text.length <= StoryReadingPagePlanner.singlePageCharacterCapacity(4) })
        assertEquals(text, parts.joinToString(" ") { it.text })
        assertEquals(0, parts.first().startOffset)
        assertEquals(text.length, parts.last().endOffset)
    }

    @Test
    fun illustrated_next_page_keeps_text_together_and_reduces_image_space() {
        val short = StoryReadingPagePlanner.imageTextWeights(1, 40, landscape = false)
        val long = StoryReadingPagePlanner.imageTextWeights(1, 220, landscape = false)
        val text = "Nova ontdekt een nieuw sterrenstelsel. ".repeat(20)

        val parts = StoryReadingPagePlanner.paginate(
            text = text,
            grade = 3,
            nextPageHasIllustration = true
        )

        assertEquals(1, parts.size)
        assertTrue(long.image < short.image)
        assertTrue(long.text > short.text)
        assertEquals(1f, long.image + long.text, .0001f)
    }
}
