package com.classflow.timetable

import com.classflow.timetable.domain.model.WeekMask
import com.classflow.timetable.domain.model.WeekRuleType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WeekMaskTest {

    @Test
    fun `odd rule produces every other week`() {
        val mask = WeekMask.of(WeekRuleType.ODD, emptySet(), 16)
        assertThat(mask.toWeeks()).isEqualTo(listOf(1, 3, 5, 7, 9, 11, 13, 15))
    }

    @Test
    fun `even rule produces even weeks`() {
        val mask = WeekMask.of(WeekRuleType.EVEN, emptySet(), 16)
        assertThat(mask.toWeeks()).isEqualTo(listOf(2, 4, 6, 8, 10, 12, 14, 16))
    }

    @Test
    fun `custom rule keeps only selected weeks`() {
        val mask = WeekMask.of(WeekRuleType.CUSTOM, setOf(1, 2, 3, 9, 17), 20)
        assertThat(mask.toWeeks()).isEqualTo(listOf(1, 2, 3, 9, 17))
    }

    @Test
    fun `all rule fills the whole semester`() {
        val mask = WeekMask.of(WeekRuleType.ALL, emptySet(), 20)
        assertThat(mask.count()).isEqualTo(20)
        assertThat(1 in mask).isTrue()
        assertThat(20 in mask).isTrue()
    }

    @Test
    fun `out of range week is not contained and does not crash`() {
        val mask = WeekMask.of(WeekRuleType.ALL, emptySet(), 20)
        assertThat(0 in mask).isFalse()
        assertThat(21 in mask).isFalse()
        assertThat(WeekMask.MAX_WEEKS + 1 in mask).isFalse()
    }

    @Test
    fun `intersects detects shared weeks`() {
        val odd = WeekMask.of(WeekRuleType.ODD, emptySet(), 16)
        val even = WeekMask.of(WeekRuleType.EVEN, emptySet(), 16)
        val all = WeekMask.of(WeekRuleType.ALL, emptySet(), 16)

        assertThat(odd.intersects(even)).isFalse()
        assertThat(odd.intersects(all)).isTrue()
        assertThat(WeekMask.EMPTY.intersects(all)).isFalse()
    }

    @Test
    fun `describe gives human readable text`() {
        assertThat(WeekMask.of(WeekRuleType.ALL, emptySet(), 16).describe(16)).isEqualTo("每周")
        assertThat(WeekMask.of(WeekRuleType.ODD, emptySet(), 16).describe(16)).isEqualTo("单周")
        assertThat(WeekMask.of(WeekRuleType.EVEN, emptySet(), 16).describe(16)).isEqualTo("双周")
        assertThat(WeekMask.of(WeekRuleType.CUSTOM, setOf(1, 2, 3, 4, 6), 16).describe(16))
            .isEqualTo("1-4,6 周")
    }
}
