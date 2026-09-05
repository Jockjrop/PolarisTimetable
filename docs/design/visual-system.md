---
name: Polaris课程表
description: 原生 Android 课程表的四主题视觉系统
colors:
  minimal-bg-light: "#EAF3FB"
  minimal-bg-dark: "#0D1422"
  aurora-bg-light: "#EEF3FF"
  aurora-accent: "#6F60D9"
  galaxy-bg-dark: "#06152C"
  galaxy-accent: "#6EA3FF"
  campus-bg-light: "#EFF6FF"
  campus-accent: "#2F6CAB"
  ink-light: "#102A4D"
  ink-dark: "#F1F6FF"
typography:
  headline:
    fontFamily: "sans"
    fontSize: "22sp"
    fontWeight: 700
  title:
    fontFamily: "sans"
    fontSize: "16sp"
    fontWeight: 700
  body:
    fontFamily: "sans"
    fontSize: "13sp"
    fontWeight: 400
  label:
    fontFamily: "sans"
    fontSize: "12sp"
    fontWeight: 500
rounded:
  course: "9dp"
  group: "18dp"
  themed-card: "22dp"
  glass: "24dp"
  navigation: "58dp"
spacing:
  compact: "8dp"
  standard: "12dp"
  generous: "18dp"
components:
  themed-card:
    backgroundColor: "{colors.aurora-bg-light}"
    textColor: "{colors.ink-light}"
    rounded: "{rounded.themed-card}"
    padding: "16dp 18dp"
  bottom-navigation:
    backgroundColor: "{colors.aurora-bg-light}"
    textColor: "{colors.ink-light}"
    rounded: "{rounded.navigation}"
    height: "60dp"
---

# Polaris课程表视觉系统

设计资料入口见根目录 [DESIGN.md](../../DESIGN.md)。

## 概述

整体感受是“安静的校园天气”。

Polaris 以课程内容和时间判断为主角，视觉主题只改变环境感受，不改变信息结构。系统包含四套主题：完整保留旧版的“极简风格”，以及“极光幻彩”“深空星河”“云境校园”三套氛围主题。三套新主题使用低对比程序化背景、半透明表面和克制的主题色；极简风格不继承这些装饰。

- 四套主题共用相同导航、页面层级和触控区域。
- 用户背景图在课程表页拥有最高视觉优先级。
- 课程数据、状态和可读性始终高于背景纹理。

## 颜色

主题使用冷色中性色与单一主强调色；课程块拥有独立但受控的六色色板。

极简风格保持原有纯色表面和课程色算法；新主题的背景、玻璃和色板不得影响极简风格。

新主题正文与弱化文字在默认背景上的目标对比度不低于 4.5:1；课程块文字按实际填充色动态选择深色或白色。

## 字体

界面使用 Android 系统 sans 字体与 Material 式层级。页面标题为 21–25sp 粗体，卡片标题为 16sp 粗体，正文与辅助信息为 12–15sp；所有字号使用 sp 并跟随系统字体缩放。

周次、日期、节次和课程名始终优先保证可读性，主题名称和装饰文案不进入课程网格。

## 布局

手机保持单列布局与底部双目标导航；课程表维持原有横向周视图和纵向节次滚动。“我的”页在氛围主题中使用横向身份卡与 78dp 设置入口，极简主题继续使用原布局。触控目标不小于 48dp，底部内容为悬浮导航预留安全距离。

## 层级与景深

极简主题主要依靠色块分层。三套新主题使用真实模糊可用时的背景采样、半透明色面、1dp 高光边和低位柔和阴影；Android 12 以下保留半透明表面作为稳定降级。阴影颜色从主题强调色取样，不使用硬边阴影。

四套主题的顶部与底栏共用同一套材质参数：24% 背景采样、18dp 模糊、相同透明度映射，并且不附加主题阴影。极简继续使用原中性色；极光、深空、校园只在同一透明度下轻微调整玻璃 RGB 与描边，不改变遮挡强度。Android 12 以下没有真实背景模糊时，使用极简风格的不透明度降级。

## 形状

课程块默认 9dp；设置组在极简主题中为 18dp，在氛围主题中为 22dp；顶部玻璃层为 24dp；底部导航默认高度 60dp、圆角 58dp。高级设置可覆盖课程块与导航尺寸，但不会改变主题结构。

## 组件

### 卡片与容器

- 极简主题使用原有不透明卡片和细白边。
- 氛围主题使用半透明表面、1dp 主题高光边和 2–4dp tonal elevation。
- 设置入口包含绘制式图标、标题、说明与方向提示，整卡可点击。

### 导航

底部导航保留“课表 / 我的”两个现有目标、激活字重和滚动隐藏逻辑。氛围主题仅替换玻璃材质、边框和阴影，不改变点击、位置或状态逻辑。

### 课表

网格、周切换、课程点击与长按沿用原实现。新主题替换默认课程色板、网格线和当天高亮；显式保存的课程颜色继续优先。

## 实现注意事项

- 四套主题在所有主页面上使用统一的选择结果。
- 低版本 Android 使用无实时模糊的半透明降级。
- 用户自定义背景继续覆盖课程表主题背景。
- 主题不增加业务入口或改变页面导航。
- 极简风格不加入星点、极光线或校园线稿。
- 不为追求氛围降低节次、课程和设置文字的可读性。
