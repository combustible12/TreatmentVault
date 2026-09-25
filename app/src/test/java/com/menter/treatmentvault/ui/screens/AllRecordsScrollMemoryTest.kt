package com.menter.treatmentvault.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllRecordsScrollMemoryTest {
    @Test fun capturePreservesMiddlePositionAndOffset() {
        val memory = AllRecordsScrollMemory().capture(18, 47)
        assertEquals(18, memory.index)
        assertEquals(47, memory.offset)
        assertTrue(memory.restorePending)
    }

    @Test fun normalReturnRestoresExactMiddlePosition() {
        val target = resolveAllRecordsRestoreTarget(AllRecordsScrollMemory(18, 47, true), 80)
        assertEquals(AllRecordsScrollTarget(18, 47), target)
    }

    @Test fun dataRefreshKeepsPositionWhenListStillLongEnough() {
        val target = resolveAllRecordsRestoreTarget(AllRecordsScrollMemory(35, 12, true), 50)
        assertEquals(AllRecordsScrollTarget(35, 12), target)
    }

    @Test fun shrinkingDataClampsToNearestValidPosition() {
        val target = resolveAllRecordsRestoreTarget(AllRecordsScrollMemory(35, 12, true), 5)
        assertEquals(AllRecordsScrollTarget(7, 12), target)
    }

    @Test fun emptyInitialEmissionDoesNotConsumePendingRestore() {
        assertNull(resolveAllRecordsRestoreTarget(AllRecordsScrollMemory(18, 47, true), 0))
    }

    @Test fun restoredStateDoesNotRestoreAgain() {
        assertNull(resolveAllRecordsRestoreTarget(AllRecordsScrollMemory(18, 47, false), 80))
    }

    @Test fun invalidNegativeCapturedValuesAreSanitized() {
        val memory = AllRecordsScrollMemory().capture(-3, -20)
        assertEquals(AllRecordsScrollMemory(0, 0, true), memory)
    }
}
