# Polaris 自主迭代路线（2026-09）

> 授权：2026-09-03 用户全权委托自主迭代（"自己规划迭代、增加功能、优化UI、不中途停止"）。
> 本文件是跨会话/跨上下文的**路线图 + 进度台账**：每轮结束时更新状态，下轮从台账续接。
> 约束：遵守 `AGENTS.md`（原生 Java、每轮一个明确任务、交付 APK 必升版本、保护三个核心文件）。

## 路线

| 轮 | 内容 | 类型 | 版本 | 状态 |
|---|---|---|---|---|
| P0 | 提交积压 1.16.6/1.16.7；AGENTS.md 优先级区重写；iteration-plan 状态标注 | 文档 | — | ✅ |
| P1 | ScheduleBoardRenderTest flake 加固（isDisplayed→isCompletelyDisplayed）+ 模拟器全量 instrumented 验证 | 测试 | — | 🔄 |
| P2 | 课表**当前时间指示线**：今日列内当前节高亮 + 实时进行中线（仅当本周含今天时显示） | 功能 | 1.17.0 | ⏳ |
| P3 | **下节课倒计时**：今日概览/横幅显示"距下节课 X 分钟" | 功能 | 1.17.1 | ⏳ |
| P4 | **数据备份/恢复**：课程+设置 JSON 经 SAF 导出/导入 | 功能 | 1.18.0 | ⏳ |
| P5 | UI 打磨批：M3 审计遗留（popup 暗色评估）、课块视觉细节、空态文案 | 优化 | 1.18.1+ | ⏳ |

## 执行纪律（每轮固定）

1. 设计先行：写明目标/涉及文件/风险（对话里说明）。
2. 实现：只动本轮文件；核心流程（导入 PDF → 周课表展示）保持可用。
3. 验证：`testDebugUnitTest` 全绿；UI 轮次在模拟器（Polaris_Test，Pixel 7 / API 35）截图验收；`connectedDebugAndroidTest` 全绿。
4. 交付：升版本（功能 minor / 修复 patch）→ `assembleDebug` → 单独 commit → 本文件更新。

## 发现与决策记录

- **环境**：本机 adb + 模拟器可用（此前记忆"本机无 adb"失效）。AVD `Polaris_Test`（pixel_7 / API 35 / WHPX 加速）。所有 UI 交付必须截图验收，不再"待真机"。
- **flake 根因分析（P1）**：周页 ViewPager 相邻预加载页与当前页同含同名课程块（种子课程覆盖 1-20 周），`isDisplayed()` 在预加载瞬态可能双命中 → AmbiguousViewMatcherException。`isCompletelyDisplayed()` 在静止断言时刻结构性排除屏外页。CI 上曾"复跑绿"未修，本轮落地加固。
- **iteration-plan 体检快照已过时**（P2/P3 所列残留均已清零），已在文首标注，勿再按旧快照开工。

## 已完成（前序，本轮之前）

- 1.16.6/1.16.7：M3 审计修复两轮（token 收敛、sw600dp 断点资源层、自适应图标），commit `a34dd6c`。
