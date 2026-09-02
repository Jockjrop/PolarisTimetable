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
| P3' | **进行中时段高亮框**：NowLineView 同层为当前节格画圆角描边环 | 功能 | 1.17.1 | 🔄 |
| P4 | ~~备份/恢复~~ **已存在**：ScheduleBackupManager + 分享导出 + 文件导入 + 确认恢复 | — | — | 🚫 取消 |
| P4' | 待选题：先做功能清单盘点（设置四面板/我的页/编辑器），避免再撞已有实现 | 功能 | — | ⏳ |
| P5 | UI 打磨批：学期外状态卡与列日期不一致、M3 遗留、视觉细节 | 优化 | — | ⏳ |

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
