package com.classflow.timetable.di

import com.classflow.timetable.core.time.AppClock
import com.classflow.timetable.core.time.SystemAppClock
import com.classflow.timetable.data.remote.sztu.CookieJar
import com.classflow.timetable.data.remote.sztu.MiniHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppClock(): AppClock = SystemAppClock()

    /**
     * 公文通专用的 HTTP 客户端。
     *
     * 特意**不复用**教务系统的那个（[com.classflow.timetable.data.remote.sztu.SztuAuthClient]）：
     * 那个实例的 CookieJar 里装着统一身份认证的会话票据，公文通是公开页面，
     * 既不需要也不该把票据发给另一个站点。
     */
    @Provides
    @Singleton
    @Named("nbw")
    fun provideNbwHttp(): MiniHttp = MiniHttp(CookieJar())
}
