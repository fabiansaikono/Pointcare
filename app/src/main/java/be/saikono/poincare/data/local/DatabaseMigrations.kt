package be.saikono.poincare.data.local

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN hiddenMainCategories TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE profiles ADD COLUMN hiddenSubcategories TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Rename columns safely: if they don't exist, we skip
            runCatching {
                db.execSQL("ALTER TABLE profiles RENAME COLUMN GeometryLevel TO geometryLevel")
            }.onFailure { Log.w("DatabaseMigrations", "Failed to rename GeometryLevel, might already be renamed") }
            
            runCatching {
                db.execSQL("ALTER TABLE profiles RENAME COLUMN WorldKnowledgeLevel TO worldKnowledgeLevel")
            }.onFailure { Log.w("DatabaseMigrations", "Failed to rename WorldKnowledgeLevel, might already be renamed") }
        }
    }
}