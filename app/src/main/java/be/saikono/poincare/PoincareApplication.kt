package be.saikono.poincare

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import be.saikono.poincare.data.local.DatabaseMigrations
import be.saikono.poincare.data.local.PoincareDatabase
import be.saikono.poincare.data.sync.PoincareWorkerFactory
import be.saikono.poincare.repository.FakeRemoteProgressRepository
import be.saikono.poincare.repository.LocalProgressRepository
import androidx.room.Room

class PoincareApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() {
            try {
                val database = Room.databaseBuilder(
                    applicationContext,
                    PoincareDatabase::class.java,
                    "poincare-db"
                ).addMigrations(DatabaseMigrations.MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                
                val progressRepository = LocalProgressRepository(database.progressDao())
                val remoteProgressRepository = FakeRemoteProgressRepository()
                
                val workerFactory = PoincareWorkerFactory(progressRepository, remoteProgressRepository)
                
                return Configuration.Builder()
                    .setWorkerFactory(workerFactory)
                    .build()
            } catch (error: Throwable) {
                Log.e("PoincareStartup", "WorkManager configuration failed", error)
                throw error
            }
        }
}
