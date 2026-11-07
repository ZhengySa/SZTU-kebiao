package com.classflow.timetable

import com.classflow.timetable.domain.model.ZhongdeFloors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 中德学院楼层数据 / 房间查找的回归测试。
 *
 * 这份数据是从 PDF 文字层直接抄的，抄错一个房号在 UI 上根本看不出来
 * （图还是那张图），所以只能靠断言把「哪些层有哪些房」钉住。
 */
class ZhongdeFloorsTest {

    @Test
    fun `楼层必须是这 13 层，不能有 7 11 14 15 16`() {
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 8, 9, 10, 12, 13, 17, 18),
            ZhongdeFloors.floors,
        )
        // 这几个楼层是别的学院的，混进来会显示一张空白图
        listOf(7, 11, 14, 15, 16).forEach {
            assertFalse("$it F 不该出现", ZhongdeFloors.floors.contains(it))
        }
    }

    @Test
    fun `每层都有图有房且不重复`() {
        ZhongdeFloors.all.forEach { f ->
            assertTrue("${f.floor}F 没有房间", f.rooms.isNotEmpty())
            assertTrue("${f.floor}F 没有摘要", f.summary.isNotBlank())
            val dup = f.rooms.groupBy { it.no }.filter { it.value.size > 1 }.keys
            assertTrue("${f.floor}F 房号重复：$dup", dup.isEmpty())
        }
    }

    @Test
    fun `房号必须落在本层前两位里`() {
        // 规则：房号开头就是楼层号。1F 是 101~116（不是 0101），13F 是 1301~
        // 唯一的例外：5F 的 503 被拆成 503-1 / 503-2，仍然是 503 开头
        ZhongdeFloors.all.forEach { f ->
            f.rooms.forEach { r ->
                assertTrue(
                    "${r.no} 不该在 ${f.floor}F（应以 ${f.floor} 开头）",
                    r.no.startsWith(f.floor.toString()),
                )
            }
        }
    }

    @Test
    fun `搜房间号 - 完全匹配优先，不会把 305 匹配到 1305`() {
        val hits = ZhongdeFloors.find("305")
        assertTrue(hits.isNotEmpty())
        assertEquals(3, hits.first().first.floor)
        assertEquals("305", hits.first().second.no)
        // 「1305」也 endsWith 305，但它只是前缀匹配的候选，不能排在完全匹配前面
        assertFalse(hits.any { it.second.no == "1305" })
    }

    @Test
    fun `搜房间号 - 前缀匹配能查出同号段的房间`() {
        // 13F 以「130」开头的有 1301/1302/1303/1303A/1305~1309 共 9 间；
        // 1310 起就换号段了，不该被算进来
        val hits = ZhongdeFloors.find("130")
        assertEquals(9, hits.size)
        assertTrue(hits.all { it.first.floor == 13 })
        assertFalse(hits.any { it.second.no.startsWith("131") })
    }

    @Test
    fun `搜房间号 - 空串和小写都能处理`() {
        assertTrue(ZhongdeFloors.find("").isEmpty())
        assertEquals(
            ZhongdeFloors.find("103A").map { it.second.no },
            ZhongdeFloors.find("103a").map { it.second.no },
        )
    }

    @Test
    fun `搜房间号 - 查不到就是空`() {
        assertTrue(ZhongdeFloors.find("999").isEmpty())
    }

    @Test
    fun `关键房间都在`() {
        // 这几个是学生最常找的：院长办公室、教务办公室、主实验室
        assertEquals(5, ZhongdeFloors.find("509").single().first.floor)
        assertEquals(5, ZhongdeFloors.find("503-1").single().first.floor)
        assertEquals(1, ZhongdeFloors.find("101").single().first.floor)
        assertTrue(ZhongdeFloors.find("101").single().second.name.contains("工业机器人"))
        // 啤酒实验室是这学院的招牌，别抄丢了
        assertTrue(ZhongdeFloors.of(1).rooms.any { it.name.contains("啤酒") })
    }

    @Test
    fun `房间总数与 PDF 一致`() {
        // PDF 各页「本层房间一览」的条目数相加
        assertEquals(17, ZhongdeFloors.of(1).rooms.size)
        assertEquals(5, ZhongdeFloors.of(2).rooms.size)
        assertEquals(20, ZhongdeFloors.of(3).rooms.size)
        // 4F 是 26 间：408~418 的电学基础实验中心是连续编号，中间 409/410/412/417 也各占一间
        assertEquals(26, ZhongdeFloors.of(4).rooms.size)
        assertEquals(16, ZhongdeFloors.of(5).rooms.size)
        assertEquals(6, ZhongdeFloors.of(6).rooms.size)
        assertEquals(6, ZhongdeFloors.of(8).rooms.size)
        assertEquals(5, ZhongdeFloors.of(9).rooms.size)
        assertEquals(4, ZhongdeFloors.of(10).rooms.size)
        assertEquals(4, ZhongdeFloors.of(12).rooms.size)
        assertEquals(16, ZhongdeFloors.of(13).rooms.size)
        assertEquals(15, ZhongdeFloors.of(17).rooms.size)
        assertEquals(20, ZhongdeFloors.of(18).rooms.size)
    }
}
