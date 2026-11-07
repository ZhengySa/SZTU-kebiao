package com.classflow.timetable.core.di

import android.content.Context
import com.classflow.timetable.domain.repository.SettingsRepository
import com.classflow.timetable.domain.repository.TimetableRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 非 Activity 场景（Glance 小组件、Worker、BroadcastReceiver）取依赖的唯一入口。
 *
 * 这些组件的生命周期由系统控制，不经过 Hilt 的 Activity 注入，
 * 所以只能用 EntryPoint 从 Application 的依赖图里取已经建好的单例。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun timetableRepository(): TimetableRepository
    fun settingsRepository(): SettingsRepository
}

fun appEntryPoint(context: Context): AppEntryPoint =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        AppEntryPoint::class.java,
    )
