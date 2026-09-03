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
- 当前基准：`versionName "1.21.0"`，`versionCode 12100`。改版本时以 `app/build.gradle` 中的实际值为准。
- 构建命令：`.\gradlew.bat :app:assembleDebug`，产物位于 `app/build/outputs/apk/debug/`，文件名自带版本号（如 `Polaris-1.0.0-debug.apk`）。
- 未构建 APK 的纯代码改动（如仅改测试、文档）不强制升版本。

## 当前状态（2026-09-03 核对，版本 1.21.0）

以下各项已基本完成，不要再作为待办启动：

- **PDF 导入与周课表展示已稳定**：内置西安邮电 / 杭电 / 西安理工三校模板，另有 AI 识别导入与分享导入。
- **数据模型已结构化**：`model/` 下已有 `StructuredCourse`、`CourseMeeting`、`WeekRule`、`CourseTimeMode`、`StableCourseId`/`StableMeetingId`，支持多上课时间、单双周、项目周，并具备迁移测试。
- **解析诊断已具备**：`parser/` 下有 `ParseDiagnostics`、`ParseDiagnosticsReport`、`WeekRuleParser`、`SemesterTextExtractor`。
- **UI 重构阶段 0–6 已竣工**（1.13.18 → 1.15.1）：设计令牌、edge-to-edge、字符串资源化、`MainActivity` 页面级 Builder 拆分、设置页/我的页 XML + ViewBinding + Fragment 迁移、边界与可访问性验收。详见 `docs/ui-refactor-plan.md`（已归档）。
- **颜色/形状 token 架构已确立（2026-09-02 M3 审计后落地，1.16.6/1.16.7）**：主 UI 颜色 token 源是 `ui/PolarisVisualTheme.java`（4 风格 × 明暗），`res/values/colors.xml` 仅保留 widget/主题/图标实际引用的镜像 token，Java UI 不引用 `R.color` 是既定形态；组件专属色（如开关）用类内命名常量；`reminder/CourseReminderPopup` 固定浅色卡片是覆盖窗的设计意图，勿"修"成主题化；警示文案色统一走 `PolarisVisualTheme.warningColor(dark)`。形状角半径与课表网格断点尺寸在 `values/dimens.xml`（平板覆盖 `values-sw600dp/dimens.xml`），新视图勿写魔法数。
- **Lint 基线已入 CI（2026-09-03，1.19.1）**：`lintDebug` 0 error 即门槛，新增 error 阻塞合并。警告基线 3 条（bouncycastle 依赖内部 TrustAllX509TrustManager，随依赖升级线处理）；代码内 `@SuppressLint`/`tools:ignore` 必须带理由注释（按压反馈装饰、格式示例文案、有意贴边等），勿无说明压制。
- **外评估 P0–P3 已落地（2026-09-03，1.19.1→1.21.0）**，勿再作为待办启动：质量基线（lint 清零入 CI + PDF 护栏两层真实化——合成文本层硬断言必跑、真三校样例按基线表校验）；CI 前置（PR 跑 build job + 仪器 smoke，push main 跑全量仪器）；多课表 Widget（每实例经 `ScheduleWidgetConfigActivity` 独立绑定课表，配置存 `polaris_widget_config` 按 widgetId，渲染回退激活课表）；新学期向导（⋯菜单，模板复制配置 + 日期锚定 + 切换 + 自动进入 PDF 导入）。

## 当前优先级（2026-09-03 核对，iteration-plan 阶段 0–5 已全部收官）

旧五项全部完成，勿再启动：① 文档对齐（2026-08-31）② MainActivity 第二轮瘦身（1.15.3–1.15.9 收官，8846→6309 行；1.16.x 回升至 6899 行属正常增长）③ CI 可信度（2026-09-01 闭环：continue-on-error 已摘、UTP 开关已清；2026-09-03 起仪器测试 PR 跑 smoke、push main 跑全量，失败均阻塞合并）④ 兼容性与本地化（getIdentifier/Gravity 均已清零）⑤ 依赖升级（AndroidX 已升 1.16.0；pdfbox 评估结论为无版本可升，三校 PDF 回归护栏已入库）。

当前工作主线见 `docs/autonomous-iteration-2026-09.md`（自主迭代路线与进度台账）。截至 2026-09-03：台账内 P0–P9（测试护栏、指示线、时段高亮、空闲速查、日期对齐、widget 高亮、质量基线、多课表 Widget、新学期向导）全部收官。剩余候选按评估优先级：**考试/DDL 时间线**（P4，需新建 `AcademicEvent` 数据模型——绝对日期/地点/座位/截止/状态，动工前先与用户对齐设计）→ 学校模板扩展（P5，一校一迭代，先匿名样例）→ 本地诊断包（P6）。多课表 Widget 余项（今日/明日独立模式、紧凑布局）可选。长期结构债：MainActivity 约 6300 行，继续按"用例"维度渐进拆分，勿一次性重构。

> 注意：字号已 sp 化（`spToPx` + `TypedValue.COMPLEX_UNIT_SP`）。`docs/ui-refactor-plan.md` 中"字号大量 dp 绝对值导致 fontScale 失效"的描述已失效，不要据此改代码。
