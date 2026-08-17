package be.saikono.poincare.domain.curriculum.manifest

import be.saikono.poincare.domain.curriculum.CurriculumValidationMode
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumManifestValidatorTest {
    @Test
    fun `canonical full manifest is release valid`() {
        val report = CurriculumManifestValidator.validate(manifest(), CurriculumValidationMode.RELEASE)
        assertTrue(report.issues.joinToString("\n") { "${it.code}: ${it.path} ${it.message}" }, report.isValid)
    }

    @Test
    fun `duplicate taxonomy subcategory is rejected`() {
        val source = manifest()
        val firstCategory = source.categories.first()
        val broken = source.copy(
            categories = source.categories.mapIndexed { index, category ->
                if (index == 1) category.copy(
                    subcategories = category.subcategories + firstCategory.subcategories.first()
                ) else category
            }
        )
        val report = CurriculumManifestValidator.validate(broken)
        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.code == "duplicate_manifest_subcategory" })
    }

    @Test
    fun `lesson unit must map to the manifest taxonomy`() {
        val source = manifest()
        val unit = source.lessonCatalog.units.first()
        val broken = source.copy(
            lessonCatalog = source.lessonCatalog.copy(
                units = source.lessonCatalog.units.map {
                    if (it.id == unit.id) it.copy(subcategoryId = "unknown_subcategory") else it
                }
            )
        )
        val report = CurriculumManifestValidator.validate(broken)
        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.code == "unmapped_lesson_subcategory" })
    }

    @Test
    fun `hybrid curriculum must declare parent support`() {
        val source = manifest()
        val entry = source.roadmapEntries.first {
            it.deliveryMode == CurriculumDeliveryMode.HYBRID_APP_PARENT
        }
        val broken = source.copy(
            roadmapEntries = source.roadmapEntries.map {
                if (it.id == entry.id) it.copy(
                    parentSupport = be.saikono.poincare.domain.curriculum.ParentSupportLevel.NONE
                ) else it
            }
        )
        val report = CurriculumManifestValidator.validate(broken)
        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.code == "hybrid_without_parent_support" })
    }

    @Test
    fun `outside app responsibility cannot create a fake lesson unit`() {
        val source = manifest()
        val external = source.roadmapEntries.first {
            it.deliveryMode == CurriculumDeliveryMode.PARENT_TEACHER_RESPONSIBILITY
        }
        val broken = source.copy(
            roadmapEntries = source.roadmapEntries.map {
                if (it.id == external.id) it.copy(
                    unitId = source.lessonCatalog.units.first().id
                ) else it
            }
        )
        val report = CurriculumManifestValidator.validate(broken)
        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.code == "external_entry_with_unit" })
    }

    @Test
    fun `every full catalogue unit must be linked exactly once`() {
        val source = manifest()
        val removed = source.roadmapEntries.first { it.unitId != null }
        val broken = source.copy(
            roadmapEntries = source.roadmapEntries.filterNot { it.id == removed.id }
        )
        val report = CurriculumManifestValidator.validate(broken)
        assertFalse(report.isValid)
        assertTrue(report.errors.any {
            it.code == "full_catalogue_linkage_mismatch" ||
                it.code == "unexpected_full_roadmap_size"
        })
    }

    private fun manifest(): CurriculumManifest = assetFile(
        "src/main/assets/${CurriculumManifestLoader.ASSET_PATH}"
    ).inputStream().use(CurriculumManifestLoader::fromStream)

    private fun assetFile(relativePath: String): File {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Missing test asset: $relativePath")
    }
}
