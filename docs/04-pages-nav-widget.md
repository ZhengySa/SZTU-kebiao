# 04 · 页面清单、导航图与小组件刷新策略

## 一、页面清单（13 个页面 / 5 个底部主导航入口中的 3 个）

底部导航 3 个 Tab：**今日** / **周课表** / **设置**。其余为二级页。

| # | 页面 | 路由（类型安全） | 入口 | 关键内容 | 状态处理 |
|---|------|------------------|------|----------|----------|
| 1 | 今日页 | `Today` | 底部 Tab / 小组件点击 / 启动默认 | 顶部日期 + 第 N 周 + 周切换；`HorizontalPager` 左右滑动日期；课程卡列表 | Loading / 空（「今日无课」+ 插画）/ 有课 / 未设置学期 |
| 2 | 周课表页 | `Week` | 底部 Tab | 7×12 网格、节次栏固定、课程块、当前周高亮、当前时间线、双指缩放（可选） | Loading / 空 / 正常 |
| 3 | 课程详情 | `CourseDetail(courseId, sessionId?)` | 今日卡片 / 周课表块 | 课程名、教师、颜色、备注；**全部时段列表**（每条：星期 + 第几节 + 起止时间 + 地点 + 周次描述）；编辑/删除 | 正常 / 已删除（返回并 Toast） |
| 4 | 课程编辑（新增/编辑） | `CourseEdit(courseId?)` | 详情页「编辑」/ 周课表 FAB / 空状态按钮 | 名称*、教师、默认地点、颜色、备注；时段列表可增删；每时段：星期、起始节-结束节、周次规则（全部/单/双/指定周次多选）、覆盖地点、备注 | 校验错误（名称必填、结束节 ≥ 起始节）/ 冲突提示条 |
| 5 | 设置首页 | `Settings` | 底部 Tab | 学期管理、节次设置、提醒、外观、导入、备份、小组件引导、隐私说明、关于 | 正常 |
| 6 | 学期设置 | `SemesterSettings` | 设置 → 学期 | 学期名、开学日期（DatePicker）、总周数、启用单双周开关、当前周（自动/手动切换）、多学期切换与新增 | 校验（总周数 1..60）/ 切换后提示「小组件已按新学期刷新」 |
| 7 | 节次设置 | `SlotSettings` | 设置 → 节次 | 12 行，每行：节次号、开始时间（TimePicker）、结束时间、所属段（自动推断可改）、启用开关；底栏「恢复默认」 | 校验（相邻节次重叠警告，不硬拦） |
| 8 | 导入入口 | `Import` | 设置 → 导入 / 空状态「导入课表」 | 三个卡片：ICS 文件、CSV 文件、教务系统（Mock）；各自说明与「选择文件」 | 文件选择取消 / 解析失败（显示行号） |
| 9 | 导入预览 | `ImportPreview(sourceType)` | 导入入口选择文件后 | 顶部：来源名 + 条数 + 模式选择（合并/覆盖）；列表：每条可勾选；⚠告警条目黄色标记；⚠冲突条目红色标记；底部「导入 N 条」 | 全不选禁用按钮 / 导入进度 / 完成结果（成功 N、跳过 M、冲突 K） |
| 10 | 备份与恢复 | `Backup` | 设置 → 备份 | 「导出 JSON」到 SAF、「从文件恢复」、上次备份时间、恢复前二次确认（会覆盖当前数据） | 成功/失败/文件格式不符 |
| 11 | 小组件引导 | `WidgetGuide` | 设置 → 桌面小组件 / 首次进入今日页的一次性提示 | 分步图文：长按桌面 → 小组件 → 找到「课表」→ 拖到桌面 → 调整大小；两个尺寸示意图 | 正常 |
| 12 | 隐私说明 | `Privacy` | 设置 → 隐私 | 数据只存本地、网络仅用于教务系统同步（域名白名单）、备份文件由用户自行保管、不收集统计 | 正常 |
| 13 | 关于 | `About` | 设置 → 关于 | 版本号、开源许可、反馈邮箱 | 正常 |

### 1.1 关键交互细节（避免第二阶段返工）

- **今日页日期切换**：`HorizontalPager` 共 `totalWeeks * 7` 页，`initialPage` 定位到今天；滑到学期外时显示「学期已结束」占位而不是空白。翻页后顶部周次标签联动。
- **今日页卡片排序**：按 `startSlot` 升序；上下课时间用 `TimeSlot` 表实时换算，不是导入时算死的字符串，**改节次时间后全 App 立即正确**。
- **周课表当前时间线**：仅当「显示周 == 当前周」且「今天在可视列」时绘制；位置由 `now` 在当天第 1 节开始到最后一节结束之间的线性插值确定；每分钟（`LaunchedEffect` + `delay(60_000)`）更新一次，页面不可见时停止。
- **周课表空态**：整周无课时，网格照常绘制（节次栏保留），中央浮层提示「本周无课，点击 + 添加」。
- **冲突提示**：编辑页保存时，如有冲突，弹出 `Snackbar`/底部条：「与「高等数学」周一第 3-4 节（1-16 周）冲突，仍要保存吗？」，两个按钮「返回修改 / 仍然保存」。**不硬拦**。
- **删除课程**：二次确认对话框，文案含课程名与时段数量。

## 二、导航图

```mermaid
graph LR
    subgraph BottomNav
        T[今日 Today]
        W[周课表 Week]
        S[设置 Settings]
    end

    T -->|点击课程卡| D[课程详情 CourseDetail]
    W -->|点击课程块| D
    T -->|FAB / 空态按钮| E[课程编辑 CourseEdit]
    W -->|FAB| E
    D -->|编辑| E
    D -->|删除后| T

    S --> SEM[学期设置]
    S --> SLOT[节次设置]
    S --> IMP[导入 Import]
    S --> BK[备份与恢复]
    S --> WG[小组件引导]
    S --> PV[隐私说明]
    S --> AB[关于]

    IMP -->|选择 ICS/CSV/教务| PRE[导入预览 ImportPreview]
    PRE -->|确认导入| W
    E -->|保存成功| T

    WIDGET((桌面小组件)] -.->|点击| T
    NOTI((课前提醒通知)] -.->|点击| D
```

**导航实现约定**
- 单一 `NavHost` + `NavController`，底部 3 个 Tab 用 `saveState/restoreState` + `launchSingleTop`，切 Tab 不重建。
- 小组件点击 → `Intent` 带 `android:data = "classflow://today"`，`MainActivity` 解析后 `navigate(Today)`；若目标页是详情，用 `classflow://course/{id}`。
- 通知点击 → `classflow://course/{id}`，直达详情。
- 返回栈：`CourseEdit` 保存后 `popBackStack()` 回到来源页；从 Tab 进入的二级页返回时回到对应 Tab。
- **不要在导航参数里传对象**（`Course` 等），只传 ID，页面内自行查库；这是避免「保存后列表不刷新」类 bug 的关键。

## 三、桌面小组件设计（Jetpack Glance）

### 3.1 尺寸与布局

需求写了「2x4 和 4x4」。为了同时覆盖两种方向理解，用 `SizeMode.Responsive` 声明三档，同一份数据渲染三套布局：

| 档位 | 典型尺寸 | 单元格估算 | 行数上限 | 每条内容 |
|------|----------|-----------|----------|----------|
| 紧凑（4 列 × 2 行） | 250 × 110 dp | 200 × 90 dp | 3 | 时间 + 课程名 |
| 竖长（2 列 × 4 行） | 180 × 250 dp | 140 × 230 dp | 4 | 时间 + 课程名 + 地点 |
| 大（4 列 × 4 行） | 250 × 250 dp | 200 × 230 dp | 6 | 第几节 + 课程名 + 起止时间 + 地点 |

- `appwidget-provider` 中 `minWidth/minHeight` 取最小档（140×90dp），`resizeMode="horizontal|vertical"`，`targetCellWidth/Height` 给默认 4×2。
- 超出条数：最后一行固定显示 `还有 N 节`（灰色，不可点击但整卡可点）。
- 无课：居中显示「今日无课」+ 日期；未设置学期：显示「去设置学期」。
- **必须包含的三要素**（验收标准 4）：名字、时间、节数、地点 —— 紧凑档至少给到「第 1-2 节 08:00-09:40」+ 课程名，地点在小档省略、竖长/大档显示。

### 3.2 视觉与深色模式

- 用 `GlanceTheme(colors = ...)` 按 `ColorProviders` 自动跟随系统深浅色；额外读取 App 内主题设置（`theme_mode`）优先级高于系统，保证「App 内选深色，小组件也深色」。
- 课程色只用**左侧 4dp 竖条**表达（不整块上色），保证深色下文字对比度足够（正文用 `onSurface`，不用课程色）。
- 圆角 16dp，内边距 12dp，字重只有 400/500 两档，标题 14sp、正文 12sp、辅助 11sp。
- 点击热区：整卡 → 今日页；单条 → 该课程详情（`actionStartActivity` + data uri）。

### 3.3 刷新策略（核心，逐条对应需求）

| # | 触发时机 | 机制 | 说明 |
|---|----------|------|------|
| 1 | 课程新增/编辑/删除 | `WidgetRefresher.requestRefresh()`：300ms 去抖 + `Mutex` + `updateAll()` | 批量导入只触发 1 次 |
| 2 | 当前周切换（手动覆盖/学期切换） | 同上（设置写入后调用） | |
| 3 | 节次时间修改 | 同上 | 时间显示实时换算，必须刷新 |
| 4 | 每日零点后（跨天） | `WorkManager` OneTime 自链：每次执行后重排下一个 00:05 | 跨天要换「今天」 |
| 5 | 每周一 00:00（周切换） | 被 #4 覆盖（00:05 已在新的一周）；额外安排「周一 00:01」独立 OneTime 作双保险 | 跨周要换「本周上不上」 |
| 6 | 系统日期/时间/时区变化 | `SystemEventReceiver` 监听 `ACTION_DATE_CHANGED`、`ACTION_TIME_CHANGED`、`ACTION_TIMEZONE_CHANGED` | 用户手动改时间也能纠正 |
| 7 | 开机 | 同上监听 `ACTION_BOOT_COMPLETED`（需 `RECEIVE_BOOT_COMPLETED`）→ 刷新 + 重排闹钟与 Worker | WorkManager 任务会持久化，但自链的 OneTime 需重排 |
| 8 | App 升级/首次安装 | `ACTION_MY_PACKAGE_REPLACED` → 刷新 + 排程 | |
| 9 | 用户添加/调整小组件尺寸 | Glance 自动重建（`SizeMode.Responsive` 变化触发） | 无需自己处理 |
| 10 | 提醒触发后 | 刷新一次（可选，让「已上过的课」样式置灰） | MVP 可不做，留扩展点 |

**实现要点**
- `provideGlance` 是 `suspend` 函数，**直接查 Room 即可**，不需要 `runBlocking`，也不需要把数据塞进 Glance state：
  ```kotlin
  override suspend fun provideGlance(context: Context, id: GlanceId) {
      val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
      val model = ep.buildWidgetModel()(ep.clock.now())          // 只取前 N+1 条
      provideContent { GlanceTheme { TodayWidgetContent(model) } }
  }
  ```
- 刷新入口统一走 `WidgetRefresher`（`object` + `Mutex` + `flow.debounce(300)`），避免导入 200 条课触发 200 次 `updateAll` 造成系统限流（Glance 更新过频会被 Launcher 丢帧甚至 ANR）。
- 时间线：`refreshPeriodMillis` 设为 `0`（关闭系统定时刷新），全部由 WorkManager + 事件驱动，**省电且可控**。
- 每日刷新 Worker 用「OneTime 自链」而不是 `PeriodicWorkRequest`：周期任务的 24h 窗口会在 Doze 下漂移到不可控时刻，自链能稳定卡在 00:05；同时保留一个 24h 的 Periodic 作为「自链断了」的兜底（`ExistingPeriodicWorkPolicy.KEEP`）。

**刷新时序（一次课程修改）**

```
用户点保存
  → Repository.insert/update (Room 事务)
  → Room Flow 发射 → ViewModel 自动重渲染
  → SaveCourseUseCase 末尾调用 WidgetRefresher.requestRefresh()
       → debounce(300ms) → GlanceAppWidget.updateAll(context)
            → provideGlance 重新执行 → 查库 → 渲染 RemoteViews
  → ReminderScheduler.rearmFor(course)   // 顺带重排提醒闹钟
```

## 四、提醒设计（补充说明，见问题 Q4）

| 项 | 方案 |
|----|------|
| 时间计算 | 对今天起未来 7 天的每个时段，`上课开始时间 - reminderMinutes` 得出提醒时刻；只排「未来 7 天」，每天 00:05 的 Worker 负责往后滚动补排 |
| 调度 | `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, triggerAt, pendingIntent)`；`canScheduleExactAlarms()` 为 false 时用 `setWindow(RTC_WAKEUP, triggerAt, 10min, pi)` |
| 权限 | `POST_NOTIFICATIONS`（Android 13+ 运行时申请）、`SCHEDULE_EXACT_ALARM`（Android 12+ 声明，用户可在设置关闭）、`RECEIVE_BOOT_COMPLETED` |
| 拒绝场景 | 通知权限被拒 → 设置页显示「提醒已关闭，点击开启」横幅 + 跳系统通知设置；精确闹钟被拒 → 提示「提醒可能延迟最多 10 分钟」 |
| 去重 | 每条提醒 `PendingIntent` 用 `requestCode = sessionId * 10 + dayOffset`，`FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`，重排同条覆盖不叠加 |
| 通知内容 | 标题：课程名；正文：`第 3-4 节 10:00-11:40 · 教三-201 · 15 分钟后开始`；点击进课程详情 |
| 渠道 | `class_reminder`，`IMPORTANCE_HIGH`，`setSound` 默认，`enableVibration(true)` |
