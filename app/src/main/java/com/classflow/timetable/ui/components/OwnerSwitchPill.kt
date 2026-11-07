package com.classflow.timetable.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classflow.timetable.domain.model.OwnerKey
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.GlassPalette

/**
 * 「我 / TA」切换按钮。
 *
 * 一按就换课表：自己 ↔ 对方，全局同步（今日页与周课表一起切）。
 * 长按打开管理（重新导入 / 清除 TA 的课表），不额外占界面空间。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OwnerSwitchPill(
    palette: GlassPalette,
    owner: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isPartner = owner == OwnerKey.PARTNER
    val shape = RoundedCornerShape(50)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (isPartner) {
                    Brush.horizontalGradient(
                        listOf(palette.accent, palette.accent.copy(alpha = 0.78f)),
                    )
                } else {
                    Brush.horizontalGradient(listOf(palette.glassFillTop, palette.glassFillTop))
                },
            )
            .border(1.dp, if (isPartner) Color.Transparent else palette.glassBorderTop, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isPartner) Icons.Filled.Favorite else Icons.Filled.Person,
            contentDescription = null,
            tint = if (isPartner) Color.White else palette.textSecondary,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = if (isPartner) "TA" else "我",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isPartner) Color.White else palette.textSecondary,
        )
        Spacer(Modifier.width(3.dp))
        Icon(
            imageVector = Icons.Filled.SwapHoriz,
            contentDescription = "切换课表",
            tint = if (isPartner) Color.White.copy(alpha = 0.85f) else palette.textTertiary,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * 长按「我 / TA」按钮弹出的管理面板：
 * 重新导入 / 清除 TA 的课表，不占主界面空间。
 */
@Composable
fun PartnerManageDialog(
    palette: GlassPalette,
    partnerName: String,
    onDismiss: () -> Unit,
    onReimport: () -> Unit,
    onClear: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // 弹窗用**实色**卡片：GlassCard 的渐变中间夹着一层半透明，叠在正文上会糊成一团
        val shape = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .padding(horizontal = 30.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (palette.isDark) Color(0xFF1A2136) else Color(0xFFFCFCFF),
                            if (palette.isDark) Color(0xFF121826) else Color(0xFFF1F3FD),
                        ),
                    ),
                )
                .border(1.dp, palette.glassBorderTop, shape)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
                Text(
                    "TA 的课表",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                )
                Text(
                    text = partnerName.takeIf { it.isNotBlank() }
                        ?.let { "已导入 $it 的课表，存在这台手机上。" }
                        ?: "还没有导入过 TA 的课表。",
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                DialogButton(
                    text = "重新导入 TA 的课表",
                    background = Brush.horizontalGradient(
                        listOf(palette.accent, palette.accent.copy(alpha = 0.74f)),
                    ),
                    textColor = Color.White,
                    onClick = onReimport,
                )
                DialogButton(
                    text = "清除 TA 的课表",
                    background = Brush.horizontalGradient(
                        listOf(
                            palette.danger.copy(alpha = 0.14f),
                            palette.danger.copy(alpha = 0.14f),
                        ),
                    ),
                    textColor = palette.danger,
                    onClick = onClear,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消", color = palette.textTertiary, fontSize = 13.sp)
                }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    background: Brush,
    textColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 切到 TA 但还没导入过 TA 的课表时占位。
 * 直接给一个登录入口，不用再去设置里绕。
 */
@Composable
fun PartnerEmptyCard(
    palette: GlassPalette,
    onImport: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    "还没有 TA 的课表",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                )
                Text(
                    "用对方的学号密码登录一次教务系统就能导进来，" +
                        "只存在这台手机上，TA 的密码不会被保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(palette.accent, palette.accent.copy(alpha = 0.74f)),
                            ),
                        )
                        .clickable(onClick = onImport),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "登录 TA 的教务账号导入",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
