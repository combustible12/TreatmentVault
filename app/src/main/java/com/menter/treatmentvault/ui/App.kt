package com.menter.treatmentvault.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.menter.treatmentvault.ui.screens.*

private val WarmBeigeScheme = lightColorScheme(
    primary = Color(0xFF8F6F52),
    onPrimary = Color(0xFFFFFBF6),
    primaryContainer = Color(0xFFEEDFCC),
    onPrimaryContainer = Color(0xFF3A2B20),
    secondary = Color(0xFFA4876B),
    onSecondary = Color(0xFFFFFBF6),
    secondaryContainer = Color(0xFFF3E7D9),
    onSecondaryContainer = Color(0xFF403227),
    tertiary = Color(0xFF9A806C),
    onTertiary = Color(0xFFFFFBF6),
    background = Color(0xFFFFFAF4),
    onBackground = Color(0xFF2F2A25),
    surface = Color(0xFFFFFAF4),
    onSurface = Color(0xFF2F2A25),
    surfaceVariant = Color(0xFFF4EADF),
    onSurfaceVariant = Color(0xFF5B5047),
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFFCF5ED),
    surfaceContainer = Color(0xFFF7EDE3),
    surfaceContainerHigh = Color(0xFFF1E4D7),
    surfaceContainerHighest = Color(0xFFEBDCCF),
    outline = Color(0xFFB9AA9C),
    outlineVariant = Color(0xFFDDD0C4),
)

@Composable
fun TreatmentVaultApp() {
    MaterialTheme(colorScheme = WarmBeigeScheme) {
        val nav = rememberNavController()
        NavHost(nav, "home") {
            composable("home") { HomeScreen { nav.navigate(it) } }
            composable("notes") { AllRecordsScreen(back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("todos") { TodoCenterScreen(back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("note/new/{categoryId}") { entry ->
                NoteEditorScreen(
                    noteId = 0L,
                    initialCategoryId = entry.arguments?.getString("categoryId"),
                    back = { nav.popBackStack() },
                )
            }
            composable("note/{noteId}") { entry ->
                NoteEditorScreen(
                    noteId = entry.arguments?.getString("noteId")?.toLongOrNull() ?: 0L,
                    initialCategoryId = null,
                    back = { nav.popBackStack() },
                )
            }
            composable("categories") { EditableCategoryManagementScreen { nav.popBackStack() } }
            composable("structure") { StructureScreen(back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("update") { UpdateScreen { nav.popBackStack() } }
            composable("folder/{categoryId}") { entry ->
                FolderScreen(
                    categoryId = entry.arguments?.getString("categoryId").orEmpty(),
                    back = { nav.popBackStack() },
                    go = { nav.navigate(it) },
                )
            }
            composable("custom/{categoryId}") { entry ->
                FolderScreen(
                    categoryId = entry.arguments?.getString("categoryId").orEmpty(),
                    back = { nav.popBackStack() },
                    go = { nav.navigate(it) },
                )
            }
            composable("smart_input") { SmartInputScreen { nav.popBackStack() } }
            composable("floating_audio") { FloatingAudioScreen { nav.popBackStack() } }

            // System category roots always open the unified folder UI. The specialized
            // screens below are kept only for opening/editing one concrete structured record.
            composable("supplies") {
                FolderScreen(categoryId = "supplies", back = { nav.popBackStack() }, go = { nav.navigate(it) })
            }
            composable("supplies/{itemId}") { entry ->
                SupplyScreen(back = { nav.popBackStack() }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L)
            }

            composable("exams") {
                FolderScreen(categoryId = "exams", back = { nav.popBackStack() }, go = { nav.navigate(it) })
            }
            composable("exams/{itemId}") { entry ->
                ExamScreen(back = { nav.popBackStack() }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L)
            }

            composable("treatment") {
                FolderScreen(categoryId = "treatment", back = { nav.popBackStack() }, go = { nav.navigate(it) })
            }
            composable("treatment/plan/{itemId}") { entry ->
                TreatmentScreen(back = { nav.popBackStack() }, initialPlanId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L)
            }
            composable("treatment/record/{itemId}") { entry ->
                TreatmentScreen(back = { nav.popBackStack() }, initialRecordId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L)
            }

            composable("symptoms") {
                FolderScreen(categoryId = "symptoms", back = { nav.popBackStack() }, go = { nav.navigate(it) })
            }
            composable("symptoms/{itemId}") { entry ->
                SymptomScreen(back = { nav.popBackStack() }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L)
            }

            composable("temperature") {
                FolderScreen(categoryId = "temperature", back = { nav.popBackStack() }, go = { nav.navigate(it) })
            }
            composable("temperature/{itemId}") { entry ->
                TemperatureScreen(back = { nav.popBackStack() }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L)
            }

            composable("questions") {
                FolderScreen(categoryId = "questions", back = { nav.popBackStack() }, go = { nav.navigate(it) })
            }
            composable("questions/{itemId}") { entry ->
                QuestionScreen(back = { nav.popBackStack() }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L)
            }

            composable("expenses") {
                FolderScreen(categoryId = "expenses", back = { nav.popBackStack() }, go = { nav.navigate(it) })
            }
            composable("expenses/{itemId}") { entry ->
                ExpenseScreen(back = { nav.popBackStack() }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L)
            }

            composable("timeline") { TimelineScreen(back = { nav.popBackStack() }, go = { nav.navigate(it) }) }
            composable("timeline/{itemId}") { entry ->
                TimelineScreen(back = { nav.popBackStack() }, go = { nav.navigate(it) }, initialEditId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L)
            }
            composable("import") { ImportScreen { nav.popBackStack() } }
            composable("settings") { SettingsScreen { nav.popBackStack() } }
        }
    }
}
