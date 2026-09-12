package com.menter.treatmentvault.importer

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.menter.treatmentvault.data.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

class UpdateImporter(private val context:Context, private val db:AppDatabase){
    private val gson=Gson(); private val dao=db.dao()
    fun preview(uri:Uri,fileName:String):ImportPreview{
        val text= if(fileName.endsWith(".zip",true)) readManifestFromZip(uri) else context.contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()}
        val pkg=gson.fromJson(text,UpdatePackage::class.java)
        return ImportPreview(pkg.packageId.ifBlank{"local-${System.currentTimeMillis()}"},fileName,pkg.items.groupingBy{it.type}.eachCount(),pkg)
    }
    private fun readManifestFromZip(uri:Uri):String{
        ZipInputStream(context.contentResolver.openInputStream(uri)).use{zis->
            var e=zis.nextEntry
            while(e!=null){ if(e.name=="manifest.json") return zis.bufferedReader().readText(); e=zis.nextEntry }
        }; error("ZIP 中缺少 manifest.json")
    }
    suspend fun import(preview:ImportPreview):ImportResult{
        var added=0; var updated=0; var failed=0; val messages= mutableListOf<String>()
        db.withTransaction {
            preview.updatePackage.items.forEach { item ->
                try { val wasUpdate=upsert(item); if(wasUpdate) updated++ else added++ } catch(e:Exception){ failed++; messages += "${item.type}: ${e.message}" }
            }
            dao.saveImportHistory(ImportHistoryEntity(packageId=preview.packageId,fileName=preview.fileName,added=added,updated=updated,failed=failed,summary=preview.counts.toString()))
        }
        return ImportResult(added,updated,failed,messages)
    }
    private fun Map<String,Any?>.s(k:String)=this[k]?.toString()?.removeSuffix(".0")?:""
    private fun Map<String,Any?>.d(k:String)=this[k]?.toString()?.toDoubleOrNull()
    private fun Map<String,Any?>.i(k:String)=this[k]?.toString()?.toDoubleOrNull()?.toInt()
    private fun Map<String,Any?>.b(k:String)=this[k]?.toString()?.toBooleanStrictOrNull()?:false
    private fun Map<String,Any?>.stages():String = when(val v=this["stages"]){ is List<*> -> v.joinToString(","); else -> s("stages") }
    private suspend fun upsert(item:UpdateItem):Boolean{ val d=item.data
        return when(item.type){
            "supply"->{ val old=dao.supplyByName(d.s("name")); val v=SupplyEntity(id=old?.id?:0,name=d.s("name"),stages=d.stages(),purpose=d.s("purpose"),necessity=d.s("necessity").ifBlank{"按需购买"},purchaseStatus=d.s("purchaseStatus").ifBlank{"待购"},quantity=d.i("quantity"),price=d.d("price"),purchaseUrl=d.s("purchaseUrl"),usage=d.s("usage"),doctorConfirmed=d.b("doctorConfirmed"),notes=d.s("notes")); dao.saveSupply(v); old!=null }
            "exam"->{ val old=dao.findExam(d.s("name"),d.s("date"),d.s("hospital")); val v=ExamEntity(id=old?.id?:0,name=d.s("name"),date=d.s("date"),hospital=d.s("hospital"),type=d.s("type").ifBlank{"其他"},summary=d.s("summary"),keyData=d.s("keyData"),doctorExplanation=d.s("doctorExplanation"),notes=d.s("notes")); if(old==null)dao.saveExam(v) else dao.updateExam(v); old!=null }
            "treatment_plan"->{val old=dao.findPlan(d.s("name"),d.s("date")); val v=TreatmentPlanEntity(id=old?.id?:0,name=d.s("name"),category=d.s("category"),hospital=d.s("hospital"),doctor=d.s("doctor"),date=d.s("date"),medicines=d.s("medicines"),dose=d.s("dose"),cycle=d.s("cycle"),sessions=d.s("sessions"),startDate=d.s("startDate"),expectedEnd=d.s("expectedEnd"),explanation=d.s("explanation"),notes=d.s("notes")); if(old==null)dao.savePlan(v)else dao.updatePlan(v);old!=null}
            "treatment_record"->{val old=dao.findRecord(d.s("date"),d.s("type"),d.s("content")); val v=TreatmentRecordEntity(id=old?.id?:0,date=d.s("date"),type=d.s("type"),hospital=d.s("hospital"),doctor=d.s("doctor"),content=d.s("content"),medicines=d.s("medicines"),sideEffects=d.s("sideEffects"),condition=d.s("condition"),notes=d.s("notes"));if(old==null)dao.saveRecord(v)else dao.updateRecord(v);old!=null}
            "symptom"->{val old=dao.findSymptom(d.s("date"));val v=SymptomEntity(id=old?.id?:0,date=d.s("date"),temperature=d.d("temperature"),weight=d.d("weight"),appetite=d.s("appetite"),nausea=d.s("nausea"),vomiting=d.s("vomiting"),diarrhea=d.s("diarrhea"),constipation=d.s("constipation"),bowelCount=d.i("bowelCount"),urinaryFrequency=d.s("urinaryFrequency"),dysuria=d.s("dysuria"),pain=d.s("pain"),fatigue=d.s("fatigue"),bleeding=d.s("bleeding"),other=d.s("other"),notes=d.s("notes"));if(old==null)dao.saveSymptom(v)else dao.updateSymptom(v);old!=null}
            "question"->{val old=dao.findQuestion(d.s("question"),d.s("date"));val v=QuestionEntity(id=old?.id?:0,question=d.s("question"),date=d.s("date"),hospital=d.s("hospital"),doctor=d.s("doctor"),answer=d.s("answer"),status=d.s("status").ifBlank{"待问"},followUp=d.s("followUp"),notes=d.s("notes"));if(old==null)dao.saveQuestion(v)else dao.updateQuestion(v);old!=null}
            "expense"->{val amount=d.d("amount")?:0.0; val old=dao.findExpense(d.s("date"),amount,d.s("type"),d.s("notes"));val v=ExpenseEntity(id=old?.id?:0,date=d.s("date"),type=d.s("type"),amount=amount,merchant=d.s("merchant"),insuranceAmount=d.d("insuranceAmount")?:0.0,selfPayAmount=d.d("selfPayAmount")?:0.0,reimbursedAmount=d.d("reimbursedAmount")?:0.0,notes=d.s("notes"));if(old==null)dao.saveExpense(v)else dao.updateExpense(v);old!=null}
            "timeline"->{val old=dao.findTimeline(d.s("date"),d.s("type"),d.s("title"));if(old==null)dao.saveTimeline(TimelineEntity(date=d.s("date"),type=d.s("type"),title=d.s("title"),content=d.s("content"),important=d.b("important")));old!=null}
            else -> error("暂不支持 ${item.type}")
        }
    }
}
