# -*- coding: utf-8 -*-
"""MainActivity 导入流程簇文案资源化:先表达式重写,再字面量映射,最后校验残留。"""
import io, re, sys

PATH = 'MainActivity.java'
with io.open(PATH, encoding='utf-8', newline='') as f:
    lines = f.read().split('\n')

# ---- 第一通道:行级表达式重写(带格式参数的拼接) ----
T = [
    (732, 'instruction.setText("1. 点击下方按钮选择课表图片\\n"', 'instruction.setText(getString(R.string.import_ai_instruction));'),
    (733, '+ "2. 图片和识别指令将预填到所选 AI\\n"', ''),
    (734, '+ "3. 确认输入框内容和图片后，由你手动发送\\n"', ''),
    (735, '+ "4. 回到 Polaris，将自动读取新复制的结果");', ''),
    (1158, '.append(meeting.endSection).append("节");', '.append(meeting.endSection).append(getString(R.string.import_section_suffix));'),
    (944, 'text.append("\\nAI 识别结果无法解析");', 'text.append("\\n").append(getString(R.string.import_ai_parse_failed));'),
    (951, 'errorView.setContentDescription("AI 导入错误，" + text);', 'errorView.setContentDescription(getString(R.string.import_ai_errors_cd, text));'),
    (1027, '"外部 AI 识别 · " + courseCount + " 门课程 · "', 'getString(R.string.import_ai_summary, courseCount,'),
    (1028, '+ preview.meetingCount() + " 条安排",', 'preview.meetingCount()),'),
    (1029, '"识别来源：外部多模态 AI\\n"', 'getString(R.string.import_ai_detail, courseCount,'),
    (1030, '+ "协议：Polaris Schedule JSON v1\\n"', '/* 协议与写入说明在资源内 */'),
    (1031, '+ "文件写入：已由用户预览并确认\\n"', ''),
    (1032, '+ "课程数量：" + courseCount + "\\n"', ''),
    (1033, '+ "上课安排：" + preview.meetingCount(),', 'preview.meetingCount()),'),
    (1034, '"课表导入成功，共 " + courseCount + " 门课程");', 'getString(R.string.import_ai_success_toast, courseCount));'),
    (1039, 'confirmText + "，确认后才会覆盖当前课表数据");', 'confirmText + getString(R.string.import_confirm_cd_overwrite));'),
    (1064, 'preview.regularCourseCount() + " 门普通课程 · "', 'getString(R.string.import_candidates_count, preview.regularCourseCount(),'),
    (1065, '+ preview.practiceCourseCount() + " 门实践课程",', 'preview.practiceCourseCount()),'),
    (1091, 'card.setContentDescription("待导入课程，" + course.name);', 'card.setContentDescription(getString(R.string.import_course_cd, course.name));'),
    (1102, 'card.addView(scheduleImportDetail("教师：" + course.teacher, false));', 'card.addView(scheduleImportDetail(getString(R.string.import_detail_teacher, course.teacher), false));'),
    (1105, 'card.addView(scheduleImportDetail("学分：" + course.credit, false));', 'card.addView(scheduleImportDetail(getString(R.string.import_detail_credit, course.credit), false));'),
    (1146, 'return weeks + "\\n无固定上课时间";', 'return weeks + "\\n" + getString(R.string.import_no_fixed_time);'),
    (1222, ': "学校：" + importParserModel[0].label, v -> {', ': getString(R.string.import_school_value, importParserModel[0].label), v -> {'),
    (1228, 'parserChoice[0].setText("学校：" + model.label);', 'parserChoice[0].setText(getString(R.string.import_school_value, model.label));'),
    (1346, 'lastParseDiagnosticsText = "解析状态：失败\\n原因：" + reason;', 'lastParseDiagnosticsText = getString(R.string.import_diag_failure, reason);'),
    (1348, '"PDF解析失败：" + reason,', 'getString(R.string.import_pdf_failed, reason),'),
    (1357, '"无法启动 PDF 解析：" + reason,', 'getString(R.string.import_launch_failed, reason),'),
    (1375, '? "识别到 " + review.courseCount + " 门课程 · "', '? getString(R.string.import_review_count, review.courseCount,'),
    (1376, '+ review.meetingCount + " 条上课安排"', 'review.meetingCount)'),
    (1390, 'Math.max(0, result.pageCount) + " 页", false));', 'getString(R.string.import_pages_value, Math.max(0, result.pageCount)), false));'),
    (1396, 'review.addedCount + " 条", false));', 'getString(R.string.import_count_value, review.addedCount), false));'),
    (1397, 'review.modifiedCount + " 条",', 'getString(R.string.import_count_value, review.modifiedCount),'),
    (1399, 'review.removedCount + " 条",', 'getString(R.string.import_count_value, review.removedCount),'),
    (1407, 'review.errorCount + " 条", true));', 'getString(R.string.import_count_value, review.errorCount), true));'),
    (1410, 'review.warningCount + " 条", true));', 'getString(R.string.import_count_value, review.warningCount), true));'),
    (1414, 'review.unknownWeekCount + " 条安排", true));', 'getString(R.string.import_arrangements_value, review.unknownWeekCount), true));'),
    (1418, 'review.missingLocationCount + " 条安排", true));', 'getString(R.string.import_arrangements_value, review.missingLocationCount), true));'),
    (1422, 'review.missingTeacherCount + " 条安排", true));', 'getString(R.string.import_arrangements_value, review.missingTeacherCount), true));'),
    (1426, 'review.conflictCount + " 组", true));', 'getString(R.string.import_conflict_value, review.conflictCount), true));'),
    (1443, 'confirm.setContentDescription(confirmText + "，此操作将在确认后写入课程数据");', 'confirm.setContentDescription(confirmText + getString(R.string.import_confirm_cd_review));'),
]

errors = []
for ln, old, new in T:
    idx = ln - 1
    if idx >= len(lines) or old not in lines[idx]:
        errors.append('行%d 未命中: %s' % (ln, old[:50]))
        continue
    lines[idx] = lines[idx].replace(old, new)

# ---- 第二通道:引号字面量映射(无格式参数) ----
D = {
    'AI 识别导入': 'import_ai_title',
    '复制 AI 识别指令': 'import_ai_copy_prompt',
    '选择课表图片并跳转 AI': 'import_ai_pick_image',
    '将 AI 返回内容粘贴到这里': 'import_ai_paste_hint',
    'AI 返回内容输入框': 'import_ai_input_cd',
    '从剪贴板粘贴': 'import_ai_paste',
    '解析并预览': 'import_ai_parse',
    '请先粘贴或输入 AI 返回内容': 'import_ai_error_empty',
    '解析 AI 返回内容并打开课程预览': 'import_ai_parse_cd',
    '取消': 'editor_action_cancel',
    'AI 识别指令已复制': 'import_ai_prompt_copied',
    '无法访问系统剪贴板': 'import_clipboard_unavailable',
    'Polaris AI 识别指令': 'import_clipboard_label',
    '未找到可选择图片的应用': 'import_no_image_picker',
    'Polaris 课表图片': 'import_ai_share_image_label',
    '请确认 AI 输入框中的图片和指令，再手动发送': 'import_ai_send_hint',
    '选择支持图片识别的 AI': 'import_ai_chooser_title',
    '未找到可接收课表图片的 AI 应用': 'import_no_ai_app',
    '未检测到新复制的 AI 返回文本': 'import_ai_return_none',
    '已自动读取 AI 返回内容': 'import_ai_return_read_cd',
    '剪贴板中没有可用文本': 'import_clipboard_empty',
    '已从剪贴板粘贴 AI 返回内容': 'import_ai_paste_done_cd',
    '请检查以下问题：': 'import_ai_errors_header',
    '没有识别到可导入课程': 'import_ai_no_courses',
    '没有识别到可导入的课程': 'import_review_empty',
    '检查 AI 识别结果': 'import_ai_review_title',
    '确认后将覆盖当前正在查看的课表。识别学期和教学班仅用于本次预览。': 'import_ai_note_overwrite',
    '确认后将导入当前正在查看的课表。识别学期和教学班仅用于本次预览。': 'import_ai_note_import',
    '确认并覆盖当前课表': 'import_confirm_overwrite',
    '确认导入': 'import_confirm_short',
    '课程候选': 'import_candidates_heading',
    '识别到': 'import_review_recognized',
    '识别学期': 'import_review_semester',
    '需要检查': 'import_review_needs_check',
    '提示': 'import_review_hint',
    '周次待确认': 'editor_week_unknown_full',
    '导入到当前课表？': 'import_overwrite_title',
    '先解析 PDF 并检查课程、缺失字段和冲突；只有在检查页再次确认后，才会覆盖当前课表。请先选择学校解析模型。': 'import_overwrite_message',
    '选择学校解析模型': 'import_pick_model',
    '选择学校': 'import_school_chooser_title',
    '解析并检查': 'import_parse_and_check',
    '请先选择学校解析模型': 'import_error_pick_model',
    '新建课表并检查': 'import_create_and_check',
    '新课表必须先选择学校解析模型': 'import_error_create_pick_model',
    '设置课表名称': 'import_name_title',
    '课表名称': 'settings_row_schedule_name',
    '开始解析并检查': 'import_start_parse',
    '课表名称不能为空': 'import_error_name_empty',
    '课表正在解析，请稍候': 'import_parsing_busy',
    '正在解析课表...': 'import_parsing_header',
    '未知原因': 'import_reason_unknown',
    '解析失败': 'import_diag_failed',
    '导入失败': 'import_failed_header',
    '检查导入结果': 'import_review_title',
    '学校': 'import_review_label_school',
    '学期': 'import_review_label_semester',
    'PDF 页数': 'import_review_pages',
    '开学日期': 'import_review_start_date',
    '确认后设置': 'import_review_date_pending',
    '将创建新课表': 'import_review_destination_new',
    '与当前课表对比': 'import_review_destination_compare',
    '将导入当前课表': 'import_review_destination_current',
    '新增安排': 'import_review_added',
    '修改安排': 'import_review_modified',
    '移除安排': 'import_review_removed',
    '检查结果': 'import_review_check_result',
    '未发现明显问题': 'import_review_no_issues',
    '解析错误': 'import_review_parse_errors',
    '解析提示': 'import_review_warnings',
    '周次不确定': 'import_review_unknown_weeks',
    '缺少教室': 'import_review_missing_location',
    '缺少教师': 'import_review_missing_teacher',
    '课程冲突': 'import_review_conflicts',
    '查看解析诊断': 'import_review_diagnostics',
    '确认并创建新课表': 'import_confirm_new',
    '确认导入课表': 'import_confirm_import_pdf',
    '当前课表不会发生任何变更。请查看解析诊断后重新选择 PDF。': 'import_blocked_message',
    '取消导入': 'import_cancel_import',
    '关闭': 'import_close',
}
dict_hits = 0
for i in range(728, min(1460, len(lines))):
    line = lines[i]
    for lit, name in D.items():
        key = '"%s"' % lit
        if key in line:
            line = line.replace(key, 'getString(R.string.%s)' % name)
            dict_hits += 1
    lines[i] = line

# ---- 校验:729-1460 残留(允许 "新课表" 数据默认值) ----
leftover = []
for i in range(728, min(1460, len(lines))):
    for m in re.finditer(r'"([^"]*[一-龥][^"]*)"', lines[i]):
        if m.group(1) != '新课表':
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
