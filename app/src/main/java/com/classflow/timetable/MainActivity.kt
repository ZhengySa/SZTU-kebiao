package com.classflow.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.classflow.timetable.domain.model.AppSettings
import com.classflow.timetable.domain.model.ThemeMode
import com.classflow.timetable.domain.repository.SettingsRepository
import com.classflow.timetable.ui.navigation.ClassFlowNavHost
import com.classflow.timetable.ui.theme.ClassFlowTheme
import com.classflow.timetable.widget.WidgetRefreshScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            ClassFlowTheme(darkTheme = darkTheme) {
                // 透明容器：真正的底色由 ClassFlowNavHost 里的 LiquidBackdrop 负责，
                // 否则 Surface 会盖住渐变，玻璃卡片就变成"贴在纯色上"，没有层次。
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                ) {
                    ClassFlowNavHost()
                }
            }
        }
    }

    /**
     * 每次回到前台都把小组件拉一遍。
     *
     * 这是小组件「跨天」的第三层保险：不管厂商后台策略怎么杀、
     * 闹钟和系统轮询有没有被推迟，只要用户打开过 App，桌面上的日期就一定是今天的。
     * 代价只是一次 Room 查询 + 一次 RemoteViews 更新。
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            WidgetRefreshScheduler.ensureScheduled(this@MainActivity)
            WidgetRefreshScheduler.refreshNow(this@MainActivity)
        }
    }
}
