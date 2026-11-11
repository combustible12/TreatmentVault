package com.menter.treatmentvault

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.menter.treatmentvault.data.CustomEntryEntity
import com.menter.treatmentvault.ui.screens.NoteEditorScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class KeyboardScrollDiagnosisTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val listMatcher = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)
    private val bodyMatcher = hasSetTextAction() and hasText("DIAG-LINE", substring = true)

    @Test fun ordinaryEditor() = exercise(false)
    @Test fun dialogEditor() = exercise(true)

    private fun exercise(dialog: Boolean) {
        val app = rule.activity.application as TreatmentVaultApplication
        val body = (1..35).joinToString("\n\n") { "DIAG-LINE $it 长文本滚动检查，这一行用来检查键盘打开之后是否能够继续滑动。" }
        val id = runBlocking { app.database.dao().saveCustomEntry(CustomEntryEntity(categoryId = "inbox", date = "2026-11-12", title = "滚动诊断", content = body)) }
        rule.runOnUiThread {
            rule.activity.setContent { MaterialTheme { Box(Modifier.imePadding()) { NoteEditorScreen(id, null, inDialog = dialog, back = {}) } } }
        }
        rule.waitUntil(10_000) { rule.onAllNodes(bodyMatcher).fetchSemanticsNodes().isNotEmpty() }
        val failures = mutableListOf<String>()
        repeat(6) { cycle ->
            rule.onNode(listMatcher).performScrollToIndex(1)
            rule.onNode(bodyMatcher).performTouchInput { click(Offset(30f, 30f)) }
            rule.onNode(bodyMatcher).performTextInputSelection(TextRange(if (cycle % 2 == 0) body.length / 2 else body.length))
            rule.onNode(bodyMatcher).performTextInput("\n追加输入-$cycle\n\n\n")
            device.waitForIdle()
            // Let real keyboard animation and the existing 600 ms autosave finish.
            Thread.sleep(1200)
            rule.waitForIdle()
            snapshot("${dialog}_${cycle}_before")
            repeat(12) { attempt ->
                if (!footerVisible()) {
                    rule.onNode(listMatcher).performTouchInput {
                        val x = when { cycle < 2 -> centerX; cycle < 4 -> 6f; else -> width - 10f }
                        swipe(Offset(x, height * .85f), Offset(x, height * .15f), 350)
                    }
                    rule.waitForIdle()
                    logRange("dialog=$dialog cycle=$cycle swipe=$attempt")
                }
            }
            snapshot("${dialog}_${cycle}_after")
            val visible = footerVisible()
            Log.i("KeyboardDiag", "RESULT dialog=$dialog cycle=$cycle footerVisible=$visible")
            if (!visible) failures += "dialog=$dialog cycle=$cycle"
            device.pressBack()
            Thread.sleep(500)
        }
        assertTrue("Unable to reach footer: $failures", failures.isEmpty())
    }

    private fun footerVisible(): Boolean = runCatching {
        val footer = rule.onNodeWithText("添加标签")
        val node = footer.fetchSemanticsNode()
        footer.isDisplayed() && node.boundsInRoot.height >= node.size.height - 1f
    }.getOrDefault(false)

    private fun logRange(stage: String) {
        val node = rule.onNode(listMatcher).fetchSemanticsNode()
        val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
        Log.i("KeyboardDiag", "$stage bounds=${node.boundsInRoot} scroll=${range.value()} max=${range.maxValue()} footer=${footerVisible()}")
    }

    private fun snapshot(name: String) {
        logRange(name)
        val dir = File(rule.activity.getExternalFilesDir(null), "diagnosis").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
