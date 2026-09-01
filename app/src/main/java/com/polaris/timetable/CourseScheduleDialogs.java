package com.polaris.timetable;

import android.app.Dialog;
import android.content.UriPermission;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.polaris.timetable.importer.ScheduleImportPreviewData;
import com.polaris.timetable.parser.SchoolParserModel;
import com.polaris.timetable.storage.ScheduleBackupManager;
import com.polaris.timetable.storage.ScheduleRepository;
import com.polaris.timetable.ui.DesignTokens;

import java.util.Calendar;
import java.util.List;

/**
 * 课程与导入组对话框（阶段 2-1 抽取）。
 * 学校模型/自定义学校、课时粘贴、备份恢复、周导出、分享导入、
 * 批量改色/改教师/删除、课表切换/重命名/新建/删除、学期名、首周设置、解析诊断。
 * 动作由宿主 MainActivity 执行。
 */
public class CourseScheduleDialogs extends DialogKit {

    public CourseScheduleDialogs(MainActivity host) {
        super(host);
    }

    public void showParserModelDialog() {
        showParserModelDialog(null);
    }

    public void showParserModelDialog(Runnable onSelected) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.import_school_chooser_title));
        TextView message = new TextView(host);
        message.setText(host.getString(R.string.school_picker_message));
        message.setTextColor(host.mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(host.dp(4), 1f);
        panel.addView(message);
        for (SchoolParserModel model : SchoolParserModel.values()) {
            panel.addView(dialogAction(model.label, v -> {
                host.selectedParserModel = model;
                host.schoolName = model.label;
                host.applySchoolTimeDefaults(model);
                host.saveConfig();
                host.renderSchedule();
                dialog.dismiss();
                host.refreshActiveSettingsPage();
                host.refreshMyPage();
                if (onSelected != null) {
                    onSelected.run();
                }
            }));
        }
        panel.addView(dialogAction(host.getString(R.string.school_custom_action), v -> {
            dialog.dismiss();
            showCustomSchoolDialog(onSelected);
        }));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showCustomSchoolDialog(Runnable onSelected) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.school_custom_title));
        EditText schoolInput = host.input(host.getString(R.string.school_custom_hint_input), "");
        schoolInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(48)});
        panel.addView(schoolInput);
        TextView hint = new TextView(host);
        hint.setText(host.getString(R.string.school_custom_hint));
        hint.setTextColor(host.mutedColor());
        hint.setTextSize(13);
        hint.setLineSpacing(host.dp(3), 1f);
        panel.addView(hint);
        panel.addView(host.pageSaveButton(() -> {
            String value = schoolInput.getText().toString().trim();
            if (value.length() == 0) {
                Toast.makeText(host, host.getString(R.string.school_error_empty), Toast.LENGTH_SHORT).show();
                return;
            }
            host.selectedParserModel = null;
            host.schoolName = value;
            host.saveConfig();
            host.renderSchedule();
            dialog.dismiss();
            host.refreshActiveSettingsPage();
            host.refreshMyPage();
            host.showClassTimeTableEditor(onSelected);
        }));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showClassTimePasteDialog(final List<int[]> rows,
                                         final LinearLayout rowsContainer,
                                         final Dialog owner, final ScrollView scroll) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.classtime_paste_title));
        TextView hint = new TextView(host);
        hint.setText(host.getString(R.string.classtime_paste_hint));
        hint.setTextColor(host.mutedColor());
        hint.setTextSize(13);
        hint.setLineSpacing(host.dp(3), 1f);
        panel.addView(hint);

        EditText textInput = new EditText(host);
        textInput.setHint(host.getString(R.string.classtime_paste_input_hint));
        textInput.setHintTextColor(host.mutedColor());
        textInput.setTextColor(host.inkColor());
        textInput.setTextSize(15);
        textInput.setGravity(Gravity.TOP | Gravity.START);
        textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        textInput.setBackground(host.roundedBg(host.cardColorHex(), DesignTokens.RADIUS_CHIP));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, host.dp(DesignTokens.TABLET_PRACTICE_MIN_WIDTH));
        inputParams.topMargin = host.dp(8);
        panel.addView(textInput, inputParams);

        TextView apply = dialogAction(host.getString(R.string.classtime_parse_apply), v -> {
            List<int[]> parsed = host.parseClassTimeText(textInput.getText().toString());
            if (parsed.isEmpty()) {
                Toast.makeText(host,
                        host.getString(R.string.classtime_paste_no_rows), Toast.LENGTH_LONG).show();
                return;
            }
            rows.clear();
            rows.addAll(parsed);
            host.renderClassTimeRows(rowsContainer, rows, owner, scroll, true);
            dialog.dismiss();
            Toast.makeText(host, host.getString(R.string.classtime_paste_done, parsed.size()),
                    Toast.LENGTH_SHORT).show();
        });
        apply.setTextColor(Color.WHITE);
        apply.setBackground(host.roundedBg(host.primaryActionFillHex(), DesignTokens.RADIUS_CARD));
        panel.addView(apply);
        panel.addView(dialogAction(host.getString(R.string.editor_action_cancel), v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showBackupRestoreConfirmDialog(ScheduleBackupManager.BackupBundle bundle) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.settings_row_restore_backup));
        ScheduleBackupManager.BackupSummary summary =
                ScheduleBackupManager.summaryOf(bundle);
        TextView message = new TextView(host);
        String sourceVersion = summary.appVersion.length() == 0
                ? host.getString(R.string.backup_version_unknown) : "v" + summary.appVersion;
        message.setText(host.getString(R.string.backup_confirm_message, summary.createdAt,
                sourceVersion,
                summary.scheduleCount,
                summary.courseCount));

        message.setTextColor(host.mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(host.dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 0, 0, host.dp(8));
        panel.addView(message, messageParams);
        TextView confirm = dialogAction(host.getString(R.string.backup_confirm_action), v -> {
            dialog.dismiss();
            host.applyBackupRestore(bundle);
        });
        confirm.setTextColor(Color.WHITE);
        confirm.setBackground(host.roundedBg(host.primaryActionFillHex(), DesignTokens.RADIUS_CARD));
        panel.addView(confirm);
        panel.addView(dialogAction(host.getString(R.string.editor_action_cancel), v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showWeekExportDialog() {
        if (host.scheduleExportInProgress) {
            Toast.makeText(host, host.getString(R.string.export_busy), Toast.LENGTH_SHORT).show();
            return;
        }
        int maxWeek = Math.max(1, host.semesterWeeks);
        final int[] selectedWeek = {
                Math.max(1, Math.min(maxWeek, host.currentWeek))
        };
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.export_dialog_title));

        TextView message = new TextView(host);
        message.setText(host.getString(R.string.export_dialog_message));
        message.setTextColor(host.mutedColor());
        message.setTextSize(14);
        message.setPadding(0, 0, 0, host.dp(8));
        panel.addView(message);

        EditText weekInput = host.stepInput(String.valueOf(selectedWeek[0]));
        weekInput.setContentDescription(host.getString(R.string.export_cd_week));
        LinearLayout weekRow = new LinearLayout(host);
        weekRow.setOrientation(LinearLayout.HORIZONTAL);
        weekRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = host.stepButton("−");
        TextView plus = host.stepButton("+");
        minus.setContentDescription(host.getString(R.string.export_cd_prev_week));
        plus.setContentDescription(host.getString(R.string.export_cd_next_week));
        minus.setOnClickListener(v -> {
            selectedWeek[0] = Math.max(1, host.parseBounded(
                    weekInput.getText().toString(), 1, maxWeek, selectedWeek[0]) - 1);
            weekInput.setText(String.valueOf(selectedWeek[0]));
            weekInput.setSelection(weekInput.getText().length());
        });
        plus.setOnClickListener(v -> {
            selectedWeek[0] = Math.min(maxWeek, host.parseBounded(
                    weekInput.getText().toString(), 1, maxWeek, selectedWeek[0]) + 1);
            weekInput.setText(String.valueOf(selectedWeek[0]));
            weekInput.setSelection(weekInput.getText().length());
        });
        weekRow.addView(minus);
        weekRow.addView(weekInput, new LinearLayout.LayoutParams(0, host.dp(46), 1f));
        weekRow.addView(plus);
        panel.addView(weekRow);

        panel.addView(dialogAction(host.getString(R.string.export_action_week_image), v -> {
            int week = host.parseBounded(weekInput.getText().toString(),
                    1, maxWeek, selectedWeek[0]);
            dialog.dismiss();
            host.exportWeekImage(week);
        }));
        panel.addView(dialogAction(host.getString(R.string.export_action_week_pdf), v -> {
            int week = host.parseBounded(weekInput.getText().toString(),
                    1, maxWeek, selectedWeek[0]);
            dialog.dismiss();
            host.exportWeekPdf(week);
        }));
        panel.addView(dialogAction(host.getString(R.string.export_action_semester_pdf), v -> {
            dialog.dismiss();
            host.exportSemesterPdf();
        }));
        panel.addView(dialogAction(host.getString(R.string.export_action_ics), v -> {
            dialog.dismiss();
            host.exportICal();
        }));
        panel.addView(dialogAction(host.getString(R.string.export_action_csv), v -> {
            dialog.dismiss();
            host.exportCsv();
        }));
        panel.addView(dialogAction(host.getString(R.string.editor_action_cancel), v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showSharedLinkInputDialog() {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.shared_link_title));
        TextView message = new TextView(host);
        message.setText(host.getString(R.string.shared_link_message));
        message.setTextColor(host.mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(host.dp(4), 1f);
        panel.addView(message);
        EditText linkInput = host.input("polaris://schedule/import?payload=...", "");
        panel.addView(linkInput);
        panel.addView(host.pageSaveButton(() -> {
            String link = host.extractSharedScheduleLink(linkInput.getText().toString());
            if (link.length() == 0) {
                Toast.makeText(host, host.getString(R.string.shared_link_empty), Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = Uri.parse(link);
            if (!host.isImportLink(uri)) {
                Toast.makeText(host, host.getString(R.string.shared_link_invalid), Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            host.startSharedScheduleImportFlow(uri);
        }));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showSharedImportOverwriteDialog(List<Course> sharedCourses) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.shared_import_title));
        TextView message = new TextView(host);
        message.setText(host.getString(R.string.shared_import_message, sharedCourses.size()));
        message.setTextColor(host.mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(host.dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 0, 0, host.dp(8));
        panel.addView(message, messageParams);
        panel.addView(dialogAction(host.getString(R.string.import_confirm_short), v -> {
            dialog.dismiss();
            showSharedImportNameDialog(sharedCourses);
        }));
        panel.addView(dialogAction(host.getString(R.string.editor_action_cancel), v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showSharedImportNameDialog(List<Course> sharedCourses) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.import_name_title));
        EditText nameInput = host.input(host.getString(R.string.settings_row_schedule_name),
                host.scheduleName.length() == 0 ? "分享课表" : host.scheduleName);
        panel.addView(nameInput);
        panel.addView(host.pageSaveButton(() -> {
            String name = nameInput.getText().toString().trim();
            if (name.length() == 0) {
                Toast.makeText(host, host.getString(R.string.import_error_name_empty), Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            host.applySharedCourses(sharedCourses, name);
        }));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showBatchColorDialog() {
        if (host.courseManageSelectedIds.isEmpty()) {
            Toast.makeText(host, host.getString(R.string.manage_error_no_selection), Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.manage_batch_color_title));
        TextView message = new TextView(host);
        message.setText(host.getString(R.string.manage_batch_color_message, host.courseManageSelectedIds.size()));

        message.setTextColor(host.mutedColor());
        message.setTextSize(14);
        message.setPadding(0, 0, 0, host.dp(8));
        panel.addView(message);
        for (int index = 0; index < host.BATCH_COLOR_VALUES.length; index += 3) {
            LinearLayout line = new LinearLayout(host);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int offset = 0; offset < 3 && index + offset < host.BATCH_COLOR_VALUES.length; offset++) {
                final String color = host.BATCH_COLOR_VALUES[index + offset];
                TextView swatch = new TextView(host);
                swatch.setBackground(host.roundedBg(color, DesignTokens.RADIUS_CARD));
                swatch.setContentDescription(host.getString(R.string.manage_swatch_cd));
                swatch.setOnClickListener(v -> {
                    dialog.dismiss();
                    host.applyBatchColor(color);
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, host.dp(52), 1f);
                params.setMargins(host.dp(3), host.dp(3), host.dp(3), host.dp(3));
                line.addView(swatch, params);
            }
            panel.addView(line);
        }
        panel.addView(dialogAction(host.getString(R.string.editor_action_cancel), v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showBatchTeacherDialog() {
        if (host.courseManageSelectedIds.isEmpty()) {
            Toast.makeText(host, host.getString(R.string.manage_error_no_selection), Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.manage_batch_teacher_title));
        TextView message = new TextView(host);
        message.setText(host.getString(R.string.manage_batch_teacher_message, host.courseManageSelectedIds.size()));

        message.setTextColor(host.mutedColor());
        message.setTextSize(14);
        message.setLineSpacing(host.dp(3), 1f);
        message.setPadding(0, 0, 0, host.dp(4));
        panel.addView(message);
        EditText teacherInput = host.input(host.getString(R.string.manage_teacher_hint), "");
        panel.addView(teacherInput);
        panel.addView(host.pageSaveButton(() -> {
            String teacher = teacherInput.getText() == null
                    ? "" : teacherInput.getText().toString().trim();
            if (teacher.length() == 0) {
                Toast.makeText(host, host.getString(R.string.manage_error_teacher_empty), Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            host.applyBatchTeacher(teacher);
        }));
        panel.addView(dialogAction(host.getString(R.string.editor_action_cancel), v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showBatchDeleteDialog() {
        if (host.courseManageSelectedIds.isEmpty()) {
            Toast.makeText(host, host.getString(R.string.manage_error_no_selection), Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.manage_batch_delete_title));
        TextView message = new TextView(host);
        message.setText(host.getString(R.string.manage_batch_delete_message, host.courseManageSelectedIds.size()));

        message.setTextColor(host.mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(host.dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 0, 0, host.dp(8));
        panel.addView(message, messageParams);
        panel.addView(dialogAction(host.getString(R.string.manage_batch_delete_action), v -> {
            dialog.dismiss();
            host.applyBatchDelete();
        }));
        panel.addView(dialogAction(host.getString(R.string.editor_action_cancel), v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showScheduleSwitchDialog() {
        List<ScheduleRepository.ScheduleEntry> schedules = host.scheduleRepository.loadSchedules();
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.schedule_switch_title));
        for (ScheduleRepository.ScheduleEntry entry : schedules) {
            TextView item = new TextView(host);
            item.setText(entry.id.equals(host.activeScheduleId) ? entry.name
                + host.getString(R.string.schedule_switch_current_suffix) : entry.name);
            item.setGravity(Gravity.CENTER);
            item.setTextSize(17);
            boolean active = entry.id.equals(host.activeScheduleId);
            item.setTextColor(active ? host.selectedTextColor() : host.inkColor());
            item.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            item.setBackground(host.roundedBg(active ? host.selectedFillHex() : host.cardColorHex(), DesignTokens.RADIUS_CARD));
            item.setOnClickListener(v -> {
                dialog.dismiss();
                if (active) {
                    showRenameScheduleDialog();
                } else {
                    host.switchSchedule(entry.id);
                    host.showGlobalSettings();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, host.dp(46));
            params.setMargins(0, host.dp(5), 0, host.dp(5));
            panel.addView(item, params);
        }
        panel.addView(dialogAction(host.getString(R.string.schedule_action_add), v -> {
            dialog.dismiss();
            showCreateScheduleDialog();
        }));
        panel.addView(dialogAction(host.getString(R.string.schedule_action_delete), v -> {
            dialog.dismiss();
            showDeleteCurrentScheduleDialog();
        }));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showRenameScheduleDialog() {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.schedule_rename_title));
        EditText nameInput = host.input(host.getString(R.string.settings_row_schedule_name), host.scheduleName);
        panel.addView(nameInput);
        panel.addView(host.pageSaveButton(() -> {
            String nextName = nameInput.getText().toString().trim();
            if (nextName.length() == 0) {
                Toast.makeText(host, host.getString(R.string.import_error_name_empty), Toast.LENGTH_SHORT).show();
                return;
            }
            host.scheduleName = nextName;
            host.saveConfig();
            host.updateHeader();
            host.refreshMyPage();
            dialog.dismiss();
            host.showGlobalSettings();
        }));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showCreateScheduleDialog() {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.schedule_action_add));
        EditText nameInput = host.input(host.getString(R.string.schedule_create_name_hint), "");
        panel.addView(nameInput);
        panel.addView(host.pageSaveButton(() -> {
            String name = nameInput.getText().toString().trim();
            ScheduleRepository.ScheduleEntry entry = host.scheduleRepository.createSchedule(
                    name.length() == 0 ? "新课表" : name);
            host.copyGlobalAppearanceToSchedule(entry.id);
            host.switchSchedule(entry.id);
            dialog.dismiss();
            host.showGlobalSettings();
        }));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showDeleteCurrentScheduleDialog() {
        List<ScheduleRepository.ScheduleEntry> schedules = host.scheduleRepository.loadSchedules();
        if (schedules.size() <= 1) {
            Toast.makeText(host, host.getString(R.string.schedule_error_last_one), Toast.LENGTH_SHORT).show();
            showScheduleSwitchDialog();
            return;
        }
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.schedule_delete_confirm_title));
        TextView message = new TextView(host);
        message.setText(host.getString(R.string.schedule_delete_confirm_message, host.scheduleName));
        message.setTextColor(host.mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(host.dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 0, 0, host.dp(8));
        panel.addView(message, messageParams);
        panel.addView(dialogAction(host.getString(R.string.schedule_confirm_delete), v -> {
            dialog.dismiss();
            host.deleteCurrentSchedule();
            host.showGlobalSettings();
        }));
        panel.addView(dialogAction(host.getString(R.string.editor_action_cancel), v -> {
            dialog.dismiss();
            showScheduleSwitchDialog();
        }));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showSemesterNameDialog() {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.semester_edit_title));
        EditText semesterInput = host.input(host.getString(R.string.semester_edit_hint), host.semesterName);
        semesterInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(48)});
        panel.addView(semesterInput);
        panel.addView(host.pageSaveButton(() -> {
            String value = semesterInput.getText().toString().trim();
            if (value.length() == 0) {
                Toast.makeText(host, host.getString(R.string.semester_error_empty), Toast.LENGTH_SHORT).show();
                return;
            }
            host.semesterName = value;
            host.saveConfig();
            host.refreshMyPage();
            dialog.dismiss();
            host.refreshActiveSettingsPage();
        }));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showImportedFirstWeekDayDialog() {
        String defaultDate = SemesterStartDateDefaults.resolve(Calendar.getInstance());
        host.firstWeekDay = defaultDate;
        host.currentWeek = host.currentWeekFromDate();
        host.saveConfig();
        host.updateHeader();
        host.renderSchedule();

        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.first_week_day_title));

        TextView message = new TextView(host);
        message.setText(host.getString(R.string.first_week_day_message, defaultDate));
        message.setTextColor(host.mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(host.dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 0, 0, host.dp(8));
        panel.addView(message, messageParams);

        Calendar date = host.calendarFromText(defaultDate);
        DatePicker picker = new DatePicker(themedControlContext());
        picker.init(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH), null);
        panel.addView(picker);

        panel.addView(dialogAction(host.getString(R.string.first_week_day_use), v -> {
            host.firstWeekDay = picker.getYear() + "/" + (picker.getMonth() + 1) + "/" + picker.getDayOfMonth();
            host.currentWeek = host.currentWeekFromDate();
            host.saveConfig();
            host.updateHeader();
            host.renderSchedule();
            dialog.dismiss();
        }));
        panel.addView(dialogAction(host.getString(R.string.first_week_day_skip), v -> dialog.dismiss()));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> host.scheduleWeekSwipeHintIfNeeded());
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showParseDiagnosticsDialog() {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.diagnostics_title));
        if (host.lastParseDiagnosticsText.length() == 0) {
            TextView empty = new TextView(host);
            empty.setText(host.getString(R.string.diagnostics_empty));
            empty.setTextColor(host.mutedColor());
            empty.setTextSize(15);
            empty.setLineSpacing(host.dp(4), 1f);
            empty.setPadding(0, host.dp(4), 0, host.dp(8));
            panel.addView(empty);
        } else {
            TextView report = new TextView(host);
            report.setText(host.lastParseDiagnosticsText);
            report.setTextColor(host.inkColor());
            report.setTextSize(13);
            report.setTypeface(Typeface.MONOSPACE);
            report.setTextIsSelectable(true);
            report.setLineSpacing(host.dp(3), 1f);
            report.setPadding(host.dp(12), host.dp(10), host.dp(12), host.dp(10));
            report.setBackground(host.roundedBg(host.cardColorHex(), DesignTokens.RADIUS_CHIP));

            ScrollView reportScroll = new ScrollView(host);
            reportScroll.setFillViewport(false);
            reportScroll.setVerticalScrollBarEnabled(true);
            reportScroll.addView(report, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT));
            int lineCount = Math.max(1, host.lastParseDiagnosticsText.split("\\n", -1).length);
            int visibleLines = Math.min(16, lineCount);
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.min(host.dp(360), Math.max(host.dp(DesignTokens.TABLET_PRACTICE_MIN_WIDTH), visibleLines * host.dp(22))));
            panel.addView(reportScroll, scrollParams);
            panel.addView(dialogAction(host.getString(R.string.diagnostics_copy_action), v -> host.copyParseDiagnostics()));
        }
        panel.addView(dialogAction(host.getString(R.string.import_close), v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }
}
