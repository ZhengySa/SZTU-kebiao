package com.classflow.timetable.ui.screen.backup

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import com.classflow.timetable.ui.theme.GlassScreenHeader
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.classflow.timetable.backup.BackupManager
import com.classflow.timetable.backup.RestoreResult
import com.classflow.timetable.widget.TodayWidgetReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Working : BackupUiState
    data class Message(val text: String, val isError: Boolean = false) : BackupUiState
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun suggestedFileName(): String =
        "课表备份-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".json"

    fun export(target: android.net.Uri) {
        _state.value = BackupUiState.Working
        viewModelScope.launch {
            val result = runCatching {
                val text = backupManager.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(target)?.use { stream ->
                        stream.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入所选文件")
                }
            }
            _state.value = result.fold(
                onSuccess = { BackupUiState.Message("备份已保存") },
                onFailure = { BackupUiState.Message("导出失败：${it.message}", isError = true) },
            )
        }
    }

    fun restore(source: android.net.Uri) {
        _state.value = BackupUiState.Working
        viewModelScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(source)?.use {
                        String(it.readBytes(), Charsets.UTF_8)
                    } ?: error("无法读取所选文件")
                }
                backupManager.restoreFromJson(text)
            }
            _state.value = result.fold(
                onSuccess = { BackupUiState.Message(describe(it)) },
                onFailure = { BackupUiState.Message("恢复失败：${it.message}", isError = true) },
            )
        }
    }

    private fun describe(result: RestoreResult): String =
        "恢复完成：${result.semesterCount} 个学期、${result.courseCount} 门课、${result.sessionCount} 个时段"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.restore(uri)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassScreenHeader(title = "备份与恢复", onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "备份文件是一个普通 JSON，包含学期、节次时间、课程与全部上课时段。文件放在哪、发给谁，完全由你决定。",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
            ) { Text("导出备份文件") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            ) { Text("从备份文件恢复") }

            when (val current = state) {
                BackupUiState.Idle -> Unit
                BackupUiState.Working -> Text("处理中…", style = MaterialTheme.typography.bodyMedium)
                is BackupUiState.Message -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (current.isError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    ),
                ) {
                    Text(current.text, modifier = Modifier.padding(12.dp))
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("注意", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "· 恢复会覆盖当前设备上的全部课表数据，建议先导出一次当前备份\n" +
                            "· 卸载应用会删除本机数据库，卸载前记得导出",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** 桌面小组件引导页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassScreenHeader(title = "桌面小组件", onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("今日课表小组件", style = MaterialTheme.typography.titleMedium)
            Text(
                "桌面会显示今天的课程（课程名、第几节、时间、教室），没课显示「今日无课」。" +
                    "每晚零点后自动切换到新的一天，课程改了也会立刻刷新。",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("怎么添加", style = MaterialTheme.typography.titleSmall)
            Text(
                "1. 回到手机桌面，长按空白处\n" +
                    "2. 选择「小部件 / 小组件」\n" +
                    "3. 找到「课表」，把「今日课表」拖到桌面\n" +
                    "4. 拖边缘可以拉成 4×2 或 4×4，内容会跟着变",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { message = requestPin(context) },
            ) { Text("直接添加到桌面") }

            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            Text("显示规则", style = MaterialTheme.typography.titleSmall)
            Text(
                "· 只显示本周真正要上的课，单双周与指定周次都会正确过滤\n" +
                    "· 桌面太小会自动减少条数，多出来的显示「还有 N 节」\n" +
                    "· 点击整块区域打开 App 的今日页\n" +
                    "· 深色模式跟随系统",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun requestPin(context: Context): String {
    val manager = context.getSystemService(AppWidgetManager::class.java)
        ?: return "这台设备不支持直接添加小组件，请按上面的步骤手动添加"
    if (!manager.isRequestPinAppWidgetSupported) {
        return "当前桌面不支持一键添加，请按上面的步骤手动添加"
    }
    val provider = ComponentName(context, TodayWidgetReceiver::class.java)
    val requested = manager.requestPinAppWidget(provider, null, null)
    return if (requested) "已发送添加请求，请看桌面提示" else "添加失败，请按上面的步骤手动添加"
}
