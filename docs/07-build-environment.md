# 07 · 构建环境与实际踩坑记录

> 这份文档记录的是**这台电脑上真实跑通的配置**，不是照抄官方文档的理想值。
> 换电脑时按最后一节改两三个数字即可。

## 一、本机实际版本

| 项 | 实际值 | 说明 |
|----|--------|------|
| 操作系统 | Windows（`D:\` 为工作盘） | — |
| Android Studio | **2026.2.1**，路径 `D:\no game\Android` | 自带的 JBR 是 **JDK 25** |
| JDK | `D:\no game\Android\jbr`（OpenJDK 25.0.3） | Studio 启动时强制要求 25，所以整个工程必须能在 JDK 25 上跑 |
| Gradle | **9.8.2**，解压于 `C:\Users\ZhengY\gradle-dist\gradle-9.8.2` | 系统里没有 `gradle` 命令，工程里也没放 wrapper jar，所以用这个目录里的 `gradle.bat` |
| Android Gradle Plugin | **9.4.1** | 见下方「为什么必须 AGP 9」 |
| Kotlin 编译器 | **2.3.21** | AGP 9 内置的 KGP 版本较旧（2.2.x），读不了用 Kotlin 2.4 编译的 AndroidX 库，用 buildscript classpath 覆盖掉 |
| KSP | **2.3.12** | Room / Hilt 的注解处理 |
| compileSdk / targetSdk | **37 / 37** | 本机 SDK 只装了 `platforms/android-37.0`，没有 35/36 |
| minSdk | 26 | 按需求 |
| Build-Tools | 36.0.0（SDK 里唯一一个） | AGP 会自动选用 |

依赖版本全部在 `gradle/libs.versions.toml` 里，改一处即可。

## 二、为什么必须 AGP 9（而不是常见的 AGP 8.x）

本机 JDK 是 25。AGP 8.x + Gradle 8.x 组合不支持 JDK 25（Gradle 8 最高支持到 Java 24，会直接报
`Unsupported class file major version 69`）。要让工程在这台机器上编译，只有两条路：

1. 另外装一个 JDK 17/21，并让 Gradle 用它 —— 需要额外下载 200MB 和一个全局配置；
2. 用 Gradle 9 + AGP 9 —— 直接吃 JDK 25，不用装任何东西。

选了第 2 条。

**代价**（也是这次真实踩到的坑）：

- **AGP 9 起 Kotlin 编译内置**，`app/build.gradle.kts` 里**不能再写** `alias(libs.plugins.kotlin.android)`，
  否则报：
  ```
  The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.
  ```
- Compose 编译器插件仍然需要（`org.jetbrains.kotlin.plugin.compose`），否则报
  `Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is required`。
- `kotlinOptions { jvmTarget = "17" }` 这种老写法换成顶层 `kotlin { compilerOptions { jvmTarget.set(...) } }`。
- 内置 Kotlin 默认版本读不了新库的元数据（报
  `Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.4.0, expected version is 2.2.0`），
  解决办法是在根 `build.gradle.kts` 里用 `buildscript { dependencies { classpath(kotlin-gradle-plugin:2.3.21) } }` 提升版本。

## 三、Gradle 文件锁被 Windows 拒绝访问（已解决）

现象：编译到 `checkDebugDuplicateClasses` / `mergeExtDexDebug` / `hiltAggregateDepsDebug` 时随机失败：

```
java.io.FileNotFoundException: C:\Users\ZhengY\.gradle\caches\9.8.2\transforms\.internal\locks\xxxx.lock (拒绝访问)
```

原因：上一次被中断的编译在同一目录留下大量锁文件，处于「删除挂起」状态，
后续进程既打不开也删不掉。日志里那串乱码 `鎷掔粷璁块棶` 就是 GBK 显示成 UTF-8 的「拒绝访问」。

处理办法（按顺序试）：

1. 关掉 Android Studio，确认任务管理器里没有 `java.exe`；
2. 删掉整个目录：
   ```
   C:\Users\ZhengY\.gradle\caches\9.8.2\transforms\.internal\locks
   ```
   删不掉就改名成 `locks_bak`（改名不需要删除权限，Gradle 会自己重建）；
3. 再编译一次。

预防：`gradle.properties` 里已把并行构建关掉（`org.gradle.parallel=false`、`org.gradle.workers.max=2`），
并且不要中途强杀编译进程。

## 四、命令行怎么编译

工程根目录有 `build.bat`，已经把 JDK 和 Gradle 路径写死：

```bat
REM 打包
D:\xiangmu\build.bat :app:assembleDebug

REM 单元测试
D:\xiangmu\build.bat :app:testDebugUnitTest

REM 两个一起
D:\xiangmu\build.bat :app:testDebugUnitTest :app:assembleDebug
```

产物：`app\build\outputs\apk\debug\app-debug.apk`

用 Android Studio 打开也可以，但注意 **不要让它「升级 AGP」**，也不要选低于 9 的版本，
否则会回到上面第二节那个 JDK 25 不兼容的死路。

## 五、换到别的电脑怎么改

### 场景 A：目标机器是 JDK 17/21 + 常规 Android Studio

改 `gradle/libs.versions.toml`：

```toml
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
```

并做三件事：

1. `app/build.gradle.kts` 的 plugins 里把 `alias(libs.plugins.kotlin.android)` 加回来（AGP 8 需要）；
2. 根 `build.gradle.kts` 里的 `buildscript { classpath(kotlin-gradle-plugin...) }` 整段删掉；
3. `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 改回 `gradle-8.9-bin.zip`。

依赖版本（Compose BOM 2026.09、Room 2.8.5 等）在 JDK 17 上也能用，
但 `compileSdk = 37` 需要对应 SDK 平台，可降到 `36` 并按提示安装。

### 场景 B：只想在手机上装个包

不用改任何东西，直接用本机的 `build.bat` 打包，然后：

```
adb install -r D:\xiangmu\app\build\outputs\apk\debug\app-debug.apk
```

## 六、本机 SDK 现状（供参考）

```
%LOCALAPPDATA%\Android\Sdk
├── platforms/android-37.0        ← 唯一平台，所以 compileSdk=37
├── build-tools/36.0.0            ← 唯一构建工具
├── platform-tools / emulator / system-images
└── 没有 cmdline-tools（所以没法用 sdkmanager 装别的平台）
```

如果以后要装 SDK Platform 35/36，需要先补 `cmdline-tools`，再 `sdkmanager "platforms;android-36"`。

## 七、发版（打 tag → CI 出包）

推一个 `v*` 的 tag 就会触发 `.github/workflows/android-release.yml`：跑单元测试 → 出 release 包 → 建 Release 挂 APK。

```
# 1. 先改 app/build.gradle.kts 的 versionCode / versionName
# 2. 写这一版的更新说明（可选，但强烈建议）
#    文件名必须是 docs/releases/<tag>.md，例如 docs/releases/v1.0.3.md
# 3. 提交后打 tag 并推送
git commit -am "..." && git push origin main
git tag -a v1.0.3 -m "ClassFlow v1.0.3" && git push origin v1.0.3
```

- 有 `docs/releases/<tag>.md` 就用它当 Release 说明；没有才退回通用模板
  （通用模板里「这版有什么」是三句固定文字，看不出改了什么）。
- 重打同一个 tag 不会失败：workflow 会检测到 Release 已存在，改用 `gh release edit` + 覆盖上传 APK。
- 只出包不发版：在 Actions 页面手动 Run workflow，产物在 artifact 里。
