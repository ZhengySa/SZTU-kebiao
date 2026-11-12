package com.classflow.timetable.domain.usecase

import com.classflow.timetable.data.local.SztuAccountStore
import com.classflow.timetable.data.remote.sztu.SessionExpiredException
import com.classflow.timetable.domain.model.ImportMode
import com.classflow.timetable.domain.model.OwnerKey
import com.classflow.timetable.domain.repository.TimetableRepository
import java.io.IOException
import javax.inject.Inject

/** 自动补教室的结果。 */
sealed interface RoomRepairResult {
    /** 没有要补的东西（手动建的课不算）。 */
    data object NotNeeded : RoomRepairResult

    /** 从来没连过教务，没有可用来补的来源。 */
    data object NoAccount : RoomRepairResult

    /**
     * 会话过期了，只能请用户再登录一次。
     *
     * 不做静默登录：教务登录要验证码，那玩意儿机器读不了，
     * 硬要自动化的结果只会是「莫名其妙失败」。所以这里老实说清楚。
     */
    data object NeedsLogin : RoomRepairResult

    /** 补好了。[repairedSessions] 是这次真正补上教室的时段数。 */
    data class Repaired(val repairedSessions: Int, val updatedCourses: Int) : RoomRepairResult

    data class Failed(val message: String) : RoomRepairResult
}

/**
 * 把老数据缺失的教室，用一次静默的教务同步补回来。
 *
 * 为什么需要这个东西：1.0.2 之前，一门课的教室是拼成一串存在**课程级**的
 * （`C-5-215 / D-3-321`），时段本身没有地点。1.0.2 把拼接串清掉了（那串确实是错的），
 * 但老数据的时段是空的 —— 于是用户看到满屏「地点待填」，除了自己一个个手输没有别的路。
 * 这是产品层面的失败：清理脏数据的时候没给出替代来源。
 *
 * 现在：App 启动时自己跑一次，用本地保存的会话重新读一遍教务，逐时段填回去。
 * 用户什么都不用点。
 */
class RoomRepairUseCase @Inject constructor(
    private val repository: TimetableRepository,
    private val accountStore: SztuAccountStore,
    private val sync: SztuSyncUseCase,
    private val applySztuImport: ApplySztuImportUseCase,
) {

    private companion object {
        /** 补不上就别反复试：12 小时内只碰一次教务。 */
        const val RETRY_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }

    suspend operator fun invoke(): RoomRepairResult {
        val semester = repository.getActiveSemester() ?: return RoomRepairResult.NotNeeded

        val missing = repository.countSessionsMissingLocation(semester.id, OwnerKey.SELF)
        if (missing == 0) return RoomRepairResult.NotNeeded

        if (accountStore.currentUsername().isBlank()) return RoomRepairResult.NoAccount

        // 有些课的教室教务自己就没写，补也补不出来。那种情况下别每次启动都去敲一遍教务，
        // 半天试一次就够了；一旦真的补上了，count 变 0 这里会直接返回 NotNeeded。
        val lastAttempt = accountStore.lastRoomRepairAt()
        if (lastAttempt > 0 && System.currentTimeMillis() - lastAttempt < RETRY_INTERVAL_MS) {
            return RoomRepairResult.NotNeeded
        }
        accountStore.markRoomRepairAttempt()

        return runCatching {
            val client = sync.restoreClient(OwnerKey.SELF)
            // 直接抓课表：会话过期会抛 SessionExpiredException，比先探一次会话少跑一趟
            val snapshot = sync.snapshot(client, null, OwnerKey.SELF)
            val preview = sync.preview(snapshot)
            if (preview.isEmpty) return RoomRepairResult.Failed("教务返回的课表是空的，先没动本地数据")

            // MERGE 现在会就地补同名课程的时段教室，不会覆盖用户改过的地点和周次
            val outcome = applySztuImport(snapshot, preview, ImportMode.MERGE, OwnerKey.SELF)
            // 顺手把刷新后的会话存回去，下次不用再走一遍登录
            sync.persistSession(client, snapshot, OwnerKey.SELF)

            RoomRepairResult.Repaired(
                repairedSessions = outcome.repairedSessions,
                updatedCourses = outcome.updatedCourses,
            )
        }.getOrElse { error ->
            when (error) {
                is SessionExpiredException -> RoomRepairResult.NeedsLogin
                // 没网/超时不该报「登录过期」，那会把用户指到错误的方向
                is IOException -> RoomRepairResult.Failed("没连上网，等有网再补")
                else -> RoomRepairResult.Failed(error.message ?: "同步失败")
            }
        }
    }
}
