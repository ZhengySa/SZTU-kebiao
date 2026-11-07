package com.classflow.timetable.domain.model

import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 今日运气的档位。
 *
 * 60 分以上统一算「顺」，只是程度分了几档 —— 全是好话，但没有「你一定会成功」这种话。
 */
enum class LuckTier(val label: String) {
    EXCELLENT("欧气爆棚"),
    GREAT("挺顺"),
    LUCKY("幸运"),
    PLAIN("一般般"),
    MEH("差点意思"),
    LOW("低调一天"),
}

/** 某一天、某台设备的运气。 */
data class Luck(
    val value: Int,
    val tier: LuckTier,
    val line: String,
)

/**
 * 当日运气值：0~100 的随机数，但**同一天同一台设备永远是同一个数**。
 *
 * 所以它不是「摇一摇」，是「今天几号、这台手机」决定的一个数：
 * 退出去再进来还是这个分，第二天凌晨换一个。这样它才像"今天的运气"，
 * 而不是一个可以一直点、直到点出高分的老虎机。
 *
 * 分布不是均匀的：`u^0.62` 把结果往高分那一侧压了一点 ——
 * 均值约 62，60 分以上的概率约 56%，也就是"好日子稍微多一点"，
 * 但也会真的掉到 30 以下，不至于每天都是 70 分往上、看久了就假。
 *
 * 纯函数，[salt] 由调用方给（本机随机盐），方便测试。
 */
object LuckMeter {

    fun of(date: LocalDate, salt: Long): Luck {
        val seed = seedOf(date, salt)
        val value = valueOf(seed)
        val tier = tierOf(value)
        return Luck(value = value, tier = tier, line = lineOf(tier, seed))
    }

    fun valueOf(seed: Long): Int {
        val u = Random(seed).nextDouble()
        return (100.0 * u.pow(0.62)).roundToInt().coerceIn(0, 100)
    }

    fun tierOf(value: Int): LuckTier = when {
        value >= 90 -> LuckTier.EXCELLENT
        value >= 76 -> LuckTier.GREAT
        value >= 60 -> LuckTier.LUCKY
        value >= 45 -> LuckTier.PLAIN
        value >= 30 -> LuckTier.MEH
        else -> LuckTier.LOW
    }

    fun lineOf(tier: LuckTier, seed: Long): String {
        val lines = when (tier) {
            LuckTier.EXCELLENT -> listOf(
                "手气有点不正常，抽卡可以冲。",
                "想干嘛就去干，今天拦你的东西比较少。",
                "这种日子一个月没几天，别都花在刷手机上。",
            )
            LuckTier.GREAT -> listOf(
                "脑子清楚，适合把那件一直拖着的事办了。",
                "开口比较容易说服人，该提的要求今天提。",
                "重要的事排在上午，往后会松一点。",
            )
            LuckTier.LUCKY -> listOf(
                "小的事都顺，大的别硬来。",
                "够用了。想约人就约，成了算赚，不成也不亏。",
                "运气在线，但别赌，尤其别赌钱。",
            )
            LuckTier.PLAIN -> listOf(
                "普通的一天，按计划走就行。",
                "没什么风浪，适合清一清攒着的杂事。",
                "别临时改主意，原计划是最稳的那个。",
            )
            LuckTier.MEH -> listOf(
                "有点闷。文件记得存，伞记得带。",
                "今天容易走神，要做的事先写下来。",
                "大决定放一放，明天再想。",
            )
            LuckTier.LOW -> listOf(
                "就……早点睡吧。",
                "今天少说话，多喝水，别签任何东西。",
                "低谷也就今天，明天重新抽。",
            )
        }
        val index = (seed.mod(lines.size.toLong())).toInt()
        return lines[index]
    }

    /** 同一天 + 同一台设备 → 同一个种子。乘上不相干的大质数，避免相邻日期出现相近的序列。 */
    private fun seedOf(date: LocalDate, salt: Long): Long =
        date.toEpochDay() * 1_000_003L xor (salt * -0x61c8864680b583ebL)
}
