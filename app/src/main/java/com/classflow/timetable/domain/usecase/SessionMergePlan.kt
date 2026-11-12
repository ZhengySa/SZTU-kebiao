package com.classflow.timetable.domain.usecase

import com.classflow.timetable.domain.imports.ImportedSession

/**
 * 合并导入时，一门同名课程的已有时段该怎么改。
 *
 * 单独抽出来是因为这里的规则很容易写错，而写错的代价很高：
 * 1.0.2 之前「同名就整门跳过」，导致教室只存在课程级的那些课**永远修不好**，
 * 用户重新同步多少次都是「地点待填」，只能自己一个个手输。
 *
 * 三条规则，都很保守：
 * - 只补空地点，不覆盖用户手填过的地点（他可能就是为了备注「换到别的楼」才改的）
 * - 不删已有时段（可能是用户自己加的）
 * - 教务新出现的时段才插入
 */
internal object SessionMergePlan {

    /** 已有时段里参与比对的最小信息（不依赖数据库实体，好单测）。 */
    data class Existing(
        val id: Long,
        val dayOfWeek: Int,
        val startSlot: Int,
        val endSlot: Int,
        val location: String?,
    )

    /** 需要把某个已有时间段的教室补成 [location]。 */
    data class Patch(val sessionId: Long, val location: String)

    data class Result(
        val patches: List<Patch>,
        val inserts: List<ImportedSession>,
    ) {
        val isEmpty: Boolean get() = patches.isEmpty() && inserts.isEmpty()
    }

    fun plan(
        existing: List<Existing>,
        incoming: List<ImportedSession>,
    ): Result {
        val bySlot = existing.associateBy { SessionKey(it.dayOfWeek, it.startSlot, it.endSlot) }
        val patches = mutableListOf<Patch>()
        val inserts = mutableListOf<ImportedSession>()

        incoming.forEach { session ->
            val match = bySlot[SessionKey(session.dayOfWeek, session.startSlot, session.endSlot)]
            val room = session.location?.takeIf { it.isNotBlank() }
            when {
                match == null -> inserts += session
                room != null && match.location.isNullOrBlank() -> patches += Patch(match.id, room)
                else -> Unit
            }
        }
        return Result(patches = patches, inserts = inserts)
    }

    private data class SessionKey(val day: Int, val start: Int, val end: Int)
}
