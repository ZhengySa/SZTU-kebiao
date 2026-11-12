package com.classflow.timetable.ui.navigation

object Routes {
    const val TODAY = "today"
    const val WEEK = "week"
    const val SETTINGS = "settings"

    const val COURSE_DETAIL = "course/{courseId}"
    const val COURSE_EDIT = "courseEdit?courseId={courseId}"
    const val SEMESTER = "semester"
    const val SLOTS = "slots"
    const val PRIVACY = "privacy"
    const val ABOUT = "about"
    const val IMPORT = "import"
    const val SZTU_LOGIN = "sztuLogin"
    const val BACKUP = "backup"
    const val WIDGET_GUIDE = "widgetGuide"
    const val BUS = "bus"
    const val MAP = "map"
    const val POMODORO = "pomodoro"
    const val GRADES = "grades"
    const val EXAMS = "exams"
    const val LUCK = "luck"

    // 情侣课表：不再是独立页面，而是「我 / TA」一键切换；
    // 只有「导入 TA 的课表」这一条登录流程还需要单独的路由。
    const val PARTNER_LOGIN = "partnerLogin"

    /** 公文通列表 / 详情。详情路径里的斜杠要编码，否则会被当成多级路由。 */
    const val NBW = "nbw"
    const val NBW_DETAIL = "nbwDetail?path={path}"

    const val ARG_COURSE_ID = "courseId"
    const val ARG_NBW_PATH = "path"

    fun courseDetail(courseId: Long): String = "course/$courseId"

    fun courseEdit(courseId: Long?): String = "courseEdit?courseId=${courseId ?: 0L}"

    fun nbwDetail(path: String): String =
        "nbwDetail?path=" + android.net.Uri.encode(path)
}
