package com.menter.treatmentvault.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.menter.treatmentvault.ui.screens.*

@Composable
fun TreatmentVaultApp() {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Color(0xFF176B5B), secondary = Color(0xFF4F7F75))
    ) {
        val nav = rememberNavController()
        NavHost(nav, "home") {
            composable("home") { HomeScreen { nav.navigate(it) } }
            composable("categories") { CategoryManagementScreen { nav.popBackStack() } }
            composable("update") { UpdateScreen { nav.popBackStack() } }
            composable("custom/{categoryId}") { entry ->
                CustomCategoryScreen(entry.arguments?.getString("categoryId").orEmpty()) { nav.popBackStack() }
            }
            composable("smart_input") { SmartInputScreen { nav.popBackStack() } }
            composable("supplies") { SupplyScreen { nav.popBackStack() } }
            composable("exams") { ExamScreen { nav.popBackStack() } }
            composable("treatment") { TreatmentScreen { nav.popBackStack() } }
            composable("symptoms") { SymptomScreen { nav.popBackStack() } }
            composable("temperature") { TemperatureScreen { nav.popBackStack() } }
            composable("questions") { QuestionScreen { nav.popBackStack() } }
            composable("expenses") { ExpenseScreen { nav.popBackStack() } }
            composable("timeline") { TimelineScreen { nav.popBackStack() } }
            composable("import") { ImportScreen { nav.popBackStack() } }
            composable("settings") { SettingsScreen { nav.popBackStack() } }
        }
    }
}
