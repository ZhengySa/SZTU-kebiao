package com.classflow.timetable

import com.classflow.timetable.domain.imports.ImportedSession
import com.classflow.timetable.domain.usecase.SessionMergePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 合并导入的时段规则。
 *
 * 这组用例针对的是一个真实事故：1.0.2 之前导入的课，教室只存在课程级，
 * 时段是空的；而「合并」模式下同名课程被整门跳过，于是这些课永远补不上教室，
 * 用户只能自己一个个手输。下面第一条就是这个场景。
 */
class SessionMergePlanTest {

    private fun existing(
        id: Long,
        day: Int,
        start: Int,
        end: Int,
        location: String? = null,
    ) = SessionMergePlan.Existing(id, day, start, end, location)

    private fun incoming(
        day: Int,
        start: Int,
        end: Int,
        location: String? = null,
    ) = ImportedSession(
        dayOfWeek = day,
        startSlot = start,
        endSlot = end,
        weeks = (1..16).toSet(),
        location = location,
    )

    @Test
    fun `老数据时段没地点 合并时按时间对齐补上`() {
        val plan = SessionMergePlan.plan(
            existing = listOf(
                existing(id = 1, day = 3, start = 6, end = 7),
                existing(id = 2, day = 4, start = 8, end = 10),
                existing(id = 3, day = 2, start = 11, end = 13),
            ),
            incoming = listOf(
                incoming(day = 3, start = 6, end = 7, location = "C-5-215"),
                incoming(day = 4, start = 8, end = 10, location = "C-5-215"),
                incoming(day = 2, start = 11, end = 13, location = "D-3-321"),
            ),
        )

        assertTrue("不该插入新时段", plan.inserts.isEmpty())
        assertEquals(
            "三个时段各拿自己那间教室，不能串",
            listOf(
                SessionMergePlan.Patch(1, "C-5-215"),
                SessionMergePlan.Patch(2, "C-5-215"),
                SessionMergePlan.Patch(3, "D-3-321"),
            ),
            plan.patches,
        )
    }

    @Test
    fun `用户手填过的地点不被教务覆盖`() {
        val plan = SessionMergePlan.plan(
            existing = listOf(existing(id = 9, day = 1, start = 1, end = 2, location = "自己改的：实验楼")),
            incoming = listOf(incoming(day = 1, start = 1, end = 2, location = "C-5-101")),
        )

        assertTrue("没有空位要补，就不该产生修改", plan.isEmpty)
    }

    @Test
    fun `教务新排出的时段会被插入`() {
        val plan = SessionMergePlan.plan(
            existing = listOf(existing(id = 1, day = 1, start = 1, end = 2, location = "C-5-101")),
            incoming = listOf(
                incoming(day = 1, start = 1, end = 2, location = "C-5-101"),
                incoming(day = 5, start = 3, end = 4, location = "C-5-202"),
            ),
        )

        assertTrue("已有的不用动", plan.patches.isEmpty())
        assertEquals(1, plan.inserts.size)
        assertEquals(5, plan.inserts.single().dayOfWeek)
        assertEquals("C-5-202", plan.inserts.single().location)
    }

    @Test
    fun `教务没给教室时 不动已有的空位`() {
        val plan = SessionMergePlan.plan(
            existing = listOf(existing(id = 1, day = 1, start = 1, end = 2)),
            incoming = listOf(incoming(day = 1, start = 1, end = 2, location = null)),
        )

        assertTrue("没拿到教室就没得补，别写空串进去", plan.isEmpty)
    }

    @Test
    fun `课程换了上课时间 按时间对齐而不是按顺序`() {
        // 旧数据有周二 1-2 节；教务这次把它挪到周四 3-4 节
        val plan = SessionMergePlan.plan(
            existing = listOf(existing(id = 1, day = 2, start = 1, end = 2)),
            incoming = listOf(incoming(day = 4, start = 3, end = 4, location = "C-5-215")),
        )

        assertTrue("时间对不上就不该改旧记录", plan.patches.isEmpty())
        assertEquals("应该新增一个时段", 1, plan.inserts.size)
    }
}
