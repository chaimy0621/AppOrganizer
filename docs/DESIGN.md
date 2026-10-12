# 智能桌面（App Organizer Launcher）设计文档

版本：0.1.0-mvp　目标设备：Samsung Galaxy S24（One UI 6/7/8，Android 14–16）

---

## 1. 参考项目审查

数据来自 2026-10-12 查看的 GitHub 仓库页面。页面未显示最后提交日期，活跃度以提交数、Star 数和维护状态估计。

| 项目 | License | 规模 / 活跃度 | 技术栈 | 可借鉴 | 能否直接复用代码 |
|---|---|---|---|---|---|
| **Ratio Launcher** | **未声明 License**（README 只写“独立复刻，教学用途”） | 1★，78 commits，单人项目，有 CI | Kotlin，单 `app` 模块，minSdk 26 / SDK 36 | 自动分类抽屉、自定义类别、Dock、备份恢复 | **不能**。无 License = 默认保留所有权利，Fork 后无法合法分发 |
| **Lawnchair** | GPL-3.0（基于 Apache-2.0 的 AOSP Launcher3） | 13.5k★，1.7k forks，非常活跃 | Kotlin + Java，Launcher3 fork，混合 Soong/Gradle，含 systemui/wmshell 子模块 | 成熟桌面架构、图标网格、文件夹、拖放、图标包 | 可以，但整个项目须 GPL-3.0 开源；`16-dev` 分支官方标注不稳定 |
| **android-app-organizer** | MIT | 1★，1 commit | Python + UIAutomator2/ADB + Kotlin Helper | 分类规则思路、dry-run/回滚设计 | 思路可参考；ADB 拖动 One UI 图标的方案与本项目原则相悖 |
| **Slate Minimal Launcher** | MIT | 29★，141 commits | Android / Gradle KTS | 按使用次数排序、固定置顶、隐藏应用 | 可以（保留版权声明） |
| **FuseLauncher** | MIT | 9★，35 commits | **Flutter** | 带衰减的使用频率排序 | 概念可参考；语言不同，代码无法直接用 |
| **LaunchTime** | GPL-3.0 | 82★，1014 commits | Android / Java | 首次运行与新装应用自动分类、用户修正 | 复制代码会把项目“传染”为 GPL |
| **Apps2Org** | 未声明 | 15★，190 commits，旧 Ant 工程 | Java | 多标签分类模型 | 不能复用代码，只参考概念 |

### 结论：从零开发（Compose），借鉴概念，不 Fork

| 选项 | 优点 | 主要问题 |
|---|---|---|
| Fork Ratio Launcher | 功能最接近 | **没有 License，法律上不能 Fork 分发**；单人维护 |
| Fork Lawnchair | 桌面体验最完整 | 代码量巨大（8.7 万次提交），跟随 AOSP 每年大版本合并；GPL-3.0；构建依赖 AOSP 预编译库；改造“每日排序 + 智能文件夹”需要深入 Launcher3 的 Workspace/Model 层，MVP 成本过高 |
| **从零开发（已采用）** | 代码完全自有、可选任意 License；Compose 体量小（约 2.6k 行 Kotlin）；架构正好贴合“单一数据源驱动首页/常用区/文件夹” | 拖放、Widget、图标包等高级桌面功能需后续自行实现 |

后续如需完整的拖放桌面网格，可单独参考 Lawnchair/Launcher3 的实现（注意 GPL 边界：参考设计，不复制代码）。MIT 项目（Slate）的代码可在保留版权声明的前提下复用。

---

## 2. 项目架构与模块划分

MVP 使用**单一 `:app` 模块 + 分层包**，降低 Gradle 复杂度；包边界与未来模块一一对应，可直接拆分。

```
app/src/main/java/com/apporganizer/launcher/
├── App.kt                    Application + AppContainer（手动 DI）
├── domain/                   纯 Kotlin，无 Android 依赖 → 未来 :core:domain
│   ├── Category.kt           9 个类别
│   ├── Classifier.kt         离线分类规则
│   └── RankingEngine.kt      评分 + 常用区排列
├── data/                     → 未来 :core:data
│   ├── AppRepository.kt      唯一数据入口（扫描、使用记录、排序、用户操作）
│   ├── db/                   Room：Entities / Daos / AppDatabase
│   ├── prefs/SettingsStore   DataStore：向导状态、布局、自动调整开关
│   └── system/               LauncherApps / UsageStatsManager / 图标缓存
├── work/DailyRankingWorker   WorkManager 每日任务 → 未来 :core:work
└── ui/                       Jetpack Compose → 未来 :feature:home / :feature:onboarding / :feature:settings
    ├── MainActivity          HOME Activity，处理 Home 键
    ├── HomeViewModel         把 Room 数据组合成首页状态
    ├── home/                 首页：常用区、智能文件夹、Dock、搜索、长按菜单
    ├── onboarding/           六屏向导
    ├── settings/             设置页
    ├── components/           图标、对话框
    └── theme/
```

数据流（单一数据源）：

```
LauncherApps ─┐                          ┌─► 常用区（arrange(scores, locks)）
UsageStats ───┼─► AppRepository ─► Room ─┼─► 智能文件夹（userFolder > 最近安装 > autoCategory）
内部点击 ─────┘        ▲                 └─► Dock（dockSlot）
                DailyRankingWorker（每天一次写 scores）
```

建议的拆分（v0.3 起，当团队或代码量增长时）：`:core:domain`（纯 JVM）、`:core:data`、`:core:work`、`:feature:home`、`:feature:onboarding`、`:feature:settings`、`:app`。

---

## 3. Room 数据结构

| 表 | 主键 | 字段 | 说明 |
|---|---|---|---|
| `apps` | `appKey` = `包名/Activity类名` | packageName, className, label, installedAt, firstSeenAt, autoCategory, **userFolderId**, **lockedSlot**, **dockSlot**, **excludedFromRanking** | 每个可启动 Activity 一行。四个加粗字段是用户控制，扫描时永远保留 |
| `folders` | `id` 自增 | name, category(唯一，可空), sortOrder, deleted | category 非空 = 智能文件夹；null = 用户自建。智能文件夹删除时只标记 `deleted`，防止重新扫描时复活 |
| `launch_events` | `id` 自增 | packageName, at, source | 原始启动事件，唯一索引 (packageName, at, source) 防重复；source 0=系统 1=内部点击；保留 35 天 |
| `scores` | `appKey` | score, lastUsedAt, computedAt | **每日分数快照**。首页只读快照，所以一天内未锁定图标不会移动 |

为什么存原始事件而不是每日汇总：系统 UsageStats 事件只保留数天，而且 24 小时窗口需要精确时间。每天约 50–300 条，30 天也只有数千行。

DataStore：`onboarding_done`、`layout_mode`、`auto_adjust`、`last_ranking_at`、`last_ingest_at`、`dock_initialized`。

---

## 4. 排序算法

```
score = 5 × 近24小时启动 + 2 × 近7天启动 + 0.5 × 近30天启动 + 最近使用加分
最近使用加分 = 6 × 0.5^(距上次使用小时数 / 24)        # 每 24 小时减半，上限 6
稳定性：昨天已在常用区的应用 × 1.10                   # 减少边界应用来回跳
```

窗口有意重叠（今天的启动同时计入 24h、7d、30d），所以近期行为权重最高：一次今天的启动 ≈ 7.5 分，25 天前的一次 ≈ 0.5 分，旧应用不会长期霸榜。

**“一次启动”的定义**：UsageEvents 中 `ACTIVITY_RESUMED` 的包名与上一个前台包不同；同一应用内切换页面不重复计数；锁屏/熄屏后回到同一应用算新一次。

**常用区排列**（`RankingEngine.arrange`）：
1. 锁定应用放在 `lockedSlot`（超出当前布局格数的锁定被忽略）。
2. 剩余位置按分数降序填充；同分按最近使用、名称排序。
3. 被排除、已在 Dock、分数为 0 的应用不会被自动放入。

**时间节奏**：WorkManager 每天约凌晨 4 点运行（同步应用 → 写入事件 → 重算分数）。三星可能延迟后台任务，所以打开桌面时若距上次计算超过 26 小时会在前台补算。关闭“自动调整”后分数冻结。

**降级**：未授权 Usage Access → 使用 source=1 的内部点击事件，首页显示“内部学习模式”标签。

---

## 5. 自动分类规则

优先级（高→低）：

1. **用户手动分类**（`userFolderId`）——永远不被覆盖；“恢复默认分类”才会清除
2. 精确包名映射表（约 70 个常见 App，包括 Shopee、Lazada、Grab、TNG eWallet、Maybank 等马来西亚常用应用）
3. 包名前缀（`com.microsoft.office*`、`com.maybank2u*`、`com.cimb*` 等）
4. `FLAG_IS_GAME` / `ApplicationInfo.category`（GAME→游戏，AUDIO/VIDEO/IMAGE/NEWS→影音，SOCIAL→通讯，MAPS→出行，PRODUCTIVITY→工作，ACCESSIBILITY→工具）
5. 关键词（包名 + 应用名，中英文）：≤3 字母的英文词只做整词匹配（避免 `tv` 命中 `activity`）；中文做子串匹配；按 金融→通讯→工作→购物→出行→影音→游戏→工具 顺序，先命中先返回
6. 其他

文件夹归属：手动文件夹 > “最近安装”（安装 7 天内）> 智能文件夹（autoCategory）> 其他。

---

## 6. 权限与降级

| 权限 | 用途 | 拒绝后 |
|---|---|---|
| Usage Access（`PACKAGE_USAGE_STATS`，用户在系统设置中开启） | 读取启动次数和最后使用时间 | 内部学习模式：只统计本桌面内的点击 |
| ROLE_HOME（RoleManager） | 成为默认桌面 | 仍可作为普通 App 打开；设置中可再次申请 |
| `<queries>` LAUNCHER/DIAL/IMAGE_CAPTURE/VIEW https/whatsapp | Android 11+ 包可见性，读取可启动应用和默认 Dock | 不属于运行时权限 |

**明确不申请**：Accessibility、Contacts、SMS、存储、Overlay、Location、`QUERY_ALL_PACKAGES`。
**`INTERNET` 通过 `tools:node="remove"` 从合并后的 Manifest 中强制移除**，任何依赖库都无法带入网络访问。
`allowBackup=false` + `data_extraction_rules` 禁止云备份与设备间迁移，数据只留在本机。

---

## 7. MVP 范围对照

| 需求 | 状态 |
|---|---|
| 六屏安装向导 | ✅ 欢迎 / 权限 / 扫描 / 布局 / 默认桌面 / 完成 |
| 请求 Usage Access，解释并可拒绝 | ✅ |
| 请求成为默认 Launcher（RoleManager） | ✅ |
| 读取可启动应用并监听安装/卸载 | ✅ LauncherApps + Callback |
| 8 个动态常用应用（布局可选 4/8/12） | ✅ |
| 自动建立分类文件夹 + 最近安装 | ✅ |
| 锁定位置、移动文件夹、排除排序、Dock 增删 | ✅ 长按菜单 |
| 文件夹创建 / 改名 / 删除 | ✅ |
| 恢复默认分类、重新扫描 | ✅ 设置页 |
| 每天重算一次 | ✅ WorkManager + 前台补算 |
| 返回 One UI Home 说明入口 | ✅ 向导完成页 + 设置页 |

**已知限制（MVP）**：不支持拖放排序；不显示工作资料 / 安全文件夹中的应用；没有 Widget；文件夹内部顺序按分数自动排列。

---

## 8. S24 真机测试清单

1. 安装 → 向导 → 拒绝 Usage Access → 首页显示“内部学习模式”
2. 设置 → 授权 → 返回，标签消失；“立即重新整理”后常用区出现应用
3. 设为默认桌面，按 Home 返回；在首页打开文件夹后按 Home，文件夹关闭
4. 长按常用区应用 → 锁定 → “立即重新整理”后位置不变
5. 移动应用到自建文件夹 → “重新扫描应用”后仍在该文件夹
6. 安装新应用 → 出现在“最近安装”
7. 设置 → 切换回 One UI Home → 选择 One UI 主屏幕，确认恢复
8. 设置 → 电池 → 将本应用设为“不受限制”，观察次日 4 点后是否自动重排
9. 用 `aapt2 dump permissions` 或 Android Studio APK Analyzer 确认 APK 中**没有** INTERNET 权限

---

## 9. 后续路线

- v0.2：拖放排序、文件夹内手动排序、图标包、本地 JSON 导出/导入备份
- v0.3：工作资料（`LauncherApps.getProfiles()`）、Widget、模块化拆分
- 可选（须独立开关 + 隐私说明）：在线分类、天气
