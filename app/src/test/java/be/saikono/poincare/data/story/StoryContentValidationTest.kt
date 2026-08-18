package be.saikono.poincare.data.story

import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryContentValidationTest {
    private val catalogue: StoryCatalogue by lazy {
        val asset = File("src/main/assets/poincare/content/stories/poincare-stories-v1.json")
        Json.decodeFromString(asset.readText(Charsets.UTF_8))
    }
    private val bookCatalogue: StoryBookCatalogue by lazy {
        val asset = File("src/main/assets/poincare/content/stories/poincare-story-books-v1.json")
        Json.decodeFromString(asset.readText(Charsets.UTF_8))
    }

    @Test
    fun catalogue_has_one_hundred_fifty_two_unique_complete_stories() {
        assertEquals(1, catalogue.schemaVersion)
        assertEquals(152, catalogue.stories.size)
        assertEquals(152, catalogue.stories.map(Story::id).toSet().size)
        catalogue.stories.forEach { story ->
            assertTrue(story.grade in 1..4)
            assertTrue(story.difficulty in 1..3)
            assertTrue(story.aviLevel in setOf("Start", "M3", "E3", "M4", "E4", "M5", "E5", "M6", "E6"))
            assertTrue(story.estimatedMinutes > 0)
            assertEquals("Poincaré", story.author)
            assertEquals("Poincaré", story.illustrator)
            assertEquals("Original Poincaré content", story.license)
            assertEquals(setOf("nl-BE", "fr-BE", "en-US"), story.translations.keys)
            assertTrue(story.pages.isNotEmpty())
            assertEquals(story.pages.size, story.pages.map(StoryPage::id).toSet().size)
            story.pages.forEach { page ->
                assertEquals(setOf("nl-BE", "fr-BE", "en-US"), page.visibleText.keys)
                assertTrue(page.visibleText.values.all(String::isNotBlank))
                page.illustrationAsset?.let { asset ->
                    assertTrue(asset.endsWith(".png") || asset.endsWith(".webp"))
                }
            }
        }
    }

    @Test
    fun avi_levels_match_the_requested_grade_distribution_and_fixed_nova_levels() {
        val distribution = catalogue.stories.groupingBy { it.grade to it.aviLevel }.eachCount()
        assertEquals(9, distribution[1 to "Start"])
        assertEquals(21, distribution[1 to "M3"])
        assertEquals(25, distribution[1 to "E3"])
        assertEquals(13, distribution[2 to "M4"])
        assertEquals(23, distribution[2 to "E4"])
        assertEquals(13, distribution[3 to "M5"])
        assertEquals(24, distribution[3 to "E5"])
        assertEquals(12, distribution[4 to "M6"])
        assertEquals(12, distribution[4 to "E6"])

        catalogue.stories.filter { it.id.startsWith("nova_g1_") }.forEach { assertEquals("E3", it.aviLevel) }
        catalogue.stories.filter { it.id.startsWith("nova_g2_") }.forEach { assertEquals("E4", it.aviLevel) }
        catalogue.stories.filter { it.id.startsWith("nova_g3_") }.forEach { assertEquals("E5", it.aviLevel) }
    }

    @Test
    fun catalogue_matches_required_paging_audio_and_questions() {
        val stories = catalogue.stories.associateBy(Story::id)
        assertTrue(stories.getValue("g1-sam-en-de-maan").pages.size in 8..10)
        assertTrue(stories.getValue("g1-pip-zoekt-een-vis").pages.size in 8..10)
        assertTrue(stories.getValue("g1-de-raket-van-noor").pages.size in 10..12)
        assertTrue(stories.getValue("g2-de-robot-die-niet-kon-slapen").pages.size in 10..14)
        assertTrue(stories.getValue("g2-het-geheim-onder-de-school").pages.size in 12..16)
        assertEquals(3, stories.getValue("g2-het-geheim-onder-de-school").questions.size)
        assertEquals(5, stories.getValue("g3-de-kaart-van-planeet-nox").questions.size)
        val nox = stories.getValue("g3-de-kaart-van-planeet-nox")
        listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
            val wordCount = StoryTextRanges.tokenize(nox.pages.joinToString(" ") { it.text(locale) }).size
            assertTrue("$locale Nox word count was $wordCount", wordCount in 800..1500)
        }
        assertEquals(setOf(1, 2, 3, 4), nox.pages.mapNotNull(StoryPage::chapter).toSet())
        assertTrue(stories.getValue("g1-sam-en-de-maan").hasVoiceOver)
        assertTrue(stories.filterValues { it.grade == 1 && !it.id.startsWith("robbie_") }.values.all(Story::hasVoiceOver))
    }

    @Test
    fun first_five_grade_one_books_have_complete_unique_content() {
        assertEquals(1, bookCatalogue.schemaVersion)
        assertEquals(19, bookCatalogue.books.size)
        assertEquals(19, bookCatalogue.books.map(StoryBook::id).toSet().size)
        val firstFive = bookCatalogue.books.filter { it.grade == 1 && it.order in 1..5 }
        assertEquals(5, firstFive.size)
        assertEquals(listOf(1, 2, 3, 4, 5), firstFive.map(StoryBook::order))
        val stories = catalogue.stories.associateBy(Story::id)
        val linkedIds = firstFive.flatMap(StoryBook::storyIds)
        assertEquals(20, linkedIds.size)
        assertEquals(20, linkedIds.toSet().size)
        val linkedStories = linkedIds.map(stories::getValue)
        assertTrue(linkedStories.all { it.grade == 1 && it.pages.size == 10 && it.hasVoiceOver })
        val images = linkedStories.flatMap { story -> story.pages.mapNotNull(StoryPage::illustrationAsset) }
        assertEquals(200, images.size)
        assertEquals(200, images.toSet().size)
        firstFive.forEach { book ->
            assertEquals(1, book.grade)
            assertEquals(4, book.storyIds.size)
            assertEquals(setOf("nl-BE", "fr-BE", "en-US"), book.translations.keys)
            assertTrue(File("src/main/assets", book.coverAsset).isFile)
        }
    }

    @Test
    fun all_question_indices_and_asset_paths_are_valid() {
        val root = File("src/main/assets")
        catalogue.stories.forEach { story ->
            story.questions.forEach { question ->
                listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                    assertEquals(3, question.options(locale).size)
                    assertTrue(question.correctIndex in question.options(locale).indices)
                }
            }
            assertTrue(
                story.coverAsset.startsWith("poincare/content/stories/images/") ||
                    story.coverAsset.startsWith("poincare/content/stories/book-covers/")
            )
            assertTrue("Missing story cover: ${story.coverAsset}", File(root, story.coverAsset).isFile)
            story.pages.forEach { page ->
                page.illustrationAsset?.let { asset ->
                    val target = File(root, asset)
                    assertTrue("Missing story image: $target", target.isFile)
                }
            }
        }
    }

    @Test
    fun nova_space_trilogy_has_expected_progression_and_layout() {
        val books = bookCatalogue.books.associateBy(StoryBook::id)
        val stories = catalogue.stories.associateBy(Story::id)
        val trilogyBooks = listOf(
            books.getValue("nova_space_01_ready_for_launch_g1"),
            books.getValue("nova_space_02_journey_through_space_g2"),
            books.getValue("nova_space_03_goodbye_nina_g3")
        )

        assertEquals(listOf(1, 2, 3), trilogyBooks.map(StoryBook::grade))
        assertEquals(listOf(8, 10, 12), trilogyBooks.map { it.storyIds.size })
        assertTrue(trilogyBooks.all { it.translations.keys == setOf("nl-BE", "fr-BE", "en-US") })

        val storyGroups = trilogyBooks.map { book -> book.storyIds.map(stories::getValue) }
        assertEquals(listOf(98, 199, 282), storyGroups.map { group -> group.sumOf { it.pages.size } })
        assertEquals(listOf(16, 20, 24), storyGroups.map { group -> group.sumOf { it.questions.size } })
        assertTrue(storyGroups.flatten().all(Story::hasVoiceOver))

        storyGroups[0].forEach { chapter ->
            assertTrue(chapter.pages.size in 10..15)
            assertEquals("E3", chapter.aviLevel)
            assertTrue(chapter.pages.all { it.illustrationAsset != null })
            assertEquals(4, chapter.pages.mapNotNull(StoryPage::illustrationAsset).toSet().size)
        }
        storyGroups[1].forEach { chapter ->
            assertTrue(chapter.pages.size in 15..20)
            assertEquals("E4", chapter.aviLevel)
            assertTrue(chapter.pages.all { it.illustrationAsset != null })
            assertEquals(4, chapter.pages.mapNotNull(StoryPage::illustrationAsset).toSet().size)
        }

        storyGroups[2].forEach { chapter ->
            assertTrue(chapter.pages.size in 20..30)
            assertEquals("E5", chapter.aviLevel)
            assertEquals(2, chapter.pages.mapNotNull(StoryPage::illustrationAsset).toSet().size)
            assertTrue(chapter.pages.all { it.visibleText.values.all(String::isNotBlank) })
        }

        val illustratedPages = storyGroups.flatten().flatMap(Story::pages).mapNotNull(StoryPage::illustrationAsset)
        assertEquals(321, illustratedPages.size)
        assertEquals(96, illustratedPages.toSet().size)
    }

    @Test
    fun story_series_are_named_ordered_and_robbie_books_are_split_into_chapters() {
        val expectedTitles = mapOf(
            "series.kleine-verhaaltjes" to "Kleine verhaaltjes",
            "series.nova" to "Nova",
            "series.robbie" to "Robbie"
        )
        expectedTitles.forEach { (seriesId, title) ->
            val books = bookCatalogue.books.filter { it.seriesId == seriesId }
            assertTrue(books.isNotEmpty())
            assertTrue(books.all { it.seriesTitle?.text("nl-BE") == title })
            assertEquals(books.mapNotNull(StoryBook::bookNumber).sorted(), books.mapNotNull(StoryBook::bookNumber))
            assertTrue(books.all { !it.aviLevel.isNullOrBlank() })
        }

        val stories = catalogue.stories.associateBy(Story::id)
        val robbieBooks = bookCatalogue.books.filter { it.seriesId == "series.robbie" }
        assertEquals(8, robbieBooks.size)
        robbieBooks.forEach { book ->
            assertEquals(12, book.storyIds.size)
            val chapters = book.storyIds.map(stories::getValue)
            assertEquals((1..12).toList(), chapters.mapNotNull(Story::chapterNumber))
            assertEquals(150, chapters.sumOf { it.pages.size })
            assertTrue(chapters.all { it.aviLevel == book.aviLevel })
        }
    }
}
