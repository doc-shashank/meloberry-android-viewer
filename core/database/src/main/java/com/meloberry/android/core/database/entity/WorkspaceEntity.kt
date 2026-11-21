package com.meloberry.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val kind: String,
    val subType: String,
    val itemCount: Int,
    val workspaceJson: String,
    val layoutsJson: String,
    val storageDirPath: String,
    val importedAtEpochMs: Long
)
