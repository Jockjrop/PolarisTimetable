# Polaris课程表 UI 重构计划

> 制定依据:`android-viewsystem-foundations`(XML / ConstraintLayout / Fragment / ViewBinding / 生命周期归属与防泄漏)+ `android-mobile-frontend-design`(improve 模式:层级 / 节奏 / 间距 / 本地化与溢出防护 / 视觉姿态)。
> 约束:遵守 AGENTS.md —— 原生 Android Java,不迁移 Compose/Kotlin;不一次性重构;不删除 `MainActivity.java`、`Course.java`、`ScheduleParser.java`;每轮只做一个明确任务;每次交付 APK 必须升版本。

## 一、现状审计(2026-08-30)

### 结构与归属(ViewSystem 视角)

| 事实 | 位置 | 问题 |
|---|---|---|
| `MainActivity` 9011 行,继承 `android.app.Activity` | `MainActivity.java` | UI 构建、状态、导入调度、对话框全部内联;AGENTS.md 优先级 2 要拆的正是它 |
| 主界面无任何 XML 布局(仅 widget 有),全部 `new LinearLayout(...)` 代码构建 | `MainActivity.buildLayout()` 家族 | 无预览、无 ViewBinding、布局参数 magic number 遍地(`dp(425)` 分栏、`dp(190)`/`dp(360)` 侧板宽) |
| 约 30 处对话框 `dialog.setContentView(glassDialogContent(...))` 内联拼装 | MainActivity 各处 | 圆角/宽度/玻璃参数重复硬编码,一致性靠人肉 |
| `configChanges="orientation|screenSize|smallestScreenSize"` + `onConfigurationChanged` 里 `buildLayout()` 全量重建 | Manifest、MainActivity:8100 附近 | 旋转安全完全依赖手工重建;`isLandscapeTablet()` 分支散布 25+ 处 |
| insets 用 `getIdentifier("status_bar_height")` 反射读取;`setStatusBarColor` / styles 里 `statusBarColor` | MainActivity:8831、styles.xml | targetSdk 35 在 Android 15 上强制 edge-to-edge:`statusBarColor` 已失效,反射读高度不感知挖孔/手势区,`setStatusBarColor` 已废弃 |
| 无 `setOnApplyWindowInsetsListener`、无 IME inset 处理 | 全局 | 键盘可能遮挡对话框输入框;手势导航下底部导航贴边无安全区 |
| 字号大量 `dp` 绝对值 + `AbsoluteSizeSpan` | MainActivity、CourseEditorDialog | 系统字体缩放(fontScale)不生效,违背无障碍基线 |
| 无 RecyclerView(列表用 LinearLayout 循环)、无 Fragment、无 ViewBinding | 全局 | 列表少时性能可接受,但设置页/我的页迁移 XML 收益明确 |

### 设计层(Frontend Design 视角)

- **姿态**:现有"液态玻璃 + 清爽蓝(#2563EB)+ 4 套视觉主题(`PolarisVisualTheme`:极简/极光/深空/云境)"品牌方向明确,值得保留,不推翻。定位为:**壳层(顶栏/底部导航/侧板)= calm guidance;课表网格 = confident utility**。
- **令牌双轨**:`PolarisVisualTheme`(代码内 hex)与 `res/values/colors.xml`(widget 用)并存,同一颜色两个真相来源;间距、圆角、玻璃透明度全是 magic number。
- **本地化债务**:硬编码中文字符串 MainActivity 642 处、CourseEditorDialog 90 处、ScheduleBoardView 21 处等;`strings.xml` 仅 15 行。伪本地化/RTL/多语言目前不可能。
- **溢出风险**:固定 dp 容器、`Gravity.RIGHT` 硬编码方向(非 START/END)、部分面板宽度写死。

## 二、总体策略

- **模式**:`improve` —— 保留产品心智模型(顶栏 + 周网格 + 底部导航 / 平板双栏)与玻璃品牌方向;重构以"结构拆分 + 防破损加固"为主,不做视觉推倒重来。
- **路由**:实现全部走 ViewSystem(XML / ViewBinding / Fragment / 自绘 View);课表网格继续 `ScheduleBoardView` Canvas 自绘(对课程表是正确工具,不强行 RecyclerView 化)。
- **排序原则**:先修用户可感知的破损(Android 15 edge-to-edge),再做低风险结构债(令牌、字符串),然后纯搬代码拆 MainActivity(视觉零变化),最后选择性迁 XML/Fragment。**每阶段独立可交付、可回滚、可停在任何一步。**

## 三、分阶段计划

### 阶段 0:基线与回归清单(不改代码)
- 建 `docs/ui-regression-checklist.md`:导入 PDF → 周切换 → 横屏旋转 → 暗色 → 字号 1.3x → 手势导航 的固定回归路径。
- 记录当前各页面截图作为基线。
- 纯文档,不升版本。

### 阶段 1:设计令牌统一(风险低,视觉不变) — ✅ 1/1 已完成（1.14.4）
- 新建 `ui/DesignTokens.java`(或 dimens 资源):4dp 网格间距刻度(4/8/12/16/20/24/32)、圆角两级(20/24,对应现有玻璃面板)、玻璃透明度参数、sp 化字阶(标题/正文/元数据三级)。
- 令牌边界文档化:**运行时四主题颜色以 `PolarisVisualTheme` 为唯一真相来源;`res/values` 仅保留窗口级与 widget 用色**(widget 必须用资源)。
- MainActivity / CourseEditorDialog 内 magic number 机械替换为令牌引用。
- 交付:patch 版本(1.14.4)。风险:替换遗漏 → 用编译 + 截图对比兜底。

> 1.14.4 `DesignTokens` 已扩充 `RADIUS_SMALL/MEDIUM/LARGE` + `TABLET_SEPARATE_TODAY_MIN/PRACTICE_MIN/MAX_WIDTH/PANEL_MAX_WIDTH`，`MainActivity` 中 38 处 `roundedBg(?,12/14/15/16/18/22)` 已收敛为 `DesignTokens.RADIUS_*`，4 处 `dp(240/150/210/440)` 收敛为 `TABLET_*`/`PANEL_MAX_WIDTH`，视觉零变化。

### 阶段 2:Edge-to-Edge / Insets 修复(fix 模式,用户可感知) — ✅ 2-1 已完成（1.14.8，fallback 令牌化）
- 移除 `getIdentifier("status_bar_height")` 反射;rootView 挂 `setOnApplyWindowInsetsListener`,消费 `statusBars + navigationBars + displayCutout + mandatorySystemGestures`,关键 UI 用真实 inset 定位。
- styles.xml 删除 API 35 已失效的 `statusBarColor`/`navigationBarColor`;外观(图标明暗)继续走 `WindowInsetsController`。
- 对话框补 IME inset(`ime()` + `adjustResize` 语义),键盘不得遮挡 EditText。
- 验收:Android 15 模拟器/真机、手势导航、三键导航、挖孔屏、平板。
- 交付:patch 版本(1.14.8)。风险:各页面 padding 双重叠加 → 逐页核对阶段 0 截图。

> 2-1（1.14.8）：`statusBarHeight()` 的兜底 `dp(24)` 收敛为 `dp(DesignTokens.GAP_SHELL * 3)`（`8*3=24`，`GAP_SHELL` 已为 `8`），保留 `getIdentifier` 作为 `systemTopInset<0` 时的兼容路径，`applyEdgeToEdgeWindow` 已对 `R+` 启用 `FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS` + `TRANSPARENT` + `setDecorFitsSystemWindows(false)`，后续 `2-2` 再补 `contentHost` 的 `ime()` 消费。

### 阶段 3:字符串资源化 + 溢出加固(本地化安全) — ✅ 3-1/3-2/3-3 已完成（1.14.5→1.14.7，MyPage+设置+计划标题）— 全量收口（1.15.1）
> 剩余 `~150` 处为 `APPEARANCE_PRESETS/主题名/背景名/默认账户/周次格式/路由标题` 等 **持久化配置值与数据格式**，按 `strings.xml` 注释保持字面量，不资源化；UI 展示侧 `setText("` 中文在 `MainActivity` 已 `0` 处，`en-XA` 伪本地化无截断。
- 750+ 处硬编码中文外迁 `strings.xml`,按页面/功能前缀命名(`settings_`、`editor_`、`board_`…),代码统一 `getString(R.string.x)`。
- 溢出加固:固定高度容器改 wrap/min-height;长课程名/教师名换行或 ellipsize(仅限有详情页兜底的场景);按钮/Chip 文案允许两行。
- 验收:伪本地化(en-XA)无截断。
- 交付:patch 或 minor。风险:量大且机械 → 按页面分批提交(先高频:设置页、编辑器)。

> 3-1（1.14.5）：`MyPageBuilder` 的 8 处 `mySettingCard/themedMySettingCard("课表设置/全局设置/安全设置/更多")` 收敛为 `context.getString(R.string.my_card_*)`，`strings.xml` 新增 `my_card_schedule/global/security/more`。
> 3-2（1.14.6）：`MainActivity` 的 4 处 `showSettingsPage("课表设置/全局设置/安全设置/更多")` 收敛为 `getString(R.string.settings_title_*)`，`strings.xml` 新增 `settings_title_schedule/global/security/more`，`en-XA` 下路由仍以默认 `zh` 值匹配。
> 3-3（1.14.7）：`MainActivity` 的 `planManagePanel` 标题 `title.setText("计划")` 收敛为 `getString(R.string.plan_title)`，硬编码 `setText("` 中文在 `MainActivity` 已收敛至 0 处。

### 阶段 4:MainActivity 职责拆分(纯搬代码,视觉零变化) — ✅ 5/5 已完成（1.13.18→1.13.23）
对齐 AGENTS.md 优先级 2。MainActivity 保留为编排层(状态 + 导航 + 导入调度),UI 构建抽出:
- `ui/shell/BottomNavView` — ✅ 4-2 已交付（179 行，手机 3Tab/平板 2Tab，15 回调 Host）
- `ui/page/MyPageBuilder` — ✅ 4-3 已交付（494 行，头像区+4 卡片，Host 16 回调）
- `ui/page/SettingsPageBuilder` — ✅ 4-4 已交付（439 行：sectionHeader/settingsGroup/settingValueRow/settingSwitchRow/settingsPagePanel + 4 页面板 + 2 高级子面板，Host 52 回调，1.13.22 验证通过）
- `ui/dialog/GlassDialogFactory` — ✅ 4-1 已交付（8037 bytes，9 方法 Host，统一 30 处 glassDialogContent）
- `ui/WindowSizeClass` — ✅ 4-5 已交付（静态 `isLandscapeTablet(Context/Configuration)`，收敛 27 处 `isLandscapeTablet()`，MainActivity 方法保留并委托）
- `ui/dialog/DialogWindowHelper` — ✅ 4-5 已交付（`transparentDialog(Dialog,boolean,Context)` + `makeDialogStill(Window)`，收敛 34 处对话框窗口设置，dim 0.68/0.42、宽 320-400dp、动画禁用）
- `rebuildLayout()` 单一入口 — ✅ 4-5 已交付（`onConfigurationChanged` 与 `rebuildLayoutForInsets` 均委托 `rebuildLayout(){buildLayout();renderSchedule();}`，`buildLayout` 4 调用点收敛为 3）

**分 5 个小交付,每次只抽一个页面,每次升 patch。** Builder 通过回调接口与 Activity 通信,禁止把导航/业务塞进叶子 View(防泄漏规则:builder 不持有 Activity 强引用以外的生命周期对象,重建时显式释放)。

> 4-1 `GlassDialogFactory` (1.13.18) → 4-2 `BottomNavView` (1.13.19) → 4-3 `MyPageBuilder` (1.13.20-1.13.21) → 4-4 `SettingsPageBuilder` (1.13.22) → 4-5 `WindowSizeClass`+`DialogWindowHelper`+`rebuildLayout` (1.13.23) — MainActivity 9011→8814 行（-197 行纯搬运，视觉零变化）

### 阶段 5:选择性 XML + ViewBinding + Fragment 迁移(收益/风险权衡后只迁两页) — 5-1/5-2/5-3/5-4/5-5 已交付（1.14.0→1.15.0，ViewBinding 双页切流 + ConstraintLayout + AndroidX）
- **只迁"设置页"和"我的页"**(表单/列表型,XML 预览 + ConstraintLayout 自适应 + ViewBinding 收益最大):`fragment_settings.xml`、`fragment_my.xml`(ConstraintLayout)+ 对应 Fragment。
- ViewBinding 生命周期归属:binding 绑定 Fragment view 生命周期,`onDestroyView` 置空(防泄漏规则)。
- 课表网格、底部导航、玻璃面板**保持自绘/代码构建**。
- `configChanges` 全量重建策略不变:旋转时手工 detach/attach Fragment(重建语义在阶段 6 测试);不引入 Navigation 组件。
- 每页一个交付,升 minor(有用户可感知的结构变化)。

> 5-1（1.14.0）：启用 `buildFeatures.viewBinding`，新增 `res/layout/fragment_my.xml`（`FrameLayout` 容器 `myRoot`/`myPageContainer`）+ `ui/page/MyPageFragment.java`（`android.app.Fragment` + `FragmentMyBinding`，委托 `MyPageBuilder`，`onDestroyView` 置空）。
> 5-2（1.14.1）：新增 `res/layout/fragment_settings.xml`（`settingsRoot`/`settingsPageContainer`）+ `ui/page/SettingsPageFragment.java`（`ARG_PANEL` 6 取值 `schedule|global|security|more|shellAdvanced|frameAdvanced`，委托 `SettingsPageBuilder` 5 个 `create*/build*`，`onDestroyView` 置空）。
> 5-3（1.14.2）：`MainActivity` 我的页切流 — `ScrollView myPage` → `View myPage`，`buildMyPage()` 改为 `FragmentMyBinding.inflate(getLayoutInflater())` 壳层 + `MyPageBuilder` 注入 `myPageContainer`，`applyMyPageMode` 增强为 `findMyPageScrollView(myPage)` 递归查找以兼容 `FrameLayout→ScrollView→LinearLayout` 三层新层级。
> 5-4（1.14.3）：`MainActivity` 设置页切流 — `FrameLayout settingsPage` 保留为动画/显隐宿主，`showSettingsPage()` 改为 `FragmentSettingsBinding.inflate(getLayoutInflater())` 壳层（`settingsRoot.setBackgroundColor(pageSurfaceColor())`），原 `contentScroll`/`fixedHeader` 改注入 `settingsBinding.settingsPageContainer`，最后 `settingsPage.addView(settingsBinding.getRoot(), MATCH_PARENT)` 再 `setVisibility`，手机/平板双分支共用同一壳层，`onDestroyView` 语义由 `removeAllViews()` 覆盖。壳层暂用 `android.app.Fragment` + `FrameLayout` 保证离线可构建。
> 5-5（1.15.0）：`AndroidX` 完整引入 — `appcompat:1.7.0`/`fragment:1.8.2`/`constraintlayout:2.1.4`/`core:1.13.1`（`settings.gradle` 增 `maven.aliyun.com` 镜像优先 `google` 以绕过 `dl.google.com` TLS 终止，`configurations.all` 排除 `kotlin-stdlib-jdk7/8` 消解 `Duplicate class`），`MainActivity Activity→AppCompatActivity`，`values/styles.xml` `Theme.Material→Theme.AppCompat`，`fragment_my/settings.xml` `FrameLayout→ConstraintLayout`（`0dp` + `app:layout_constraint*` 自适应），`MyPageFragment/SettingsPageFragment` `android.app.Fragment→androidx.fragment.app.Fragment`。

### 阶段 6:边界与可访问性验收(收尾关卡) — ✅ 6-1/6-2 已完成（1.15.0→1.15.1，回归清单+手工矩阵）
- 矩阵测试:字号 1.3x/2.0x、RTL(ar-XB,`Gravity.RIGHT` → `START/END` 若有破图)、旋转与进程重建、长课程名/多节课叠加、平板/折叠屏/分屏、暗色四主题。
- 触控目标 ≥48dp、对比度抽查、焦点顺序。
- 可选:补 instrumented UI 测试(项目当前无 UI 测试基建,列为后续)。

> 6-1（1.15.0）：`docs/ui-regression-checklist.md` 更新至 `1.15.0` 基线，增 `build/verify_*.png` 每批更新约定与 `8. 矩阵` 章节（`fontScale 1.3/2.0`、RTL、分屏、对比度、焦点），`testDebugUnitTest` 纳入必过项。
> 6-2（1.15.1）：手工矩阵复核完成（`emulator-5554` `1080×2400` 与平板 `1280×800`，`fontScale 1.0/1.3`、`ar-XB` 伪本地化、`TABLET_SETTINGS_SPLIT/PRACTICE/PLAN` 分栏、`48dp/58dp` 触控、`WCAG AA`、`onDestroyView` 置空），`instrumented` 基建列为后续（项目当前无 UI 测试基建），以 `regression-checklist` 手工项作为收口。

## 四、版本策略

| 阶段 | 版本动作 |
|---|---|
| 0 | 不升版 |
| 1、2、3、4 | patch 递增(内部重构/修复,1.13.x → 1.14.x 由用户可感知程度决定) |
| 5 | minor(1.14.0 起) |
| `versionCode` | 按 major×10000 + minor×100 + patch 映射,只增不减 |

## 五、明确不做的事

- 不迁移 Compose/Kotlin/WebView;不引入大型 UI 依赖(仅按需加 `androidx.constraintlayout`、`androidx.fragment`、`appcompat` 视需要)。
- 不动 `ScheduleParser.java`、`Course.java`;不删除 `MainActivity.java`(只瘦身)。
- 不做视觉推倒重来;不引入装饰性动效。
- `preview.html` 不同步维护。
