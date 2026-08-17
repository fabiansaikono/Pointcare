package be.saikono.poincare.data.local

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addColumnIfMissing("profiles", "hiddenMainCategories", "TEXT NOT NULL DEFAULT ''")
            db.addColumnIfMissing("profiles", "hiddenSubcategories", "TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addColumnIfMissing("profiles", "showUnassignedExercises", "INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * Version 7 predates checked-in Room schema export. This migration is deliberately
     * defensive: it preserves a version-6 database whether gifted mode and the sync
     * queue were already present or were introduced by that historical build.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addColumnIfMissing("profiles", "giftedModeEnabled", "INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `sync_queue` (
                    `eventId` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`eventId`)
                )""".trimIndent()
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addColumnIfMissing("profiles", "selectedGrades", "TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `lesson_progress` (
                    `progressId` TEXT NOT NULL,
                    `childProfileId` TEXT NOT NULL,
                    `unitId` TEXT NOT NULL,
                    `lessonId` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `masteryLevel` TEXT NOT NULL,
                    `masteryPercent` INTEGER NOT NULL,
                    `bestScorePercent` INTEGER NOT NULL,
                    `latestScorePercent` INTEGER NOT NULL,
                    `attemptCount` INTEGER NOT NULL,
                    `startedAtUtc` INTEGER,
                    `completedAtUtc` INTEGER,
                    `lastActivityAtUtc` INTEGER NOT NULL,
                    `updatedAtUtc` INTEGER NOT NULL,
                    `syncId` TEXT NOT NULL,
                    `syncStatus` TEXT NOT NULL,
                    `revision` INTEGER NOT NULL,
                    PRIMARY KEY(`progressId`)
                )""".trimIndent()
            )
            db.execSQL(
                """CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_progress_child_lesson`
                    ON `lesson_progress` (`childProfileId`, `lessonId`)""".trimIndent()
            )
            db.execSQL(
                """CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_progress_sync_id`
                    ON `lesson_progress` (`syncId`)""".trimIndent()
            )
            db.execSQL(
                """CREATE INDEX IF NOT EXISTS `index_lesson_progress_sync_status`
                    ON `lesson_progress` (`syncStatus`)""".trimIndent()
            )

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `lesson_attempts` (
                    `attemptId` TEXT NOT NULL,
                    `progressId` TEXT NOT NULL,
                    `childProfileId` TEXT NOT NULL,
                    `unitId` TEXT NOT NULL,
                    `lessonId` TEXT NOT NULL,
                    `sequenceNumber` INTEGER NOT NULL,
                    `startedAtUtc` INTEGER NOT NULL,
                    `completedAtUtc` INTEGER NOT NULL,
                    `scorePercent` INTEGER NOT NULL,
                    `correctCount` INTEGER NOT NULL,
                    `questionCount` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `hintsUsed` INTEGER NOT NULL,
                    `completed` INTEGER NOT NULL,
                    `masteryAfterPercent` INTEGER NOT NULL,
                    `generatorSeed` INTEGER,
                    `contentVersion` TEXT NOT NULL,
                    `syncId` TEXT NOT NULL,
                    `syncStatus` TEXT NOT NULL,
                    `metadataJson` TEXT NOT NULL,
                    PRIMARY KEY(`attemptId`)
                )""".trimIndent()
            )
            db.execSQL(
                """CREATE INDEX IF NOT EXISTS `index_lesson_attempts_progress_id`
                    ON `lesson_attempts` (`progressId`)""".trimIndent()
            )
            db.execSQL(
                """CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_attempts_child_lesson_sequence`
                    ON `lesson_attempts` (`childProfileId`, `lessonId`, `sequenceNumber`)""".trimIndent()
            )
            db.execSQL(
                """CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_attempts_sync_id`
                    ON `lesson_attempts` (`syncId`)""".trimIndent()
            )
            db.execSQL(
                """CREATE INDEX IF NOT EXISTS `index_lesson_attempts_sync_status`
                    ON `lesson_attempts` (`syncStatus`)""".trimIndent()
            )
        }
    }

    /**
     * Historical versions added non-null profile columns with SQL DEFAULT clauses.
     * Room 9 validates those clauses against the current entity definition, where
     * defaults are deliberately supplied by Kotlin and are not part of the schema.
     * Rebuilding the small profile table once makes every supported upgrade path
     * converge on the exported canonical schema without changing any stored value.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE `_profiles_canonical_v10` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `avatarId` TEXT NOT NULL,
                    `rocketColorId` TEXT NOT NULL,
                    `birthYear` INTEGER NOT NULL,
                    `appLanguage` TEXT NOT NULL,
                    `exerciseLanguage` TEXT NOT NULL,
                    `supportLanguage` TEXT NOT NULL,
                    `speechLanguage` TEXT NOT NULL,
                    `readingLevel` TEXT NOT NULL,
                    `writingLevel` TEXT NOT NULL,
                    `mathLevel` TEXT NOT NULL,
                    `geometryLevel` TEXT NOT NULL,
                    `worldKnowledgeLevel` TEXT NOT NULL,
                    `puzzleLevel` TEXT NOT NULL,
                    `selectedGrades` TEXT NOT NULL,
                    `hiddenMainCategories` TEXT NOT NULL,
                    `hiddenSubcategories` TEXT NOT NULL,
                    `showUnassignedExercises` INTEGER NOT NULL,
                    `giftedModeEnabled` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )""".trimIndent()
            )
            db.execSQL(
                """INSERT INTO `_profiles_canonical_v10` (
                    `id`, `name`, `avatarId`, `rocketColorId`, `birthYear`,
                    `appLanguage`, `exerciseLanguage`, `supportLanguage`, `speechLanguage`,
                    `readingLevel`, `writingLevel`, `mathLevel`, `geometryLevel`,
                    `worldKnowledgeLevel`, `puzzleLevel`, `selectedGrades`,
                    `hiddenMainCategories`, `hiddenSubcategories`, `showUnassignedExercises`,
                    `giftedModeEnabled`, `createdAt`, `updatedAt`
                ) SELECT
                    `id`, `name`, `avatarId`, `rocketColorId`, `birthYear`,
                    `appLanguage`, `exerciseLanguage`, `supportLanguage`, `speechLanguage`,
                    `readingLevel`, `writingLevel`, `mathLevel`, `geometryLevel`,
                    `worldKnowledgeLevel`, `puzzleLevel`, `selectedGrades`,
                    `hiddenMainCategories`, `hiddenSubcategories`, `showUnassignedExercises`,
                    `giftedModeEnabled`, `createdAt`, `updatedAt`
                FROM `profiles`""".trimIndent()
            )
            db.execSQL("DROP TABLE `profiles`")
            db.execSQL("ALTER TABLE `_profiles_canonical_v10` RENAME TO `profiles`")
        }
    }

    /**
     * Adds lesson architecture v2 evidence without replacing v1 progress. Existing
     * COMPLETED rows are explicitly grandfathered as PASSED_LEGACY and remain unlocked.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addColumnIfMissing("lesson_progress", "gateStateV2", "TEXT")
            db.addColumnIfMissing("lesson_progress", "currentStageTypeV2", "TEXT")
            db.addColumnIfMissing("lesson_progress", "passedAtUtc", "INTEGER")
            db.addColumnIfMissing("lesson_progress", "masteredAtUtc", "INTEGER")
            db.addColumnIfMissing("lesson_progress", "contentDefinitionVersion", "INTEGER")
            db.addColumnIfMissing("lesson_progress", "legacyEvidence", "INTEGER")
            db.execSQL(
                """UPDATE `lesson_progress`
                    SET `gateStateV2` = 'PASSED_LEGACY', `legacyEvidence` = 1
                    WHERE `state` = 'COMPLETED' AND `gateStateV2` IS NULL""".trimIndent()
            )

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `lesson_stage_progress` (
                    `stageProgressId` TEXT NOT NULL,
                    `childProfileId` TEXT NOT NULL,
                    `lessonId` TEXT NOT NULL,
                    `stageId` TEXT NOT NULL,
                    `stageType` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `diagnosticScorePercent` INTEGER,
                    `startedAtUtc` INTEGER,
                    `completedAtUtc` INTEGER,
                    `updatedAtUtc` INTEGER NOT NULL,
                    `contentDefinitionVersion` INTEGER NOT NULL,
                    `syncId` TEXT NOT NULL,
                    `syncStatus` TEXT NOT NULL,
                    PRIMARY KEY(`stageProgressId`)
                )""".trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_stage_progress_child_lesson_stage` ON `lesson_stage_progress` (`childProfileId`, `lessonId`, `stageId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_stage_progress_sync_id` ON `lesson_stage_progress` (`syncId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_stage_progress_sync_status` ON `lesson_stage_progress` (`syncStatus`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `lesson_assessment_attempts` (
                    `assessmentAttemptId` TEXT NOT NULL,
                    `progressId` TEXT NOT NULL,
                    `childProfileId` TEXT NOT NULL,
                    `unitId` TEXT NOT NULL,
                    `lessonId` TEXT NOT NULL,
                    `stageId` TEXT NOT NULL,
                    `evidenceSource` TEXT NOT NULL,
                    `startedAtUtc` INTEGER NOT NULL,
                    `completedAtUtc` INTEGER NOT NULL,
                    `correctCount` INTEGER NOT NULL,
                    `itemCount` INTEGER NOT NULL,
                    `scorePercent` INTEGER NOT NULL,
                    `hintsUsed` INTEGER NOT NULL,
                    `passed` INTEGER NOT NULL,
                    `retentionCheck` INTEGER NOT NULL,
                    `contentVersion` TEXT NOT NULL,
                    `syncId` TEXT NOT NULL,
                    `syncStatus` TEXT NOT NULL,
                    `metadataJson` TEXT NOT NULL,
                    PRIMARY KEY(`assessmentAttemptId`)
                )""".trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_assessment_attempts_progress_id` ON `lesson_assessment_attempts` (`progressId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_assessment_attempts_child_lesson` ON `lesson_assessment_attempts` (`childProfileId`, `lessonId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_assessment_attempts_sync_id` ON `lesson_assessment_attempts` (`syncId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_assessment_attempts_sync_status` ON `lesson_assessment_attempts` (`syncStatus`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `lesson_question_attempts` (
                    `questionAttemptId` TEXT NOT NULL,
                    `assessmentAttemptId` TEXT NOT NULL,
                    `childProfileId` TEXT NOT NULL,
                    `lessonId` TEXT NOT NULL,
                    `exerciseId` TEXT NOT NULL,
                    `itemFingerprint` TEXT NOT NULL,
                    `masteryTagsJson` TEXT NOT NULL,
                    `correct` INTEGER NOT NULL,
                    `hintsUsed` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `answeredAtUtc` INTEGER NOT NULL,
                    PRIMARY KEY(`questionAttemptId`)
                )""".trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_question_attempts_assessment_id` ON `lesson_question_attempts` (`assessmentAttemptId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_question_attempts_child_lesson_tags` ON `lesson_question_attempts` (`childProfileId`, `lessonId`, `masteryTagsJson`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `lesson_skill_error_memory` (
                    `memoryId` TEXT NOT NULL,
                    `childProfileId` TEXT NOT NULL,
                    `lessonId` TEXT NOT NULL,
                    `masteryTag` TEXT NOT NULL,
                    `wrongCount` INTEGER NOT NULL,
                    `consecutiveCorrect` INTEGER NOT NULL,
                    `nextReviewAtUtc` INTEGER NOT NULL,
                    `resolved` INTEGER NOT NULL,
                    `lastItemFingerprint` TEXT NOT NULL,
                    `updatedAtUtc` INTEGER NOT NULL,
                    `syncId` TEXT NOT NULL,
                    `syncStatus` TEXT NOT NULL,
                    PRIMARY KEY(`memoryId`)
                )""".trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_skill_error_child_lesson_tag` ON `lesson_skill_error_memory` (`childProfileId`, `lessonId`, `masteryTag`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_skill_error_due` ON `lesson_skill_error_memory` (`childProfileId`, `nextReviewAtUtc`, `resolved`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_skill_error_sync_id` ON `lesson_skill_error_memory` (`syncId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_skill_error_sync_status` ON `lesson_skill_error_memory` (`syncStatus`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11
    )
}

private fun SupportSQLiteDatabase.addColumnIfMissing(
    table: String,
    column: String,
    definition: String
) {
    if (!hasColumn(table, column)) {
        execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
    }
}

private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        cursor.anyRow { getString(nameIndex) == column }
    }

private inline fun Cursor.anyRow(predicate: Cursor.() -> Boolean): Boolean {
    while (moveToNext()) {
        if (predicate()) return true
    }
    return false
}
