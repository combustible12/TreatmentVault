package com.menter.treatmentvault.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImageShareTest {
    @Test
    fun shareableImagesIgnoreMissingEmptyAndDuplicateFiles() {
        val dir = createTempDir(prefix = "image-share-")
        try {
            val first = File(dir, "first.jpg").apply { writeBytes(byteArrayOf(1)) }
            val empty = File(dir, "empty.jpg").apply { createNewFile() }
            val missing = File(dir, "missing.jpg")

            assertEquals(listOf(first), shareableImageFiles(listOf(first, first, empty, missing)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun usersCanSelectIndividuallyThenSelectOrClearAll() {
        val dir = createTempDir(prefix = "image-share-selection-")
        try {
            val first = File(dir, "first.jpg").apply { writeBytes(byteArrayOf(1)) }
            val second = File(dir, "second.jpg").apply { writeBytes(byteArrayOf(2)) }
            val oneSelected = toggleSharedImage(emptySet(), first)
            assertEquals(setOf(first.absolutePath), oneSelected)

            val allSelected = toggleAllSharedImages(listOf(first, second), oneSelected)
            assertEquals(setOf(first.absolutePath, second.absolutePath), allSelected)
            assertTrue(toggleAllSharedImages(listOf(first, second), allSelected).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
