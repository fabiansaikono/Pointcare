package be.saikono.poincare.domain.curriculum

import android.content.Context
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifest
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifestLoader
import be.saikono.poincare.domain.curriculum.v2.LessonCatalogV2
import be.saikono.poincare.domain.curriculum.v2.LessonCatalogV2Loader
import be.saikono.poincare.domain.curriculum.v2.LessonCatalogV2Validator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LoadedCurriculumCatalog(
    val manifest: CurriculumManifest,
    val lessonCatalogV2: LessonCatalogV2
)

/**
 * Loads the bundled curriculum once per app process.
 *
 * The V2 catalogue is large enough that decoding separate copies in multiple eagerly-created
 * ViewModels can exceed Android's heap limit. The mutex also prevents concurrent first callers
 * from decoding duplicate copies before the cache is populated.
 */
object CurriculumCatalogStore {
    private val loadMutex = Mutex()

    @Volatile
    private var cached: LoadedCurriculumCatalog? = null

    suspend fun load(context: Context): LoadedCurriculumCatalog =
        cached ?: loadMutex.withLock {
            cached ?: withContext(Dispatchers.IO) {
                val manifest = context.assets.open(CurriculumManifestLoader.ASSET_PATH)
                    .use(CurriculumManifestLoader::fromStream)
                val lessonCatalogV2 = LessonCatalogV2Loader.fromAssetProvider(context.assets::open)
                val issues = LessonCatalogV2Validator.validate(lessonCatalogV2)
                check(issues.isEmpty()) {
                    issues.joinToString("\n") { "${it.ruleId} ${it.path}: ${it.message}" }
                }
                LoadedCurriculumCatalog(manifest, lessonCatalogV2)
            }.also { loaded ->
                cached = loaded
            }
        }
}
