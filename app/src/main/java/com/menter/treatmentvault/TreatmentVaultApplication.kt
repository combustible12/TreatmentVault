package com.menter.treatmentvault

import android.app.Application
import com.menter.treatmentvault.data.AppDatabase

class TreatmentVaultApplication : Application() {
    val database by lazy { AppDatabase.get(this) }
}
