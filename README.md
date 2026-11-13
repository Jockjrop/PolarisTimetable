# Polaris课程表

原生 Android 课程表应用（Java）：从教务系统 PDF 课表自动提取课程、节次、周次、地点与教师，以适配手机和平板的横向周课表展示。

官网：<https://timetable.deeps.top/>

> **当前版本**：`1.28.0`（`versionCode 12800`），发布基线为线上稳定版 `v1.27.14`。版本号唯一数据源是 [`app/build.gradle`](app/build.gradle)，本版对外发布说明见 [`docs/releases/1.28.0.md`](docs/releases/1.28.0.md)，完整历史见 [CHANGELOG.md](CHANGELOG.md)。

> **⚠️ 严禁商用**：本项目仅供个人学习与非商业用途使用。任何形式的商业用途（包括但不限于出售、付费分发、商业二次开发、打包收费、课表代导入收费服务等）均被**严格禁止**；二次分发必须保留本项目署名与许可声明。详见下方 [License](#license) 说明。

> **隐私承诺**：Polaris 默认开启每天最多一次的自动版本检查，仅访问官方 GitHub / Gitee 发布源读取公开版本清单；只有你手动选择下载更新时才会获取安装包（详见 [PRIVACY.md](PRIVACY.md)）。课程、课表、学校、账户资料、设置及导入文件不会上传；「扫一扫」的相机预览只在设备本地解码二维码，不拍照、不保存、不联网。课表分享链接虽经加密处理，但不等同于端到端加密，请勿用于传递敏感信息（[保护级别说明](PRIVACY.md#课表分享链接的保护级别)）。

## 功能

### 导入
- **PDF 课表导入**：内置 西安邮电大学 / 杭州电子科技大学 / 西安理工大学 解析模板，支持自定义学校；基于 PDF 文字坐标识别星期与节次，自动提取上课时间表与学期信息
- **AI 识别导入**：选择课表图片跳转任意外部多模态 AI，识别结果粘贴回 Polaris 校验后导入（Polaris Schedule JSON v1 协议）
- **手动添加课程**：长按空格子直接新建
- **课表分享导入**：`polaris://` 分享链接（内容经加密处理，保护级别见 [隐私政策](PRIVACY.md#课表分享链接的保护级别)），支持应用内扫一扫、相册识别二维码、剪贴板一键粘贴与系统深链；旧版 `.polaris` 课表文件与早期明文链接仍可导入
- **相机权限可选**：扫码仅在主动点击时使用相机、画面本地解码；无相机设备或拒绝权限时，仍可通过粘贴 / 相册导入

### 课表展示
- 横向滑动周视图，3/5/7 天显示切换，随时回到本周
- 今日概览（下一节课/进行中）、课程冲突检测与摘要
- **节假日与调休**：内置 2026 年国务院放假安排，放假日不排课并标记「休」；补班日可指定补哪一天的课（标记「补」），未指定时按真实星期显示、不臆测并提示待确认；「全局设置 → 节假日与调休」可逐项配置或一键恢复，覆盖只存本机
- 课前提醒、iCal 导出、今日 / 整周桌面小组件同步节假日与调休：假期不触发提醒、不生成日历事件，整周组件放假列留空、补班日按指定星期排课，表头标记「休」「补」
- 实践课程横幅、周次外课程显示、午休/中间节折叠
- 深色模式、清爽蓝等多套视觉主题、背景图、底部导航定制、毛玻璃效果；切换主题就地换肤，不再重建页面、改写明暗模式或丢失滚动位置
- **平板横屏**：课表列宽与节高按大屏收敛，今日概览、实践、本周计划三块侧栏可同屏完整显示，底部导航避让课程格子；手机与竖屏布局不变

### 课程管理
- 点击查看详情，长按编辑（颜色/类型/教师/地点/学分/单双周/项目周）
- **长按拖动课程**到新位置，保存后可撤销
- 课程管理页：搜索（课程/教师/地点）、多选批量改颜色/改教师/删除、课程统计（课时/学分/教师分布）
- 多课表管理，每课表独立配置

### 提醒与组件
- 课前提醒（通知/精确闹钟/悬浮窗），重启自动恢复；自动跳过放假日、覆盖补班日
- 桌面小组件：今日概览与整周课表两种形态，支持 5/6/7 天配置、按实例绑定课表、总开关与空态；整周形态下连堂课程按整块课程格依次展示课名、教师与地点，续段不再留白，长名称显示更完整
- 上课时间表编辑器（支持粘贴导入）

### 导出与备份
- 导出周课表图片 / PDF、整学期合并 PDF、**iCal 日历（.ics）**、**CSV**；iCal 按真实日历展开并体现节假日/补班，CSV 保持「星期 + 周次」模板（不含具体日期，不受节假日影响）
- **课表分享链接**：链接内容经 AES-256-GCM 加密与完整性校验，课程信息不以明文呈现，不含账户资料与设备标识，篡改即拒绝；同一课表每次生成的链接都不同。**该加密用于防止内容被直接查看，不等同于端到端加密**：密钥由应用内置种子派生，取得安装包者可还原解密方式，请勿用于传递敏感信息，详见 [隐私政策](PRIVACY.md#课表分享链接的保护级别)
- 二维码为白底深码、纠错级别 M 并带 4 模块静默区，可保存到相册（720×720 PNG → `Pictures/Polaris`）或直接分享图片到任意应用
- **一键备份/恢复**全部课表、计划、考试/DDL等事件与设置（`.polarisbackup` 文件）；背景图和头像仅保存图片 URI

### 应用内更新
- 「更多 → 更新 → 检查更新」手动检查稳定版更新；自动检查更新默认开启，每天最多一次，可随时关闭
- 检测到新版本时展示版本号、安装包大小、发布日期与更新说明，应用内下载并显示实时进度
- 下载完成后进行大小、SHA-256、包名/版本与签名四层校验，全部通过才交给系统安装器由你确认覆盖安装
- 更新数据来自 Polaris 官方 GitHub / Gitee Release 清单（`latest.json`），Gitee 不可用时自动切换 GitHub；检查与下载不携带任何课表或账户信息
- 更新说明与 `latest.json` 均由 `docs/releases/<versionName>.md` 生成，构建时校验标题与条目，避免弹窗滞留上一版内容

## 已知限制

- 节假日内置表 `app/src/main/res/raw/holidays.json` 每年需随国务院安排更新一次
- CSV 导出是「星期 + 周次」模板，不含具体日期，不体现节假日；需要按真实日期查看请用 iCal 导出
- 加密分享链接比明文版长约 6%（AES-GCM 的盐、IV 与认证标签固定占 36 字节），应用内二维码用于消除长度对使用的影响
- 课表分享链接不等同于端到端加密：密钥由应用内置种子派生，取得安装包者可还原解密方式；请勿用于传递敏感信息，详见 [隐私政策](PRIVACY.md#课表分享链接的保护级别)

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

### 版本基线（1.28.0）

| 项 | 值 |
|---|---|
| 线上稳定版 | `v1.27.14` / `versionCode 12714`（2026-09-14） |
| 本次发布 | `v1.28.0` / `versionCode 12800` |
| 版本号数据源 | `app/build.gradle` 的 `polarisVersionName` / `polarisVersionCode` |
| 发布说明 | `docs/releases/1.28.0.md`（标题必须为 `# Polaris 1.28.0`，1–20 条一级列表项） |
| 完整历史 | `CHANGELOG.md` |

`versionCode = major × 10000 + minor × 100 + patch`，且必须严格大于上一稳定清单。线上 `v1.27.14` 之后的多轮本地迭代**未单独发布、未打标签**，已全部合并进本次 `1.28.0`；逐版过程记录见 [内部迭代记录](docs/archive/internal-releases/INDEX.md)。

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
- [1.28.0 发布说明](docs/releases/1.28.0.md)
- [内部迭代记录（未发布版本）](docs/archive/internal-releases/INDEX.md)
- [视觉系统](docs/design/visual-system.md)
- [UI 回归清单](docs/qa/ui-regression-checklist.md)

## 官网仓库

官网是纯静态单页，位于 `web/` 目录但维护为**独立 Git 仓库**（主仓库通过 `.gitignore` 忽略该目录）：修改 `web/` 内文件请在其目录内单独提交与推送，不要用 `git add -f` 强行加入主仓库。本地 `web.zip` 为历史快照，以 `web/` 仓库为准。

## 隐私

完整隐私声明见 [PRIVACY.md](PRIVACY.md)。核心承诺：

- Polaris **默认开启每天最多一次的自动版本检查**，仅访问官方更新地址读取公开版本清单；只有你主动选择下载更新时才会获取安装包。课程、课表、学校、账户资料、设置及导入文件不会上传
- 相机权限仅用于「扫一扫」读取课表分享链接，预览帧在设备本地解码、不写入文件也不经过网络；拒绝权限后仍可用「粘贴」或「相册」导入
- 课表分享生成加密链接与二维码，本版不再导出 `.polaris` 文件；备份文件（`.polarisbackup` / `.ics` / `.csv`）由你主动导出并自行保管；分享链接不等同于端到端加密，详见 [PRIVACY.md](PRIVACY.md)
- 外部 AI 识别流程中，课表图片仅发送给你主动选择的 AI 应用，Polaris 不参与数据传输

## License

本项目代码以 [GPL-3.0](LICENSE) 开源，并附加**非商业条款（严禁商用）**：

- **允许**：个人学习、自用、修改，以及在同样遵循 GPL-3.0 与本"严禁商用"条款、保留署名的前提下非商业分发
- **禁止**：任何形式的商业用途，包括但不限于出售、付费捆绑、商业二次分发、应用商店收费上架、收费代配置 / 代导入 / 定制服务等
- 二次分发时必须保留本项目署名、[LICENSE](LICENSE) 原文与本"严禁商用"声明；发现商用侵权将依法追究

简单说：**Polaris 永远免费，严禁商用。**
