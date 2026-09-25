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
    val createdAt: Long = ownerId,
    val folderKeys: Set<String> = setOf(categoryKey),
    val thumbnailFileName: String? = null,
    val searchText: String = preview,
) {
    val key: String get() = "$ownerType:$ownerId"
}

internal fun defaultFolderKey(ownerType: String): String = when (ownerType) {
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
    categories: List<CategoryEntity>, supplies: List<SupplyEntity>, exams: List<ExamEntity>, plans: List<TreatmentPlanEntity>,
    records: List<TreatmentRecordEntity>, symptoms: List<SymptomEntity>, temperatures: List<TemperatureEntity>,
    questions: List<QuestionEntity>, expenses: List<ExpenseEntity>, timeline: List<TimelineEntity>, customEntries: List<CustomEntryEntity>,
    recordFolders: List<RecordFolderEntity> = emptyList(),
): List<UnifiedRecordItem> {
    val categoryMap = categories.associateBy { it.key }
    val folderMap = recordFolders.groupBy { "${it.ownerType}:${it.ownerId}" }
    fun clean(value: String): String = richBodyPlainText(value).replace("\n", " ").trim()
    fun trimPreview(value: String): String = clean(value).let { if (it.length > 120) it.take(120) + "…" else it }
    fun search(vararg values: String): String = values.joinToString(" ") { clean(it) }
    fun firstImage(vararg values: String): String? = values.asSequence().flatMap { richBodyImageNames(it).asSequence() }.firstOrNull()
    fun section(key: String, fallback: String): String = categoryMap[key]?.name ?: fallback
    fun icon(key: String, fallback: String): String = categoryMap[key]?.iconKey ?: fallback
    fun explicitFolders(ownerType: String, ownerId: Long): LinkedHashSet<String> = folderMap["$ownerType:$ownerId"].orEmpty()
        .sortedWith(compareBy<RecordFolderEntity> { it.updatedAt }.thenBy { it.folderKey }).map { it.folderKey }
        .filter { categoryMap.containsKey(it) }.toCollection(linkedSetOf())
    fun typedFolders(ownerType: String, ownerId: Long, fallback: String): LinkedHashSet<String> = explicitFolders(ownerType, ownerId).let { if (it.isEmpty()) linkedSetOf(fallback) else it }
    fun primaryFolder(folders: Set<String>, fallback: String): String = folders.firstOrNull { categoryMap.containsKey(it) } ?: fallback

    return buildList {
        customEntries.forEach { item ->
            val extraFolders = explicitFolders("note", item.id)
            val folders = linkedSetOf(item.categoryId).apply { addAll(extraFolders.filter { it != item.categoryId }) }
            val category = categoryMap[item.categoryId]
            add(UnifiedRecordItem("note", item.id, item.title.ifBlank { clean(item.content).take(48).ifBlank { "未命名笔记" } }, trimPreview(item.content), category?.name ?: "笔记", item.categoryId, "note/${item.id}", category?.iconKey ?: "note", item.date, item.updatedAt, folders, firstImage(item.content), search(item.title, item.content, item.tags)))
        }
        exams.forEach { item -> val f=typedFolders("exam",item.id,"exams"); val p=primaryFolder(f,"exams"); add(UnifiedRecordItem("exam",item.id,item.name.ifBlank{"未命名检查"},trimPreview(item.summary.ifBlank{item.notes}),section(p,"检查报告"),p,"exams/${item.id}",icon("exams","description"),item.date,item.updatedAt,f,firstImage(item.summary,item.notes),search(item.name,item.summary,item.keyData,item.doctorExplanation,item.notes,item.hospital,item.type))) }
        plans.forEach { item -> val f=typedFolders("plan",item.id,"treatment"); val p=primaryFolder(f,"treatment"); add(UnifiedRecordItem("plan",item.id,item.name.ifBlank{"治疗方案"},trimPreview(item.explanation.ifBlank{item.notes}),section(p,"治疗方案与记录"),p,"treatment/plan/${item.id}",icon("treatment","medical"),item.date.ifBlank{item.startDate},item.updatedAt,f,firstImage(item.explanation,item.notes),search(item.name,item.explanation,item.notes,item.medicines,item.dose,item.cycle,item.hospital,item.doctor))) }
        records.forEach { item -> val f=typedFolders("record",item.id,"treatment"); val p=primaryFolder(f,"treatment"); val t=listOf(item.type,item.date).filter{it.isNotBlank()}.joinToString(" · ").ifBlank{"治疗记录"}; add(UnifiedRecordItem("record",item.id,t,trimPreview(item.content.ifBlank{item.notes}),section(p,"治疗方案与记录"),p,"treatment/record/${item.id}",icon("treatment","medical"),item.date,item.updatedAt,f,firstImage(item.content,item.notes),search(item.type,item.content,item.notes,item.medicines,item.sideEffects,item.condition,item.hospital,item.doctor))) }
        supplies.forEach { item -> val f=typedFolders("supply",item.id,"supplies"); val p=primaryFolder(f,"supplies"); add(UnifiedRecordItem("supply",item.id,item.name.ifBlank{"药品/用品"},trimPreview(item.notes.ifBlank{item.purpose}),section(p,"药品用品"),p,"supplies/${item.id}",icon("supplies","medication"),"",item.updatedAt,f,firstImage(item.notes,item.purpose),search(item.name,item.purpose,item.usage,item.notes,item.stages,item.necessity,item.purchaseStatus))) }
        symptoms.forEach { item -> val f=typedFolders("symptom",item.id,"symptoms"); val p=primaryFolder(f,"symptoms"); val s=listOf(item.nausea,item.vomiting,item.diarrhea,item.constipation,item.urinaryFrequency,item.dysuria,item.pain,item.fatigue,item.bleeding,item.other).firstOrNull{it.isNotBlank()}.orEmpty(); add(UnifiedRecordItem("symptom",item.id,item.date.ifBlank{"症状记录"},trimPreview(item.notes.ifBlank{s}),section(p,"症状记录"),p,"symptoms/${item.id}",icon("symptoms","heart"),item.date,item.updatedAt,f,firstImage(item.notes),search(item.notes,item.nausea,item.vomiting,item.diarrhea,item.constipation,item.urinaryFrequency,item.dysuria,item.pain,item.fatigue,item.bleeding,item.other))) }
        temperatures.forEach { item -> val f=typedFolders("temperature",item.id,"temperature"); val p=primaryFolder(f,"temperature"); add(UnifiedRecordItem("temperature",item.id,"${item.temperature}℃",trimPreview(item.notes),section(p,"体温记录"),p,"temperature/${item.id}",icon("temperature","thermostat"),listOf(item.date,item.time).filter{it.isNotBlank()}.joinToString(" "),item.updatedAt,f,firstImage(item.notes),search(item.notes,item.measurementSite,item.date,item.time,item.temperature.toString()))) }
        questions.forEach { item -> val f=typedFolders("question",item.id,"questions"); val p=primaryFolder(f,"questions"); add(UnifiedRecordItem("question",item.id,item.question.ifBlank{"医生问诊"},trimPreview(item.answer.ifBlank{item.notes}),section(p,"医生问诊"),p,"questions/${item.id}",icon("questions","question"),item.date,item.updatedAt,f,firstImage(item.answer,item.notes),search(item.question,item.answer,item.followUp,item.notes,item.hospital,item.doctor,item.status))) }
        expenses.forEach { item -> val f=typedFolders("expense",item.id,"expenses"); val p=primaryFolder(f,"expenses"); add(UnifiedRecordItem("expense",item.id,"${item.type.ifBlank{"费用"}} ¥${item.amount}",trimPreview(item.notes.ifBlank{item.merchant}),section(p,"费用记录"),p,"expenses/${item.id}",icon("expenses","payments"),item.date,item.updatedAt,f,firstImage(item.notes),search(item.type,item.merchant,item.notes,item.date,item.amount.toString()))) }
        timeline.forEach { item -> val f=typedFolders("timeline",item.id,"timeline"); val p=primaryFolder(f,"timeline"); add(UnifiedRecordItem("timeline",item.id,item.title.ifBlank{item.type.ifBlank{"时间线记录"}},trimPreview(item.content),section(p,"总时间轴"),p,"timeline/${item.id}",icon("timeline","timeline"),item.date,item.updatedAt,f,firstImage(item.content),search(item.title,item.type,item.content,item.date))) }
    }
    // 注意：这里不再排序。排序由 AllRecordsScreen 统一负责。
}

fun recordStateFor(item: UnifiedRecordItem, states: Map<String, RecordStateEntity>): RecordStateEntity = states[item.key] ?: RecordStateEntity(ownerType=item.ownerType, ownerId=item.ownerId)
internal fun isTodo(state: RecordStateEntity): Boolean = state.displayType == "todo"

suspend fun setUnifiedRecordFolders(dao: AppDao,item: UnifiedRecordItem,requestedFolderKeys:Set<String>) {
    val now=System.currentTimeMillis(); dao.deleteRecordFolder(item.ownerType,item.ownerId)
    if(item.ownerType=="note") { val selected=requestedFolderKeys.filter{it.isNotBlank()}.toCollection(linkedSetOf()).ifEmpty{linkedSetOf(item.categoryKey)}; val primary=if(item.categoryKey in selected)item.categoryKey else selected.first(); val note=dao.customEntryById(item.ownerId); if(note!=null&&note.categoryId!=primary)dao.updateCustomEntry(note.copy(categoryId=primary,updatedAt=now)); selected.filter{it!=primary}.forEachIndexed{i,k->dao.saveRecordFolder(RecordFolderEntity(item.ownerType,item.ownerId,k,now+i+1))}; return }
    val d=defaultFolderKey(item.ownerType); val selected=requestedFolderKeys.ifEmpty{setOf(d)}; if(selected.size==1&&d in selected)return; selected.forEachIndexed{i,k->dao.saveRecordFolder(RecordFolderEntity(item.ownerType,item.ownerId,k,now+i))}
}

suspend fun moveUnifiedRecordsToFolder(dao:AppDao,allNotes:List<CustomEntryEntity>,allItems:List<UnifiedRecordItem>,recordKeys:Set<String>,destinationFolderKey:String){ if(recordKeys.isEmpty())return; val notes=allNotes.associateBy{it.id}; val now=System.currentTimeMillis(); allItems.filter{it.key in recordKeys}.forEachIndexed{i,item->dao.deleteRecordFolder(item.ownerType,item.ownerId); if(item.ownerType=="note"){val n=notes[item.ownerId]?:return@forEachIndexed;if(n.categoryId!=destinationFolderKey)dao.updateCustomEntry(n.copy(categoryId=destinationFolderKey,updatedAt=now+i))}else{val d=defaultFolderKey(item.ownerType);if(destinationFolderKey!=d)dao.saveRecordFolder(RecordFolderEntity(item.ownerType,item.ownerId,destinationFolderKey,now+i))}} }

suspend fun permanentlyDeleteRecord(dao:AppDao,item:UnifiedRecordItem){ when(item.ownerType){"note"->dao.deleteCustomEntryById(item.ownerId);"exam"->dao.deleteExamById(item.ownerId);"plan"->dao.deletePlanById(item.ownerId);"record"->dao.deleteRecordById(item.ownerId);"supply"->dao.deleteSupplyById(item.ownerId);"symptom"->dao.deleteSymptomById(item.ownerId);"temperature"->dao.deleteTemperatureById(item.ownerId);"question"->dao.deleteQuestionById(item.ownerId);"expense"->dao.deleteExpenseById(item.ownerId);"timeline"->dao.deleteTimelineById(item.ownerId)};dao.clearAttachmentsForOwner(item.ownerType,item.ownerId);dao.deleteRecordState(item.ownerType,item.ownerId);dao.deleteRecordFolder(item.ownerType,item.ownerId) }
