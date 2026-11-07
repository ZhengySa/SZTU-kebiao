# 06 · 第二阶段完成情况（全部模块已出代码 + 已编译通过）

> 状态：**M1–M12 全部完成**。本文件记录每个模块的落点与关键实现，
> 以及和第一阶段文档不一致的地方（都写明了原因）。
> 最后一次实际构建：`BUILD SUCCESSFUL`，`app-debug.apk` 22.4MB，单元测试 30 个全绿。

## 一、模块完成表

| 模块 | 状态 | 关键文件 |
|------|------|----------|
| M1 项目骨架与 Gradle | ✅ | `settings.gradle.kts`、`build.gradle.kts`（含 buildscript 覆盖 KGP 版本）、`gradle/libs.versions.toml`、`app/build.gradle.kts`、`AndroidManifest.xml`、`build.bat` |
| M2 Room 数据层 | ✅ | `data/local/Entities.kt`、`Daos.kt`、`AppDatabase.kt`、`Converters.kt`、`DefaultData.kt` |
| M3 仓库层 | ✅ | `data/mapper/Mappers.kt`、`TimetableRepositoryImpl.kt`、`SettingsRepositoryImpl.kt`、`DataBootstrap.kt`、`di/*` |
| M4 学期 / 节次设置 | ✅ | `ui/screen/semester/SemesterScreen.kt`、`ui/screen/slots/SlotScreen.kt` |
| M5 课程 CRUD + 冲突检测 | ✅ | `ui/screen/edit/CourseEditScreen.kt`、`ui/screen/detail/CourseDetailScreen.kt`、`domain/usecase/DetectConflictsUseCase.kt` |
| M6 周课表 / 今日页 | ✅ | `ui/screen/today/TodayScreen.kt`、`ui/screen/week/WeekScreen.kt`、`ui/components/WeekGrid.kt`、`CourseCard.kt` |
| M7 ICS / CSV 导入 | ✅ | `domain/imports/IcsParser.kt`、`CsvParser.kt`、`ImportSupport.kt`、`ImportModels.kt`、`domain/usecase/ImportUseCases.kt`、`ui/screen/imports/ImportScreen.kt` |
| M8 Glance 小组件 | ✅ | `widget/TodayWidget.kt`、`TodayWidgetReceiver.kt`、`WidgetDataLoader.kt`、`WidgetRefreshScheduler.kt`、`SystemEventReceiver.kt`、`res/xml/today_widget_info.xml` |
| M9 课前提醒 | ✅ | `reminder/ReminderScheduler.kt`、`ReminderReceiver.kt`、`NotificationHelper.kt`、`res/drawable/ic_notification.xml` |
| M10 备份 / 恢复 | ✅ | `backup/BackupModels.kt`、`BackupManager.kt`、`ui/screen/backup/BackupScreen.kt` |
| M11 测试 | ✅ | `WeekMaskTest`(7)、`WeekMathTest`(8)、`ScheduleBuilderTest`(4)、`ImportParsingTest`(11) |
| M12 设置页补全 + 小组件引导 | ✅ | `ui/screen/settings/SettingsScreen.kt`（导入/提醒/小组件/备份四个入口）、`WidgetGuideScreen` |

## 二、和第一阶段文档的差异（都写明原因）

| 项 | 一阶段写的 | 实际 | 原因 |
|----|-----------|------|------|
| compileSdk / targetSdk | 36 | **37** | 本机 SDK 只装了 android-37，没有 35/36 平台，也没有 cmdline-tools 可装 |
| AGP | 8.x 系列 | **9.4.1** | 本机 JDK 是 25，AGP 8 + Gradle 8 不支持 Java 25 |
| Kotlin 插件 | `kotlin.android` | **不适用** | AGP 9 起 Kotlin 编译内置，加了反而报错 |
| 小组件尺寸口径 | 2x4 与 4x4 | **4 档响应式**（150/250 宽 × 110/190/260 高） | 「2x4」在不同桌面网格下含义不同，用 `SizeMode.Responsive` 按真实 dp 选最近一档，任何网格都不会截断 |
| 提醒实现 | WorkManager（一阶段明确标注不可行） | **AlarmManager** | 一阶段已说明并征得同意：WorkManager 做不到分钟级准时 |
| Compose UI Test | 计划编写 | **未写** | 需真机/模拟器；核心逻辑已由纯 JVM 单测覆盖，UI 回归按 `docs/09` 的手工步骤执行 |

## 三、这一批值得注意的实现

1. **周次位图**：`WeekMask`（`@JvmInline value class` 包 `Long`）。`contains` / `intersects` 都是单次位运算，冲突检测和单双周过滤没有循环。
2. **时间不落库**：`ScheduleBuilder` 每次渲染现算起止时间，改节次时间 => 今日页/周课表/小组件同时变。
3. **写操作统一通知**：仓库层每个写方法末尾调 `ScheduleChangeNotifier.notifyScheduleChanged()`，
   `AppScheduleChangeNotifier` 用 `Channel.CONFLATED` + 300ms 合并，批量导入 200 门课也只刷一次桌面。
4. **导入两阶段**：`PrepareImportUseCase` 只解析 + 检测冲突（不写库），用户确认后才 `ApplyImportUseCase` 落库，
   不存在「先写再删」的危险实现。CSV 读文件时先按 UTF-8 解，出现替换字符再退回 GBK（Excel 导出常见）。
5. **提醒整体重排**：每次变更先取消全部已排闹钟再重排未来 8 天，`requestCode = sessionId*1000 + dayOfYear` 稳定且不撞码。
6. **小组件刷新三路兜底**：数据变更（去抖）、每日 00:05 WorkManager OneTime 自链、开机/改时区/跨天广播。
7. **备份格式独立于数据库**：`BackupFile` 单独定 DTO 且带 `version`，恢复时先 `clearAllTables()` 再在事务里写，整体替换不留中间态。

## 四、怎么跑

见 `README.md` 和 `docs/07-build-environment.md`。最短路径：

```bat
D:\xiangmu\build.bat :app:assembleDebug
adb install -r D:\xiangmu\app\build\outputs\apk\debug\app-debug.apk
```
