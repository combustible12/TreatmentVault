package com.menter.treatmentvault.ui.screens

import com.menter.treatmentvault.data.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordFolderUiTest {
    @Test
    fun `folder path includes all ancestors in display order`() {
        val categories = listOf(
            CategoryEntity(key = "root", name = "检查报告"),
            CategoryEntity(key = "hospital", name = "闽东医院报告", parentId = "root"),
            CategoryEntity(key = "ecg", name = "心电图", parentId = "hospital"),
        )

        assertEquals(
            listOf("检查报告", "闽东医院报告", "心电图"),
            folderPathFor("ecg", categories).map { it.name },
        )
    }

    @Test
    fun `folder path stops safely on malformed cycle`() {
        val categories = listOf(
            CategoryEntity(key = "a", name = "A", parentId = "b"),
            CategoryEntity(key = "b", name = "B", parentId = "a"),
        )

        assertEquals(2, folderPathFor("a", categories).size)
    }
    @Test
    fun `current folder can be unchecked while editing memberships`() {
        assertEquals(emptySet<String>(), toggleFolderSelection(setOf("inbox"), "inbox"))
    }

    @Test
    fun `a different folder can replace the old one without old folder being re-added`() {
        var selected: Set<String> = setOf("inbox")
        selected = toggleFolderSelection(selected, "patient")
        selected = toggleFolderSelection(selected, "inbox")
        assertEquals(setOf("patient"), selected)
    }

}
