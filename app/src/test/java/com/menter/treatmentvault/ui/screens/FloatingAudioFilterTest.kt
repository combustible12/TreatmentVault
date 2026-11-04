package com.menter.treatmentvault.ui.screens

import com.menter.treatmentvault.data.CustomEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingAudioFilterTest {
    private fun recording(
        id: Long,
        title: String,
        marker: String? = null,
        createdAt: Long = id,
        updatedAt: Long = id,
    ) = CustomEntryEntity(
        id = id,
        categoryId = "floating_audio",
        title = title,
        content = marker?.let { contentWithAudioMarkers("[[audio:$id.m4a]]", listOf(AudioTimeMarker(1_000, it))) }
            ?: "[[audio:$id.m4a]]",
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun searchesRecordingNameAndMarkerLabel() {
        val entries = listOf(
            recording(1, "第一次问诊", "药量"),
            recording(2, "第二次问诊", "复查时间"),
        )

        assertEquals(listOf(1L), filterAudioRecordings(entries, "第一次", RecordingMarkerFilter.ALL).map { it.id })
        assertEquals(listOf(2L), filterAudioRecordings(entries, "复查时间", RecordingMarkerFilter.ALL).map { it.id })
    }

    @Test
    fun filtersMarkedAndUnmarkedRecordings() {
        val entries = listOf(recording(1, "有标记", "重点"), recording(2, "无标记"))

        assertEquals(listOf(1L), filterAudioRecordings(entries, "", RecordingMarkerFilter.MARKED).map { it.id })
        assertEquals(listOf(2L), filterAudioRecordings(entries, "", RecordingMarkerFilter.UNMARKED).map { it.id })
    }

    @Test
    fun markerEditDoesNotChangeStableOrder() {
        val olderButRecentlyEdited = recording(1, "旧录音", "新标记", createdAt = 100, updatedAt = 9_999)
        val newer = recording(2, "新录音", createdAt = 200, updatedAt = 200)

        assertEquals(
            listOf(2L, 1L),
            filterAudioRecordings(listOf(olderButRecentlyEdited, newer), "", RecordingMarkerFilter.ALL).map { it.id },
        )
    }
}
