# 02 · 技术方案与模块划分

## 一、总体架构（MVVM + Clean 简化版）

```
┌──────────────────────────────────────────────────────────────┐
│  UI 层  (Jetpack Compose + Material 3 + Navigation Compose)   │
│  Screen / Composable / ViewModel(StateFlow<UiState>)          │
└───────────────────────────┬──────────────────────────────────┘
                            │ 只依赖 UseCase / Repository 接口
┌───────────────────────────▼──────────────────────────────────┐
│  Domain 层（纯 Kotlin，无 Android 依赖）                        │
│  model: Semester / TimeSlot / Course / CourseSession / WeekMask│
│  usecase: GetTodaySessions / GetWeekSessions / SaveCourse /    │
│           DetectConflicts / ResolveCurrentWeek /              │
│           ImportIcs / ImportCsv / BuildWidgetModel / Backup    │
└───────────────────────────┬──────────────────────────────────┘
                            │ 依赖抽象接口
┌───────────────────────────▼──────────────────────────────────┐
│  Data 层                                                       │
│  Room(Entity/Dao/Database) ─ RepositoryImpl ─ DataStore(Prefs) │
│  IcsParser / CsvParser / TimetableProvider(Mock) / BackupJson  │
└───────────────────────────────────────────────────────────────┘
┌───────────────────────────────────────────────────────────────┐
│  平台接入层（不参与业务，只做「被系统唤起」）                     │
│  Glance Widget  |  WorkManager(每日刷新)  |  AlarmManager(提醒) │
│  BroadcastReceiver(开机/时区/日期变化)  |  Notification        │
└───────────────────────────────────────────────────────────────┘
```

**设计原则**
1. ViewModel 只有一个 `UiState` data class + 一个 `StateFlow`，禁止多个零散 StateFlow 造成重排。
2. Domain 层不 import 任何 `android.*`（除 `java.time`），保证 100% JVM 可单测。
3. Repository 返回 `Flow<T>` 做读、`suspend fun` 做写；写入后由 Room 的 `Flow` 自动推新，UI 不需要手动刷新。
4. 平台层通过 `@EntryPoint` 拿依赖，避免在 Receiver/Worker/Widget 里 new 对象。

## 二、Gradle 模块划分（决策：MVP 单模块）

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| A. 单模块 + 按功能分包 | 编译快、配置简单、MVP 迭代快 | 编译期无法强制分层边界 | ✅ **采用** |
| B. `:app / :core:model / :core:data / :core:ui / :feature:*` | 边界清晰、并行编译 | 11 个模块配置成本高，MVP 阶段收益低 | ❌ 延后 |

> 边界靠 **包结构 + 依赖方向约定**保证；第二阶段的 `:domain` 包不依赖 `:data` 包，若将来要拆模块，机械移动包即可，无需改代码。这一点会在第三阶段 README 的「后续演进」里写清。

## 三、依赖版本（第二阶段以实际可解析的稳定版锁定）

| 依赖 | 版本 | 说明 |
|------|------|------|
| AGP | 8.7.x | 需 JDK 17 |
| Kotlin | 2.1.x | Compose Compiler 由 Kotlin 插件提供（`kotlin("plugin.compose")`） |
| KSP | 2.1.x-1.0.x | Room + Hilt 都用 KSP，不用 kapt |
| compileSdk / targetSdk | 36 | 若上架时 Google Play 要求更高，改 `targetSdk` 一行即可 |
| minSdk | 26 | ✅ `java.time` 从 API 26 原生可用，**不需要 desugaring** |
| Compose BOM | 最新稳定 | `androidx.compose:compose-bom` |
| Material3 | BOM 内 | |
| Room | 2.7.x | KSP + `room-ktx` |
| Hilt | 2.56.x | `hilt-android` + `hilt-compiler` + `hilt-navigation-compose` + `hilt-work` |
| Glance | 1.1.x | `glance-appwidget` |
| WorkManager | 2.10.x | |
| DataStore | 1.1.x | Preferences |
| Navigation Compose | 2.8.x | 类型安全路由可用 |
| kotlinx-serialization | 1.7.x | JSON 备份 |
| 测试 | JUnit4 / Truth / Turbine / kotlinx-coroutines-test | |

> 版本号不是「拍脑袋写死」：M1 阶段我会先跑 `./gradlew :app:dependencies` 验证可解析，任何不可解析的版本当场说明并换成最近的稳定版。

## 四、核心算法设计

### 4.1 周次模型：Long 位图 + 规则双写

需求要求「周次用可查询的集合/位图存储」，同时编辑器要能回显「单周/双周/指定周次」。因此**规则与位图都存**：

```kotlin
enum class WeekRuleType { ALL, ODD, EVEN, CUSTOM }

/**
 * 位图：bit0 = 第 1 周，bit(i) = 第 i+1 周，最多 60 周（留 3 位余量，避免符号位）
 * 规则变更时立即重算 mask，mask 是唯一事实来源（single source of truth for filtering）
 */
@JvmInline
value class WeekMask(val bits: Long) {
    fun contains(week: Int): Boolean = week in 1..60 && (bits shr (week - 1)) and 1L == 1L
    fun toWeeks(): List<Int> = (1..60).filter(::contains)
    fun intersects(other: WeekMask): Boolean = (bits and other.bits) != 0L
    companion object {
        fun of(rule: WeekRuleType, explicit: Set<Int>, totalWeeks: Int): WeekMask {
            var b = 0L
            for (w in 1..totalWeeks) {
                val hit = when (rule) {
                    WeekRuleType.ALL  -> true
                    WeekRuleType.ODD  -> w % 2 == 1
                    WeekRuleType.EVEN -> w % 2 == 0
                    WeekRuleType.CUSTOM -> w in explicit
                }
                if (hit) b = b or (1L shl (w - 1))
            }
            return WeekMask(b)
        }
    }
}
```

**好处**
- 过滤一门课是否本周上：一次位运算，O(1)。
- 冲突检测两个时段是否有共同周次：`(a.bits and b.bits) != 0`，一次运算。
- SQL 也可查：`WHERE (week_mask & :bit) != 0`（SQLite 支持整数位与），小组件/统计查询能下沉到数据库。
- 存 `INTEGER`（8 字节），比 junction 表省 20~60 倍空间且无 join。

**为什么不建 `session_week` 关联表**：每门课 16 周 × 200 时段 = 3200 行，查询要 join，且写入要事务对齐；而位图是一列。代价是「按周次范围查」不能走索引，但全量时段数据 < 1000 条，全表扫描 < 1ms，不构成瓶颈。若未来出现「跨学期海量数据」场景，再补物化关联表。

### 4.2 当前周推导

```kotlin
fun resolveCurrentWeek(semester: Semester, today: LocalDate, override: Int?): WeekState {
    if (override != null) return WeekState(override.coerceIn(1, semester.totalWeeks), isOverridden = true)
    val days = ChronoUnit.DAYS.between(semester.startDate, today)   // 开学日所在周为第 1 周
    val week = floorDiv(days, 7L).toInt() + 1
    return when {
        week < 1 -> WeekState(1, isOverridden = false, beforeTerm = true)   // 未开学
        week > semester.totalWeeks -> WeekState(semester.totalWeeks, false, afterTerm = true)
        else -> WeekState(week, false)
    }
}
```
- `floorDiv` 处理「开学日之前」的负数天，避免 `%` 在负数上取到 -1 造成周次错位（这是最常见的课表 bug）。
- 手动切换当前周 = 写 `semester.week_override`；「恢复自动」= 置 NULL。**注意：手动覆盖会影响小组件与提醒**，所以设置页必须显式提示「当前为手动周次，提醒按第 N 周计算」。

### 4.3 冲突检测

```kotlin
data class Conflict(val newSession: CourseSession, val existing: CourseSession)

fun detect(new: CourseSession, all: List<CourseSession>): List<Conflict> = all.filter { e ->
    e.courseId != new.courseId &&                     // 同一门课的多时段不算冲突
    e.dayOfWeek == new.dayOfWeek &&
    e.startSlot <= new.endSlot && new.startSlot <= e.endSlot &&   // 节次区间相交
    e.weekMask.intersects(new.weekMask)               // 周次有交集
}.map { Conflict(new, it) }
```
- 复杂度 O(n)，n 为当前学期时段数（< 1000），UI 层做**非阻塞提示**：显示黄色警告条 + 「仍然保存」，不硬拦（因为调课、临时借教室是真实需求）。
- 冲突文案要具体：`与「高等数学」周一 3-4 节（第 1-16 周）冲突`。

### 4.4 长课程跨节渲染

- 单元格高度固定 `slotHeight = 64.dp`。
- 课程块：`top = (startSlot - 1) * slotHeight`，`height = (endSlot - startSlot + 1) * slotHeight - 4.dp`（4dp 用于视觉间隙）。
- 用 `Box` + `Modifier.offset(y = top)` 绝对定位，而不是把块塞进行（否则跨节必然被行分割）。

### 4.5 周课表滚动方案（含风险与兜底）

**方案 A（采用）**：共享 `ScrollState`
```
Column {
  HeaderRow:  [节次表头(56dp)] + Row(horizontalScroll(hState)) { 周一..周日 }
  Body: Row(verticalScroll(vState)) {
          TimeColumn(verticalScroll(vState))            // 左侧固定
          Row(horizontalScroll(hState)) { 7 × DayColumn } // 右侧横滑
        }
}
```
- 表头与内容共用同一个 `hState`，左右拖动内容时表头同步。
- **风险**：两个可滚动容器共享同一个 `ScrollState`，在快速甩动时可能出现 1 帧抖动或双重消费。
- **兜底 B**：若真机上出现抖动，改为自定义 `Layout`——单一 `horizontalScroll` 容器内绘制「节次栏 + 7 列 + 表头」，用 `graphicsLayer { translationX }` 把节次栏反向偏移，这样只有 1 个滚动容器，绝对无抖动。
- 第二阶段先做 A（代码短、易维护），并在 README 记录 B 的切换条件；这不属于技术栈变更。

### 4.6 ICS 解析

**决策：自研轻量解析器，不引入 ical4j/biweekly。**
理由：ical4j 依赖重（含 slf4j、时间库），APK 体积 +800KB 以上，而我们只需 `VEVENT` 的 6 个属性。自研约 250 行 + 单测，可控且可加告警。

```
解析流程：
1. 按行读取，处理行折叠（RFC5545：以空格/TAB 开头的续行拼接到上一行）
2. 状态机：VCALENDAR → VEVENT → 属性键值（支持 X- 前缀忽略）
3. 只取：SUMMARY / LOCATION / DESCRIPTION(教师正则) / DTSTART / DTEND / RRULE
4. DTSTART 解析：支持 `20250901T080000`、`20250901T080000Z`、`20250901`（全天事件跳过并告警）
5. 时区：带 Z 的按 UTC 转系统时区；带 TZID 的先按系统时区近似（告警）；无时区的按本地时间
6. 星期 = DTSTART 的 dayOfWeek → 1..7
7. 节次映射：用「起始时间落入哪一节的 [start, end) 区间」定 startSlot；
   若不在任何区间 → 取时间最近的一节并 `warning("时间 08:20 未匹配节次，已吸附到第 1 节")`
   endSlot 同理（结束时间 ≤ 该节 end 则归该节）
8. 周次：RRULE 无 → 单周(按 DTSTART 所在周)；INTERVAL=2 → 奇偶（按 DTSTART 周奇偶推 ODD/EVEN）；
   有 UNTIL/COUNT → 展开为 CUSTOM 集合
9. 组装 PreviewItem 列表（含 warning 与 conflict 标记）
```
预览页展示：`课程名 / 星期 / 节次 / 周次 / 地点 / ⚠告警 / ⚠冲突`，可单条勾选排除，再点「导入」。

### 4.7 CSV 解析

模板列（第一行表头，UTF-8 带 BOM 以便 Excel 正确识别中文）：

```csv
课程名称,教师,地点,星期,开始节次,结束节次,周次规则,周次,颜色,备注
高等数学,张伟,教三-201,1,1,2,ALL,,#3F51B5,带计算器
大学英语,李娜,外语楼-105,3,3,4,ODD,,#009688,
线性代数,王强,教一-302,5,5,6,CUSTOM,"1-8,10-16",#FF9800,
```
- `星期`：接受 `1..7` 或 `周一..周日` / `Mon..Sun`。
- `周次规则`：`ALL / ODD / EVEN / CUSTOM`（大小写不敏感）。
- `周次`：仅 CUSTOM 必填，格式 `1-8,10,12-16`，解析器容错中文逗号、全角数字。
- 支持下载模板：把上述内容写进 `assets/template.csv`，用 SAF `ACTION_CREATE_DOCUMENT` 让用户另存。

### 4.8 导入模式与幂等

- `OVERWRITE`：删除当前学期所有 `source != MANUAL` 的课程 → 插入新数据（事务内完成）。
- `MERGE`：按 `dedupe_key = sha1(课程名|教师|星期|起始节|结束节|周次规则|周次)` 去重，已存在则跳过并在结果里报告「跳过 N 条重复」。
- 两种模式都在**单个 Room 事务**内执行，失败整体回滚，不会出现「导了一半」。

## 五、关键决策与替代方案

| 议题 | 决策 | 替代方案 | 说明 |
|------|------|----------|------|
| 小组件框架 | Jetpack Glance | RemoteViews | 按需求指定；Glance 可用 Compose 语法复用设计 token |
| 提醒调度 | **AlarmManager 精确闹钟** | WorkManager | 需求只写了 WorkManager，但 WorkManager 无法满足分钟级准时提醒；**这是唯一需要你点头的补充** |
| 小组件每日刷新 | WorkManager（OneTime 自链 + Periodic 兜底） | AlarmManager | 00:05 刷新允许有几分钟误差，用 WorkManager 更省电、重启自动恢复 |
| ICS 解析 | 自研 | ical4j | 体积与可控性权衡 |
| 密码存储 | 不存（MVP 无网络） | EncryptedSharedPreferences | 未来真实适配器才用，且只存 token |
| 键值偏好 | DataStore Preferences | Room 单行表 | DataStore 更适合少量标量 |
| 时间类型 | `java.time`（LocalDate/LocalTime） | 时间戳 Long | API 26+ 原生支持，避免手写时间计算 bug |
| 颜色 | `Long` ARGB 存 Room | 枚举调色板 | 允许自定义任意色，导入的 ICS 可自动分配 |
| 主题 | Material3 `dynamicColor` + 手动覆盖 | 仅跟随系统 | 需求要求「主题切换」，所以提供 跟随系统/浅色/深色 三态 |

## 六、错误处理与状态约定

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Empty(val hint: String) : UiState<Nothing>   // 「今日无课」也算一种成功态
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>
}
```
- 所有用户可见错误文案集中在 `UiText`，不把异常栈直接抛给用户。
- 导入失败单独有 `ImportResult.Failure(reason, lineNo)`，能定位到 CSV 第几行。

## 七、性能与功耗约定

1. 周课表用固定高度 + 绝对定位，**不使用 `LazyColumn` 嵌套 `LazyColumn`**（会引发布局抖动）；节次 ≤ 12 时直接用 `Column` 足够。
2. Room 查询全部带 `semesterId` / `dayOfWeek` 索引；今日页只查 1 天。
3. 小组件不做全量查询，走 `BuildWidgetModelUseCase(limit)` 只取前 N+1 条。
4. 每次数据变更触发小组件刷新时，**300ms 去抖 + Mutex**，避免批量导入 200 条触发 200 次刷新。
5. 无网络权限，无后台常驻，无前台服务。

## 八、测试策略

| 层级 | 工具 | 覆盖对象 |
|------|------|----------|
| 纯 JVM 单测 | JUnit4 + Truth | `WeekMask`、`resolveCurrentWeek`、`detectConflicts`、`IcsParser`、`CsvParser`、`BuildWidgetModel` |
| DAO 测试 | Room in-memory + Robolectric（或 androidTest） | 5 张表的 CRUD、级联删除、位图查询 |
| ViewModel 测试 | `kotlinx-coroutines-test` + Turbine | 今日页/周课表状态流 |
| UI 测试 | Compose UI Test | 今日无课空状态、课表块点击进详情、冲突提示出现 |
| 手工验收 | 见 `05-acceptance-tests.md` | 8 条验收标准 |
