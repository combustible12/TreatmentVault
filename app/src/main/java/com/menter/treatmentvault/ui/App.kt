package com.menter.treatmentvault.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.menter.treatmentvault.ui.screens.*

private val SoftWarmWhiteScheme = lightColorScheme(
    primary = Color(0xFF292623),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5F2EE),
    onPrimaryContainer = Color(0xFF292623),
    secondary = Color(0xFF6D6862),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6F3EF),
    onSecondaryContainer = Color(0xFF37332F),
    tertiary = Color(0xFF9A7462),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF7EEE9),
    onTertiaryContainer = Color(0xFF49372E),
    background = Color(0xFFFEFDFC),
    onBackground = Color(0xFF242220),
    surface = Color(0xFFFEFDFC),
    onSurface = Color(0xFF242220),
    surfaceVariant = Color(0xFFF7F4F0),
    onSurfaceVariant = Color(0xFF68625D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF9F6),
    surfaceContainer = Color(0xFFF8F5F1),
    surfaceContainerHigh = Color(0xFFF4F1ED),
    surfaceContainerHighest = Color(0xFFF0ECE7),
    outline = Color(0xFFC8C1BA),
    outlineVariant = Color(0xFFE9E4DE),
)

@Composable
fun TreatmentVaultApp() {
    MaterialTheme(colorScheme = SoftWarmWhiteScheme) {
        val nav = rememberNavController()
        NavHost(nav, "home") {
            composable("home") { HomeScreen { nav.navigate(it) } }
            composable("notes") {
                AllRecordsScreen(
                    back = { nav.popBackStack() },
                    go = { nav.navigate(it) },
                )
            }
            composable("notes/manage") { AllRecordsScreen(back = { nav.popBackStack() }, go = { nav.navigate(it) }, startInManagementMode = true) }
            composable("todos") { TodoCenterScreen(back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("note/new/{categoryId}") { entry ->
                NoteEditorScreen(noteId = 0L, initialCategoryId = entry.arguments?.getString("categoryId"), back = { nav.popBackStack() })
            }
            composable("note/{noteId}") { entry ->
                NoteEditorScreen(noteId = entry.arguments?.getString("noteId")?.toLongOrNull() ?: 0L, initialCategoryId = null, back = { nav.popBackStack() })
            }
            composable("categories") { EditableCategoryManagementScreen { nav.popBackStack() } }
            composable("structure") { StructureScreen(back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("update") { UpdateScreen { nav.popBackStack() } }
            composable("folder/{categoryId}") { entry -> FolderScreen(categoryId = entry.arguments?.getString("categoryId").orEmpty(), back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("custom/{categoryId}") { entry -> FolderScreen(categoryId = entry.arguments?.getString("categoryId").orEmpty(), back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("smart_input") { SmartInputScreen { nav.popBackStack() } }
            composable("floating_audio") { FloatingAudioScreen { nav.popBackStack() } }
            composable("supplies") { FolderScreen(categoryId = "supplies", back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("supplies/{itemId}") { entry -> SupplyScreen(back = { nav.popBackStack() }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L) }
            composable("exams") { FolderScreen(categoryId = "exams", back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("exams/{itemId}") { entry -> ExamScreen(back = { nav.popBackStack() }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L) }
            composable("treatment") { FolderScreen(categoryId = "treatment", back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("treatment/plan/{itemId}") { entry -> TreatmentScreen(back = { nav.popBackStack() }, initialPlanId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L) }
            composable("treatment/record/{itemId}") { entry -> TreatmentScreen(back = { nav.popBackStack() }, initialRecordId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L) }
            composable("symptoms") { FolderScreen(categoryId = "symptoms", back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("symptoms/{itemId}") { entry -> SymptomScreen(back = { nav.popBackStack() }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L) }
            composable("temperature") { FolderScreen(categoryId = "temperature", back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("temperature/{itemId}") { entry -> TemperatureScreen(back = { nav.popBackStack() }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L) }
            composable("questions") { FolderScreen(categoryId = "questions", back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("questions/{itemId}") { entry -> QuestionScreen(back = { nav.popBackStack() }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L) }
            composable("expenses") { FolderScreen(categoryId = "expenses", back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("expenses/{itemId}") { entry -> ExpenseScreen(back = { nav.popBackStack() }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L) }
            composable("timeline") { TimelineScreen(back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("timeline/{itemId}") { entry -> TimelineScreen(back = { nav.popBackStack() }, go = { nav.navigate(it) }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L) }
            composable("import") { ImportScreen { nav.popBackStack() } }
            composable("settings") { SettingsScreen { nav.popBackStack() } }
        }
    }
}
