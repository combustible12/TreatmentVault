package com.menter.treatmentvault

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.menter.treatmentvault.data.CustomEntryEntity
import com.menter.treatmentvault.ui.screens.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class EditorWindowRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val body = (1..18).joinToString("\n\n") { "DIAG-LINE $it 检查已有笔记打开后的页面高度，以及页面底部的添加标签是否完整可见。" }
    private val bodyMatcher = hasSetTextAction() and hasText("DIAG-LINE", substring = true)
    private var inDialog = true
    private val listMatcher get() = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex).let {
        if (inDialog) it and hasAnyAncestor(isDialog()) else it
    }

    @Test fun existingNoteReopenAndKeyboardCycles() {
        val app = rule.activity.application as TreatmentVaultApplication
        val entryId = runBlocking { app.database.dao().saveCustomEntry(CustomEntryEntity(categoryId = "inbox", date = "2026-11-12", title = "ENTRY-DIAG", content = body)) }
        rule.runOnUiThread { rule.activity.setContent { AllRecordsScreen(back = {}, go = {}) } }
        repeat(2) { visit ->
            rule.waitUntil(10_000) { rule.onAllNodes(hasText("ENTRY-DIAG")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("ENTRY-DIAG").performClick()
            waitForBody()
            reachBottom("existing_${visit}_closed", keyboard = false)
            repeat(2) { cycle ->
                openKeyboard()
                rule.onNode(bodyMatcher).performTextInputSelection(androidx.compose.ui.text.TextRange(body.length / 2))
                rule.onNode(bodyMatcher).performTextInput("追加-$visit-$cycle")
                Thread.sleep(900)
                reachBottom("existing_${visit}_${cycle}_open", keyboard = true)
                device.pressBack()
                Thread.sleep(600)
                reachBottom("existing_${visit}_${cycle}_closed", keyboard = false)
            }
            device.pressBack()
            rule.waitUntil(10_000) { rule.onAllNodes(bodyMatcher).fetchSemanticsNodes().isEmpty() }
        }
        val saved = runBlocking { app.database.dao().customEntryById(entryId) }
        assertTrue("Edits lost after closing and reopening", saved?.content?.contains("追加-1-1") == true)
    }

    @Test fun sharedStructuredEditor() {
        rule.runOnUiThread {
            rule.activity.setContent {
                var shown by remember { mutableStateOf(true) }
                var value by remember { mutableStateOf(body) }
                if (shown) FullScreenEditorDialog(title = "编辑诊断", onDismiss = { shown = false }, onSave = { shown = false }) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item { BasicTextField(value, { value = it }, Modifier.fillMaxWidth()) }
                        item { Text("添加标签") }
                        item { Spacer(Modifier.height(28.dp)) }
                    }
                }
            }
        }
        waitForBody()
        repeat(2) { cycle ->
            openKeyboard()
            reachBottom("shared_${cycle}_open", true)
            device.pressBack()
            Thread.sleep(600)
            reachBottom("shared_${cycle}_closed", false)
        }
        rule.onNodeWithContentDescription("保存并关闭").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
    }

    private fun waitForBody() = rule.waitUntil(10_000) { rule.onAllNodes(bodyMatcher).fetchSemanticsNodes().isNotEmpty() }

    private fun openKeyboard() {
        rule.onNode(listMatcher).performScrollToIndex(0)
        if (rule.onAllNodes(bodyMatcher).fetchSemanticsNodes().isEmpty()) rule.onNode(listMatcher).performScrollToIndex(1)
        rule.onNode(bodyMatcher).performTouchInput { click(Offset(30f, 30f)) }
        Thread.sleep(1000)
    }

    private fun reachBottom(name: String, keyboard: Boolean) {
        repeat(12) { rule.onNode(listMatcher).performTouchInput { swipeUp() } }
        rule.waitForIdle()
        Thread.sleep(200)
        val node = rule.onNode(listMatcher).fetchSemanticsNode()
        val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
        val footer = rule.onNodeWithText("添加标签").fetchSemanticsNode()
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        val ime = ui.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.let { Rect().also(it::getBoundsInScreen) }
        val actual = requireNotNull(device.findObject(By.text("添加标签"))) { "Missing footer: $name" }.visibleBounds
        Log.i("KeyboardDiag", "$name scroll=${range.value()}/${range.maxValue()} compose=${footer.boundsInWindow} actual=$actual ime=$ime screen=${device.displayHeight}")
        device.executeShellCommand("mkdir -p /sdcard/Download/keyboard-diagnosis")
        device.executeShellCommand("screencap -p /sdcard/Download/keyboard-diagnosis/$name.png")
        assertTrue("Footer clipped: $name $actual expectedHeight=${footer.size.height}", actual.height() >= footer.size.height - 1)
        if (keyboard) {
            assertTrue("Keyboard did not open: $name ime=$ime", ime != null && ime.height() > 100)
            assertTrue("Footer under keyboard: $name footer=$actual ime=$ime", actual.bottom <= requireNotNull(ime).top)
        } else {
            assertTrue("Footer below screen: $name $actual", actual.bottom <= device.displayHeight)
        }
    }
}
