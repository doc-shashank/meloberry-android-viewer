package com.meloberry.android.core.database.di

import android.content.Context
import androidx.room.Room
import com.meloberry.android.core.database.MeloberryDatabase
import com.meloberry.android.core.database.dao.AnalysisRunDao
import com.meloberry.android.core.database.dao.WorkspaceDao
import com.meloberry.android.core.model.ApplicationScope
import com.meloberry.android.core.model.DefaultDispatcher
import com.meloberry.android.core.model.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMeloberryDatabase(
        @ApplicationContext context: Context
    ): MeloberryDatabase = Room.databaseBuilder(
        context,
        MeloberryDatabase::class.java,
        "meloberry_ssot.db"
    )
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideWorkspaceDao(db: MeloberryDatabase): WorkspaceDao = db.workspaceDao()

    @Provides
    fun provideAnalysisRunDao(db: MeloberryDatabase): AnalysisRunDao = db.analysisRunDao()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
}
