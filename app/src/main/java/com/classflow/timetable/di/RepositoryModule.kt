package com.classflow.timetable.di

import com.classflow.timetable.data.repository.SettingsRepositoryImpl
import com.classflow.timetable.data.repository.TimetableRepositoryImpl
import com.classflow.timetable.domain.repository.SettingsRepository
import com.classflow.timetable.domain.repository.TimetableRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTimetableRepository(impl: TimetableRepositoryImpl): TimetableRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
