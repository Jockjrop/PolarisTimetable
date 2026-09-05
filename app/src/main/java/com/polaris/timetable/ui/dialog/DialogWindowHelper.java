package com.polaris.timetable.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * 对话框窗口助手：收敛 MainActivity 中 30+ 处 transparentDialog / makeDialogStill 重复代码。
 * 视觉参数与原实现像素级一致（dim 0.68/0.42、宽 320-400dp、背景透明、动画禁用）。
 */
public final class DialogWindowHelper {
    private DialogWindowHelper() {}

    public static void transparentDialog(Dialog dialog, boolean isDark, Context context) {
        if (dialog == null || dialog.getWindow() == null || context == null) {
            return;
        }
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(isDark ? 0.68f : 0.42f);
        int dialogWidth = Math.max(dp(context, 320), Math.min(dp(context, 400),
                context.getResources().getDisplayMetrics().widthPixels - dp(context, 64)));
        window.setLayout(dialogWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        makeDialogStill(window);
    }

    public static void makeDialogStill(Window window) {
        if (window == null) {
            return;
        }
        window.setWindowAnimations(0);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.windowAnimations = 0;
        window.setAttributes(attributes);
    }

    /**
     * 大屏靠左对齐：对话框窗口贴屏幕左侧、垂直居中，
     * 与平板空状态「导入课表」卡片的左侧位置保持同侧（1.27.7）。
     */
    public static void alignDialogStart(Window window, Context context) {
        if (window == null || context == null) {
            return;
        }
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        attributes.x = dp(context, 24);
        attributes.y = 0;
        window.setAttributes(attributes);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
