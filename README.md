# Polaris课程表

原生 Android 课程表应用（Java）：从教务系统 PDF 课表自动提取课程、节次、周次、地点与教师，以适配手机和平板的横向周课表展示。

> **⚠️ 严禁商用**：本项目仅供个人学习与非商业用途使用。任何形式的商业用途（包括但不限于出售、付费分发、商业二次开发、打包收费、课表代导入收费服务等）均被**严格禁止**；二次分发必须保留本项目署名与许可声明。详见下方 [License](#license) 说明。

> **隐私承诺**：Polaris 默认不联网。仅当你手动检查更新，或主动开启"自动检查更新"后，应用才会访问官方 GitHub / Gitee 发布源获取版本信息与安装包（详见 [PRIVACY.md](PRIVACY.md)）。课程、课表、学校、账户资料、设置及导入文件不会上传。

## 功能

### 导入
- **PDF 课表导入**：内置 西安邮电大学 / 杭州电子科技大学 / 西安理工大学 解析模板，支持自定义学校；基于 PDF 文字坐标识别星期与节次，自动提取上课时间表与学期信息
- **AI 识别导入**：选择课表图片跳转任意外部多模态 AI，识别结果粘贴回 Polaris 校验后导入（Polaris Schedule JSON v1 协议）
- **手动添加课程**：长按空格子直接新建
- **课表分享导入**：`polaris://` 深链、`.polaris` 课表文件、链接粘贴导入

### 课表展示
- 横向滑动周视图，3/5/7 天显示切换，随时回到本周
- 今日概览（下一节课/进行中）、课程冲突检测与摘要
- 实践课程横幅、周次外课程显示、午休/中间节折叠
- 深色模式、清爽蓝等多套视觉主题、背景图、底部导航定制、毛玻璃效果

### 课程管理
- 点击查看详情，长按编辑（颜色/类型/教师/地点/学分/单双周/项目周）
- **长按拖动课程**到新位置，保存后可撤销
- 课程管理页：搜索（课程/教师/地点）、多选批量改颜色/改教师/删除、课程统计（课时/学分/教师分布）
- 多课表管理，每课表独立配置

### 提醒与组件
- 课前提醒（通知/精确闹钟/悬浮窗），重启自动恢复
- 桌面小部件：今日与明日课程
- 上课时间表编辑器（支持粘贴导入）

### 导出与备份
- 导出周课表图片 / PDF、整学期合并 PDF、**iCal 日历（.ics）**、**CSV**
- **一键备份/恢复**全部课表与设置（`.polarisbackup` 文件）

### 应用内更新
- 「更多 → 关于 → 检查更新」手动检查稳定版更新，可开启每日一次的自动检查（默认关闭）
- 检测到新版本时展示版本号、安装包大小、发布日期与更新说明，应用内下载并显示实时进度
- 下载完成后进行大小、SHA-256、包名/版本与签名四层校验，全部通过才交给系统安装器由你确认覆盖安装
- 更新数据来自 Polaris 官方 GitHub / Gitee Release 清单（`latest.json`），Gitee 不可用时自动切换 GitHub；检查与下载不携带任何课表或账户信息

## 打开方式

1. 用 Android Studio 打开本目录，等待 Gradle 同步完成
2. 连接手机或启动模拟器，运行 `app`
3. 在应用中选择「导入 PDF」或「AI 识别导入」

### 命令行构建

```bash
# Debug（产物在 app/build/outputs/apk/debug/）
./gradlew :app:assembleDebug        # Windows: .\gradlew.bat :app:assembleDebug

# 单元测试
./gradlew :app:testDebugUnitTest
```

要求：JDK 17、Android SDK（compileSdk 35）。

## Release 构建

1. 复制 `keystore.properties.example` 为 `keystore.properties`，填写发布证书路径与密码（该文件与 `*.jks`、`*.keystore` 已被 Git 忽略）
2. 运行 `./gradlew :app:assembleRelease`
3. 缺少签名信息时 release 任务会直接失败，不会生成未签名安装包

CI 可通过环境变量提供签名：`POLARIS_RELEASE_STORE_FILE`、`POLARIS_RELEASE_STORE_PASSWORD`、`POLARIS_RELEASE_KEY_ALIAS`、`POLARIS_RELEASE_KEY_PASSWORD`（GitHub Actions 工作流见 `.github/workflows/build.yml`）。

### 稳定版发布流水线（应用内更新依赖）

推送 `v<versionName>` 标签时，`release` job 会：

- 强制要求签名 Secret（`POLARIS_RELEASE_KEYSTORE_B64` 等）；缺失时直接失败，**不再回退 Debug APK**
- 用 `POLARIS_RELEASE_CERT_SHA256` 核对 APK 签名证书指纹，并用 `apkanalyzer` 核对包名与版本
- 校验 Git 标签 = `v<versionName>`、`versionCode` 三段式映射且严格大于最近稳定清单
- 以 `docs/releases/<versionName>.md` 为唯一来源生成 GitHub / Gitee 的 `latest.json` 与 `.sha256`；同一个正式 APK 经复验后上传两个 Release
- 客户端按稳定清单协议检查、下载、校验并交给系统安装器

发布前需在仓库 Secrets 配置：`POLARIS_RELEASE_KEYSTORE_B64`、`POLARIS_RELEASE_STORE_PASSWORD`、`POLARIS_RELEASE_KEY_ALIAS`、`POLARIS_RELEASE_KEY_PASSWORD`、`POLARIS_RELEASE_CERT_SHA256`（证书 SHA-256 指纹）和具有 Gitee Release 写权限的 `GITEE_TOKEN`，并由维护者离线备份签名材料。

## 学校 PDF 模板支持

解析器按 `SchoolParserModel` 枚举区分各校课表版式（`ScheduleParser.java`）。当前支持：

| 学校 | 解析分支 | 特点 |
|---|---|---|
| 西安邮电大学 | `parseBlocks` | 默认模板 |
| 杭州电子科技大学 | `parseHduBlocks` | 星期列 + 课程种子定位 |
| 西安理工大学 | `parseXautBlocks` | 周次节次文本模式 |

**想支持你的学校？** 欢迎贡献解析模板，指南见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 文档

- [项目开发规则](AGENTS.md)
- [贡献指南](CONTRIBUTING.md)
- [设计资料](DESIGN.md)
- [测试说明](docs/qa/test-plan.md)
- [版本历史](CHANGELOG.md)

## 隐私

完整隐私声明见 [PRIVACY.md](PRIVACY.md)。核心承诺：

- Polaris **默认不会自动联网**。用户手动检查更新或主动开启自动检查更新后，应用仅访问官方更新地址以获取版本信息和安装包；课程、课表、学校、账户资料、设置及导入文件不会上传
- 分享/备份文件（`.polaris` / `.polarisbackup` / `.ics` / `.csv`）由你主动导出并自行保管
- 外部 AI 识别流程中，课表图片仅发送给你主动选择的 AI 应用，Polaris 不参与数据传输

## License

本项目代码以 [GPL-3.0](LICENSE) 开源，并附加**非商业条款（严禁商用）**：

- **允许**：个人学习、自用、修改，以及在同样遵循 GPL-3.0 与本"严禁商用"条款、保留署名的前提下非商业分发
- **禁止**：任何形式的商业用途，包括但不限于出售、付费捆绑、商业二次分发、应用商店收费上架、收费代配置 / 代导入 / 定制服务等
- 二次分发时必须保留本项目署名、[LICENSE](LICENSE) 原文与本"严禁商用"声明；发现商用侵权将依法追究

简单说：**Polaris 永远免费，严禁商用。**
