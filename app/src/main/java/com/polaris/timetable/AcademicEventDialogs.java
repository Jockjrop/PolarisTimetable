package com.polaris.timetable;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.polaris.timetable.model.AcademicEvent;
import com.polaris.timetable.ui.DesignTokens;
import com.polaris.timetable.ui.PolarisVisualTheme;
import com.polaris.timetable.ui.WeekdayLabels;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 学业事件（考试 / DDL / 实践）时间线对话框与编辑器（P4，2026-09）。
 * 事件以绝对日期排序：未完成按日期升序、已完成按日期降序；同一日内按时刻排序。
 * 数据读写与动作由宿主 MainActivity 执行，本类只负责 UI 构造与回写回调。
 */
public class AcademicEventDialogs extends DialogKit {

    private static final int RADIUS = 16;
    /** 事件类型徽标色（组件专属色，明暗主题下均采用高对比度色相）。 */
    private static final String COLOR_EXAM = "#E4572E";
    private static final String COLOR_DEADLINE = "#8B5CF6";
    private static final String COLOR_PRACTICE = "#0E9F6E";

    public AcademicEventDialogs(MainActivity host) {
        super(host);
    }

    /** 时间线主对话框：标题 + 添加按钮 + 分组列表。 */
    public void showTimelineDialog() {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.academic_title));
        LinearLayout.LayoutParams panelWidth = new LinearLayout.LayoutParams(
                Math.min(host.dp(420), host.getResources().getDisplayMetrics().widthPixels - host.dp(48)),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        panel.setLayoutParams(panelWidth);

        TextView add = dialogAction(host.getString(R.string.academic_action_add), v -> {
            dialog.dismiss();
            showEditorDialog(null);
        });
        panel.addView(add);

        ScrollView scroll = new ScrollView(host);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, host.dp(4), 0, host.dp(4));
        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(host.dp(460), host.getResources().getDisplayMetrics().heightPixels - host.dp(360))));
        populateList(list, dialog);
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    private void populateList(LinearLayout list, Dialog dialog) {
        List<AcademicEvent> events = host.academicEventSnapshot();
        if (events.isEmpty()) {
            TextView empty = new TextView(host);
            empty.setText(host.getString(R.string.academic_empty_hint));
            empty.setTextColor(host.mutedColor());
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, host.dp(30), 0, host.dp(30));
            list.addView(empty);
            return;
        }
        List<AcademicEvent> pending = new ArrayList<>();
        List<AcademicEvent> finished = new ArrayList<>();
        for (AcademicEvent event : events) {
            (event.done ? finished : pending).add(event);
        }
        Comparator<AcademicEvent> asc = (a, b) -> {
            int byDate = Long.compare(a.dateMillis, b.dateMillis);
            if (byDate != 0) {
                return byDate;
            }
            return Integer.compare(a.minuteOfDay < 0 ? -1 : a.minuteOfDay,
                    b.minuteOfDay < 0 ? -1 : b.minuteOfDay);
        };
        Comparator<AcademicEvent> desc = (a, b) -> Long.compare(b.dateMillis, a.dateMillis);
        Collections.sort(pending, asc);
        Collections.sort(finished, desc);

        if (!pending.isEmpty()) {
            list.addView(sectionTitle(host.getString(R.string.plan_section_pending)));
            for (AcademicEvent event : pending) {
                list.addView(eventRow(event, dialog));
            }
        }
        if (!finished.isEmpty()) {
            list.addView(sectionTitle(host.getString(R.string.plan_section_done)));
            for (AcademicEvent event : finished) {
                list.addView(eventRow(event, dialog));
            }
        }
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(host);
        title.setText(text);
        title.setTextColor(host.mutedColor());
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(host.dp(4), host.dp(12), host.dp(4), host.dp(6));
        return title;
    }

    private LinearLayout eventRow(AcademicEvent event, Dialog parent) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(host.dp(8), host.dp(8), host.dp(8), host.dp(8));
        row.setBackground(host.roundedBg(host.cardColorHex(), RADIUS));
        row.setOnClickListener(v -> {
            parent.dismiss();
            showEditorDialog(event);
        });
        host.attachPressFeedback(row);

        // 类型徽标：首字 + 主题色圆点底
        TextView badge = new TextView(host);
        badge.setText(typeBadge(event.type));
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(12);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(typeColor(event.type));
        badgeBg.setCornerRadius(host.dp(14));
        badge.setBackground(badgeBg);
        row.addView(badge, new LinearLayout.LayoutParams(host.dp(28), host.dp(28)));

        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        contentParams.leftMargin = host.dp(10);
        row.addView(content, contentParams);

        TextView title = new TextView(host);
        title.setText(event.title.length() == 0
                ? host.getString(R.string.academic_unnamed) : event.title);
        title.setTextColor(host.inkColor());
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (event.done) {
            title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            title.setAlpha(0.55f);
        }
        content.addView(title);

        StringBuilder meta = new StringBuilder(eventDateText(event));
        if (event.hasTime()) {
            meta.append(" ").append(minuteText(event.minuteOfDay));
        }
        if (event.hasCourse()) {
            meta.append(" · ").append(event.courseName);
        }
        if (event.hasLocation()) {
            meta.append(" · ").append(event.location);
        }
        if (event.hasSeat()) {
            meta.append(" · ").append(host.getString(R.string.academic_meta_seat, event.seat));
        }
        TextView metaView = new TextView(host);
        metaView.setText(meta.toString());
        metaView.setTextColor(host.mutedColor());
        metaView.setTextSize(12);
        metaView.setSingleLine(true);
        metaView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = host.dp(2);
        content.addView(metaView, metaParams);

        TextView check = new TextView(host);
        check.setText(event.done ? "☑" : "☐");
        check.setTextSize(24);
        check.setGravity(Gravity.CENTER);
        check.setTextColor(event.done ? host.accentColor() : host.mutedColor());
        check.setContentDescription(event.done
                ? host.getString(R.string.academic_cd_mark_undone)
                : host.getString(R.string.academic_cd_mark_done));
        check.setOnClickListener(v -> {
            host.toggleAcademicEventDone(event);
            parent.dismiss();
            showTimelineDialog();
        });
        host.attachPressFeedback(check);
        row.addView(check, new LinearLayout.LayoutParams(host.dp(40), host.dp(40)));

        TextView edit = new TextView(host);
        edit.setText("✎");
        edit.setTextSize(18);
        edit.setGravity(Gravity.CENTER);
        edit.setTextColor(host.mutedColor());
        edit.setContentDescription(host.getString(R.string.academic_edit));
        edit.setOnClickListener(v -> {
            parent.dismiss();
            showEditorDialog(event);
        });
        host.attachPressFeedback(edit);
        row.addView(edit, new LinearLayout.LayoutParams(host.dp(40), host.dp(40)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, host.dp(8));
        row.setLayoutParams(params);
        return row;
    }

    /** 编辑器：标题/类型/关联课程/日期/时刻/地点/座位/备注 + 保存/删除/取消。 */
    public void showEditorDialog(AcademicEvent existing) {
        showEditorDialog(existing, null);
    }

    /**
     * 编辑器：可指定新建时的预选类型（悬浮菜单「添加考试 / 添加DDL」直达入口）。
     *
     * @param existing   非空表示编辑既有事件，此时 {@code presetType} 被忽略
     * @param presetType 仅新建生效；为空时沿用默认「考试」，保持时间线「添加事件」旧流程不变
     */
    public void showEditorDialog(AcademicEvent existing, AcademicEvent.Type presetType) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(existing == null
                ? host.getString(R.string.academic_editor_new)
                : host.getString(R.string.academic_edit));

        final String[] titleValue = {existing == null ? "" : existing.title};
        final AcademicEvent.Type[] typeValue = {
                existing != null ? existing.type
                        : (presetType == null ? AcademicEvent.Type.EXAM : presetType)};
        final String[] courseValue = {existing == null ? "" : existing.courseName};
        final long[] dateValue = {existing == null
                ? AcademicEvent.normalizedDateMillis(Calendar.getInstance())
                : existing.dateMillis};
        final int[] minuteValue = {existing == null ? -1 : existing.minuteOfDay};
        final String[] locationValue = {existing == null ? "" : existing.location};
        final String[] seatValue = {existing == null ? "" : existing.seat};
        final String[] noteValue = {existing == null ? "" : existing.note};

        EditText titleInput = host.input(host.getString(R.string.academic_editor_title_hint),
                titleValue[0]);
        panel.addView(titleInput);

        // 类型 chips
        LinearLayout typeRow = new LinearLayout(host);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        typeRow.setGravity(Gravity.CENTER);
        final TextView[] chips = new TextView[AcademicEvent.Type.values().length];
        AcademicEvent.Type[] types = AcademicEvent.Type.values();
        final int accent = host.accentColor();
        for (int i = 0; i < types.length; i++) {
            final int index = i;
            TextView chip = new TextView(host);
            chip.setText(typeLabel(types[i]));
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(types[i] == typeValue[0] ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            chip.setTextColor(types[i] == typeValue[0] ? Color.WHITE : host.mutedColor());
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setCornerRadius(host.dp(16));
            chipBg.setColor(types[i] == typeValue[0] ? accent : host.color(host.cardColorHex()));
            chip.setBackground(chipBg);
            chip.setClickable(true);
            chip.setOnClickListener(v -> {
                typeValue[0] = types[index];
                for (int j = 0; j < chips.length; j++) {
                    boolean selected = j == index;
                    chips[j].setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                    chips[j].setTextColor(selected ? Color.WHITE : host.mutedColor());
                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(host.dp(16));
                    bg.setColor(selected ? accent : host.color(host.cardColorHex()));
                    chips[j].setBackground(bg);
                }
            });
            chips[i] = chip;
            typeRow.addView(chip, new LinearLayout.LayoutParams(0, host.dp(36), 1f));
        }
        LinearLayout.LayoutParams typeRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        typeRowParams.topMargin = host.dp(10);
        typeRow.setLayoutParams(typeRowParams);
        panel.addView(typeRow);

        // 关联课程
        String currentCourseLabel = courseValue[0].length() == 0
                ? host.getString(R.string.academic_course_none) : courseValue[0];
        View courseRow = host.settingValueRow(
                host.getString(R.string.academic_row_course), currentCourseLabel, v ->
                        host.showChooser(v, host.getString(R.string.academic_row_course),
                                host.courseNameChoices(), currentCourseLabel, value -> {
                                    courseValue[0] = "不关联".equals(value) ? "" : value;
                                    host.updateSettingValueRow(v, "不关联".equals(value)
                                            ? host.getString(R.string.academic_course_none) : value);
                                }));
        panel.addView(courseRow);

        // 日期
        View dateRow = host.settingValueRow(host.getString(R.string.academic_row_date),
                dateText(dateValue[0]), v -> host.showDatePickerDialog(
                        host.getString(R.string.academic_row_date), dateValue[0], value -> {
                            dateValue[0] = value;
                            host.updateSettingValueRow(v, dateText(dateValue[0]));
                        }));
        panel.addView(dateRow);

        // 时刻（可切换“仅日期”）
        final boolean[] hasTime = {minuteValue[0] >= 0};
        View timeRow = host.settingValueRow(host.getString(R.string.academic_row_time),
                hasTime[0] ? minuteText(minuteValue[0])
                        : host.getString(R.string.academic_time_none), v -> {
                    if (!hasTime[0]) {
                        minuteValue[0] = AcademicEvent.DEFAULT_MINUTE_OF_DAY;
                    }
                    hasTime[0] = true;
                    host.showTimePickerDialog(host.getString(R.string.academic_row_time),
                            minuteValue[0], value -> {
                                minuteValue[0] = value;
                                host.updateSettingValueRow(v, minuteText(minuteValue[0]));
                            });
                });
        panel.addView(timeRow);
        TextView clearTime = new TextView(host);
        clearTime.setText(host.getString(R.string.academic_time_clear));
        clearTime.setTextSize(13);
        clearTime.setTypeface(Typeface.DEFAULT_BOLD);
        clearTime.setTextColor(host.accentColor());
        clearTime.setGravity(Gravity.CENTER);
        clearTime.setMinHeight(host.dp(36));
        clearTime.setOnClickListener(v -> {
            minuteValue[0] = -1;
            hasTime[0] = false;
            host.updateSettingValueRow(timeRow, host.getString(R.string.academic_time_none));
        });
        panel.addView(clearTime);

        // 地点 / 座位
        EditText locationInput = host.input(host.getString(R.string.academic_editor_location),
                locationValue[0]);
        locationInput.setInputType(InputType.TYPE_CLASS_TEXT);
        panel.addView(locationInput);
        EditText seatInput = host.input(host.getString(R.string.academic_editor_seat),
                seatValue[0]);
        seatInput.setInputType(InputType.TYPE_CLASS_TEXT);
        panel.addView(seatInput);

        // 备注
        EditText noteInput = host.input(host.getString(R.string.academic_editor_note),
                noteValue[0]);
        noteInput.setInputType(InputType.TYPE_CLASS_TEXT);
        noteInput.setSingleLine(false);
        noteInput.setMinLines(2);
        android.view.ViewGroup.LayoutParams noteParams = noteInput.getLayoutParams();
        if (noteParams != null) {
            noteParams.height = host.dp(76);
        }
        panel.addView(noteInput);

        panel.addView(dialogAction(host.getString(R.string.editor_action_save), v -> {
            String title = titleInput.getText() == null ? "" : titleInput.getText().toString().trim();
            if (title.length() == 0) {
                Toast.makeText(host, host.getString(R.string.academic_error_title_required),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            host.saveAcademicEvent(existing, title, courseValue[0], typeValue[0],
                    dateValue[0], minuteValue[0],
                    locationInput.getText() == null ? "" : locationInput.getText().toString().trim(),
                    seatInput.getText() == null ? "" : seatInput.getText().toString().trim(),
                    noteInput.getText() == null ? "" : noteInput.getText().toString().trim());
            dialog.dismiss();
        }));
        if (existing != null) {
            panel.addView(dialogAction(host.getString(R.string.academic_action_delete), v -> {
                host.deleteAcademicEvent(existing);
                dialog.dismiss();
            }));
        }
        panel.addView(dialogAction(host.getString(R.string.editor_action_cancel), v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    private String typeLabel(AcademicEvent.Type type) {
        switch (type) {
            case DEADLINE:
                return host.getString(R.string.academic_type_deadline);
            case PRACTICE:
                return host.getString(R.string.academic_type_practice);
            case EXAM:
            default:
                return host.getString(R.string.academic_type_exam);
        }
    }

    private String typeBadge(AcademicEvent.Type type) {
        switch (type) {
            case DEADLINE:
                return "截";
            case PRACTICE:
                return "践";
            case EXAM:
            default:
                return "考";
        }
    }

    private int typeColor(AcademicEvent.Type type) {
        switch (type) {
            case DEADLINE:
                return host.color(COLOR_DEADLINE);
            case PRACTICE:
                return host.color(COLOR_PRACTICE);
            case EXAM:
            default:
                return host.color(COLOR_EXAM);
        }
    }

    private String dateText(long dateMillis) {
        return new SimpleDateFormat("yyyy/M/d", Locale.ROOT)
                .format(new java.util.Date(dateMillis));
    }

    /** 「9月26日 周日」样式（用于列表 meta）。 */
    private String eventDateText(AcademicEvent event) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(event.dateMillis);
        String dayLabel = WeekdayLabels.label(host, date.get(Calendar.DAY_OF_WEEK) - 1);
        return host.getString(R.string.academic_meta_date,
                date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH), dayLabel);
    }

    private String minuteText(int minute) {
        if (minute < 0) {
            return host.getString(R.string.academic_time_none);
        }
        return host.twoDigits(minute / 60) + ":" + host.twoDigits(minute % 60);
    }
}
