package com.polaris.timetable;

import android.app.Dialog;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.polaris.timetable.ui.DesignTokens;

/**
 * 提醒与权限组对话框（阶段 2-1 抽取）。
 * 悬浮窗/精确闹钟/后台保护引导对话框，全部为一次性引导确认，
 * 动作由宿主 MainActivity 执行。
 */
public class ReminderDialogs extends DialogKit {

    public ReminderDialogs(MainActivity host) {
        super(host);
    }

    public void showOverlayPermissionDialog() {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.reminder_overlay_title));
        TextView message = new TextView(host);
        message.setText(host.getString(R.string.reminder_overlay_message));
        message.setTextColor(host.mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(0f, 1.2f);
        message.setPadding(0, 0, 0, host.dp(8));
        panel.addView(message);
        panel.addView(dialogAction(host.getString(R.string.reminder_overlay_grant), v -> {
            dialog.dismiss();
            host.enableCourseReminders();
            host.openOverlaySettings();
        }));
        panel.addView(dialogAction(host.getString(R.string.reminder_overlay_continue), v -> {
            dialog.dismiss();
            host.requestNotificationPermissionForReminders();
        }));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showExactReminderAccessDialog() {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.reminder_exact_title));
        TextView message = new TextView(host);
        message.setText(host.getString(R.string.reminder_exact_message));
        message.setTextColor(host.mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(0f, 1.2f);
        message.setPadding(0, 0, 0, host.dp(8));
        panel.addView(message);
        panel.addView(dialogAction(host.getString(R.string.reminder_exact_allow), v -> {
            dialog.dismiss();
            host.openExactAlarmSettings();
        }));
        panel.addView(dialogAction(host.getString(R.string.reminder_exact_use_normal), v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    public void showReminderGuardDialog() {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = dialogPanel(host.getString(R.string.reminder_restore_title));
        TextView message = new TextView(host);
        message.setText(host.getString(R.string.reminder_restore_message));
        message.setTextColor(host.mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(0f, 1.2f);
        message.setPadding(0, 0, 0, host.dp(8));
        panel.addView(message);
        panel.addView(dialogAction(host.getString(R.string.reminder_restore_go_settings), v -> {
            dialog.dismiss();
            host.openAppDetailsSettings();
        }));
        panel.addView(dialogAction(host.getString(R.string.reminder_restore_ack), v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }
}
