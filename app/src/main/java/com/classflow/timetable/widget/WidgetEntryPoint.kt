package com.classflow.timetable.widget

import android.content.Context
import com.classflow.timetable.core.di.appEntryPoint
import com.classflow.timetable.domain.repository.TimetableRepository

/** 小组件取仓库：统一走 [appEntryPoint]，不在这里另建依赖图。 */
internal fun widgetRepository(context: Context): TimetableRepository =
    appEntryPoint(context).timetableRepository()
