# -*- coding: utf-8 -*-
"""MainActivity 导出/分享/备份 + 课程管理簇文案资源化:表达式重写 + 字面量映射 + 残留校验。"""
import io, re, sys

PATH = 'MainActivity.java'
with io.open(PATH, encoding='utf-8', newline='') as f:
    lines = f.read().split('\n')

# ---- 第一通道:行级表达式重写 ----
T = [
    (3351, 'share.putExtra(Intent.EXTRA_SUBJECT, scheduleName + " · Polaris课表");',
     'share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_file_subject, scheduleName));'),
    (3352, 'share.putExtra(Intent.EXTRA_TEXT, "使用 Polaris课程表 打开附件即可预览并导入。");',
     'share.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_file_text));'),
    (3383, '"Polaris课程表备份 · " + scheduleName);',
     'getString(R.string.backup_share_subject, scheduleName));'),
    (3385, '"使用 Polaris课程表 的「恢复备份」导入，即可完整还原课表与设置。");',
     'getString(R.string.backup_share_text));'),
    (3354, 'share.setClipData(ClipData.newRawUri("Polaris课表文件", uri));',
     'share.setClipData(ClipData.newRawUri(getString(R.string.share_clip_label), uri));'),
    (3358, '"生成课表分享文件失败：" + exception.getMessage(),',
     'getString(R.string.share_file_failed, exception.getMessage()),'),
    (3391, '"生成备份文件失败：" + exception.getMessage(),',
     'getString(R.string.backup_share_failed, exception.getMessage()),'),
    (3415, '"备份文件读取失败：" + reason,',
     'getString(R.string.backup_read_failed, reason),'),
    (3428, 'message.setText("备份创建于 " + summary.createdAt',
     'message.setText(getString(R.string.backup_confirm_message, summary.createdAt,'),
    (3429, '+ "（来源 " + sourceVersion + "）\\n"', 'sourceVersion,'),
    (3430, '+ "包含 " + summary.scheduleCount + " 个课表 · 共 "', 'summary.scheduleCount,'),
    (3431, '+ summary.courseCount + " 门课程\\n\\n"', 'summary.courseCount),'),
    (3432, '+ "恢复将覆盖本机当前所有课表和设置，且无法撤销。"', ''),
    (3433, '+ "建议先导出当前数据作为备份。");', ''),
    (3463, '"恢复失败：" + exception.getMessage(),',
     'getString(R.string.backup_restore_failed, exception.getMessage()),'),
    (3481, 'Toast.makeText(this, "已从备份恢复：" + bundle.schedules.size()',
     'Toast.makeText(this, getString(R.string.backup_restored_toast, bundle.schedules.size(),'),
    (3482, '+ " 个课表 · " + restoredCourseCount + " 门课程", Toast.LENGTH_LONG).show();',
     'restoredCourseCount), Toast.LENGTH_LONG).show();'),
    (3582, '"正在生成第 " + request.week + " 周课表图片…",',
     'getString(R.string.export_generating_week_image, request.week),'),
    (3596, 'handleScheduleExportFailure(exception, "导出图片失败：");',
     'handleScheduleExportFailure(exception, getString(R.string.export_failed_image));'),
    (3632, '"正在生成第 " + request.week + " 周课表 PDF…",',
     'getString(R.string.export_generating_week_pdf, request.week),'),
    (3646, 'handleScheduleExportFailure(exception, "导出 PDF 失败：");',
     'handleScheduleExportFailure(exception, getString(R.string.export_failed_pdf));'),
    (3680, 'handleScheduleExportFailure(exception, "导出整学期 PDF 失败：");',
     'handleScheduleExportFailure(exception, getString(R.string.export_failed_semester));'),
    (3705, 'share.putExtra(Intent.EXTRA_SUBJECT, scheduleName + " · 第 " + week + " 周");',
     'share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_week_subject, scheduleName, week));'),
    (3706, 'share.putExtra(Intent.EXTRA_TEXT, "Polaris课程表 · 第 " + week + " 周");',
     'share.putExtra(Intent.EXTRA_TEXT, getString(R.string.export_share_week_text, week));'),
    (3722, 'share.putExtra(Intent.EXTRA_SUBJECT, scheduleName + " · 第 " + week + " 周");',
     'share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_week_subject, scheduleName, week));'),
    (3723, 'share.putExtra(Intent.EXTRA_TEXT, "Polaris课程表 · 第 " + week + " 周");',
     'share.putExtra(Intent.EXTRA_TEXT, getString(R.string.export_share_week_text, week));'),
    (3739, 'share.putExtra(Intent.EXTRA_SUBJECT, scheduleName + " · 整学期合并课表");',
     'share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_subject_semester, scheduleName));'),
    (3740, 'share.putExtra(Intent.EXTRA_TEXT, "Polaris课程表 · 整学期合并课表");',
     'share.putExtra(Intent.EXTRA_TEXT, getString(R.string.export_share_text_semester));'),
    (3762, 'ScheduleCalendarExporter.safeFileName(scheduleName) + "-日历.ics"),',
     'ScheduleCalendarExporter.safeFileName(scheduleName) + getString(R.string.export_ics_suffix)),'),
    (3763, '"text/calendar", "iCal 日历");', '"text/calendar", getString(R.string.export_ics_label));'),
    (3765, '"导出 iCal 日历失败：" + exception.getMessage(),',
     'getString(R.string.export_ics_failed, exception.getMessage()),'),
    (3784, '"导出 CSV 失败：" + exception.getMessage(),',
     'getString(R.string.export_csv_failed, exception.getMessage()),'),
    (3817, '"Polaris课程表 · " + label + " · " + file.getName());',
     'getString(R.string.calendar_share_subject, label, file.getName()));'),
    (3819, 'share.setClipData(ClipData.newRawUri("Polaris" + label + "文件", uri));',
     'share.setClipData(ClipData.newRawUri(getString(R.string.calendar_clip_label, label), uri));'),
    (3821, 'startActivity(Intent.createChooser(share, "分享" + label + "文件"));',
     'startActivity(Intent.createChooser(share, getString(R.string.calendar_share_chooser, label)));'),
    (3823, 'Toast.makeText(this, label + "已生成，但无法打开系统分享面板",',
     'Toast.makeText(this, getString(R.string.calendar_share_panel_failed, label),'),
    (3851, '"课表文件导入失败：" + reason,', 'getString(R.string.shared_file_import_failed, reason),'),
    (3921, '"课表分享链接无效：" + exception.getMessage(), Toast.LENGTH_LONG).show();',
     'getString(R.string.shared_link_invalid_toast, exception.getMessage()), Toast.LENGTH_LONG).show();'),
    (3929, 'message.setText("分享链接包含 " + sharedCourses.size() + " 门课程，继续导入会覆盖当前课表。");',
     'message.setText(getString(R.string.shared_import_message, sharedCourses.size()));'),
    (6108, 'text.append("课程 ").append(stats.courseCount).append(" 门");',
     'text.append(getString(R.string.manage_stats_course, stats.courseCount));'),
    (6110, 'text.append(" · 实验 ").append(stats.experimentCount);',
     'text.append(getString(R.string.manage_stats_experiment, stats.experimentCount));'),
    (6113, 'text.append(" · 实践 ").append(stats.practiceCount);',
     'text.append(getString(R.string.manage_stats_practice, stats.practiceCount));'),
    (6116, 'text.append(" · 网络 ").append(stats.onlineCount);',
     'text.append(getString(R.string.manage_stats_online, stats.onlineCount));'),
    (6118, "text.append('\\n').append(\"每周课时 \").append(stats.weeklySections)",
     'text.append(getString(R.string.manage_stats_weekly, stats.weeklySections,'),
    (6119, '.append(" 节 · 学期共 ").append(stats.semesterSections).append(" 节");',
     'stats.semesterSections));'),
    (6121, 'text.append(" · 学分 ").append(formatCredit(stats.totalCredits));',
     'text.append(getString(R.string.manage_stats_credit, formatCredit(stats.totalCredits)));'),
    (6124, "text.append('\\n').append(\"教师 \").append(stats.teacherCount).append(\" 位\");",
     'text.append(getString(R.string.manage_stats_teacher, stats.teacherCount));'),
    (6126, 'text.append(" · ").append(stats.topTeacher).append(" ")',
     'text.append(getString(R.string.manage_stats_top_teacher,'),
    (6127, '.append(stats.topTeacherCount).append(" 门");',
     'stats.topTeacher, stats.topTeacherCount));'),
    (6131, 'courseManageStatsText.setContentDescription("课程统计，" + text);',
     'courseManageStatsText.setContentDescription(getString(R.string.manage_stats_cd, text));'),
    (6233, '"已选 " + courseManageSelectedIds.size() + " 门");',
     'getString(R.string.manage_selected_count, courseManageSelectedIds.size()));'),
    (6298, 'message.setText("为选中的 " + courseManageSelectedIds.size()',
     'message.setText(getString(R.string.manage_batch_color_message, courseManageSelectedIds.size()));'),
    (6299, '+ " 门课程设置统一颜色。");', ''),
    (6353, 'message.setText("为选中的 " + courseManageSelectedIds.size()',
     'message.setText(getString(R.string.manage_batch_teacher_message, courseManageSelectedIds.size()));'),
    (6354, '+ " 门课程设置统一教师（覆盖原有教师信息）。");', ''),
    (6402, 'message.setText("删除选中的 " + courseManageSelectedIds.size()',
     'message.setText(getString(R.string.manage_batch_delete_message, courseManageSelectedIds.size()));'),
    (6403, '+ " 门课程（包括全部上课安排）？该操作无法撤销。");', ''),
    (6341, '"已更新 " + updated + " 门课程的颜色",',
     'getString(R.string.manage_updated_color, updated),'),
    (6390, '"已更新 " + updated + " 门课程的教师",',
     'getString(R.string.manage_updated_teacher, updated),'),
    (6442, '"已删除 " + removed + " 门课程",',
     'getString(R.string.manage_deleted, removed),'),
]

errors = []
for ln, old, new in T:
    idx = ln - 1
    if idx >= len(lines) or old not in lines[idx]:
        errors.append('行%d 未命中: %s' % (ln, old[:60]))
        continue
    lines[idx] = lines[idx].replace(old, new)

# ---- 第二通道:引号字面量映射 ----
D = {
    '导入 PDF': 'action_import_pdf',
    'AI 识别导入': 'action_import_ai',
    '导入课表文件': 'action_import_file',
    '手动添加课程': 'action_add_course',
    '分享课表文件': 'action_share_file',
    '导出课表': 'export_dialog_title',
    '未找到可选择课表文件的应用': 'import_no_file_picker',
    '还没有课程，先导入或添加课程后再分享': 'share_no_courses',
    '无法创建课表分享目录': 'share_dir_failed',
    '无法创建备份目录': 'backup_dir_failed',
    'Polaris课表文件': 'share_clip_label',
    '课表名称': 'settings_row_schedule_name',
    '分享课表文件': 'share_chooser_file',
    'Polaris备份文件': 'backup_clip_label',
    '导出备份': 'backup_chooser_title',
    '未找到可选择文件的应用': 'backup_picker_missing',
    '无法读取备份文件': 'backup_read_unknown',
    '未知版本': 'backup_version_unknown',
    '恢复备份': 'settings_row_restore_backup',
    '恢复并覆盖本机数据': 'backup_confirm_action',
    '恢复失败': 'backup_restore_failed',
    '课表文件正在生成，请稍候': 'export_busy',
    '选择周次导出单周课表，或把全学期课程合并到一张课表中导出。': 'export_dialog_message',
    '导出周次': 'export_cd_week',
    '上一周': 'export_cd_prev_week',
    '下一周': 'export_cd_next_week',
    '导出指定周图片': 'export_action_week_image',
    '导出指定周 PDF': 'export_action_week_pdf',
    '导出整学期 PDF': 'export_action_semester_pdf',
    '导出 iCal 日历 (.ics)': 'export_action_ics',
    '导出 CSV (.csv)': 'export_action_csv',
    '取消': 'editor_action_cancel',
    '本周没有课程，请切换到有课周后再导出': 'export_week_empty',
    '当前课表没有课程，请先导入或添加课程': 'export_semester_empty',
    '正在生成整学期合并课表 PDF…': 'export_generating_semester',
    '未知原因': 'import_reason_unknown',
    '导出任务启动失败，请重试': 'export_start_failed',
    'Polaris课表图片': 'export_clip_week_image',
    'Polaris课表 PDF': 'export_clip_week_pdf',
    'Polaris整学期课表 PDF': 'export_clip_semester_pdf',
    '分享本周课表图片': 'export_share_week_image_chooser',
    '分享本周课表 PDF': 'export_share_week_pdf_chooser',
    '分享整学期课表 PDF': 'export_share_semester_chooser',
    '图片已生成，但无法打开系统分享面板': 'export_share_panel_failed_image',
    'PDF 已生成，但无法打开系统分享面板': 'export_share_panel_failed_pdf',
    '整学期 PDF 已生成，但无法打开系统分享面板': 'export_share_panel_failed_semester',
    '无法创建导出目录': 'export_dir_failed',
    '文件中的课表数据无效': 'shared_file_invalid',
    '无法读取文件内容': 'shared_file_read_failed',
    '链接导入': 'shared_link_title',
    '粘贴 Polaris 课程表分享链接，确认后可直接导入。': 'shared_link_message',
    '请先粘贴分享链接': 'shared_link_empty',
    '这不是有效的 Polaris 课表分享链接': 'shared_link_invalid',
    '分享链接里没有可导入的课程': 'shared_link_no_courses',
    '导入分享课表？': 'shared_import_title',
    '确认导入': 'import_confirm_short',
    '设置课表名称': 'import_name_title',
    '课表名称不能为空': 'import_error_name_empty',
    '选择课程': 'manage_title_select',
    '全部课程': 'manage_title_all',
    '完成': 'manage_select_done',
    '多选': 'manage_select_multi',
    '完成多选': 'manage_select_done_cd',
    '进入多选': 'manage_select_multi_cd',
    '改颜色': 'manage_action_color',
    '改教师': 'manage_action_teacher',
    '删除': 'manage_action_delete',
    '请先选择课程': 'manage_error_no_selection',
    '批量修改颜色': 'manage_batch_color_title',
    '选择颜色': 'manage_swatch_cd',
    '批量修改教师': 'manage_batch_teacher_title',
    '教师姓名': 'manage_teacher_hint',
    '请输入教师姓名': 'manage_error_teacher_empty',
    '批量删除课程': 'manage_batch_delete_title',
    '删除选中课程': 'manage_batch_delete_action',
    '没有可更新的课程': 'manage_error_nothing_updated',
    '没有可删除的课程': 'manage_error_nothing_deleted',
    '未命名课程': 'course_unnamed',
    '搜索课程 / 教师 / 地点': 'manage_search_hint',
    '搜索课程': 'manage_search_cd',
    '该课程数据较旧，暂不支持批量操作': 'manage_old_data_unsupported',
}
dict_hits = 0
for i in range(3258, min(3975, len(lines))):
    line = lines[i]
    for lit, name in D.items():
        key = '"%s"' % lit
        if key in line:
            line = line.replace(key, 'getString(R.string.%s)' % name)
            dict_hits += 1
    lines[i] = line
for i in range(6088, min(6460, len(lines))):
    line = lines[i]
    for lit, name in D.items():
        key = '"%s"' % lit
        if key in line:
            line = line.replace(key, 'getString(R.string.%s)' % name)
            dict_hits += 1
    lines[i] = line

# ---- 校验:两区间残留(允许数据默认值) ----
ALLOW = {'Polaris课表', 'Polaris备份-', '分享课表'}
leftover = []
for i in list(range(3258, min(3975, len(lines)))) + list(range(6088, min(6460, len(lines)))):
    for m in re.finditer(r'"([^"]*[一-龥][^"]*)"', lines[i]):
        if m.group(1) not in ALLOW:
            leftover.append('%d: %s' % (i + 1, m.group(1)[:40]))

if errors or leftover:
    for e in errors:
        print('T-MISS:', e)
    for e in leftover:
        print('LEFTOVER:', e)
    sys.exit(1)

with io.open(PATH, 'w', encoding='utf-8', newline='') as f:
    f.write('\n'.join(lines))
print('OK: 表达式重写 %d 条,字面量映射 %d 处,无残留' % (len(T), dict_hits))
