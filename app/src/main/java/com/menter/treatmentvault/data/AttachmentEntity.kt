package com.menter.treatmentvault.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["ownerType", "ownerId"])])
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: String,
    val ownerId: Long,
    val fileName: String,
    val createdAt: Long = System.currentTimeMillis(),
)
