package com.classflow.timetable.ui

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classflow.timetable.data.local.BackdropStore
import com.classflow.timetable.domain.model.AppSettings
import com.classflow.timetable.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 外壳（NavHost 那一层）需要的东西：主题设置 + 自定义背景位图。
 *
 * 背景图放在这里而不是每个页面各自加载：它是**整棵 UI 树的最底层**，
 * 一个 Activity 生命周期内只该解码一次。放页面里会因为 Tab 切换反复解码 1600px 的图。
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    private val _backdrop = MutableStateFlow<ImageBitmap?>(null)
    val backdrop: StateFlow<ImageBitmap?> = _backdrop.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { current ->
                val file = current.backdropFile
                if (file == null) {
                    _backdrop.value = null
                    return@collect
                }
                val bitmap = BackdropStore.load(context)
                _backdrop.value = bitmap
                // 设置里记着有背景、文件却没了（用户清了应用数据、或手动删了文件）：
                // 把标记也清掉，否则每次启动都白读一遍磁盘。
                if (bitmap == null) settingsRepository.setBackdropFile(null)
            }
        }
    }
}
