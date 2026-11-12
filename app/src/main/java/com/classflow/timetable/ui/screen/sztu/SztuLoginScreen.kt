package com.classflow.timetable.ui.screen.sztu

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classflow.timetable.domain.model.ImportMode
import com.classflow.timetable.domain.model.OwnerKey
import com.classflow.timetable.domain.model.dayOfWeekLabel
import com.classflow.timetable.domain.usecase.describeWeeks
import com.classflow.timetable.domain.usecase.slotsLabelOf
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.GlassPalette
import com.classflow.timetable.ui.theme.LocalGlassPalette
import com.classflow.timetable.ui.theme.glassSurface

/**
 * 教务系统登录 + 导入。
 *
 * 表单是 App 自己的：学号、密码、验证码都在本机输入，
 * 验证码图片由学校接口原样返回（App 不做任何识别或绕过）。
 * [forPartner] = true 时这一页是给「情侣课表」导入另一个人的课表用：
 * 课表写进另一个归属，密码不落盘，也不会动到自己的学期/节次设置。
 */
@Composable
fun SztuLoginScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    forPartner: Boolean = false,
    viewModel: SztuLoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalGlassPalette.current

    LaunchedEffect(forPartner) {
        viewModel.start(if (forPartner) OwnerKey.PARTNER else OwnerKey.SELF)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        HeaderRow(palette = palette, forPartner = forPartner, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                SztuUiState.Loading -> LoadingCard(palette)

                is SztuUiState.NeedInput -> CredentialsCard(
                    state = s,
                    palette = palette,
                    onUsername = viewModel::onUsernameChange,
                    onPassword = viewModel::onPasswordChange,
                    onCaptcha = viewModel::onCaptchaChange,
                    onRemember = viewModel::onRememberPasswordChange,
                    onRefreshCaptcha = viewModel::refreshCaptcha,
                    onLogin = viewModel::login,
                )

                SztuUiState.Working -> WorkingCard(palette)

                is SztuUiState.Preview -> PreviewCard(
                    state = s,
                    palette = palette,
                    onApply = viewModel::apply,
                    onRelogin = viewModel::relogin,
                )

                is SztuUiState.Done -> DoneCard(
                    state = s,
                    palette = palette,
                    onDone = onDone,
                )

                is SztuUiState.Failed -> FailedCard(
                    state = s,
                    palette = palette,
                    onRetry = viewModel::relogin,
                )
            }
        }
    }
}

@Composable
private fun HeaderRow(palette: GlassPalette, forPartner: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .glassSurface(palette, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "返回",
                tint = palette.textPrimary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                if (forPartner) "导入 TA 的课表" else "从教务系统同步",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
            )
            Text(
                if (forPartner) "深圳技术大学 · 用 TA 的账号登录，课表单独存放" else "深圳技术大学 · 账号密码 + 验证码",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textTertiary,
            )
        }
    }
}

@Composable
private fun LoadingCard(palette: GlassPalette) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
            Text("正在准备登录…", color = palette.textSecondary)
        }
    }
}

@Composable
private fun WorkingCard(palette: GlassPalette) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
            Text("正在登录 / 抓取课表…", color = palette.textSecondary)
        }
    }
}

@Composable
private fun CredentialsCard(
    state: SztuUiState.NeedInput,
    palette: GlassPalette,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onCaptcha: (String) -> Unit,
    onRemember: (Boolean) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onLogin: () -> Unit,
) {
    val form = state.form
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.forPartner) {
                Text(
                    "用对方的学号和密码登录一次，就能把 TA 的课表导进来（只读，不会改动你的课表和学期设置）。",
                    color = palette.textTertiary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }

            OutlinedTextField(
                value = form.username,
                onValueChange = onUsername,
                label = { Text("学号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.password,
                onValueChange = onPassword,
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = form.captcha,
                    onValueChange = onCaptcha,
                    label = { Text("验证码") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                CaptchaBox(
                    palette = palette,
                    jpeg = form.captchaJpeg,
                    loading = form.captchaLoading,
                    onClick = onRefreshCaptcha,
                )
            }

            Text(
                "看不清？点右边图片换一张",
                color = palette.textTertiary,
                fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onRefreshCaptcha),
            )

            // TA 的密码永远不保存，所以这个开关只在同步自己的课表时出现
            if (!state.forPartner) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = form.rememberPassword,
                        onCheckedChange = onRemember,
                        colors = CheckboxDefaults.colors(checkedColor = palette.accent),
                    )
                    Text(
                        "记住密码（加密存在本机，方便下次刷新课表）",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            if (!state.error.isNullOrBlank()) {
                Text(state.error, color = palette.danger, fontSize = 12.sp)
            }

            Spacer(Modifier.height(2.dp))
            GlassPrimaryButton(
                text = if (state.forPartner) "登录并读取 TA 的课表" else "登录并读取课表",
                palette = palette,
                enabled = form.username.isNotBlank() &&
                    form.password.isNotBlank() &&
                    form.captcha.isNotBlank() &&
                    !form.captchaLoading,
                onClick = onLogin,
            )
        }
    }
}

/** 验证码图片：字节来自学校接口，原样显示；拿不到时给一个可点的占位。 */
@Composable
private fun CaptchaBox(
    palette: GlassPalette,
    jpeg: ByteArray?,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(width = 108.dp, height = 52.dp)
            .clip(shape)
            .background(palette.glassFillTop)
            .border(1.dp, palette.glassBorderTop, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = palette.accent, strokeWidth = 2.dp)
        } else if (jpeg != null && jpeg.isNotEmpty()) {
            val bitmap = remember(jpeg) {
                runCatching { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) }.getOrNull()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "验证码，点击换一张",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("点击刷新", color = palette.textTertiary, fontSize = 11.sp)
            }
        } else {
            Text("点击获取验证码", color = palette.textTertiary, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun GlassPrimaryButton(
    text: String,
    palette: GlassPalette,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.accent.copy(alpha = if (enabled) 1f else 0.4f),
                        if (palette.isDark) Color(0xFF6E5BD8) else Color(0xFF7A6BFF),
                    ),
                ),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PreviewCard(
    state: SztuUiState.Preview,
    palette: GlassPalette,
    onApply: (ImportMode) -> Unit,
    onRelogin: () -> Unit,
) {
    var mode by remember { mutableStateOf(ImportMode.OVERWRITE) }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                state.semesterName,
                color = palette.textPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "抓到 ${state.preview.courses.size} 门课 / ${state.preview.sessionCount} 个时段",
                color = palette.textSecondary,
                fontSize = 13.sp,
            )

            state.preview.courses.take(30).forEach { course ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(palette.accent),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            course.name,
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        course.sessions.joinToString("；") {
                            "${dayOfWeekLabel(it.dayOfWeek)} ${slotsLabelOf(it.startSlot, it.endSlot)} ${describeWeeks(it.weeks)}"
                        },
                        color = palette.textTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 15.dp),
                    )
                }
            }

            if (state.preview.courses.size > 30) {
                Text(
                    "……还有 ${state.preview.courses.size - 30} 门",
                    color = palette.textTertiary,
                    fontSize = 11.sp,
                )
            }

            if (state.preview.conflicts.isNotEmpty()) {
                Text(
                    "发现 ${state.preview.conflicts.size} 处冲突（仍然可以导入）",
                    color = palette.danger,
                    fontSize = 12.sp,
                )
                state.preview.conflicts.take(5).forEach {
                    Text("· ${it.courseName}：${it.detail}", color = palette.textTertiary, fontSize = 11.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("覆盖已有", mode == ImportMode.OVERWRITE, palette) { mode = ImportMode.OVERWRITE }
                ModeChip("只补充", mode == ImportMode.MERGE, palette) { mode = ImportMode.MERGE }
            }

            GlassPrimaryButton(
                text = if (state.forPartner) "写入 TA 的课表" else "写入我的课表",
                palette = palette,
                onClick = { onApply(mode) },
            )
            Text(
                "换个账号重新登录",
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onRelogin),
            )
        }
    }
}

@Composable
private fun ModeChip(
    text: String,
    selected: Boolean,
    palette: GlassPalette,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) palette.accentSoft else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) palette.accent.copy(alpha = 0.55f) else palette.glassBorderBottom,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            color = if (selected) palette.accent else palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DoneCard(
    state: SztuUiState.Done,
    palette: GlassPalette,
    onDone: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("导入完成", color = palette.textPrimary, style = MaterialTheme.typography.headlineSmall)
            Text(
                buildString {
                    append("新增 ${state.outcome.insertedCourses} 门课 / ")
                    append("${state.outcome.insertedSessions} 个时段")
                    if (state.outcome.updatedCourses > 0) {
                        append("，更新 ${state.outcome.updatedCourses} 门同名课")
                    }
                    if (state.outcome.repairedSessions > 0) {
                        append("（其中 ${state.outcome.repairedSessions} 个时段补上了教室）")
                    }
                },
                color = palette.textSecondary,
                fontSize = 13.sp,
            )
            Text(
                if (state.forPartner) {
                    "TA 的课表已经存好，回到课表按一下左上角「我 / TA」就能看。"
                } else {
                    "节次时间已按学校（15 节）同步，开学日期取自教学周历。"
                },
                color = palette.textTertiary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(4.dp))
            GlassPrimaryButton(
                text = if (state.forPartner) "去看看 TA 的课表" else "去看看课表",
                palette = palette,
                onClick = onDone,
            )
        }
    }
}

@Composable
private fun FailedCard(
    state: SztuUiState.Failed,
    palette: GlassPalette,
    onRetry: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("没成功", color = palette.danger, style = MaterialTheme.typography.titleMedium)
            Text(state.message, color = palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            GlassPrimaryButton("重新登录", palette, onClick = onRetry)
        }
    }
}
