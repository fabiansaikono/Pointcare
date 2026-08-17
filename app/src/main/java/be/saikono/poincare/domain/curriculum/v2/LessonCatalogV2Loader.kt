package be.saikono.poincare.domain.curriculum.v2

import java.io.InputStream
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

object LessonCatalogV2Loader {
    const val ASSET_PATH: String = "poincare/curriculum/lesson-catalog-v2.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun fromStream(input: InputStream): LessonCatalogV2 = json.decodeFromStream(input)

    fun fromJson(value: String): LessonCatalogV2 = json.decodeFromString(value)

    /** Decodes release shards one by one to avoid retaining one very large JSON input buffer. */
    fun fromAssetProvider(open: (String) -> InputStream): LessonCatalogV2 {
        val indexText = open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val index = json.parseToJsonElement(indexText).jsonObject
        val shards = index["shards"]?.jsonArray.orEmpty()
        if (shards.isEmpty()) return fromJson(indexText)
        val lessons = shards.flatMap { shard ->
            val relativePath = shard.jsonObject.getValue("file").jsonPrimitive.content.removePrefix("/")
            open("poincare/curriculum/$relativePath").use(::fromStream).lessons
        }
        return LessonCatalogV2(
            schemaVersion = index.getValue("schemaVersion").jsonPrimitive.int,
            definitionVersion = index.getValue("definitionVersion").jsonPrimitive.int,
            lessons = lessons
        )
    }

    fun fromFile(indexFile: File): LessonCatalogV2 = fromAssetProvider { assetPath ->
        val relativePath = assetPath.removePrefix("poincare/curriculum/")
        (if (assetPath == ASSET_PATH) indexFile else File(indexFile.parentFile, relativePath)).inputStream()
    }
}
