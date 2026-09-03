# Polaris 自主迭代路线（2026-09）

> 授权：2026-09-03 用户全权委托自主迭代（"自己规划迭代、增加功能、优化UI、不中途停止"）。
> 本文件是跨会话/跨上下文的**路线图 + 进度台账**：每轮结束时更新状态，下轮从台账续接。
> 约束：遵守 `AGENTS.md`（原生 Java、每轮一个明确任务、交付 APK 必升版本、保护三个核心文件）。

## 路线

| 轮 | 内容 | 类型 | 版本 | 状态 |
|---|---|---|---|---|
| P0 | 提交积压 1.16.6/1.16.7；AGENTS.md 优先级区重写；iteration-plan 状态标注 | 文档 | — | ✅ |
| P1 | ScheduleBoardRenderTest flake 加固（isDisplayed→isCompletelyDisplayed）+ 模拟器全量 instrumented 验证 | 测试 | — | ✅ |
| P2 | 课表**当前时间指示线**：今日列实时红线 + 描边圆点，像素级验证 | 功能 | 1.17.0 | ✅ ff6e9b6 |
| P3 | ~~下节课倒计时~~ **已存在**：壳层状态卡已有 上课中/距下课倒计时/今日无课 三态 | — | — | 🚫 取消 |
| P3' | **进行中时段高亮框**：NowLineView 同层为当前节格画圆角描边环 | 功能 | 1.17.1 | ✅ c654170 |
| P4 | ~~备份/恢复~~ **已存在**：ScheduleBackupManager + 分享导出 + 文件导入 + 确认恢复 | — | — | 🚫 取消 |
| P4' | **空闲时段速查**：动作面板入口 + FreeSlotCalculator 纯计算 + 按天对话框 | 功能 | 1.18.0 | ✅ 9ded5bb |
| P5 | UI 打磨批：学期外副标题与网格日期对齐、环/线白色衬底晕 | 优化 | 1.18.1 | ✅ 7cada91 |
| P6 | **Widget 进行中高亮**（用户点单）：ongoing 条目高亮底+时间强调+开始时刻刷新 | 功能 | 1.19.0 | ✅ 03ba6fb |
| P7 | **质量基线**（外部评估 P0/P1）：lint 清零入 CI、PDF 护栏真实化、PR smoke | 质量 | 1.19.1 | ✅ bac5f92/b6d287d |
| P8 | **多课表 Widget**（外部评估 P2）：每实例独立绑定课表 + 配置页 + 标题课表名前缀 | 功能 | 1.20.0 | ✅ 8d44b06 |
| P9 | **新学期向导**（外部评估 P3）：模板复制配置 + 日期锚定 + 切换 + 进入导入 | 功能 | 1.21.0 | ✅ 1797712 |
| P10 | **考试/DDL 时间线**（外部评估 P4）：AcademicEvent 绝对日期事件模型 + 时间线对话框 + 完成勾选 | 功能 | 1.22.0 | ✅ 2247409 |
| P11 | **本地诊断包**（外部评估 P6）：一键导出诊断包 .txt（应用/设备/课表概要/提醒/解析日志）经系统分享 | 功能 | 1.23.0 | ✅ 2990972 |
| P12 | **日程体验整合**：移除空闲时段速查；计划页已完成分组可折叠；本周实践并入课表顶部栏（3s 折叠 + 多实践轮播）；板内横幅移除后网格上移补位 | 优化 | 1.24.0 | ✅ 86a638d |
| P13 | **桌面图标回退**（用户点单）：撤销 1.16.6 新增的自适应图标外壳，恢复 Polaris 星图 PNG 图标 | 修复 | 1.24.1 | ✅（本轮） |

## 迭代总结（2026-09-03 收官）

自主迭代交付 5 个版本（1.17.0 / 1.17.1 / 1.18.0 / 1.18.1 / 1.19.0），全部通过 单测 275+9 全绿、instrumented 12 条全绿、模拟器明暗双主题截图验收：

1. **测试护栏**：修复 CI 已知 flake（周页预加载瞬态双命中 → `isCompletelyDisplayed`）。
2. **当前时间指示线**（1.17.0）：今天列实时红线 + 描边圆点，`ScheduleTimeAxis.yForMinute` 分钟级定位，像素级核对（ppm=4.0 含节间塌缩修正）。
3. **进行中时段高亮框**（1.17.1）：当前节格圆角描边环，与指示线同层联动。
4. **空闲时段速查**（1.18.0）：`FreeSlotCalculator` 纯逻辑（分钟重叠判定，节次/钟点统一）+ 动作面板入口 + glass 对话框。
5. **日期错配修复**（1.18.1）：学期外钳制周副标题改显该周周一，与网格列日期同源；环/线加白色衬底晕提升同色块对比度。
6. **Widget 进行中高亮**（1.19.0）：今天列表内正在上课的条目加淡色高亮底（`widget_item_ongoing_surface`，明暗双 token）+ 时间行强调；provider 刷新调度扩展到课程开始时刻（`nextCourseStartAfter` 与结束时刻取较早者）。

**Widget 桌面实测技法**：Pixel Launcher（launcher3）添加 widget = widget 选择器中**单击预览本体** → 预览下方出现「+ ADD」→ 点击放置（长按拖拽手势在 `input swipe` 下会被识别为滚动，不可靠）。误加的 widget 移除：长按 → 顶部「✕ Remove」；弹窗时有时无时，卸载来源应用（`pm uninstall -k --user 0`）最可靠。

## 质量基线轮（2026-09-03，外部评估 P0/P1 落地）

- **Lint 61 警告+2 错误 → 0 error/3 警告**（`bac5f92`，1.19.1）：3 条剩余为 bouncycastle 依赖内部 TrustAllX509TrustManager（pdfbox 传递依赖，随依赖线）。约定：`@SuppressLint`/`tools:ignore` 必须带理由注释；colors.xml 镜像层已瘦身为仅被引用 token。
- **PDF 护栏真实化**（`b6d287d`）：原 `assertTrue(true)` 永真断言换两层——①合成文本层（反射注入 TextBlock）硬断言课程数/名称/节次/周次/地点，CI 必跑；②真三校 PDF 样例存在时按基线表（11/24/27）断言课程数。要点：fixture 必须用 ROOM_PATTERN 支持的教务地点格式（A-101），自然语言写法（教学楼A101）会被既定正则截断——这是格式约定不是 bug。
- **CI 前置拦截**：build job 并入 lintDebug（PR+main 都跑）；instrumented job 改为 PR 跑 MainActivitySmokeTest（分钟级）、push main 跑全量。评估中"PR 无法拦截"的部分此前已由 build job 覆盖，本轮补齐仪器 smoke。

后续候选（未排期）：学校模板扩展（P5，一校一迭代，先匿名样例）；考试/DDL 余项：提醒调度（绝对日期 AlarmManager）、今日概览联动、课表网格标注。多课表 Widget 的余项：今日/明日独立模式、紧凑布局变体。英文本地化已明确不做。MainActivity 瘦身仍是长期结构债（约 6400 行）。

## 考试/DDL 时间线轮（2026-09-03，外部评估 P4 落地，1.22.0）

- **数据模型**：`model/AcademicEvent`（不可变，与 StudyPlan 隔离）——id/标题/关联课程/类型(EXAM·DEADLINE·PRACTICE)/dateMillis（本地零点毫秒，`normalizedDateMillis` 归一）/minuteOfDay（-1=仅日期）/地点/座位/备注/done/createdAt；`withDone` 派生。
- **存储**：`storage/AcademicEventRepository` 与 PlanRepository 完全同构（`polaris_schedule` prefs、`academic_events_<scheduleId>` 键、损坏容错、未知 type 回退 EXAM）。切换课表时 reloadAcademicEvents（onCreate + switchSchedule）。
- **UI**：`AcademicEventDialogs extends DialogKit`——时间线对话框（待完成按日期升序/已完成降序分组，类型徽标 考/截/践 用组件专属色常量，勾选即时落库并重建对话框）+ 编辑器（类型 chips、关联课程复用 courseNameChoices、DatePicker/TimePicker、清除时刻=仅日期、地点/座位/备注）。入口两处：计划页/平板管理浮层入口卡（PlanPageBuilder.Host.openAcademicTimeline）+ 动作面板「考试 / DDL」。
- **Host 契约变化**：DialogKit 新增 LongSetter；MainActivity 的 courseNameChoices/settingValueRow/updateSettingValueRow 由 private 放宽到包可见（同包对话框类复用，无行为变化）。
- **验证**：单测全绿（新增 AcademicEventRepositoryTest 9 例：往返/缺失/损坏/跳坏条目/未知类型/隔离/清空/默认键/normalizedDateMillis）；lint 0 error（警告仍为 bouncycastle 基线 3 条）；instrumented 13/13 全绿；模拟器明暗双主题截图验收（添加→列表→勾选→重启持久化全链路）。
- **实测技法**：键盘弹出会平移对话框布局，adb 点击需先收键盘再按收起态坐标定位；`input text` 中文在模拟器报 NPE，UI 验证用英文占位文本即可。

## 本地诊断包轮（2026-09-03，外部评估 P6 落地，1.23.0）

- **构建器**：`diagnostics/DiagnosticsBundleBuilder`（纯 Java，无 Android 依赖）——分节（标题 + 有序键值行 + 可选原文块）拼固定格式文本；空值统一「未设置」；顺序与输入一致。
- **收集与导出**：`MainActivity.buildDiagnosticsBundle()`（应用版本/系统/设备、激活课表概要：学校/学期/开学/周数/节次/课程/计划/事件数、提醒总开关、最近解析诊断全文或"无导入记录"）+ `exportDiagnosticsBundle()`（写 cache/schedule_exports/`Polaris诊断-<stamp>.txt` → FileProvider → ACTION_SEND text/plain，复用备份导出模式）。
- **入口**：解析诊断对话框新增「导出诊断包」按钮（空态同样可用，先 dismiss 再导出，与其他 action 模式一致）。
- **验证**：新增 DiagnosticsBundleBuilderTest 5 例全绿；lint 0 error；instrumented 13/13；模拟器实测——对话框空态导出按钮可用，连续两次点击均落文件，run-as 验明内容完整（此模拟器无分享目标故 chooser 未弹出，属环境限制非缺陷）。
- **实测技法**：设置页下部行滚动时才构建，uiautomator dump 需滑到底后再取 bounds 精确点击；BACK 会重置设置页滚动位置。

## 多课表 Widget 轮（2026-09-03，外部评估 P2 落地，1.20.0）
- **渲染隔离**：Service 按 `EXTRA_APPWIDGET_ID` 查绑定课表取数；Provider 标题在绑定非激活课表时前缀课表名。
- **实测技法补充**：注入多课表需写全自包含 prefs（`schedules` 数组 + 每表 `config_/courses_/structured_courses_/schema_version_` 四键，schema_version=3）；instrumented 全量会 clearAll 清掉一切，注入要在测试跑完之后做。launcher 添加 widget 的 configure 流程会在放置前弹配置页，取消即不放置。
- **坑**：模拟器 `auto_time` 恢复 1 后网络对时会覆盖手动拨的日期（真机日期 2026-09-03）——时间相关验证前必须先关 auto_time。截图坐标换算：缩略图坐标 × (1080/缩略宽)，别直接用。

## 日程体验整合轮（2026-09-03，1.24.0）

- **目标**：清理低频入口并收敛实践课展示位置——移除空闲时段速查；计划页已完成分组可折叠；本周实践从板内横幅并入课表顶部栏（3s 折叠 + 多实践轮播）；板内横幅移除后课表网格自动上移补位。
- **涉及文件**：`MainActivity.java`（顶栏实践条 `buildPracticeTopBar`/`updatePracticeTopBar`/`cancelPracticeBarTimers`/`practiceTopSummaryText`/`showPracticeCourses`，动作面板移除空闲时段入口，设置开关/切页/周变化回调联动）；`ui/ScheduleBoardView.java`（移除板内实践横幅渲染与空闲速查栏共 -177 行，网格上移补位）；`ui/page/PlanPageBuilder.java`（已完成分组头：标题+数量+箭头，点击切换 + 新鲜打开 3s 自动折叠 `armDoneAutoCollapse`，手动切换取消计时）；删除 `time/FreeSlotCalculator.java` 与 `FreeSlotCalculatorTest.java`（回撤 1.18.0 引入的空闲速查）；`res/values/strings.xml`（新增 `plan_done_count`/`plan_done_cd_expand`/`plan_done_cd_collapse`/`board_cd_practice_single`，删除 `action_free_slots` 与 `free_slot_*`）；`app/build.gradle`（1.23.0→1.24.0，12300→12400）。
- **行为细节**：顶栏实践条仅在「课表页 + 展示实践开关开 + 本周有实践」显示；展开态 3s 后折叠为单行名称，多实践每 3s 轮播；关键修复——重复 layout/render 进入时不重复武装折叠计时（`practiceBarCollapse == null` 判定），避免 3s 折叠被无限推迟；切页隐藏与销毁均 `cancelPracticeBarTimers` 防泄漏，回课表页重新展开。计划页已完成组折叠态与自动折叠逻辑同构（手动切换取消计时）。
- **验证**：`testDebugUnitTest` 全绿、`assembleDebug` 成功（`Polaris-1.24.0-debug.apk`）、`lintDebug` 0 error（警告仍为 bouncycastle 基线 3 条）；模拟器（Polaris_Test）冒烟——启动顶栏展开态 → 3s 折叠为单行「实践课」→ 切「我的」回「课表」重新展开；计划页「已完成（1）」可折叠分组头渲染正常。
- **风险**：折叠/轮播 Runnable 经 Handler 持有视图引用，隐藏与销毁分支均已清回调；FreeSlotCalculator 为纯计算类无存储迁移负担，删除无数据兼容问题。

## 桌面图标回退轮（2026-09-03，用户点单，1.24.1）

- **背景**：1.16.6（`a34dd6c`）为 M3 合规**新增**自适应图标外壳——`mipmap-anydpi-v26/ic_launcher.xml` + `drawable/ic_launcher_foreground.xml` + `ic_launcher_background` 色，使 API 26+ 桌面图标从「深蓝底北极星 + 日历」的 Polaris 原图变成「纯蓝底 + 白色网格」的扁平图形（清单 `android:icon="@mipmap/ic_launcher"` 未变，仅解析优先级变了）。用户要求换回。
- **改动**：删除上述自适应外壳与前景 drawable；移除 `colors.xml` 中仅被其引用的 `ic_launcher_background` 及注释里已失效的「自适应图标背景」说明；`app/build.gradle` 1.24.0→1.24.1（12400→12401）。回退后各密度 PNG 成为唯一图标源。
- **取舍**：源图 `drawable/ic_launcher_source_transparent.png` 已在 `bac5f92` 作为未引用资源删除，现存美术最高仅 192px（xxxhdpi）。因此**不**重做「星图版自适应图标」——那需把 192px 放大到 432px 前景层，必然发糊；直接回退 PNG 反而画质无损。代价是失去 Android 13+ 主题图标（monochrome）能力，桌面由启动器自行加底板蒙版（即 1.16.6 之前的表现）。
- **验证**：`assembleDebug` + `lintDebug` 0 error（警告仍为 bouncycastle 基线 3 条）；APK 内 `ic_launcher` 仅剩 5 个密度 PNG、`mipmap-anydpi-v26` 条目消失；模拟器（API 35）应用抽屉实测图标已回到 Polaris 星图。
- **排查记录**：底部导航「课表」tab 图标自 UI 重构（`613c529`）后未改动，非本轮目标。

## 执行纪律（每轮固定）

1. 设计先行：写明目标/涉及文件/风险（对话里说明）。
2. 实现：只动本轮文件；核心流程（导入 PDF → 周课表展示）保持可用。
3. 验证：`testDebugUnitTest` 全绿；UI 轮次在模拟器（Polaris_Test，Pixel 7 / API 35）截图验收；`connectedDebugAndroidTest` 全绿。
4. 交付：升版本（功能 minor / 修复 patch）→ `assembleDebug` → 单独 commit → 本文件更新。

## 发现与决策记录

- **环境**：本机 adb + 模拟器可用（此前记忆"本机无 adb"失效）。AVD `Polaris_Test`（pixel_7 / API 35 / WHPX 加速）。所有 UI 交付必须截图验收，不再"待真机"。
- **模拟器验证技法**（P2 沉淀）：`adb root` + `settings put global auto_time 0` + `date MMDDhhmmYYYY.ss` 拨时钟驱动时间相关 UI；学期配置经 `run-as com.polaris.timetable` 注入 `shared_prefs/polaris_schedule.xml` 的 `config_default` 键（`{"firstWeekDay":"2026/9/7"}` 即可，其余字段走默认）；`cmd uimode night yes` 切暗色。**instrumented 测试的 clearAll 会清掉 config 键**——注入前备份、验证后恢复。
- **flake 根因分析（P1）**：周页 ViewPager 相邻预加载页与当前页同含同名课程块，`isDisplayed()` 在预加载瞬态可能双命中 → AmbiguousViewMatcherException。`isCompletelyDisplayed()` 在静止断言时刻结构性排除屏外页。
- **功能撞车教训（P3/P4）**：选题前必须盘点既有功能——倒计时（壳层状态卡 TodayOverview）与备份/恢复（ScheduleBackupManager）均已存在。P4' 选题前先做全量功能清单。
- **轴映射语义（P2）**：`ScheduleTimeAxis` 的 `yForMinute` 会扣除节间塌缩间隙（所有 >0 间隙都进 collapsedGaps，午餐仅在开启塌缩时计入），ppm = sectionHeightPx/classMinutes；像素核对时必须用同样修正。
- **iteration-plan 体检快照已过时**（P2/P3 所列残留均已清零），已在文首标注，勿再按旧快照开工。

## 已完成（前序，本轮之前）

- 1.16.6/1.16.7：M3 审计修复两轮（token 收敛、sw600dp 断点资源层、自适应图标），commit `a34dd6c`。
