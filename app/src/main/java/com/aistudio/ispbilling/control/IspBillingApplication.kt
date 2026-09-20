package com.aistudio.ispbilling.control

import android.app.Application
import com.aistudio.ispbilling.control.data.local.AppDatabase
import com.aistudio.ispbilling.control.data.sync.SyncScheduler
import com.aistudio.ispbilling.control.data.sync.SyncWorker

class IspBillingApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        AppDatabase.getInstance(this)

        SyncScheduler.schedule(this)

        SyncWorker.enqueue(this)
    }
}
