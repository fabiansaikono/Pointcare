package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.LocalizedText
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class Grade1UnitKnowledgeProfile(
    val unitId: String,
    val title: LocalizedText,
    val terms: List<LocalizedText>,
    val facts: List<LocalizedText>,
    val warning: LocalizedText
)

/**
 * Reviewed unit-specific content shared by the three broad Grade 1 generators.
 * The JSON lives in JVM resources so the same source can also be consumed by
 * the catalogue build script.
 */
object Grade1UnitKnowledgeProfiles {
    private const val RESOURCE = "poincare/curriculum/grade1-unit-knowledge-profiles.json"

    private val profiles: Map<String, Grade1UnitKnowledgeProfile> by lazy {
        val stream = checkNotNull(Grade1UnitKnowledgeProfiles::class.java.classLoader?.getResourceAsStream(RESOURCE)) {
            "Missing Grade 1 unit-knowledge resource: $RESOURCE"
        }
        val raw = stream.bufferedReader(Charsets.UTF_8).use { reader ->
            Json.decodeFromString<List<RawProfile>>(reader.readText())
        }
        raw.associate { profile ->
            require(profile.title.size == 3) { "${profile.unitId} needs a three-language title" }
            require(profile.terms.size >= 3) { "${profile.unitId} needs at least three terms" }
            require(profile.facts.size >= 10) { "${profile.unitId} needs at least ten concrete facts" }
            require(profile.warning.size == 3) { "${profile.unitId} needs a three-language warning" }
            profile.unitId to Grade1UnitKnowledgeProfile(
                unitId = profile.unitId,
                title = profile.title.localized(),
                terms = profile.terms.map { it.localized() },
                facts = profile.facts.map { it.localized() },
                warning = profile.warning.localized()
            )
        }.also { loaded ->
            require(loaded.size == raw.size) { "Duplicate Grade 1 unit IDs in $RESOURCE" }
        }
    }

    fun supports(unitId: String): Boolean = unitId in profiles

    fun require(unitId: String): Grade1UnitKnowledgeProfile =
        checkNotNull(profiles[unitId]) { "Missing reviewed Grade 1 unit-knowledge profile for $unitId" }

    fun distractors(unitId: String, factIndex: Int): List<LocalizedText> {
        val orderedProfiles = profiles.values.sortedBy(Grade1UnitKnowledgeProfile::unitId)
        val currentIndex = orderedProfiles.indexOfFirst { it.unitId == unitId }
        require(currentIndex >= 0) { "Missing reviewed Grade 1 unit-knowledge profile for $unitId" }
        return listOf(1, 2).map { shift ->
            val other = orderedProfiles[(currentIndex + shift) % orderedProfiles.size]
            other.facts[factIndex % other.facts.size]
        }
    }

    private fun List<String>.localized(): LocalizedText {
        require(size == 3) { "Localized Grade 1 profile value needs NL, FR, and EN" }
        require(all(String::isNotBlank)) { "Localized Grade 1 profile value may not be blank" }
        return LocalizedText(this[0], this[1], this[2])
    }

    @Serializable
    private data class RawProfile(
        val unitId: String,
        val title: List<String>,
        val terms: List<List<String>>,
        val facts: List<List<String>>,
        val warning: List<String>
    )
}
