# 12 · 验证码登录恢复 + 情侣课表

> 2026-10-27 · 第 5 轮迭代

## 背景与决策

上一轮把登录页改成了内嵌 WebView（学校官网登录页），真机上**总是进入失败**，用户登录一直不成功。
本轮按用户要求：

1. **恢复原生验证码登录**：学号 + 密码 + 学校验证码图片，走回已验证可用的
   `SztuAuthClient.prepare()/submit()` 链路（DES/ECB + 竹云 SSO + 正方）。
2. **新增「情侣课表」**：通过登录对方的教务账号导入 TA 的课表；
   入口放「设置」页（用户明确要求**不放底部导航栏**）。

## 验证码登录（恢复 + 增强）

`SztuLoginViewModel` 重写回表单流：

- `start(targetKey)`：进页面先尝试用本地会话恢复；没会话/会话过期 → 拉一张验证码落到表单。
- `login()`：`prepare()` 拿到的会话 + `submit(学号, 密码, 验证码)`；
  验证码错或密码错会**自动换一张新验证码**并保留已填的账号密码。
- `refreshCaptcha()`：只换验证码，不动账号密码。
- UI：玻璃卡片表单，验证码图片点击即换；记住密码可选（Keystore 加密落盘）。

## 情侣课表（数据归属 owner_key）

### 数据层

- `OwnerKey`（`SELF` / `PARTNER`）概念常量。
- `course` 表新增列 `owner_key TEXT NOT NULL DEFAULT 'self'`，Room **v1→v2 显式迁移**
  （`Migrations.kt`，仍然禁止 destructive migration）。
- DAO/仓库/usecase 全链路加 `ownerKey` 参数：
  - `getAllInSemester / deleteBySemester / countInSemester` 按归属过滤；
  - `observeSchedule(semesterId, ownerKey)` SQL 带 `c.owner_key = :ownerKey`；
  - `importCourses(..., ownerKey)`：**合并/覆盖模式只作用于对应归属**——
    覆盖导入 TA 的课表不会动自己的课，反之亦然。
- `GetWeekCoursesUseCase(semester, week, ownerKey)`：今日页/周网格/情侣页共用，
  今日与课表页传 `SELF`，情侣页传 `PARTNER`。

### 会话隔离（SztuAccountStore）

TA 单独一套 DataStore 键（`sztu_partner_*`），只存**学号 + 会话 Cookie**，**绝不存密码**：

- `savePartnerLogin(username)` / `savePartnerSession(cookies, ...)` / `forgetPartner()`
- 自己的登录状态与 TA 的互不覆盖（否则给 TA 登录一次就把自己顶掉）。

### 页面与入口

- `PartnerScreen`（`Routes.PARTNER`）：
  - 空状态：说明文案 +「登录 TA 的教务账号导入」渐变按钮；
  - 状态卡：**「TA 正在上课 / TA 接下来有课 / TA 今天没课 / TA 今天已放学」**，
    下课倒计时每分钟刷新；
  - 今日/本周双 Tab，本周复用 `WeekGrid`（7×15 一屏）；
  - 重新导入、清除（带确认对话框，只清 TA 的数据）。
- 入口：设置页新增「关系」分组 →「情侣课表」行，副标题动态显示
  「已导入 xxx 的课表」。
- `SztuLoginScreen(forPartner = true)`：标题「导入 TA 的课表」，登录成功写入
  `PARTNER` 归属；同步页（自己）标题不变。

### 边界规则

- `ApplySztuImportUseCase`：节次时间/开学日期/总周数等**共享设置只在同步自己时覆盖**；
  导 TA 的课表不会改我自己的学期配置。
- 课程详情/编辑页点开 TA 的课时正常（课程本体共用一张表，归属只影响列表过滤）。

## 测试

- 新增/调整：BusDataTest（9 条）、迁移与归属相关用例；**共 50 条单测全绿**。
- 模拟器实测链路：设置 → 情侣课表 → 登录 TA → **验证码图片真实从学校服务器拉到**、
  点击换图正常；自己的同步页同样恢复验证码表单（记住的学号密码自动回填）。
- 截图未随仓库发布（涉及真实学号和课表，留在本地）。

## 已知坑（本轮）

- 并行编辑同一文件的 Edit 又吞了一次 import（`PartnerScreen` 未导入），编译期暴露后补上
  ——**同一文件的编辑必须串行**。
- 模拟器中途掉线 + 冷启动慢，截图前先 `getprop sys.boot_completed` 确认开机完成。
