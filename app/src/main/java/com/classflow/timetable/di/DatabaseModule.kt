package com.classflow.timetable.di

import android.content.Context
import androidx.room.Room
import com.classflow.timetable.data.local.AppDatabase
import com.classflow.timetable.data.local.MIGRATION_1_2
import com.classflow.timetable.data.local.CourseDao
import com.classflow.timetable.data.local.CourseSessionDao
import com.classflow.timetable.data.local.ImportSourceDao
import com.classflow.timetable.data.local.SemesterDao
import com.classflow.timetable.data.local.TimeSlotDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "classflow.db")
            // 不添加 fallbackToDestructiveMigration：迁移失败必须报错而不是清库
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideSemesterDao(db: AppDatabase): SemesterDao = db.semesterDao()

    @Provides
    fun provideTimeSlotDao(db: AppDatabase): TimeSlotDao = db.timeSlotDao()

    @Provides
    fun provideCourseDao(db: AppDatabase): CourseDao = db.courseDao()

    @Provides
    fun provideCourseSessionDao(db: AppDatabase): CourseSessionDao = db.courseSessionDao()

    @Provides
    fun provideImportSourceDao(db: AppDatabase): ImportSourceDao = db.importSourceDao()
}
