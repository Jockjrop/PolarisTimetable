# Polaris课程表技术架构设计

## 1. 架构目标

当前项目可用但集中在 `MainActivity.java`。架构设计目标是逐步分层，而不是一次性重构：

- 保持原生 Android Java。
- 保持 PDFBox Android 解析方案。
- 不迁移 Kotlin、Compose、Web 或后端。
- 不引入大型依赖。
- 先拆职责，再补能力。

## 2. 当前技术现状

- Android Gradle 插件应用模块。
- `compileSdk 35`，`minSdk 23`，`targetSdk 35`。
- 依赖：`com.tom-roush:pdfbox-android:2.0.27.0`。
- 单 Activity：`MainActivity`。
- 模型：根包下 `Course`。
- 解析：根包下 `ScheduleParser`。
- UI：全部用 Java 动态创建原生 View。
- Manifest 支持 Launcher 和 PDF VIEW intent。

## 3. 建议目录结构

```text
app/src/main/java/com/polaris/timetable/
├─ MainActivity.java
├─ model/
│  ├─ Course.java
│  ├─ CourseMeeting.java
│  ├─ Semester.java
│  ├─ WeekRule.java
│  ├─ ParseResult.java
│  ├─ ParseError.java
│  ├─ ScheduleState.java
│  └─ UserSettings.java
├─ parser/
│  ├─ ScheduleParser.java
│  ├─ PdfTextExtractor.java
│  ├─ TextBlock.java
│  ├─ DayColumnDetector.java
│  ├─ SectionDetector.java
│  ├─ CourseBlockParser.java
│  ├─ WeekRuleParser.java
│  ├─ CourseMerger.java
│  └─ ParseDiagnostics.java
├─ ui/
│  ├─ ScheduleBoardView.java
│  ├─ ScheduleHeaderView.java
│  ├─ TodayOverviewView.java
│  ├─ CourseDetailDialog.java
│  ├─ ParseResultConfirmView.java
│  ├─ EmptyStateView.java
│  ├─ ErrorStateView.java
│  └─ SettingsView.java
├─ export/
│  ├─ ScheduleImageExporter.java
│  ├─ SchedulePdfExporter.java
│  └─ ShareHelper.java
├─ storage/
│  ├─ ScheduleRepository.java
│  ├─ SettingsRepository.java
│  └─ LocalJsonStore.java
└─ util/
   ├─ ColorUtil.java
   ├─ DateUtil.java
   ├─ DimensionUtil.java
   └─ CourseTimeUtil.java
```

## 4. 模块职责

### model

负责数据结构，不依赖 Android UI。

包含：

- 课程、上课时间、周次规则。
- 学期和课表状态。
- 解析结果和解析错误。
- 用户设置。

设计原则：

- 字段结构化。
- 保留原始文本。
- 可序列化到本地。

### parser

负责 PDF 解析，不绘制 UI。

包含：

- PDF 文本坐标提取。
- 星期列识别。
- 节次识别。
- 课程详情解析。
- 周次规则解析。
- 课程合并。
- 诊断日志。

设计原则：

- 输入：`Context + Uri` 或输入流。
- 输出：`ParseResult`。
- 不直接 Toast。
- 不直接修改 UI 状态。

### ui

负责 Android 原生界面。

包含：

- 课表网格绘制。
- 今日概览。
- 顶部周次和日期。
- 课程详情弹窗。
- 空状态和错误状态。
- 解析结果确认页。
- 设置页。

设计原则：

- UI 读取 `ScheduleState`。
- 点击事件通过回调交给 Activity。
- 课表绘制逻辑从 `MainActivity` 拆出。

### export

负责导出和分享。

包含：

- 用 Canvas 导出课表图片。
- 用 Android `PdfDocument` 导出 PDF。
- 调用系统分享面板。

设计原则：

- 不影响主课表展示。
- 不引入大型导出依赖。

### storage

负责本地保存。

可选方案：

- 第一阶段：不保存或只保存简单设置。
- 第二阶段：用 `SharedPreferences` 保存设置。
- 第四阶段：用 JSON 文件保存课表和学期。

设计原则：

- 先简单可靠。
- 不急于引入数据库。
- 保存数据版本号，方便后续迁移。

### util

负责小型工具：

- 颜色分配。
- 日期和周次计算。
- dp/sp 转换。
- 节次时间映射。

## 5. MainActivity 未来职责

重构后的 `MainActivity` 应只负责：

- 管理页面生命周期。
- 接收文件选择结果。
- 持有当前 `ScheduleState`。
- 调用 parser、storage、export。
- 把状态传给 UI 组件。
- 响应导航和弹窗事件。

不再负责：

- 直接写大量 View 绘制代码。
- 直接解析 PDF 细节。
- 直接维护示例课程列表。
- 直接生成导出文件。

## 6. 分阶段迁移策略

### 第一阶段

- 保留根包下现有 `Course`、`ScheduleParser`、`MainActivity`。
- 只提取不会改变行为的小工具或模型。
- 不改变 UI 外观。

### 第二阶段

- 在 parser 包中新增解析组件。
- 让旧 `ScheduleParser` 逐步委托给新组件。
- 输出仍可转换为旧 `List<Course>`。

### 第三阶段

- 提取 `ScheduleBoardView`。
- 提取课程详情弹窗。
- 保持 MainActivity 作为页面协调者。

### 第四阶段

- 引入 storage。
- 保存导入课表和设置。
- 支持编辑。

### 第五阶段

- 引入 export。
- 支持图片/PDF导出和分享。
- 支持提醒。

## 7. 线程与状态

当前用 `new Thread()` 解析 PDF，然后 `runOnUiThread()` 更新 UI。短期可继续使用。

建议改进：

- 解析开始：`ScheduleState.isLoading = true`。
- 解析成功：保存 `ParseResult`。
- 解析失败：保留错误信息和诊断日志。
- Activity 销毁时避免更新已失效 UI。

不建议当前阶段引入 RxJava、Coroutine 或大型状态库。

## 8. 未来可选迁移方案

未来如果项目规模变大，可以单独评估：

- Kotlin：提高模型表达和空安全。
- Jetpack ViewModel：增强横竖屏状态保留。
- Room：管理多学期和编辑历史。
- Compose：重建 UI。

这些都不是当前设计落地的前置条件，不应现在执行。
