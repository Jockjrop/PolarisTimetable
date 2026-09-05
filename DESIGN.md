# 设计文档导航

Polaris 设计相关材料的总入口。仓库铁律见 [AGENTS.md](AGENTS.md)，产品与构建说明见 [README.md](README.md)，贡献流程见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 视觉系统规范

- [docs/design/visual-system.md](docs/design/visual-system.md) —— 四主题视觉系统唯一规范源（本文件前身的正文，含设计 token front-matter）：色板 / 字体 / 圆角 / 间距 / 组件 token，主题边界、对比度、毛玻璃与课表优先规则，Do's and Don'ts。
  - 颜色 token 的代码落点：`app/src/main/java/com/polaris/timetable/ui/PolarisVisualTheme.java`（4 风格 × 明暗）；`res/values/colors.xml` 仅保留 widget / 主题 / 图标实际引用的镜像 token，Java UI 不引用 `R.color` 是既定形态。
  - 形状角半径与课表网格断点：`app/src/main/res/values/dimens.xml`（平板覆盖 `values-sw600dp/dimens.xml`）。

## 设计文档（docs/design/）

- [架构设计](docs/design/architecture.md)
- [数据模型](docs/design/data-model.md)
- [解析器设计](docs/design/parser-design.md)
- [界面设计](docs/design/ui-design.md)
- [产品设计](docs/design/product-design.md)

## UI 高保真页面（design/）

- `design/pages/*.html` —— 课表 / 课程详情 / PDF 导入 / 解析确认 / 设置 / 今日概览高保真页面，历史视觉参考，非可运行预览（根目录旧 `preview.html` 已停更，归档于 `docs/archive/`）
- `design/polaris-ui-design.design`、`design/generation-tree.json`、`design/orchestration-summary.json` —— 设计生成过程产物
- `design/colors_and_type.css`、`design/partials/project-shell.html` —— 设计系统样式与页面骨架局部

## 质量与回归（docs/qa/）

- [测试计划](docs/qa/test-plan.md)
- [UI 回归清单](docs/qa/ui-regression-checklist.md)

## 迭代计划（docs/plans/）

- [应用内更新系统方案](docs/plans/app-self-update-plan.md) 与 [改进计划](docs/plans/app-self-update-improvement-plan.md)
- [计划页悬浮新增菜单优化](docs/plans/plan-page-fab-optimization-plan.md)

## 版本历史

- [CHANGELOG.md](CHANGELOG.md)
