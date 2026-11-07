# ClassFlow

**给深技大（SZTU）同学用的课表 App：打开学校教务系统登一次，整学期课表自动拉下来。**

> ### 📱 [点这里下载 APK](https://github.com/ZhengySa/SZTU-kebiao/releases/latest)
> 进页面拉到 **Assets**，点 `ClassFlow-v*.apk` 下载到手机，点开安装（允许「未知来源」）就行。
> 不用装 Android Studio，不用配环境。系统要求 Android 8.0（API 26）及以上。
> 自签名包，以后新版本直接覆盖安装，课表数据不会丢。

不用截图 OCR、不用折腾 CSV、不用手打三十门课。登录发生在学校自己的页面上（验证码照常输），登完课表就上屏了 —— 外面套一层液态玻璃 UI，再配上桌面小组件、成绩、校园巴士时刻表。

[English](README.en.md) · [功能](#功能) · [怎么做的](#怎么做的) · [构建](#构建)

---

## 功能

### 登录＝打开学校自己的登录页

App 里**没有用户名输入框、没有密码框、没有验证码框**。它把 `auth.sztu.edu.cn` 加载进内嵌 WebView，你在那里像用浏览器一样登录（验证码也由学校页面自己校验），落到教务系统后 App 接管 session。密码从头到尾不经过 App 代码。

### 同步一次，拿全学期

课程、教师、教室、教学楼、周次范围 —— 外加两件大部分课表 App 让你手动填的事：

- **上课时间**：学校一天 15 节，从 08:30 开始。这些跟课程一起导入，所以第 11 节不会显示成错误的时间。
- **开学日期**：从学校校历页面推出来，你不用知道哪天开学，第 1 周就是对的。

写入之前先给预览（课程数 / 上课记录数 / 冲突数），再让你选**替换**还是**合并**。

### 五个页面

| 页面 | 做什么 |
|------|--------|
| **今日** | 今天的课排成玻璃卡片。左右滑可以翻遍**整学期的每一天** —— 它是一整条连续的带子，滑过周日就是下周一。 |
| **课表** | 一屏一周：7 天一横排、15 节一竖列，连堂课合成一块，今天的列上有一条红色时间线。左右滑 / 拖页头 / 点箭头，三种方式翻周。 |
| **巴士** | 校车 655，工作日和休息日两套时刻表，下一班倒计时，过点的车次置灰。 |
| **地图** | 三个 Tab：校园导览、C-5 栋 1～5 层教室图、中德智能制造学院 13 层平面图（可按房间号搜，问「305 在哪层」直接给答案）。双指缩放、双击复位。 |
| **设置** | 学期、节次时间、同步、成绩、公文通、备份、小组件教程、隐私。 |

### 成绩

每行给出课程名、成绩、绩点、学分。表格上方是总绩点、已修学分、课程数和教务处的专业排名。上学期的 chip 可以切学期；session 过期时退回上次的缓存，而不是白屏。

### 公文通

学校通知公告平台（`nbw.sztu.edu.cn`）做成了原生列表，不是套一层 WebView：

- 按**教务 / 科研 / 行政 / 学工 / 校园**五个栏目筛选，下拉刷新，滑到底自动加载下一页；
- 点进详情看正文和附件，附件交给系统浏览器打开；
- **只在校内网可达**（域名解析到 `10.1.12.148`），所以首页 20 条和看过的正文都会缓存 —— 出了校门打开还是能翻。

站点是服务端渲染的博达站群，解析靠 `<li>` 的 class 定位，不数 DOM 顺序。结构一变会明确报「页面结构变了」，而不是安静地显示一个空列表。

### 桌面小组件

Jetpack Glance，适配 4×2 / 2×4 / 4×4。显示今天的课、每门课的进度条和学期进度条。

让小组件在午夜准时翻页是没人会提前告诉你的坑 —— 见 [docs/15](docs/15-widget-day-change-and-week-swipe.md)。

### 还有一些小的

- **情侣课表**：用**对方**的账号登一次，导入 TA 的课表。上课记录分开存，对方的密码不落盘，页头一个胶囊在两套课表间切换。
- **运势**：每天 0～100 的数字，同一天内稳定不变。纯娱乐。
- **备份**：整库 JSON 导出 / 恢复。
- **上课提醒**：精确闹钟，课前 10 分钟；闹钟权限被拒时降级成 ±10 分钟窗口。

---

## 怎么做的

**Kotlin · Jetpack Compose (M3) · MVVM + 轻量 Clean Architecture · Room · DataStore · Hilt · Coroutines/Flow**

值得说几句的地方：

**周次是一张位图。** `WeekMask` 把 1～60 周压进一个 `Long`。"这门课第 N 周上不上"就是 `(bits shr (n-1)) and 1`；"两条记录周次是否重叠"就是 `(a and b) != 0`。冲突检测不需要双层循环，也不需要 `session_week` 关联表。

**时间从不写死。** `ScheduleBuilder` 在渲染时按**当前**节次表重算每门课的起止时间。你在设置里把第 3 节从 10:00 改成 10:30，周课表、今日页、小组件会一起变。

**当前周是推出来的，但可覆盖。** `WeekResolver` 用 `floorDiv`，开学日之前的天数不会算出"第 0 周"或"第 -1 周"。校历页面写错时，手动覆盖优先。

**`course_session` 故意不对 `time_slot` 建外键。** 它只存 `slot_index`，所以改某一节的时间不会级联删掉历史课程。

**小组件没用 WorkManager。** 它投递的延时任务会被 MIUI 的后台清理直接干掉 —— 那正是"今天周三了小组件还显示周二"的真正原因。改成：`updatePeriodMillis = 30min`（系统轮询，不受厂商策略影响）+ 00:02 的 AlarmManager 打点 + App 启动时刷新。这里还埋着一个真坑：`java.util.TimeZone` 把默认时区缓存在静态字段里，切过时区之后 `LocalDate.now()` 会一直返回旧日期，除非先 `TimeZone.setDefault(null)`。

**Pager 的状态不能依赖 Pager 自己上报的值。** `rememberPagerState(initialPage = ...)` 把 `initialPage` 当**可保存的 key** —— 把"当前页"喂进去，每翻一页就会重建 Pager，页码归零、手势中断。用 `remember` 把起始页钉住只取一次。另外绝不能让 `LaunchedEffect(state.week)` 去滚 Pager，同时手指翻页又在写 `state.week`：两个方向互相拽，表现就是"划一下弹回去，划一下弹回去"。最后只留一个方向 —— Pager → ViewModel，外加一个一次性的「回到本周」请求。

---

## 安全边界

| 规则 | 怎么保证的 |
|------|-----------|
| 只允许访问两个域名 | `jwxt.sztu.edu.cn` / `auth.sztu.edu.cn` 白名单，**每次请求都检查、包括 302 跳转**；其他一律抛异常 |
| 只对这两个域名放行明文 HTTP | 学校 HTTPS 首页会 302 到 HTTP，所以用一份收窄范围的 `network_security_config.xml` 只给这两个域名开口 |
| **密码不经过 App 代码** | 登录发生在学校自己的页面上，本地只留下 session cookie |
| 验证码是学校自己的 | 由学校页面显示和校验。没有绕过，没有 OCR |
| 没有中间服务器 | 手机直连学校，没有后端 |
| 不引第三方网络 SDK | 纯 `HttpURLConnection`，没有能偷偷外传数据的东西 |

域名清单、接口和解析器都是**深技大专用**的。移植到别的学校 = 重写 `SztuConfig` 和 `data/remote/sztu/` 下的三个解析器。

---

## 目录结构

```
app/src/main/java/com/classflow/timetable/
├── core/       security（Keystore AES/GCM）、time（WeekMath、AppClock）、di、ui helpers
├── domain/     纯 Kotlin：models、repositories（接口）、use cases、导入解析器
├── data/
│   ├── remote/sztu/   SztuConfig、DesEcb、MiniHttp、SztuAuthClient、
│   │                  ZfKbParser（课表）、ZfCalendarParser（校历）、ZfGradeParser
│   ├── local/         Room entities/DAOs/DB、DataStore stores
│   ├── mapper/
│   └── repository/
├── widget/     Glance 小组件、刷新调度、系统事件接收器
├── reminder/   AlarmManager + 通知
├── backup/     JSON 导出 / 恢复
└── ui/
    ├── theme/  Glass.kt —— 液态玻璃设计系统
    └── screen/ today / week / edit / detail / settings / semester / slots /
                imports / backup / help / sztu / grade / luck
```

五张表：`semester`、`time_slot`、`course`、`course_session`、`import_source`。禁用 `fallbackToDestructiveMigration()` —— 改 schema 必须写真正的 `Migration`。

---

## 构建

| | |
|---|---|
| JDK | 25（Android Studio 自带的 JBR 就行） |
| Gradle | 9.8.2 |
| AGP | 9.4.1 |
| Kotlin | 2.3.21 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |

```bash
./gradlew :app:assembleDebug        # Windows 上可以用 build.bat
./gradlew :app:testDebugUnitTest    # 109 条单元测试
```

`local.properties` 里需要有 `sdk.dir`。本项目是在 Android Studio 2026.2 / JDK 25 工具链上编译的 —— 换成更老的工具链，得把 AGP 和 Gradle wrapper 一起降级；版本矩阵和坑见 [docs/07](docs/07-build-environment.md)（AGP 9 自带 Kotlin，所以**不能**再加 `kotlin-android` 插件）。

> `gradle-wrapper.properties` 里的分发地址指向腾讯云镜像（官方的 `services.gradle.org` 在国内经常下不动）。
> 想换回官方，把 `distributionUrl` 改回 `https\://services.gradle.org/distributions/gradle-9.8.2-bin.zip` 就行。
> `build.bat` 只是帮忙找 `JAVA_HOME` 再转给 `gradlew.bat`，用不上可以直接敲 `./gradlew`。

---

## 明确的非目标

不做社交、不做云同步、不做多校适配（换学校要重写 `SztuConfig` 和 `data/remote/sztu/` 下的解析器）。

教务系统同步是**针对本校真实接口实现的**，但守住了三条底线：登录只在学校官方页面完成
（App 不收集、不存储账号密码，也不碰验证码逻辑）、不把课表发到学校之外的任何服务器。

---

## 文档

设计笔记都在 [`docs/`](docs/) 里 —— 大部分是具体 bug 的复盘，因为有用的细节都在那儿：

| | |
|---|---|
| [01–06](docs/01-prd-and-questions.md) | PRD、技术设计、数据库、页面与导航、验收清单 |
| [07](docs/07-build-environment.md) | 构建环境与版本坑 |
| [10](docs/10-sztu-sync-and-glass.md) | 登录流程、解析规则、安全边界、玻璃设计系统 |
| [12](docs/12-captcha-login-and-partner-timetable.md) | 验证码登录、情侣课表与按人隔离的数据迁移 |
| [14](docs/14-grades-and-motion.md) | 成绩、绩点、排名；以及为什么背景在每帧重新栅格化 5 张全屏渐变 |
| [15](docs/15-widget-day-change-and-week-swipe.md) | 小组件跨日翻页、RemoteViews 的限制、翻周手势 |
| [16](docs/16-luck-box-swipe-and-privacy.md) | Pager 吸附阈值、运势分布、重写隐私页 |
| [17](docs/17-continuous-day-pager.md) | 把今日页从 7 页改成"整学期一条带子" |
| [18](docs/18-week-swipe-real-root-causes.md) | 翻周"滑不动"的四个真因：Pager 重建、双向自反馈、拖动位移被清零、lambda 捕获旧快照 |

---

## 许可证

暂时没有 LICENSE 文件。在补上之前，默认保留所有权利 —— 想用请先开 issue。
