# Polaris课程表 迭代改进计划

> 制定日期：2026-08-31
> 基线：`versionName 1.15.2` / `versionCode 11502`，单元测试 275 个全绿，`assembleDebug` 与 `assembleRelease` 均可构建。
> 约束：遵守 `AGENTS.md` —— 原生 Android Java，不迁 Compose/Kotlin/Web；不一次性重构；不删除 `MainActivity.java`、`Course.java`、`ScheduleParser.java`；每轮只做一个明确任务；每次交付 APK 必须升版本。
> 说明：本文件取代已过期的 `docs/roadmap.md`（其阶段 1–5 已全部落地）。

---

## 一、现状体检

### 1.1 健康面（值得保持）

| 项 | 事实 |
|---|---|
| 构建 | `testDebugUnitTest` 275 个用例 0 失败；debug/release 双产物可构建 |
| 分层 | 12 个业务包（`model` / `parser` / `storage` / `time` / `ui` / `export` / `importer` / `reminder` / `sharing` / `statistics` / `validation` / `widget`），93 个源文件 27125 行 |
| 数据模型 | 已是结构化模型：`StructuredCourse` + `CourseMeeting` + `WeekRule` + `StableCourseId`/`StableMeetingId`，含迁移测试 |
| 测试 | 39 个单测文件 + 6 个 Espresso instrumented 测试，合计 5889 行 |
| 无障碍 | 字号已 sp 化（`spToPx` + `TypedValue.COMPLEX_UNIT_SP`），无 `setTextSize(dp(...))` 反模式 |
| 字符串 | `strings.xml` 653 行，UI 展示侧硬编码中文已从 750+ 收敛到 8 处 |
| 代码债标记 | `TODO`/`FIXME`/`HACK` 数量：0 |
| 仓库卫生 | `.gitignore` 正确排除 `keystore.properties` / `*.jks`，`git ls-files` 无敏感文件泄漏 |
| CI | 三 job：`build`（test + assembleDebug）、`instrumented`（模拟器）、`release`（tag 触发 + GitHub Release） |

### 1.2 问题面（按严重度排序）

**P0 — 文档与认知脱节（零技术风险，但会持续误导后续开发）**

| 事实 | 位置 | 后果 |
|---|---|---|
| 优先级仍写"稳定 PDF 导入 / 拆 MainActivity / 改进解析诊断 / 设计数据模型" | `AGENTS.md` 第 5 节 | 这四项实际已基本完成（模型已结构化、诊断已具备、UI 重构阶段 0–6 全绿），新会话会重复做已完成的事 |
| 阶段 1–5 全部已落地 | `docs/roadmap.md` | 路线图失效，无法指导排期 |
| 仍写"模型：根包下 `Course.java`" | `docs/architecture.md` | 实际 `model/` 已有 12 个类；根包仅剩 6 个遗留文件 |
| 阶段 0–6 全部标记已完成 | `docs/ui-refactor-plan.md` | 属于已归档的施工记录，且部分问题描述已失效（如"字号大量 dp"实际已 sp 化） |
| 描述 `preview.html` 作为参考 | `docs/desktop-preview.md` | `AGENTS.md` 已声明停止维护并不同步 |

**P1 — MainActivity 仍是巨石（8846 行，拆了 5 个 builder 只瘦了 197 行）**

| 维度 | 数据 |
|---|---|
| 规模 | 8846 行、约 300 个方法、158 个字段、5 个内部类/接口 |
| 对话框 | 53 个 `show*Dialog` 方法，合计 777 行（最长 `showChoiceDialog` 108 行 ×3 个重载） |
| 导入编排 | 27 个导入相关方法（PDF / AI / 分享 / 备份四条流），365 行 |
| UI 构建 | 12 个 `build*` 方法，233 行（最长 `buildPlanManageOverlay` 78 行） |
| 设置配置 | 46 个 `config*/setting*/apply*` 方法，281 行 |

补充：导入相关的**数据与算法层其实已抽出**（`importer/PdfImportCoordinator` 72 行 + `importer/ai/` 包 12 个类 1605 行），留在 Activity 里的是**用户决策流与对话框**，即 `MainActivity` 728–1190（AI 链路）与 1207–1620（PDF 审阅链）。

根因判断：上一轮拆的是**页面级 Builder**（我的页、设置页、底栏、对话框工厂），收益已经吃完。剩下的 8846 行不是"页面"，而是**用例编排 + 对话框集合 + 配置状态镜像**三块，需要换拆分维度才动得了。

**P2 — CI 可信度**

- `instrumented` job 被设为 `continue-on-error: true`，Espresso 测试失败不阻塞合并 —— 测试等于装饰。
- 连续 4 个 commit（`8b40872`→`3b70b24`）都在修 UTP / `EnumEntriesKt`，最终靠 `continue-on-error` 兜底，未定位根因。
- `gradle.properties` 中三行 UTP 开关（`android.experimental.androidTest.useUnifiedTestPlatform` / `android.experimental.testOptions.useUnifiedTestPlatform` / `android.useUnifiedTestPlatform`）属试探性残留，需核验哪一行真正生效，无效项应删除。

**P3 — 兼容性与本地化残留**

- `getIdentifier("status_bar_height")` 反射仍存 2 处：`MainActivity.java:8704`、`ui/CourseEditorDialog.java:1155`（阶段 2 只做了 fallback 令牌化，未真正移除）。
- `Gravity.LEFT` / `Gravity.RIGHT` 共 17 处，未用 `START/END`，RTL（`ar-XB`）存在破图风险。
- `isLandscapeTablet` 调用点 36 处：虽已抽出 `WindowSizeClass`，但调用仍遍布 Activity，平板分支难以统一调整。

**P4 — 依赖陈旧**

`pdfbox-android:2.0.27.0`、`constraintlayout:2.1.4`（已有 2.2.x）、`fragment:1.8.2`、`core:1.13.1`、`appcompat:1.7.0`。其中 pdfbox-android 是解析核心，升级需回归三校模板。

**P5 — 资产与工具链流失风险**

- `build/` 目录下 10 个本地化 / 资源追加 `.py` 脚本（如 `localize_mypage.py`、`append_export_resources.py`）被 `.gitignore` 排除，未入库。
- `polaris-ui-design/`（设计源文件）同样被忽略，未入库。
- 根目录 `preview.html`（66 KB）已停止维护但仍占位。

---

## 二、迭代计划

### 阶段 0：文档对齐（P0，不升版本）

纯文档改动，零代码风险，建议最先做——否则后续每轮都会基于错误前提开工。

| 动作 | 文件 | 内容 |
|---|---|---|
| 重写优先级 | `AGENTS.md` | 现状改为"PDF 导入与周课表展示已稳定；数据模型已结构化；UI 重构阶段 0–6 已完成"。新优先级指向本计划 P1–P3 |
| 标记归档 | `docs/roadmap.md` | 顶部注明"阶段 1–5 已于 1.4.0–1.15.2 全部落地，路线图由 `iteration-plan.md` 接替" |
| 对齐现状 | `docs/architecture.md` | "技术现状"改写为 12 包结构；补 `importer/ai`、`reminder`、`sharing`、`statistics`、`validation`、`widget` 六个包的职责；第 6 节分阶段迁移策略标注已完成 |
| 归档施工记录 | `docs/ui-refactor-plan.md` | 改名为 `docs/archive/ui-refactor-plan-1.15.1.md`，或顶部加"已于 1.15.1 竣工，仅作历史记录" |
| 清理失效文档 | `docs/desktop-preview.md` | 标注已废弃（`preview.html` 停止维护） |

验收：新会话读完 `AGENTS.md` + `docs/architecture.md` 能正确描述项目现状。
风险：无。

### 阶段 1：让 CI 说真话（P1，不升版本或 patch）

目标：把 instrumented 从"装饰"变回"门禁"。

1. **定位根因**：在本地或 CI 抓一次 `connectedDebugAndroidTest` 的真实堆栈，确认 `EnumEntriesKt` 缺失来自 UTP launcher 还是测试 APK 打包。已知当前规避手段是 `androidTestImplementation "org.jetbrains.kotlin:kotlin-stdlib:1.9.20"` + 关闭 UTP。
2. **收敛 `gradle.properties`**：逐个删掉三行 UTP 开关中未生效的行，只保留真正起作用的一项，并在注释里写清"为什么需要它、什么时候可以删"。
3. **摘掉 `continue-on-error: true`**：`.github/workflows/build.yml` 的 `instrumented` job 恢复为阻塞。若个别用例确有环境抖动（如模拟器冷启动超时），用 `@FlakyTest` 或按类拆分，而不是整体放行。

验收：连续 3 次 push 到 `main`，instrumented job 稳定通过且不依赖 `continue-on-error`。
风险：若根因未定位就摘掉兜底，会让 CI 长期飘红 —— 因此第 1 步是前置，未定位前保持现状。

### 阶段 2：MainActivity 第二轮瘦身（P1，每个小交付升 patch）

**换拆分维度**：不再按"页面"，改按"用例"和"状态"。五个独立小交付，每次只做一个，视觉零变化。

| 序 | 抽取目标 | 内容 | 预估 |
|---|---|---|---|
| 2-1 | `ui/dialog/*Dialogs` 三组 | 53 个 `show*Dialog`（777 行）按主题分组：课程与导入 / 外观与背景 / 提醒与权限。复用已有 `GlassDialogFactory` 与 `DialogWindowHelper` | -700 行 |
| 2-2 | `state/ScheduleViewState` | 158 个字段中"配置镜像"部分（配合 `applyConfig`/`saveConfig`，46 个方法 281 行）抽成独立状态持有者，Activity 委托读写 | -400 行 |
| 2-3 | `ui/page/PlanPageBuilder` | `buildPlanManageOverlay`（78 行）+ 计划页相关方法，对齐 `MyPageBuilder` / `SettingsPageBuilder` 的既有模式 | -150 行 |
| 2-4 | `importer/ai/AiImportFlow` | AI 导入整条链路（`MainActivity` 728–1190，约 460 行）：引导对话框 + 剪贴板读写 + 跳转外部 AI + 回跳接管 + 结果预览 + 落库 | -450 行 |
| 2-5 | `importer/PdfImportReviewFlow` | PDF 导入的对话框决策链（`MainActivity` 1207–1620，约 410 行）：覆盖确认 → 命名 → 解析审阅 → 落库 | -400 行 |

> **已核实的既有基础**：`importer/PdfImportCoordinator.java` 已存在，但只有 72 行，仅负责"执行解析 + 持久化 URI 读权限"这一步；`importer/ai/` 包下 12 个类已覆盖 JSON 解析、校验、映射、提示词与外跳回收。也就是说**数据与算法层已抽好，留在 Activity 里的是用户决策流与对话框**——这正是 2-4 / 2-5 要搬的东西，不是重做解析。

执行纪律：
- 每次只做一个子项，交付一次 patch 版本（1.15.3 → 1.15.4 → 1.15.5 → 1.15.6）。
- 每个子项完成后必须跑 `testDebugUnitTest` + `assembleDebug`，并按 `docs/ui-regression-checklist.md` 走一遍主路径（导入 PDF → 周切换 → 旋转 → 暗色）。
- Builder / Coordinator 通过回调接口与 Activity 通信，**禁止持有 Activity 强引用以外的生命周期对象**，重建时显式释放（沿用阶段 4 的防泄漏约定）。
- 目标：2.x 全部完成后 MainActivity 降至 6000 行以内；**不追求一次性清空**，随时可停。

风险：导入是项目主链路，抽取时最容易引入回归 —— **先做 2-1 / 2-3 练手，2-4 与 2-5 放在最后，且两者不要安排在同一轮**。每完成一项即跑 `testDebugUnitTest` + 走一遍真实导入（PDF 一份、AI 一份），确认无误再开下一项。

### 阶段 3：兼容性与本地化收尾（P3，patch）

1. **移除状态栏高度反射**：删除 `MainActivity.java:8704` 与 `CourseEditorDialog.java:1155` 的 `getIdentifier("status_bar_height")`，统一走阶段 2 已建立的 `OnApplyWindowInsetsListener` 真实 inset 路径。
2. **RTL 加固**：17 处 `Gravity.LEFT/RIGHT` → `START/END`，用 `ar-XB` 伪本地化逐页核对。
3. **平板分支收敛**：36 处 `isLandscapeTablet` 调用点评估是否可下沉到 `WindowSizeClass` 或各 Builder 内部，减少 Activity 内的条件散布。

验收：`docs/ui-regression-checklist.md` 第 8 节矩阵（fontScale 1.3/2.0、RTL、分屏、旋转、暗色四主题）全过。
风险：inset 改动可能引起各页 padding 双重叠加 —— 需逐页对比 `build/verify_*.png` 基线截图。

### 阶段 4：依赖升级（P4，minor，需回归）

顺序：先升与解析无关的 AndroidX 组件（`constraintlayout` → 2.2.x、`fragment` / `core` / `appcompat` 到当前稳定版），单独一个 patch 交付；确认无回归后，再单独评估 `pdfbox-android` 升级，必须回归三校模板（西安邮电 / 杭电 / 西安理工）各一份真实 PDF。

风险：`pdfbox-android` 是解析核心，跨版本升级可能改变文本坐标提取行为 —— 必须一校一测，不与其他改动混在同一轮。

### 阶段 5：资产与工具链入库（P5，不升版本）

- 把 `build/` 下 10 个 `.py` 脚本迁入 `tools/` 目录并入库（这些是本地化与资源生成的实际生产力工具，丢失后难以重建）。
- `polaris-ui-design/` 评估是否入库（若体积可控，建议纳入，避免设计源文件丢失）。
- `preview.html` 移入 `docs/archive/` 或删除，根目录只保留活跃资产。

---

## 三、版本策略

| 阶段 | 版本动作 |
|---|---|
| 0、1、3、5 | 不升版本（文档 / CI / 内部重构，未构建 APK 时不强制升版） |
| 2（每个子项） | patch 递增，1.15.3 起 |
| 4 | minor（1.16.0） |
| `versionCode` | 按 `major×10000 + minor×100 + patch` 映射，只增不减 |

每次构建 APK 交付前，必须先改 `app/build.gradle` 的 `versionName`/`versionCode`，再执行 `.\gradlew.bat :app:assembleDebug`。

---

## 四、明确不做的事

- 不迁移 Compose / Kotlin / WebView；不引入大型 UI 或状态管理依赖。
- 不删除 `MainActivity.java`、`Course.java`、`ScheduleParser.java`（只瘦身，禁止一次性重写）。
- 不做视觉推倒重来；本计划全程"视觉零变化"。
- 不为了拆而拆：阶段 2 的四个子项可随时停在任意一项，剩余部分维持现状是可接受的。
- 不在同一轮混合"解析改动 + UI 改动 + 存储改动"。
