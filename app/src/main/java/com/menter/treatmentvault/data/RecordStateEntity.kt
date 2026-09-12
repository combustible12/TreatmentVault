package com.menter.treatmentvault.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    primaryKeys = ["ownerType", "ownerId"],
    indices = [Index("pinned"), Index("favorite"), Index("archived"), Index("trashedAt")],
)
data class RecordStateEntity(
    val ownerType: String,
    val ownerId: Long,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val trashedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
