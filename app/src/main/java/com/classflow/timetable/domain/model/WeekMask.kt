package com.classflow.timetable.domain.model

/**
 * 周次规则。UI 上就是 4 个选项：全部周 / 单周 / 双周 / 指定周次。
 * 真实的过滤永远走 [WeekMask]，规则只用于回显和重新计算。
 */
enum class WeekRuleType {
    ALL, ODD, EVEN, CUSTOM;

    companion object {
        fun safeOf(raw: String?): WeekRuleType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ALL
    }
}

/**
 * 周次位图：bit0 = 第 1 周，bit(i) = 第 i+1 周。
 *
 * 用一个 Long 就能表达 60 周，判断「本周上不上」是 O(1) 位运算，
 * 判断「两个时段周次是否有交集」也是 O(1)，冲突检测因此不需要任何嵌套循环。
 */
@JvmInline
value class WeekMask(val bits: Long) {

    operator fun contains(week: Int): Boolean =
        week in 1..MAX_WEEKS && ((bits shr (week - 1)) and 1L) == 1L

    fun toWeeks(): List<Int> = (1..MAX_WEEKS).filter { it in this }

    fun intersects(other: WeekMask): Boolean = (bits and other.bits) != 0L

    fun isEmpty(): Boolean = bits == 0L

    fun count(): Int = java.lang.Long.bitCount(bits)

    /**
     * 人类可读描述，例如「每周」「单周」「双周」「1-8,10,12-16 周」。
     * 用于课程详情页、冲突提示、小组件副标题。
     */
    fun describe(totalWeeks: Int): String {
        if (bits == 0L) return "无周次"
        val safeTotal = totalWeeks.coerceIn(1, MAX_WEEKS)
        val weeks = toWeeks().filter { it <= safeTotal }
        if (weeks.isEmpty()) return "无周次"
        if (weeks.size == safeTotal) return "每周"

        val oddWeeks = (1..safeTotal).filter { it % 2 == 1 }
        val evenWeeks = (1..safeTotal).filter { it % 2 == 0 }
        if (weeks == oddWeeks) return "单周"
        if (weeks == evenWeeks) return "双周"

        return compress(weeks) + " 周"
    }

    private fun compress(weeks: List<Int>): String {
        val sb = StringBuilder()
        var rangeStart = weeks.first()
        var prev = rangeStart

        fun flush() {
            if (sb.isNotEmpty()) sb.append(',')
            if (rangeStart == prev) sb.append(rangeStart) else sb.append(rangeStart).append('-').append(prev)
        }

        for (i in 1 until weeks.size) {
            val w = weeks[i]
            if (w == prev + 1) {
                prev = w
            } else {
                flush()
                rangeStart = w
                prev = w
            }
        }
        flush()
        return sb.toString()
    }

    companion object {
        const val MAX_WEEKS = 60

        val EMPTY = WeekMask(0L)

        fun of(rule: WeekRuleType, explicitWeeks: Set<Int>, totalWeeks: Int): WeekMask {
            val safeTotal = totalWeeks.coerceIn(1, MAX_WEEKS)
            var bits = 0L
            for (week in 1..safeTotal) {
                val hit = when (rule) {
                    WeekRuleType.ALL -> true
                    WeekRuleType.ODD -> week % 2 == 1
                    WeekRuleType.EVEN -> week % 2 == 0
                    WeekRuleType.CUSTOM -> week in explicitWeeks
                }
                if (hit) bits = bits or (1L shl (week - 1))
            }
            return WeekMask(bits)
        }

        fun fromWeeks(weeks: Collection<Int>): WeekMask {
            var bits = 0L
            weeks.filter { it in 1..MAX_WEEKS }.forEach { bits = bits or (1L shl (it - 1)) }
            return WeekMask(bits)
        }
    }
}
