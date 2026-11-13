package com.menter.treatmentvault

import android.content.Context
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.menter.treatmentvault.data.CustomEntryEntity
import com.menter.treatmentvault.ui.screens.FloatingRecorderService
import com.menter.treatmentvault.ui.screens.attachmentFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Runs the real app/theme, with synthetic recordings confined to this test device. */
@OptIn(ExperimentalTestApi::class)
class RecordingAppearanceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val app get() = rule.activity.application as TreatmentVaultApplication
    private val entryIds = mutableListOf<Long>()
    private val audioFiles = mutableListOf<File>()
    private val sliderMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
    private val listMatcher = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)

    @Test fun recordingControlsMatchPreviewAndKeepPlaybackBehavior() {
        seedRecordings()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("悬浮录音").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("悬浮录音").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(listMatcher).fetchSemanticsNodes().isNotEmpty() }
        // Keep the existing settings/search above the viewport, just as in the supplied screenshot.
        rule.onNode(listMatcher).performScrollToIndex(2)
        rule.waitUntil(10_000) { rule.onAllNodes(sliderMatcher).fetchSemanticsNodes().size == 3 }
        rule.waitUntil(10_000) {
            firstSlider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range.endInclusive > 500_000f
        }
        rule.onAllNodes(sliderMatcher).assertCountEquals(3)
        rule.onNodeWithText("添加标记").assertDoesNotExist()
        saveScreenshot("01-collapsed")

        // Dragging an idle recording changes its seek position, without playback or expansion.
        firstSlider().performTouchInput {
            swipe(Offset(width * 0.05f, centerY), Offset(width * 0.4f, centerY), durationMillis = 500)
        }
        rule.waitForIdle()
        rule.onNodeWithText("添加标记").assertDoesNotExist()
        rule.onAllNodesWithContentDescription("暂停").assertCountEquals(0)
        val position = firstSlider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue("Dragging must update the recording position", position in 100_000f..300_000f)
        assertThinTrackAndRoundThumb()
        saveScreenshot("02-collapsed-seek")

        rule.onAllNodesWithContentDescription("播放")[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("暂停").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("添加标记").assertIsDisplayed()
        rule.onNodeWithText("用药说明").assertIsDisplayed()
        rule.onNodeWithText("复查时间").assertIsDisplayed()
        rule.onNodeWithContentDescription("收起详情和标记").assertIsDisplayed()
        saveScreenshot("03-expanded-playing")

        rule.onNodeWithContentDescription("收起详情和标记").performClick()
        rule.onNodeWithText("添加标记").assertDoesNotExist()
        rule.onNodeWithContentDescription("暂停").assertIsDisplayed()
        val before = firstSlider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        rule.waitUntil(5_000) {
            firstSlider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current > before + 300f
        }
        saveScreenshot("04-collapsed-playing")

        rule.onNodeWithContentDescription("暂停").performClick()
        rule.onNodeWithText("添加标记").assertDoesNotExist()
        rule.onAllNodesWithContentDescription("暂停").assertCountEquals(0)
        saveScreenshot("05-collapsed-paused")

        rule.onAllNodesWithContentDescription("播放")[0].performClick()
        rule.onNodeWithText("添加标记").assertIsDisplayed()
        rule.onNodeWithContentDescription("暂停").performClick()
        rule.onNodeWithText("添加标记").assertIsDisplayed()
        saveScreenshot("06-expanded-paused")
    }

    private fun firstSlider() = rule.onAllNodes(sliderMatcher)[0]

    private fun saveScreenshot(name: String) {
        rule.waitForIdle()
        device.executeShellCommand("mkdir -p /sdcard/Download/recording-appearance")
        device.executeShellCommand("screencap -p /sdcard/Download/recording-appearance/$name.png")
    }

    private fun assertThinTrackAndRoundThumb() {
        val bitmap = firstSlider().captureToImage().asAndroidBitmap()
        val density = rule.activity.resources.displayMetrics.density
        fun inkHeight(x: Int, shade: Int): Int = (0 until bitmap.height).count { y ->
            val pixel = bitmap.getPixel(x, y)
            abs(Color.red(pixel) - shade) <= 8 && abs(Color.green(pixel) - shade) <= 8 && abs(Color.blue(pixel) - shade) <= 8
        }
        val active = inkHeight((bitmap.width * 0.2f).toInt(), 138)
        val inactive = inkHeight((bitmap.width * 0.75f).toInt(), 212)
        assertTrue("Played track must be a visible thin line, actual=$active px", active in (2f * density).toInt()..(6f * density).toInt())
        assertTrue("Unplayed track must remain visible, actual=$inactive px", inactive in (2f * density).toInt()..(6f * density).toInt())
        val darkHeights = (0 until bitmap.width).map { inkHeight(it, 138) }
        val thumbHeight = darkHeights.maxOrNull() ?: 0
        val widePart = darkHeights.count { it > 6f * density }
        assertTrue("Thumb must be a small circle rather than a tall bar, actual=$thumbHeight px", thumbHeight in (8f * density).toInt()..(12f * density).toInt())
        assertTrue("Thumb must have visible round width, actual=$widePart px", widePart >= 6f * density && widePart <= 12f * density)
    }

    private fun seedRecordings() {
        app.getSharedPreferences(FloatingRecorderService.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("audio_grid_mode", false)
            .putBoolean(FloatingRecorderService.PREF_ENABLED, false)
            .commit()
        val titles = listOf("医生沟通", "病房记录", "复查安排")
        val durations = listOf(516, 204, 728)
        val dates = listOf("2026-11-13 09:42:00", "2026-11-12 18:10:00", "2026-11-11 14:30:00")
        titles.forEachIndexed { index, title ->
            val file = attachmentFile(app, "recording-appearance-$index.wav")
            writeSilentWave(file, durations[index])
            audioFiles += file
            val markers = if (index == 1) "" else "\n[[audiomark:86000:用药说明]]\n[[audiomark:342000:复查时间]]"
            entryIds += runBlocking {
                app.database.dao().saveCustomEntry(
                    CustomEntryEntity(
                        categoryId = "floating_audio", title = title, date = dates[index],
                        content = "[[audio:${file.name}]]$markers", createdAt = (3 - index).toLong(),
                    ),
                )
            }
        }
    }

    private fun writeSilentWave(file: File, seconds: Int) {
        val rate = 8_000
        val dataSize = seconds * rate * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(dataSize + 36).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(rate)
            .putInt(rate * 2).putShort(2.toShort()).putShort(16.toShort())
            .put("data".toByteArray()).putInt(dataSize).array()
        file.outputStream().buffered().use { out ->
            out.write(header)
            val zeros = ByteArray(8_192)
            var remaining = dataSize
            while (remaining > 0) {
                val count = minOf(remaining, zeros.size)
                out.write(zeros, 0, count)
                remaining -= count
            }
        }
    }

    @After fun removeFixtures() {
        runBlocking { entryIds.forEach { app.database.dao().deleteCustomEntryById(it) } }
        audioFiles.forEach(File::delete)
    }
}
