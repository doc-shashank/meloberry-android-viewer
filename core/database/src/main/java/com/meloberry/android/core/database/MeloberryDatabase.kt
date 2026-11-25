package com.meloberry.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                var hasDescription = false
                db.query("PRAGMA table_info(`workspaces`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0 && cursor.getString(nameIdx) == "description") {
                            hasDescription = true
                            break
                        }
                    }
                }
                if (!hasDescription) {
                    db.execSQL("ALTER TABLE `workspaces` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        /**
         * Register explicit non-destructive [Migration] objects here whenever bumping [version]
         * (e.g., MIGRATION_2_3) so user workspaces and analysis history are always preserved.
         */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
