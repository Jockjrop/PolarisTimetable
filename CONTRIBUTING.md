# 贡献指南

感谢你愿意为 Polaris课程表 贡献代码。这个项目由个人维护，请遵守以下约定，让协作更顺畅。

## 项目规则（务必阅读）

仓库根目录的 [AGENTS.md](AGENTS.md) 是项目铁律，核心几条：

- **保持原生 Android Java**：不迁移 Web/React/Kotlin/Compose，不引入大型依赖替代 PDFBox Android 解析路线
- **不整体重构**：`MainActivity.java`、`Course.java`、`ScheduleParser.java` 是核心文件，调整必须保持现有导入 PDF 和课表展示流程可用
- **一次一个任务**：解析、UI、存储、导出不要混在同一轮修改
- **先设计后实现**：改代码前说明目标、涉及文件、预期风险；解析规则不确定时先补测试样例再动手
- **版本号**：每次改动代码并构建 APK 交付时，同时更新 `app/build.gradle` 的 `versionName`/`versionCode`（语义化版本，只能递增；功能增强升 minor）

## 如何提交

1. Fork 仓库，从 `main` 开分支，命名如 `feat/school-xxx`、`fix/parser-week`
2. 一次 PR 只做一个任务，PR 描述里写明：目标、涉及文件、测试情况、风险
3. 新功能必须带单元测试（`app/src/test/`，项目已有 40+ 测试文件）
4. 提交前本地跑通：`./gradlew :app:testDebugUnitTest` 与 `./gradlew :app:assembleDebug`

## 最有价值的贡献：新学校 PDF 模板

Polaris 目前只内置 3 所学校的解析模板，而各校教务系统 PDF 版式各不相同。**支持你的学校是贡献价值最高的方向**，流程如下：

### 1. 准备样例

- 一份真实课表 PDF（请隐去个人姓名/学号等隐私信息）
- 文字型 PDF（能选中复制文字）；截图型 PDF 无法用坐标解析，不在模板范围内

### 2. 实现解析分支

解析器结构在 `ScheduleParser.java`，各校版式对应独立分支：

```java
// SchoolParserModel 枚举（parser/SchoolParserModel.java）
// 1. 新增学校枚举（label 用学校全名）
// 2. 在 ScheduleParser.parseDetailed() 中按 model 分发到新分支
// 3. 实现 parseXxxBlocks(...)：复用 findDayCenters / CourseSeed / collectCellText
//    等坐标定位基础设施，按该校版式写课程种子与单元格解析
```

可参考现有三个分支（`parseBlocks` / `parseHduBlocks` / `parseXautBlocks`）与 [docs/parser-design.md](docs/parser-design.md)。

### 3. 编写测试样例

在 `app/src/test/java/com/polaris/timetable/ScheduleParserTest.java` 中按现有模式补充：

- 用构造的 `TextBlock` 坐标数据模拟该校版式
- 断言：课程名/节次/周次/地点/教师的解析结果
- 覆盖单双周、项目周、跨页等边界情况

解析规则不确定时，**先写测试样例再实现**，这是本项目的要求。

### 4. 提交

PR 中附上：学校名、样例 PDF 特征描述、解析分支与测试说明。

## 其他可贡献方向

- **Bug 修复**：解析失败的 PDF（附脱敏样例）是最有价值的反馈
- **文档**：docs/ 目录的设计文档、README 多语言
- **测试**：为现有逻辑补边界测试
- **界面打磨**：遵循现有代码构建 UI 的风格（无 XML 布局、全代码构建）

## 反馈问题

- 解析问题请附：学校名、PDF 版式描述（最好有脱敏样例或截图）
- 使用问题请附：机型、Android 版本、复现步骤
