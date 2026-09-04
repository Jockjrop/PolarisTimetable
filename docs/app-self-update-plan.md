# Polaris 课程表应用内更新系统实施计划（方案 B）

> 文档状态：待实施  
> 编制日期：2026-09-04  
> 当前代码基线：`versionName 1.26.1`、`versionCode 12601`，以实施时 `app/build.gradle` 实际值为准  
> 适用分发渠道：GitHub Release 直装版  
> 核心方案：由发布流水线生成独立 `latest.json`，客户端按固定协议检查、下载、校验并交给 Android 系统安装器覆盖安装

## 1. 背景与决策

Polaris 当前通过 GitHub Actions 在推送 `v*` 标签时创建 GitHub Release，并上传构建产物。客户端尚未申请 `INTERNET` 权限，README 与隐私政策也明确承诺完全不联网。

本计划采用“方案 B”：客户端不解析 GitHub Release API 的通用响应，而是读取由 Polaris 发布流水线生成的专用更新清单 `latest.json`。清单协议由项目自身控制，客户端只依赖稳定字段；以后即使将 APK 镜像到 Gitee、对象存储或 CDN，也不需要重写版本判断与更新 UI。

本项目是普通第三方 Android 应用，更新过程不能静默完成。客户端负责检查、下载和安全校验，最终覆盖安装必须由 Android 系统安装器展示确认界面并由用户确认。

## 2. 目标

实施完成后，系统必须具备以下能力：

1. 用户可在“更多 → 关于”页面手动检查稳定版更新。
2. 用户可主动开启每日一次的自动检查，默认关闭。
3. 检测到新版本时展示版本号、安装包大小、发布日期和完整更新说明。
4. 用户确认后在应用内下载 APK，并展示实时进度、速度、已下载大小和取消入口。
5. 安装前验证下载完整性、包名、目标版本号和签名证书。
6. Android 8.0 及以上设备在未授权未知来源安装时，准确引导到当前应用的授权页面。
7. 所有安装都交给系统安装器确认，客户端不尝试静默安装。
8. 检查或下载失败不影响课表查看、PDF 导入、提醒、Widget 和其他本地功能。
9. GitHub Release 只能发布正式签名 APK；缺少签名材料时发布任务必须失败。
10. 发布流水线自动生成 APK SHA-256、`latest.json`，并验证标签、版本号、文件名与清单一致。
11. 更新网络行为透明、可关闭，不上传课程、账户、设置或设备文件。

## 3. 非目标

首个完整版本明确不包含以下能力：

- 静默安装、设备所有者安装、Root 安装。
- 增量包、差分包或热更新。
- 动态下发 Java 代码、Dex、资源包或解析器脚本。
- 强制阻断用户进入课表。
- Beta、Nightly、多分支更新通道。
- 应用市场版本自动安装；应用市场渠道应使用对应市场的更新机制。
- 后台周期任务、开机检查或高频轮询。
- 服务端用户画像、设备标识、安装量统计或更新埋点。
- 将 GitHub Token、签名私钥或发布凭据放入 APK。
- 为更新功能引入 Retrofit、OkHttp、WorkManager 或第三方更新 SDK。

## 4. 总体流程

```text
用户手动检查，或已开启自动检查且距离上次成功检查超过 24 小时
                                │
                                ▼
通过 HTTPS 获取 stable/latest.json
                                │
                     ┌──────────┴──────────┐
                     │                     │
                  请求失败              请求成功
                     │                     │
         手动检查显示错误；自动检查静默    ▼
                                      校验 JSON 协议
                                            │
                              ┌─────────────┴─────────────┐
                              │                           │
                           清单非法                    清单合法
                              │                           │
                         拒绝继续                         ▼
                                            比较远端与本地 versionCode
                                                        │
                                  ┌─────────────────────┴─────────────────────┐
                                  │                                           │
                         远端版本不更高                                  远端版本更高
                                  │                                           │
                         显示“已是最新版”                              展示更新弹窗
                                                                              │
                                                               ┌──────────────┴──────────────┐
                                                               │                             │
                                                            稍后/忽略                      下载
                                                                                             │
                                                                                             ▼
                                                                               下载到私有缓存临时文件
                                                                                             │
                                                                                             ▼
                                                                      SHA-256、APK、签名四层校验
                                                                                             │
                                                               ┌─────────────────────────────┴─────┐
                                                               │                                   │
                                                            校验失败                            校验成功
                                                               │                                   │
                                                       删除文件并报告原因                           ▼
                                                                                检查未知来源安装授权
                                                                                             │
                                                                            ┌────────────────┴───────────────┐
                                                                            │                                │
                                                                         未授权                            已授权
                                                                            │                                │
                                                               引导系统设置，返回后续装                       ▼
                                                                                              启动 Android 系统安装器
```

## 5. 发布源与 URL 约定

### 5.1 稳定入口

客户端内置唯一稳定清单地址：

```text
https://github.com/Jockjrop/PolarisTimetable/releases/latest/download/latest.json
```

GitHub 的 `latest` 路径只用于稳定版。预发布版本必须标记为 prerelease，并且不得替换稳定入口。

客户端每次请求增加按天变化的查询参数以降低中间缓存返回陈旧清单的概率：

```text
https://github.com/Jockjrop/PolarisTimetable/releases/latest/download/latest.json?day=20260904
```

请求必须禁止明文 HTTP，重定向后的最终地址也必须是 HTTPS。允许的主机限定为：

- `github.com`
- `objects.githubusercontent.com`
- `release-assets.githubusercontent.com`

如未来迁移分发源，必须通过发版修改客户端允许列表，不能由远端清单任意扩大允许主机。

### 5.2 Release 资产命名

每个稳定 Release 必须且只能包含一个面向更新系统的正式 APK：

```text
Polaris-<versionName>-release.apk
```

同时包含：

```text
latest.json
Polaris-<versionName>-release.apk.sha256
```

示例：

```text
Polaris-1.27.0-release.apk
Polaris-1.27.0-release.apk.sha256
latest.json
```

Debug APK、测试 APK、未签名 APK 不得作为稳定 Release 资产上传。

## 6. `latest.json` 协议

### 6.1 完整示例

```json
{
  "schemaVersion": 1,
  "channel": "stable",
  "packageName": "com.polaris.timetable",
  "versionCode": 12700,
  "versionName": "1.27.0",
  "minSdk": 23,
  "minSupportedVersionCode": 12601,
  "publishedAt": "2026-09-10T12:00:00Z",
  "apk": {
    "fileName": "Polaris-1.27.0-release.apk",
    "url": "https://github.com/Jockjrop/PolarisTimetable/releases/download/v1.27.0/Polaris-1.27.0-release.apk",
    "size": 18342190,
    "sha256": "5c99f983378f6c9d5a4ed50670f8e64207075b5051825c922ad98af9f2112c24"
  },
  "releaseNotes": [
    "新增应用内安全更新功能",
    "优化更新失败后的错误提示",
    "修复部分设备课前提醒恢复异常"
  ],
  "releaseNotesUrl": "https://github.com/Jockjrop/PolarisTimetable/releases/tag/v1.27.0",
  "required": false
}
```

### 6.2 字段约束

| 字段 | 类型 | 必填 | 约束与用途 |
|---|---:|---:|---|
| `schemaVersion` | integer | 是 | 首版固定为 `1`；客户端拒绝高于自身支持值的协议 |
| `channel` | string | 是 | 首版只接受 `stable` |
| `packageName` | string | 是 | 必须严格等于 `com.polaris.timetable` |
| `versionCode` | integer | 是 | 必须大于 0；是否更新只依据该值判断 |
| `versionName` | string | 是 | 用于展示与文件名核对，不参与大小比较 |
| `minSdk` | integer | 是 | 若高于当前设备 API，则不允许下载并给出兼容性提示 |
| `minSupportedVersionCode` | integer | 是 | 用于提示旧版本跨度过大；首版不据此强制封锁应用 |
| `publishedAt` | string | 是 | ISO-8601 UTC 时间，用于展示和审计 |
| `apk.fileName` | string | 是 | 必须符合正式 APK 命名规则，且不包含路径分隔符 |
| `apk.url` | string | 是 | 必须为 HTTPS 且主机位于内置允许列表 |
| `apk.size` | integer | 是 | 必须大于 0 且不超过 200 MiB |
| `apk.sha256` | string | 是 | 64 位小写十六进制字符串 |
| `releaseNotes` | string array | 是 | 1 至 20 项；单项最长 200 字符；纯文本展示 |
| `releaseNotesUrl` | string | 是 | HTTPS GitHub Release 页面，用于查看完整说明 |
| `required` | boolean | 是 | 首版仅改变提示强度，不阻断课表使用 |

### 6.3 解析规则

- 未知字段忽略，以便协议向后兼容。
- 缺失必填字段、类型错误、数值越界、非法 URL 或非法哈希时整份清单无效。
- JSON 最大响应体限制为 256 KiB，超过限制立即终止读取。
- 字符集固定为 UTF-8。
- `versionName` 必须符合 `major.minor.patch`，但更新判断仍只比较 `versionCode`。
- `versionCode` 必须符合项目映射规则 `major × 10000 + minor × 100 + patch`。
- `apk.fileName` 中的版本号必须与 `versionName` 相同。
- APK URL 中的标签必须为 `v<versionName>`。
- 客户端不得渲染 HTML；更新说明按纯文本逐条展示。

## 7. 客户端结构

新增独立 `update` 包，避免把网络、下载和安装逻辑写入 `MainActivity`：

```text
app/src/main/java/com/polaris/timetable/update/
├── UpdateInfo.java
├── UpdateJsonParser.java
├── UpdateCheckResult.java
├── UpdateRepository.java
├── UpdatePolicy.java
├── UpdatePreferences.java
├── UpdateCoordinator.java
├── UpdateDownloadController.java
├── UpdateDownloadState.java
├── ApkVerifier.java
├── UpdateInstaller.java
└── UpdateError.java
```

### 7.1 类职责

#### `UpdateInfo`

不可变数据模型，完整表达清单字段。构造后不允许字段被 UI 修改。

#### `UpdateJsonParser`

使用 Android 自带 `org.json` 解析清单，执行第 6 节全部结构和边界校验。不得访问网络、磁盘或 UI，确保可在本地单元测试中覆盖。

#### `UpdateCheckResult`

使用明确结果类型表达：

- `UPDATE_AVAILABLE`
- `UP_TO_DATE`
- `DEVICE_UNSUPPORTED`
- `IGNORED_VERSION`
- `NETWORK_ERROR`
- `HTTP_ERROR`
- `INVALID_METADATA`
- `UNSUPPORTED_SCHEMA`
- `CANCELLED`

不得用 `null` 同时表示无更新、失败和取消。

#### `UpdateRepository`

使用 `HttpsURLConnection` 请求 `latest.json`：

- 连接超时 10 秒。
- 读取超时 15 秒。
- 最大 5 次 HTTPS 重定向。
- `Accept: application/json`。
- 明确的 `User-Agent: PolarisTimetable/<versionName> Android/<sdk>`。
- 仅接受 HTTP 200。
- 限制响应体为 256 KiB。
- 正确关闭连接和输入流。
- 不记录完整下载 URL 查询参数、响应正文或设备信息。

#### `UpdatePolicy`

集中实现更新判断：

- `remoteVersionCode <= localVersionCode`：已是最新版。
- `remoteVersionCode > localVersionCode`：存在更新。
- `remoteMinSdk > deviceSdk`：设备不兼容。
- 自动检查时命中用户忽略的版本：不弹窗。
- 手动检查时即使版本被忽略，也允许重新展示。
- `required=true`：使用更醒目的安全更新提示，但保留“稍后”入口。

#### `UpdatePreferences`

独立 SharedPreferences 文件 `polaris_update`，只保存：

| 键 | 类型 | 含义 |
|---|---|---|
| `auto_check_enabled` | boolean | 是否允许自动检查，默认 `false` |
| `last_successful_check_at` | long | 最近一次成功获得并解析清单的时间 |
| `ignored_version_code` | int | 用户忽略的稳定版版本号，默认 `0` |
| `pending_apk_path` | string | 已校验、等待安装的 APK 路径 |
| `pending_version_code` | int | 待安装 APK 的版本号 |

不得存储课程、学号、学校名称、设备唯一标识、IP 或 GitHub 凭据。

#### `UpdateCoordinator`

作为单一业务入口，负责：

- 防止重复检查。
- 区分手动检查和自动检查。
- 调用 Repository、Parser、Policy。
- 将结果切回主线程通知 UI。
- 启动下载、取消下载、恢复待安装状态。
- Activity 销毁后不更新失效 View。

它不直接创建具体 View，以便后续测试业务状态。

#### `UpdateDownloadController`

首版采用应用进程内、用户主动触发的 HTTPS 下载，不引入系统 `DownloadManager` 或后台调度依赖：

- 使用单线程 `ExecutorService`。
- 下载到 `getCacheDir()/updates/<fileName>.part`。
- 响应成功且校验通过后原子重命名为 `.apk`。
- 下载前确认可用空间至少为 `apk.size + 50 MiB`。
- 单个 APK 上限 200 MiB。
- 每 250 毫秒或每增加 256 KiB 汇报一次进度，避免高频刷新 UI。
- 支持原子取消标志；取消后关闭流并删除 `.part`。
- 应用进程被系统终止后，临时文件视为失败，下次启动清理并重新下载。
- 下载期间允许应用进入后台，但不保证进程被杀后续传；首版明确采用可重试而非断点续传。
- 最多保留一个已验证 APK；开始新下载前清理旧版本文件。

选择该策略的原因是 APK 体积有限、下载由用户明确触发，并且可以把文件始终保存在应用私有缓存中。首版不为一次低频下载引入前台服务及额外生命周期复杂度。

#### `UpdateDownloadState`

完整状态机：

- `IDLE`
- `PREPARING`
- `DOWNLOADING`
- `VERIFYING_HASH`
- `VERIFYING_APK`
- `READY_TO_INSTALL`
- `CANCELLED`
- `FAILED`

状态转换必须单向且可观察；同一时刻只允许一个下载任务。

#### `ApkVerifier`

负责第 10 节的全部安装前校验，并返回具体失败原因，不负责显示弹窗。

#### `UpdateInstaller`

负责：

- Android 8.0 以上检查 `canRequestPackageInstalls()`。
- 未授权时打开 `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`，URI 指向当前包。
- 从授权页返回后再次检查，不假定用户已授权。
- 用专用 FileProvider 生成 APK URI。
- 使用 `Intent.ACTION_INSTALL_PACKAGE` 启动系统安装器。
- 添加 `FLAG_GRANT_READ_URI_PERMISSION`。
- 没有可处理安装 Intent 的系统组件时显示明确错误。
- 启动安装器前再次确认 APK 文件存在且校验状态仍有效。

## 8. Manifest 与文件共享

### 8.1 权限

新增：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

`INTERNET` 是普通权限，没有运行时授权弹窗。`REQUEST_INSTALL_PACKAGES` 只允许应用请求系统安装流程，并不授予静默安装能力。

### 8.2 专用 FileProvider

不复用现有 `ExportFileProvider`。Manifest 新增：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.updates"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/update_file_paths" />
</provider>
```

新增 `res/xml/update_file_paths.xml`，只暴露更新缓存子目录：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path
        name="verified_updates"
        path="updates/" />
</paths>
```

Provider authority 固定为 `${applicationId}.updates`，不能扩大到整个缓存目录、内部文件目录或外部存储根目录。

## 9. UI 与交互

### 9.1 设置入口

在 `SettingsPageBuilder.createMoreSettingsPanel()` 的“关于”卡片中，将顺序调整为：

1. 版本信息。
2. 检查更新。
3. 自动检查更新开关。
4. 联系我们。
5. GitHub 项目地址。

“检查更新”副标题按状态显示：

- 默认：`当前版本 1.26.1`
- 检查中：`正在检查…`
- 无更新：`已是最新版 · 刚刚`
- 有更新：`发现 1.27.0`
- 失败：`检查失败，点击重试`

自动检查开关第一次开启时展示一次说明：

> 开启后，Polaris 每天最多访问一次官方更新地址，仅获取版本信息，不会上传课表或其他个人数据。

### 9.2 更新可用弹窗

弹窗必须展示：

- 标题：`发现新版本 1.27.0`
- 当前版本与目标版本。
- 发布日期。
- APK 大小。
- 更新说明列表。
- “查看完整说明”链接。
- 操作：`稍后`、`忽略此版本`、`下载并安装`。

`required=true` 时标题改为“发现重要安全更新”，隐藏“忽略此版本”，但仍保留“稍后”。

### 9.3 下载弹窗

下载阶段展示：

- 百分比。
- 已下载大小与总大小。
- 当前阶段：下载中、校验完整性、校验安装包。
- 取消按钮。

下载完成后按钮变为“安装”。安装授权未开启时，按钮文案为“允许安装更新”，点击后进入系统设置。

### 9.4 错误文案

至少区分：

- 网络不可用。
- 连接超时。
- GitHub 服务暂时不可用。
- 更新信息格式错误。
- 更新协议版本过新。
- 设备系统版本不受目标 APK 支持。
- 存储空间不足。
- 下载被取消。
- 文件大小不符。
- 文件校验失败。
- APK 包名错误。
- APK 版本错误。
- APK 签名不一致。
- 系统没有可用安装器。
- 用户未授权安装未知应用。

错误信息不得直接展示异常堆栈、缓存绝对路径或内部实现类名。

### 9.5 自动检查体验

- 默认关闭。
- 只在 `MainActivity` 冷启动并稳定显示主页面约 5 秒后触发。
- 距离上次成功检查不足 24 小时不联网。
- 无更新或失败时不弹 Toast、不抢焦点。
- 有更新时每个版本每个进程最多弹一次。
- 用户正在导入 PDF、编辑课程或操作弹窗时延后显示。
- 手动检查不受 24 小时间隔和忽略版本限制。

## 10. 安全模型

### 10.1 安装前强制校验顺序

下载结束后必须依次执行：

1. 文件实际长度等于清单 `apk.size`。
2. 流式计算 SHA-256，结果等于 `apk.sha256`。
3. 使用 `PackageManager.getPackageArchiveInfo()` 读取 APK 信息。
4. APK 包名严格等于当前应用包名。
5. APK `versionCode` 严格等于清单 `versionCode`。
6. APK `versionName` 严格等于清单 `versionName`。
7. APK `versionCode` 严格大于已安装版本。
8. APK `minSdkVersion` 不高于设备 API。
9. 读取归档 APK 的签名证书摘要。
10. 读取当前已安装 App 的签名证书摘要。
11. 两者签名集合按 Android 版本兼容方式比较，必须确认更新 APK 由认可的发布证书签名。

任一步失败都必须：

- 不启动系统安装器。
- 删除 `.part` 和目标 `.apk`。
- 清空 `pending_apk_path` 与 `pending_version_code`。
- 向用户展示可理解的失败原因。

### 10.2 签名证书连续性

发布证书是自更新系统的根信任。必须执行：

- 正式版本永久使用同一签名身份，或使用 Android 支持且经过规划的证书轮换机制。
- Keystore 不提交到 Git。
- CI 中仅保存 Base64 编码的加密环境 Secret 仍不等于备份；项目维护者必须另有离线备份。
- 记录证书 SHA-256 指纹，并在发布任务中核对。
- Release 不允许回退到 Debug 签名。
- 签名材料缺失、密码错误或指纹不符时立即终止发布。

### 10.3 威胁与防护

| 威胁 | 防护 |
|---|---|
| 网络中间人替换清单或 APK | 仅 HTTPS、主机允许列表、APK SHA-256、APK 签名校验 |
| GitHub Release 误传 Debug APK | CI 禁止 fallback、包名/构建类型/签名指纹检查 |
| 清单与 APK 版本不一致 | 文件名、标签、清单、APK 内版本四方核对 |
| 下载中断产生半包 | `.part` 临时文件、成功后原子重命名 |
| 恶意清单指向超大文件 | 200 MiB 上限、Content-Length 和累计字节双限制 |
| 路径穿越 | 文件名只取清单 basename，并按固定正则验证 |
| 降级攻击 | 远端和 APK `versionCode` 都必须大于本地 |
| 重放旧清单 | `versionCode` 单调比较，旧清单只能得到“无更新” |
| HTML/脚本注入 | 更新说明只按纯文本渲染 |
| 更新文件被其他应用替换 | 应用私有缓存、专用非导出 FileProvider、安装前即时复验 |

### 10.4 首版不增加独立清单签名的理由

`latest.json` 的 SHA-256 字段主要负责传输完整性，真正的安装身份由 Android APK 发布证书决定。即使攻击者能够修改清单并替换 APK，只要无法使用 Polaris 发布证书签名，就不能通过客户端签名校验，也不能覆盖已安装正式版本。

因此首版不额外维护第二套 RSA 清单签名密钥，避免引入密钥轮换和双重信任源。若未来更新源不再由 GitHub Release 承载，或需要跨域镜像自动切换，应单独设计签名清单协议，而不是在本次范围内增加未经演练的密钥系统。

## 11. GitHub Actions 发布流水线

### 11.1 现有流程必须修正

当前 `.github/workflows/build.yml` 在没有正式签名 Secret 时会构建 Debug APK 作为 Release fallback。启用自更新前必须删除该路径，改成：

- 缺少 `POLARIS_RELEASE_KEYSTORE_B64`：发布 job 失败。
- 缺少任一密码或 alias：发布 job 失败。
- Release 构建没有生成正式 APK：发布 job 失败。
- 禁止 `app/build/outputs/apk/debug/` 出现在 Release 上传路径中。

### 11.2 标签和版本检查

发布前读取 `app/build.gradle` 并检查：

- Git 标签必须为 `v<versionName>`。
- `versionName` 必须符合语义化版本三段式。
- `versionCode` 必须符合 `major × 10000 + minor × 100 + patch`。
- `versionCode` 必须大于仓库最近稳定 Release 清单中的值。
- APK 文件名必须为 `Polaris-<versionName>-release.apk`。

任一不一致时不创建 Release。

### 11.3 推荐发布步骤

1. Checkout 标签对应提交。
2. 配置 JDK 17 和 Android SDK。
3. 运行 `:app:testDebugUnitTest`。
4. 运行 `:app:lintDebug`，保持 0 error 门槛。
5. 校验所有签名 Secret 存在。
6. 解码临时 keystore。
7. 执行 `:app:assembleRelease`。
8. 使用 `apksigner verify --verbose --print-certs` 验证 APK。
9. 核对 APK signer SHA-256 与仓库配置的期望指纹。
10. 使用 `apkanalyzer manifest application-id` 核对包名。
11. 使用 `apkanalyzer manifest version-code` 和 `version-name` 核对版本。
12. 计算 APK 字节数和 SHA-256。
13. 根据 Release notes 与构建信息生成 `latest.json`。
14. 使用脚本重新解析 `latest.json` 并执行协议校验。
15. 将 APK、`.sha256` 和 `latest.json` 上传到同一个 GitHub Release。
16. 删除 runner 工作区中的临时 keystore。

### 11.4 清单生成脚本

新增：

```text
tools/generate-update-manifest.ps1
```

脚本输入必须显式提供：

- APK 路径。
- `versionCode`。
- `versionName`。
- Git 标签。
- Release notes 文件路径。
- 输出路径。

脚本输出固定为 UTF-8 `latest.json` 和 `.sha256` 文件。脚本遇到空更新说明、非法版本、错误文件名、APK 不存在或哈希计算失败时返回非零退出码。

为了保证 Linux GitHub runner 可执行，若 PowerShell Core 可用则直接运行 `.ps1`；否则增加等价的 Gradle 生成任务。两种实现只能保留一个事实来源，避免两个生成器产生不同协议。

### 11.5 Release notes 来源

发布者在创建标签前准备版本说明文件：

```text
docs/releases/<versionName>.md
```

生成器读取其中的一级无序列表，转换为 `releaseNotes` 数组。该文件同时作为 GitHub Release 正文来源，避免清单说明与 Release 页面不一致。

## 12. 隐私与合规调整

启用更新后，以下现有表述不再准确，必须随功能一并修改：

- README 中“不申请网络权限”。
- README 隐私章节中“所有数据只保存在本机”的网络语境说明。
- `PRIVACY.md` 一句话总结。
- `PRIVACY.md` 权限说明表。

新的核心表述应为：

> Polaris 默认不会自动联网。用户手动检查更新或主动开启自动检查更新后，应用仅访问官方更新地址以获取版本信息和安装包。课程、课表、学校、账户资料、设置及导入文件不会上传。

隐私政策权限表新增：

| 权限 | 用途 |
|---|---|
| 网络（INTERNET） | 用户触发或授权自动执行的版本检查与官方 APK 下载 |
| 请求安装应用（REQUEST_INSTALL_PACKAGES） | 将已验证的更新 APK 交给 Android 系统安装器，由用户确认安装 |

设置页首次开启自动检查时必须提供与隐私政策一致的短说明。不得使用更新请求附带课程数量、学校、学期、账户名称、设备序列号或广告标识。

## 13. 计划修改文件

### 13.1 新增文件

```text
app/src/main/java/com/polaris/timetable/update/UpdateInfo.java
app/src/main/java/com/polaris/timetable/update/UpdateJsonParser.java
app/src/main/java/com/polaris/timetable/update/UpdateCheckResult.java
app/src/main/java/com/polaris/timetable/update/UpdateRepository.java
app/src/main/java/com/polaris/timetable/update/UpdatePolicy.java
app/src/main/java/com/polaris/timetable/update/UpdatePreferences.java
app/src/main/java/com/polaris/timetable/update/UpdateCoordinator.java
app/src/main/java/com/polaris/timetable/update/UpdateDownloadController.java
app/src/main/java/com/polaris/timetable/update/UpdateDownloadState.java
app/src/main/java/com/polaris/timetable/update/ApkVerifier.java
app/src/main/java/com/polaris/timetable/update/UpdateInstaller.java
app/src/main/java/com/polaris/timetable/update/UpdateError.java
app/src/main/res/xml/update_file_paths.xml
app/src/test/java/com/polaris/timetable/update/UpdateJsonParserTest.java
app/src/test/java/com/polaris/timetable/update/UpdatePolicyTest.java
app/src/test/java/com/polaris/timetable/update/UpdatePreferencesTest.java
app/src/androidTest/java/com/polaris/timetable/update/UpdateEntryTest.java
app/src/androidTest/java/com/polaris/timetable/update/UpdateInstallerTest.java
tools/generate-update-manifest.ps1
docs/releases/<实施版本号>.md
```

### 13.2 修改文件

| 文件 | 修改内容 |
|---|---|
| `app/build.gradle` | 按实际交付版本递增版本号；如需要，为测试注入更新清单地址 |
| `app/src/main/AndroidManifest.xml` | 增加网络、安装权限和专用 FileProvider |
| `SettingsPageBuilder.java` | 增加检查更新行、自动检查开关和 Host 回调 |
| `MainActivity.java` | 只接入 UpdateCoordinator 生命周期与设置页 Host 回调，不承载协议或下载实现 |
| `strings.xml` | 增加全部更新 UI、状态、错误和无障碍文案 |
| `.github/workflows/build.yml` | 删除 Debug fallback，增加版本、签名、APK、清单验证和正式资产上传 |
| `README.md` | 更新网络权限、构建和 Release 发布说明 |
| `PRIVACY.md` | 更新联网行为与权限用途 |
| `docs/test-plan.md` | 纳入更新系统回归项；若该文档已不再维护，则将回归项保留在本文验收章节 |

如果实施时文件结构已经变化，以功能职责匹配为准，但不得把上述新增业务逻辑重新塞入 `MainActivity`。

## 14. 测试计划

### 14.1 单元测试：清单解析

必须覆盖：

1. 完整合法清单解析成功。
2. 未知字段被忽略。
3. 每一个必填字段缺失时失败。
4. 字段类型错误时失败。
5. `schemaVersion` 过高时返回协议不支持。
6. channel 非 stable 时拒绝。
7. packageName 错误时拒绝。
8. versionCode 为 0、负数或与语义版本映射不符时拒绝。
9. versionName 非三段式时拒绝。
10. HTTP URL 被拒绝。
11. 非允许主机被拒绝。
12. 文件名包含 `/`、`\` 或路径穿越片段时拒绝。
13. size 为 0、负数或超过 200 MiB 时拒绝。
14. SHA-256 长度或字符非法时拒绝。
15. releaseNotes 为空、过多或单项过长时拒绝。
16. 响应体超过 256 KiB 时拒绝。
17. 文件名、URL 标签与 versionName 不一致时拒绝。

### 14.2 单元测试：版本策略

必须覆盖：

- 远端版本大于本地版本。
- 远端版本等于本地版本。
- 远端版本小于本地版本。
- versionName 看似更高但 versionCode 不高。
- 设备 API 低于远端 minSdk。
- 自动检查命中忽略版本。
- 手动检查绕过忽略版本。
- required 版本不允许忽略但仍可稍后。
- 上次成功检查不足 24 小时。
- 系统时钟回拨时不产生高频检查。

### 14.3 单元测试：下载与哈希

使用本地假数据流或测试 HTTP server 覆盖：

- 正常下载及进度。
- Content-Length 缺失。
- 声明长度与实际长度不同。
- 下载中断。
- 用户取消。
- 超时。
- 重定向过多。
- 重定向到 HTTP。
- 重定向到非允许主机。
- 超过最大文件限制。
- SHA-256 匹配。
- SHA-256 不匹配。
- `.part` 清理与成功后的原子重命名。
- 同时发起两次下载时只保留一个任务。

### 14.4 仪器测试

必须覆盖：

- “更多 → 关于 → 检查更新”入口可达。
- 检查中防重复点击。
- 无更新、有更新、网络错误三类 UI。
- 自动检查开关默认关闭。
- 第一次开启时出现隐私说明。
- 更新弹窗在手机和横屏平板布局不溢出。
- 大字体下版本号、说明和按钮仍可读。
- 深色模式下进度、警告和错误颜色符合主题 token。
- 下载取消后 UI 恢复。
- 未授权未知来源安装时生成正确设置 Intent。
- 已授权时生成正确安装 Intent、authority 和读权限 flag。
- Activity 不可用时异步回调不访问失效 View。

真实系统安装确认不应在常规 CI 中执行；通过 Intent、URI、包信息校验及至少一台实体设备手工验收覆盖。

### 14.5 CI 脚本测试

必须覆盖：

- 正确版本与标签生成合法清单。
- 标签和 versionName 不一致时失败。
- versionCode 映射错误时失败。
- APK 不存在时失败。
- Debug 签名或错误签名指纹时失败。
- APK 包名不匹配时失败。
- APK 内版本与 Gradle 不一致时失败。
- 空 Release notes 时失败。
- 生成的 SHA-256 与独立命令计算结果一致。
- Release 上传集合不包含 Debug APK。

### 14.6 手工设备矩阵

至少验证：

| 系统范围 | 重点 |
|---|---|
| Android 6–7 | 旧版安装 Intent、FileProvider、覆盖安装 |
| Android 8–10 | 未知来源按应用授权流程 |
| Android 11–12 | 包可见性、安装器跳转、后台返回 |
| Android 13–14 | 通知拒绝不影响主动下载和安装流程 |
| Android 15 / targetSdk 35 | 最新安装限制、edge-to-edge 弹窗与返回行为 |
| 手机竖屏 | 标准主流程 |
| 手机横屏 | 下载弹窗与系统设置返回 |
| `sw600dp` 平板 | 关于页和更新说明布局 |

网络场景至少验证 Wi-Fi、移动网络、断网、弱网、下载中切换网络和 GitHub 无法访问。

## 15. 分阶段施工

每一阶段都保持主分支可构建、现有课表流程可用。阶段间可以分提交，但完整功能只在所有验收门槛通过后发布。

### 阶段 0：发布链安全加固

目标：先保证更新源永远不会发布错误签名或 Debug APK。

工作：

- 删除 Debug Release fallback。
- 增加标签与版本一致性检查。
- 增加 APK 包名、版本、签名指纹检查。
- 增加 Release notes 规范。
- 增加清单生成与自校验脚本。
- 用非公开测试标签验证资产集合，确认后删除测试 Release。

退出条件：缺少签名、标签错误、APK 错误、清单错误均能阻止发布。

### 阶段 1：检查更新闭环

目标：客户端能安全获取、解析、判断和展示更新，但下载按钮暂时只打开 Release 页面，不请求安装权限。

工作：

- 实现 UpdateInfo、Parser、Repository、Policy、Preferences、Coordinator。
- 增加手动检查和默认关闭的自动检查。
- 增加更新弹窗、错误态和忽略版本。
- 更新 README 与隐私政策中的联网说明。
- 完成解析、策略和 UI 测试。

退出条件：合法、非法、无更新、有更新、无网络场景行为稳定，课表主流程无回归。

### 阶段 2：安全下载闭环

目标：在应用私有缓存中下载并验证 APK。

工作：

- 实现 DownloadController 与状态机。
- 实现大小限制、临时文件、取消、清理和 SHA-256。
- 实现 APK 包名、版本与签名证书验证。
- 实现进度 UI 和全部失败文案。
- 补齐下载、哈希及 APK 校验测试。

退出条件：任何损坏、伪造、降级、错误包名或错误签名 APK 都无法进入安装阶段。

### 阶段 3：系统安装闭环

目标：通过系统授权和安装器完成正式覆盖安装。

工作：

- 增加 REQUEST_INSTALL_PACKAGES 权限。
- 增加专用 FileProvider。
- 实现 Android 版本兼容的授权与安装 Intent。
- 增加待安装状态恢复和旧 APK 清理。
- 在实体设备上验证从旧正式版本升级到新正式版本。

退出条件：Android 6 至 Android 15 的代表设备可完成升级，课程和设置在覆盖安装后完整保留，提醒与 Widget 能在 `MY_PACKAGE_REPLACED` 后恢复。

### 阶段 4：正式发布与观察

目标：以一个稳定版本交付完整自更新功能。

工作：

- 按实施时版本基线做语义化 minor 版本升级，并按公式递增 versionCode。
- 运行单测、lint、仪器 smoke 与全量关键用例。
- 从前一个正式签名版本执行真实升级。
- 发布正式标签和 Release。
- 用正式旧版本检查并安装新版本。
- 核对 GitHub Release 的三项资产与 `latest.json` 内容。
- 发布后保留前一正式 APK，支持人工回退安装说明；Android 默认不允许低 versionCode 覆盖，回退需先备份并卸载，因此不在 App 内提供降级按钮。

退出条件：第 16 节所有验收项通过。

## 16. 最终验收标准

### 16.1 功能验收

- [ ] 手动检查能正确识别比本地更高的 versionCode。
- [ ] 相同或更低 versionCode 不提示更新。
- [ ] 自动检查默认关闭，用户开启后最多每日一次。
- [ ] 更新说明、大小、发布日期和目标版本完整展示。
- [ ] 下载进度准确，可取消，可在失败后重试。
- [ ] 下载完成后通过全部校验才能进入安装流程。
- [ ] 未知来源未授权时引导正确，返回后可继续安装。
- [ ] 系统安装器能完成同签名覆盖升级。
- [ ] 升级后所有课表、设置、背景引用、提醒配置和 Widget 绑定仍存在。
- [ ] 检查或下载失败不阻断 App 其他功能。

### 16.2 安全验收

- [ ] HTTP 清单、HTTP APK 和非允许主机全部被拒绝。
- [ ] 超大清单和超大 APK 被拒绝。
- [ ] SHA-256 不匹配的 APK 被删除。
- [ ] 错包名 APK 被删除。
- [ ] 低版本或同版本 APK 被删除。
- [ ] Debug 签名或其他证书签名 APK 被删除。
- [ ] FileProvider 只暴露 `cache/updates/`。
- [ ] APK URI 仅临时授予系统安装器读取权限。
- [ ] Release job 不存在 Debug fallback。
- [ ] 正式发布证书指纹在 CI 中得到验证。
- [ ] APK 和源码中不存在 GitHub Token、keystore 或密码。

### 16.3 质量验收

- [ ] `:app:testDebugUnitTest` 通过。
- [ ] `:app:lintDebug` 保持 0 error。
- [ ] 主界面 instrumentation smoke 通过。
- [ ] 更新模块新增单元与仪器测试通过。
- [ ] 手机、平板、深浅色、大字体视觉检查通过。
- [ ] PDF 导入、周课表、提醒、Widget、备份恢复回归通过。
- [ ] 实体设备完成至少一次正式签名跨版本升级。
- [ ] `latest.json` 可由独立脚本验证。
- [ ] README、隐私政策和实际网络行为一致。

## 17. 风险与处置

| 风险 | 影响 | 处置 |
|---|---|---|
| 发布证书丢失 | 无法覆盖更新现有安装 | 上线前完成离线加密备份和恢复演练 |
| CI 上传 Debug APK | 正式用户安装失败或信任混乱 | 删除 fallback，签名指纹不符即失败 |
| GitHub 在部分网络不可达 | 检查或下载失败 | 不影响本地功能，提供 Release 页面和浏览器下载作为人工备选 |
| `latest` 缓存陈旧 | 新版本短时间检测不到 | 按天 cache-busting、手动重试、发布后实机验证 |
| 用户拒绝未知来源授权 | 无法由 App 发起安装 | 保留已验证 APK，明确说明并允许再次授权 |
| 下载时进程被杀 | 下载中断 | 清理 `.part`，下次由用户重新下载，不伪装为成功 |
| 存储空间不足 | 下载或校验失败 | 下载前预检，保留 50 MiB 安全余量 |
| 清单协议升级 | 老客户端无法解析 | schemaVersion 明确拒绝并引导打开 Release 页面 |
| 更新功能破坏隐私定位 | 用户信任下降 | 默认关闭自动检查、明确网络用途、零业务数据上报 |
| MainActivity 继续膨胀 | 长期维护成本升高 | 主 Activity 只保留入口和生命周期委托，业务集中在 update 包 |
| 应用市场审核限制 REQUEST_INSTALL_PACKAGES | 市场包被拒 | 若增加应用市场渠道，拆分 GitHub 与市场 flavor，市场包移除该权限并采用市场更新机制 |

## 18. 回滚与故障恢复

### 18.1 发布前

- Release 创建前完成旧正式版到候选正式版的实体机升级。
- Release 资产未全部生成并验证前，不更新稳定入口。
- 发布 job 任一步失败时不得留下带正式标签但资产不完整的 Release。

### 18.2 发布后发现问题

- 立即将错误 Release 标记为非最新或删除其 `latest.json`，阻止更多客户端发现。
- 发布修复版本时使用更高 versionCode，不能复用原版本号覆盖资产。
- 已下载但未安装的错误版本，可通过移除远端资产降低继续下载概率；客户端安装前仍依赖本地签名和版本校验，无法依赖远端撤回实现绝对召回。
- 已安装错误版本的用户通过更高版本修复更新恢复。
- 不提供自动降级。用户如需回退，必须先导出 `.polarisbackup`，卸载后安装旧版并恢复备份，同时明确卸载会清除本地数据。

### 18.3 客户端自恢复

- 启动时清理超过 48 小时的 `.part` 文件。
- 已验证 APK 若版本不再高于当前安装版本则删除。
- `pending_apk_path` 指向不存在文件时清空记录。
- 系统安装取消后保留已验证 APK 24 小时，允许用户再次安装。
- APK 超过 24 小时后安装前重新获取清单并复验；无法联网时仍执行本地全部校验并提示清单可能已过期，由用户明确确认是否继续。

## 19. 版本与交付规则

本文是纯设计文档，不构建 APK，因此本次不修改版本号。

正式实施并构建交付 APK 时：

- 以 `app/build.gradle` 当时实际版本为基线。
- 应用内更新属于功能增强，建议递增 minor 版本。
- `versionCode` 严格按 `major × 10000 + minor × 100 + patch` 映射。
- `versionCode` 只能递增，不能因分支合并回退。
- Git 标签、Gradle 版本、APK 内版本、APK 文件名、Release 标题和 `latest.json` 必须一致。

## 20. 推荐实施顺序结论

实施顺序固定为：

1. 先封死 Debug Release 和错误签名发布路径。
2. 再上线只读检查更新能力并完成隐私披露。
3. 再增加私有目录下载与四层 APK 校验。
4. 最后接入未知来源授权和系统安装器。
5. 通过实体机跨版本升级后再发布稳定版。

该顺序把发布源安全放在客户端自动安装之前。任何阶段未达到退出条件，都不得提前开放下一阶段入口。
