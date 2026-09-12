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
): List<UnifiedRecordItem> {
    val categoryMap = categories.associateBy { it.key }
    fun clean(value: String): String = richBodyPlainText(value).replace("\n", " ").trim()
    fun trimPreview(value: String): String = clean(value).let { if (it.length > 120) it.take(120) + "…" else it }
    fun section(key: String, fallback: String): String = categoryMap[key]?.name ?: fallback
    fun icon(key: String, fallback: String): String = categoryMap[key]?.iconKey ?: fallback

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
            add(UnifiedRecordItem("exam", item.id, item.name.ifBlank { "未命名检查" }, trimPreview(item.summary.ifBlank { item.notes }), section("exams", "检查报告"), "exams", "exams/${item.id}", icon("exams", "description"), item.date, item.updatedAt))
        }
        plans.forEach { item ->
            add(UnifiedRecordItem("plan", item.id, item.name.ifBlank { "治疗方案" }, trimPreview(item.explanation.ifBlank { item.notes }), section("treatment", "治疗方案与记录"), "treatment", "treatment/plan/${item.id}", icon("treatment", "medical"), item.date.ifBlank { item.startDate }, item.updatedAt))
        }
        records.forEach { item ->
            val title = listOf(item.type, item.date).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "治疗记录" }
            add(UnifiedRecordItem("record", item.id, title, trimPreview(item.content.ifBlank { item.notes }), section("treatment", "治疗方案与记录"), "treatment", "treatment/record/${item.id}", icon("treatment", "medical"), item.date, item.updatedAt))
        }
        supplies.forEach { item ->
            add(UnifiedRecordItem("supply", item.id, item.name.ifBlank { "药品/用品" }, trimPreview(item.notes.ifBlank { item.purpose }), section("supplies", "药品用品"), "supplies", "supplies/${item.id}", icon("supplies", "medication"), "", item.updatedAt))
        }
        symptoms.forEach { item ->
            val structured = listOf(item.nausea, item.vomiting, item.diarrhea, item.constipation, item.urinaryFrequency, item.dysuria, item.pain, item.fatigue, item.bleeding, item.other).firstOrNull { it.isNotBlank() }.orEmpty()
            add(UnifiedRecordItem("symptom", item.id, item.date.ifBlank { "症状记录" }, trimPreview(item.notes.ifBlank { structured }), section("symptoms", "症状记录"), "symptoms", "symptoms/${item.id}", icon("symptoms", "heart"), item.date, item.updatedAt))
        }
        temperatures.forEach { item ->
            add(UnifiedRecordItem("temperature", item.id, "${item.temperature}℃", trimPreview(item.notes), section("temperature", "体温记录"), "temperature", "temperature/${item.id}", icon("temperature", "thermostat"), listOf(item.date, item.time).filter { it.isNotBlank() }.joinToString(" "), item.updatedAt))
        }
        questions.forEach { item ->
            add(UnifiedRecordItem("question", item.id, item.question.ifBlank { "医生问诊" }, trimPreview(item.answer.ifBlank { item.notes }), section("questions", "医生问诊"), "questions", "questions/${item.id}", icon("questions", "question"), item.date, item.updatedAt))
        }
        expenses.forEach { item ->
            add(UnifiedRecordItem("expense", item.id, "${item.type.ifBlank { "费用" }} ¥${item.amount}", trimPreview(item.notes.ifBlank { item.merchant }), section("expenses", "费用记录"), "expenses", "expenses/${item.id}", icon("expenses", "payments"), item.date, item.updatedAt))
        }
        timeline.forEach { item ->
            add(UnifiedRecordItem("timeline", item.id, item.title.ifBlank { item.type.ifBlank { "时间线记录" } }, trimPreview(item.content), section("timeline", "总时间轴"), "timeline", "timeline/${item.id}", icon("timeline", "timeline"), item.date, item.updatedAt))
        }
    }.sortedByDescending { it.updatedAt }
}

fun recordStateFor(item: UnifiedRecordItem, states: Map<String, RecordStateEntity>): RecordStateEntity =
    states[item.key] ?: RecordStateEntity(ownerType = item.ownerType, ownerId = item.ownerId)

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
}
