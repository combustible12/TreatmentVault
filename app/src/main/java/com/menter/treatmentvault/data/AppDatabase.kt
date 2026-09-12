package com.menter.treatmentvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities=[SupplyEntity::class,ExamEntity::class,TreatmentPlanEntity::class,TreatmentRecordEntity::class,SymptomEntity::class,TemperatureEntity::class,QuestionEntity::class,ExpenseEntity::class,TimelineEntity::class,ImportHistoryEntity::class],version=2,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){
    abstract fun dao():AppDao
    companion object { @Volatile private var INSTANCE:AppDatabase?=null
        private val MIGRATION_1_2=object:Migration(1,2){ override fun migrate(db:SupportSQLiteDatabase){ db.execSQL("CREATE TABLE IF NOT EXISTS `TemperatureEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `temperature` REAL NOT NULL, `measurementSite` TEXT NOT NULL, `notes` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)") } }
        fun get(context:Context)=INSTANCE?: synchronized(this){ INSTANCE?:Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"treatment_vault.db").addMigrations(MIGRATION_1_2).build().also{INSTANCE=it} }
    }
}
