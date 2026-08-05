# Polaris课程表电脑端预览说明

> 维护状态：`preview.html` 已停止维护，仅作为历史视觉参考保留。后续 Android 原生界面改动不再同步到该文件。

`preview.html` 是 Polaris课程表的桌面浏览器 UI 预览稿，用来快速查看 Android App 的视觉方向、状态流转和关键交互。它不是 App 的运行方式，也不会替代原生 Android Java 实现。

## 直接双击打开

1. 打开项目目录 `D:\Polaris课程表`。
2. 双击 `preview.html`。
3. 系统会用默认浏览器打开预览页。

这个方式不需要网络、不需要安装前端工具，也不依赖 CDN。

## 使用本地服务器打开

如果浏览器对本地文件有额外限制，可以在项目根目录启动一个简单本地服务器：

```powershell
cd D:\Polaris课程表
python -m http.server 8080
```

然后在浏览器中访问：

```text
http://localhost:8080/preview.html
```

如果电脑没有 Python，也可以继续使用双击打开的方式。

## preview.html 和 Android App 的关系

`preview.html` 只是一份电脑端预览稿，用于帮助设计和讨论 Polaris课程表的界面。它不会被 Android App 直接加载，不应该作为 WebView 页面使用，也不代表项目迁移为 Web 应用。

Android App 仍然应保持当前原生 Android Java 项目结构，继续使用现有的 PDF 导入、课程解析和周课表展示流程。

## 当前预览模拟内容

以下内容只在 `preview.html` 中模拟：

- 手机、平板、桌面大屏预览模式切换。
- PDF 导入 loading、解析成功、解析失败、空课表状态。
- 导出和分享入口提示。
- 设置页弹窗。
- 底部导航的学习页、我的页入口提示。
- 内置 `mockCourses` 数据。

## 应与 Android 实现保持一致的内容

后续迁移到 Android 原生 UI 时，建议保持这些设计方向一致：

- 清爽蓝色背景和半透明卡片层次。
- 顶部统计卡片，包括本周课程、今日课程、解析置信度。
- 横向周课表布局和课程块圆角样式。
- 课程详情中的课程名、星期、节次、周次、地点、教师、原始 PDF 文本和解析置信度。
- PDF 导入后的状态反馈，包括成功、失败、空课表和重试入口。
- 手机和平板的响应式信息密度。

## 不应从预览稿直接搬到 Android 的内容

- HTML、CSS、JavaScript 代码本身。
- 浏览器弹窗和 toast 的具体实现方式。
- Web 布局单位和滚动实现。
- 模拟导入逻辑和 mock 数据。

Android 端应使用原生 View、Java 数据模型和现有解析流程逐步实现对应体验。
