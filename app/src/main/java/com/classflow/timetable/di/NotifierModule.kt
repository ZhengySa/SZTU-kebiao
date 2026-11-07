package com.classflow.timetable.di

import com.classflow.timetable.domain.sync.ScheduleChangeNotifier
import com.classflow.timetable.widget.AppScheduleChangeNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 仓库层只认识 [ScheduleChangeNotifier] 这个接口，
 * 真正的实现在 widget 包里（要刷新 Glance 小组件、重排闹钟）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotifierModule {

    @Binds
    @Singleton
    abstract fun bindScheduleChangeNotifier(impl: AppScheduleChangeNotifier): ScheduleChangeNotifier
}
