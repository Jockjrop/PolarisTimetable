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
- 当前基准：`versionName "1.0.0"`，`versionCode 10000`。
- 构建命令：`.\gradlew.bat :app:assembleDebug`，产物位于 `app/build/outputs/apk/debug/`，文件名自带版本号（如 `Polaris-1.0.0-debug.apk`）。
- 未构建 APK 的纯代码改动（如仅改测试、文档）不强制升版本。

## 当前优先级

1. 稳定现有 PDF 导入和周课表展示。
2. 拆清 `MainActivity.java` 中 UI、状态、导入、解析调度的职责。
3. 改进 `ScheduleParser.java` 的诊断能力和周次解析。
4. 设计并逐步引入可支持多上课时间、单双周、项目周和置信度的课程数据模型。
5. 继续在 Android 原生 UI 内完善清爽蓝色、卡片课程块、横向周视图，不再依赖或维护 `preview.html`。
