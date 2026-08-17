package be.saikono.poincare.domain.curriculum.manifest

import be.saikono.poincare.domain.curriculum.CurriculumIssueSeverity
import be.saikono.poincare.domain.curriculum.CurriculumRequirement
import be.saikono.poincare.domain.curriculum.CurriculumValidationIssue
import be.saikono.poincare.domain.curriculum.CurriculumValidationMode
import be.saikono.poincare.domain.curriculum.CurriculumValidationReport
import be.saikono.poincare.domain.curriculum.CurriculumValidator
import be.saikono.poincare.domain.curriculum.ParentSupportLevel
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText

object CurriculumManifestValidator {
    const val FULL_ROADMAP_ENTRY_COUNT: Int = 67
    const val FULL_LESSON_UNIT_COUNT: Int = 65
    const val FULL_EXTERNAL_RESPONSIBILITY_COUNT: Int = 2
    const val FULL_SUBCATEGORY_COUNT: Int = 56
    val fullRoadmapCountsByGrade: Map<Int, Int> = linkedMapOf(
        1 to 13,
        2 to 10,
        3 to 10,
        4 to 9,
        5 to 12,
        6 to 13
    )
    val fullUnitCountsByGrade: Map<Int, Int> = linkedMapOf(
        1 to 13,
        2 to 10,
        3 to 10,
        4 to 9,
        5 to 11,
        6 to 12
    )

    val approvedCategoryIds: List<String> = listOf(
        "language_skills",
        "spelling_grammar",
        "number_sense",
        "measurement_geometry",
        "world_orientation",
        "logic_puzzles"
    )

    private val approvedDomains: List<LearningDomain> = listOf(
        LearningDomain.READING,
        LearningDomain.WRITING,
        LearningDomain.MATH,
        LearningDomain.GEOMETRY,
        LearningDomain.WORLD_KNOWLEDGE,
        LearningDomain.PUZZLE
    )
    private val stableIdRegex = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
    private val sha256Regex = Regex("^[0-9a-f]{64}$")

    fun validate(
        manifest: CurriculumManifest,
        mode: CurriculumValidationMode = CurriculumValidationMode.RELEASE
    ): CurriculumValidationReport {
        val issues = mutableListOf<CurriculumValidationIssue>()

        if (manifest.schemaVersion != CURRENT_MANIFEST_SCHEMA_VERSION) {
            issues.error("unsupported_manifest_schema", "manifest.schemaVersion", "Expected manifest schema $CURRENT_MANIFEST_SCHEMA_VERSION.")
        }
        if (manifest.definitionVersion < 1) {
            issues.error("invalid_manifest_definition_version", "manifest.definitionVersion", "Definition version must be at least 1.")
        }
        validateSource(manifest, issues)
        validateStructureRevisions(manifest.structureRevisions, issues)

        if (manifest.categories.map { it.id } != approvedCategoryIds) {
            issues.error(
                "unexpected_category_order",
                "manifest.categories",
                "The manifest must contain the six approved categories in canonical order."
            )
        }
        if (manifest.categories.map { it.domain } != approvedDomains) {
            issues.error(
                "unexpected_domain_mapping",
                "manifest.categories",
                "Each approved category must map once to its established Poincaré learning domain."
            )
        }

        val categoryIds = linkedSetOf<String>()
        val subcategoryIds = linkedSetOf<String>()
        manifest.categories.forEachIndexed { categoryIndex, category ->
            val categoryPath = "manifest.categories[$categoryIndex]"
            validateId(category.id, "$categoryPath.id", issues)
            if (!categoryIds.add(category.id)) {
                issues.error("duplicate_manifest_category", "$categoryPath.id", "Category ID '${category.id}' is not unique.")
            }
            if (category.definitionVersion < 1) {
                issues.error("invalid_category_definition_version", "$categoryPath.definitionVersion", "Definition version must be at least 1.")
            }
            validateText(category.title, "$categoryPath.title", mode, issues)
            if (category.subcategories.isEmpty() && category.domain != LearningDomain.PUZZLE) {
                issues.error("empty_manifest_category", "$categoryPath.subcategories", "A manifest category must contain subcategories.")
            }

            category.subcategories.forEachIndexed { subcategoryIndex, subcategory ->
                val path = "$categoryPath.subcategories[$subcategoryIndex]"
                validateId(subcategory.id, "$path.id", issues)
                if (!subcategoryIds.add(subcategory.id)) {
                    issues.error("duplicate_manifest_subcategory", "$path.id", "Subcategory ID '${subcategory.id}' is not globally unique.")
                }
                if (subcategory.definitionVersion < 1) {
                    issues.error("invalid_subcategory_definition_version", "$path.definitionVersion", "Definition version must be at least 1.")
                }
                validateText(subcategory.title, "$path.title", mode, issues)
                validateText(subcategory.summary, "$path.summary", mode, issues)
                validateGrades(subcategory.grades, "$path.grades", issues)
                validateAliases(subcategory.legacySubcategoryIds, "$path.legacySubcategoryIds", issues)
                validateTags(subcategory.tags, "$path.tags", issues)
                validateGradeSequences(subcategory.gradeSequences, "$path.gradeSequences", issues)
                validateDelivery(
                    requirement = subcategory.requirement,
                    deliveryMode = subcategory.deliveryMode,
                    parentSupport = subcategory.parentSupport,
                    path = path,
                    issues = issues
                )
            }
        }
        if (subcategoryIds.size != FULL_SUBCATEGORY_COUNT) {
            issues.error(
                "unexpected_taxonomy_size",
                "manifest.categories",
                "The reviewed taxonomy must contain exactly $FULL_SUBCATEGORY_COUNT conceptual subcategories."
            )
        }

        val lessonReport = CurriculumValidator.validate(manifest.lessonCatalog, mode)
        issues += lessonReport.issues.map { it.copy(path = "manifest.lessonCatalog.${it.path}") }

        val index = CurriculumManifestIndex.create(manifest)
        validateRoadmap(manifest, index, categoryIds, subcategoryIds, mode, issues)
        validateCatalogueMappings(manifest, index, issues)
        validateScope(manifest, issues)

        return CurriculumValidationReport(issues)
    }

    private fun validateSource(manifest: CurriculumManifest, issues: MutableList<CurriculumValidationIssue>) {
        val source = manifest.source
        validateId(source.id, "manifest.source.id", issues)
        if (source.version < 1) {
            issues.error("invalid_manifest_source_version", "manifest.source.version", "Source version must be at least 1.")
        }
        if (!sha256Regex.matches(source.workbookSha256)) {
            issues.error("invalid_manifest_source_hash", "manifest.source.workbookSha256", "Workbook SHA-256 must contain 64 lowercase hexadecimal characters.")
        }
        if (source.sheet.isBlank()) {
            issues.error("missing_manifest_source_sheet", "manifest.source.sheet", "Taxonomy source sheet is required.")
        }
        if (source.title.isBlank()) {
            issues.error("missing_manifest_source_title", "manifest.source.title", "Taxonomy source title is required.")
        }
        if (manifest.scope == CurriculumManifestScope.FULL) {
            if (source.roadmapSheet.isBlank()) {
                issues.error("missing_roadmap_source_sheet", "manifest.source.roadmapSheet", "The full catalogue must name its roadmap worksheet.")
            }
            if (source.roadmapTitle.isBlank()) {
                issues.error("missing_roadmap_source_title", "manifest.source.roadmapTitle", "The full catalogue must record the roadmap title.")
            }
        }
    }

    private fun validateStructureRevisions(
        revisions: List<CurriculumStructureRevisionManifest>,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        if (revisions.map { it.id } != revisions.map { it.id }.distinct()) {
            issues.error("duplicate_structure_revision", "manifest.structureRevisions", "Structure revision IDs must be unique.")
        }
        revisions.forEachIndexed { index, revision ->
            val path = "manifest.structureRevisions[$index]"
            validateId(revision.id, "$path.id", issues)
            if (revision.grade !in 1..6) issues.error("invalid_structure_revision_grade", "$path.grade", "Revision grades must be between 1 and 6.")
            if (revision.sourceFile.isBlank()) issues.error("missing_structure_source_file", "$path.sourceFile", "A structure source file is required.")
            if (revision.sourceSheet.isBlank()) issues.error("missing_structure_source_sheet", "$path.sourceSheet", "A structure source sheet is required.")
            if (!sha256Regex.matches(revision.sourceWorkbookSha256)) {
                issues.error("invalid_structure_source_hash", "$path.sourceWorkbookSha256", "Workbook SHA-256 must contain 64 lowercase hexadecimal characters.")
            }
        }
    }

    private fun validateRoadmap(
        manifest: CurriculumManifest,
        index: CurriculumManifestIndex,
        categoryIds: Set<String>,
        subcategoryIds: Set<String>,
        mode: CurriculumValidationMode,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        val roadmapIds = linkedSetOf<String>()
        val linkedUnitIds = linkedSetOf<String>()
        val unitById = manifest.lessonCatalog.units.associateBy { it.id }

        manifest.roadmapEntries.forEachIndexed { entryIndex, entry ->
            val path = "manifest.roadmapEntries[$entryIndex]"
            validateId(entry.id, "$path.id", issues)
            if (!roadmapIds.add(entry.id)) {
                issues.error("duplicate_roadmap_entry", "$path.id", "Roadmap entry ID '${entry.id}' is not unique.")
            }
            if (entry.grade !in 1..6) {
                issues.error("invalid_roadmap_grade", "$path.grade", "Roadmap grades must be between 1 and 6.")
            }
            if (entry.definitionVersion < 1) {
                issues.error("invalid_roadmap_definition_version", "$path.definitionVersion", "Definition version must be at least 1.")
            }
            validateText(entry.title, "$path.title", mode, issues)
            validateText(entry.learningGoal, "$path.learningGoal", mode, issues)
            validateText(entry.appAction, "$path.appAction", mode, issues)
            validateText(entry.adultAction, "$path.adultAction", mode, issues)
            if (entry.sourceLearningGoalNl.isBlank()) {
                issues.error("missing_source_learning_goal", "$path.sourceLearningGoalNl", "The exact Dutch workbook learning goal is required.")
            } else if (entry.sourceLearningGoalNl != entry.learningGoal.nl) {
                issues.error("source_goal_mismatch", "$path.learningGoal.nl", "The Dutch manifest learning goal must preserve the exact workbook text.")
            }
            if (entry.coverage.isBlank()) issues.error("missing_roadmap_coverage", "$path.coverage", "Coverage status is required.")
            if (entry.decision.isBlank()) issues.error("missing_roadmap_decision", "$path.decision", "The reviewed decision is required.")
            if (entry.sourceReference.isBlank()) issues.error("missing_roadmap_source_reference", "$path.sourceReference", "A roadmap source reference is required.")
            validateDelivery(entry.requirement, entry.deliveryMode, entry.parentSupport, path, issues)

            if (entry.deliveryMode == CurriculumDeliveryMode.PARENT_TEACHER_RESPONSIBILITY) {
                if (entry.categoryId != null) {
                    issues.error("external_entry_with_category", "$path.categoryId", "An outside-app responsibility cannot claim an app category.")
                }
                if (entry.subcategoryIds.isNotEmpty()) {
                    issues.error("external_entry_with_subcategories", "$path.subcategoryIds", "An outside-app responsibility cannot claim app subcategories.")
                }
                if (entry.unitId != null) {
                    issues.error("external_entry_with_unit", "$path.unitId", "An outside-app responsibility cannot create a lesson unit.")
                }
                if (entry.parentSupport != ParentSupportLevel.REQUIRED) {
                    issues.error("external_entry_without_required_support", "$path.parentSupport", "Outside-app responsibilities require adult or teacher support.")
                }
                return@forEachIndexed
            }

            val categoryId = entry.categoryId
            if (categoryId == null || categoryId !in categoryIds) {
                issues.error("unknown_roadmap_category", "$path.categoryId", "Roadmap entry '${entry.id}' must reference an approved category.")
            }
            if (entry.subcategoryIds.isEmpty()) {
                issues.error("roadmap_without_subcategory", "$path.subcategoryIds", "An app roadmap entry must reference at least one taxonomy subcategory.")
            }
            if (entry.subcategoryIds.size != entry.subcategoryIds.distinct().size) {
                issues.error("duplicate_roadmap_subcategory", "$path.subcategoryIds", "Roadmap subcategory references must be unique.")
            }
            entry.subcategoryIds.forEachIndexed { subIndex, subcategoryId ->
                validateId(subcategoryId, "$path.subcategoryIds[$subIndex]", issues)
                if (subcategoryId !in subcategoryIds) {
                    issues.error("unknown_roadmap_subcategory", "$path.subcategoryIds[$subIndex]", "Unknown taxonomy subcategory '$subcategoryId'.")
                }
            }
            val primaryId = entry.subcategoryIds.firstOrNull()
            if (primaryId != null && categoryId != null && index.categoryForSubcategory(primaryId)?.id != categoryId) {
                issues.error(
                    "roadmap_primary_category_mismatch",
                    "$path.subcategoryIds[0]",
                    "The first roadmap subcategory must belong to the roadmap entry's primary category."
                )
            }

            val unitId = entry.unitId
            val unit = unitId?.let(unitById::get)
            if (unitId == null || unit == null) {
                issues.error("unknown_roadmap_unit", "$path.unitId", "Every app roadmap entry must reference one lesson unit.")
            } else {
                if (!linkedUnitIds.add(unitId)) {
                    issues.error("duplicate_roadmap_unit_link", "$path.unitId", "Lesson unit '$unitId' is linked by more than one roadmap entry.")
                }
                if (unit.grades != listOf(entry.grade)) {
                    issues.error("roadmap_unit_grade_mismatch", "$path.unitId", "Unit '$unitId' must apply only to roadmap grade ${entry.grade}.")
                }
                if (unit.requirement != entry.requirement) {
                    issues.error("roadmap_unit_requirement_mismatch", "$path.unitId", "Unit '$unitId' must use the roadmap requirement.")
                }
                val resolved = index.resolveLegacySubcategory(unit.domain, unit.subcategoryId)
                if (primaryId != null && resolved?.id != primaryId) {
                    issues.error(
                        "roadmap_unit_subcategory_mismatch",
                        "$path.unitId",
                        "Unit '$unitId' must resolve to primary taxonomy subcategory '$primaryId'."
                    )
                }
            }
        }

        if (manifest.scope == CurriculumManifestScope.FULL) {
            val catalogueUnitIds = manifest.lessonCatalog.units.mapTo(linkedSetOf()) { it.id }
            if (linkedUnitIds != catalogueUnitIds) {
                val unlinked = catalogueUnitIds - linkedUnitIds
                val missing = linkedUnitIds - catalogueUnitIds
                issues.error(
                    "full_catalogue_linkage_mismatch",
                    "manifest.roadmapEntries",
                    "Every full-catalogue unit must be linked exactly once. Unlinked=$unlinked unknown=$missing"
                )
            }
        }
    }

    private fun validateCatalogueMappings(
        manifest: CurriculumManifest,
        index: CurriculumManifestIndex,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        manifest.lessonCatalog.units.forEachIndexed { unitIndex, unit ->
            val path = "manifest.lessonCatalog.units[$unitIndex]"
            val taxonomy = index.resolveLegacySubcategory(unit.domain, unit.subcategoryId)
            if (taxonomy == null) {
                issues.error(
                    "unmapped_lesson_subcategory",
                    "$path.subcategoryId",
                    "Unit '${unit.id}' uses subcategory '${unit.subcategoryId}', which is not mapped in the manifest for ${unit.domain}."
                )
            } else if (unit.grades.any { it !in taxonomy.grades }) {
                issues.error(
                    "unit_grade_outside_manifest_subcategory",
                    "$path.grades",
                    "Unit '${unit.id}' contains a grade outside manifest subcategory '${taxonomy.id}'."
                )
            }
        }
    }

    private fun validateScope(
        manifest: CurriculumManifest,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        val unitIds = manifest.lessonCatalog.units.map { it.id }
        if (manifest.pilotUnitIds.size != manifest.pilotUnitIds.distinct().size) {
            issues.error("duplicate_pilot_unit", "manifest.pilotUnitIds", "Pilot unit IDs must be unique.")
        }
        manifest.pilotUnitIds.forEachIndexed { index, id ->
            validateId(id, "manifest.pilotUnitIds[$index]", issues)
            if (id !in unitIds) {
                issues.error("unknown_pilot_unit", "manifest.pilotUnitIds[$index]", "Pilot unit '$id' does not exist in the lesson catalogue.")
            }
        }

        if (manifest.scope == CurriculumManifestScope.PILOT) {
            if (manifest.pilotUnitIds.isEmpty()) {
                issues.error("empty_pilot_scope", "manifest.pilotUnitIds", "A pilot manifest must identify at least one pilot unit.")
            }
            return
        }

        if (manifest.pilotUnitIds.isNotEmpty()) {
            issues.error("full_manifest_with_pilot_units", "manifest.pilotUnitIds", "A full manifest must not retain pilot-only unit markers.")
        }
        if (manifest.roadmapEntries.size != FULL_ROADMAP_ENTRY_COUNT) {
            issues.error(
                "unexpected_full_roadmap_size",
                "manifest.roadmapEntries",
                "The reviewed full roadmap must contain $FULL_ROADMAP_ENTRY_COUNT entries."
            )
        }
        if (manifest.lessonCatalog.units.size != FULL_LESSON_UNIT_COUNT) {
            issues.error(
                "unexpected_full_unit_count",
                "manifest.lessonCatalog.units",
                "The full app catalogue must contain $FULL_LESSON_UNIT_COUNT lesson units."
            )
        }
        val external = manifest.roadmapEntries.count {
            it.deliveryMode == CurriculumDeliveryMode.PARENT_TEACHER_RESPONSIBILITY
        }
        if (external != FULL_EXTERNAL_RESPONSIBILITY_COUNT) {
            issues.error(
                "unexpected_external_responsibility_count",
                "manifest.roadmapEntries",
                "The reviewed roadmap must retain $FULL_EXTERNAL_RESPONSIBILITY_COUNT outside-app responsibilities."
            )
        }
        val roadmapCounts = (1..6).associateWith { grade -> manifest.roadmapEntries.count { it.grade == grade } }
        if (roadmapCounts != fullRoadmapCountsByGrade) {
            issues.error(
                "unexpected_roadmap_grade_counts",
                "manifest.roadmapEntries",
                "Full roadmap grade counts must be $fullRoadmapCountsByGrade but were $roadmapCounts."
            )
        }
        val unitCounts = (1..6).associateWith { grade ->
            manifest.lessonCatalog.units.count { it.grades == listOf(grade) }
        }
        if (unitCounts != fullUnitCountsByGrade) {
            issues.error(
                "unexpected_unit_grade_counts",
                "manifest.lessonCatalog.units",
                "Full catalogue unit grade counts must be $fullUnitCountsByGrade but were $unitCounts."
            )
        }
    }

    private fun validateText(
        text: LocalizedText,
        path: String,
        mode: CurriculumValidationMode,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        if (text.nl.isBlank()) issues.error("missing_nl_text", "$path.nl", "Belgian Dutch text is required.")
        fun missing(value: String?, locale: String) {
            if (value.isNullOrBlank()) {
                val severity = if (mode == CurriculumValidationMode.RELEASE) CurriculumIssueSeverity.ERROR else CurriculumIssueSeverity.WARNING
                issues += CurriculumValidationIssue(severity, "missing_${locale}_text", "$path.$locale", "Translation '$locale' is required for release content.")
            }
        }
        missing(text.fr, "fr")
        missing(text.en, "en")
    }

    private fun validateGrades(grades: List<Int>, path: String, issues: MutableList<CurriculumValidationIssue>) {
        if (grades.isEmpty()) issues.error("empty_manifest_grade_scope", path, "At least one grade is required.")
        if (grades.any { it !in 1..6 }) issues.error("invalid_manifest_grade", path, "Grades must be between 1 and 6.")
        if (grades != grades.distinct().sorted()) issues.error("noncanonical_manifest_grades", path, "Grades must be unique and sorted.")
    }

    private fun validateAliases(aliases: List<String>, path: String, issues: MutableList<CurriculumValidationIssue>) {
        if (aliases.size != aliases.distinct().size) issues.error("duplicate_legacy_alias", path, "Legacy subcategory aliases must be unique within one entry.")
        aliases.forEachIndexed { index, alias -> validateId(alias, "$path[$index]", issues) }
    }

    private fun validateTags(tags: List<String>, path: String, issues: MutableList<CurriculumValidationIssue>) {
        if (tags.size != tags.distinct().size) issues.error("duplicate_manifest_tag", path, "Tags must be unique.")
        tags.forEachIndexed { index, tag -> validateId(tag, "$path[$index]", issues) }
    }

    private fun validateGradeSequences(
        sequences: List<CurriculumGradeSequenceManifest>,
        path: String,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        if (sequences.map { it.grade } != sequences.map { it.grade }.distinct().sorted()) {
            issues.error("noncanonical_grade_sequences", path, "Grade sequences must be unique and sorted by grade.")
        }
        sequences.forEachIndexed { index, entry ->
            if (entry.grade !in 1..6) {
                issues.error("invalid_grade_sequence_grade", "$path[$index].grade", "Sequence grades must be between 1 and 6.")
            }
            if (entry.sequence < 1) {
                issues.error("invalid_grade_sequence", "$path[$index].sequence", "Subcategory sequences start at 1.")
            }
        }
    }

    private fun validateDelivery(
        requirement: CurriculumRequirement,
        deliveryMode: CurriculumDeliveryMode,
        parentSupport: ParentSupportLevel,
        path: String,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        if (requirement == CurriculumRequirement.OPTIONAL && deliveryMode != CurriculumDeliveryMode.OPTIONAL_IN_APP) {
            issues.error("optional_delivery_mismatch", "$path.deliveryMode", "Optional enrichment must use OPTIONAL_IN_APP delivery.")
        }
        if (requirement != CurriculumRequirement.OPTIONAL && deliveryMode == CurriculumDeliveryMode.OPTIONAL_IN_APP) {
            issues.error("mandatory_optional_delivery", "$path.deliveryMode", "Mandatory or recommended curriculum cannot use optional-only delivery.")
        }
        if (deliveryMode == CurriculumDeliveryMode.HYBRID_APP_PARENT && parentSupport == ParentSupportLevel.NONE) {
            issues.error("hybrid_without_parent_support", "$path.parentSupport", "Hybrid delivery must declare recommended or required parent support.")
        }
        if (deliveryMode == CurriculumDeliveryMode.IN_APP_CORE && parentSupport == ParentSupportLevel.REQUIRED) {
            issues.error("core_requires_parent", "$path.parentSupport", "A core in-app entry cannot require parent support for completion.")
        }
        if (deliveryMode == CurriculumDeliveryMode.PARENT_TEACHER_RESPONSIBILITY && parentSupport != ParentSupportLevel.REQUIRED) {
            issues.error("external_without_required_support", "$path.parentSupport", "Outside-app responsibilities require parent or teacher support.")
        }
    }

    private fun validateId(id: String, path: String, issues: MutableList<CurriculumValidationIssue>) {
        if (!stableIdRegex.matches(id)) issues.error("invalid_manifest_id", path, "'$id' is not a stable language-neutral ID.")
    }

    private fun MutableList<CurriculumValidationIssue>.error(code: String, path: String, message: String) {
        add(CurriculumValidationIssue(CurriculumIssueSeverity.ERROR, code, path, message))
    }
}
