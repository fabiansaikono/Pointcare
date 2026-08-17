package be.saikono.poincare.domain.curriculum

import be.saikono.poincare.model.LearningDomain

/**
 * Canonical one-to-one bridge between curriculum definitions and visible app tiles.
 *
 * XML exercises already use these concise IDs. Generated exercises and lessons
 * must resolve through this registry so conceptual curriculum groups can never
 * make one lesson appear under several tiles.
 */
object CurriculumSubcategoryRegistry {
    data class TileId(val domain: LearningDomain, val id: String)

    private val conceptualToTile = mapOf(
        "language.early_literacy" to TileId(LearningDomain.READING, "reading"),
        "language.technical_reading" to TileId(LearningDomain.READING, "reading"),
        "language.reading_comprehension" to TileId(LearningDomain.READING, "reading"),
        "language.reading_strategies_information" to TileId(LearningDomain.READING, "reading"),
        "language.listening_comprehension" to TileId(LearningDomain.READING, "listening"),
        "language.speaking_interaction" to TileId(LearningDomain.READING, "speaking"),
        "language.listening_speaking" to TileId(LearningDomain.READING, "listening"),
        "language.dictation" to TileId(LearningDomain.READING, "dictation"),
        "language.writing_expression" to TileId(LearningDomain.WRITING, "writing"),
        "language.literature_experience" to TileId(LearningDomain.READING, "reading"),
        "spelling.phonology_letter_sound" to TileId(LearningDomain.WRITING, "spelling"),
        "spelling.rules_word_patterns" to TileId(LearningDomain.WRITING, "spelling"),
        "spelling.capitals_punctuation" to TileId(LearningDomain.WRITING, "spelling"),
        "spelling.word_formation_vocabulary" to TileId(LearningDomain.WRITING, "spelling"),
        "spelling.parts_of_speech" to TileId(LearningDomain.WRITING, "grammar"),
        "spelling.sentence_structure" to TileId(LearningDomain.WRITING, "grammar"),
        "spelling.verbs_tenses" to TileId(LearningDomain.WRITING, "verbs"),
        "spelling.language_reflection_variation" to TileId(LearningDomain.WRITING, "grammar"),
        "math.number_place_value" to TileId(LearningDomain.MATH, "number_sequences"),
        "math.addition_subtraction" to TileId(LearningDomain.MATH, "mixed_arithmetic"),
        "math.addition" to TileId(LearningDomain.MATH, "addition"),
        "math.subtraction" to TileId(LearningDomain.MATH, "subtraction"),
        "math.mixed_calculations" to TileId(LearningDomain.MATH, "mixed_arithmetic"),
        "math.multiplication_division" to TileId(LearningDomain.MATH, "mixed_arithmetic"),
        "math.fractions" to TileId(LearningDomain.MATH, "fractions"),
        "math.decimals" to TileId(LearningDomain.MATH, "fractions"),
        "math.ratios_percentages" to TileId(LearningDomain.MATH, "fractions"),
        "math.money_financial_literacy" to TileId(LearningDomain.MATH, "money"),
        "math.tables_graphs_statistics" to TileId(LearningDomain.MATH, "statistics"),
        "math.problem_solving" to TileId(LearningDomain.MATH, "mixed_arithmetic"),
        "geometry.compare_order_estimate" to TileId(LearningDomain.GEOMETRY, "compare_size"),
        "geometry.time_calendar" to TileId(LearningDomain.GEOMETRY, "time"),
        "geometry.length_perimeter_scale" to TileId(LearningDomain.GEOMETRY, "measurement"),
        "geometry.area" to TileId(LearningDomain.GEOMETRY, "measurement"),
        "geometry.capacity_volume" to TileId(LearningDomain.GEOMETRY, "measurement"),
        "geometry.mass_weight" to TileId(LearningDomain.GEOMETRY, "measurement"),
        "geometry.weight_volume" to TileId(LearningDomain.GEOMETRY, "measurement"),
        "geometry.temperature" to TileId(LearningDomain.GEOMETRY, "temperature"),
        "geometry.shapes_lines_angles" to TileId(LearningDomain.GEOMETRY, "geometry_shapes"),
        "geometry.spatial_orientation_coordinates" to TileId(LearningDomain.GEOMETRY, "geometry_shapes"),
        "geometry.symmetry_patterns_transformations" to TileId(LearningDomain.GEOMETRY, "geometry_shapes"),
        "geometry.patterns_orientation" to TileId(LearningDomain.GEOMETRY, "geometry_shapes"),
        "world.nature_living_world" to TileId(LearningDomain.WORLD_KNOWLEDGE, "nature"),
        "world.science_inquiry" to TileId(LearningDomain.WORLD_KNOWLEDGE, "science"),
        "world.technology_design" to TileId(LearningDomain.WORLD_KNOWLEDGE, "technology"),
        "world.geography_space" to TileId(LearningDomain.WORLD_KNOWLEDGE, "geography"),
        "world.history_time" to TileId(LearningDomain.WORLD_KNOWLEDGE, "history"),
        "world.body_health" to TileId(LearningDomain.WORLD_KNOWLEDGE, "human_body"),
        "world.citizenship_social" to TileId(LearningDomain.WORLD_KNOWLEDGE, "citizenship_society"),
        "world.traffic_mobility_safety" to TileId(LearningDomain.WORLD_KNOWLEDGE, "traffic_safety"),
        "world.environment_sustainability" to TileId(LearningDomain.WORLD_KNOWLEDGE, "environment_sustainability"),
        "world.digital_media_literacy" to TileId(LearningDomain.WORLD_KNOWLEDGE, "technology"),
        "world.daily_life_self_reliance" to TileId(LearningDomain.WORLD_KNOWLEDGE, "everyday_life")
    )

    fun tileFor(unit: CurriculumUnit): TileId {
        if (unit.id == "math.addition.g1.foundations") {
            return TileId(LearningDomain.MATH, "addition")
        }
        return conceptualToTile[unit.subcategoryId]
            ?: TileId(unit.domain, unit.subcategoryId)
    }

    fun tileFor(unit: CurriculumUnit, lesson: LessonDefinition): TileId {
        if (unit.id == "math.addition.g1.foundations") {
            val operation = lesson.sections.asSequence()
                .mapNotNull { it.activitySource as? LessonActivitySource.Generated }
                .mapNotNull { it.parameters["operation"] }
                .distinct()
                .singleOrNull()
            if (operation == "subtraction" || ".subtraction." in lesson.id) {
                return TileId(LearningDomain.MATH, "subtraction")
            }
        }
        if (unit.id == "unit.g1.geometry.measurement_shapes.foundations") {
            val focus = lesson.sections.asSequence()
                .mapNotNull { it.activitySource as? LessonActivitySource.Generated }
                .mapNotNull { it.parameters["focus"] }
                .filter { it != "mixed" }
                .distinct()
                .singleOrNull()
            return when (focus) {
                "comparison" -> TileId(LearningDomain.GEOMETRY, "compare_size")
                "measurement" -> TileId(LearningDomain.GEOMETRY, "measurement")
                "time" -> TileId(LearningDomain.GEOMETRY, "time")
                "money" -> TileId(LearningDomain.MATH, "money")
                "shapes" -> TileId(LearningDomain.GEOMETRY, "geometry_shapes")
                else -> tileFor(unit)
            }
        }
        return tileFor(unit)
    }

    fun conceptualIdsFor(domain: LearningDomain, tileId: String): Set<String> =
        conceptualToTile
            .filterValues { it.domain == domain && it.id == tileId }
            .keys
}
