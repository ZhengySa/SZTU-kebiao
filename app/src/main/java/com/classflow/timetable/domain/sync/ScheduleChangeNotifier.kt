package com.classflow.timetable.domain.sync

/**
 * 课表数据变更的对外通知口。
 *
 * 为什么放在 domain：仓库层只管存数据，不该知道桌面小组件、提醒这些平台细节。
 * 需要「数据一变就刷新」的地方（小组件、提醒重排）实现这个接口即可，
 * 仓库层只依赖接口，测试时传一个空实现就行。
 */
interface ScheduleChangeNotifier {

    /** 课程/节次/学期任一变化后调用，实现方负责去抖，可以高频调用。 */
    fun notifyScheduleChanged()
}

/** 单元测试与预览用：什么都不做。 */
object NoopScheduleChangeNotifier : ScheduleChangeNotifier {
    override fun notifyScheduleChanged() = Unit
}
