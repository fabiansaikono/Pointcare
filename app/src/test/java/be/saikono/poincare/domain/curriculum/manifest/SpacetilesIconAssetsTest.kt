package be.saikono.poincare.domain.curriculum.manifest

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacetilesIconAssetsTest {

    @Test
    fun `all curriculum hierarchy nodes have exported Spacetiles icons`() {
        val root = assetRoot()
        val manifest = File(root, CurriculumManifestLoader.ASSET_PATH)
            .inputStream()
            .use(CurriculumManifestLoader::fromStream)
        val items = manifest.categories.flatMap { category -> listOf(category) + category.subcategories }

        assertEquals(62, items.size)
        items.forEach { item ->
            val theme = when (item) {
                is CurriculumCategoryManifest -> item.imageGenerationTheme
                is CurriculumSubcategoryManifest -> item.imageGenerationTheme
                else -> null
            }
            val assetPath = when (item) {
                is CurriculumCategoryManifest -> item.iconAssetPath
                is CurriculumSubcategoryManifest -> item.iconAssetPath
                else -> null
            }
            assertEquals(item.toString(), "SPACETILES", theme)
            assertTrue(item.toString(), !assetPath.isNullOrBlank())
            assertConformingSvg(File(root, requireNotNull(assetPath)))
        }
    }

    @Test
    fun `standalone puzzle experiences have exported Spacetiles icons`() {
        val root = assetRoot()
        val names = listOf(
            "sudoku",
            "math-crosswords",
            "word-seeker",
            "mothership-dock",
            "galaxycon",
            "calculator",
            "constellations",
            "orbit-builder"
        )
        names.forEach { name ->
            assertConformingSvg(File(root, "poincare/curriculum/icons/spacetiles/runtime-puzzle-$name.svg"))
        }
    }

    private fun assertConformingSvg(file: File) {
        assertTrue("Missing ${file.path}", file.isFile)
        val svg = file.readText()
        assertTrue(file.path, "data-theme=\"SPACETILES\"" in svg)
        assertTrue(file.path, "stroke=\"#ffffff\"" in svg)
        assertTrue(file.path, "stroke-width=\"24\"" in svg)
        assertTrue(file.path, "fill=\"none\"" in svg)
    }

    private fun assetRoot(): File = listOf(
        File("src/main/assets"),
        File("app/src/main/assets")
    ).first { it.isDirectory }
}
