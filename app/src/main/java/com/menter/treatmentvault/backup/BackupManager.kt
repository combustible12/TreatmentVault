package com.menter.treatmentvault.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.menter.treatmentvault.data.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class Snapshot(val supplies:List<SupplyEntity>,val exams:List<ExamEntity>,val plans:List<TreatmentPlanEntity>,val records:List<TreatmentRecordEntity>,val symptoms:List<SymptomEntity>,val temperatures:List<TemperatureEntity>?=null,val questions:List<QuestionEntity>,val expenses:List<ExpenseEntity>,val timeline:List<TimelineEntity>)
class BackupManager(private val context:Context,private val db:AppDatabase){ private val gson=Gson(); private val dao=db.dao()
    suspend fun export(uri:Uri){ val snap=Snapshot(dao.allSupplies(),dao.allExams(),dao.allPlans(),dao.allRecords(),dao.allSymptoms(),dao.allTemperatures(),dao.allQuestions(),dao.allExpenses(),dao.allTimeline()); ZipOutputStream(context.contentResolver.openOutputStream(uri)).use{z->z.putNextEntry(ZipEntry("backup.json"));z.write(gson.toJson(snap).toByteArray());z.closeEntry()} }
    suspend fun restore(uri:Uri){ var json:String?=null; ZipInputStream(context.contentResolver.openInputStream(uri)).use{z->var e=z.nextEntry;while(e!=null){if(e.name=="backup.json"){json=z.bufferedReader().readText();break};e=z.nextEntry}}; val snap=gson.fromJson(json?:error("无 backup.json"),Snapshot::class.java); db.withTransaction{dao.clearSupplies();dao.clearExams();dao.clearPlans();dao.clearRecords();dao.clearSymptoms();dao.clearTemperatures();dao.clearQuestions();dao.clearExpenses();dao.clearTimeline();snap.supplies.forEach{dao.saveSupply(it)};snap.exams.forEach{dao.saveExam(it)};snap.plans.forEach{dao.savePlan(it)};snap.records.forEach{dao.saveRecord(it)};snap.symptoms.forEach{dao.saveSymptom(it)};snap.temperatures.orEmpty().forEach{dao.saveTemperature(it)};snap.questions.forEach{dao.saveQuestion(it)};snap.expenses.forEach{dao.saveExpense(it)};snap.timeline.forEach{dao.saveTimeline(it)}} }
}
