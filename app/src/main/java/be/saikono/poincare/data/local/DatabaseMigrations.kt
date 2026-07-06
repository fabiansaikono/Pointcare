package be.saikono.poincare.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN hiddenMainCategories TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE profiles ADD COLUMN hiddenSubcategories TEXT NOT NULL DEFAULT ''")
        }
    }
}