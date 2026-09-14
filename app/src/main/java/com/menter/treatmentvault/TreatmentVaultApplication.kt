package com.menter.treatmentvault

import android.app.Application
import com.menter.treatmentvault.data.AppDatabase
import com.menter.treatmentvault.data.CategoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TreatmentVaultApplication : Application() {
    val database by lazy { AppDatabase.get(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val dao = database.dao()
            if (dao.categoryByKey("floating_audio") == null) {
                dao.saveCategory(
                    CategoryEntity(
                        key = "floating_audio",
                        name = "悬浮录音",
                        route = "floating_audio",
                        iconKey = "circle",
                        sortOrder = 7,
                        isSystem = true,
                        smartEnabled = false,
                    )
                )
            }
        }
    }
}
