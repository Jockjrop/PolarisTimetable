# Polaris课程表 PDF 解析设计

## 1. 设计目标

解析设计基于当前 `ScheduleParser.java` 继续演进，不推倒重写。

目标：

- 保持 PDFBox Android + `TextPosition` 坐标提取方案。
- 提高示例 PDF 和同类教务系统 PDF 的识别准确率。
- 输出结构化课程、周次、错误和诊断日志。
- 支持部分成功，不因个别课程失败而放弃全部结果。
- 为后续解析结果确认页和用户纠错保留原始文本。

## 2. 当前解析方案回顾

当前 `ScheduleParser` 的核心思路是：

- 使用 `PDFTextStripper` 读取 PDF 页面。
- 在 `writeString()` 中访问 `TextPosition`。
- 将同一行文字按 X 间距切成 `TextBlock`。
- 找到星期标题列的中心点。
- 通过节次正则识别课程详情起点。
- 根据 X 坐标把节次文本归入星期列。
- 向前找课程名，向后收集地点、教师等详情。
- 按简单 key 去重。

示例 PDF 特征：

- 页面为横向 A4，第一页宽约 842，高约 595。
- 星期标题在同一行，包含“星期一”到“星期五”。
- 示例坐标中星期列中心约为 197、337、477、616、756。
- 课程名通常带 `◆`、`◇`、`●`、`○` 标记。
- 节次和周次格式类似 `(1-2节)1-18周`、`(1-2节)1-17周(单)`、`(1-2节)2-18周(双)`。
- 地点和教师以 `/场地:`、`/教师:` 标记出现。
- 第二页包含 7-8 节课程和“其他课程”列表。

## 3. 模块拆分建议

未来 parser 包建议拆成：

```text
parser/
├─ ScheduleParser.java
├─ PdfTextExtractor.java
├─ TextBlock.java
├─ DayColumnDetector.java
├─ SectionDetector.java
├─ CourseBlockParser.java
├─ WeekRuleParser.java
├─ CourseMerger.java
└─ ParseDiagnostics.java
```

### ScheduleParser

职责：

- 对外提供 `parse(Context, Uri)`。
- 串联提取、检测、解析、合并、诊断。
- 处理异常并返回 `ParseResult`。

短期兼容：

- 可以保留当前 `List<Course> parse()` 方法。
- 新增设计中的 `ParseResult parseDetailed()`，待实现阶段再加入。

### PdfTextExtractor

职责：

- 初始化 PDFBox Android。
- 打开 PDF。
- 提取每页 `TextBlock`。
- 保留页码、x、y、width、height、文本。

设计要点：

- 当前 `PositionStripper` 可移动到这里。
- `TextPosition` 合并策略应记录每个 block 的高度。
- 对空文本 PDF 返回 `NO_TEXT_FOUND`。

### TextBlock

职责：

- 表示一个文本块。

建议字段：

```java
class TextBlock {
    String text;
    int pageIndex;
    float x;
    float y;
    float width;
    float height;
    float centerX();
    float centerY();
}
```

说明：

- 当前内部类只有 text、page、x、y、width，建议增加 height。
- 保留原始 text 和 clean text 两个概念，避免清理后丢失调试信息。

### DayColumnDetector

职责：

- 识别星期列。
- 输出 `dayOfWeek -> centerX` 和置信度。

识别策略：

1. 优先查找包含“星期一”到“星期日”的标题块。
2. 如果只找到周一到周五，也视为有效。
3. 检查列中心是否有足够横向跨度。
4. 若标题缺失，按页面宽度和课程块分布估算列中心。
5. 记录 fallback 诊断。

注意：

- 当前项目显示周一到周六，但当前解析只识别到周五。后续要支持星期六、星期日。
- 如果 PDF 只有工作日，UI 仍可显示周六为空。

### SectionDetector

职责：

- 识别节次行和节次范围。

识别策略：

- 课程详情中的节次：`(1-2节)`、`（1-2节）`。
- 左侧节次列：独立数字 `1` 到 `11` 可作为辅助定位。
- 允许空格：`(1 - 2节)`。
- 输出 `startSection`、`endSection` 和所在 `TextBlock`。

失败处理：

- 没有任何节次时返回 `SECTION_NOT_FOUND`。
- 节次超过 1-14 时标记低置信度。

### CourseBlockParser

职责：

- 将课程名、节次、地点、教师等文本合成 `CourseMeeting`。

识别课程名：

- 优先使用节次块同列、Y 距离最近且位于节次块上方的文本块。
- 课程名通常含 `◆`、`◇`、`●`、`○`。
- 如果课程名换行，如“习近平新时代中国特色社会主义 / 思想概论◆”，需要把同列相邻上方文本合并。
- 排除“时间段”“节次”“上午”“下午”“晚上”等非课程文本。

识别地点：

- 从详情文本中提取 `/场地:` 到下一个 `/`。
- 如果是 `场地:未排地点`，保留为“未排地点”。
- 兼容 `地点:`、`教室:` 作为未来扩展。

识别教师：

- 从详情文本中提取 `/教师:` 到下一个 `/`。
- 如果缺失，标记 `TEACHER_NOT_FOUND` 警告。

识别课程类型：

- `◆` -> 讲课
- `◇` -> 实验
- `●` -> 实践
- `○` -> 网络

### WeekRuleParser

职责：

- 把周次字符串解析成 `WeekRule`。

支持格式：

- `1-18周`
- `1-17周(单)`
- `2-18周(双)`
- `9-16周`
- `项目周`
- `共18周`
- `周次见PDF`

单双周处理：

- `(单)` 表示只包含奇数周。
- `(双)` 表示只包含偶数周。
- `1-17周(单)` 包含 1、3、5 ... 17。
- `2-18周(双)` 包含 2、4、6 ... 18。

项目周处理：

- 如果原文包含“项目周”，设置 `WeekRule.Type.PROJECT`。
- 项目周不应被普通周次过滤误删。
- UI 可在课程块上显示“项目周”。

未知周次处理：

- 无法解析时返回 `UNKNOWN`，保留 `rawText`。
- 课程仍显示，但在确认页标记低置信度。

### CourseMerger

职责：

- 合并重复课程和同一课程的多个上课时间。

当前去重 key：

```text
day | startSection | endSection | name | weeks
```

未来合并策略：

- 先按 `normalizedName + teacher + teachingClass` 合并为同一 `Course`。
- 再把不同 `CourseMeeting` 放入 `meetings`。
- 完全相同的 meeting 去重。
- 同名但教师或教学班不同的课程不要强行合并。

### ParseDiagnostics

职责：

- 记录解析过程中的关键判断，供调试和用户反馈。

建议日志内容：

- PDF 文件名、页数。
- 每页提取的文本块数量。
- 星期列检测结果和置信度。
- 节次块数量。
- 每个课程块的来源页码、X/Y 坐标、匹配星期、匹配节次。
- 低置信度原因。
- 合并前后课程数量。

日志示例：

```text
[page=1] blocks=126
[day-columns] Mon=197 Tue=337 Wed=477 Thu=616 Fri=756 confidence=0.96
[section] text="(1-2节)1-18周/校区..." day=1 start=1 end=2
[course] name="计算机组成原理" location="B332" teacher="张洁" confidence=0.93
[merge] meetings=14 courses=10
```

## 4. 解析失败处理

失败不应只有 Toast。建议分层：

### 硬失败

- PDF 无法打开。
- 没有任何可提取文字。
- 没有识别到节次。
- 没有识别到星期列且 fallback 也失败。

处理：

- 返回 `ParseResult.success = false`。
- `errors` 包含明确 code。
- UI 显示错误状态页。

### 部分成功

- 有课程但部分字段缺失。
- 周次无法结构化。
- 地点或教师缺失。
- 课程名疑似截断。

处理：

- 返回 `success = true`。
- 进入解析结果确认页。
- 低置信度项目显示标记。

## 5. 调试日志导出

第一阶段建议只保存内存字符串，显示在错误页或确认页。

后续可加入：

- 复制诊断日志。
- 保存到本地文件。
- 附带来源 PDF 文件名和应用版本。

## 6. 不做的事情

当前阶段不做：

- OCR 识别扫描版 PDF。
- 引入大型表格识别或机器学习依赖。
- 重写为后端解析服务。
- 把 PDF 转图片后用视觉模型解析。

这些可以作为未来可选增强。
