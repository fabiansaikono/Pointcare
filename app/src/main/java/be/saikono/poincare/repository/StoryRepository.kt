package be.saikono.poincare.repository

import android.content.Context
import be.saikono.poincare.data.story.Story
import be.saikono.poincare.data.story.StoryBook
import be.saikono.poincare.data.story.StoryBookCatalogue
import be.saikono.poincare.data.story.StoryCatalogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.Json

interface StoryRepository {
    suspend fun catalogue(): StoryCatalogue
    suspend fun bookCatalogue(): StoryBookCatalogue
    suspend fun story(storyId: String): Story?
    suspend fun book(bookId: String): StoryBook?
}

class AssetStoryRepository(private val context: Context) : StoryRepository {
    private val json = Json {
        ignoreUnknownKeys = true
    }
    @Volatile private var cachedCatalogue: StoryCatalogue? = null
    @Volatile private var cachedBookCatalogue: StoryBookCatalogue? = null

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun catalogue(): StoryCatalogue = withContext(Dispatchers.IO) {
        cachedCatalogue ?: context.assets.open(ASSET_PATH).use { input ->
            json.decodeFromStream<StoryCatalogue>(input)
        }.also { cachedCatalogue = it }
    }

    override suspend fun story(storyId: String): Story? =
        catalogue().stories.firstOrNull { it.id == storyId }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun bookCatalogue(): StoryBookCatalogue = withContext(Dispatchers.IO) {
        cachedBookCatalogue ?: context.assets.open(BOOK_ASSET_PATH).use { input ->
            json.decodeFromStream<StoryBookCatalogue>(input)
        }.also { cachedBookCatalogue = it }
    }

    override suspend fun book(bookId: String): StoryBook? =
        bookCatalogue().books.firstOrNull { it.id == bookId }

    companion object {
        const val ASSET_PATH = "poincare/content/stories/poincare-stories-v1.json"
        const val BOOK_ASSET_PATH = "poincare/content/stories/poincare-story-books-v1.json"
    }
}
