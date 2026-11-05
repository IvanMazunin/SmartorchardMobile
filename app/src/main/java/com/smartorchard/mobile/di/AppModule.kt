package com.smartorchard.mobile.di

import android.content.Context
import com.smartorchard.mobile.data.MockRepository
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.services.DemoDiagnosisService
import com.smartorchard.mobile.services.DiagnosisService
import com.smartorchard.mobile.services.PhotoStore
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
     * Источник данных. Для переключения на бэкенд замените на:
     *   fun provideRepository(remote: RemoteRepository): OrchardRepository = remote
     */
    @Provides
    @Singleton
    fun provideRepository(@ApplicationContext context: Context): OrchardRepository =
        MockRepository(context)

    @Provides
    @Singleton
    fun provideDiagnosisService(): DiagnosisService = DemoDiagnosisService()

    @Provides
    @Singleton
    fun providePhotoStore(@ApplicationContext context: Context): PhotoStore =
        PhotoStore(context)
}
