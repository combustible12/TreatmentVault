package com.menter.treatmentvault.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CreatedAtMigrationContractTest {
    @Test
    fun migration_8_9_preservesExistingNoteTimestamp() {
        val source = File("src/main/java/com/menter/treatmentvault/data/AppDatabase.kt").readText()
        assertTrue(source.contains("ALTER TABLE `CustomEntryEntity` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0"))
        assertTrue(source.contains("UPDATE `CustomEntryEntity` SET `createdAt`=`updatedAt` WHERE `createdAt`=0"))
        assertTrue(source.contains("MIGRATION_8_9"))
    }
}
