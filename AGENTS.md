# Polaris课程表项目规则

## 项目定位

Polaris课程表是面向学生的原生 Android Java 应用。核心能力是导入教务系统 PDF 课表、解析课程数据，并在手机和平板上展示周课表。

## 技术边界

- 保持原生 Android Java 项目，不迁移到 Web、React、Vue、Kotlin 或 Compose。
- 不把归档的 `preview.html` 作为 WebView 页面加载。
- 不引入大型依赖替代现有 PDFBox Android 解析路线。
- PDF 解析、课程数据模型和周课表 UI 是项目核心。

## 修改范围

- 不做一次性全量重构；一次只处理一个明确任务，不把解析、UI、存储和导出混在同一轮修改。
- 不删除 `MainActivity.java`、`Course.java`、`ScheduleParser.java`。修改这些文件时必须保持 PDF 导入和课表展示流程可用。
- 解析规则不确定时，先补充匿名样例或测试，再扩大实现范围。
- 改代码前说明目标、涉及文件和风险；完成后说明修改文件、验证结果和剩余风险。
- `docs/archive/preview.html` 仅作历史视觉参考，Android UI 修改无需同步它。

## 架构约束

- 结构化课程模型位于 `app/src/main/java/com/polaris/timetable/model/`；解析诊断位于 `parser/`。新增逻辑应沿用现有模型和稳定 ID 规则。
- 主 UI 颜色由 `ui/PolarisVisualTheme.java` 提供；`res/values/colors.xml` 只保存 widget、主题和图标使用的镜像 token。Java UI 不直接引用 `R.color`。
- 角半径和课表网格尺寸使用 `res/values/dimens.xml`，平板差异放在 `values-sw600dp/dimens.xml`，不要在新视图中写同类魔法数。
- `reminder/CourseReminderPopup` 固定浅色是覆盖窗设计；警示文字使用 `PolarisVisualTheme.warningColor(dark)`。
- 继续按用例渐进拆分 `MainActivity`，不要以压缩行数为目标整体搬迁代码。

## 构建与测试

- Debug 构建：`.\gradlew.bat :app:assembleDebug`
- 单元测试：`.\gradlew.bat :app:testDebugUnitTest`
- Lint：`.\gradlew.bat :app:lintDebug`，不得新增 error；代码内 lint 抑制必须带原因说明。
- 与 PDF 解析、迁移、UI 或更新流程有关的修改，还应运行对应的仪器测试或回归检查。
- APK 位于 `app/build/outputs/apk/debug/`。纯文档、测试或仓库整理不要求构建 APK。

## 版本号

- 改动应用代码并构建 APK 交付时，同时更新 `app/build.gradle` 的 `versionName` 和 `versionCode`。
- `versionName` 使用 `major.minor.patch`；`versionCode = major×10000 + minor×100 + patch`，且只能递增。
- 版本基准以 `app/build.gradle` 的实际值为准，不在本文维护当前版本流水账。
- 未构建 APK 的纯测试、文档或仓库整理不强制升版本。
