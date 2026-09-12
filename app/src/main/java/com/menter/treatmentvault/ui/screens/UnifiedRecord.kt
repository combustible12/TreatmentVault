package com.menter.treatmentvault.ui.screens

import com.menter.treatmentvault.data.*

data class UnifiedRecordItem(
    val ownerType: String,
    val ownerId: Long,
    val title: String,
    val preview: String,
    val section: String,
    val categoryKey: String,
    val route: String,
    val iconKey: String,
    val date: String,
    val updatedAt: Long,
) {
    val key: String get() = "$ownerType:$ownerId"
}

private fun defaultFolderKey(ownerType: String): String = when (ownerType) {
    "exam" -> "exams"
    "plan", "record" -> "treatment"
    "supply" -> "supplies"
    "symptom" -> "symptoms"
    "temperature" -> "temperature"
    "question" -> "questions"
    "expense" -> "expenses"
    "timeline" -> "timeline"
    else -> "inbox"
}

fun buildUnifiedRecords(
    categories: List<CategoryEntity>,
    supplies: List<SupplyEntity>,
    exams: List<ExamEntity>,
    plans: List<TreatmentPlanEntity>,
    records: List<TreatmentRecordEntity>,
    symptoms: List<SymptomEntity>,
    temperatures: List<TemperatureEntity>,
    questions: List<QuestionEntity>,
    expenses: List<ExpenseEntity>,
    timeline: List<TimelineEntity>,
    customEntries: List<CustomEntryEntity>,
    recordFolders: List<RecordFolderEntity> = emptyList(),
): List<UnifiedRecordItem> {
    val categoryMap = categories.associateBy { it.key }
    val folderMap = recordFolders.associateBy { "${it.ownerType}:${it.ownerId}" }
    fun clean(value: String): String = richBodyPlainText(value).replace("\n", " ").trim()
    fun trimPreview(value: String): String = clean(value).let { if (it.length > 120) it.take(120) + "…" else it }
    fun section(key: String, fallback: String): String = categoryMap[key]?.name ?: fallback
    fun icon(key: String, fallback: String): String = categoryMap[key]?.iconKey ?: fallback
    fun effectiveFolder(ownerType: String, ownerId: Long, fallback: String): String {
        val mapped = folderMap["$ownerType:$ownerId"]?.folderKey
        return if (mapped != null && categoryMap.containsKey(mapped)) mapped else fallback
    }

    return buildList {
        customEntries.forEach { item ->
            val category = categoryMap[item.categoryId]
            add(
                UnifiedRecordItem(
                    ownerType = "note",
                    ownerId = item.id,
                    title = item.title.ifBlank { clean(item.content).take(48).ifBlank { "未命名笔记" } },
                    preview = trimPreview(item.content),
                    section = category?.name ?: "笔记",
                    categoryKey = item.categoryId,
                    route = "note/${item.id}",
                    iconKey = category?.iconKey ?: "note",
                    date = item.date,
                    updatedAt = item.updatedAt,
                )
            )
        }
        exams.forEach { item ->
            val folder = effectiveFolder("exam", item.id, "exams")
            add(UnifiedRecordItem("exam", item.id, item.name.ifBlank { "未命名检查" }, trimPreview(item.summary.ifBlank { item.notes }), section(folder, "检查报告"), folder, "exams/${item.id}", icon("exams", "description"), item.date, item.updatedAt))
        }
        plans.forEach { item ->
            val folder = effectiveFolder("plan", item.id, "treatment")
            add(UnifiedRecordItem("plan", item.id, item.name.ifBlank { "治疗方案" }, trimPreview(item.explanation.ifBlank { item.notes }), section(folder, "治疗方案与记录"), folder, "treatment/plan/${item.id}", icon("treatment", "medical"), item.date.ifBlank { item.startDate }, item.updatedAt))
        }
        records.forEach { item ->
            val folder = effectiveFolder("record", item.id, "treatment")
            val title = listOf(item.type, item.date).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "治疗记录" }
            add(UnifiedRecordItem("record", item.id, title, trimPreview(item.content.ifBlank { item.notes }), section(folder, "治疗方案与记录"), folder, "treatment/record/${item.id}", icon("treatment", "medical"), item.date, item.updatedAt))
        }
        supplies.forEach { item ->
            val folder = effectiveFolder("supply", item.id, "supplies")
            add(UnifiedRecordItem("supply", item.id, item.name.ifBlank { "药品/用品" }, trimPreview(item.notes.ifBlank { item.purpose }), section(folder, "药品用品"), folder, "supplies/${item.id}", icon("supplies", "medication"), "", item.updatedAt))
        }
        symptoms.forEach { item ->
            val folder = effectiveFolder("symptom", item.id, "symptoms")
            val structured = listOf(item.nausea, item.vomiting, item.diarrhea, item.constipation, item.urinaryFrequency, item.dysuria, item.pain, item.fatigue, item.bleeding, item.other).firstOrNull { it.isNotBlank() }.orEmpty()
            add(UnifiedRecordItem("symptom", item.id, item.date.ifBlank { "症状记录" }, trimPreview(item.notes.ifBlank { structured }), section(folder, "症状记录"), folder, "symptoms/${item.id}", icon("symptoms", "heart"), item.date, item.updatedAt))
        }
        temperatures.forEach { item ->
            val folder = effectiveFolder("temperature", item.id, "temperature")
            add(UnifiedRecordItem("temperature", item.id, "${item.temperature}℃", trimPreview(item.notes), section(folder, "体温记录"), folder, "temperature/${item.id}", icon("temperature", "thermostat"), listOf(item.date, item.time).filter { it.isNotBlank() }.joinToString(" "), item.updatedAt))
        }
        questions.forEach { item ->
            val folder = effectiveFolder("question", item.id, "questions")
            add(UnifiedRecordItem("question", item.id, item.question.ifBlank { "医生问诊" }, trimPreview(item.answer.ifBlank { item.notes }), section(folder, "医生问诊"), folder, "questions/${item.id}", icon("questions", "question"), item.date, item.updatedAt))
        }
        expenses.forEach { item ->
            val folder = effectiveFolder("expense", item.id, "expenses")
            add(UnifiedRecordItem("expense", item.id, "${item.type.ifBlank { "费用" }} ¥${item.amount}", trimPreview(item.notes.ifBlank { item.merchant }), section(folder, "费用记录"), folder, "expenses/${item.id}", icon("expenses", "payments"), item.date, item.updatedAt))
        }
        timeline.forEach { item ->
            val folder = effectiveFolder("timeline", item.id, "timeline")
            add(UnifiedRecordItem("timeline", item.id, item.title.ifBlank { item.type.ifBlank { "时间线记录" } }, trimPreview(item.content), section(folder, "总时间轴"), folder, "timeline/${item.id}", icon("timeline", "timeline"), item.date, item.updatedAt))
        }
    }.sortedByDescending { it.updatedAt }
}

fun recordStateFor(item: UnifiedRecordItem, states: Map<String, RecordStateEntity>): RecordStateEntity =
    states[item.key] ?: RecordStateEntity(ownerType = item.ownerType, ownerId = item.ownerId)

suspend fun moveUnifiedRecordsToFolder(
    dao: AppDao,
    allNotes: List<CustomEntryEntity>,
    allItems: List<UnifiedRecordItem>,
    recordKeys: Set<String>,
    destinationFolderKey: String,
) {
    if (recordKeys.isEmpty()) return
    val notesById = allNotes.associateBy { it.id }
    val now = System.currentTimeMillis()
    allItems.filter { it.key in recordKeys }.forEachIndexed { index, item ->
        if (item.ownerType == "note") {
            val note = notesById[item.ownerId] ?: return@forEachIndexed
            if (note.categoryId != destinationFolderKey) {
                dao.updateCustomEntry(note.copy(categoryId = destinationFolderKey, updatedAt = now + index))
            }
        } else {
            val defaultFolder = defaultFolderKey(item.ownerType)
            if (destinationFolderKey == defaultFolder) {
                dao.deleteRecordFolder(item.ownerType, item.ownerId)
            } else {
                dao.saveRecordFolder(
                    RecordFolderEntity(
                        ownerType = item.ownerType,
                        ownerId = item.ownerId,
                        folderKey = destinationFolderKey,
                        updatedAt = now + index,
                    )
                )
            }
        }
    }
}

suspend fun permanentlyDeleteRecord(dao: AppDao, item: UnifiedRecordItem) {
    when (item.ownerType) {
        "note" -> dao.deleteCustomEntryById(item.ownerId)
        "exam" -> dao.deleteExamById(item.ownerId)
        "plan" -> dao.deletePlanById(item.ownerId)
        "record" -> dao.deleteRecordById(item.ownerId)
        "supply" -> dao.deleteSupplyById(item.ownerId)
        "symptom" -> dao.deleteSymptomById(item.ownerId)
        "temperature" -> dao.deleteTemperatureById(item.ownerId)
        "question" -> dao.deleteQuestionById(item.ownerId)
        "expense" -> dao.deleteExpenseById(item.ownerId)
        "timeline" -> dao.deleteTimelineById(item.ownerId)
    }
    dao.clearAttachmentsForOwner(item.ownerType, item.ownerId)
    dao.deleteRecordState(item.ownerType, item.ownerId)
    dao.deleteRecordFolder(item.ownerType, item.ownerId)
}
