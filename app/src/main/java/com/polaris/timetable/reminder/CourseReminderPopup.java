package com.polaris.timetable.reminder;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.polaris.timetable.MainActivity;

/**
 * Shows the course reminder as a floating popup on top of any app
 * (TYPE_APPLICATION_OVERLAY). Requires the SYSTEM_ALERT_WINDOW permission;
 * callers should fall back to a notification when the permission is missing.
 */
public final class CourseReminderPopup {
    private static final long AUTO_DISMISS_MILLIS = 15_000L;

    private static View activeView;
    private static WindowManager activeWindowManager;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    // 悬浮提醒窗覆盖在任意应用之上，不跟随应用内视觉风格与暗色模式，
    // 固定为浅色卡片（与 PolarisVisualTheme 极简亮色 token 同源）。
    private static final int INK = Color.parseColor("#172033");
    private static final int MUTED = Color.parseColor("#667085");
    private static final int ACCENT = Color.parseColor("#3D66C8");
    private static final int CARD = Color.WHITE;
    private static final int SURFACE = Color.parseColor("#EAF3FB");
    private static final int STROKE = Color.parseColor("#D1DCEE");

    private CourseReminderPopup() {
    }

    public static void show(Context context, Intent source) {
        Context appContext = context.getApplicationContext();
        dismiss();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || !android.provider.Settings.canDrawOverlays(appContext)) {
            return;
        }
        String courseName = source == null ? null
                : source.getStringExtra(CourseReminderScheduler.EXTRA_COURSE_NAME);
        String location = source == null ? null
                : source.getStringExtra(CourseReminderScheduler.EXTRA_LOCATION);
        String timeText = source == null ? null
                : source.getStringExtra(CourseReminderScheduler.EXTRA_TIME_TEXT);
        int week = source == null ? 0
                : source.getIntExtra(CourseReminderScheduler.EXTRA_WEEK, 0);
        int minutesBefore = source == null ? 0
                : source.getIntExtra(CourseReminderScheduler.EXTRA_MINUTES_BEFORE, 0);

        StringBuilder details = new StringBuilder();
        details.append(minutesBefore > 0 ? minutesBefore + " 分钟后上课" : "课程即将开始");
        if (timeText != null && timeText.length() > 0) {
            details.append(" · ").append(timeText);
        }
        if (location != null && location.length() > 0) {
            details.append(" · ").append(location);
        }
        if (week > 0) {
            details.append(" · 第 ").append(week).append(" 周");
        }

        LinearLayout card = new LinearLayout(appContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(appContext, 20), dp(appContext, 16),
                dp(appContext, 20), dp(appContext, 12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD);
        background.setCornerRadius(dp(appContext, 20));
        background.setStroke(dp(appContext, 1), STROKE);
        card.setBackground(background);
        card.setElevation(dp(appContext, 10));

        TextView title = new TextView(appContext);
        title.setText(courseName == null || courseName.length() == 0
                ? "课程提醒" : courseName);
        title.setTextColor(INK);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title);

        TextView body = new TextView(appContext);
        body.setText(details.toString());
        body.setTextColor(MUTED);
        body.setTextSize(14);
        body.setLineSpacing(dp(appContext, 2), 1f);
        body.setPadding(0, dp(appContext, 4), 0, dp(appContext, 10));
        card.addView(body);

        LinearLayout actions = new LinearLayout(appContext);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(actionButton(appContext, "关闭", false, v -> dismiss()));
        actions.addView(actionButton(appContext, "打开课表", true, v -> {
            dismiss();
            Intent openApp = new Intent(appContext, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                appContext.startActivity(openApp);
            } catch (RuntimeException ignored) {
                // Activity start from background can be blocked by the system.
            }
        }));
        card.addView(actions);

        int width = Math.min(dp(appContext, 340),
                appContext.getResources().getDisplayMetrics().widthPixels - dp(appContext, 32));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(appContext, 48);

        try {
            WindowManager windowManager = (WindowManager) appContext.getSystemService(
                    Context.WINDOW_SERVICE);
            if (windowManager == null) {
                return;
            }
            windowManager.addView(card, params);
            activeView = card;
            activeWindowManager = windowManager;
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(CourseReminderPopup::dismiss, AUTO_DISMISS_MILLIS);
        } catch (RuntimeException ignored) {
            // Overlay addition can fail if the permission was revoked concurrently.
        }
    }

    public static void dismiss() {
        handler.removeCallbacksAndMessages(null);
        View view = activeView;
        WindowManager windowManager = activeWindowManager;
        activeView = null;
        activeWindowManager = null;
        if (view != null && windowManager != null) {
            try {
                windowManager.removeView(view);
            } catch (RuntimeException ignored) {
                // The window may already have been removed by the system.
            }
        }
    }

    private static TextView actionButton(Context context, String text, boolean primary,
                                         View.OnClickListener listener) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(primary ? Color.WHITE : ACCENT);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? ACCENT : SURFACE);
        background.setCornerRadius(dp(context, 14));
        button.setBackground(background);
        button.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, dp(context, 8), 0);
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        return button;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
