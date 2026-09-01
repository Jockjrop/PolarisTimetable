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
        return isTablet(configuration)
                && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /** 平板判定（纯尺寸语义，不含方向）：smallestScreenWidthDp >= 600。 */
    public static boolean isTablet(Context context) {
        return context != null && isTablet(context.getResources().getConfiguration());
    }

    public static boolean isTablet(Configuration configuration) {
        return configuration != null && configuration.smallestScreenWidthDp >= 600;
    }
}
