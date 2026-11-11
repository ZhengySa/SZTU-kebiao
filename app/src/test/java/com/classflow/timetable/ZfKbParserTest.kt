package com.classflow.timetable

import com.classflow.timetable.data.remote.sztu.ZfKbParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实抓下来的「学期理论课表」页面做回归测试。
 *
 * 为什么要有这个测试：教务系统同步是整条链路里最容易悄悄坏掉的一环。
 * 页面结构一改，解析器可能只是"少解析出几门课"而不报错——用户看到的是一张缺课的课表，
 * 却以为导入成功了。把真实页面钉成夹具，结构一变测试就红。
 *
 * 夹具来源：jwxt.sztu.edu.cn/jsxsd/xskb/xskb_list.do（2026-2027-1 学期，已脱敏保留全部课程字段）。
 */
class ZfKbParserTest {

    private fun loadFixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "找不到夹具 $name" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    @Test
    fun `解析真实课表页 - 节次表是 15 节且时间正确`() {
        val timetable = ZfKbParser.parse(loadFixture("xskb_xskb_list.html"))

        // 学校的课表把 15 小节压成 9 行（1-2 / 3-4 / 5 / 6-7 / 8-9 / 10 / 11-12 / 13-14 / 15）
        assertEquals(15, timetable.slots.size)
        assertEquals((1..15).toList(), timetable.slots.map { it.index })

        fun slot(index: Int) = checkNotNull(timetable.slots.firstOrNull { it.index == index }) {
            "缺少第 $index 节"
        }

        // 合并行要拆成每节自己的时间，不能两节共用整行区间
        assertEquals(8 * 60 + 30, slot(1).startMinute)
        assertEquals(9 * 60 + 10, slot(1).endMinute)
        assertEquals(9 * 60 + 15, slot(2).startMinute)
        assertEquals(9 * 60 + 55, slot(2).endMinute)

        // 晚上连上两节没有课间：19:00-20:20 / 20:30-21:50
        assertEquals(19 * 60, slot(11).startMinute)
        assertEquals(19 * 60 + 40, slot(11).endMinute)
        assertEquals(19 * 60 + 40, slot(12).startMinute)
        assertEquals(20 * 60 + 20, slot(12).endMinute)

        // 这条是老 bug 的正题：单上第 13 节是 21:10 下课，不是 21:50
        assertEquals(20 * 60 + 30, slot(13).startMinute)
        assertEquals(21 * 60 + 10, slot(13).endMinute)
        assertEquals(21 * 60 + 10, slot(14).startMinute)
        assertEquals(21 * 60 + 50, slot(14).endMinute)

        // 第 15 节是傍晚单独一节，时间早于 11-14 节
        assertEquals(18 * 60, slot(15).startMinute)
        assertEquals(18 * 60 + 40, slot(15).endMinute)
    }

    @Test
    fun `解析真实课表页 - 课程字段完整`() {
        val timetable = ZfKbParser.parse(loadFixture("xskb_xskb_list.html"))

        assertTrue("至少要解析出几门课", timetable.entries.size >= 5)

        val digital = timetable.entries.firstOrNull {
            it.name == "数字电子技术A" && it.dayOfWeek == 1 && it.startSlot == 1
        }
        checkNotNull(digital) { "没解析到「数字电子技术A」周一第 1-2 节" }

        assertEquals(2, digital.endSlot)
        assertEquals("罗一星", digital.teacher)
        assertEquals("C-5-225", digital.room)
        assertEquals("C-5公共教学楼", digital.building)
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15, 17), digital.weeks)
    }

    @Test
    fun `解析真实课表页 - 所有条目都落在合法范围内`() {
        val timetable = ZfKbParser.parse(loadFixture("xskb_xskb_list.html"))
        val slotCount = timetable.slots.size

        timetable.entries.forEach { entry ->
            assertTrue("星期越界：${entry.name}", entry.dayOfWeek in 1..7)
            assertTrue("起始节次越界：${entry.name}", entry.startSlot in 1..slotCount)
            assertTrue("结束节次越界：${entry.name}", entry.endSlot in 1..slotCount)
            assertTrue("节次区间反了：${entry.name}", entry.startSlot <= entry.endSlot)
            assertTrue("周次为空：${entry.name}", entry.weeks.isNotEmpty())
            assertTrue("课程名为空", entry.name.isNotBlank())
        }
    }

    @Test
    fun `解析真实课表页 - 同一门课不会因跨行重复出现两次`() {
        val timetable = ZfKbParser.parse(loadFixture("xskb_xskb_list.html"))
        val keys = timetable.entries.map { it.dedupeKey }
        assertEquals("存在重复条目", keys.size, keys.toSet().size)
    }

    @Test
    fun `解析真实课表页 - 能读到当前学期`() {
        val timetable = ZfKbParser.parse(loadFixture("xskb_xskb_list.html"))
        assertEquals("2026-2027-1", timetable.currentSemester)
        assertTrue(timetable.semesterKeys.contains("2026-2027-1"))
    }

    @Test
    fun `周次解析 - 支持区间与逗号列表`() {
        assertEquals((1..16).toSet(), ZfKbParser.parseWeeks("1-16"))
        assertEquals((1..8).toSet() + (10..18).toSet(), ZfKbParser.parseWeeks("1-8,10-18"))
        assertEquals(setOf(1, 3, 5, 7), ZfKbParser.parseWeeks("1,3,5,7"))
        assertEquals(setOf(2), ZfKbParser.parseWeeks("2"))
        assertEquals(emptySet<Int>(), ZfKbParser.parseWeeks(""))
    }
}
