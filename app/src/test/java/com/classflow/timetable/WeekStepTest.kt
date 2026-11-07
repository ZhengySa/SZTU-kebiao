package com.classflow.timetable

import com.classflow.timetable.ui.screen.week.stepPage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 翻周到「目标页」的映射。
 *
 * 这些用例钉住的是 [stepPage] 的边界行为，而不是 Compose 本身：
 * 之前翻周失灵有三个独立原因（Pager 被重建、双向自反馈、拖动累计位移被清零），
 * 其中「基准页取错」这一条最容易回归 —— 只要有人把「传方向」改回「传目标周次」，
 * 连点就会丢事件。这里用「连点 N 次 == 前进 N 周」的形状把它钉死。
 */
class WeekStepTest {

    private val totalWeeks = 20

    @Test
    fun `next from first page goes to second`() {
        assertEquals(1, stepPage(base = 0, dir = 1, totalWeeks = totalWeeks))
    }

    @Test
    fun `prev from second page goes back to first`() {
        assertEquals(0, stepPage(base = 1, dir = -1, totalWeeks = totalWeeks))
    }

    /** 连点 3 次右箭头 = 前进 3 周。每次都用上一次的结果当基准。 */
    @Test
    fun `three consecutive next steps advance three weeks`() {
        var page = 0
        repeat(3) { page = stepPage(page, dir = 1, totalWeeks = totalWeeks) }
        assertEquals(3, page)
    }

    /** 反例：如果每次都拿同一个旧基准算（老 bug 的形状），三次之后仍只前进一周。 */
    @Test
    fun `stale base only advances once which is the old bug`() {
        val staleBase = 0
        val results = List(3) { stepPage(staleBase, dir = 1, totalWeeks = totalWeeks) }
        assertEquals(listOf(1, 1, 1), results)
    }

    @Test
    fun `prev on first page stays on first page`() {
        assertEquals(0, stepPage(base = 0, dir = -1, totalWeeks = totalWeeks))
    }

    @Test
    fun `next on last page stays on last page`() {
        assertEquals(totalWeeks - 1, stepPage(base = totalWeeks - 1, dir = 1, totalWeeks = totalWeeks))
    }

    /** 学期只有 1 周时，两个方向都被夹到第 0 页，不会算出 -1 或 1。 */
    @Test
    fun `single week semester clamps both directions`() {
        assertEquals(0, stepPage(base = 0, dir = 1, totalWeeks = 1))
        assertEquals(0, stepPage(base = 0, dir = -1, totalWeeks = 1))
    }

    /** 从中间任意一页出发，正反各走一步应当回到原页。 */
    @Test
    fun `forward then backward returns to the same page`() {
        val start = 9
        val forward = stepPage(start, dir = 1, totalWeeks = totalWeeks)
        val back = stepPage(forward, dir = -1, totalWeeks = totalWeeks)
        assertEquals(start, back)
    }
}
