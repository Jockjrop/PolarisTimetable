# -*- coding: utf-8 -*-
"""MainActivity 收尾批:剩余显示文案资源化。标识符/持久化值全部保留。"""
import io, re, sys

BS = chr(92)
PATH = 'MainActivity.java'
with io.open(PATH, encoding='utf-8', newline='') as f:
    lines = f.read().split('\n')

T = [
    (1490, '? "需要检查，" + label + "，" + value : label + "，" + value);',
     '? getString(R.string.review_needs_check_line, label, value) : getString(R.string.review_line, label, value));'),
    (1538, 'return "已导入 " + result.courses.size() + " 门课程 · " + result.pageCount + " 页";',
     'return getString(R.string.parse_ok_summary, result.courses.size(), result.pageCount);'),
    (1540, 'return "已导入 " + result.courses.size() + " 门课程 · 有 " + result.errors.size() + " 条提示";',
     'return getString(R.string.parse_ok_summary_warnings, result.courses.size(), result.errors.size());'),
    (2038, 'meta.append(" · 老师 ").append(course.teacher.trim());',
     'meta.append(getString(R.string.practice_meta_teacher, course.teacher.trim()));'),
    (2069, 'LinearLayout panel = dialogPanel("第 " + currentWeek + " 周课程冲突");',
     'LinearLayout panel = dialogPanel(getString(R.string.conflict_week_title, currentWeek));'),
    (2123, 'item.setContentDescription(firstName + "与" + secondName + "时间冲突，"',
     'item.setContentDescription(getString(R.string.conflict_item_cd, firstName, secondName,'),
    (2124, '+ dayText(conflict.day) + section + "，" + conflict.commonWeeksText()',
     'dayText(conflict.day) + section + "，" + conflict.commonWeeksText()'),
    (2125, '+ "，点击选择要编辑的课程");', ');'),
    (2133, 'dialogAction("编辑：" + courseChoiceText(conflict.first), v -> {',
     'dialogAction(getString(R.string.conflict_edit_prefix, courseChoiceText(conflict.first)), v -> {'),
    (2141, 'dialogAction("编辑：" + courseChoiceText(conflict.second), v -> {',
     'dialogAction(getString(R.string.conflict_edit_prefix, courseChoiceText(conflict.second)), v -> {'),
    (2170, 'String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};', ''),
    (2171, 'return day >= 0 && day < days.length ? days[day] : "日期待定";',
     'return day >= 0 && day < WeekdayLabels.count() ? WeekdayLabels.label(this, day)\n                : getString(R.string.weekday_undetermined);'),
    (2337, 'compactDialogAction("填入示例", v -> showChoiceDialog(v, "填入示例作息",',
     'compactDialogAction(getString(R.string.classtime_fill_example), v -> showChoiceDialog(v, getString(R.string.classtime_fill_example_title),'),
    (2438, 'label.setText("第" + (index + 1) + "节");',
     'label.setText(getString(R.string.classtime_section_ordinal, index + 1));'),
    (2446, 'showTimeDialog("第" + (index + 1) + "节开始",',
     'showTimeDialog(getString(R.string.classtime_section_start_title, index + 1),'),
    (2461, 'showTimeDialog("第" + (index + 1) + "节结束",',
     'showTimeDialog(getString(R.string.classtime_section_end_title, index + 1),'),
    (2624, '"第" + (i + 1) + "节结束时间必须晚于开始时间",',
     'getString(R.string.classtime_error_end_before_start, i + 1),'),
    (2660, 'return rows.size() + " 节 · 第1节 "', 'return getString(R.string.classtime_summary_value, rows.size(),'),
    (2661, '+ timeTextMinute(rows.get(0)[0]) + "–" + timeTextMinute(rows.get(0)[1]);',
     'timeTextMinute(rows.get(0)[0]) + "–" + timeTextMinute(rows.get(0)[1]));'),
    (3984, '"已导入分享课表：" + courses.size() + " 门课程",',
     'getString(R.string.shared_import_done, courses.size()),'),
    (3997, '"已显示 " + visibleDayCount + " 天",',
     'getString(R.string.days_visible_toast, visibleDayCount),'),
    (5779, 'String value = twoDigits(picker.getHour()) + ":" + twoDigits(picker.getMinute()) + "开始";',
     'String value = getString(R.string.classtime_picker_value, twoDigits(picker.getHour())\n                + ":" + twoDigits(picker.getMinute()));'),
    (5739, 'message.setText("请选择本学期第一周的第一天。若跳过，将使用默认日期 " + defaultDate + "。");',
     'message.setText(getString(R.string.first_week_day_message, defaultDate));'),
    (6605, 'return "顶部横幅";', 'return getString(R.string.banner_no_fixed_time_short);'),
    (6609, ': course.startSection + "-" + course.endSection + "节");',
     ': course.startSection + "-" + course.endSection + getString(R.string.import_section_suffix));'),
    (6614, 'return "顶部横幅\\n无固定节次";', 'return getString(R.string.banner_no_fixed_time_long);'),
    (6618, ': course.startSection + "-" + course.endSection + "节");',
     ': course.startSection + "-" + course.endSection + getString(R.string.import_section_suffix));'),
    (6630, 'builder.append("顶部横幅 · 无固定节次");',
     'builder.append(getString(R.string.banner_no_fixed_time_line));'),
    (6635, ': course.startSection + "-" + course.endSection + "节");',
     ': course.startSection + "-" + course.endSection + getString(R.string.import_section_suffix));'),
    (7604, 'Toast.makeText(this, "已应用“" + appearancePresetName() + "”外观", Toast.LENGTH_SHORT).show();',
     'Toast.makeText(this, getString(R.string.appearance_applied_toast, appearancePresetName()), Toast.LENGTH_SHORT).show();'),
    (7623, 'Toast.makeText(this, "已切换为“" + visualTheme + "”", Toast.LENGTH_SHORT).show();',
     'Toast.makeText(this, getString(R.string.theme_switched_toast, visualTheme), Toast.LENGTH_SHORT).show();'),
    (7740, 'item.setText(entry.id.equals(activeScheduleId) ? entry.name + "  当前" : entry.name);',
     'item.setText(entry.id.equals(activeScheduleId) ? entry.name\n                + getString(R.string.schedule_switch_current_suffix) : entry.name);'),
    (7800, 'input("课表名称，例如 大二下", "");', 'input(getString(R.string.schedule_create_name_hint), "");'),
    (7826, 'message.setText("将删除“" + scheduleName + "”。删除后会自动切换到其他课表。");',
     'message.setText(getString(R.string.schedule_delete_confirm_message, scheduleName));'),
    (7970, 'input("例如 2025-2026学年第2学期", semesterName);',
     'input(getString(R.string.semester_edit_hint), semesterName);'),
    (8011, 'button.setText("第 " + currentWeek + " 周 ▾");',
     'button.setText(getString(R.string.week_selector_value, currentWeek));'),
    (8041, 'item.setText("第 " + week + " 周");',
     'item.setText(getString(R.string.settings_current_week_value, week));'),
    (8071, 'weekSelectorButton.setText("第 " + currentWeek + " 周 ▾");',
     'weekSelectorButton.setText(getString(R.string.week_selector_value, currentWeek));'),
    (8132, 'return "第 " + currentWeek + " 周";',
     'return getString(R.string.settings_current_week_value, currentWeek);'),
    (8460, 'message.setText("检测到课程提醒曾因后台清理而失效，现已自动恢复。' + BS + 'n' + BS + 'n为避免再次失效，建议允许 Polaris 后台运行、自启动，并关闭锁屏清理/省电限制。");',
     'message.setText(getString(R.string.reminder_restore_message));'),
]

errors = []
for ln, old, new in T:
    idx = ln - 1
    if idx >= len(lines) or old not in lines[idx]:
        errors.append('行%d 未命中: %s' % (ln, old[:60]))
        continue
    lines[idx] = lines[idx].replace(old, new)

D = {
    '更多操作': 'more_actions_cd',
    '未获得通知权限，课程提醒没有开启': 'reminder_perm_denied',
    '结构化课程数据不可用，已取消写入以保护当前课表': 'structured_data_missing',
    '未识别到课程，请确认PDF是教务系统课表': 'parse_no_courses_hint',
    '解析失败，请确认PDF是文字型教务课表': 'parse_failed_hint',
    '部分字段未识别完整，可继续查看课表': 'parse_partial_hint',
    '这门课程无法拖动调整': 'drag_not_allowed',
    '课程数据已变化，请重试': 'drag_stale_retry',
    '移动课程失败，请重试': 'drag_move_failed',
    '该位置与其他课程时间重叠': 'drag_overlap_warning',
    '本周实践': 'board_practice_title',
    '本周计划': 'side_panel_plan_title',
    '管理': 'common_manage',
    '本周暂无计划，点「管理」新建': 'side_panel_plan_empty',
    '未命名实践': 'board_practice_unnamed',
    '集中实践': 'board_practice_concentrated',
    '以下课程在当前周的上课时间重叠。点击一组冲突，可选择要编辑的课程。': 'conflict_explanation',
    '关闭': 'import_close',
    '选择要编辑的课程': 'conflict_edit_prefix',
    '取消': 'editor_action_cancel',
    '未命名课程': 'course_unnamed',
    '还没有课程': 'empty_courses_title',
    '选择学校后导入教务系统 PDF，Polaris 会解析课程，并在写入前让你检查结果。': 'empty_courses_message',
    '选择学校': 'import_school_chooser_title',
    '选择匹配的学校解析格式，Polaris 会按对应的课表格式识别课程和上课时间；也可以自定义学校名称。': 'school_picker_message',
    '自定义学校…': 'school_custom_action',
    '自定义学校': 'school_custom_title',
    '输入学校名称': 'school_custom_hint_input',
    '输入学校名称后，接着设置这所学校的上课时间（节次作息表）。导入 PDF 时仍需选择匹配的学校解析格式。': 'school_custom_hint',
    '学校名称不能为空': 'school_error_empty',
    '上课时间': 'classtime_title',
    '按学校作息设置每节课的开始与结束时间；可先「填入示例」再修改，或直接「粘贴作息文本」。': 'classtime_hint',
    '填入示例': 'classtime_fill_example',
    '填入示例作息': 'classtime_fill_example_title',
    '从文本粘贴': 'classtime_paste_action',
    '＋ 添加一节': 'classtime_add_row',
    '上课时间已保存并应用': 'classtime_saved_toast',
    '粘贴作息文本': 'classtime_paste_title',
    '粘贴学校作息表…': 'classtime_paste_input_hint',
    '解析并填入': 'classtime_parse_apply',
    '没有识别到节次时间行；每行格式如 1 08:00-08:50': 'classtime_paste_no_rows',
    '至少需要一节课': 'classtime_error_min_one',
    '部分节次时间重叠，已按输入保存，请核对': 'classtime_warn_overlap_saved',
    '未设置': 'classtime_summary_unset',
    '无法读取所选图片，请重新选择': 'image_read_failed',
    '账户名称不能为空': 'profile_name_empty',
    '账户资料已保存': 'profile_saved',
    '课程已保存，可以撤销本次保存': 'save_undo_cd',
    '撤销本次课程保存': 'save_undo_button_cd',
    '课程已保存，可以撤销': 'save_undo_announce',
    '课程已发生变化，请重新打开后编辑': 'editor_course_changed',
    '本周没有这节课程，无需删除': 'delete_no_this_week',
    '已删除本周此节课程': 'delete_done_week',
    '已删除每周此节课程': 'delete_done_meeting',
    '已删除该课程全部节次': 'delete_done_all',
    '关闭计划管理': 'plan_overlay_close_cd',
    '＋ 新建计划': 'plan_action_new',
    '课程表': 'schedule_switch_title',
    '新增课表': 'schedule_action_add',
    '删除此课表': 'schedule_action_delete',
    '修改课表名称': 'schedule_rename_title',
    '课表名称': 'settings_row_schedule_name',
    '课表名称不能为空': 'import_error_name_empty',
    '至少保留一个课表': 'schedule_error_last_one',
    '删除此课表？': 'schedule_delete_confirm_title',
    '确认删除': 'schedule_confirm_delete',
    '已删除课表': 'schedule_deleted_toast',
    '修改学期': 'semester_edit_title',
    '学期不能为空': 'semester_error_empty',
    '选择周次': 'editor_title_week',
    '设置第一周的第一天': 'first_week_day_title',
    '使用此日期': 'first_week_day_use',
    '跳过，使用默认日期': 'first_week_day_skip',
    '保存': 'editor_action_save',
    '最近一次解析诊断': 'diagnostics_title',
    '还没有解析记录。导入一次 PDF 课表后，可在这里查看识别提示和详细日志。': 'diagnostics_empty',
    '复制诊断日志': 'diagnostics_copy_action',
    '暂无可复制的解析诊断': 'diagnostics_copy_none',
    '无法访问系统剪贴板': 'import_clipboard_unavailable',
    'Polaris解析诊断': 'diagnostics_clip_label',
    '解析诊断已复制': 'diagnostics_copied',
    '没有找到匹配的课程': 'manage_no_match',
    '已选中': 'row_selected_cd',
    '未选中': 'row_unselected_cd',
    '顶部横幅\n无固定节次': 'banner_no_fixed_time_long',
    '保存并应用': 'profile_save_apply',
    '课程提醒已关闭': 'reminder_toggle_off_toast',
    '开启弹窗提醒': 'reminder_overlay_title',
    '课程提醒将以悬浮窗弹窗显示在任意应用上方。需要先授予“悬浮窗”权限；不授权也可以继续，提醒会改用系统通知。': 'reminder_overlay_message',
    '去授权悬浮窗': 'reminder_overlay_grant',
    '继续（使用系统通知）': 'reminder_overlay_continue',
    '请先在系统设置中允许 Polaris 发送通知': 'reminder_notify_permission_toast',
    '课程提醒已开启': 'reminder_enabled_toast',
    '提高提醒准时性': 'reminder_exact_title',
    '允许“闹钟和提醒”权限后，课程提醒可按设定时间准时触发。暂不允许也能使用普通提醒，但系统省电时可能延迟。': 'reminder_exact_message',
    '允许精确提醒': 'reminder_exact_allow',
    '使用普通提醒': 'reminder_exact_use_normal',
    '未开启': 'reminder_status_off',
    '悬浮窗弹窗 · 精确': 'reminder_status_overlay_exact',
    '悬浮窗弹窗': 'reminder_status_overlay',
    '系统通知 · 精确': 'reminder_status_notify_exact',
    '系统通知（可能延迟）': 'reminder_status_notify_delayed',
    '需要悬浮窗或通知权限': 'reminder_status_need_permission',
    '请先开启课程提醒': 'reminder_enable_first',
    '当前使用悬浮窗弹窗 · 精确提醒': 'reminder_status_overlay_exact_toast',
    '提醒恢复': 'reminder_restore_title',
    '去设置': 'reminder_restore_go_settings',
    '知道了': 'reminder_restore_ack',
}
dict_hits = 0
for i in range(len(lines)):
    line = lines[i]
    for lit, name in D.items():
        key = '"%s"' % lit
        if key in line:
            line = line.replace(key, 'getString(R.string.%s)' % name)
            dict_hits += 1
    lines[i] = line

# ---- 第三通道:内容定位(day 数组方法、已填入提示、粘贴长提示) ----
third_hits = 0
for i, line in enumerate(lines):
    if 'String[] days = {"周一"' in line or 'String[] names = {"周日"' in line:
        lines[i] = ''
        third_hits += 1
        for j in range(i + 1, min(i + 6, len(lines))):
            l = lines[j]
            if 'day >= 0 && day < days.length' in l:
                lines[j] = l.replace('day < days.length', 'day < WeekdayLabels.count()')
                third_hits += 1
            elif 'return days[day];' in l:
                lines[j] = l.replace('return days[day];', 'return WeekdayLabels.label(this, day);')
                third_hits += 1
            elif 'return "未知";' in l:
                lines[j] = l.replace('return "未知";', 'return getString(R.string.weekday_unknown_short);')
                third_hits += 1
            elif 'return names[date.get(Calendar.DAY_OF_WEEK) - 1];' in l:
                lines[j] = l.replace('return names[date.get(Calendar.DAY_OF_WEEK) - 1];',
                                     'return WeekdayLabels.label(this, date.get(Calendar.DAY_OF_WEEK) + 5);')
                third_hits += 1
    if '"已填入 " + parsed.size() + " 节课，可继续逐节修改",' in line:
        lines[i] = line.replace('"已填入 " + parsed.size() + " 节课，可继续逐节修改",',
                                'getString(R.string.classtime_paste_done, parsed.size()),')
        third_hits += 1
    key = '"每行一个节次，格式：节次 开始-结束。例如：' + BS + 'n1 08:00-08:50' + BS + 'n2 08:55-09:45"'
    if key in line:
        lines[i] = line.replace(key, 'getString(R.string.classtime_paste_hint)')
        third_hits += 1

ALLOW = {
    '标准', '紧凑', '沉浸', '自定义', '默认课表', '08:00 开始', '清爽蓝', '管理员',
    '跟随系统', 'Polaris课程表', '暂无导入记录', '新课表', 'Polaris课表', 'Polaris备份-',
    '分享课表', '不关联', '课表设置', '全局设置', '查看所有课程', '管理已添加课程',
    '系统相册', '从系统相册选择', '清除背景', '纯白', '深海', '深色', '浅色',
    '盾', '课表', '我的', '开', '关', '当前',
    '安全设置', '更多', '计划',
    '全局', '安全', '从', '1-20周',
    '^(?:第?(' + BS + BS + 'd{1,2})' + BS + BS + 's*节?' + BS + BS + 's*[.、．:：]?' + BS + BS + 's*)?',
    '(' + BS + BS + 'd{1,2})[:：](' + BS + BS + 'd{1,2})' + BS + BS + 's*[-~～至]' + BS + BS + 's*(' + BS + BS + 'd{1,2})[:：](' + BS + BS + 'd{1,2})' + BS + BS + 's*$',
    'the "我的" page',
}
leftover = []
for i, line in enumerate(lines):
    for m in re.finditer(r'"([^"]*[一-龥][^"]*)"', line):
        if m.group(1) not in ALLOW:
            leftover.append('%d: %s' % (i + 1, m.group(1)[:44]))

if errors or leftover:
    for e in errors:
        print('T-MISS:', e)
    for e in leftover:
        print('LEFTOVER:', e)
    sys.exit(1)

with io.open(PATH, 'w', encoding='utf-8', newline='') as f:
    f.write('\n'.join(lines))
print('OK: 表达式重写 %d 条,字面量映射 %d 处,内容定位 %d 处,残留仅标识符' % (len(T), dict_hits, third_hits))
