package com.classflow.timetable.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * 一场考试。
 *
 * 字段全部对应教务页面上真实存在的列，不做任何"推测填充"：
 * 页面上没写座位号，[seat] 就是空串，界面上也不显示这一格。
 *
 * [rawTime] 一定保留原始文本 —— 教务的时间格式五花八门（"2026-01-05(19周星期一) 09:00-11:00"、
 * "1月5日 上午 9:00"…），解析不出来的时候界面直接显示原文，总比显示空白好。
 */
data class ExamEntry(
    val course: String,
    /** 考试名称 / 批次，例如「2026-2027-1 期末考试」。用来判断是不是期末。 */
    val name: String = "",
    val date: LocalDate? = null,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val rawTime: String = "",
    val location: String = "",
    val seat: String = "",
    /** 考试形式：闭卷 / 开卷 / 机考… */
    val form: String = "",
    val note: String = "",
) {
    /** 去重用：教务偶尔把同一场合考拆成两行。 */
    val identity: String get() = "$course|$date|$start|$end|$location"
}

/** 教务考试页里的学期选项。 */
data class ExamSemester(
    val key: String,
    val label: String,
)
