package com.menter.treatmentvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SupplyEntity::class, ExamEntity::class, TreatmentPlanEntity::class, TreatmentRecordEntity::class,
        SymptomEntity::class, TemperatureEntity::class, QuestionEntity::class, ExpenseEntity::class,
        TimelineEntity::class, ImportHistoryEntity::class, CategoryEntity::class, TagEntity::class,
        CustomEntryEntity::class, AttachmentEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `TemperatureEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `temperature` REAL NOT NULL, `measurementSite` TEXT NOT NULL, `notes` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createCategoryTables(db)
                seedCategories(db)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `AttachmentEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ownerType` TEXT NOT NULL, `ownerId` INTEGER NOT NULL, `fileName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_AttachmentEntity_ownerType_ownerId` ON `AttachmentEntity` (`ownerType`, `ownerId`)")
            }
        }

        private fun createCategoryTables(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `CategoryEntity` (`key` TEXT NOT NULL, `name` TEXT NOT NULL, `route` TEXT NOT NULL, `iconKey` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, `isSystem` INTEGER NOT NULL, `smartEnabled` INTEGER NOT NULL, `smartKeywords` TEXT NOT NULL, `parentId` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `TagEntity` (`key` TEXT NOT NULL, `name` TEXT NOT NULL, `colorKey` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `CustomEntryEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` TEXT NOT NULL, `date` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `tags` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_CustomEntryEntity_categoryId` ON `CustomEntryEntity` (`categoryId`)")
        }

        private data class Seed(
            val key: String,
            val name: String,
            val route: String,
            val icon: String,
            val order: Int,
            val smart: Boolean,
            val keywords: String,
        )

        private fun seedCategories(db: SupportSQLiteDatabase) {
            val rows = listOf(
                Seed("smart_input", "智能录入", "smart_input", "mic", 0, false, ""),
                Seed("timeline", "总时间轴", "timeline", "timeline", 10, false, ""),
                Seed("exams", "检查报告", "exams", "description", 20, true, "MRI,磁共振,CT,PET,病理,活检,免疫组化,彩超,心电图,抽血,血常规,尿检,检查,报告"),
                Seed("treatment", "治疗方案与记录", "treatment", "medical", 30, true, "化疗,放疗,手术,治疗,定位,输液港,PICC,方案,周期,复查,评估"),
                Seed("supplies", "药品用品", "supplies", "medication", 40, true, "买,购买,准备,用品,喷雾,体温计,蛋白粉,营养粉,乳果糖,益生菌,比亚芬,康复新液,药店"),
                Seed("symptoms", "症状记录", "symptoms", "heart", 50, true, "尿频,尿急,尿痛,恶心,呕吐,腹泻,便秘,出血,疼,痛,乏力,疲劳,没胃口,食欲,发热,发烧,分泌物,头晕,腹胀"),
                Seed("temperature", "体温记录", "temperature", "thermostat", 60, true, "体温,发热,发烧,℃,度"),
                Seed("questions", "医生问诊", "questions", "question", 70, true, "要问,问医生,请问,想问,需要问,？,?"),
                Seed("expenses", "费用记录", "expenses", "payments", 80, true, "花了,费用,挂号,缴费,交了,支付,自费,报销,元,¥,￥"),
                Seed("import", "导入更新包", "import", "upload", 90, false, ""),
                Seed("settings", "备份与恢复", "settings", "backup", 100, false, ""),
            )
            rows.forEach { row ->
                db.execSQL(
                    "INSERT OR IGNORE INTO `CategoryEntity` (`key`,`name`,`route`,`iconKey`,`sortOrder`,`hidden`,`isSystem`,`smartEnabled`,`smartKeywords`,`parentId`,`updatedAt`) VALUES (?,?,?,?,?,0,1,?,?,NULL,?)",
                    arrayOf(row.key, row.name, row.route, row.icon, row.order, if (row.smart) 1 else 0, row.keywords, System.currentTimeMillis())
                )
            }
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "treatment_vault.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        seedCategories(db)
                    }
                })
                .build()
                .also { INSTANCE = it }
        }
    }
}
