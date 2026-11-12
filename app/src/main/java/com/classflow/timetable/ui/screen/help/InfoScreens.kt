package com.classflow.timetable.ui.screen.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.GlassPalette
import com.classflow.timetable.ui.theme.GlassScreenHeader
import com.classflow.timetable.ui.theme.LocalGlassPalette

/**
 * 隐私说明。
 *
 * 写法上刻意避开"我们承诺""请您放心"这类话：一份说明书只需要回答
 * 「存了什么、什么时候联网、密码怎么办」，剩下的交给读者自己判断。
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val palette = LocalGlassPalette.current

    Column(Modifier.fillMaxSize()) {
        GlassScreenHeader(
            title = "隐私说明",
            subtitle = "存了什么 · 什么时候联网",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 4.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "这个 App 没有服务器。课表从导进来那天起就只在你手机上，" +
                    "没有云端账号，也不用注册。",
                fontSize = 14.sp,
                lineHeight = 23.sp,
                color = palette.textPrimary,
            )

            Block(
                palette = palette,
                title = "存在哪",
                body = listOf(
                    "课表、学期、节次、提醒设置，都写进 App 自己的数据库，" +
                        "路径是 /data/data/com.classflow.timetable/。",
                    "卸载应用，这些文件跟着一起没，我这边留不下任何副本。",
                ),
            )

            Block(
                palette = palette,
                title = "什么时候联网",
                body = listOf(
                    "只有你主动点了「同步教务系统」的那一刻。这时候会连学校的两个地址：",
                    "jwxt.sztu.edu.cn —— 课表、成绩\nauth.sztu.edu.cn —— 统一身份认证",
                    "这两个域名在代码里写死成白名单，其它地址的请求会被直接拒掉。" +
                        "除了登录、拉课表、拉成绩，App 不发任何网络请求。",
                ),
            )

            Block(
                palette = palette,
                title = "账号和密码",
                body = listOf(
                    "勾了「记住密码」才会存。存之前用 Android Keystore 里的密钥加密，" +
                        "密钥由系统托管，App 自己也取不出明文。不勾就只用这一次，不落盘。",
                    "登录后拿到的会话会留在本机，省得每次都登一遍。" +
                        "想清掉：设置里点「忘记学校登录」。",
                ),
            )

            Block(
                palette = palette,
                title = "没做的事",
                body = listOf(
                    "没有埋点，没有崩溃上报，没有广告 SDK，" +
                        "也没有那种默认开着的「改进计划」。",
                    "不读通讯录、位置、相册、短信。" +
                        "通知和闹钟的权限，只有你打开课前提醒、或者把小组件放上桌面时才用得上。",
                ),
            )

            Block(
                palette = palette,
                title = "成绩和考试",
                body = listOf(
                    "成绩、考试安排都只来自 jwxt.sztu.edu.cn，用你自己登录后的会话去取，" +
                        "不经过任何中间服务器。",
                    "成绩会缓存在本机，断网也能看；考试安排不缓存：" +
                        "考场和座位属于敏感信息，每次打开临时取，看完只在内存里，" +
                        "不进数据库、不进备份、也不进桌面小组件。",
                ),
            )

            Block(
                palette = palette,
                title = "TA 的课表",
                body = listOf(
                    "导入别人的课表时，只存课表内容和他的学号（用来显示这是谁的课表）。" +
                        "他的密码不保存。",
                    "他的会话和你自己的会话一样，只在本机，随时能清。",
                ),
            )

            Block(
                palette = palette,
                title = "备份和验证码",
                body = listOf(
                    "JSON 备份要你自己点「导出」才会生成。存哪、发给谁，你说了算，" +
                        "App 不会自动往外送。",
                    "登录时的图形验证码由学校服务器生成，App 只负责把图显示给你、" +
                        "把你打的字原样交上去。没有识别，也没有绕过的打算。",
                ),
            )

            Text(
                text = "有疑问就去看代码，或者把这份说明和实际行为对着比一遍。",
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = palette.textTertiary,
            )
        }
    }
}

/** 一小节：一个不抢眼的小标题 + 一张玻璃卡，正文按段落分开排版。 */
@Composable
private fun Block(
    palette: GlassPalette,
    title: String,
    body: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 8.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textTertiary,
        )
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            // 这一页是纯阅读，卡片只要轮廓、不要投影：一屏五张带阴影的卡在滑动时会掉帧
            elevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                body.forEach { text ->
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        color = palette.textPrimary.copy(alpha = 0.86f),
                    )
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val palette = LocalGlassPalette.current

    Column(Modifier.fillMaxSize()) {
        GlassScreenHeader(title = "关于", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "课表 1.0.0",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
            )
            Text(
                text = "一个纯本地、离线优先的课表工具。",
                fontSize = 13.sp,
                color = palette.textSecondary,
            )
            Text(
                text = "技术栈：Kotlin · Jetpack Compose (Material 3) · Room · Hilt · Glance · AlarmManager",
                fontSize = 11.sp,
                lineHeight = 18.sp,
                color = palette.textTertiary,
            )
            Text(
                text = "数据存储：本机 SQLite（Room）+ DataStore，不联网。",
                fontSize = 11.sp,
                lineHeight = 18.sp,
                color = palette.textTertiary,
            )
        }
    }
}
