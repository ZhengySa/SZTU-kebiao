package com.classflow.timetable

import com.classflow.timetable.data.remote.sztu.ZfKbParser
import com.classflow.timetable.domain.imports.ImportedCourse
import com.classflow.timetable.domain.usecase.SztuCourseAggregator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「一门课多个时间多个教室」的对应关系。
 *
 * 这组用例锁的是最容易回退的一个细节：地点必须挂在时段上，
 * 不能挂在课程上拼成一串 —— 拼成一串之后，学生看到三个时间三个教室，
 * 却不知道哪个时间去哪一栋楼。
 */
class SztuCourseAggregatorTest {

    private fun courses(): List<ImportedCourse> {
        val html = requireNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/xskb_xskb_list.html"))
            .bufferedReader()
            .readText()
        return SztuCourseAggregator.aggregate(ZfKbParser.parse(html).entries)
    }

    private fun List<ImportedCourse>.named(name: String): ImportedCourse =
        firstOrNull { it.name == name } ?: error("没解析到「$name」")

    @Test
    fun `传感器 - 三个时段各自带自己的教室`() {
        val sensor = courses().named("传感器原理及应用")

        assertEquals("三个时段不该被合并成一条", 3, sensor.sessions.size)

        val actual = sensor.sessions
            .map { "${it.dayOfWeek}-${it.startSlot}-${it.endSlot}@${it.location}" }
            .toSet()
        assertEquals(
            setOf(
                "3-6-7@C-5-215",
                "4-8-10@C-5-215",
                "2-11-13@D-3-321",
            ),
            actual,
        )

        // 三个时段两间教室，课程级地点必须留空，否则又变回「一串地点」
        assertNull("多地点时课程级地点应为空", sensor.location)
    }

    @Test
    fun `智能制造实践 - 换周次换教室，地点跟着周次走`() {
        val practice = courses().named("智能制造实践")
        assertEquals(3, practice.sessions.size)

        val byRoom = practice.sessions.associate { it.location to it.weeks }
        assertEquals(setOf(3), byRoom["D-3-203A（阶梯教室）"])
        assertEquals((4..13).toSet(), byRoom["D-3-115"])
        assertEquals(setOf(14), byRoom["C-5-304"])
    }

    @Test
    fun `人工智能算法导论 - 同一时段不同周次不同教室不被合并`() {
        val ai = courses().named("人工智能算法导论")
        val rooms = ai.sessions.map { it.location }.toSet()
        assertEquals(setOf("C-5-222", "C-5-360机房"), rooms)
        assertEquals(2, ai.sessions.size)
    }

    @Test
    fun `只有一个教室的课 - 课程级地点才有值`() {
        val math = courses().named("复变函数与积分变换")
        assertEquals("C-5-211", math.location)
        assertTrue(math.sessions.all { it.location == "C-5-211" })
    }

    @Test
    fun `每个时段都有地点，没有一门课是靠课程级地点兜底的`() {
        courses().forEach { course ->
            course.sessions.forEach { session ->
                assertTrue(
                    "「${course.name}」的 ${session.dayOfWeek} 第 ${session.startSlot} 节没有地点",
                    !session.location.isNullOrBlank(),
                )
            }
        }
    }
}
