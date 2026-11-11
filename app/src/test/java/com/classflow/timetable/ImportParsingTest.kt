package com.classflow.timetable

import com.classflow.timetable.domain.imports.CsvParser
import com.classflow.timetable.domain.imports.IcsParser
import com.classflow.timetable.domain.imports.ImportSupport
import com.classflow.timetable.domain.model.Semester
import com.classflow.timetable.domain.model.SlotSection
import com.classflow.timetable.domain.model.TimeSlot
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * ICS / CSV 解析的单元测试。
 *
 * 这些用例全部跑在 JVM 上（不依赖 Android），因为解析逻辑是纯函数，
 * 一旦哪天因为「导入后课程位置错乱」被投诉，先跑这个测试就能定位是解析错了还是渲染错了。
 */
class ImportParsingTest {

    /** 2026-09-07 是周一，方便手算周次：第 1 周 = 09-07 ~ 09-13。 */
    private val semester = Semester(
        id = 1L,
        name = "2026 秋",
        startDate = LocalDate.of(2026, 9, 7),
        totalWeeks = 16,
        isActive = true,
    )

    private val slots = listOf(
        slot(1, "08:00", "08:45"),
        slot(2, "08:55", "09:40"),
        slot(3, "10:00", "10:45"),
        slot(4, "10:55", "11:40"),
        slot(5, "14:00", "14:45"),
        slot(6, "14:55", "15:40"),
    )

    private fun slot(index: Int, start: String, end: String): TimeSlot {
        fun parse(t: String) = LocalTime.parse(t)
        return TimeSlot(
            id = index.toLong(),
            index = index,
            start = parse(start),
            end = parse(end),
            section = if (index <= 4) SlotSection.MORNING else SlotSection.AFTERNOON,
        )
    }

    // ---------- ICS ----------

    @Test
    fun `ics 每周重复的事件按 totalWeeks 展开到全部周`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:高等数学
            LOCATION:教三-201
            DTSTART:20260907T080000
            DTEND:20260907T094000
            RRULE:FREQ=WEEKLY;COUNT=8
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = IcsParser.parse(ics, semester, slots)

        assertThat(result.courses).hasSize(1)
        val course = result.courses.first()
        assertThat(course.name).isEqualTo("高等数学")
        assertThat(course.location).isEqualTo("教三-201")
        assertThat(course.sessions).hasSize(1)

        val session = course.sessions.first()
        assertThat(session.dayOfWeek).isEqualTo(1)          // 周一
        assertThat(session.startSlot).isEqualTo(1)
        assertThat(session.endSlot).isEqualTo(2)
        assertThat(session.weeks).containsExactly(1, 2, 3, 4, 5, 6, 7, 8)
    }

    @Test
    fun `ics 的 BYDAY 多天会拆成多个时段`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:大学英语
            DTSTART:20260909T100000
            DTEND:20260909T114000
            RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=4;BYDAY=MO,WE
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = IcsParser.parse(ics, semester, slots)

        val sessions = result.courses.first().sessions
        assertThat(sessions.map { it.dayOfWeek }).containsExactly(1, 3)
        sessions.forEach { session ->
            assertThat(session.startSlot).isEqualTo(3)
            assertThat(session.endSlot).isEqualTo(4)
            assertThat(session.weeks).isEqualTo(setOf(1, 2, 3, 4))
        }
    }

    @Test
    fun `ics 的单次事件只落在它所在的那一周`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:讲座
            DTSTART:20260924T140000
            DTEND:20260924T154000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        // 09-24 是第 3 周的周四
        val result = IcsParser.parse(ics, semester, slots)

        val session = result.courses.first().sessions.first()
        assertThat(session.dayOfWeek).isEqualTo(4)
        assertThat(session.weeks).isEqualTo(setOf(3))
    }

    @Test
    fun `ics 全天事件会被跳过并给出警告而不是猜时间`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:校运会
            DTSTART;VALUE=DATE:20260907
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = IcsParser.parse(ics, semester, slots)

        assertThat(result.courses).isEmpty()
        assertThat(result.skippedItems).isEqualTo(1)
        assertThat(result.warnings).isNotEmpty()
    }

    @Test
    fun `ics 折行的长行能正确拼接`() {
        // RFC 5545：折行以「一个空格或制表符」开头，展开时要去掉这个前导字符再拼上
        val ics = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:非常长的\n 课程名称\n" +
            "DTSTART:20260907T080000\nDTEND:20260907T094000\nEND:VEVENT\nEND:VCALENDAR"

        val result = IcsParser.parse(ics, semester, slots)

        assertThat(result.courses).hasSize(1)
        assertThat(result.courses.first().name).isEqualTo("非常长的课程名称")
    }

    // ---------- CSV ----------

    @Test
    fun `csv 表头可乱序并识别中英文别名`() {
        val csv = """
            教师,课程名,星期,开始节,结束节,地点,周次
            张老师,线性代数,周二,3,4,教二-101,1-8
        """.trimIndent()

        val result = CsvParser.parse(csv, semester)

        assertThat(result.courses).hasSize(1)
        val course = result.courses.first()
        assertThat(course.name).isEqualTo("线性代数")
        assertThat(course.teacher).isEqualTo("张老师")
        assertThat(course.location).isEqualTo("教二-101")
        assertThat(course.sessions.first().dayOfWeek).isEqualTo(2)
        assertThat(course.sessions.first().weeks).containsExactly(1, 2, 3, 4, 5, 6, 7, 8)
    }

    @Test
    fun `csv 同名多行会合并成一门课的两个时段`() {
        val csv = """
            课程名,星期,开始节,结束节,周次
            数据结构,周一,1,2,1-16
            数据结构,周三,5,6,1-16
        """.trimIndent()

        val result = CsvParser.parse(csv, semester)

        assertThat(result.courses).hasSize(1)
        assertThat(result.courses.first().sessions).hasSize(2)
    }

    @Test
    fun `csv 双周表达式只留偶数周`() {
        val csv = """
            课程名,星期,开始节,结束节,周次
            体育,周五,5,6,1-16双
        """.trimIndent()

        val weeks = CsvParser.parse(csv, semester).courses.first().sessions.first().weeks

        assertThat(weeks).hasSize(8)
        assertThat(weeks.all { it % 2 == 0 }).isTrue()
    }

    @Test
    fun `csv 坏行不会中断整个导入`() {
        val csv = """
            课程名,星期,开始节,结束节,周次
            正常课,周一,1,2,1-8
            ,周二,1,2,1-8
            坏星期,星期八,1,2,1-8
        """.trimIndent()

        val result = CsvParser.parse(csv, semester)

        assertThat(result.courses).hasSize(1)
        assertThat(result.skippedItems).isEqualTo(2)
        assertThat(result.warnings).hasSize(2)
    }

    @Test
    fun `csv 缺少必要列时直接拒绝而不是导入一堆空课`() {
        val csv = """
            课程名,教师
            高等数学,张老师
        """.trimIndent()

        val result = CsvParser.parse(csv, semester)

        assertThat(result.courses).isEmpty()
        assertThat(result.warnings.first()).contains("表头")
    }

    // ---------- 一门课多个地点 ----------

    @Test
    fun `csv 同一门课不同行不同地点 各自带自己的教室`() {
        val csv = """
            课程名,教师,地点,星期,开始节,结束节,周次
            传感器原理及应用,文克春,C-5-215,周三,1,2,1-17
            传感器原理及应用,文克春,D-3-321,周二,5,6,11-15
        """.trimIndent()

        val result = CsvParser.parse(csv, semester)

        assertThat(result.courses).hasSize(1)
        val course = result.courses.first()
        assertThat(course.sessions).hasSize(2)
        assertThat(course.sessions.map { it.location }).containsExactly("C-5-215", "D-3-321")
        // 两个时段两间教室，课程级地点必须留空，否则又会退化成「一串地点」
        assertThat(course.location).isNull()
    }

    @Test
    fun `csv 带 BOM 也能认表头`() {
        // Excel 另存的 CSV 几乎都带 BOM，不带处理的话整份表格会被判成表头不完整
        val csv = "﻿" + """
            课程名,地点,星期,开始节,结束节,周次
            高等数学,教三-201,周一,1,2,1-16
        """.trimIndent()

        val result = CsvParser.parse(csv, semester)

        assertThat(result.courses).hasSize(1)
        assertThat(result.courses.first().name).isEqualTo("高等数学")
    }

    @Test
    fun `csv 全课同一间教室时 课程级地点才有值`() {
        val csv = """
            课程名,地点,星期,开始节,结束节,周次
            高等数学,教三-201,周一,1,2,1-16
            高等数学,教三-201,周三,3,4,1-16
        """.trimIndent()

        val course = CsvParser.parse(csv, semester).courses.first()

        assertThat(course.sessions).hasSize(2)
        assertThat(course.location).isEqualTo("教三-201")
    }

    @Test
    fun `ics 同名课程的两个 VEVENT 各自保留自己的地点`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:传感器原理及应用
            LOCATION:C-5-215
            DTSTART:20260907T080000
            DTEND:20260907T094000
            RRULE:FREQ=WEEKLY;COUNT=6
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:传感器原理及应用
            LOCATION:D-3-321
            DTSTART:20260909T100000
            DTEND:20260909T114000
            RRULE:FREQ=WEEKLY;COUNT=6
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = IcsParser.parse(ics, semester, slots)

        assertThat(result.courses).hasSize(1)
        val course = result.courses.first()
        assertThat(course.sessions).hasSize(2)
        val byLocation = course.sessions.associateBy { it.location }
        assertThat(byLocation.keys).containsExactly("C-5-215", "D-3-321")
        // 周一那节课在 C-5-215，周三那节在 D-3-321，不能对调
        assertThat(byLocation.getValue("C-5-215").dayOfWeek).isEqualTo(1)
        assertThat(byLocation.getValue("D-3-321").dayOfWeek).isEqualTo(3)
        assertThat(course.location).isNull()
    }

    // ---------- 周次表达式 ----------

    @Test
    fun `周次表达式支持区间 逗号 单双周 空值`() {
        assertThat(ImportSupport.parseWeekExpression("1-4", 16)).isEqualTo(setOf(1, 2, 3, 4))
        assertThat(ImportSupport.parseWeekExpression("1-2,5-6", 16)).isEqualTo(setOf(1, 2, 5, 6))
        assertThat(ImportSupport.parseWeekExpression("", 16)).hasSize(16)
        assertThat(ImportSupport.parseWeekExpression("单周", 16).all { it % 2 == 1 }).isTrue()
        // 超出总周数的部分要被裁掉，不能出现「第 20 周」这种不存在的周
        assertThat(ImportSupport.parseWeekExpression("1-30", 16)).hasSize(16)
    }
}
