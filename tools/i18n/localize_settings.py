# -*- coding: utf-8 -*-
"""MainActivity 设置页簇文案资源化:行级精确替换,每条必须恰好命中一次。"""
import io, sys

PATH = 'MainActivity.java'
with io.open(PATH, encoding='utf-8', newline='') as f:
    lines = f.read().split('\n')

# (起始行, 结束行, 旧子串, 新子串),行号 1-based,含端点
R = [
    (4007, 4007, 'heading.setText("设置");', 'heading.setText(getString(R.string.settings_title));'),
    (4014, 4014, 'description.setText("选择课表页显示几天");', 'description.setText(getString(R.string.settings_days_description));'),
    (4023, 4023, 'dayChoice("5天", 5, dialog)', 'dayChoice(getString(R.string.settings_days_option, 5), 5, dialog)'),
    (4024, 4024, 'dayChoice("6天", 6, dialog)', 'dayChoice(getString(R.string.settings_days_option, 6), 6, dialog)'),
    (4025, 4025, 'dayChoice("7天", 7, dialog)', 'dayChoice(getString(R.string.settings_days_option, 7), 7, dialog)'),
    (5180, 5180, 'return "管理课表与上课时间";', 'return getString(R.string.settings_themed_desc_schedule);'),
    (5183, 5183, 'return "主题、通知与显示设置";', 'return getString(R.string.settings_themed_desc_global);'),
    (5186, 5186, 'return "版本、联系与更多设置";', 'return getString(R.string.settings_themed_desc_more);'),
    (5188, 5188, 'return "账户与本机数据保护";', 'return getString(R.string.settings_themed_desc_security);'),
    (5910, 5910, 'settingValueRow("课表名称"', 'settingValueRow(getString(R.string.settings_row_schedule_name)'),
    (5911, 5911, 'settingValueRow("当前周", "第 " + currentWeekFromDate() + " 周", v -> {})',
     'settingValueRow(getString(R.string.settings_row_current_week), getString(R.string.settings_current_week_value, currentWeekFromDate()), v -> {})'),
    (5912, 5912, 'sectionHeader("课表外观")', 'sectionHeader(getString(R.string.settings_section_schedule_appearance))'),
    (5914, 5914, 'settingSwitchRow("显示周六"', 'settingSwitchRow(getString(R.string.settings_row_show_saturday)'),
    (5920, 5920, 'settingSwitchRow("显示周日"', 'settingSwitchRow(getString(R.string.settings_row_show_sunday)'),
    (5926, 5926, 'settingSwitchRow("显示非本周课程"', 'settingSwitchRow(getString(R.string.settings_row_show_out_of_week)'),
    (5932, 5932, 'sectionHeader("课程提醒")', 'sectionHeader(getString(R.string.settings_section_reminder))'),
    (5934, 5934, 'settingSwitchRow("课程提醒",', 'settingSwitchRow(getString(R.string.settings_row_reminder),'),
    (5936, 5936, 'settingValueRow("提前时间", reminderMinutesBefore + " 分钟",',
     'settingValueRow(getString(R.string.settings_row_reminder_lead), getString(R.string.settings_minutes_value, reminderMinutesBefore),'),
    (5937, 5937, 'showChoiceDialog(v, "提前时间",', 'showChoiceDialog(v, getString(R.string.settings_row_reminder_lead),'),
    (5938, 5938, 'new String[]{"5 分钟", "10 分钟", "15 分钟", "30 分钟"},',
     'new String[]{getString(R.string.settings_minutes_5), getString(R.string.settings_minutes_10), getString(R.string.settings_minutes_15), getString(R.string.settings_minutes_30)},'),
    (5939, 5939, 'reminderMinutesBefore + " 分钟", value -> {', 'getString(R.string.settings_minutes_value, reminderMinutesBefore), value -> {'),
    (5944, 5944, 'settingValueRow("提醒状态"', 'settingValueRow(getString(R.string.settings_row_reminder_status)'),
    (5947, 5947, 'sectionHeader("上课时间")', 'sectionHeader(getString(R.string.settings_section_class_time))'),
    (5949, 5949, 'settingValueRow("节次时间表"', 'settingValueRow(getString(R.string.settings_row_class_time_table)'),
    (5952, 5952, 'sectionHeader("课表数据")', 'sectionHeader(getString(R.string.settings_section_schedule_data))'),
    (5954, 5954, 'settingValueRow("第一周的第一天"', 'settingValueRow(getString(R.string.settings_row_first_week_day)'),
    (5955, 5955, 'showDateDialog("第一周的第一天"', 'showDateDialog(getString(R.string.settings_row_first_week_day)'),
    (5962, 5962, 'settingValueRow("学期周数", semesterWeeks + " 周",',
     'settingValueRow(getString(R.string.settings_row_semester_weeks), getString(R.string.settings_weeks_value, semesterWeeks),'),
    (5963, 5963, 'showNumberDialog("学期周数"', 'showNumberDialog(getString(R.string.settings_row_semester_weeks)'),
    (5971, 5971, 'settingValueRow("查看所有课程", courses.size() + " 门", v -> showCourseManagePage())',
     'settingValueRow(getString(R.string.settings_row_view_all_courses), getString(R.string.settings_courses_value, courses.size()), v -> showCourseManagePage())'),
    (5972, 5972, 'settingValueRow("解析诊断"', 'settingValueRow(getString(R.string.settings_row_parse_diagnostics)'),
    (7200, 7200, 'if ("课表设置".equals(target)) {', 'if ("课表设置".equals(target)) { // 页面标题同时是路由标识,不资源化'),
    (7421, 7421, 'sectionHeader("显示")', 'sectionHeader(getString(R.string.settings_section_display))'),
    (7423, 7423, 'settingValueRow("课程表", scheduleName', 'settingValueRow(getString(R.string.settings_row_timetable), scheduleName'),
    (7424, 7424, 'settingValueRow("视觉主题", visualTheme,', 'settingValueRow(getString(R.string.settings_row_visual_theme), visualTheme,'),
    (7425, 7425, 'showChoiceDialog(v, "视觉主题",', 'showChoiceDialog(v, getString(R.string.settings_row_visual_theme),'),
    (7427, 7427, 'settingValueRow("深色模式", darkMode,', 'settingValueRow(getString(R.string.settings_row_dark_mode), darkMode,'),
    (7428, 7428, 'new String[]{"跟随系统", "浅色", "深色"}',
     'new String[]{getString(R.string.settings_dark_follow_system), getString(R.string.settings_dark_light), getString(R.string.settings_dark_dark)}'),
    (7440, 7440, 'settingValueRow("课程表背景", backgroundImageUri.length() == 0 ? "未设置" : "系统相册",',
     'settingValueRow(getString(R.string.settings_row_board_background), backgroundImageUri.length() == 0 ? getString(R.string.settings_common_not_set) : getString(R.string.settings_album_value),'),
    (7441, 7441, 'showChoiceDialog(v, "课程表背景",', 'showChoiceDialog(v, getString(R.string.settings_row_board_background),'),
    (7453, 7453, 'updateSettingValueRow(v, "未设置");', 'updateSettingValueRow(v, getString(R.string.settings_common_not_set));'),
    (7458, 7458, 'settingSwitchRow("显示本周实践"', 'settingSwitchRow(getString(R.string.settings_row_show_practice)'),
    (7464, 7464, 'settingSwitchRow("折叠午休时间"', 'settingSwitchRow(getString(R.string.settings_row_collapse_lunch)'),
    (7471, 7471, 'sectionHeader("外观")', 'sectionHeader(getString(R.string.settings_section_appearance))'),
    (7473, 7473, 'settingValueRow("外观预设"', 'settingValueRow(getString(R.string.settings_row_appearance_preset)'),
    (7474, 7474, 'showChoiceDialog(v, "外观预设",', 'showChoiceDialog(v, getString(R.string.settings_row_appearance_preset),'),
    (7483, 7483, 'settingValueRow("高级设置", "已收起", v -> {',
     'settingValueRow(getString(R.string.settings_row_advanced), getString(R.string.settings_advanced_collapsed), v -> {'),
    (7486, 7486, 'advancedExpanded[0] ? "已展开" : "已收起");',
     'advancedExpanded[0] ? getString(R.string.settings_advanced_expanded) : getString(R.string.settings_advanced_collapsed));'),
    (7491, 7491, 'sectionHeader("界面细节")', 'sectionHeader(getString(R.string.settings_section_ui_detail))'),
    (7493, 7493, 'sectionHeader("课表框架")', 'sectionHeader(getString(R.string.settings_section_board_frame))'),
    (7501, 7501, 'settingSwitchRow("全局毛玻璃效果"', 'settingSwitchRow(getString(R.string.settings_row_shell_blur)'),
    (7506, 7506, 'settingValueRow("课表顶部透明度", timetableHeaderOpacity + "%",',
     'settingValueRow(getString(R.string.settings_row_header_opacity), getString(R.string.settings_percent_value, timetableHeaderOpacity),'),
    (7507, 7507, 'showNumberDialog("课表顶部透明度"', 'showNumberDialog(getString(R.string.settings_row_header_opacity)'),
    (7513, 7513, 'settingValueRow("课表底部透明度", bottomNavOpacity + "%",',
     'settingValueRow(getString(R.string.settings_row_nav_opacity), getString(R.string.settings_percent_value, bottomNavOpacity),'),
    (7514, 7514, 'showNumberDialog("课表底部透明度"', 'showNumberDialog(getString(R.string.settings_row_nav_opacity)'),
    (7520, 7520, 'settingValueRow("底部切换栏高度", bottomNavHeight + " dp",',
     'settingValueRow(getString(R.string.settings_row_nav_height), getString(R.string.settings_dp_value, bottomNavHeight),'),
    (7521, 7521, 'showNumberDialog("底部切换栏高度"', 'showNumberDialog(getString(R.string.settings_row_nav_height)'),
    (7529, 7529, 'settingValueRow("底部圆角半径", bottomNavRadius() + " dp",',
     'settingValueRow(getString(R.string.settings_row_nav_radius), getString(R.string.settings_dp_value, bottomNavRadius()),'),
    (7530, 7530, 'showNumberDialog("底部圆角半径"', 'showNumberDialog(getString(R.string.settings_row_nav_radius)'),
    (7541, 7541, 'settingValueRow("课程格子高度", courseCellHeight + " dp",',
     'settingValueRow(getString(R.string.settings_row_cell_height), getString(R.string.settings_dp_value, courseCellHeight),'),
    (7542, 7542, 'showNumberDialog("课程格子高度"', 'showNumberDialog(getString(R.string.settings_row_cell_height)'),
    (7548, 7548, 'settingValueRow("格子圆角半径", courseCornerRadius + " dp",',
     'settingValueRow(getString(R.string.settings_row_cell_radius), getString(R.string.settings_dp_value, courseCornerRadius),'),
    (7549, 7549, 'showNumberDialog("格子圆角半径"', 'showNumberDialog(getString(R.string.settings_row_cell_radius)'),
    (7555, 7555, 'settingValueRow("格子不透明度", courseBlockOpacity + "%",',
     'settingValueRow(getString(R.string.settings_row_cell_opacity), getString(R.string.settings_percent_value, courseBlockOpacity),'),
    (7556, 7556, 'showNumberDialog("格子不透明度"', 'showNumberDialog(getString(R.string.settings_row_cell_opacity)'),
    (7913, 7913, 'sectionHeader("课表信息")', 'sectionHeader(getString(R.string.settings_section_schedule_info))'),
    (7915, 7915, 'settingValueRow("学期"', 'settingValueRow(getString(R.string.settings_row_semester)'),
    (7917, 7917, 'settingValueRow("学校"', 'settingValueRow(getString(R.string.settings_row_school)'),
    (7921, 7921, 'sectionHeader("账号")', 'sectionHeader(getString(R.string.settings_section_account))'),
    (7923, 7923, 'settingValueRow("登录方式", accountName + " · 无需账号登录", v -> {})',
     'settingValueRow(getString(R.string.settings_row_login), getString(R.string.settings_account_value, accountName), v -> {})'),
    (7924, 7924, 'settingValueRow("本机数据", "课程保存在本机", v -> {})',
     'settingValueRow(getString(R.string.settings_row_local_data), getString(R.string.settings_local_data_value), v -> {})'),
    (7927, 7927, 'sectionHeader("数据备份")', 'sectionHeader(getString(R.string.settings_section_backup))'),
    (7929, 7929, 'settingValueRow("导出备份", "保存全部课表和设置",',
     'settingValueRow(getString(R.string.settings_row_export_backup), getString(R.string.settings_export_backup_desc),'),
    (7931, 7931, 'settingValueRow("恢复备份", "从备份文件完整还原",',
     'settingValueRow(getString(R.string.settings_row_restore_backup), getString(R.string.settings_restore_backup_desc),'),
    (7939, 7939, 'sectionHeader("关于")', 'sectionHeader(getString(R.string.settings_section_about))'),
    (7941, 7941, 'settingValueRow("版本信息"', 'settingValueRow(getString(R.string.settings_row_version)'),
    (7943, 7943, 'Toast.makeText(this, "版本信息已复制"', 'Toast.makeText(this, getString(R.string.settings_toast_version_copied)'),
    (7945, 7945, 'settingValueRow("联系我们"', 'settingValueRow(getString(R.string.settings_row_contact)'),
    (7947, 7947, '"邮箱已复制：" + CONTACT_EMAIL', 'getString(R.string.settings_toast_email_copied, CONTACT_EMAIL)'),
    (7949, 7949, 'settingValueRow("GitHub 项目地址"', 'settingValueRow(getString(R.string.settings_row_github)'),
    (7952, 7952, '"GitHub 地址已复制：" + PROJECT_HOME_URL', 'getString(R.string.settings_toast_github_copied, PROJECT_HOME_URL)'),
]

errors = []
applied = 0
for start, end, old, new in R:
    hits = [i for i in range(start - 1, min(end, len(lines))) if old in lines[i]]
    if len(hits) != 1:
        errors.append('预期1次,实际%d次: 行%d-%d: %s' % (len(hits), start, end, old[:60]))
        continue
    lines[hits[0]] = lines[hits[0]].replace(old, new)
    applied += 1

if errors:
    for e in errors:
        print('MISS:', e)
    sys.exit(1)

with io.open(PATH, 'w', encoding='utf-8', newline='') as f:
    f.write('\n'.join(lines))
print('OK: 应用 %d 条替换' % applied)
