package be.saikono.poincare.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class PoincareDatabaseMigrationTest {
    private lateinit var context: Context
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "poincare-migration-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration8To11PreservesLegacyRowsCreatesLessonTablesAndCanonicalizesDefaults() {
        createVersion8Database()

        val migrated = openMigratedDatabase()
        try {
            val sqlite = migrated.openHelper.writableDatabase
            sqlite.query(
                "SELECT name, selectedGrades, giftedModeEnabled FROM profiles WHERE id = 'child-1'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Ada", cursor.getString(0))
                assertEquals("MATH=1,2;READING=1", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
            }
            assertRepresentativeRowsPreserved(sqlite)
            assertLessonTablesCreatedEmpty(sqlite)
            assertProfileColumnsHaveNoSqlDefaults(sqlite)
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migration7To11UsesTheCompleteRegisteredChainWithoutLosingData() {
        createVersion7Database()

        val migrated = openMigratedDatabase()
        try {
            val sqlite = migrated.openHelper.writableDatabase
            sqlite.query(
                "SELECT name, selectedGrades, giftedModeEnabled FROM profiles WHERE id = 'child-1'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Ada", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
            }
            assertRepresentativeRowsPreserved(sqlite)
            assertLessonTablesCreatedEmpty(sqlite)
            assertProfileColumnsHaveNoSqlDefaults(sqlite)
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migration4To11UsesTheCompleteRegisteredChainWithoutLosingData() {
        createVersion4Database()

        val migrated = openMigratedDatabase()
        try {
            val sqlite = migrated.openHelper.writableDatabase
            sqlite.query(
                """SELECT hiddenMainCategories, hiddenSubcategories,
                    showUnassignedExercises, giftedModeEnabled, selectedGrades
                    FROM profiles WHERE id = 'child-1'""".trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals("", cursor.getString(4))
            }
            assertRepresentativeRowsPreserved(sqlite, expectQueuedEvent = false)
            assertEquals(0, sqlite.singleInt("SELECT COUNT(*) FROM sync_queue"))
            assertLessonTablesCreatedEmpty(sqlite)
            assertProfileColumnsHaveNoSqlDefaults(sqlite)
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migration10To11GrandfathersCompletedLessonsAndCreatesV2EvidenceTables() {
        createVersion10DatabaseWithCompletedLesson()

        val migrated = openMigratedDatabase()
        try {
            val sqlite = migrated.openHelper.writableDatabase
            sqlite.query(
                "SELECT gateStateV2, legacyEvidence FROM lesson_progress WHERE progressId = 'legacy-progress'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("PASSED_LEGACY", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
            assertLessonTablesCreatedEmpty(sqlite, expectedLegacyProgress = 1)
        } finally {
            migrated.close()
        }
    }

    private fun openMigratedDatabase(): PoincareDatabase =
        Room.databaseBuilder(context, PoincareDatabase::class.java, databaseName)
            .addMigrations(*DatabaseMigrations.ALL)
            .allowMainThreadQueries()
            .build()
            .also { it.openHelper.writableDatabase }

    private fun createVersion8Database() {
        val db = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            createProfileTableV8(db)
            createSharedTables(db, includeSyncQueue = true)
            seedProfileV8(db)
            seedSharedRows(db, includeQueuedEvent = true)
            db.version = 8
        } finally {
            db.close()
        }
    }

    private fun createVersion7Database() {
        val db = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            createProfileTableV7(db)
            createSharedTables(db, includeSyncQueue = true)
            seedProfileV7(db)
            seedSharedRows(db, includeQueuedEvent = true)
            db.version = 7
        } finally {
            db.close()
        }
    }

    private fun createVersion4Database() {
        val db = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            createProfileTableV4(db)
            createSharedTables(db, includeSyncQueue = false)
            seedProfileV4(db)
            seedSharedRows(db, includeQueuedEvent = false)
            db.version = 4
        } finally {
            db.close()
        }
    }

    private fun createVersion10DatabaseWithCompletedLesson() {
        val db = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            val schema = JSONObject(
                context.assets.open("be.saikono.poincare.data.local.PoincareDatabase/10.json")
                    .bufferedReader().use { it.readText() }
            ).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (entityIndex in 0 until entities.length()) {
                val entity = entities.getJSONObject(entityIndex)
                val tableName = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", tableName))
                val indices = entity.optJSONArray("indices") ?: continue
                for (index in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(index).getString("createSql").replace("${'$'}{TABLE_NAME}", tableName))
                }
            }
            db.execSQL(
                """INSERT INTO lesson_progress VALUES (
                    'legacy-progress', 'child-1', 'legacy-unit', 'legacy-lesson',
                    'COMPLETED', 'PROFICIENT', 80, 80, 80, 1,
                    1000, 2000, 2000, 2000, 'legacy-progress', 'SYNCED', 1
                )""".trimIndent()
            )
            db.version = 10
        } finally {
            db.close()
        }
    }

    private fun createProfileTableV8(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE `profiles` (
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
                `selectedGrades` TEXT NOT NULL DEFAULT '',
                `hiddenMainCategories` TEXT NOT NULL DEFAULT '',
                `hiddenSubcategories` TEXT NOT NULL DEFAULT '',
                `showUnassignedExercises` INTEGER NOT NULL DEFAULT 0,
                `giftedModeEnabled` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )""".trimIndent()
        )
    }

    private fun createProfileTableV7(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE `profiles` (
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
                `hiddenMainCategories` TEXT NOT NULL DEFAULT '',
                `hiddenSubcategories` TEXT NOT NULL DEFAULT '',
                `showUnassignedExercises` INTEGER NOT NULL DEFAULT 0,
                `giftedModeEnabled` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )""".trimIndent()
        )
    }

    private fun createProfileTableV4(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE `profiles` (
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
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )""".trimIndent()
        )
    }

    private fun createSharedTables(db: SQLiteDatabase, includeSyncQueue: Boolean) {
        db.execSQL(
            """CREATE TABLE `progress` (
                `id` TEXT NOT NULL,
                `childProfileId` TEXT NOT NULL,
                `exerciseId` TEXT NOT NULL,
                `domain` TEXT NOT NULL,
                `skill` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `answerCorrect` INTEGER NOT NULL,
                `responseTimeMs` INTEGER NOT NULL,
                `hintsUsed` INTEGER NOT NULL,
                `retryCount` INTEGER NOT NULL,
                `languageSwitches` INTEGER NOT NULL,
                `translationsUsed` INTEGER NOT NULL,
                `rewardStampsEarned` INTEGER NOT NULL,
                `syncStatus` TEXT NOT NULL,
                `metadataJson` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE `parent_rewards` (
                `id` TEXT NOT NULL,
                `childProfileId` TEXT NOT NULL,
                `tier` INTEGER NOT NULL,
                `labelsJson` TEXT NOT NULL,
                `stampsRequired` INTEGER NOT NULL,
                `currentStamps` INTEGER NOT NULL,
                `isClaimed` INTEGER NOT NULL,
                `requiresParentApproval` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE `stamp_cards` (
                `id` TEXT NOT NULL,
                `childProfileId` TEXT NOT NULL,
                `totalStamps` INTEGER NOT NULL,
                `lastUpdated` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )""".trimIndent()
        )
        if (includeSyncQueue) {
            db.execSQL(
                """CREATE TABLE `sync_queue` (
                    `eventId` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`eventId`)
                )""".trimIndent()
            )
        }
    }

    private fun seedProfileV8(db: SQLiteDatabase) {
        db.execSQL(
            """INSERT INTO profiles (
                id, name, avatarId, rocketColorId, birthYear, appLanguage,
                exerciseLanguage, supportLanguage, speechLanguage,
                readingLevel, writingLevel, mathLevel, geometryLevel,
                worldKnowledgeLevel, puzzleLevel, selectedGrades,
                hiddenMainCategories, hiddenSubcategories, showUnassignedExercises,
                giftedModeEnabled, createdAt, updatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
            arrayOf<Any>(
                "child-1", "Ada", "avatar-1", "blue", 2018, "nl-BE",
                "nl-BE", "fr-BE", "nl-BE", "FIRST_GRADE", "FIRST_GRADE",
                "THIRD_GRADE", "SECOND_GRADE", "SECOND_GRADE", "SECOND_GRADE",
                "MATH=1,2;READING=1", "", "", 0, 1, 1000L, 2000L
            )
        )
    }

    private fun seedProfileV7(db: SQLiteDatabase) {
        db.execSQL(
            """INSERT INTO profiles (
                id, name, avatarId, rocketColorId, birthYear, appLanguage,
                exerciseLanguage, supportLanguage, speechLanguage,
                readingLevel, writingLevel, mathLevel, geometryLevel,
                worldKnowledgeLevel, puzzleLevel, hiddenMainCategories,
                hiddenSubcategories, showUnassignedExercises, giftedModeEnabled,
                createdAt, updatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
            arrayOf<Any>(
                "child-1", "Ada", "avatar-1", "blue", 2018, "nl-BE",
                "nl-BE", "fr-BE", "nl-BE", "FIRST_GRADE", "FIRST_GRADE",
                "THIRD_GRADE", "SECOND_GRADE", "SECOND_GRADE", "SECOND_GRADE",
                "", "", 0, 1, 1000L, 2000L
            )
        )
    }

    private fun seedProfileV4(db: SQLiteDatabase) {
        db.execSQL(
            """INSERT INTO profiles (
                id, name, avatarId, rocketColorId, birthYear, appLanguage,
                exerciseLanguage, supportLanguage, speechLanguage,
                readingLevel, writingLevel, mathLevel, geometryLevel,
                worldKnowledgeLevel, puzzleLevel, createdAt, updatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
            arrayOf<Any>(
                "child-1", "Ada", "avatar-1", "blue", 2018, "nl-BE",
                "nl-BE", "fr-BE", "nl-BE", "FIRST_GRADE", "FIRST_GRADE",
                "THIRD_GRADE", "SECOND_GRADE", "SECOND_GRADE", "SECOND_GRADE",
                1000L, 2000L
            )
        )
    }

    private fun seedSharedRows(db: SQLiteDatabase, includeQueuedEvent: Boolean) {
        db.execSQL(
            "INSERT INTO progress VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(
                "progress-1", "child-1", "exercise-1", "MATH", "addition",
                3000L, 1, 1250L, 0, 0, 0, 0, 1, "PENDING", "{}"
            )
        )
        db.execSQL(
            "INSERT INTO parent_rewards VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>("reward-1", "child-1", 1, "{}", 5, 2, 0, 1)
        )
        db.execSQL(
            "INSERT INTO stamp_cards VALUES (?, ?, ?, ?)",
            arrayOf<Any>("card-1", "child-1", 2, 4000L)
        )
        if (includeQueuedEvent) {
            db.execSQL(
                "INSERT INTO sync_queue VALUES (?, ?)",
                arrayOf<Any>("event-1", 5000L)
            )
        }
    }

    private fun assertRepresentativeRowsPreserved(
        sqlite: androidx.sqlite.db.SupportSQLiteDatabase,
        expectQueuedEvent: Boolean = true
    ) {
        assertEquals(1, sqlite.singleInt("SELECT COUNT(*) FROM progress WHERE id = 'progress-1'"))
        assertEquals(1, sqlite.singleInt("SELECT COUNT(*) FROM parent_rewards WHERE id = 'reward-1'"))
        assertEquals(1, sqlite.singleInt("SELECT COUNT(*) FROM stamp_cards WHERE id = 'card-1'"))
        if (expectQueuedEvent) {
            assertEquals(1, sqlite.singleInt("SELECT COUNT(*) FROM sync_queue WHERE eventId = 'event-1'"))
        }
    }

    private fun assertLessonTablesCreatedEmpty(
        sqlite: androidx.sqlite.db.SupportSQLiteDatabase,
        expectedLegacyProgress: Int = 0
    ) {
        assertEquals(1, sqlite.singleInt(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'lesson_progress'"
        ))
        assertEquals(1, sqlite.singleInt(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'lesson_attempts'"
        ))
        assertEquals(expectedLegacyProgress, sqlite.singleInt("SELECT COUNT(*) FROM lesson_progress"))
        assertEquals(0, sqlite.singleInt("SELECT COUNT(*) FROM lesson_attempts"))
        listOf(
            "lesson_stage_progress",
            "lesson_assessment_attempts",
            "lesson_question_attempts",
            "lesson_skill_error_memory"
        ).forEach { table ->
            assertEquals(1, sqlite.singleInt(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '$table'"
            ))
            assertEquals(0, sqlite.singleInt("SELECT COUNT(*) FROM $table"))
        }
        assertEquals(1, sqlite.singleInt(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_lesson_attempts_sync_id'"
        ))
    }

    private fun assertProfileColumnsHaveNoSqlDefaults(
        sqlite: androidx.sqlite.db.SupportSQLiteDatabase
    ) {
        val historicallyDefaulted = setOf(
            "selectedGrades",
            "hiddenMainCategories",
            "hiddenSubcategories",
            "showUnassignedExercises",
            "giftedModeEnabled"
        )
        val seen = linkedSetOf<String>()
        sqlite.query("PRAGMA table_info(`profiles`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                if (name in historicallyDefaulted) {
                    seen += name
                    assertNull("Unexpected SQL default for profiles.$name", cursor.getString(defaultIndex))
                }
            }
        }
        assertEquals(historicallyDefaulted, seen)
    }
}

private fun androidx.sqlite.db.SupportSQLiteDatabase.singleInt(sql: String): Int =
    query(sql).use { cursor ->
        check(cursor.moveToFirst()) { "Query returned no rows: $sql" }
        cursor.getInt(0)
    }
