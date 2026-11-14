package com.smartorchard.mobile.di

import android.content.Context
import androidx.room.Room
import com.smartorchard.mobile.data.local.OrchardDao
import com.smartorchard.mobile.data.local.OrchardDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OrchardDatabase =
        Room.databaseBuilder(context, OrchardDatabase::class.java, "smartorchard.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideDao(database: OrchardDatabase): OrchardDao = database.dao()
}
