package com.college.library

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.college.library.utils.OverdueCheckWorker
import com.college.library.utils.OverdueNotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LibraryApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Create notification channels (safe to call multiple times)
        OverdueNotificationHelper.createChannels(this)
        // Schedule the periodic overdue background check (every 12 hours)
        OverdueCheckWorker.schedule(this)
    }
}
