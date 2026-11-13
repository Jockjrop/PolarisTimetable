package com.polaris.timetable.ui;

import com.polaris.timetable.R;

/**
 * 视图 tag key 常量（配合 {@code View.setTag(int, Object)} 使用）。
 *
 * <p>用 keyed tag 而不是字符串 tag 的原因：
 * <ul>
 *   <li>一个 View 只有一个字符串 tag，多个用途会互相覆盖；</li>
 *   <li>字符串常量分散在两个类里，一旦拼写或语义撞名（例如 1.27.16 前的
 *       {@code section_header} 与 {@code settings_header}），换肤时就会把
 *       章节标题当成固定顶栏涂成底色块，且编译期无法发现。</li>
 * </ul>
 * 统一 key 后，撞名会在资源合并阶段直接失败。
 */
public final class ViewTags {

    public static final int SETTINGS_GROUP = R.id.tag_settings_group;
    public static final int SECTION_HEADER = R.id.tag_section_header;
    public static final int SETTING_LABEL = R.id.tag_setting_label;
    public static final int SETTING_VALUE = R.id.tag_setting_value;
    public static final int SWITCH = R.id.tag_switch;
    public static final int SETTINGS_HEADER = R.id.tag_settings_header;
    public static final int SETTINGS_ROOT = R.id.tag_settings_root;
    public static final int SETTINGS_TITLE = R.id.tag_settings_title;
    public static final int SETTINGS_SCROLL = R.id.tag_settings_scroll;

    private ViewTags() {
    }

    /** 标记：keyed tag 只需要 key 存在，值统一用这个哨兵，避免每处 new Object()。 */
    public static final Boolean MARK = Boolean.TRUE;

    public static boolean marked(android.view.View view, int key) {
        return view.getTag(key) != null;
    }
}
