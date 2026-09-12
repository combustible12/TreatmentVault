package com.menter.treatmentvault.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    primaryKeys = ["ownerType", "ownerId"],
    indices = [
        Index(value = ["pinned"]),
        Index(value = ["favorite"]),
        Index(value = ["archived"]),
        Index(value = ["trashedAt"]),
    ],
)
data class RecordStateEntity(
    val ownerType: String,
    val ownerId: Long,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val trashedAt: Long? = null,
    val displayType: String = "normal",
    val completed: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)
