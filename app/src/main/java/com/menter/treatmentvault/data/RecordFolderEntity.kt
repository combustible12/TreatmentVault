package com.menter.treatmentvault.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    primaryKeys = ["ownerType", "ownerId"],
    indices = [Index(value = ["folderKey"])],
)
data class RecordFolderEntity(
    val ownerType: String,
    val ownerId: Long,
    val folderKey: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
