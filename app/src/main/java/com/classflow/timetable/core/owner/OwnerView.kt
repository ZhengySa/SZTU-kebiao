package com.classflow.timetable.core.owner

import com.classflow.timetable.domain.model.OwnerKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「现在在看谁的课表」。
 *
 * 全局一个开关：今日页和周课表共用同一个 [owner]，
 * 所以在任一页面按一下切换，另一个页面也跟着切过去，不会出现
 * "课表显示 TA 的、今日显示我的" 这种错位。
 *
 * 纯内存状态，不落盘：App 重启回到「看自己的课表」，避免下次打开以为课表丢了。
 */
@Singleton
class OwnerView @Inject constructor() {

    private val _owner = MutableStateFlow(OwnerKey.SELF)
    val owner: StateFlow<String> = _owner.asStateFlow()

    val isPartner: Boolean get() = _owner.value == OwnerKey.PARTNER

    fun toggle() {
        _owner.value = if (isPartner) OwnerKey.SELF else OwnerKey.PARTNER
    }

    /** 导入完 TA 的课表后直接切过去看。 */
    fun set(owner: String) {
        _owner.value = if (owner == OwnerKey.PARTNER) OwnerKey.PARTNER else OwnerKey.SELF
    }

    fun backToSelf() {
        _owner.value = OwnerKey.SELF
    }
}
