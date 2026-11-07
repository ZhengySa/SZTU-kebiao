# 18 · 翻周为什么「滑不动」：三处真因

> 用户连续两轮反馈「周课表不能滑动翻周」「第九周周日继续滑要能到第十周」。
> 第一轮改的是「今日页」，改完用户说**还是不行** —— 因为真正的病灶在周课表页，而且不止一个。
> 这份文档记录**三个**各自独立、但症状都表现成「滑不动」的 bug，以及怎么把它们逐个钉死的。

---

## 症状一：翻一页后 Pager 被重建

**表现**：连续左滑，只前进一两周就再也不动了；右滑完全无效。

**取证**：在 `rememberPagerState` 前打一行日志

```kotlin
Log.d("ClassFlowPager", "compose new pager initialPage=$startPage")
```

连续滑两次，日志是：

```
compose new pager initialPage=0
compose new pager initialPage=0
page->1
compose new pager initialPage=1     ← 翻页后 PagerState 被重建
page->1
```

`rememberPagerState` 内部是 `rememberSaveable(initialPage, ...)`，**`initialPage` 是 saveable 的 key**。
把「当前页」喂给它 → 用户翻页 → ViewModel 的周次变 → `initialPage` 变 → key 变 → `PagerState` 重建 → 页码回落到新 key 对应页、进行中的手势被取消。

**修法**：初始页只取一次。

```kotlin
// ❌ 每翻一页都会重建
val pagerState = rememberPagerState(initialPage = state.week - 1, pageCount = { state.totalWeeks })

// ✅ 首帧定死，之后只由 Pager 自己改自己
val startPage = remember { (state.week - 1).coerceAtLeast(0) }
val pagerState = rememberPagerState(initialPage = startPage, pageCount = { state.totalWeeks })
```

**教训**：Pager 的状态里，**任何**从「Pager 自己上报出去的值」（这里是 `state.week`）反推回来的构造参数，都会形成自反馈环。这类参数必须只读一次。

---

## 症状二：两个方向互相拽

**表现**：划一下弹回去，划一下弹回去；只有连续快划才能勉强往前挪。

**原因**：同时存在两条方向相反的写入：

- 手指翻页 → `snapshotFlow { currentPage }` → `viewModel.showWeek()`
- `LaunchedEffect(state.week) { pagerState.animateScrollToPage(state.week - 1) }`

第二条本意是「点回到本周之后跳过去」，但它对**任何**周次变化都生效。手指翻到第 4 周 → VM 变 4 → Effect 触发 → 滚向第 4 页（此刻 Pager 已经在那，但动画打断了手势）→ 再往前，VM 又变 → 又被拽。

**修法**：单一数据流，只保留 Pager → ViewModel 一个方向。反向跳转用**一次性请求信号**：

```kotlin
var backRequest by remember { mutableStateOf<Int?>(null) }

LaunchedEffect(backRequest, pagerState) {
    val week = backRequest ?: return@LaunchedEffect
    pagerState.animateScrollToPage(week - 1)
    onBackHandled()          // 消费掉，绝不会因为别的原因再触发
}
```

---

## 症状三：`onDragCancel` 把累计位移清零

这个最隐蔽，而且**是我自己写的代码自己坑自己**。

页头左右拖也能翻周，实现是：

```kotlin
var total = 0f
val threshold = 48.dp.toPx()
detectHorizontalDragGestures(
    onDragStart = { total = 0f },
    onDragEnd = {
        if (total <= -threshold) onDragEnd(-1f)
        else if (total >= threshold) onDragEnd(1f)
    },
    onDragCancel = { total = 0f },      // ← 这里
) { _, amount -> total += amount }
```

看着合理，实际**永远不翻**。因为在 Compose 的 `detectHorizontalDragGestures` 里，
手指抬起时**先走 `onDragCancel`、再走 `onDragEnd`**（内部把「抬起」当成一次 cancel 分支收尾）。
于是 `onDragEnd` 读到的 `total` 永远是 0。

**修法**：`onDragCancel` 只在**真被打断**时才清（多指、系统手势抢走）；正常抬起交给 `onDragEnd`。

```kotlin
onDragEnd = { /* 读 total，不清零 */ },
onDragCancel = { total = 0f },   // 保留，但已经不影响正常抬起路径
```

---

## 症状四：lambda 里捕获了过期的 `state.week`

修完上面三个之后，页头拖动**能**触发了，但「拖三下只动一下」。

日志证明：`headerDrag dir=-1.0 vmWeek=1` —— 连着三次，`vmWeek` **恒为 1**。

```kotlin
onDragWeek = { dir -> viewModel.showWeek(state.week + dir.toInt()) }
```

这个 lambda 捕获的是**组合那一刻的 `state.week` 快照**。它只在 `CompactHeader` 重组时被替换，
而翻页走的是 Pager → VM，`CompactHeader` 的参数里没有「当前页」这种每帧都变的东西，
所以 lambda 可能长期持有旧值。三次都算 `1 - 1 = 0` → clamp 到第 1 周 → 看起来一动不动。

**修法**：不要传「目标周次」，传「方向」，让 Pager 拿**自己实时的 `currentPage`** 去加。

```kotlin
var stepRequest by remember { mutableStateOf<Int?>(null) }
onPrevWeek  = { stepRequest = -1 }
onNextWeek  = { stepRequest = 1 }
onDragWeek  = { dir -> stepRequest = dir.toInt() }

// WeekPager 内
LaunchedEffect(stepRequest, pagerState) {
    val dir = stepRequest ?: return@LaunchedEffect
    val target = (pagerState.currentPage + dir)
        .coerceIn(0, (state.totalWeeks - 1).coerceAtLeast(0))
    if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
    onStepHandled()
}
```

**教训**：凡是「相对当前状态挪一步」的操作，**不要在提交处计算目标值**，把「步长」交给持有真实状态的那一层去算。这条放到任何 UI 状态管理里都成立。

---

## 排查方法论

模拟器测试这类「滑不动」时，有两件事比看代码有用得多：

1. **先确认前台是哪个 App**
   ```bash
   adb shell dumpsys window | grep mCurrentFocus
   ```
   本次调试中，模拟器里一个第三方小游戏 `com.example.miemie` 会反复抢走焦点，
   `uiautomator dump` 抓到的是它而不是被测 App —— 有一轮所有的「滑动无效」结论都是假的。

2. **拆开测「手势」和「状态」**
   滑动无效有三种完全不同的成因：手势没收到、收到了但被别的组件消费、状态改了但被反向拽回。
   分别用「加日志看 `isScrollInProgress`/`offsetFraction`」和「看 ViewModel 的周次」来区分，
   不要笼统地当成一个 bug 反复改同一个地方。

---

## 验收（模拟器 emulator-5554，逐条实测）

| 操作 | 期望 | 实测 |
|------|------|------|
| 网格左滑 ×3（慢速，y 在网格下半部） | 逐周 +1 | 1 → 2 → 3 → 4 ✅ |
| 网格左滑 ×3（快速） | 逐周 +1 | 1 → 2 → 3 ✅ |
| 网格右滑 ×2 | 逐周 -1 | 4 → 3 → 2 ✅ |
| 页头左拖 | -1 | ✅（触发正常） |
| 页头右拖 | +1 | ✅（触发正常） |
| 「回到本周」 | 跳回今天所在周 | ✅ |
| 页头箭头点按 | ±1 | 逻辑由 `WeekStepTest` 覆盖；**触屏实测未完成**，原因见下 |

### 未能完成实测的原因（重要，不是代码问题）

测试用的模拟器同时被**另一个自动化会话**占用。从 logcat 抓到的外部 adb 命令原文：

```
adbd service requested 'shell,v2,TERM=dumb,raw:pm uninstall --user 0 com.classflow.timetable; \
  am force-stop com.example.miemie; am start -n com.example.miemie/.MainActivity; \
  sleep 12; input tap 912 1278; ...'
```

它会在任意时刻：卸载本项目的测试包、抢走前台（`com.example.miemie`、`googlequicksearchbox`、
GMS 账号页）、在固定坐标上乱点。表现极具迷惑性 —— 看起来像「滑动无效」「白屏」
「Activity does not exist」，实际是外部会话在改这台设备的状态。

排查这类问题最快的一招是直接问 adb 自己要日志：

```bash
adb logcat -d | grep "adbd service requested"
```

每条经 adb 下发的命令都会原文出现在这里，是不是自己发的、什么时候发的，一目了然。


---

## 顺带记录：今日页

今日页是另一个 Pager，问题不同：它原来固定 7 页（一周），所以「第九周周日再划」会停住。
改成 `totalWeeks * 7` 页，把整学期连成一条带子 —— 周日 +1 天然就是下周一，不需要特判。
详见 [17](17-continuous-day-pager.md)。
