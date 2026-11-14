package com.smartorchard.mobile.di

import com.smartorchard.mobile.data.OfflineFirstRepository
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.services.DemoDiagnosisService
import com.smartorchard.mobile.services.DiagnosisService
import com.smartorchard.mobile.services.PhotoStore
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Источник данных с оффлайн-поддержкой (Room).
     * Для переключения на моки:
     *   fun provideRepository(context: Context): OrchardRepository = MockRepository(context)
     */
    @Provides
    @Singleton
    fun provideRepository(offline: OfflineFirstRepository): OrchardRepository = offline

    @Provides
    @Singleton
    fun provideDiagnosisService(): DiagnosisService = DemoDiagnosisService()

    @Provides
    @Singleton
    fun providePhotoStore(@ApplicationContext context: Context): PhotoStore =
        PhotoStore(context)
}
