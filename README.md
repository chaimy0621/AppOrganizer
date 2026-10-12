# 智能桌面（App Organizer Launcher）MVP

一个可安装在 Samsung Galaxy S24 上的 Android Launcher：按使用频率显示常用应用、自动建立智能文件夹，所有数据只保存在本机，APK 不含网络权限。

设计说明（项目审查、架构、Room 结构、排序与分类算法、权限）见 [`docs/DESIGN.md`](docs/DESIGN.md)。

## 环境要求

- Android Studio Ladybug（2024.2）或更新版本，自带 JDK 17+
- Android SDK Platform 35
- 手机：Android 10 及以上（S24 出厂 Android 14，满足）

## 1. 打开与调试运行

1. Android Studio → **Open** → 选择 `AppOrganizer` 文件夹，等待 Gradle Sync 完成（首次会下载 Gradle 8.9 和依赖）。
   - 如果 Studio 提示升级 AGP / Kotlin，可以先忽略，当前版本组合已对齐：AGP 8.7.3 + Kotlin 2.0.21 + KSP 2.0.21-1.0.28。
2. 手机：设置 → 关于手机 → 软件信息 → 连续点 7 次「编译编号」开启开发者选项 → 开发者选项中打开 **USB 调试**。
3. 连接手机，选择 `app` 配置，点 ▶ Run。调试版包名为 `com.apporganizer.launcher.debug`，可与正式版共存。

命令行等价：

```bash
./gradlew testDebugUnitTest      # 运行排序和分类单元测试
./gradlew assembleDebug          # 输出 app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 2. 生成签名密钥（只需一次）

```bash
mkdir -p release
keytool -genkeypair -v \
  -keystore release/apporganizer-release.jks \
  -alias apporganizer \
  -keyalg RSA -keysize 4096 -validity 10000
```

然后：

```bash
cp keystore.properties.example keystore.properties
# 编辑 keystore.properties，填入上一步设置的密码
```

> `keystore.properties`、`*.jks` 已加入 `.gitignore`。**请备份 .jks 文件和密码**：丢失后无法用同一签名更新已安装的应用。

## 3. 构建签名的 Release APK

```bash
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/app-release.apk
```

`app/build.gradle.kts` 会自动读取 `keystore.properties` 并签名（v1+v2+v3）。没有该文件时会生成未签名 APK，无法安装。

验证签名与权限：

```bash
# apksigner 位于 $ANDROID_HOME/build-tools/<版本>/
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
# 预期只有 PACKAGE_USAGE_STATS（以及 AndroidX 自动添加的 DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION 等内部权限），没有 INTERNET
```

也可在 Android Studio 中：**Build → Generate Signed App Bundle / APK → APK**，选择上面的 .jks。

## 4. 安装到 S24

- USB：`adb install -r app-release.apk`
- 或把 APK 传到手机，用「我的文件」打开安装（需允许“安装未知应用”）。三星可能弹出“自动拦截器”提示，需在 设置 → 安全与隐私 → 自动拦截器 中暂时关闭。

首次打开会进入六屏向导：欢迎 → 使用情况访问（可跳过）→ 扫描应用 → 选择布局 → 设为默认桌面（可跳过）→ 完成。

**建议**：设置 → 应用 → 智能桌面 → 电池 → 选择「不受限制」，保证每日排序任务按时运行（即使不设置，打开桌面时也会补算）。

## 5. 恢复 One UI Home

任选其一：

- 本应用 设置 → **切换回 One UI Home**
- 系统 设置 → 应用 → 选择默认应用 → 主屏幕应用 → **One UI 主屏幕**
- 直接卸载本应用，系统会自动回到 One UI Home

One UI Home 的布局不会被本应用修改。

## 项目结构

```
app/src/main/java/com/apporganizer/launcher/
  domain/   Category · Classifier · RankingEngine（纯 Kotlin，有单元测试）
  data/     AppRepository · Room · DataStore · LauncherApps/UsageStats 封装
  work/     DailyRankingWorker（每天一次）
  ui/       MainActivity · HomeViewModel · home/ onboarding/ settings/ components/
app/src/test/  RankingEngineTest · ClassifierTest
docs/DESIGN.md
```

## 隐私

- Manifest 中通过 `tools:node="remove"` 强制移除 `INTERNET` / `ACCESS_NETWORK_STATE`
- 无遥测、无崩溃上报、无广告 SDK
- `allowBackup=false`，并禁止设备间数据迁移
- 只读取应用启动时间，不读取通知、聊天、文件或屏幕内容
