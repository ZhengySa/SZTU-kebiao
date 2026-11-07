package com.classflow.timetable.domain.model

/**
 * 一份 App 里可能有两条课表：自己的 + 另一个人的（「情侣课表」）。
 *
 * 用 owner_key 区分，而不是给对方单独建学期：
 * 学期决定的是「第几周」，两个人显然在同一周里上课；
 * 真正要分开的只是"这批课属于谁"。
 */
object OwnerKey {
    const val SELF = "self"
    const val PARTNER = "partner"

    fun isSelf(key: String): Boolean = key == SELF
}
