# -*- coding: utf-8 -*-
"""MainActivity 计划页簇文案资源化:行级精确替换。"""
import io, sys

PATH = 'MainActivity.java'
with io.open(PATH, encoding='utf-8', newline='') as f:
    lines = f.read().split('\n')

R = [
    (4304, 4304, 'addButton.setText("＋ 新建计划");', 'addButton.setText(getString(R.string.plan_action_new));'),
    (4337, 4337, 'empty.setText("还没有计划，点击上方「＋ 新建计划」");', 'empty.setText(getString(R.string.plan_empty_hint));'),
    (4353, 4353, 'sectionHeader("待完成")', 'sectionHeader(getString(R.string.plan_section_pending))'),
    (4361, 4361, 'sectionHeader("已完成")', 'sectionHeader(getString(R.string.plan_section_done))'),
    (4422, 4422, 'plan.done ? "标记为未完成" : "标记为完成");',
     'plan.done ? getString(R.string.plan_cd_mark_undone) : getString(R.string.plan_cd_mark_done));'),
    (4431, 4431, 'title.setText(plan.title.length() == 0 ? "未命名计划" : plan.title);',
     'title.setText(plan.title.length() == 0 ? getString(R.string.plan_unnamed) : plan.title);'),
    (4443, 4443, 'StringBuilder meta = new StringBuilder("第").append(plan.week).append("周 · ")',
     'StringBuilder meta = new StringBuilder(getString(R.string.plan_meta_week, plan.week))'),
    (4449, 4449, 'meta.append(" · 提醒 ").append(remindTimeText(plan.remindMinute));',
     'meta.append(getString(R.string.plan_meta_remind, remindTimeText(plan.remindMinute)));'),
    (4470, 4470, 'edit.setContentDescription("编辑计划");', 'edit.setContentDescription(getString(R.string.plan_edit));'),
    (4522, 4522, 'dialogPanel(existing == null ? "新建计划" : "编辑计划");',
     'dialogPanel(existing == null ? getString(R.string.plan_editor_new) : getString(R.string.plan_edit));'),
    (4533, 4533, 'input("计划内容，如：预习高数第3章", titleValue[0])', 'input(getString(R.string.plan_editor_title_hint), titleValue[0])'),
    (4538, 4538, 'settingValueRow("关联课程", currentCourseLabel, v ->', 'settingValueRow(getString(R.string.plan_row_related_course), currentCourseLabel, v ->'),
    (4539, 4539, 'showChoiceDialog(v, "关联课程", choices, currentCourseLabel, value -> {',
     'showChoiceDialog(v, getString(R.string.plan_row_related_course), choices, currentCourseLabel, value -> {'),
    (4550, 4550, 'weekLabel.setText("周次");', 'weekLabel.setText(getString(R.string.plan_row_week));'),
    (4557, 4557, 'weekInput.setContentDescription("计划周次");', 'weekInput.setContentDescription(getString(R.string.plan_cd_week));'),
    (4583, 4583, 'final String[] weekdays = {"一", "二", "三", "四", "五", "六", "日"};', ''),
    (4592, 4592, 'chip.setText("周" + weekdays[i]);', 'chip.setText(WeekdayLabels.label(this, i));'),
    (4630, 4630, 'remindLabel.setText("到期提醒");', 'remindLabel.setText(getString(R.string.plan_row_reminder));'),
    (4646, 4646, 'settingValueRow("提醒时间",', 'settingValueRow(getString(R.string.plan_row_remind_time),'),
    (4648, 4648, 'showTimeDialog("提醒时间",', 'showTimeDialog(getString(R.string.plan_row_remind_time),'),
    (4655, 4655, 'dialogAction("保存", v -> {', 'dialogAction(getString(R.string.editor_action_save), v -> {'),
    (4658, 4658, '"请填写计划内容"', 'getString(R.string.plan_error_title_required)'),
    (4666, 4666, 'dialogAction("删除计划", v -> {', 'dialogAction(getString(R.string.plan_action_delete), v -> {'),
    (4671, 4671, 'dialogAction("取消", v -> dialog.dismiss()));', 'dialogAction(getString(R.string.editor_action_cancel), v -> dialog.dismiss()));'),
    (4699, 4699, '"未获得通知权限，计划提醒不会生效，请在系统设置中允许通知"', 'getString(R.string.plan_toast_no_permission)'),
    (4703, 4703, '"提醒时间已过，不会触发通知，请调整计划周次或提醒时间"', 'getString(R.string.plan_toast_remind_time_passed)'),
]

errors = []
applied = 0
for start, end, old, new in R:
    hits = [i for i in range(start - 1, min(end, len(lines))) if old in lines[i]]
    if len(hits) != 1:
        errors.append('预期1次,实际%d次: 行%d: %s' % (len(hits), start, old[:50]))
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
