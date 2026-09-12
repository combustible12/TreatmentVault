package com.menter.treatmentvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities=[SupplyEntity::class,ExamEntity::class,TreatmentPlanEntity::class,TreatmentRecordEntity::class,SymptomEntity::class,QuestionEntity::class,ExpenseEntity::class,TimelineEntity::class,ImportHistoryEntity::class],version=1,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){
    abstract fun dao():AppDao
    companion object { @Volatile private var INSTANCE:AppDatabase?=null
        fun get(context:Context)=INSTANCE?: synchronized(this){ INSTANCE?:Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"treatment_vault.db").build().also{INSTANCE=it} }
    }
}
