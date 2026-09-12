package com.menter.treatmentvault.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.menter.treatmentvault.TreatmentVaultApplication

@Composable
fun categoryDisplayName(key: String, fallback: String): String {
    val app = LocalContext.current.applicationContext as TreatmentVaultApplication
    val categories by app.database.dao().categories().collectAsState(initial = emptyList())
    return categories.firstOrNull { it.key == key }?.name ?: fallback
}
