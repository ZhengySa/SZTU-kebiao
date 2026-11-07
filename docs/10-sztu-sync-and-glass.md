# 教务系统同步 + 液态玻璃视觉（第三阶段）

本文记录两件事：**怎么从深圳技术大学教务系统直接把课表抓下来**，以及**这一版界面为什么长这样**。
两者都写了"为什么这么做"，不是只列功能。

---

## 一、教务系统同步

### 1.1 用户看到的流程

1. 设置页最上面那张高亮卡片「同步学校教务系统」→ 点进去
2. App 打开**学校官方登录页**（内嵌 WebView）——账号、密码、**验证码全部在学校自己的页面里输入**
3. 登录成功的一瞬间（URL 稳定落在教务系统首页），App 自动接管会话、抓课表
4. 屏幕上出现**预览**：多少门课、多少个时段、有没有跟已有课程冲突
5. 选「覆盖」或「合并」，点确认，写库完成

第一次用完之后，会话（Cookie）会存在本地。下次进来如果会话还有效，会**跳过登录直接拉课表**；
只有会话过期才会再打开一次学校登录页。

> **为什么 App 里没有账号输入框和验证码输入框（第四阶段改动）**
> 最初的版本是 App 自己画表单 + 自己取验证码图片，虽然验证码是人眼识别、没有绕过，
> 但"密码经过 App 的输入框"这件事本身就是多余的风险面，而且每次会话过期都要重新输一遍三个字段。
> 现在改成 WebView 方案后：密码不经过 App 的任何代码路径（只在学校页面里），验证码就是
> 学校官方页面上那个验证码，App 只在登录完成后接管 Cookie。表单登录的代码
> （`DesEcb` / `SztuAuthClient.prepare/submit`）保留未删，作为链路文档和兜底，UI 已不再调用。

### 1.2 真实的认证链路（已用真实账号逐步验证）

> 现在的 App 版本通过内嵌 WebView 登录，下面的完整表单链路由学校页面自己完成；
> 这段保留下来是因为「会话怎么来的、哪一步种了什么 Cookie」的知识仍然被
> `SztuWebLogin` 的成功判定和会话接管逻辑依赖。

学校的统一身份认证用的是竹云（BambooCloud UIS），教务系统是正方新版（jsxsd）。
一条完整的登录要经过这些步骤，少一步都进不去：

```
GET  https://jwxt.sztu.edu.cn/                     302 → 认证页
GET  https://auth.sztu.edu.cn/idp/...ActionAuthChain?entityId=jiaowu
        └─ 页面里 JS 注入会话令牌：$(spCode).val("cc2fdbc3...")
GET  https://auth.sztu.edu.cn/idp/Kaptcha.jpg      取验证码（与当前会话绑定）
POST https://auth.sztu.edu.cn/idp/authcenter/ActionAuthChain
        op=login & spAuthChainCode=<令牌> & j_username=.. & j_password=<DES密文> & j_checkcode=..
        ← {"loginFailed":"false"}  ← 只说明账号密码+验证码过了
POST https://auth.sztu.edu.cn/idp/AuthnEngine?currentAuth=urn_oasis_names_tc_SAML_2.0_ac_classes_BAMUsernamePassword
        ← 302 一路跳回 jwxt.sztu.edu.cn/jsxsd/framework/xsMain.htmlx，拿到 jsxsd 会话
GET  https://jwxt.sztu.edu.cn/jsxsd/xskb/xskb_list.do        学期理论课表
GET  https://jwxt.sztu.edu.cn/jsxsd/jxzl/jxzl_query          教学周历（推导开学日期）
```

下面这几条是踩过的坑，写进代码注释里了，这里再记一遍：

**坑 1：密码不是明文传的。** 前端用 `encryptByDES` 加密，key 原文是 `PassB01Il71`，
但 CryptoJS 的 DES 只吃前 8 字节，所以真正参与运算的是 `PassB01I`。
用完整 11 个字符会直接报 `Wrong key size`。算法是 `DES/ECB/PKCS5Padding`，结果 Base64。

**坑 2：必须从教务系统入口进，不能直接打开认证页。** 直接打开认证页提交，服务端会回
`AuthChainList is null`——因为缺了从 jwxt 302 过来时拿到的 `spAuthChainCode` 会话令牌。

**坑 3：`spAuthChainCode` 不在 HTML 的 input 里。** 那些 `input[name=spAuthChainCode]` 的
`value` 全是空的，真正的值在页面底部的一段 JS 里：`$(spCode).val("<32位hex>")`。
所以只能按这段 JS 去正则提取。

**坑 4：`loginFailed=false` 不等于登录完成。** `ActionAuthChain` 只校验账号密码和验证码，
真正把票据发回教务系统、种下 `JSESSIONID` 的是后面那次 `AuthnEngine`。漏掉它，
下一次请求还是未登录。

**坑 5（真机才暴露）：https 首页会 302 到 http。**
```
curl -I https://jwxt.sztu.edu.cn/
→ 302  Location: http://jwxt.sztu.edu.cn/Logon.do?method=logonSSOszjsdx
```
学校服务器就是这么配的，改不了。所以清单里不能全局 `usesCleartextTraffic="false"`，
否则 Android 直接抛 `Cleartext HTTP traffic to jwxt.sztu.edu.cn not permitted`，
同步第一步就死。
正确做法是 `res/xml/network_security_config.xml` 里**只给这两个域名**开明文：

```xml
<base-config cleartextTrafficPermitted="false" />
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="false">jwxt.sztu.edu.cn</domain>
    <domain includeSubdomains="false">auth.sztu.edu.cn</domain>
</domain-config>
```

**坑 6（真机才暴露）：不能用 URL 判断是否已登录。**
一开始 `isLoggedIn` 写成「finalUrl 含 `/jsxsd/` 就算登录成功」。结果未登录时，
教务系统也会在 `/jsxsd/` 下返回一个错误页，于是被判成"已登录"，
程序接着去解析课表 → 抛"没找到课表表格"，用户看到的错误信息指向了完全错误的原因。
现在改成**只认课表表格本身**：

- `hasTimetable(body)` = 响应里有 `id="timetable"`
- `isAuthPage(finalUrl, body)` = 被踢回认证页（含 `spAuthChainCode`）
- 抓课表时：没表格 + 是认证页 ⇒ `SessionExpiredException`；没表格 + 不是认证页 ⇒ 明确的"没排课/服务异常"

**坑 7（WebView 方案的新坑）：登录成功的判定与 Cookie 域。**

- 未登录时访问 `jwxt.sztu.edu.cn` 会 302 去认证站，**URL 会离开 jwxt 域**；
  登录成功后才 302 回 `jwxt.sztu.edu.cn/jsxsd/...`。
  所以 WebView 里的成功判定是「URL 稳定停留在 jwxt 域的 `/jsxsd/` 路径下」，
  不能只看一次 URL 变化（中间跳转会被误判）。
- `CookieManager.getCookie(url)` 按 cookie 域过滤：竹云票据种在 `auth.sztu.edu.cn`，
  教务会话种在 `jwxt.sztu.edu.cn`。抓课表只走 jwxt 域，所以只导 jwxt 域的 Cookie 就够；
  把 auth 的 Cookie 也塞进 jwxt 域没有意义（域名对不上，服务端不认）。
- WebView 必须 `mixedContentMode = COMPATIBILITY`：学校会把 https 首页 302 到 http（坑 5），
  严格模式会在 WebView 里直接白屏。
- WebView 只放行 `*.sztu.edu.cn`，其它链接一律交给系统浏览器，登录页里点不了任何外站。

### 1.3 课表是怎么解析的

`ZfKbParser` 用 jsoup 解析 `table#timetable`。这个页面有几个反直觉的地方：

- **15 小节被压成 9 行**：`1-2 / 3-4 / 5 / 6-7 / 8-9 / 10 / 11-12 / 13-14 / 15`。
  所以解析时不能假设"一行一节"，要按表头里写的 `(01,02小节)` 展开成 15 个节次。
- **一门跨多节的课会在多行里重复出现**（第 3-4 节一行、第 5 节一行），
  所以**真实节次区间以课程自己写的 `[03-04-05节]` 为准**，再按 `dedupeKey` 去重。
- **教学楼和教室之间没有 `<br>`**：`<font title='教学楼'>【C-5公共教学楼】</font><font title='教室'>C-5-225</font>`
  两个标签紧挨着。如果只按 `<br>` 分行，两者会粘成 `【C-5公共教学楼】C-5-225`，教室就丢了。
  这个 bug 是被回归测试抓出来的（见 1.5）。
- **每格里有 `kbcontent1` 和 `kbcontent` 两份内容**：前者是简版、后者是详版，
  只取 `div.kbcontent`，否则每门课都会被算两遍。

### 1.4 开学日期不用手填

`ZfCalendarParser` 从「教学周历」表里推导：

- 表的第一列是周次，第二列是**星期日**（不是星期一），所以"第 1 周的星期一"要拿周日 +1 天
- 只有首尾两格带月份（`08月30日` / `09月05日`），中间几天只有日号，跨月要进位
- 某个学期代号 `2027-2028-2` → 第 1 周落在 **2028** 年 2 月（下学期属于结束年）

这里曾经有个 bug：代码里写"月份 < 6 就 +1 年"，结果春季学期（2 月开学）被整整错了一年。
现在改成由 `academicYearOf()` 统一算好"该学期落在哪个自然年"，`parseMonthDay()` 直接用，不再猜。

拿到开学日期后，连同 15 节的节次时间一起覆盖本地设置——因为学校是 8:30 开始、15 节，
而 App 默认是 8:00 开始、12 节。不对齐的话，"第 11 节 19:00" 会显示成"第 11 节 20:50"。

### 1.5 怎么保证它以后不悄悄坏掉

页面结构一变，解析器最常见的失败方式不是崩溃，而是**少解析出几门课**——
用户看到一张缺课的课表，还以为导入成功了。所以把真实抓下来的页面钉成测试夹具：

`app/src/test/resources/fixtures/xskb_xskb_list.html`（209 KB，2026-2027-1 学期真实页面）

`ZfKbParserTest` 用它断言：
- 15 个节次、第 1 节 08:30-09:55
- 「数字电子技术A」周一第 1-2 节、教师罗一星、教室 C-5-225、教学楼 C-5公共教学楼、周次 1,3,5…17
- 所有条目的星期/节次都在合法范围内，且没有重复条目
- 能读到当前学期 `2026-2027-1`

`ZfCalendarParserTest` 用构造的小表断言跨月进位、春季学期跨年、空页面不崩。

### 1.5.1 真机端到端验证结果

装到 Android 模拟器（1080×2400 / API 37）上，用真实账号完整走了一遍：

| 步骤 | 结果 |
|------|------|
| 打开同步页 | 表单渲染正常，验证码图片正常加载（本次为 `3UZE`） |
| 提交登录 | 通过统一身份认证，进入 jsxsd |
| 抓取课表 | **10 门课 / 21 个时段**，学期识别为 `2026-2027 学年 第 1 学期` |
| 推导开学日期 | `2026-08-31`，据此今天（10/26）是**第 9 周**，与教学周历一致 |
| 落库 | 「新增 10 门课 / 21 个时段」，节次表被替换成学校的 15 节 |
| 周课表渲染 | 课程块、跨节合并、今天列高亮、时间轴全部正确 |

过程中修掉的真机 bug 就是上面坑 5、坑 6 两条——这两条在纯逻辑推演里看不出来，
必须真跑一次才会暴露。

### 1.6 安全边界（重要）

这几条是硬约束，不是"尽量"：

| 约束 | 实现方式 |
|------|----------|
| 只能连学校两个域名 | `SztuConfig.ALLOWED_HOSTS` 白名单，`MiniHttp` 每次请求前 `require()` 校验，包括 302 跳转后的地址；WebView 侧同样只放行 `*.sztu.edu.cn` |
| **密码完全不经过 App** | 登录在学校的官方页面（内嵌 WebView）里完成，App 里没有任何账号/密码/验证码输入框，`SztuAccountStore` 不再存密码 |
| 不识别、不绕过验证码 | 验证码就是学校官方页面上那个，由学校页面自己校验，App 连"显示它"都不需要 |
| 不经过中间服务器 | 手机直连学校服务器，没有自建后端 |
| 不引入第三方网络库 | 抓课表只用 `HttpURLConnection`，避免"某个 SDK 顺手把数据发走" |
| 会话可随时清除 | 设置页「忘记学校登录」一键清空本地 Cookie |

---

## 二、液态玻璃（Liquid Glass）

### 2.1 为什么不用真正的背景模糊

Compose 在 Android 12 以下没有 backdrop blur。硬做的两种办法都很糟：
把背景重画一遍塞进卡片（卡顿、截断位置对不齐），或者用 RenderEffect（系统版本不够就没了）。
所以这里用**观感等价**的三层结构，稳定且不挑机型：

1. **底层**：4 个大半径低透明度彩色光斑，以 26s / 34s / 41s 三个错开的周期缓慢漂移
   （周期互质，看不出循环接缝）
2. **玻璃层**：竖向三段渐变（顶部亮 → 中间 → 底部暗）+ **渐变描边** + 顶部 1px 高光
   （描边比填充更关键——没有描边就是一块半透明的塑料片）
3. **内容层**：文字固定用高对比深色，不跟随背景变亮变暗，避免光斑飘过来就糊

### 2.2 组件

| 组件 | 位置 | 用法 |
|------|------|------|
| `LiquidBackdrop` | `ui/theme/Glass.kt` | 全屏背景，在 `ClassFlowNavHost` 里包一层，所有页面共享同一片光斑 |
| `GlassCard` | 同上 | 玻璃卡片，自带柔和外阴影（避免"贴纸感"） |
| `glassSurface()` | 同上 | Modifier 扩展，给任意形状（胶囊、圆形按钮）套玻璃 |
| `GlassScreenHeader` | 同上 | 统一页头，替代 Material 的 `TopAppBar` |
| `GlassPalette` | 同上 | 明/暗两套色板，通过 `LocalGlassPalette` 下发 |

### 2.3 两处容易做错的地方

**系统栏内边距不能叠加。** 外层 `Scaffold` 只管底部悬浮导航条，`contentWindowInsets` 设成 0；
状态栏内边距由各页面自己处理（有 `GlassScreenHeader` 的页面由它处理，没有的用 `statusBarsPadding()`）。
之前是内外都算，内容被顶下去一大截。

**`Surface` 不能是实色。** `MainActivity` 里原来用 `colorScheme.background`，会把整片渐变盖掉，
玻璃卡就变成"贴在纯色上"，完全没有层次。现在改成 `Color.Transparent`，底色交给 `LiquidBackdrop`。

---

## 三、这一版改了什么（相对第二阶段）

- 新增 `data/remote/sztu/` 整个远程模块（Config / DesEcb / MiniHttp / AuthClient / 两个解析器）
- 新增 `core/security/SecureBox.kt`、`data/local/SztuAccountStore.kt`
- 新增 `domain/usecase/SztuSyncUseCases.kt`
- 新增 `ui/screen/sztu/`（登录 / 预览 / 导入界面）
- 新增 `ui/theme/Glass.kt`，重写 `Theme.kt`、`SettingsScreen`、`ClassFlowNavHost`
- 重做 `TodayScreen`、`WeekScreen`、`WeekGrid`、`CourseCard` 为玻璃风格
- 其余页面（详情/编辑/导入/节次/学期/备份/帮助）统一换成 `GlassScreenHeader` + 透明容器
- `AndroidManifest` 加 INTERNET / ACCESS_NETWORK_STATE，并 `usesCleartextTraffic="false"`
- 单元测试从 30 个增加到 41 个（新增 6 个课表解析 + 5 个周历解析）
