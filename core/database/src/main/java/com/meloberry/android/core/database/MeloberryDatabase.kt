package com.meloberry.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
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
    exportSchema = false
)
abstract class MeloberryDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun analysisRunDao(): AnalysisRunDao
}
