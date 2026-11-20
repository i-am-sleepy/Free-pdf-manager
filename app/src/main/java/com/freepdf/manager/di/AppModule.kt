package com.freepdf.manager.di

import android.content.Context
import com.freepdf.manager.db.FileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFileRepository(@ApplicationContext context: Context): FileRepository =
        FileRepository(context)

    @Provides
    @Singleton
    fun provideStorageDir(@ApplicationContext context: Context): File =
        File(context.filesDir, "pdfs").apply { mkdirs() }
}
