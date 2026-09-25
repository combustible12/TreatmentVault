package com.menter.treatmentvault.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllRecordsScrollMemoryTest {
    private val keys = (0 until 80).map { "record:$it" }

    @Test fun capturePreservesVisibleRecordAndPixelOffset() {
        val memory = AllRecordsScrollMemory().capture("record:15", 18, 47)
        assertEquals("record:15", memory.anchorKey)
        assertEquals(18, memory.fallbackIndex)
        assertEquals(47, memory.offset)
        assertTrue(memory.restorePending)
    }

    @Test fun normalReturnRestoresExactViewport() {
        val target = resolveAllRecordsRestoreTarget(
            AllRecordsScrollMemory("record:15", 18, 47, true), keys
        )
        assertEquals(AllRecordsScrollTarget(18, 47), target)
    }

    @Test fun dataRefreshAndReorderFollowsSameVisibleRecord() {
        val reordered = keys.toMutableList().apply {
            remove("record:15")
            add(0, "record:15")
        }
        val target = resolveAllRecordsRestoreTarget(
            AllRecordsScrollMemory("record:15", 18, 47, true), reordered
        )
        assertEquals(AllRecordsScrollTarget(3, 47), target)
    }

    @Test fun unrelatedRecordReorderStillFollowsAnchor() {
        val reordered = keys.toMutableList().apply {
            remove("record:70")
            add(0, "record:70")
        }
        val target = resolveAllRecordsRestoreTarget(
            AllRecordsScrollMemory("record:15", 18, 9, true), reordered
        )
        assertEquals(AllRecordsScrollTarget(19, 9), target)
    }

    @Test fun deletedAnchorFallsBackAndClampsAfterShrink() {
        val target = resolveAllRecordsRestoreTarget(
            AllRecordsScrollMemory("deleted", 35, 12, true), keys.take(5)
        )
        assertEquals(AllRecordsScrollTarget(7, 12), target)
    }

    @Test fun emptyInitialEmissionDoesNotConsumePendingRestore() {
        assertNull(resolveAllRecordsRestoreTarget(
            AllRecordsScrollMemory("record:15", 18, 47, true), emptyList()
        ))
    }

    @Test fun restoredStateDoesNotRestoreAgain() {
        assertNull(resolveAllRecordsRestoreTarget(
            AllRecordsScrollMemory("record:15", 18, 47, false), keys
        ))
    }

    @Test fun visibleAnchorMapsLazyColumnIndexPastHeaders() {
        assertEquals("record:0", visibleRecordAnchorKey(3, keys))
        assertEquals("record:15", visibleRecordAnchorKey(18, keys))
        assertNull(visibleRecordAnchorKey(2, keys))
    }

    @Test fun invalidNegativeCapturedValuesAreSanitized() {
        val memory = AllRecordsScrollMemory().capture("record:0", -3, -20)
        assertEquals(AllRecordsScrollMemory("record:0", 0, 0, true), memory)
    }
}
