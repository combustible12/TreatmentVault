package com.menter.treatmentvault.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM SupplyEntity ORDER BY CASE purchaseStatus WHEN '待购' THEN 0 WHEN '已购' THEN 1 ELSE 2 END, name") fun supplies():Flow<List<SupplyEntity>>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveSupply(v:SupplyEntity):Long
    @Update suspend fun updateSupply(v:SupplyEntity)
    @Query("SELECT * FROM SupplyEntity WHERE name=:name LIMIT 1") suspend fun supplyByName(name:String):SupplyEntity?
    @Delete suspend fun deleteSupply(v:SupplyEntity)
    @Query("DELETE FROM SupplyEntity WHERE id=:id") suspend fun deleteSupplyById(id:Long)

    @Query("SELECT * FROM ExamEntity ORDER BY date DESC,id DESC") fun exams():Flow<List<ExamEntity>>
    @Insert suspend fun saveExam(v:ExamEntity):Long
    @Update suspend fun updateExam(v:ExamEntity)
    @Delete suspend fun deleteExam(v:ExamEntity)
    @Query("DELETE FROM ExamEntity WHERE id=:id") suspend fun deleteExamById(id:Long)
    @Query("SELECT * FROM ExamEntity WHERE name=:name AND date=:date AND hospital=:hospital LIMIT 1") suspend fun findExam(name:String,date:String,hospital:String):ExamEntity?

    @Query("SELECT * FROM TreatmentPlanEntity ORDER BY date DESC,id DESC") fun plans():Flow<List<TreatmentPlanEntity>>
    @Insert suspend fun savePlan(v:TreatmentPlanEntity):Long
    @Update suspend fun updatePlan(v:TreatmentPlanEntity)
    @Delete suspend fun deletePlan(v:TreatmentPlanEntity)
    @Query("DELETE FROM TreatmentPlanEntity WHERE id=:id") suspend fun deletePlanById(id:Long)
    @Query("SELECT * FROM TreatmentPlanEntity WHERE name=:name AND date=:date LIMIT 1") suspend fun findPlan(name:String,date:String):TreatmentPlanEntity?

    @Query("SELECT * FROM TreatmentRecordEntity ORDER BY date DESC,id DESC") fun records():Flow<List<TreatmentRecordEntity>>
    @Insert suspend fun saveRecord(v:TreatmentRecordEntity):Long
    @Update suspend fun updateRecord(v:TreatmentRecordEntity)
    @Delete suspend fun deleteRecord(v:TreatmentRecordEntity)
    @Query("DELETE FROM TreatmentRecordEntity WHERE id=:id") suspend fun deleteRecordById(id:Long)
    @Query("SELECT * FROM TreatmentRecordEntity WHERE date=:date AND type=:type AND content=:content LIMIT 1") suspend fun findRecord(date:String,type:String,content:String):TreatmentRecordEntity?

    @Query("SELECT * FROM SymptomEntity ORDER BY date DESC,id DESC") fun symptoms():Flow<List<SymptomEntity>>
    @Insert suspend fun saveSymptom(v:SymptomEntity):Long
    @Update suspend fun updateSymptom(v:SymptomEntity)
    @Delete suspend fun deleteSymptom(v:SymptomEntity)
    @Query("DELETE FROM SymptomEntity WHERE id=:id") suspend fun deleteSymptomById(id:Long)
    @Query("SELECT * FROM SymptomEntity WHERE date=:date LIMIT 1") suspend fun findSymptom(date:String):SymptomEntity?

    @Query("SELECT * FROM TemperatureEntity ORDER BY date DESC,time DESC,id DESC") fun temperatures():Flow<List<TemperatureEntity>>
    @Insert suspend fun saveTemperature(v:TemperatureEntity):Long
    @Update suspend fun updateTemperature(v:TemperatureEntity)
    @Delete suspend fun deleteTemperature(v:TemperatureEntity)
    @Query("DELETE FROM TemperatureEntity WHERE id=:id") suspend fun deleteTemperatureById(id:Long)

    @Query("SELECT * FROM QuestionEntity ORDER BY CASE status WHEN '待问' THEN 0 ELSE 1 END,date DESC,id DESC") fun questions():Flow<List<QuestionEntity>>
    @Insert suspend fun saveQuestion(v:QuestionEntity):Long
    @Update suspend fun updateQuestion(v:QuestionEntity)
    @Delete suspend fun deleteQuestion(v:QuestionEntity)
    @Query("DELETE FROM QuestionEntity WHERE id=:id") suspend fun deleteQuestionById(id:Long)
    @Query("SELECT * FROM QuestionEntity WHERE question=:question AND date=:date LIMIT 1") suspend fun findQuestion(question:String,date:String):QuestionEntity?

    @Query("SELECT * FROM ExpenseEntity ORDER BY date DESC,id DESC") fun expenses():Flow<List<ExpenseEntity>>
    @Insert suspend fun saveExpense(v:ExpenseEntity):Long
    @Update suspend fun updateExpense(v:ExpenseEntity)
    @Delete suspend fun deleteExpense(v:ExpenseEntity)
    @Query("DELETE FROM ExpenseEntity WHERE id=:id") suspend fun deleteExpenseById(id:Long)
    @Query("SELECT * FROM ExpenseEntity WHERE date=:date AND amount=:amount AND type=:type AND notes=:notes LIMIT 1") suspend fun findExpense(date:String,amount:Double,type:String,notes:String):ExpenseEntity?

    @Query("SELECT * FROM TimelineEntity ORDER BY date DESC,id DESC") fun timeline():Flow<List<TimelineEntity>>
    @Insert suspend fun saveTimeline(v:TimelineEntity):Long
    @Update suspend fun updateTimeline(v:TimelineEntity)
    @Delete suspend fun deleteTimeline(v:TimelineEntity)
    @Query("DELETE FROM TimelineEntity WHERE id=:id") suspend fun deleteTimelineById(id:Long)
    @Query("SELECT * FROM TimelineEntity WHERE date=:date AND type=:type AND title=:title LIMIT 1") suspend fun findTimeline(date:String,type:String,title:String):TimelineEntity?

    @Query("SELECT * FROM ImportHistoryEntity ORDER BY importedAt DESC") fun importHistory():Flow<List<ImportHistoryEntity>>
    @Insert suspend fun saveImportHistory(v:ImportHistoryEntity):Long

    @Query("SELECT * FROM CategoryEntity ORDER BY sortOrder ASC, name ASC") fun categories():Flow<List<CategoryEntity>>
    @Query("SELECT * FROM CategoryEntity ORDER BY sortOrder ASC, name ASC") suspend fun allCategories():List<CategoryEntity>
    @Query("SELECT * FROM CategoryEntity WHERE `key`=:key LIMIT 1") suspend fun categoryByKey(key:String):CategoryEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveCategory(v:CategoryEntity)
    @Update suspend fun updateCategory(v:CategoryEntity)
    @Delete suspend fun deleteCategory(v:CategoryEntity)
    @Query("DELETE FROM CategoryEntity") suspend fun clearCategories()

    @Query("SELECT * FROM CustomEntryEntity WHERE categoryId=:categoryId ORDER BY date DESC,id DESC") fun customEntries(categoryId:String):Flow<List<CustomEntryEntity>>
    @Query("SELECT * FROM CustomEntryEntity ORDER BY date DESC,id DESC") fun allCustomEntriesFlow():Flow<List<CustomEntryEntity>>
    @Query("SELECT * FROM CustomEntryEntity WHERE id=:id LIMIT 1") fun customEntry(id:Long):Flow<CustomEntryEntity?>
    @Query("SELECT * FROM CustomEntryEntity WHERE id=:id LIMIT 1") suspend fun customEntryById(id:Long):CustomEntryEntity?
    @Query("SELECT * FROM CustomEntryEntity") suspend fun allCustomEntries():List<CustomEntryEntity>
    @Insert suspend fun saveCustomEntry(v:CustomEntryEntity):Long
    @Update suspend fun updateCustomEntry(v:CustomEntryEntity)
    @Delete suspend fun deleteCustomEntry(v:CustomEntryEntity)
    @Query("DELETE FROM CustomEntryEntity WHERE id=:id") suspend fun deleteCustomEntryById(id:Long)
    @Query("DELETE FROM CustomEntryEntity WHERE categoryId=:categoryId") suspend fun clearCustomEntriesForCategory(categoryId:String)
    @Query("DELETE FROM CustomEntryEntity") suspend fun clearCustomEntries()

    @Query("SELECT * FROM TagEntity ORDER BY name") fun tags():Flow<List<TagEntity>>
    @Query("SELECT * FROM TagEntity") suspend fun allTags():List<TagEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveTag(v:TagEntity)
    @Query("DELETE FROM TagEntity") suspend fun clearTags()

    @Query("SELECT * FROM AttachmentEntity WHERE ownerType=:ownerType AND ownerId=:ownerId ORDER BY createdAt ASC,id ASC") fun attachments(ownerType:String, ownerId:Long):Flow<List<AttachmentEntity>>
    @Query("SELECT * FROM AttachmentEntity WHERE ownerType=:ownerType AND ownerId=:ownerId ORDER BY createdAt ASC,id ASC") suspend fun attachmentsForOwner(ownerType:String, ownerId:Long):List<AttachmentEntity>
    @Query("SELECT * FROM AttachmentEntity ORDER BY id") suspend fun allAttachments():List<AttachmentEntity>
    @Insert suspend fun saveAttachment(v:AttachmentEntity):Long
    @Delete suspend fun deleteAttachment(v:AttachmentEntity)
    @Query("DELETE FROM AttachmentEntity WHERE ownerType=:ownerType AND ownerId=:ownerId") suspend fun clearAttachmentsForOwner(ownerType:String, ownerId:Long)
    @Query("DELETE FROM AttachmentEntity") suspend fun clearAttachments()

    @Query("SELECT * FROM RecordStateEntity") fun recordStates():Flow<List<RecordStateEntity>>
    @Query("SELECT * FROM RecordStateEntity") suspend fun allRecordStates():List<RecordStateEntity>
    @Query("SELECT * FROM RecordStateEntity WHERE ownerType=:ownerType AND ownerId=:ownerId LIMIT 1") suspend fun recordState(ownerType:String, ownerId:Long):RecordStateEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveRecordState(v:RecordStateEntity)
    @Query("DELETE FROM RecordStateEntity WHERE ownerType=:ownerType AND ownerId=:ownerId") suspend fun deleteRecordState(ownerType:String, ownerId:Long)
    @Query("DELETE FROM RecordStateEntity") suspend fun clearRecordStates()

    @Query("SELECT * FROM RecordFolderEntity") fun recordFolders():Flow<List<RecordFolderEntity>>
    @Query("SELECT * FROM RecordFolderEntity") suspend fun allRecordFolders():List<RecordFolderEntity>
    @Query("SELECT * FROM RecordFolderEntity WHERE ownerType=:ownerType AND ownerId=:ownerId ORDER BY updatedAt ASC, folderKey ASC") suspend fun recordFoldersForOwner(ownerType:String, ownerId:Long):List<RecordFolderEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveRecordFolder(v:RecordFolderEntity)
    @Query("DELETE FROM RecordFolderEntity WHERE ownerType=:ownerType AND ownerId=:ownerId AND folderKey=:folderKey") suspend fun deleteRecordFolderMembership(ownerType:String, ownerId:Long, folderKey:String)
    @Query("DELETE FROM RecordFolderEntity WHERE ownerType=:ownerType AND ownerId=:ownerId") suspend fun deleteRecordFolder(ownerType:String, ownerId:Long)
    @Query("DELETE FROM RecordFolderEntity WHERE folderKey=:folderKey") suspend fun clearRecordFoldersForFolder(folderKey:String)
    @Query("DELETE FROM RecordFolderEntity") suspend fun clearRecordFolders()

    @Query("SELECT * FROM SupplyEntity") suspend fun allSupplies():List<SupplyEntity>
    @Query("SELECT * FROM ExamEntity") suspend fun allExams():List<ExamEntity>
    @Query("SELECT * FROM TreatmentPlanEntity") suspend fun allPlans():List<TreatmentPlanEntity>
    @Query("SELECT * FROM TreatmentRecordEntity") suspend fun allRecords():List<TreatmentRecordEntity>
    @Query("SELECT * FROM SymptomEntity") suspend fun allSymptoms():List<SymptomEntity>
    @Query("SELECT * FROM TemperatureEntity") suspend fun allTemperatures():List<TemperatureEntity>
    @Query("SELECT * FROM QuestionEntity") suspend fun allQuestions():List<QuestionEntity>
    @Query("SELECT * FROM ExpenseEntity") suspend fun allExpenses():List<ExpenseEntity>
    @Query("SELECT * FROM TimelineEntity") suspend fun allTimeline():List<TimelineEntity>

    @Query("DELETE FROM SupplyEntity") suspend fun clearSupplies(); @Query("DELETE FROM ExamEntity") suspend fun clearExams(); @Query("DELETE FROM TreatmentPlanEntity") suspend fun clearPlans(); @Query("DELETE FROM TreatmentRecordEntity") suspend fun clearRecords(); @Query("DELETE FROM SymptomEntity") suspend fun clearSymptoms(); @Query("DELETE FROM TemperatureEntity") suspend fun clearTemperatures(); @Query("DELETE FROM QuestionEntity") suspend fun clearQuestions(); @Query("DELETE FROM ExpenseEntity") suspend fun clearExpenses(); @Query("DELETE FROM TimelineEntity") suspend fun clearTimeline()
}
