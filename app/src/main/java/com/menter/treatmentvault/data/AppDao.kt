package com.menter.treatmentvault.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM SupplyEntity ORDER BY CASE purchaseStatus WHEN '待购' THEN 0 WHEN '已购' THEN 1 ELSE 2 END, name") fun supplies():Flow<List<SupplyEntity>>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveSupply(v:SupplyEntity):Long
    @Query("SELECT * FROM SupplyEntity WHERE name=:name LIMIT 1") suspend fun supplyByName(name:String):SupplyEntity?
    @Delete suspend fun deleteSupply(v:SupplyEntity)

    @Query("SELECT * FROM ExamEntity ORDER BY date DESC,id DESC") fun exams():Flow<List<ExamEntity>>
    @Insert suspend fun saveExam(v:ExamEntity):Long
    @Update suspend fun updateExam(v:ExamEntity)
    @Query("SELECT * FROM ExamEntity WHERE name=:name AND date=:date AND hospital=:hospital LIMIT 1") suspend fun findExam(name:String,date:String,hospital:String):ExamEntity?

    @Query("SELECT * FROM TreatmentPlanEntity ORDER BY date DESC,id DESC") fun plans():Flow<List<TreatmentPlanEntity>>
    @Insert suspend fun savePlan(v:TreatmentPlanEntity):Long
    @Update suspend fun updatePlan(v:TreatmentPlanEntity)
    @Query("SELECT * FROM TreatmentPlanEntity WHERE name=:name AND date=:date LIMIT 1") suspend fun findPlan(name:String,date:String):TreatmentPlanEntity?

    @Query("SELECT * FROM TreatmentRecordEntity ORDER BY date DESC,id DESC") fun records():Flow<List<TreatmentRecordEntity>>
    @Insert suspend fun saveRecord(v:TreatmentRecordEntity):Long
    @Update suspend fun updateRecord(v:TreatmentRecordEntity)
    @Query("SELECT * FROM TreatmentRecordEntity WHERE date=:date AND type=:type AND content=:content LIMIT 1") suspend fun findRecord(date:String,type:String,content:String):TreatmentRecordEntity?

    @Query("SELECT * FROM SymptomEntity ORDER BY date DESC,id DESC") fun symptoms():Flow<List<SymptomEntity>>
    @Insert suspend fun saveSymptom(v:SymptomEntity):Long
    @Update suspend fun updateSymptom(v:SymptomEntity)
    @Query("SELECT * FROM SymptomEntity WHERE date=:date LIMIT 1") suspend fun findSymptom(date:String):SymptomEntity?

    @Query("SELECT * FROM TemperatureEntity ORDER BY date DESC,time DESC,id DESC") fun temperatures():Flow<List<TemperatureEntity>>
    @Insert suspend fun saveTemperature(v:TemperatureEntity):Long
    @Delete suspend fun deleteTemperature(v:TemperatureEntity)

    @Query("SELECT * FROM QuestionEntity ORDER BY CASE status WHEN '待问' THEN 0 ELSE 1 END,date DESC,id DESC") fun questions():Flow<List<QuestionEntity>>
    @Insert suspend fun saveQuestion(v:QuestionEntity):Long
    @Update suspend fun updateQuestion(v:QuestionEntity)
    @Query("SELECT * FROM QuestionEntity WHERE question=:question AND date=:date LIMIT 1") suspend fun findQuestion(question:String,date:String):QuestionEntity?

    @Query("SELECT * FROM ExpenseEntity ORDER BY date DESC,id DESC") fun expenses():Flow<List<ExpenseEntity>>
    @Insert suspend fun saveExpense(v:ExpenseEntity):Long
    @Update suspend fun updateExpense(v:ExpenseEntity)
    @Query("SELECT * FROM ExpenseEntity WHERE date=:date AND amount=:amount AND type=:type AND notes=:notes LIMIT 1") suspend fun findExpense(date:String,amount:Double,type:String,notes:String):ExpenseEntity?

    @Query("SELECT * FROM TimelineEntity ORDER BY date DESC,id DESC") fun timeline():Flow<List<TimelineEntity>>
    @Insert suspend fun saveTimeline(v:TimelineEntity):Long
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
    @Query("SELECT * FROM CustomEntryEntity") suspend fun allCustomEntries():List<CustomEntryEntity>
    @Insert suspend fun saveCustomEntry(v:CustomEntryEntity):Long
    @Delete suspend fun deleteCustomEntry(v:CustomEntryEntity)
    @Query("DELETE FROM CustomEntryEntity WHERE categoryId=:categoryId") suspend fun clearCustomEntriesForCategory(categoryId:String)
    @Query("DELETE FROM CustomEntryEntity") suspend fun clearCustomEntries()

    @Query("SELECT * FROM TagEntity ORDER BY name") fun tags():Flow<List<TagEntity>>
    @Query("SELECT * FROM TagEntity") suspend fun allTags():List<TagEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveTag(v:TagEntity)
    @Query("DELETE FROM TagEntity") suspend fun clearTags()

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
