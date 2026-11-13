package com.polaris.timetable.ui.page;

/**
 * 设置页路由标识。
 *
 * <p>设置页的页面标题同时充当路由 key：{@code MainActivity} 用它决定刷新哪一页、
 * 返回时回到哪一页。过去这些中文字面量散落在 Activity 与 Builder 两处，
 * 改标题就会静默断路由，因此集中到这里。
 *
 * <p>注意：这些值会直接显示给用户，改动等同于改 UI 文案。
 */
public final class SettingsRoute {

    public static final String SCHEDULE = "课表设置";
    public static final String GLOBAL = "全局设置";
    public static final String SECURITY = "安全设置";
    public static final String COURSES = "查看所有课程";
    public static final String COURSES_MANAGE = "管理已添加课程";
    public static final String HOLIDAY = "节假日与调休";
    public static final String MORE = "更多";

    private SettingsRoute() {
    }
}
