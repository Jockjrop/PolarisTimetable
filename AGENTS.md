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
- 当前基准：`versionName "1.15.2"`，`versionCode 11502`。改版本时以 `app/build.gradle` 中的实际值为准。
- 构建命令：`.\gradlew.bat :app:assembleDebug`，产物位于 `app/build/outputs/apk/debug/`，文件名自带版本号（如 `Polaris-1.0.0-debug.apk`）。
- 未构建 APK 的纯代码改动（如仅改测试、文档）不强制升版本。

## 当前状态（2026-08-31 核对，版本 1.15.2）

以下四项已基本完成，不要再作为待办启动：

- **PDF 导入与周课表展示已稳定**：内置西安邮电 / 杭电 / 西安理工三校模板，另有 AI 识别导入与分享导入。
- **数据模型已结构化**：`model/` 下已有 `StructuredCourse`、`CourseMeeting`、`WeekRule`、`CourseTimeMode`、`StableCourseId`/`StableMeetingId`，支持多上课时间、单双周、项目周，并具备迁移测试。
- **解析诊断已具备**：`parser/` 下有 `ParseDiagnostics`、`ParseDiagnosticsReport`、`WeekRuleParser`、`SemesterTextExtractor`。
- **UI 重构阶段 0–6 已竣工**（1.13.18 → 1.15.1）：设计令牌、edge-to-edge、字符串资源化、`MainActivity` 页面级 Builder 拆分、设置页/我的页 XML + ViewBinding + Fragment 迁移、边界与可访问性验收。详见 `docs/ui-refactor-plan.md`（已归档）。

## 当前优先级

1. ✅ **文档对齐（2026-08-31 已完成）**：`docs/roadmap.md` 已标注归档、路线图由 `docs/iteration-plan.md` 接替；`docs/architecture.md` 技术现状与目录结构已同步；`docs/ui-refactor-plan.md` 已标注竣工。文档侧暂无待办。
2. **`MainActivity.java` 第二轮瘦身**：当前 8846 行 / 约 300 方法 / 158 字段。第一轮只拆出页面级 Builder（净减 197 行），剩余部分是**用例编排 + 53 个对话框 + 配置状态镜像**，必须按"用例"而非"页面"继续拆。步骤见 `docs/iteration-plan.md` 阶段 2。
3. **CI 可信度**：`instrumented` job 被设为 `continue-on-error: true`，UI 测试失败不阻塞合并；`gradle.properties` 中三行 UTP 开关为试探残留，待收敛。
4. **兼容性与本地化收尾**：移除 2 处 `getIdentifier("status_bar_height")` 反射（`MainActivity.java:8704`、`ui/CourseEditorDialog.java:1155`）；17 处 `Gravity.LEFT/RIGHT` 改 `START/END` 以适配 RTL。
5. **依赖升级**：`pdfbox-android` 等依赖陈旧，升级须按校回归解析模板。

> 注意：字号已 sp 化（`spToPx` + `TypedValue.COMPLEX_UNIT_SP`）。`docs/ui-refactor-plan.md` 中"字号大量 dp 绝对值导致 fontScale 失效"的描述已失效，不要据此改代码。
