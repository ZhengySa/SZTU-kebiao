package com.classflow.timetable.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 运气值的"盐"。
 *
 * 只干一件事：让两台手机在同一天抽到不同的数。第一次用到时随机生成一个 Long 存本机，
 * 之后一直复用。它不上传、不参与任何网络请求，也不和账号关联 ——
 * 说白了就是个本地的随机种子，丢了也就是明天的数换一个而已。
 */
@Singleton
class LuckSaltStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("luck", Context.MODE_PRIVATE)

    fun salt(): Long {
        val saved = prefs.getLong(KEY_SALT, 0L)
        if (saved != 0L) return saved
        // nextLong() 有极小概率给 0，而 0 是"还没生成"的标记，换一个
        val fresh = generateSequence { SecureRandom().nextLong() }
            .first { it != 0L }
        prefs.edit().putLong(KEY_SALT, fresh).apply()
        return fresh
    }

    private companion object {
        const val KEY_SALT = "salt"
    }
}
