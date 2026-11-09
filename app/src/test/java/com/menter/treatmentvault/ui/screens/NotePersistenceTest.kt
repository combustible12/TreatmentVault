package com.menter.treatmentvault.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePersistenceTest {
    @Test
    fun imageOnlyNoteCountsAsContent() {
        assertTrue(noteDraftHasContent("", "[[image:first.jpg]]", emptyList()))
    }

    @Test
    fun pendingImageCountsAsContentBeforeAttachmentRowExists() {
        assertTrue(noteDraftHasContent("", "", listOf("pending.jpg")))
    }

    @Test
    fun existingBlankNoteStillPersistsSoClearingContentIsNotLost() {
        assertTrue(shouldPersistNote(42L, "", "", emptyList()))
    }

    @Test
    fun tagsAloneAreSavedButWhitespaceDoesNotCreateANote() {
        assertTrue(shouldPersistNote(0L, "", "", emptyList(), "复查"))
        assertFalse(shouldPersistNote(0L, "", "", emptyList(), "  "))
    }

    @Test
    fun completelyBlankNewNoteDoesNotCreateJunkRecord() {
        assertFalse(shouldPersistNote(0L, "", "", emptyList()))
    }
}
