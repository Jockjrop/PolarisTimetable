package com.polaris.timetable.importer;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.polaris.timetable.Course;
import com.polaris.timetable.DialogKit;
import com.polaris.timetable.MainActivity;
import com.polaris.timetable.R;
import com.polaris.timetable.model.ParseResult;
import com.polaris.timetable.parser.SchoolParserModel;
import com.polaris.timetable.ui.DesignTokens;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * PDF 导入对话框决策链：覆盖确认 → 命名 → 解析审阅。
 * 数据与算法层复用 {@link PdfImportCoordinator} / {@link ImportReviewSummary}，
 * 解析执行（{@link Host#loadPdf}）与落库副作用（{@link Host#applyReviewedImport}）
 * 经宿主回调交还 Activity。阶段 2-5 从 MainActivity 抽出，视觉零变化。
 */
public class PdfImportReviewFlow extends DialogKit {

    /** 宿主回调:由 MainActivity 实现,提供课表状态、解析入口与落库提交。 */
    public interface Host {
        boolean hasCourses();

        String activeScheduleId();

        String currentScheduleName();

        SchoolParserModel selectedParserModel();

        List<Course> existingCourses();

        String nextScheduleName();

        void loadPdf(Uri uri, ImportDestination destination);

        int inferSemesterWeeks(List<Course> courses);

        boolean hasDiagnosticsText();

        void showParseDiagnosticsDialog();

        void applyReviewedImport(ParseResult result,
                                  ImportDestination destination,
                                  int importedSemesterWeeks);

        View importReviewHeading(String text);

        View importReviewLine(String label, String value, boolean needsAttention);
    }

    /** PDF 导入目标描述：课表 id/名称、解析模板、是否新建、既有课程基线。 */
    public static final class ImportDestination {
        public final String scheduleId;
        public final String scheduleName;
        public final SchoolParserModel parserModel;
        public final boolean createNewSchedule;
        public final List<Course> existingCourses;

        public ImportDestination(String scheduleId, String scheduleName,
                                 SchoolParserModel parserModel, boolean createNewSchedule,
                                 List<Course> existingCourses) {
            this.scheduleId = scheduleId == null ? "" : scheduleId;
            this.scheduleName = scheduleName == null ? "" : scheduleName;
            this.parserModel = parserModel;
            this.createNewSchedule = createNewSchedule;
            this.existingCourses = Collections.unmodifiableList(new ArrayList<>(
                    existingCourses == null
                            ? Collections.<Course>emptyList() : existingCourses));
        }
    }

    private final Host flowHost;

    public PdfImportReviewFlow(MainActivity host, Host flowHost) {
        super(host);
        this.flowHost = flowHost;
    }

    /** 覆盖确认：解析并覆盖当前课表 / 新建课表 / 取消。 */
    public void showImportOverwriteDialog(Uri uri) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.import_overwrite_title));
        final SchoolParserModel[] importParserModel = {flowHost.selectedParserModel()};
        TextView message = new TextView(host);
        message.setText(host.getString(R.string.import_overwrite_message));
        message.setTextColor(host.mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(host.dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 0, 0, host.dp(8));
        panel.addView(message, messageParams);
        final TextView[] parserChoice = new TextView[1];
        parserChoice[0] = dialogAction(importParserModel[0] == null
                ? host.getString(R.string.import_pick_model)
                : host.getString(R.string.import_school_value, importParserModel[0].label), v -> {
            Dialog chooser = new Dialog(host);
            LinearLayout chooserPanel = dialogPanel(host.getString(R.string.import_school_chooser_title));
            for (SchoolParserModel model : SchoolParserModel.values()) {
                chooserPanel.addView(dialogAction(model.label, item -> {
                    importParserModel[0] = model;
                    parserChoice[0].setText(host.getString(R.string.import_school_value, model.label));
                    chooser.dismiss();
                }));
            }
            chooser.setContentView(glassDialogContent(chooserPanel, DesignTokens.RADIUS_DIALOG_SHEET));
            chooser.show();
            transparentDialog(chooser);
        });
        panel.addView(parserChoice[0]);
        TextView cover = dialogAction(host.getString(R.string.import_parse_and_check), v -> {
            if (importParserModel[0] == null) {
                Toast.makeText(host, host.getString(R.string.import_error_pick_model),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            flowHost.loadPdf(uri, new ImportDestination(flowHost.activeScheduleId(),
                    flowHost.currentScheduleName(), importParserModel[0], false,
                    flowHost.existingCourses()));
        });
        TextView create = dialogAction(host.getString(R.string.import_create_and_check), v -> {
            if (importParserModel[0] == null) {
                Toast.makeText(host, host.getString(R.string.import_error_create_pick_model),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            showImportNameDialog(uri, true, importParserModel[0]);
        });
        TextView cancel = dialogAction(host.getString(R.string.editor_action_cancel),
                v -> dialog.dismiss());
        panel.addView(cover);
        panel.addView(create);
        panel.addView(cancel);
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    /** 新建课表命名：输入名称后启动解析。 */
    public void showImportNameDialog(Uri uri, boolean createNewSchedule,
                                     SchoolParserModel parserModel) {
        if (parserModel == null) {
            Toast.makeText(host, host.getString(R.string.import_error_create_pick_model),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.import_name_title));
        EditText nameInput = input(host.getString(R.string.settings_row_schedule_name),
                createNewSchedule ? flowHost.nextScheduleName()
                        : flowHost.currentScheduleName().length() == 0
                        ? flowHost.nextScheduleName() : flowHost.currentScheduleName());
        panel.addView(nameInput);
        TextView startImport = dialogAction(host.getString(R.string.import_start_parse), v -> {
            String name = nameInput.getText().toString().trim();
            if (name.length() == 0) {
                Toast.makeText(host, host.getString(R.string.import_error_name_empty),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            flowHost.loadPdf(uri, new ImportDestination(flowHost.activeScheduleId(), name,
                    parserModel, createNewSchedule,
                    createNewSchedule ? Collections.<Course>emptyList() : flowHost.existingCourses()));
        });
        startImport.setTextColor(Color.WHITE);
        startImport.setBackground(host.roundedBg(host.primaryActionFillHex(), DesignTokens.RADIUS_CARD));
        panel.addView(startImport);
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    private EditText input(String hint, String value) {
        EditText editText = new EditText(host);
        editText.setHint(hint);
        editText.setText(value);
        editText.setTextColor(host.inkColor());
        editText.setHintTextColor(host.mutedColor());
        editText.setBackground(host.roundedBg(host.cardColorHex(), DesignTokens.RADIUS_CHIP));
        editText.setTextSize(15);
        editText.setSingleLine(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, host.dp(50));
        params.topMargin = host.dp(8);
        editText.setLayoutParams(params);
        return editText;
    }

    /** 解析审阅：概要、增减对比、待核查项、诊断入口与确认/阻断。 */
    public void showImportReviewDialog(ParseResult result, ImportDestination destination) {
        int importedSemesterWeeks = flowHost.inferSemesterWeeks(result.courses);
        List<Course> comparisonCourses = destination.existingCourses;
        boolean replacingExisting = !destination.createNewSchedule
                && !comparisonCourses.isEmpty();
        ImportReviewSummary review = ImportReviewSummary.analyze(
                result, comparisonCourses, importedSemesterWeeks);

        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.import_review_title));
        TextView summary = new TextView(host);
        summary.setText(review.canImport()
                ? host.getString(R.string.import_review_count, review.courseCount,
                        review.meetingCount)
                : host.getString(R.string.import_review_empty));
        summary.setTextColor(host.inkColor());
        summary.setTextSize(18);
        summary.setTypeface(Typeface.DEFAULT_BOLD);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(0, 0, 0, host.dp(10));
        panel.addView(summary);

        String importedSemester = result.semesterName.length() > 0
                ? result.semesterName
                : com.polaris.timetable.SemesterStartDateDefaults.resolveSemesterName(
                        Calendar.getInstance());
        panel.addView(flowHost.importReviewLine(
                host.getString(R.string.import_review_label_school),
                destination.parserModel.label, false));
        panel.addView(flowHost.importReviewLine(
                host.getString(R.string.import_review_label_semester), importedSemester, false));
        panel.addView(flowHost.importReviewLine(
                host.getString(R.string.import_review_pages),
                host.getString(R.string.import_pages_value, Math.max(0, result.pageCount)), false));
        panel.addView(flowHost.importReviewLine(
                host.getString(R.string.import_review_start_date),
                host.getString(R.string.import_review_date_pending), false));

        panel.addView(flowHost.importReviewHeading(destination.createNewSchedule
                ? host.getString(R.string.import_review_destination_new)
                : replacingExisting
                ? host.getString(R.string.import_review_destination_compare)
                : host.getString(R.string.import_review_destination_current)));
        panel.addView(flowHost.importReviewLine(host.getString(R.string.import_review_added),
                host.getString(R.string.import_count_value, review.addedCount), false));
        panel.addView(flowHost.importReviewLine(host.getString(R.string.import_review_modified),
                host.getString(R.string.import_count_value, review.modifiedCount),
                review.modifiedCount > 0));
        panel.addView(flowHost.importReviewLine(host.getString(R.string.import_review_removed),
                host.getString(R.string.import_count_value, review.removedCount),
                review.removedCount > 0));

        panel.addView(flowHost.importReviewHeading(
                host.getString(R.string.import_review_needs_check)));
        if (!review.hasIssues()) {
            panel.addView(flowHost.importReviewLine(
                    host.getString(R.string.import_review_check_result),
                    host.getString(R.string.import_review_no_issues), false));
        } else {
            if (review.errorCount > 0) {
                panel.addView(flowHost.importReviewLine(
                        host.getString(R.string.import_review_parse_errors),
                        host.getString(R.string.import_count_value, review.errorCount), true));
            }
            if (review.warningCount > 0) {
                panel.addView(flowHost.importReviewLine(
                        host.getString(R.string.import_review_warnings),
                        host.getString(R.string.import_count_value, review.warningCount), true));
            }
            if (review.unknownWeekCount > 0) {
                panel.addView(flowHost.importReviewLine(
                        host.getString(R.string.import_review_unknown_weeks),
                        host.getString(R.string.import_arrangements_value,
                                review.unknownWeekCount), true));
            }
            if (review.missingLocationCount > 0) {
                panel.addView(flowHost.importReviewLine(
                        host.getString(R.string.import_review_missing_location),
                        host.getString(R.string.import_arrangements_value,
                                review.missingLocationCount), true));
            }
            if (review.missingTeacherCount > 0) {
                panel.addView(flowHost.importReviewLine(
                        host.getString(R.string.import_review_missing_teacher),
                        host.getString(R.string.import_arrangements_value,
                                review.missingTeacherCount), true));
            }
            if (review.conflictCount > 0) {
                panel.addView(flowHost.importReviewLine(
                        host.getString(R.string.import_review_conflicts),
                        host.getString(R.string.import_conflict_value,
                                review.conflictCount), true));
            }
        }

        if (flowHost.hasDiagnosticsText()) {
            panel.addView(dialogAction(host.getString(R.string.import_review_diagnostics),
                    v -> flowHost.showParseDiagnosticsDialog()));
        }
        if (review.canImport()) {
            String confirmText = destination.createNewSchedule
                    ? host.getString(R.string.import_confirm_new)
                    : replacingExisting
                    ? host.getString(R.string.import_confirm_overwrite)
                    : host.getString(R.string.import_confirm_import_pdf);
            TextView confirm = dialogAction(confirmText, v -> {
                dialog.dismiss();
                flowHost.applyReviewedImport(result, destination, importedSemesterWeeks);
            });
            confirm.setTextColor(Color.WHITE);
            confirm.setBackground(host.roundedBg(host.primaryActionFillHex(),
                    DesignTokens.RADIUS_CARD));
            confirm.setContentDescription(confirmText
                    + host.getString(R.string.import_confirm_cd_review));
            panel.addView(confirm);
        } else {
            TextView blocked = new TextView(host);
            blocked.setText(host.getString(R.string.import_blocked_message));
            blocked.setTextColor(host.mutedColor());
            blocked.setTextSize(14);
            blocked.setLineSpacing(host.dp(3), 1f);
            blocked.setPadding(0, host.dp(8), 0, host.dp(4));
            panel.addView(blocked);
        }
        panel.addView(dialogAction(review.canImport()
                        ? host.getString(R.string.import_cancel_import)
                        : host.getString(R.string.import_close),
                v -> dialog.dismiss()));

        ScrollView reviewScroll = new ScrollView(host);
        reviewScroll.setFillViewport(false);
        reviewScroll.setVerticalScrollBarEnabled(true);
        reviewScroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(glassDialogContent(reviewScroll, panel,
                DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }
}
