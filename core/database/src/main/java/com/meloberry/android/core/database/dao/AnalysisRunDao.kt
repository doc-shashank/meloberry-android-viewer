package com.meloberry.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meloberry.android.core.database.entity.AnalysisRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisRunDao {

    @Query("SELECT * FROM analysis_runs ORDER BY savedAtEpochMs DESC")
    fun observeAll(): Flow<List<AnalysisRunEntity>>

    @Query("SELECT * FROM analysis_runs WHERE runId = :runId LIMIT 1")
    suspend fun getById(runId: String): AnalysisRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AnalysisRunEntity)

    @Query("DELETE FROM analysis_runs WHERE runId = :runId")
    suspend fun deleteById(runId: String)

    @Query("DELETE FROM analysis_runs WHERE workspaceId = :workspaceId")
    suspend fun deleteByWorkspaceId(workspaceId: String)
}
