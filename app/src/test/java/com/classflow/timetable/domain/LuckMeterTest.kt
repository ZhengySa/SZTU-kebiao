package com.classflow.timetable.domain

import com.classflow.timetable.domain.model.LuckMeter
import com.classflow.timetable.domain.model.LuckTier
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * 今日运气的性质测试。
 *
 * 重点不是"随机得像不像"，而是三条必须成立的规则：
 * 同一天不能变、分数不能越界、好日子要比坏日子多一点但不能满天飞。
 */
class LuckMeterTest {

    private val day = LocalDate.of(2026, 10, 28)

    @Test
    fun `同一天同一台设备结果完全一致`() {
        val first = LuckMeter.of(day, 12345L)
        val second = LuckMeter.of(day, 12345L)
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `隔一天结果会变`() {
        val today = LuckMeter.of(day, 12345L)
        val tomorrow = LuckMeter.of(day.plusDays(1), 12345L)
        // 分数或那句话至少有一个不一样；两天完全相同的概率极低，撞上了说明种子算错了
        assertThat(tomorrow).isNotEqualTo(today)
    }

    @Test
    fun `不同设备同一天会抽到不同的数`() {
        val values = (1L..30L).map { LuckMeter.of(day, it).value }.toSet()
        assertThat(values.size).isAtLeast(10)
    }

    @Test
    fun `三百分里的每一天分数都在 0 到 100 之间`() {
        var date = LocalDate.of(2025, 1, 1)
        repeat(300) {
            assertThat(LuckMeter.of(date, 7L).value).isIn(0..100)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `六十分以上比以下多一点`() {
        var date = LocalDate.of(2024, 1, 1)
        val values = (0 until 2000).map {
            val v = LuckMeter.of(date, 99L).value
            date = date.plusDays(1)
            v
        }
        val luckyShare = values.count { it >= 60 }.toDouble() / values.size
        // 约 0.56：好运占多数，但不至于"天天都顺"
        assertThat(luckyShare).isGreaterThan(0.50)
        assertThat(luckyShare).isLessThan(0.64)

        val average = values.average()
        assertThat(average).isGreaterThan(58.0)
        assertThat(average).isLessThan(66.0)
    }

    @Test
    fun `档位边界按六十分算幸运`() {
        assertThat(LuckMeter.tierOf(100)).isEqualTo(LuckTier.EXCELLENT)
        assertThat(LuckMeter.tierOf(90)).isEqualTo(LuckTier.EXCELLENT)
        assertThat(LuckMeter.tierOf(89)).isEqualTo(LuckTier.GREAT)
        assertThat(LuckMeter.tierOf(76)).isEqualTo(LuckTier.GREAT)
        assertThat(LuckMeter.tierOf(75)).isEqualTo(LuckTier.LUCKY)
        assertThat(LuckMeter.tierOf(60)).isEqualTo(LuckTier.LUCKY)
        assertThat(LuckMeter.tierOf(59)).isEqualTo(LuckTier.PLAIN)
        assertThat(LuckMeter.tierOf(45)).isEqualTo(LuckTier.PLAIN)
        assertThat(LuckMeter.tierOf(44)).isEqualTo(LuckTier.MEH)
        assertThat(LuckMeter.tierOf(30)).isEqualTo(LuckTier.MEH)
        assertThat(LuckMeter.tierOf(29)).isEqualTo(LuckTier.LOW)
        assertThat(LuckMeter.tierOf(0)).isEqualTo(LuckTier.LOW)
    }

    @Test
    fun `每个档位都有话说`() {
        LuckTier.entries.forEach { tier ->
            val line = LuckMeter.lineOf(tier, 1L)
            assertThat(line).isNotEmpty()
            // 一句话，不是一段；太长在卡片里会换三四行，看着像说明书
            assertThat(line.length).isAtMost(30)
        }
    }
}
