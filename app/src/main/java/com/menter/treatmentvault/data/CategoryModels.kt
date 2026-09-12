package com.menter.treatmentvault.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity
data class CategoryEntity(
    @PrimaryKey val key: String,
    val name: String,
    val route: String = "",
    val iconKey: String = "folder",
    val sortOrder: Int = 0,
    val hidden: Boolean = false,
    val isSystem: Boolean = false,
    val smartEnabled: Boolean = true,
    val smartKeywords: String = "",
    val parentId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity
data class TagEntity(
    @PrimaryKey val key: String,
    val name: String,
    val colorKey: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(indices = [Index(value = ["categoryId"])])
data class CustomEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: String,
    val date: String = "",
    val title: String = "",
    val content: String = "",
    val tags: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)
