package com.menter.treatmentvault.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineTodoEditorTest {
    @Test
    fun parsesPlainOpenAndCompletedLines() {
        val lines = parseInlineTodoLines("说明\n- [ ] 带身份证\n- [x] 充电线")
        assertEquals(3, lines.size)
        assertFalse(lines[0].isTodo)
        assertTrue(lines[1].isTodo)
        assertFalse(lines[1].isDone)
        assertEquals("带身份证", lines[1].text)
        assertTrue(lines[2].isDone)
    }

    @Test
    fun serializesChecklistWithoutLosingPlainText() {
        val value = serializeInlineTodoLines(
            listOf(
                InlineTodoLine("11:20 准备出门", isTodo = true),
                InlineTodoLine("证件放最上面"),
                InlineTodoLine("充电线", isTodo = true, isDone = true),
            )
        )
        assertEquals("- [ ] 11:20 准备出门\n证件放最上面\n- [x] 充电线", value)
    }

    @Test
    fun stripsChecklistMarkersForPreviewText() {
        assertEquals(
            "准备出门\n已经完成",
            stripInlineTodoMarkers("- [ ] 准备出门\n- [x] 已经完成"),
        )
    }
}
