# 15 · 小组件跨天不刷新 & 周课表改滑动翻周

两件事，都是「用起来才发现」的问题：

1. 周三早上看桌面，小组件还停在**周二**的课表。
2. 周课表翻周要点页头右侧那两个 30dp 的小箭头，手指要横跨半个屏幕，一天翻十几次很难受。

---

## 一、小组件跨天不刷新

### 根因

刷新链只有一条腿：`WorkManager` 排的一次性任务，`initialDelay` 到第二天 00:05，
跑完再把自己排到下一天。

这条链在 MIUI / HyperOS 上很脆：应用被后台清理、被省电策略限制、或者从最近任务划掉之后，
延迟任务会被**直接丢掉**，链断了就再也不刷新。于是小组件永远停在断链那一天。
再加上 `updatePeriodMillis = 0`（不用系统的 30 分钟轮询），系统侧完全没有兜底 —— 只能靠用户手动点刷新。

顺带还查出 `DATE_CHANGED` 广播指望不上：Android 8 之后它是隐式广播，多数 ROM 不投递。

### 现在的三层刷新

| 层 | 触发 | 延迟 | 特点 |
|---|---|---|---|
| 系统轮询 | `updatePeriodMillis = 30min` | ≤30 分钟 | **最可靠**：AppWidget 服务主动找 App，进程能被拉起，不受厂商后台策略影响 |
| 午夜闹钟 | `AlarmManager` 每天 00:02 | 秒级（有精确闹钟权限时） | 无权限时降级成 1 小时窗口，有第 1 层兜底 |
| 打开 App | `MainActivity.onStart` | 立即 | 用户打开过一次 App，桌面日期就一定是对的 |

顺带把 `WorkManager` 依赖整个去掉了（Glance 自己会带一份，不影响它内部的 SessionWorker）。

### 又一个坑：进程缓存了时区

排查时发现：改完时区后，**已经在跑的进程**里 `LocalDate.now()` 仍然是旧日期 ——
`java.util.TimeZone` 把默认时区缓存在静态字段里，进程不重启就不刷新。
症状和"跨天不刷新"一模一样，但根因完全不同。

修法：`SystemEventReceiver` 收到 `TIMEZONE_CHANGED` 时先 `TimeZone.setDefault(null)`
（让 libcore 重新读 `persist.sys.timezone`）再刷新。

验证日志（真机同款链路，模拟器上跑出来的）：

```
I ClassFlowWidget: refreshNow today=2026-10-27 tz=Etc/GMT+12   ← App 启动时
I ClassFlowWidget: system event: android.intent.action.TIMEZONE_CHANGED
I ClassFlowWidget: refreshNow today=2026-10-28 tz=GMT          ← 同一进程，日期已翻正
```

### 还有两个 RemoteViews 的硬限制

- **线性布局最多 10 个子 View**，超出的被 Glance 直接截掉，只在 logcat 里丢一行
  `Column container cannot have more than 10 elements`。
  之前 4 节课 + 4 个间隔 + 顶部两条 + 进度条一共 12 个孩子，
  表现就是「课一多，学期进度条就消失」。修法：课程列表单独包一层 `Column`。
- **内容超出卡片高度不会滚动，也不会报错，就是看不见**。
  4x3（190dp）里塞「下一节」卡 + 3 节课后，底部进度条直接被挤出卡片。
  修法：高版的行数按剩余高度算 —— `(高度 - 135dp) / 27dp`，上限 4 行。

---

## 二、周课表改成左右滑动

页头那两个箭头删掉了，换成 `HorizontalPager`：

- 一页一周，`beyondViewportPageCount = 1` 让左右各一页提前组合，
  滑到一半时下一页已经量好尺寸，不会中途才第一次布局。
- 每次落定一页给一次 `SegmentTick` 触感，不盯页头也知道翻了几周。
- 页头的「第 N 周 / 日期区间」跟着 `pagerState.currentPage` 实时变；
  不在本周时出现「回到本周」胶囊，点了会动画滚回去。

### 状态同步的两个方向

```
手指翻页  → pagerState.currentPage 变化 → ViewModel.showWeek(page+1) → 页头变
点回到本周 → ViewModel.backToCurrentWeek() → state.week 变 → pager.animateScrollToPage
```

程序化滚动期间必须**屏蔽**前一个方向的回调，否则滚动经过的每一页都会反过来改写
ViewModel 的周次，动画被一次次打断，最后停在半路。`scrollingByCode` 这个开关就是干这个的。

### 数据加载不能跟着翻页走

一次把整个学期 20 周都算好（`GetAllWeekCoursesUseCase`，key 是周次），
翻页只是换一个 Map 的 key：

- 翻页时不再查库，不会出现"新一页先空一下再跳出来"；
- 20 周 × 30 来节课的过滤总共几百次比较，比一次翻页的卡顿便宜得多；
- 关键：`selectedWeek` **不参与**重数据那条 Flow 链。参与的话每次翻页都会重新订阅 Room
  并重建 20 周课表，滑动手感就是一顿一顿的。翻周只改一个 Int，在 `uiState` 里合并。

## 验证

- 67 个单元测试全过（新增 4 个午夜闹钟触发时刻的边界测试：零点后、白天、刚好到点、跨月）。
- 模拟器上：滑到第 10 / 12 周内容与日期区间都正确；「回到本周」动画回到第 9 周并恢复时间线。
- 小组件：周二 → 改成 GMT 后**不重启进程**就地变成「周三 · 10/28」，
  3 节课的情况下「学期进度」也回来了。

