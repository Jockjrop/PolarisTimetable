# Polaris 应用内更新实施审查与改进计划

> 状态：实施后审查完成，改进待执行  
> 审查日期：2026-09-04  
> 审查基线：`versionName 1.27.0`、`versionCode 12700`  
> 审查范围：更新协议、网络请求、APK 下载、APK 校验、系统安装、UI 接入、GitHub Release、隐私文档与测试  
> 发布结论：当前实现不可作为稳定版发布，须先完成第 4 节发布阻塞项

## 1. 已实现能力

当前实现已经形成完整业务链，而非空壳：

- 新增独立 `update` 包，网络、协议、策略、下载、校验与安装职责基本分离。
- 设置页提供手动检查和默认关闭的自动检查入口。
- 使用专用 `latest.json` 协议，不解析 GitHub 通用 Release API。
- 检查请求限制 HTTPS、主机白名单、响应体大小、超时和重定向次数。
- 下载采用 `.part` 临时文件、大小校验、SHA-256 校验和完成后重命名。
- 安装前检查 APK 包名、版本号、最低系统版本和签名证书。
- 发布流水线禁止 Debug fallback，并检查 APK 包名、版本和发布证书指纹。
- README、隐私政策、测试计划和版本说明已同步更新。
- 当前版本已按功能增强升级为 `1.27.0 / 12700`。

## 2. 验证结果

### 2.1 已执行

| 验证项 | 结果 | 说明 |
|---|---|---|
| 更新模块 JVM 单元测试 | PASS | 48 个测试，0 failure，0 skipped |
| `:app:lintDebug` | PASS/WARN | 0 error、4 warning；项目既有基线为 3 warning |
| `:app:assembleDebug` | PASS | Debug APK 构建成功 |
| `git diff --check` | PASS | 未发现补丁空白错误，仅有工作区 LF/CRLF 提示 |
| 静态代码审查 | FAIL | 发现首次下载、安装 API 和发布原子性等阻塞问题 |

### 2.2 未完成

- 未运行实体设备上的正式签名旧版到 1.27.0 覆盖升级。
- 未运行真实 GitHub Draft Release 演练。
- 未执行 Android 23–35 多版本安装矩阵。
- 未验证真实 GitHub Release 重定向最终主机。
- 未完成 Android Developer Console 的包名和签名注册核对。

## 3. 缺陷清单

### 3.1 发布阻塞项

#### U-P0-01：生产下载目录未创建

位置：`UpdateDownloadController.updatesDirectory()`、`runDownload()`。

生产路径返回 `cacheDir/updates`，但开始下载前没有执行 `mkdirs()`。首次安装的用户通常不存在该目录，`new FileOutputStream(part)` 将抛出异常；异常又会被映射为网络错误，因此用户只会看到误导性的“网络失败”。现有测试把 `TemporaryFolder` 根目录直接作为更新目录，目录天然存在，未覆盖生产条件。

改进：

1. `PREPARING` 阶段显式创建更新目录。
2. 校验目录确实存在、为目录且可写。
3. 创建失败返回 `STORAGE_WRITE_FAILED`，不得映射为 `NETWORK`。
4. 增加“父目录存在、updates 子目录不存在”的单元测试。
5. 增加“路径被同名普通文件占用”的单元测试。

验收：全新安装后第一次点击下载可以创建目录并进入 `DOWNLOADING`。

#### U-P0-02：系统安装仍使用已弃用 API

位置：`UpdateInstaller`、Manifest `<queries>` 和更新 FileProvider。

当前使用 `Intent.ACTION_INSTALL_PACKAGE`。该 API 自 Android 10 / API 29 起弃用，现代实现应使用 `PackageInstaller.Session`。继续使用旧 Intent 会增加厂商 ROM、targetSdk 35 和后续 Android 版本的不确定性，也无法可靠接收安装最终状态。

改进：

1. 使用 `PackageInstaller.SessionParams(MODE_FULL_INSTALL)` 创建 Session。
2. 设置包名、安装包大小和用户操作要求。
3. 将已验证 APK 流式写入 `Session.openWrite()`。
4. 调用 `fsync()` 后关闭输出流。
5. 使用显式、仅本应用接收的状态回调。
6. targetSdk 35 使用符合平台要求的 mutable `PendingIntent` 作为 `IntentSender`。
7. 处理 `STATUS_PENDING_USER_ACTION`，仅在 Activity 处于前台时启动返回的确认 Intent；后台时以应用内待处理状态或通知引导用户返回。
8. 处理 `STATUS_SUCCESS`、`STATUS_FAILURE_ABORTED`、`STATUS_FAILURE_BLOCKED`、`STATUS_FAILURE_CONFLICT`、`STATUS_FAILURE_INCOMPATIBLE`、`STATUS_FAILURE_INVALID`、`STATUS_FAILURE_STORAGE` 和通用失败。
9. 失败或取消时 `abandonSession()`。
10. Session 已完整接管 APK 后删除本地文件。
11. 移除 `ACTION_INSTALL_PACKAGE` 的 `<queries>`。
12. 若不再有其他用途，移除更新专用 FileProvider 和 `update_file_paths.xml`。

验收：Android 23、26、29、31、33、35 代表设备均能进入系统确认并返回明确安装状态。

#### U-P0-03：版本单调性校验可被网络故障绕过

位置：`.github/workflows/build.yml` 的 `Verify versionCode is monotonically increasing`。

当前命令在 curl 失败、GitHub 临时不可用、清单损坏或 grep 失败时统一输出 `0`。这会把真正的发布源故障当成“首次发布”，使任意正 versionCode 通过单调性检查。

改进：

1. 下载上一清单时保存 HTTP 状态码和正文到临时文件。
2. HTTP 200：使用严格 JSON 解析器读取整数 `versionCode`，解析失败立即阻止发布。
3. HTTP 404：只允许进入明确的首次引导分支。
4. 超时、DNS、TLS、429、5xx：阻止发布，不得回退为 0。
5. 首次引导分支从上一正式 Release APK 读取 versionCode；只有仓库确实没有任何正式 Release 时才使用受审计的 bootstrap 基线。
6. 在 job summary 中记录比较来源、旧值和新值。

验收：断网、500、畸形 JSON、空 versionCode 都会令发布 job 失败。

#### U-P0-04：稳定 Release 不是原子发布

位置：`.github/workflows/build.yml` 的 `Create GitHub Release`。

当前动作直接创建正式 Release 并上传三个资产。在上传窗口内，`releases/latest` 可能已经指向新版本，但 `latest.json` 或 APK 尚未上传完成。

改进：

1. 先创建 Draft Release。
2. 上传正式 APK、`.sha256` 和 `latest.json`。
3. 从 Draft 资产重新下载三个文件。
4. 重新计算哈希并解析清单。
5. 核对资产数量、名称、字节数、版本、包名和签名。
6. 全部通过后将 Draft 发布为稳定 Release。
7. 任一步失败时保持 Draft，不进入 `latest`。
8. 禁止 prerelease 被发布为稳定入口。

验收：客户端永远看不到缺少任一资产的最新稳定版。

#### U-P0-05：缺少 Android 开发者验证发布前置

Polaris 通过 GitHub 直装分发。为应对 2027 年认证 Android 设备上的全球开发者验证要求，应在正式推广自更新前完成：

1. 在 Android Developer Console 或适用的 Play Console 完成开发者身份验证。
2. 注册 `com.polaris.timetable`。
3. 注册当前正式签名证书。
4. 核对 Console 指纹与 `POLARIS_RELEASE_CERT_SHA256` 一致。
5. 将注册状态、账号归属和恢复方式记录在仅维护者可访问的发布手册中。
6. 证书轮换前同时更新平台登记和客户端校验策略。

验收：维护者能够提供包名与当前发布签名已登记的核对记录。

### 3.2 高优先级可靠性问题

#### U-P1-01：已验证 APK 位于可被系统清理的缓存目录

当前把已验证 APK 和持久化的 `pending_apk_path` 放在 `cacheDir`。系统可能在用户授权未知来源期间清理缓存，导致返回后文件消失。

改进：

- `.part` 可继续放在缓存目录。
- 校验通过后移动到 `filesDir/updates`，或立即写入 PackageInstaller Session。
- SharedPreferences 优先保存 `sessionId` 和目标版本，而不是长期保存缓存绝对路径。
- 启动时恢复属于本应用且尚未结束的 Session。

#### U-P1-02：恢复待安装包时不再校验原始哈希

`verifyWithoutManifest()` 在进程重启后只检查归档身份、版本和签名，缺少原清单 SHA-256 与期望字节数。

改进：持久化目标版本、APK 大小和 SHA-256；恢复时重新执行完整校验。采用 PackageInstaller Session 后，仍需在写入 Session 前完成完整本地复验。

#### U-P1-03：开始下载时没有可靠删除同名旧目标文件

`cleanupOldFiles()` 特意跳过当前 target；若上次在重命名前后崩溃、偏好未写入，重新下载相同版本会在 `renameTo(target)` 阶段失败。

改进：新任务开始前删除同版本旧 target，或使用唯一任务目录并通过原子移动覆盖经过确认的旧目标。增加“同名 target 已存在”的回归测试。

#### U-P1-04：下载超过清单声明大小时未立即停止

循环只检查 200 MiB 全局上限，没有在 `written > expected` 时终止。恶意或错误响应可在清单声明很小时继续消耗接近 200 MiB 流量与存储。

改进：每次累加后同时检查 `written > expected` 和 `written > MAX_APK_BYTES`，任一命中立即中止并删除临时文件。

#### U-P1-05：Activity 重建可能删除正在写入的 `.part`

`onHostCreated()` 无条件调用临时文件清理，而 UpdateCoordinator 是进程单例，下载控制器可能仍在运行。若 Activity 在同一进程内重建，清理逻辑可能删除活动下载文件。

改进：

- 清理前先检查控制器是否 busy。
- 为每次下载生成任务 ID，并只清理不属于活动任务的临时文件。
- Host 重新绑定后立即回放当前下载状态。

#### U-P1-06：自动更新弹窗阻塞条件不完整

`isAutoDialogBlocked()` 只判断设置页和计划菜单，没有覆盖 PDF 导入、AI 导入、课程编辑、备份恢复、冲突选择等 Dialog。自动更新弹窗可能叠加在关键流程上。

改进：建立统一的“关键交互占用”状态，而不是枚举少数 View。Activity 不在 RESUMED、当前无窗口焦点、已有业务 Dialog、导入流程或系统选择器未返回时，只记录 pending offer，不展示更新弹窗。

### 3.3 协议与网络健壮性问题

#### U-P1-07：字符串字段没有严格类型校验

`requireString()` 使用 `JSONObject.getString()`，该方法会把部分非字符串值转换成字符串，与协议“字段类型错误必须失败”不一致。

改进：先通过 `requireRawValue()` 取值，再要求 `value instanceof String`。为所有字符串字段增加数字、布尔值、数组和对象反例。

#### U-P1-08：APK 大小接受小数并静默截断

`requireLong()` 接受所有 Number，再调用 `longValue()`；例如 `1.5` 会被截断成 `1`。

改进：只接受 JSON Integer/Long，或校验数值是有限整数且转换前后完全相等。增加小数、NaN 语义替代值和超 long 范围测试。

#### U-P1-09：版本号映射存在碰撞

当前没有限制 minor 和 patch 小于 100：`1.2.100` 与 `1.3.0` 都会映射为 `10300`。

改进：客户端解析器、PowerShell 生成器和 GitHub Actions 同时强制：

- `major >= 0`
- `0 <= minor <= 99`
- `0 <= patch <= 99`
- 结果大于 0
- 结果不超过项目采用的 Android versionCode 上限

三处规则应由同一测试向量验证，避免漂移。

#### U-P1-10：重定向上限存在 off-by-one

Repository 和 Downloader 都使用 `redirects > MAX_REDIRECTS`，实际上会处理 6 个重定向响应后才拒绝。

改进：收到重定向响应时判断 `redirects >= MAX_REDIRECTS`，并增加恰好 5 次成功、6 次失败的测试。

#### U-P1-11：超大清单错误类型被覆盖

`readBodyCapped()` 在读取循环中抛出的 `FetchException(INVALID_METADATA)` 会被外层 `catch(IOException)` 捕获并重新映射为 `NETWORK`。

改进：优先透传 `FetchException`，仅把其他 IOException 映射为网络错误；增加超过 256 KiB 的 Repository 测试。

#### U-P1-12：发布时间解析没有验证完整消费输入

`SimpleDateFormat.parse(String)` 不保证消费整个字符串。应使用固定格式的严格解析并确认 ParsePosition 到达字符串末尾，或在可兼容 API 上使用严格的 ISO-8601 解析器。

#### U-P1-13：缺少平台级明文流量禁用

代码层已经拒绝 HTTP，但 Manifest 没有设置 `android:usesCleartextTraffic="false"`。

改进：在 Application 增加该属性，形成平台级纵深防护，并运行 PDF 导入、深链、分享和外部 AI 跳转回归，确认不依赖应用自身明文请求。

### 3.4 发布与测试覆盖问题

#### U-P1-14：仪器测试会访问真实网络

`UpdateEntryTest` 点击“检查更新”会启动真实 GitHub 请求，仅仅不断言网络结果并不能消除抖动。进程级 Coordinator 单例还可能把请求与状态带到后续测试。

改进：

- 为 Coordinator 提供测试依赖注入入口。
- 仪器测试使用内存 Preferences、Fake Repository 和同步/可控 Executor。
- 每个测试重置单例或改为由 Activity scope 持有业务实例。
- CI 仪器测试不访问公网。

#### U-P1-15：Repository、Coordinator 和真实 APK 校验缺少直接测试

现有 48 个 JVM 测试只覆盖 Parser、Policy、Preferences、Downloader。需要新增：

- `UpdateRepositoryTest`：200、404、429、500、超时、空 Location、相对重定向、5/6 次重定向、超大响应。
- `UpdateCoordinatorTest`：手动/自动、忽略版本、required、Host 解绑重绑、并发检查、延后弹窗、下载状态恢复。
- `ApkVerifier` 仪器测试：正确包、错包名、同版本、降级、错签名、损坏 APK。
- `PackageInstaller` 构建块测试：Session 参数、写入、回调 Intent 和 PendingIntent flags。

#### U-P1-16：安装器测试包含无效断言

`assertTrue(granted || !granted)` 对任何 boolean 都成立，不能验证行为。

改进：把“不会崩溃”测试改为明确 API 分支断言；未知授权状态由 Fake PackageManager 或封装接口注入，分别覆盖 true/false。

#### U-P1-17：标签发布没有依赖仪器测试

instrumented job 只在 PR 和 main push 运行，tag push 不运行；release job 只依赖 build。标签可以指向未经过仪器测试的提交。

改进二选一：

1. 限制发布标签只能指向 main/master 最新且已通过全量 CI 的提交，并通过 GitHub API 校验 check suite；或
2. tag 事件直接运行更新入口 smoke 和关键安装构建块测试，release 同时依赖 build 与 instrumented。

#### U-P1-18：新增 lint warning 超过项目基线

lint 当前为 0 error、4 warning；既有允许基线为 3 条 PDFBox 依赖警告。新增 `PlanAddMenuView` 构造器 warning 虽不属于更新模块，但当前交付整体未保持仓库质量基线。

改进：补齐工具构造器，或在有充分理由时添加带说明的精确 lint 抑制。验收恢复为 0 error、3 个既有 warning。

#### U-P2-01：`minSupportedVersionCode` 在生成器中硬编码

生成脚本固定写入 `12601`，未来版本不会自动维护。该字段当前又不实施强制更新，容易成为失真的协议数据。

改进：若确有用途，改为显式脚本参数并由 Release notes 或受审计配置提供；若没有真实消费场景，从 schema v1 中删除，避免伪精确字段。

#### U-P2-02：`required` 命名容易被误解为强制更新

实现中 `required=true` 仍允许“稍后”，实际语义是“重要更新”。建议在下一协议版本改名为 `priority: normal|important`；schema v1 保持兼容解析，但 UI 和文档不得称其为强制更新。

## 4. 分阶段改进计划

### 阶段 A：修复不可发布缺陷

目标：让首次下载可用，并保证任何公开稳定 Release 都完整、单调且正式签名。

工作项：

1. 修复下载目录创建和错误分类。
2. 修复同名 target 和超声明大小处理。
3. 增加对应 JVM 回归测试。
4. 修复 CI 历史版本查询，区分 200、404 和网络故障。
5. 增加首次方案 B 发布的 bootstrap 规则。
6. 改成 Draft 上传、复验、正式发布。
7. 恢复 lint 既有警告基线。
8. 完成 Android Developer Console 注册核对。

涉及文件：

- `UpdateDownloadController.java`
- `UpdateDownloadControllerTest.java`
- `.github/workflows/build.yml`
- `tools/release/generate-update-manifest.ps1`
- 发布维护文档
- `PlanAddMenuView.java` 或对应 lint 抑制位置

退出条件：P0 项全部关闭，断网不能绕过发布校验，全新安装首次下载进入下载状态，Draft 未验证前不会成为 latest。

### 阶段 B：迁移现代安装链

目标：使用 PackageInstaller Session 替代已弃用 Intent/FileProvider 链。

工作项：

1. 设计 Session 状态模型和持久化字段。
2. 实现 Session 创建、APK 写入、fsync、commit 和 abandon。
3. 实现显式状态接收器及前台用户确认处理。
4. 处理 targetSdk 35 PendingIntent 可变性要求。
5. 从 Coordinator 暴露明确的安装状态，而非 boolean。
6. 删除不再需要的 FileProvider、XML 路径和 INSTALL_PACKAGE queries。
7. 完成 Android 23–35 代表设备测试。

涉及文件：

- `UpdateInstaller.java`
- `UpdateCoordinator.java`
- `UpdateError.java`
- `UpdatePreferences.java`
- `AndroidManifest.xml`
- `update_file_paths.xml`（删除）
- `UpdateInstallerTest.java`
- `MainActivity.java`
- `strings.xml`

退出条件：没有弃用安装 Intent，系统确认、取消、成功和失败都有可观测结果。

### 阶段 C：收紧协议与网络

目标：让客户端解析器、生成脚本和 CI 对同一协议达成严格一致。

工作项：

1. 严格校验所有 String 和整数类型。
2. 增加版本段范围与 versionCode 上限。
3. 修复重定向计数。
4. 修复超大清单错误透传。
5. 严格解析发布时间。
6. 增加 `usesCleartextTraffic=false`。
7. 新增 Repository 测试和共享协议测试向量。
8. 决定保留或移除 `minSupportedVersionCode`。

涉及文件：

- `UpdateJsonParser.java`
- `UpdateRepository.java`
- `UpdateJsonParserTest.java`
- 新增 `UpdateRepositoryTest.java`
- `generate-update-manifest.ps1`
- `build.yml`
- `AndroidManifest.xml`

退出条件：畸形类型、碰撞版本、超限响应和第 6 次重定向全部被确定性拒绝，三端版本规则一致。

### 阶段 D：生命周期、恢复与测试隔离

目标：下载和安装状态在 Activity 重建、授权往返、进程重启及 CI 中均可预测。

工作项：

1. 活动任务使用任务 ID，避免重建清理正在下载的文件。
2. Host 重绑时回放完整状态。
3. 恢复时重新验证大小、哈希、版本和签名。
4. 完善自动弹窗的统一业务占用判断。
5. 为 Coordinator/Repository/Installer 建立依赖注入点。
6. 移除仪器测试真实网络访问。
7. 增加签名 APK fixture 和真实 ApkVerifier 测试。
8. 让 tag 发布依赖已验证的仪器结果。

退出条件：测试无公网依赖；Activity 重建、授权返回和进程重启不丢状态、不误删文件、不重复弹窗。

### 阶段 E：正式升级验收

目标：证明真实正式签名升级不会破坏用户数据和系统集成。

工作项：

1. 用上一个正式签名版本安装真实课程、提醒、背景和 Widget 数据。
2. 通过方案 B 检查并下载候选 Release。
3. 完成系统覆盖安装。
4. 核对课表、设置、提醒、Widget 和备份恢复。
5. 验证拒绝授权、取消安装、安装失败和再次尝试。
6. 验证 GitHub latest 路径、重定向主机和三项资产。
7. 运行全量单测、lint、仪器测试和 Debug/Release 构建。

退出条件：第 5 节验收门槛全部通过。

## 5. 最终验收门槛

### 功能

- [ ] 全新安装首次下载成功创建目录。
- [ ] 手动和自动检查行为符合策略。
- [ ] 下载进度、取消、失败和重试均可用。
- [ ] Activity 重建不会删除活动下载。
- [ ] 进程重启后待安装状态可验证恢复。
- [ ] PackageInstaller 正确显示系统用户确认。
- [ ] 安装取消和失败能回到可重试状态。
- [ ] 正式签名旧版能够覆盖升级到候选版。
- [ ] 升级后课表、设置、提醒和 Widget 数据完整。

### 安全

- [ ] Manifest 禁止应用自身明文网络流量。
- [ ] 清单、APK 和全部重定向仅允许受控 HTTPS 主机。
- [ ] 下载超过声明大小立即终止。
- [ ] 损坏、错哈希、错包名、降级和错签名 APK 全部拒绝。
- [ ] 恢复和安装前执行完整复验。
- [ ] 正式 Release 仅包含正式签名 APK、哈希和清单。
- [ ] 网络故障不能把上一版本降级为 0。
- [ ] Draft 未通过复验前不进入 latest。
- [ ] 包名和发布签名已完成 Android 开发者验证登记。

### 质量

- [ ] 更新模块 JVM 测试全部通过。
- [ ] 新增 Repository、Coordinator、Verifier、Installer 测试通过。
- [ ] 仪器测试不访问公网。
- [ ] lint 为 0 error，warning 回到既有 3 条基线。
- [ ] Debug 和正式签名 Release 构建通过。
- [ ] Android 23、26、29、31、33、35 代表环境通过安装流程。
- [ ] GitHub Draft Release 演练通过。
- [ ] README、隐私政策、测试计划与实际行为一致。

## 6. 建议执行顺序

固定顺序为：

1. 阶段 A：先恢复基本可用性和发布安全。
2. 阶段 B：迁移 PackageInstaller，消除旧安装链。
3. 阶段 C：收紧协议和网络边界。
4. 阶段 D：补生命周期和测试隔离。
5. 阶段 E：完成真实正式签名升级验收。

在阶段 A 与阶段 B 完成前，不应发布 `1.27.0` 为最新稳定版。若 `v1.27.0` 标签尚未创建，应保留版本号直到修复完成；若已公开发布，应停止将该 Release 作为 latest，并以更高 patch 版本发布修复，不能覆盖同版本资产。
