# Polaris课程表项目规则

## 项目定位

Polaris课程表是一个原生 Android Java 课程表 App，面向学生使用。核心能力是导入教务系统 PDF 课表，解析课程数据，并以清晰、美观、适配手机和平板的周课表 UI 展示。

## 技术边界

- 必须保持原生 Android Java 项目形态。
- 不允许迁移到 Web、React、Vue、Kotlin 或 Compose。
- 不允许把 `preview.html` 作为 WebView 页面直接加载。
- 不允许引入大型依赖来替代当前 PDFBox Android 解析路线。
- PDF 解析、课程数据模型、周课表 UI 是当前项目核心。

## 代码保护规则

- 不允许一次性重构全部代码。
- 不允许删除现有 `MainActivity.java`、`Course.java`、`ScheduleParser.java`。
- 如需调整这些文件，必须保持现有导入 PDF 和课表展示流程可用。
- 每次只做一个明确任务，避免把解析、UI、存储、导出混在同一轮修改。

## 工作流程

- 先设计，后实现。
- 改代码前必须说明计划，包括目标、涉及文件、预期风险。
- 改完必须说明修改文件和风险。
- 对不确定的解析规则，要优先补充设计或测试样例，不要直接扩大改动范围。
- 保持阶段式推进：先稳定当前功能，再优化解析，再拆 UI，再做保存、编辑、导出、分享和提醒。
- 根目录 `preview.html` 已停止维护，仅作为历史视觉参考保留。
- 后续修改 Android 交互页面、布局、导航、弹窗或课程表视觉时，不再要求同步更新 `preview.html`。

## 版本号与构建

- **每次改动代码并构建 APK 交付时，必须同时更新版本号**（`app/build.gradle`）。
- 版本号遵循语义化版本 `major.minor.patch`，`versionCode` 按 `major×10000 + minor×100 + patch` 映射，只能递增不能回退。
- 示例：`1.0.0 → versionCode 10000`；下一次交付 `1.0.1 → versionCode 10001`；功能增强 `1.1.0 → versionCode 10100`。
- 当前基准：`versionName "1.27.0"`，`versionCode 12700`。改版本时以 `app/build.gradle` 中的实际值为准。
- 构建命令：`.\gradlew.bat :app:assembleDebug`，产物位于 `app/build/outputs/apk/debug/`，文件名自带版本号（如 `Polaris-1.0.0-debug.apk`）。
- 未构建 APK 的纯代码改动（如仅改测试、文档）不强制升版本。

## 当前状态（2026-09-03 核对，版本 1.24.1）

以下各项已基本完成，不要再作为待办启动：

- **PDF 导入与周课表展示已稳定**：内置西安邮电 / 杭电 / 西安理工三校模板，另有 AI 识别导入与分享导入。
- **数据模型已结构化**：`model/` 下已有 `StructuredCourse`、`CourseMeeting`、`WeekRule`、`CourseTimeMode`、`StableCourseId`/`StableMeetingId`，支持多上课时间、单双周、项目周，并具备迁移测试。
- **解析诊断已具备**：`parser/` 下有 `ParseDiagnostics`、`ParseDiagnosticsReport`、`WeekRuleParser`、`SemesterTextExtractor`。
- **UI 重构阶段 0–6 已竣工**（1.13.18 → 1.15.1）：设计令牌、edge-to-edge、字符串资源化、`MainActivity` 页面级 Builder 拆分、设置页/我的页 XML + ViewBinding + Fragment 迁移、边界与可访问性验收。施工记录已归档移除。
- **颜色/形状 token 架构已确立（2026-09-02 M3 审计后落地，1.16.6/1.16.7）**：主 UI 颜色 token 源是 `ui/PolarisVisualTheme.java`（4 风格 × 明暗），`res/values/colors.xml` 仅保留 widget/主题/图标实际引用的镜像 token，Java UI 不引用 `R.color` 是既定形态；组件专属色（如开关）用类内命名常量；`reminder/CourseReminderPopup` 固定浅色卡片是覆盖窗的设计意图，勿"修"成主题化；警示文案色统一走 `PolarisVisualTheme.warningColor(dark)`。形状角半径与课表网格断点尺寸在 `values/dimens.xml`（平板覆盖 `values-sw600dp/dimens.xml`），新视图勿写魔法数。
- **Lint 基线已入 CI（2026-09-03，1.19.1）**：`lintDebug` 0 error 即门槛，新增 error 阻塞合并。警告基线 3 条（bouncycastle 依赖内部 TrustAllX509TrustManager，随依赖升级线处理）；代码内 `@SuppressLint`/`tools:ignore` 必须带理由注释（按压反馈装饰、格式示例文案、有意贴边等），勿无说明压制。
- **外评估 P0–P3 已落地（2026-09-03，1.19.1→1.21.0）**，勿再作为待办启动：质量基线（lint 清零入 CI + PDF 护栏两层真实化——合成文本层硬断言必跑、真三校样例按基线表校验）；CI 前置（PR 跑 build job + 仪器 smoke，push main 跑全量仪器）；多课表 Widget（每实例经 `ScheduleWidgetConfigActivity` 独立绑定课表，配置存 `polaris_widget_config` 按 widgetId，渲染回退激活课表）；新学期向导（⋯菜单，模板复制配置 + 日期锚定 + 切换 + 自动进入 PDF 导入）。

## 当前优先级（2026-09-05 核对）

旧五项全部完成，勿再启动：① 文档对齐（2026-08-31）② MainActivity 第二轮瘦身（1.15.3–1.15.9 收官，8846→6309 行；1.16.x 回升至 6899 行属正常增长）③ CI 可信度（2026-09-01 闭环：continue-on-error 已摘、UTP 开关已清；2026-09-03 起仪器测试 PR 跑 smoke、push main 跑全量，失败均阻塞合并）④ 兼容性与本地化（getIdentifier/Gravity 均已清零）⑤ 依赖升级（AndroidX 已升 1.16.0；pdfbox 评估结论为无版本可升，三校 PDF 回归护栏已入库）。

- **计划页悬浮新增菜单已落地（2026-09-04，1.26.0/12600）**，勿再作为待办启动：整宽「＋ 新建计划」移除，新增统一收敛到右下角悬浮加号（`ui/page/PlanAddMenuView.java`，手机计划页与横屏平板计划管理浮层各持实例，不共享视图引用）；FAB 按底栏实际高度 + 完整 WindowInsets 动态定位、列表底部留白动态预留；事件编辑器支持预选考试/截止类型（`AcademicEventDialogs.showEditorDialog(existing, presetType)`）；新增仪器测试 `PlanAddMenuTest` 并更新 `BottomNavNavigationTest`；lint 0 error、单测通过、debug APK 已归档 `output/`。规划文档 `docs/plans/plan-page-fab-optimization-plan.md`。

- **应用内更新系统已落地并完成实施后改进轮（2026-09-04，1.27.0/12700，方案 B + 改进计划 A–D 阶段）**，勿再作为待办启动：客户端 `update/` 包（清单协议严格类型解析、HTTPS 白名单仓库、版本策略、偏好、进程内下载控制器 + 四层校验、协调器进程级单例），入口在「更多 → 关于」（检查更新行 + 自动检查开关，默认关闭，24h 节流）；**安装链已迁移 PackageInstaller Session**（U-P0-02）：移除 ACTION_INSTALL_PACKAGE/FileProvider/queries，显式广播接收器回传状态，后台收到确认 Intent 暂存 URI 返回前台启动；待安装 APK 存 `filesDir/updates` 并持久化大小+SHA-256，恢复时完整复验（U-P1-01/02）；下载目录显式创建（U-P0-01）、超声明大小即断（U-P1-04）、任务 ID 防重建误删（U-P1-05）、协议严格 String/整数/版本段 0..99（U-P1-07/08/09）、重定向恰好 5 次（U-P1-10）、`usesCleartextTraffic=false`（U-P1-13）；测试隔离：单例可注入假仓库/网关，仪器测试零公网（U-P1-14）；发布流水线：Debug fallback 封死、签名 Secret + 证书指纹核对、**历史清单查询区分 200/404/故障不得降级为 0（U-P0-03，404 走审计基线 12601）**、**Draft 上传 → 三资产复验 → publish --latest 原子发布（U-P0-04）**、tag 依赖仪器 smoke（U-P1-17）；单测（解析/策略/下载/偏好/仓库/协调器）+ 仪器（入口零网络/安装构件/Smoke）。规划文档 `docs/plans/app-self-update-plan.md` + `docs/plans/app-self-update-improvement-plan.md`。**发布 v1.27.0 稳定版前的人工前置：① 配置 Secret `POLARIS_RELEASE_CERT_SHA256` 并离线备份签名材料；② 完成阶段 E 真机正式签名覆盖升级验收与 Android 开发者验证登记（U-P0-05）。**

历史迭代路线与进度台账文档（autonomous-iteration / iteration-plan / roadmap / ui-refactor-plan / desktop-preview）已于 2026-09-05 移除，其记录的工作均已收官，勿再据此排期。剩余候选：学校模板扩展（P5，一校一迭代，先匿名样例）。考试/DDL 余项（绝对日期提醒调度、今日概览联动、课表网格标注）与多课表 Widget 余项（今日/明日独立模式、紧凑布局）可选。长期结构债：MainActivity 约 6400 行，继续按"用例"维度渐进拆分，勿一次性重构。

> 注意：字号已 sp 化（`spToPx` + `TypedValue.COMPLEX_UNIT_SP`）。"字号大量 dp 绝对值导致 fontScale 失效"为历史描述，勿据此改代码。
