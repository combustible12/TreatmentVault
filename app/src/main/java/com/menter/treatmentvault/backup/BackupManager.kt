package com.menter.treatmentvault.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.menter.treatmentvault.data.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class Snapshot(
    val supplies: List<SupplyEntity>,
    val exams: List<ExamEntity>,
    val plans: List<TreatmentPlanEntity>,
    val records: List<TreatmentRecordEntity>,
    val symptoms: List<SymptomEntity>,
    val temperatures: List<TemperatureEntity>? = null,
    val questions: List<QuestionEntity>,
    val expenses: List<ExpenseEntity>,
    val timeline: List<TimelineEntity>,
    val categories: List<CategoryEntity>? = null,
    val customEntries: List<CustomEntryEntity>? = null,
    val tags: List<TagEntity>? = null,
    val attachments: List<AttachmentEntity>? = null,
)

class BackupManager(private val context: Context, private val db: AppDatabase) {
    private val gson = Gson()
    private val dao = db.dao()
    private val attachmentDir = File(context.filesDir, "attachments").apply { mkdirs() }

    suspend fun export(uri: Uri) {
        val snap = Snapshot(
            dao.allSupplies(), dao.allExams(), dao.allPlans(), dao.allRecords(), dao.allSymptoms(),
            dao.allTemperatures(), dao.allQuestions(), dao.allExpenses(), dao.allTimeline(),
            dao.allCategories(), dao.allCustomEntries(), dao.allTags(), dao.allAttachments(),
        )
        ZipOutputStream(context.contentResolver.openOutputStream(uri)).use { z ->
            z.putNextEntry(ZipEntry("backup.json"))
            z.write(gson.toJson(snap).toByteArray())
            z.closeEntry()
            snap.attachments.orEmpty().map { it.fileName }.distinct().forEach { fileName ->
                val file = File(attachmentDir, fileName)
                if (file.isFile) {
                    z.putNextEntry(ZipEntry("attachments/$fileName"))
                    file.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }

    suspend fun restore(uri: Uri) {
        var json: String? = null
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(context.contentResolver.openInputStream(uri)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                when {
                    e.name == "backup.json" -> json = z.readBytes().toString(Charsets.UTF_8)
                    e.name.startsWith("attachments/") && !e.isDirectory -> files[e.name.removePrefix("attachments/")] = z.readBytes()
                }
                z.closeEntry()
                e = z.nextEntry
            }
        }
        val snap = gson.fromJson(json ?: error("无 backup.json"), Snapshot::class.java)
        db.withTransaction {
            dao.clearSupplies(); dao.clearExams(); dao.clearPlans(); dao.clearRecords(); dao.clearSymptoms()
            dao.clearTemperatures(); dao.clearQuestions(); dao.clearExpenses(); dao.clearTimeline(); dao.clearAttachments()
            snap.supplies.forEach { dao.saveSupply(it) }; snap.exams.forEach { dao.saveExam(it) }
            snap.plans.forEach { dao.savePlan(it) }; snap.records.forEach { dao.saveRecord(it) }
            snap.symptoms.forEach { dao.saveSymptom(it) }; snap.temperatures.orEmpty().forEach { dao.saveTemperature(it) }
            snap.questions.forEach { dao.saveQuestion(it) }; snap.expenses.forEach { dao.saveExpense(it) }
            snap.timeline.forEach { dao.saveTimeline(it) }
            snap.categories?.let { list -> dao.clearCategories(); list.forEach { dao.saveCategory(it) } }
            snap.customEntries?.let { list -> dao.clearCustomEntries(); list.forEach { dao.saveCustomEntry(it) } }
            snap.tags?.let { list -> dao.clearTags(); list.forEach { dao.saveTag(it) } }
            snap.attachments.orEmpty().forEach { dao.saveAttachment(it) }
        }
        files.forEach { (fileName, bytes) -> File(attachmentDir, fileName).writeBytes(bytes) }
    }
}
