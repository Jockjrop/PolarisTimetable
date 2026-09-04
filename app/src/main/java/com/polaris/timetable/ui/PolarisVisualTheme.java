package com.polaris.timetable.ui;

import android.graphics.Color;

import java.util.Locale;

/** Visual-only theme tokens shared by the shell and timetable renderer. */
public final class PolarisVisualTheme {
    public static final String MINIMAL = "极简风格";
    public static final String AURORA = "极光幻彩";
    public static final String GALAXY = "深空星河";
    public static final String CAMPUS = "云境校园";
    public static final String[] NAMES = {MINIMAL, AURORA, GALAXY, CAMPUS};

    private PolarisVisualTheme() {
    }

    public static String normalize(String value) {
        if (AURORA.equals(value) || GALAXY.equals(value) || CAMPUS.equals(value)) {
            return value;
        }
        return MINIMAL;
    }

    public static boolean defaultDark(String value) {
        return GALAXY.equals(normalize(value));
    }

    public static int pageColor(String value, boolean dark) {
        String theme = normalize(value);
        if (MINIMAL.equals(theme)) {
            return dark ? color("#0D1422") : color("#EAF3FB");
        }
        if (dark) {
            if (AURORA.equals(theme)) {
                return color("#10142B");
            }
            if (CAMPUS.equals(theme)) {
                return color("#0B1B2D");
            }
            return color("#06152C");
        }
        if (AURORA.equals(theme)) {
            return color("#EEF3FF");
        }
        if (GALAXY.equals(theme)) {
            return color("#EAF1FB");
        }
        return color("#EFF6FF");
    }

    public static int boardSurfaceColor(String value, boolean dark) {
        String theme = normalize(value);
        if (MINIMAL.equals(theme)) {
            return dark ? color("#101827") : color("#EAF3FB");
        }
        if (dark) {
            if (GALAXY.equals(theme)) {
                return color("#92071832");
            }
            if (AURORA.equals(theme)) {
                return color("#96122037");
            }
            return color("#9814263B");
        }
        if (AURORA.equals(theme)) {
            return color("#72F3F5FF");
        }
        if (GALAXY.equals(theme)) {
            return color("#86EEF4FC");
        }
        return color("#82F5F9FF");
    }

    public static int cardColor(String value, boolean dark) {
        String theme = normalize(value);
        if (MINIMAL.equals(theme)) {
            return dark ? color("#182235") : color("#F8FBFF");
        }
        if (dark) {
            if (AURORA.equals(theme)) {
                return color("#D51D2141");
            }
            if (CAMPUS.equals(theme)) {
                return color("#D5182B42");
            }
            return color("#D0142848");
        }
        if (AURORA.equals(theme)) {
            return color("#E8FBFCFF");
        }
        if (GALAXY.equals(theme)) {
            return color("#EAF5F8FF");
        }
        return color("#EDF9FCFF");
    }

    public static int groupColor(String value, boolean dark) {
        String theme = normalize(value);
        if (MINIMAL.equals(theme)) {
            return dark ? color("#141E30") : color("#F2F0FA");
        }
        if (dark) {
            return GALAXY.equals(theme) ? color("#C60E203B") : color("#C3172940");
        }
        return AURORA.equals(theme) ? color("#DDF2EEFC") : color("#DDEAF2FC");
    }

    public static int pressColor(String value, boolean dark) {
        if (MINIMAL.equals(normalize(value))) {
            return dark ? color("#22304A") : color("#EAF1FA");
        }
        if (dark) {
            return GALAXY.equals(normalize(value)) ? color("#2A4168") : color("#2B3B59");
        }
        return AURORA.equals(normalize(value)) ? color("#E4E6FA") : color("#DFEAF7");
    }

    public static int inkColor(String value, boolean dark) {
        if (MINIMAL.equals(normalize(value))) {
            return dark ? color("#EEF4FF") : color("#172033");
        }
        if (dark) {
            return color("#F1F6FF");
        }
        return AURORA.equals(normalize(value)) ? color("#13264A") : color("#102A4D");
    }

    public static int mutedColor(String value, boolean dark) {
        if (MINIMAL.equals(normalize(value))) {
            return dark ? color("#9AA8BE") : color("#667085");
        }
        if (dark) {
            return GALAXY.equals(normalize(value)) ? color("#A8B9D3") : color("#AAB8CF");
        }
        return AURORA.equals(normalize(value)) ? color("#596A84") : color("#596B84");
    }

    public static int accentColor(String value, boolean dark) {
        String theme = normalize(value);
        if (MINIMAL.equals(theme)) {
            return dark ? color("#1F73E0") : color("#172033");
        }
        if (AURORA.equals(theme)) {
            return dark ? color("#A99AFF") : color("#6F60D9");
        }
        if (GALAXY.equals(theme)) {
            return dark ? color("#6EA3FF") : color("#3D66C8");
        }
        return dark ? color("#78AEE8") : color("#2F6CAB");
    }

    /**
     * 强调色之上的前景色：按强调色相对亮度选择深色或白色。
     * 悬浮按钮等强调色填充控件必须使用它，不能写死白色——极简/云境等主题在
     * 浅色强调下白字对比度不足。
     */
    public static int onAccentColor(String value, boolean dark) {
        double luminance = androidx.core.graphics.ColorUtils.calculateLuminance(
                accentColor(value, dark));
        return luminance > 0.35d ? color("#FF101828") : Color.WHITE;
    }

    public static int accentSurfaceColor(String value, boolean dark) {
        String theme = normalize(value);
        if (MINIMAL.equals(theme)) {
            return dark ? color("#4D31527D") : color("#66D8E6F5");
        }
        if (dark) {
            return AURORA.equals(theme) ? color("#503D4E89") : color("#50305B98");
        }
        return AURORA.equals(theme) ? color("#80DDD8FF") : color("#8ADCEAFF");
    }

    /** 警示/需复核文案色，不随视觉风格变化。 */
    public static int warningColor(boolean dark) {
        return dark ? color("#FFC266") : color("#8A4B00");
    }

    /** 课表"当前时间"指示线用的高强调红，不随视觉风格变化。 */
    public static int nowIndicatorColor(boolean dark) {
        return dark ? color("#FF7177") : color("#E0474D");
    }

    public static int outlineColor(String value, boolean dark) {
        String theme = normalize(value);
        if (MINIMAL.equals(theme)) {
            return dark ? color("#2AFFFFFF") : color("#82FFFFFF");
        }
        if (dark) {
            return GALAXY.equals(theme) ? color("#6C7896C2") : color("#58FFFFFF");
        }
        return AURORA.equals(theme) ? color("#B8FFFFFF") : color("#D8FFFFFF");
    }

    public static int[] glassTintColors(String value, boolean dark, int neutral) {
        String theme = normalize(value);
        if (MINIMAL.equals(theme)) {
            return new int[]{neutral, neutral};
        }
        if (dark) {
            if (AURORA.equals(theme)) {
                return new int[]{
                        blendRgb(neutral, color("#5679A8"), 0.14f),
                        blendRgb(neutral, color("#7D5B9E"), 0.14f)
                };
            }
            if (GALAXY.equals(theme)) {
                return new int[]{
                        blendRgb(neutral, color("#356FAF"), 0.18f),
                        blendRgb(neutral, color("#263F7B"), 0.18f)
                };
            }
            return new int[]{
                    blendRgb(neutral, color("#4C88AB"), 0.12f),
                    blendRgb(neutral, color("#527AA0"), 0.12f)
            };
        }
        if (AURORA.equals(theme)) {
            return new int[]{
                    blendRgb(neutral, color("#8EEAF4"), 0.12f),
                    blendRgb(neutral, color("#E5A7EA"), 0.12f)
            };
        }
        if (GALAXY.equals(theme)) {
            return new int[]{
                    blendRgb(neutral, color("#8DB9F0"), 0.10f),
                    blendRgb(neutral, color("#A6B2EA"), 0.10f)
            };
        }
        return new int[]{
                blendRgb(neutral, color("#A0D5F3"), 0.10f),
                blendRgb(neutral, color("#C6DDEF"), 0.10f)
        };
    }

    public static int glassStrokeColor(String value, boolean dark, int neutralStroke) {
        String theme = normalize(value);
        if (MINIMAL.equals(theme)) {
            return neutralStroke;
        }
        int mixed = blendRgb(neutralStroke, accentColor(theme, dark), dark ? 0.22f : 0.18f);
        return Color.argb(Color.alpha(neutralStroke),
                Color.red(mixed), Color.green(mixed), Color.blue(mixed));
    }

    public static int gridLineColor(String value, boolean dark) {
        if (MINIMAL.equals(normalize(value))) {
            return dark ? color("#465B7A") : color("#D1DCEE");
        }
        if (dark) {
            return GALAXY.equals(normalize(value)) ? color("#3552769E") : color("#3D61738F");
        }
        return AURORA.equals(normalize(value)) ? color("#6FB7C5DB") : color("#76C4D2E3");
    }

    public static int[] coursePalette(String value, boolean dark) {
        String theme = normalize(value);
        if (GALAXY.equals(theme)) {
            return colors(dark
                    ? new String[]{"#C6A600", "#7441B8", "#138DC2", "#B33678", "#21833B", "#B45C2A"}
                    : new String[]{"#E7C43E", "#9A70D8", "#58B7DF", "#D978A7", "#69B979", "#E79A63"});
        }
        if (CAMPUS.equals(theme)) {
            return colors(dark
                    ? new String[]{"#A98728", "#4D8B54", "#7055A9", "#9E496E", "#397CA0", "#9C6739"}
                    : new String[]{"#F1D174", "#ACDA7A", "#B7A0E8", "#EEA0C4", "#83BDEB", "#EBA67C"});
        }
        return colors(dark
                ? new String[]{"#A64F82", "#338DAE", "#7354B8", "#4E9B65", "#B98A31", "#B65C62"}
                : new String[]{"#F09AC1", "#6BCBE6", "#B39AE9", "#96DEA1", "#F0CC70", "#EF9B91"});
    }

    public static String hex(int value) {
        return String.format(Locale.US, "#%08X", value);
    }

    private static int[] colors(String[] values) {
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = color(values[index]);
        }
        return result;
    }

    private static int color(String value) {
        return Color.parseColor(value);
    }

    private static int blendRgb(int from, int to, float amount) {
        float bounded = Math.max(0f, Math.min(1f, amount));
        int red = Math.round(Color.red(from) * (1f - bounded) + Color.red(to) * bounded);
        int green = Math.round(Color.green(from) * (1f - bounded) + Color.green(to) * bounded);
        int blue = Math.round(Color.blue(from) * (1f - bounded) + Color.blue(to) * bounded);
        return Color.rgb(red, green, blue);
    }
}
