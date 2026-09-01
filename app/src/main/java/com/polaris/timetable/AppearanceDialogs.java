package com.polaris.timetable;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;

import com.polaris.timetable.ui.DesignTokens;

import java.util.Calendar;

/**
 * 外观与背景组对话框（阶段 2-1 抽取）。
 * 通用选择/开关/数值/日期/时间对话框、周天数设置、全屏设置容器。
 * 动作与状态由宿主 MainActivity 执行，本类只负责 UI 构造与回写回调。
 */
public class AppearanceDialogs extends DialogKit {

    public AppearanceDialogs(MainActivity host) {
        super(host);
    }

    public void showSettingsDialog() {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(host.dp(18), host.dp(18), host.dp(18), host.dp(18));
        panel.setBackgroundColor(host.backgroundColor());

        TextView heading = new TextView(host);
        heading.setText(host.getString(R.string.settings_title));
        heading.setTextColor(host.inkColor());
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextSize(22);
        panel.addView(heading);

        TextView description = new TextView(host);
        description.setText(host.getString(R.string.settings_days_description));
        description.setTextColor(host.mutedColor());
        description.setTextSize(14);
        description.setPadding(0, host.dp(6), 0, host.dp(14));
        panel.addView(description);

        LinearLayout choices = new LinearLayout(host);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(choices);
        choices.addView(dayChoice(host.getString(R.string.settings_days_option, 5), 5, dialog));
        choices.addView(dayChoice(host.getString(R.string.settings_days_option, 6), 6, dialog));
        choices.addView(dayChoice(host.getString(R.string.settings_days_option, 7), 7, dialog));

        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showChoiceDialog(String titleText, String[] values, String current, StringSetter setter) {
        showChoiceDialog(null, titleText, values, current, setter);
    }

    public void showChoiceDialog(View anchor, String titleText, String[] values, String current, StringSetter setter) {
        Dialog dialog = new Dialog(host);
        final String[] pendingChoice = {null};
        FrameLayout root = new FrameLayout(host);
        root.setOnClickListener(v -> dialog.dismiss());

        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(host.dp(18), host.dp(14), host.dp(18), host.dp(14));
        panel.setBackground(host.roundedBg(host.cardColorHex(), DesignTokens.RADIUS_DIALOG_SHEET));
        panel.setOnClickListener(v -> {});

        TextView title = new TextView(host);
        title.setText(titleText);
        title.setTextColor(host.mutedColor());
        title.setTextSize(12);
        title.setSingleLine(true);
        title.setPadding(0, 0, 0, host.dp(4));
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        for (String value : values) {
            boolean active = value.equals(current);
            LinearLayout item = new LinearLayout(host);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(0, 0, 0, 0);
            item.setBackgroundColor(Color.TRANSPARENT);

            TextView label = new TextView(host);
            label.setText(value);
            label.setTextColor(host.inkColor());
            label.setTextSize(16);
            label.setSingleLine(false);
            label.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            item.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView check = new TextView(host);
            check.setText(active ? "✓" : "");
            check.setTextColor(host.inkColor());
            check.setTextSize(25);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(host.dp(30), host.dp(48));
            checkParams.leftMargin = host.dp(8);
            item.addView(check, checkParams);

            item.setOnClickListener(v -> {
                if (!value.equals(current)) {
                    pendingChoice[0] = value;
                }
                dialog.dismiss();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, host.dp(50));
            panel.addView(item, params);
        }

        int screenWidth = host.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = host.getResources().getDisplayMetrics().heightPixels;
        int panelWidth = Math.min(host.dp(214), screenWidth - host.dp(56));
        int panelHeight = host.dp(36) + values.length * host.dp(50) + host.dp(28);
        int left = screenWidth - panelWidth - host.dp(28);
        int top = host.statusBarHeight() + host.dp(88);
        if (anchor != null) {
            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            int belowTop = location[1] + anchor.getHeight() + host.dp(6);
            int aboveTop = location[1] - panelHeight - host.dp(6);
            int bottomLimit = choiceMenuBottomLimit(anchor, screenHeight, panelHeight);
            top = belowTop + panelHeight <= bottomLimit
                    ? belowTop
                    : Math.max(host.statusBarHeight() + host.dp(12), aboveTop);
            top = Math.min(top, screenHeight - panelHeight - host.dp(16));
            top = Math.max(host.statusBarHeight() + host.dp(12), top);
        }

        View choiceContent = glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        panelParams.leftMargin = left;
        panelParams.topMargin = top;
        root.addView(choiceContent, panelParams);

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            String value = pendingChoice[0];
            if (value != null) {
                View postTarget = host.rootView != null ? host.rootView : root;
                postTarget.postDelayed(() -> setter.set(value), 80L);
            }
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(host.isDarkModeActive() ? 0.68f : 0.42f);
            makeDialogStill(window);
        }
    }

    private int choiceMenuBottomLimit(View anchor, int screenHeight, int panelHeight) {
        int screenLimit = screenHeight - host.bottomContentInset() - host.dp(16);
        View group = settingGroupAncestor(anchor);
        if (group == null) {
            return screenLimit;
        }
        int[] groupLocation = new int[2];
        group.getLocationOnScreen(groupLocation);
        int groupLimit = groupLocation[1] + group.getHeight() - host.dp(8);
        return group.getHeight() >= panelHeight + host.dp(72)
                ? Math.min(screenLimit, groupLimit)
                : screenLimit;
    }

    private View settingGroupAncestor(View anchor) {
        ViewParent parent = anchor.getParent();
        while (parent instanceof View) {
            View view = (View) parent;
            if (view instanceof LinearLayout && view.getBackground() != null && view.getHeight() > host.dp(100)) {
                return view;
            }
            parent = view.getParent();
        }
        return null;
    }

    public void showToggleDialog(String titleText, boolean current, BooleanSetter setter) {
        showChoiceDialog(titleText, new String[]{"开", "关"}, current ? "开" : "关",
                value -> setter.set("开".equals(value)));
    }

    public void showNumberDialog(String titleText, int min, int max, int current, IntSetter setter) {
        showNumberDialog(titleText, min, max, current, 10, setter);
    }

    public void showNumberDialog(String titleText, int min, int max, int current, int step, IntSetter setter) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(titleText);
        final int[] value = {Math.max(min, Math.min(max, current))};
        final int safeStep = Math.max(1, step);
        EditText valueInput = host.stepInput(String.valueOf(value[0]));
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.addView(host.stepButton("−"));
        row.addView(valueInput, new LinearLayout.LayoutParams(0, host.dp(46), 1f));
        row.addView(host.stepButton("+"));
        ((TextView) row.getChildAt(0)).setOnClickListener(v -> {
            value[0] = Math.max(min, host.parseBounded(valueInput.getText().toString(), min, max, value[0]) - safeStep);
            valueInput.setText(String.valueOf(value[0]));
            valueInput.setSelection(valueInput.getText().length());
        });
        ((TextView) row.getChildAt(2)).setOnClickListener(v -> {
            value[0] = Math.min(max, host.parseBounded(valueInput.getText().toString(), min, max, value[0]) + safeStep);
            valueInput.setText(String.valueOf(value[0]));
            valueInput.setSelection(valueInput.getText().length());
        });
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        panel.addView(row, rowParams);
        panel.addView(host.pageSaveButton(() -> {
            setter.set(host.parseBounded(valueInput.getText().toString(), min, max, value[0]));
            dialog.dismiss();
        }));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showDateDialog(String titleText, String current, StringSetter setter) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(titleText);
        Calendar date = host.calendarFromText(current);
        DatePicker picker = new DatePicker(themedControlContext());
        picker.init(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH), null);
        panel.addView(picker);
        panel.addView(host.pageSaveButton(() -> {
            String value = picker.getYear() + "/" + (picker.getMonth() + 1) + "/" + picker.getDayOfMonth();
            setter.set(value);
            dialog.dismiss();
        }));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showTimeDialog(String titleText, String current, StringSetter setter) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(titleText);
        int[] time = host.timeFromText(current);
        TimePicker picker = new TimePicker(themedControlContext());
        picker.setIs24HourView(true);
        picker.setHour(time[0]);
        picker.setMinute(time[1]);
        panel.addView(picker);
        panel.addView(host.pageSaveButton(() -> {
            String value = host.getString(R.string.classtime_picker_value, host.twoDigits(picker.getHour())
                + ":" + host.twoDigits(picker.getMinute()));
            setter.set(value);
            dialog.dismiss();
        }));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public Dialog settingsDialog(String headingText) {
        Dialog dialog = new Dialog(host);
        ScrollView scrollView = new ScrollView(host);
        scrollView.setId(View.generateViewId());
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(host.dp(18), host.statusBarHeight() + host.dp(18), host.dp(18), host.dp(24));
        panel.setBackgroundColor(host.backgroundColor());
        scrollView.addView(panel);

        TextView heading = new TextView(host);
        heading.setText(headingText);
        heading.setTextColor(host.inkColor());
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextSize(22);
        panel.addView(heading);

        Button close = new Button(host);
        close.setText(host.getString(R.string.editor_action_cancel));
        close.setTextColor(host.mutedColor());
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setOnClickListener(v -> dialog.dismiss());
        panel.addView(close, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, host.dp(42)));

        dialog.setContentView(glassDialogContent(scrollView, panel, DesignTokens.RADIUS_DIALOG_SHEET));
        return dialog;
    }

    private TextView dayChoice(String text, int count, Dialog dialog) {
        TextView choice = new TextView(host);
        choice.setText(text);
        choice.setGravity(Gravity.CENTER);
        choice.setTextSize(16);
        choice.setTypeface(Typeface.DEFAULT_BOLD);
        boolean active = count == host.visibleDayCount;
        choice.setTextColor(active ? host.selectedTextColor() : host.inkColor());
        choice.setBackground(host.roundedBg(active ? host.selectedFillHex() : host.cardColorHex(), DesignTokens.RADIUS_CHIP));
        choice.setOnClickListener(v -> {
            host.visibleDayCount = count;
            host.renderSchedule();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, host.dp(44), 1f);
        params.setMargins(host.dp(4), 0, host.dp(4), 0);
        choice.setLayoutParams(params);
        return choice;
    }
}
