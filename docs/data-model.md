# Polaris课程表数据模型设计

## 1. 设计目标

新的数据模型采用渐进落地方式，目标是解决当前 `Course.java` 的限制：

- 一门课支持多个上课时间。
- 周次规则结构化，支持范围、单双周、项目周和未知周次。
- 保留 PDF 原始文本、页码、坐标，方便用户纠错。
- 支持解析置信度和错误提示。
- 支持本地保存、多学期、导出、提醒和编辑。

## 2. 模型关系概览

```text
ScheduleState
  ├─ Semester
  ├─ List<Course>
  │    └─ List<CourseMeeting>
  │         └─ WeekRule
  ├─ ParseResult
  │    ├─ List<ParseError>
  │    └─ diagnosticsId
  └─ UserSettings
```

## 3. Course

课程实体，表示一门课程本身，而不是某一次上课安排。

建议字段：

```java
class Course {
    String id;
    String name;
    String normalizedName;
    String teacher;
    String campus;
    String defaultLocation;
    String courseType;
    String teachingClass;
    String credit;
    List<CourseMeeting> meetings;
    String colorKey;
    int color;
    float confidence;
    String sourceRawText;
    List<String> warnings;
}
```

字段说明：

- `id`：本地唯一 ID，可由导入批次和课程名生成。
- `name`：课程显示名。
- `normalizedName`：去掉标记符、空格和教学班后用于合并。
- `teacher`：默认教师，具体 meeting 可覆盖。
- `campus`：校区，例如“长安校区西区”。
- `defaultLocation`：默认地点。
- `courseType`：讲课、实验、实践、网络。
- `meetings`：一门课程的多个上课时间。
- `confidence`：课程整体解析置信度。
- `sourceRawText`：该课程关联的原始 PDF 文本。
- `warnings`：低置信度提示。

## 4. CourseMeeting

一次上课安排，承载星期、节次、周次和地点。

建议字段：

```java
class CourseMeeting {
    String id;
    String courseId;
    int dayOfWeek;
    CourseTimeMode timeMode;
    int startSection;
    int endSection;
    int startMinuteOfDay;
    int endMinuteOfDay;
    WeekRule weekRule;
    String location;
    String campus;
    String teacher;
    String courseType;
    int pageIndex;
    RectF sourceBounds;
    String rawText;
    float confidence;
}
```

字段说明：

- `dayOfWeek`：建议使用 1-7 表示周一到周日，避免当前 0-5 与自然星期混淆。
- `timeMode`：`SECTION` 使用学校节次配置换算时间，`CLOCK` 使用当天精确分钟，`NONE` 表示无固定时间。
- `startSection` / `endSection`：第几节到第几节。
- `startMinuteOfDay` / `endMinuteOfDay`：具体时间模式下的当天分钟区间；非具体时间模式为 `-1`。
- `weekRule`：结构化周次。
- `sourceBounds`：来源坐标，后续可在调试页高亮 PDF 区域。
- `rawText`：该上课安排的原始文本。

## 5. Semester

学期信息。

建议字段：

```java
class Semester {
    String id;
    String name;
    String academicYear;
    int termIndex;
    long startDateMillis;
    int totalWeeks;
    int currentWeek;
    String sourceFileName;
    long importedAtMillis;
}
```

说明：

- 示例 PDF 文本包含 `2025-2026学年第2学期`，可作为 `name` 和 `academicYear` 来源。
- `startDateMillis` 第一阶段可由用户设置或默认空。
- `currentWeek` 目前代码写死为 18，未来应进入 `Semester` 或 `UserSettings`。

## 6. WeekRule

周次规则模型。

建议字段：

```java
class WeekRule {
    enum Type {
        RANGE,
        ODD,
        EVEN,
        PROJECT,
        ALL,
        UNKNOWN
    }

    Type type;
    int startWeek;
    int endWeek;
    List<Integer> explicitWeeks;
    String rawText;
    boolean includesProjectWeek;
}
```

示例映射：

- `1-18周` -> `RANGE, start=1, end=18`
- `1-17周(单)` -> `ODD, start=1, end=17`
- `2-18周(双)` -> `EVEN, start=2, end=18`
- `项目周` -> `PROJECT`
- `周次见PDF` -> `UNKNOWN`
- `1-16周` -> `RANGE, start=1, end=16`

建议方法：

```java
boolean containsWeek(int week);
String displayText();
```

## 7. ParseResult

一次解析的结果。

建议字段：

```java
class ParseResult {
    boolean success;
    List<Course> courses;
    List<ParseError> errors;
    List<String> warnings;
    float confidence;
    String sourceFileName;
    int pageCount;
    long parsedAtMillis;
    String diagnosticsText;
}
```

说明：

- `success` 不等于没有错误。可以部分成功。
- `confidence` 可由星期列、节次、课程名、周次、地点教师等子项加权得到。
- `diagnosticsText` 第一阶段可直接保存文本日志，后续再结构化。

## 8. ParseError

解析错误或警告。

建议字段：

```java
class ParseError {
    enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    enum Code {
        PDF_OPEN_FAILED,
        NO_TEXT_FOUND,
        DAY_COLUMNS_NOT_FOUND,
        SECTION_NOT_FOUND,
        COURSE_NAME_LOW_CONFIDENCE,
        WEEK_RULE_UNKNOWN,
        LOCATION_NOT_FOUND,
        TEACHER_NOT_FOUND,
        UNSUPPORTED_SCANNED_PDF
    }

    Severity severity;
    Code code;
    String message;
    int pageIndex;
    String rawText;
}
```

用途：

- 面向用户显示清楚的失败原因。
- 面向开发者定位解析规则问题。
- 面向确认页标记低置信度课程。

## 9. ScheduleState

课表页面运行状态。

建议字段：

```java
class ScheduleState {
    Semester semester;
    List<Course> courses;
    int selectedWeek;
    int selectedDayOfWeek;
    boolean isLoading;
    String loadingMessage;
    ParseResult lastParseResult;
    String errorMessage;
    boolean showingSample;
}
```

说明：

- 用它替代 `MainActivity` 中分散的 `courses`、`currentWeek`、`currentTitle`、`currentSubtitle`。
- 第一阶段可以先作为设计文档存在，不立即引入状态管理框架。

## 10. UserSettings

用户设置。

建议字段：

```java
class UserSettings {
    boolean showWeekend;
    boolean showTimeText;
    boolean useCourseColor;
    boolean enableParseDiagnostics;
    int defaultWeekStartDay;
    String colorStrategy;
    String exportFormat;
    int reminderMinutesBefore;
}
```

默认值：

- `showWeekend = true`，因为当前项目显示到周六。
- `showTimeText = true`。
- `colorStrategy = "DAY"`。
- `exportFormat = "IMAGE"`。

## 11. 与当前 Course.java 的兼容策略

短期不删除当前 `Course.java`。建议分三步迁移：

1. 保持当前 `Course` 作为 UI 展示 DTO。
2. 新增 `model` 包中的设计模型。
3. 在 parser 输出新模型稳定后，再提供转换方法给旧 UI 使用。

兼容转换：

- 新 `CourseMeeting` 可转换成旧 `Course`。
- 一门课程多个 meeting 会展开成多个旧 `Course`。
- PDF 导入继续写入 `SECTION`，用户手动选择具体时间后写入 `CLOCK`；两种模式统一由时间解析器转换成分钟区间供课表绘制、冲突检测和提醒使用。
- `WeekRule.displayText()` 填入旧 `weeks` 字符串。
- `rawText` 填入旧 `raw`。

这样可以先改解析和模型，不影响当前课表绘制。
