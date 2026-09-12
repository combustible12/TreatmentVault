package com.menter.treatmentvault.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichContentTest {
    @Test
    fun textAndImageOrderRoundTripsExactly() {
        val original = "第一段\n\n[[image:a.jpg]]\n\n第二段\n\n[[image:b.jpg]]"
        val parsed = parseRichBody(original)
        assertEquals(original, serializeRichBody(parsed))
        assertEquals(listOf("a.jpg", "b.jpg"), richBodyImageNames(original))
    }

    @Test
    fun movedImageOrderIsRepresentedOnlyByBodyMarkers() {
        val blocks = mutableListOf<RichBlock>(
            RichBlock.TextBlock(text = "文字"),
            RichBlock.ImageBlock(fileName = "bottom.jpg"),
            RichBlock.ImageBlock(fileName = "top.jpg"),
        )
        val moved = blocks.removeAt(2)
        blocks.add(0, moved)
        val saved = serializeRichBody(blocks)
        assertEquals(listOf("top.jpg", "bottom.jpg"), richBodyImageNames(saved))
        assertEquals(listOf("top.jpg", "bottom.jpg"), richBodyImageNames(serializeRichBody(parseRichBody(saved))))
    }

    @Test
    fun legacyAttachmentsAreRecoveredOnlyWhenBodyHasNoImageMarkers() {
        val legacy = recoverLegacyImageMarkers("旧文字", listOf("a.jpg", "b.jpg"))
        assertEquals(listOf("a.jpg", "b.jpg"), richBodyImageNames(legacy))
        val alreadyStructured = "[[image:b.jpg]]\n\n文字\n\n[[image:a.jpg]]"
        assertEquals(alreadyStructured, recoverLegacyImageMarkers(alreadyStructured, listOf("a.jpg", "b.jpg", "orphan.jpg")))
    }

    @Test
    fun imageOnlyBodyIsRealContent() {
        assertTrue(richBodyHasContent("[[image:a.jpg]]"))
        assertFalse(richBodyHasContent("  \n  "))
    }
}
