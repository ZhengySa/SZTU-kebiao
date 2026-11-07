package com.classflow.timetable

import com.classflow.timetable.core.time.WeekMath
import com.classflow.timetable.ui.screen.today.TodayUiState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * 今日页「一天一页」的页码换算。
 *
 * 这群测试是给一个具体 bug 钉的钉子：日滑动曾经把页数写死成 7 页（周一到周日），
 * 于是第 9 周周日再往后划就撞到 Pager 尽头，永远到不了第 10 周。
 * 现在页码 = 整学期的第几天，周日 +1 必须是下周一，一天都不能断。
 */
class TodayPageMappingTest {

    private val start = LocalDate.of(2026, 9, 7) // 周一，第 1 周第一天

    /** 跟 ViewModel 一样从日期算出当前周，避免测试自己瞎写周次。 */
    private fun state(today: LocalDate = LocalDate.of(2026, 10, 28)): TodayUiState {
        val totalWeeks = 20
        return TodayUiState(
            loading = false,
            hasSemester = true,
            currentWeek = (WeekMath.weekOf(start, today)).coerceIn(1, totalWeeks),
            totalWeeks = totalWeeks,
            semesterStart = start,
            today = today,
        )
    }

    @Test
    fun `page maps to week and weekday`() {
        val s = state()
        assertThat(s.totalPages).isEqualTo(140)
        assertThat(s.weekOfPage(0)).isEqualTo(1)
        assertThat(s.dayOfPage(0)).isEqualTo(1)
        // 起点 9/7 是第 1 周周一，所以第 9 周周一是 9/7 + 56 天 = 11/2，周日是 11/8
        assertThat(s.weekOfPage(62)).isEqualTo(9)
        assertThat(s.dayOfPage(62)).isEqualTo(7)
        assertThat(s.dateOfPage(62)).isEqualTo(LocalDate.of(2026, 11, 8))
    }

    @Test
    fun `sunday plus one page is next monday`() {
        val s = state()
        val sunday = 62
        assertThat(s.dateOfPage(sunday)).isEqualTo(LocalDate.of(2026, 11, 8))
        // 这就是「第 9 周划到周日再往后划」——必须落在第 10 周周一
        assertThat(s.weekOfPage(sunday + 1)).isEqualTo(10)
        assertThat(s.dayOfPage(sunday + 1)).isEqualTo(1)
        assertThat(s.dateOfPage(sunday + 1)).isEqualTo(LocalDate.of(2026, 11, 9))
    }

    @Test
    fun `weekday going backwards across week boundary`() {
        val s = state()
        val monday = 63
        assertThat(s.weekOfPage(monday - 1)).isEqualTo(9)
        assertThat(s.dayOfPage(monday - 1)).isEqualTo(7)
    }

    @Test
    fun `every day in the semester maps to a consecutive date`() {
        val s = state()
        // 连成一条：第 n 页的日期必须正好是开学日 + n 天
        for (page in 0 until s.totalPages) {
            assertThat(s.dateOfPage(page)).isEqualTo(start.plusDays(page.toLong()))
        }
    }

    @Test
    fun `today page points at today`() {
        // 2026-10-28 是周三
        val s = state(LocalDate.of(2026, 10, 28))
        assertThat(s.dateOfPage(s.todayPage)).isEqualTo(LocalDate.of(2026, 10, 28))
        assertThat(s.dayOfPage(s.todayPage)).isEqualTo(3)
    }

    @Test
    fun `today page stays inside range after term`() {
        // 结课后 currentWeek 被夹到 20，todayPage 不能越界
        val s = state(LocalDate.of(2027, 6, 1)).copy(currentWeek = 20)
        assertThat(s.todayPage).isAtLeast(0)
        assertThat(s.todayPage).isAtMost(s.totalPages - 1)
    }
}
