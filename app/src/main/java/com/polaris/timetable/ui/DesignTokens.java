package com.polaris.timetable.ui;

/**
 * 设计令牌:壳层跨屏幕常量的唯一来源。
 * 数值如实镜像当前实现(视觉零变化);页面内部的一次性取值仍留在原地,
 * 待布局迁移 XML 时随页收敛。运行时四主题颜色仍以 {@link PolarisVisualTheme}
 * 为唯一来源,令牌只覆盖形状、间距、透明度与字阶。
 *
 * <p>审计基线(2026-08-30,MainActivity + CourseEditorDialog):
 * <ul>
 *   <li>间距实际节奏为 2dp 网格(8/10/12/14/18 高频,另有 3/5/6 微调值),
 *       与纯 4dp 网格存在偏差,归一化属视觉决策,不在令牌引入阶段处理;</li>
 *   <li>圆角实际分布:输入框/小卡片 12/14,对话框玻璃 22(全 app 默认),
 *       右侧玻璃侧板 20,顶栏玻璃 24,页面内另有 15/16/18/26 散值待收敛;</li>
 *   <li>字号 setTextSize 默认单位为 sp,已随系统字体缩放;
 *       实际分布 15 级(12~30sp),收敛前先以导航/标题字形令牌锚定高频值。</li>
 * </ul>
 */
public final class DesignTokens {

    // ===== 间距(dp,调用方用 dp() 换算)=====
    /** 手机页左右边距 */
    public static final int MARGIN_PAGE_PHONE = 10;
    /** 平板页左右边距 */
    public static final int MARGIN_PAGE_TABLET = 16;
    /** 壳层与系统栏/面板之间的基准间隙 */
    public static final int GAP_SHELL = 8;

    // ===== 圆角(dp)=====
    /** 输入框、小组件 */
    public static final int RADIUS_CHIP = 12;
    /** 卡片 */
    public static final int RADIUS_CARD = 14;
    /** 小按钮/标签 */
    public static final int RADIUS_SMALL = 15;
    /** 行背景 */
    public static final int RADIUS_MEDIUM = 16;
    /** 卡片(大) */
    public static final int RADIUS_LARGE = 18;
    /** 右侧毛玻璃侧板(今日概览/实践/计划) */
    public static final int RADIUS_SIDE_PANEL = 20;
    /** 对话框玻璃容器,全 app 对话框默认值 */
    public static final int RADIUS_DIALOG_SHEET = 22;
    /** 顶栏毛玻璃 */
    public static final int RADIUS_TOP_PANEL = 24;

    // ===== 玻璃透明度(%,用户可在设置中调整,此处为默认值)=====
    public static final int GLASS_OPACITY_HEADER_DEFAULT = 78;
    public static final int GLASS_OPACITY_NAV_DEFAULT = 86;

    // ===== 底部导航 =====
    /** 悬浮导航默认高度(dp) */
    public static final int NAV_HEIGHT_DEFAULT = 60;
    public static final int NAV_HEIGHT_MIN = 56;
    public static final int NAV_HEIGHT_MAX = 120;
    /** 悬浮导航与屏幕边缘的距离(dp) */
    public static final int NAV_FLOATING_MARGIN = 18;
    /** 横屏平板导航条宽度(dp) */
    public static final int NAV_TABLET_WIDTH = 240;

    // ===== 平板分栏(dp)=====
    /** 横屏平板:我的页/设置页分栏位置 */
    public static final int TABLET_SETTINGS_SPLIT = 425;
    /** 横屏平板:本周实践面板宽度 */
    public static final int TABLET_PRACTICE_PANEL_WIDTH = 190;
    /** 横屏平板:本周计划面板宽度 */
    public static final int TABLET_PLAN_PANEL_WIDTH = 360;
    /** 横屏平板:今日概览分离阈值 */
    public static final int TABLET_SEPARATE_TODAY_MIN = 240;
    /** 横屏平板:实践面板最小/最大宽度 */
    public static final int TABLET_PRACTICE_MIN_WIDTH = 150;
    public static final int TABLET_PRACTICE_MAX_WIDTH = 210;
    /** 面板最大宽度 */
    public static final int PANEL_MAX_WIDTH = 440;

    // ===== 字阶(sp)=====
    /** 底部导航图标字形 */
    public static final int TYPE_NAV_GLYPH = 25;
    /** 底部导航标签 */
    public static final int TYPE_NAV_LABEL = 13;
    /** 设置标题图标字形 */
    public static final int TYPE_TITLE_GLYPH = 22;

    private DesignTokens() {
    }
}
