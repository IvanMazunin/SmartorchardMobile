package com.smartorchard.mobile

import android.app.Application
import androidx.work.Configuration
import com.smartorchard.mobile.data.sync.OrchardWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SmartOrchardApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: OrchardWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
