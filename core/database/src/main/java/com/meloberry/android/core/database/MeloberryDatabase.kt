package com.meloberry.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.meloberry.android.core.database.dao.AnalysisRunDao
import com.meloberry.android.core.database.dao.WorkspaceDao
import com.meloberry.android.core.database.entity.AnalysisRunEntity
import com.meloberry.android.core.database.entity.WorkspaceEntity

@Database(
    entities = [
        WorkspaceEntity::class,
        AnalysisRunEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class MeloberryDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun analysisRunDao(): AnalysisRunDao

    companion object {
        /**
         * Register explicit non-destructive [Migration] objects here whenever bumping [version]
         * (e.g., MIGRATION_2_3) so user workspaces and analysis history are always preserved.
         */
        val ALL_MIGRATIONS: Array<Migration> = emptyArray()
    }
}
