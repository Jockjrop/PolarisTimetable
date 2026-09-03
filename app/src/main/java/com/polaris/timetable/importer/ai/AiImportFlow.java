package com.polaris.timetable.importer.ai;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.polaris.timetable.DialogKit;
import com.polaris.timetable.MainActivity;
import com.polaris.timetable.R;
import com.polaris.timetable.importer.ScheduleImportConfirmation;
import com.polaris.timetable.importer.ScheduleImportPreviewData;
import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.ui.DesignTokens;
import com.polaris.timetable.ui.PolarisVisualTheme;

import java.util.Collections;
import java.util.List;

/**
 * AI 导入整条用户决策流：引导对话框（复制提示词 / 选图 / 粘贴文本）、
 * 剪贴板读写、跳转外部 AI 识别与回跳接管、结果预览确认、以及落库前的
 * 候选卡片渲染。数据与算法层复用 {@link AiScheduleImportWorkflow} 等既有类，
 * 落库副作用经 {@link Host#commitImport} 交还 Activity。
 * 阶段 2-4 从 MainActivity 抽出，视觉零变化。
 */
public class AiImportFlow extends DialogKit {

    private static final String TAG = "AiImportFlow";

    /** AI 识别选图的 onActivityResult 请求码（由 MainActivity 转发给本流）。 */
    public static final int REQUEST_PICK_AI_IMAGE = 1004;

    /** 宿主回调:由 MainActivity 实现,提供既有课表判断、落库提交与少量视图动作。 */
    public interface Host {
        boolean hasExistingCourses();

        void commitImport(List<StructuredCourse> importedCourses,
                          String diagnosticsSummary,
                          String diagnosticsText,
                          String successMessage);

        void launchImagePicker(Intent intent, int requestCode);

        void postOnRoot(Runnable runnable);

        View importReviewHeading(String text);

        View importReviewLine(String label, String value, boolean needsAttention);
    }

    private final Host flowHost;
    private final AiScheduleImportWorkflow aiScheduleImportWorkflow =
            new AiScheduleImportWorkflow();
    private final AiExternalImportReturnController aiExternalImportReturnController =
            new AiExternalImportReturnController();
    private EditText activeAiImportInput;
    private TextView activeAiImportErrorView;

    public AiImportFlow(MainActivity host, Host flowHost) {
        super(host);
        this.flowHost = flowHost;
    }

    // ===== 生命周期钩子（由 MainActivity 在 onResume / onPause 转发） =====

    public void onHostPaused() {
        aiExternalImportReturnController.onHostPaused();
    }

    public void onHostResumed() {
        if (!aiExternalImportReturnController.shouldCheckClipboardOnResume()) {
            return;
        }
        AiExternalImportReturnController.ReturnResult result =
                aiExternalImportReturnController.onHostResumed(readClipboardText());
        if (!result.returnedFromExternalApp) {
            return;
        }
        if (!result.hasNewClipboardText()) {
            Toast.makeText(host, host.getString(R.string.import_ai_return_none),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (activeAiImportInput == null || activeAiImportErrorView == null) {
            return;
        }
        activeAiImportInput.setText(result.newClipboardText);
        activeAiImportInput.setSelection(activeAiImportInput.length());
        activeAiImportErrorView.setVisibility(View.GONE);
        activeAiImportInput.announceForAccessibility(
                host.getString(R.string.import_ai_return_read_cd));
        Toast.makeText(host, host.getString(R.string.import_ai_return_read_cd),
                Toast.LENGTH_SHORT).show();
    }

    // ===== 引导对话框 =====

    public void showImportDialog() {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.import_ai_title));

        TextView instruction = new TextView(host);
        instruction.setText(host.getString(R.string.import_ai_instruction));
        instruction.setTextColor(host.mutedColor());
        instruction.setTextSize(14);
        instruction.setLineSpacing(host.dp(3), 1f);
        instruction.setPadding(0, 0, 0, host.dp(6));
        panel.addView(instruction);

        panel.addView(dialogAction(host.getString(R.string.import_ai_copy_prompt),
                v -> copyAiRecognitionPrompt()));
        panel.addView(dialogAction(host.getString(R.string.import_ai_pick_image),
                v -> openAiRecognitionImagePicker()));

        EditText input = new EditText(host);
        input.setHint(host.getString(R.string.import_ai_paste_hint));
        input.setTextColor(host.inkColor());
        input.setHintTextColor(host.mutedColor());
        input.setTextSize(15);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setSingleLine(false);
        input.setHorizontallyScrolling(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(host.dp(12), host.dp(10), host.dp(12), host.dp(10));
        input.setBackground(host.roundedBg(host.groupColorHex(), DesignTokens.RADIUS_CHIP));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200_000)});
        input.setContentDescription(host.getString(R.string.import_ai_input_cd));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, host.dp(190));
        inputParams.setMargins(0, host.dp(8), 0, host.dp(4));
        panel.addView(input, inputParams);

        TextView errorView = new TextView(host);
        errorView.setTextColor(PolarisVisualTheme.warningColor(
                host.isDarkModeActive()));
        errorView.setTextSize(14);
        errorView.setLineSpacing(host.dp(3), 1f);
        errorView.setPadding(host.dp(12), host.dp(10), host.dp(12), host.dp(10));
        errorView.setBackground(host.roundedBg(host.groupColorHex(), DesignTokens.RADIUS_CHIP));
        errorView.setTextIsSelectable(true);
        errorView.setVisibility(View.GONE);
        panel.addView(errorView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        activeAiImportInput = input;
        activeAiImportErrorView = errorView;

        panel.addView(dialogAction(host.getString(R.string.import_ai_paste),
                v -> pasteAiTextFromClipboard(input, errorView)));

        TextView parse = dialogAction(host.getString(R.string.import_ai_parse), v -> {
            String aiText = input.getText() == null ? "" : input.getText().toString();
            if (aiText.trim().isEmpty()) {
                showAiImportErrors(errorView,
                        Collections.singletonList(host.getString(R.string.import_ai_error_empty)));
                return;
            }
            v.setEnabled(false);
            AiScheduleImportWorkflow.PrepareResult prepared =
                    aiScheduleImportWorkflow.prepare(aiText);
            if (prepared instanceof AiScheduleImportWorkflow.PrepareFailure) {
                List<String> messages = AiImportIssueFormatter.formatAll(
                        ((AiScheduleImportWorkflow.PrepareFailure) prepared).errors);
                showAiImportErrors(errorView, messages);
                v.setEnabled(true);
                return;
            }
            dialog.dismiss();
            showAiImportPreview((AiScheduleImportWorkflow.PrepareSuccess) prepared);
        });
        parse.setTextColor(Color.WHITE);
        parse.setBackground(host.roundedBg(host.primaryActionFillHex(), DesignTokens.RADIUS_CARD));
        parse.setContentDescription(host.getString(R.string.import_ai_parse_cd));
        panel.addView(parse);
        panel.addView(dialogAction(host.getString(R.string.editor_action_cancel),
                v -> dialog.dismiss()));

        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> {
            if (activeAiImportInput == input) {
                activeAiImportInput = null;
                activeAiImportErrorView = null;
                aiExternalImportReturnController.cancel();
            }
        });
        dialog.setContentView(glassDialogContent(scroll, panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void copyAiRecognitionPrompt() {
        if (copyAiRecognitionPromptToClipboard()) {
            Toast.makeText(host, host.getString(R.string.import_ai_prompt_copied),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean copyAiRecognitionPromptToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) host.getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(host, host.getString(R.string.import_clipboard_unavailable),
                    Toast.LENGTH_LONG).show();
            return false;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                host.getString(R.string.import_clipboard_label), PolarisAiPromptV1.getPrompt()));
        return true;
    }

    // ===== 外部 AI 识别 =====

    private void openAiRecognitionImagePicker() {
        Intent pickImage = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pickImage.addCategory(Intent.CATEGORY_OPENABLE);
        pickImage.setType("image/*");
        try {
            flowHost.launchImagePicker(pickImage, REQUEST_PICK_AI_IMAGE);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(host, host.getString(R.string.import_no_image_picker),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** 相册选图返回：经宿主 post 到主线程后发起外部 AI 识别。 */
    public void onImagePicked(android.net.Uri scheduleImageUri) {
        flowHost.postOnRoot(() -> {
            if (activeAiImportInput != null) {
                launchExternalAiRecognition(scheduleImageUri);
            }
        });
    }

    private void launchExternalAiRecognition(android.net.Uri scheduleImageUri) {
        if (!copyAiRecognitionPromptToClipboard()) {
            return;
        }

        Intent sendImage = new Intent(Intent.ACTION_SEND);
        sendImage.setType("image/*");
        sendImage.putExtra(Intent.EXTRA_TEXT, PolarisAiPromptV1.getPrompt());
        sendImage.putExtra(Intent.EXTRA_STREAM, scheduleImageUri);
        sendImage.setClipData(ClipData.newRawUri(
                host.getString(R.string.import_ai_share_image_label), scheduleImageUri));
        sendImage.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        aiExternalImportReturnController.begin(readClipboardText());
        Toast.makeText(host, host.getString(R.string.import_ai_send_hint),
                Toast.LENGTH_LONG).show();
        try {
            host.startActivity(Intent.createChooser(
                    sendImage, host.getString(R.string.import_ai_chooser_title)));
        } catch (ActivityNotFoundException exception) {
            aiExternalImportReturnController.cancel();
            Toast.makeText(host, host.getString(R.string.import_no_ai_app),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ===== 剪贴板 =====

    private String readClipboardText() {
        ClipboardManager clipboard = (ClipboardManager) host.getSystemService(
                Context.CLIPBOARD_SERVICE);
        try {
            ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
            CharSequence text = clip == null || clip.getItemCount() == 0
                    ? null : clip.getItemAt(0).coerceToText(host);
            return text == null ? "" : text.toString();
        } catch (SecurityException exception) {
            Log.w(TAG, "Clipboard access was denied", exception);
            return "";
        }
    }

    void pasteAiTextFromClipboard(EditText input, TextView errorView) {
        String text = readClipboardText();
        if (text.trim().isEmpty()) {
            Toast.makeText(host, host.getString(R.string.import_clipboard_empty),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        input.setText(text);
        input.setSelection(input.length());
        errorView.setVisibility(View.GONE);
        input.announceForAccessibility(host.getString(R.string.import_ai_paste_done_cd));
    }

    private void showAiImportErrors(TextView errorView, List<String> messages) {
        StringBuilder text = new StringBuilder(host.getString(R.string.import_ai_errors_header));
        if (messages == null || messages.isEmpty()) {
            text.append("\n").append(host.getString(R.string.import_ai_parse_failed));
        } else {
            for (String message : messages) {
                text.append("\n\n").append(message);
            }
        }
        errorView.setText(text.toString());
        errorView.setContentDescription(host.getString(R.string.import_ai_errors_cd, text));
        errorView.setVisibility(View.VISIBLE);
        errorView.announceForAccessibility(text);
    }

    // ===== 结果预览与确认 =====

    private void showAiImportPreview(AiScheduleImportWorkflow.PrepareSuccess prepared) {
        ScheduleImportPreviewData preview = prepared.preview;
        if (preview == null || preview.isEmpty()) {
            Toast.makeText(host, host.getString(R.string.import_ai_no_courses),
                    Toast.LENGTH_LONG).show();
            return;
        }

        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.import_ai_review_title));
        addScheduleImportCandidatePreview(panel, preview);

        boolean replacingExisting = flowHost.hasExistingCourses();
        TextView replacementNotice = new TextView(host);
        replacementNotice.setText(replacingExisting
                ? host.getString(R.string.import_ai_note_overwrite)
                : host.getString(R.string.import_ai_note_import));
        replacementNotice.setTextColor(replacingExisting
                ? PolarisVisualTheme.warningColor(host.isDarkModeActive())
                : host.mutedColor());
        replacementNotice.setTextSize(14);
        replacementNotice.setLineSpacing(host.dp(3), 1f);
        replacementNotice.setPadding(0, host.dp(12), 0, host.dp(4));
        panel.addView(replacementNotice);

        ScheduleImportConfirmation confirmation = new ScheduleImportConfirmation();
        String confirmText = replacingExisting
                ? host.getString(R.string.import_confirm_overwrite)
                : host.getString(R.string.import_confirm_short);
        TextView confirm = dialogAction(confirmText, v -> {
            if (host.isFinishing() || host.isDestroyed()) {
                confirmation.cancel();
                dialog.dismiss();
                return;
            }
            v.setEnabled(false);
            confirmation.confirm(preview, candidates -> {
                dialog.dismiss();
                int courseCount = candidates.size();
                flowHost.commitImport(
                        candidates,
                        host.getString(R.string.import_ai_summary, courseCount,
                                preview.meetingCount()),
                        host.getString(R.string.import_ai_detail, courseCount,
                                preview.meetingCount()),
                        host.getString(R.string.import_ai_success_toast, courseCount));
            });
        });
        confirm.setTextColor(Color.WHITE);
        confirm.setBackground(host.roundedBg(host.primaryActionFillHex(), DesignTokens.RADIUS_CARD));
        confirm.setContentDescription(confirmText + host.getString(R.string.import_confirm_cd_overwrite));
        panel.addView(confirm);
        panel.addView(dialogAction(host.getString(R.string.editor_action_cancel), v -> {
            confirmation.cancel();
            dialog.dismiss();
        }));

        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(ignored -> confirmation.cancel());
        dialog.setOnDismissListener(ignored -> confirmation.cancel());
        dialog.setContentView(glassDialogContent(scroll, panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    private void addScheduleImportCandidatePreview(
            LinearLayout panel, ScheduleImportPreviewData preview) {
        panel.addView(flowHost.importReviewHeading(host.getString(R.string.import_candidates_heading)));
        panel.addView(flowHost.importReviewLine(host.getString(R.string.import_review_recognized),
                host.getString(R.string.import_candidates_count, preview.regularCourseCount(),
                        preview.practiceCourseCount()),
                false));
        if (preview.semester != null && !preview.semester.trim().isEmpty()) {
            panel.addView(flowHost.importReviewLine(
                    host.getString(R.string.import_review_semester), preview.semester, false));
        }

        for (StructuredCourse course : preview.courses) {
            if (course != null) {
                panel.addView(scheduleImportCourseCard(course, preview));
            }
        }

        if (!preview.warnings.isEmpty()) {
            panel.addView(flowHost.importReviewHeading(host.getString(R.string.import_review_needs_check)));
            for (String warning : preview.warnings) {
                panel.addView(flowHost.importReviewLine(
                        host.getString(R.string.import_review_hint), warning, true));
            }
        }
    }

    private View scheduleImportCourseCard(StructuredCourse course,
                                          ScheduleImportPreviewData preview) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(host.dp(14), host.dp(12), host.dp(14), host.dp(12));
        card.setBackground(host.roundedBg(host.groupColorHex(), DesignTokens.RADIUS_CARD));
        card.setContentDescription(host.getString(R.string.import_course_cd, course.name));

        TextView name = new TextView(host);
        name.setText(course.name);
        name.setTextColor(host.inkColor());
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(false);
        card.addView(name);

        if (!course.teacher.trim().isEmpty()) {
            card.addView(scheduleImportDetail(
                    host.getString(R.string.import_detail_teacher, course.teacher), false));
        }
        if (!course.credit.trim().isEmpty()) {
            card.addView(scheduleImportDetail(
                    host.getString(R.string.import_detail_credit, course.credit), false));
        }
        for (ScheduleImportPreviewData.Detail detail : preview.detailsFor(course)) {
            String note = detail.note.isEmpty() ? "" : "（" + detail.note + "）";
            card.addView(scheduleImportDetail(
                    detail.label + "：" + detail.value + note, false));
        }
        for (CourseMeeting meeting : course.meetings) {
            if (meeting == null) {
                continue;
            }
            card.addView(scheduleImportDetail(
                    scheduleImportMeetingText(course, meeting), true));
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, host.dp(6), 0, host.dp(6));
        card.setLayoutParams(params);
        return card;
    }

    private TextView scheduleImportDetail(String text, boolean meeting) {
        TextView detail = new TextView(host);
        detail.setText(text);
        detail.setTextColor(meeting ? host.inkColor() : host.mutedColor());
        detail.setTextSize(meeting ? 14 : 13);
        detail.setLineSpacing(host.dp(2), 1f);
        detail.setSingleLine(false);
        detail.setPadding(0, meeting ? host.dp(8) : host.dp(3), 0, 0);
        return detail;
    }

    private String scheduleImportMeetingText(StructuredCourse course,
                                             CourseMeeting meeting) {
        String weeks = meeting.weekRule == null
                ? host.getString(R.string.editor_week_unknown_full) : meeting.weekRule.displayText();
        if (course.courseType == CourseType.PRACTICE
                && meeting.timeMode == CourseTimeMode.NONE) {
            return weeks + "\n" + host.getString(R.string.import_no_fixed_time);
        }

        StringBuilder text = new StringBuilder();
        text.append(host.dayName(meeting.day)).append(' ');
        if (meeting.hasExactTime()) {
            text.append(host.twoDigits(meeting.startMinuteOfDay / 60)).append(':')
                    .append(host.twoDigits(meeting.startMinuteOfDay % 60)).append('-')
                    .append(host.twoDigits(meeting.endMinuteOfDay / 60)).append(':')
                    .append(host.twoDigits(meeting.endMinuteOfDay % 60));
        } else {
            text.append(meeting.startSection).append('-')
                    .append(meeting.endSection).append(host.getString(R.string.import_section_suffix));
        }
        text.append('\n').append(weeks);
        if (!meeting.location.trim().isEmpty()) {
            text.append('\n').append(meeting.location);
        }
        return text.toString();
    }
}
