package com.menter.treatmentvault.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.menter.treatmentvault.TreatmentVaultApplication
import com.menter.treatmentvault.data.CategoryEntity

private val directUtilityCategoryKeys = setOf("smart_input", "timeline", "import", "settings")

fun isFolderCategory(category: CategoryEntity): Boolean = category.key !in directUtilityCategoryKeys

fun categoryOpenRoute(category: CategoryEntity): String =
    if (isFolderCategory(category)) "folder/${category.key}" else category.route

fun categoryIcon(key: String): ImageVector = when (key) {
    "mic" -> Icons.Default.Mic
    "timeline" -> Icons.Default.Timeline
    "description" -> Icons.Default.Description
    "medical" -> Icons.Default.MedicalServices
    "medication" -> Icons.Default.Medication
    "heart" -> Icons.Default.MonitorHeart
    "thermostat" -> Icons.Default.Thermostat
    "question" -> Icons.Default.QuestionAnswer
    "payments" -> Icons.Default.Payments
    "upload" -> Icons.Default.UploadFile
    "backup" -> Icons.Default.Backup
    "favorite" -> Icons.Default.Favorite
    "star" -> Icons.Default.Star
    "science" -> Icons.Default.Science
    "event" -> Icons.Default.Event
    "note" -> Icons.Default.Notes
    "shield" -> Icons.Default.HealthAndSafety
    else -> Icons.Default.Folder
}

@Composable
fun categoryDisplayName(key: String, fallback: String): String {
    val app = LocalContext.current.applicationContext as TreatmentVaultApplication
    val categories by app.database.dao().categories().collectAsState(initial = emptyList())
    return categories.firstOrNull { it.key == key }?.name ?: fallback
}
