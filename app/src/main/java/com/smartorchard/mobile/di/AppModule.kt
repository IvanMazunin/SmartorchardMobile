package com.smartorchard.mobile.di

import com.smartorchard.mobile.data.OfflineFirstRepository
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.data.sync.SyncScheduler
import com.smartorchard.mobile.data.sync.WorkManagerSyncScheduler
import com.smartorchard.mobile.network.ConnectivityNetworkMonitor
import com.smartorchard.mobile.network.EncryptedTokenStorage
import com.smartorchard.mobile.network.NetworkMonitor
import com.smartorchard.mobile.network.TokenStorage
import com.smartorchard.mobile.services.DemoDiagnosisService
import com.smartorchard.mobile.services.BluetoothPlantPrinter
import com.smartorchard.mobile.services.PlantPrinter
import com.smartorchard.mobile.services.DiagnosisService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun bindPlantPrinter(impl: BluetoothPlantPrinter): PlantPrinter


    @Binds
    @Singleton
    abstract fun bindRepository(impl: OfflineFirstRepository): OrchardRepository

    @Binds
    @Singleton
    abstract fun bindDiagnosisService(impl: DemoDiagnosisService): DiagnosisService

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor

    @Binds
    @Singleton
    abstract fun bindTokenStorage(impl: EncryptedTokenStorage): TokenStorage

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
