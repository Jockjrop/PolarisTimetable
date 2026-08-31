package com.polaris.timetable.ui;

import android.content.Context;
import android.content.res.Configuration;

/**
 * 窗口尺寸判定：收敛 MainActivity 中 25+ 处 isLandscapeTablet() 分支。
 * 判定规则与原 MainActivity.isLandscapeTablet() 完全一致 — smallestScreenWidthDp >= 600 且横屏。
 * 方法保留在 MainActivity，但内部统一委托至此，避免魔法数分散。
 */
public final class WindowSizeClass {
    private WindowSizeClass() {}

    public static boolean isLandscapeTablet(Context context) {
        if (context == null) {
            return false;
        }
        return isLandscapeTablet(context.getResources().getConfiguration());
    }

    public static boolean isLandscapeTablet(Configuration configuration) {
        if (configuration == null) {
            return false;
        }
        return configuration.smallestScreenWidthDp >= 600
                && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
    }
}
