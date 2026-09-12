package com.menter.treatmentvault.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CategoryEntity

private val directUtilityCategoryKeys = setOf("smart_input", "timeline", "import", "settings")

fun isFolderCategory(category: CategoryEntity): Boolean = category.key !in directUtilityCategoryKeys

fun categoryOpenRoute(category: CategoryEntity): String =
    if (isFolderCategory(category)) "folder/${category.key}" else category.route

@Composable
fun categoryDisplayName(key: String, fallback: String): String {
    val app = LocalContext.current.applicationContext as TreatmentVaultApplication
    val categories by app.database.dao().categories().collectAsState(initial = emptyList())
    return categories.firstOrNull { it.key == key }?.name ?: fallback
}
