package com.polaris.timetable.ui.page;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.polaris.timetable.R;
import com.polaris.timetable.ui.BackgroundImageCrop;
import com.polaris.timetable.ui.CircleAvatarView;
import com.polaris.timetable.ui.PolarisVisualTheme;

/**
 * 「我的」页构建器:头像区 + 四个设置入口卡片 + 版本行。
 * 纯搬代码自 MainActivity,视觉零变化;外观与状态全部经 {@link Host}
 * 按需读取,builder 自身不持有可变配置,每次构建按次传参。
 */
public class MyPageBuilder {

    /** 宿主回调:由 MainActivity 实现,提供主题状态、布局度量、样式辅助与页面跳转。 */
    public interface Host {
        boolean isLandscapeTablet();

        boolean isMinimalVisualTheme();

        boolean isDarkModeActive();

        String visualTheme();

        int contentColumnWidthPx();

        int menuCardWidthPx(float percent);

        int statusBarInsetPx();

        int bottomContentInsetPx();

        int pageSurfaceColor();

        int inkColor();

        int mutedColor();

        String cardColorHex();

        int colorValue(String hex);

        Drawable roundedCardBackground(String hex, int radiusDp);

        void applyThemeElevation(View view, int elevationDp);

        void attachCardPressFeedback(View view, int radiusDp);

        String accountName();

        String avatarImageUri();

        BackgroundImageCrop avatarImageCrop();

        String schoolDisplayName();

        String semesterDisplayName();

        String versionText();

        void openScheduleSettings();

        void openGlobalSettings();

        void openSecuritySettings();

        void openMoreSettings();

        void editAccountProfile();
    }

    private final Host host;

    public MyPageBuilder(Host host) {
        this.host = host;
    }

    public ScrollView build(Context context) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(host.pageSurfaceColor());
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, host.statusBarInsetPx() + dp(context, 34),
                0, host.bottomContentInsetPx() + dp(context, 48));
        if (!host.isLandscapeTablet()) {
            int columnWidth = host.contentColumnWidthPx();
            if (columnWidth < context.getResources().getDisplayMetrics().widthPixels) {
                // 横屏平板除外：内容列封顶居中（双栏模式下左栏宽度由宿主决定）。
                page.setLayoutParams(new ScrollView.LayoutParams(columnWidth,
                        LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
            }
        }
        scrollView.addView(page);
        if (host.isMinimalVisualTheme()) {
            page.addView(profileHeader(context));
            page.addView(mySettingCard(context, context.getString(R.string.my_card_schedule), "schedule", v -> host.openScheduleSettings()));
            page.addView(mySettingCard(context, context.getString(R.string.my_card_global), "settings", v -> host.openGlobalSettings()));
            page.addView(mySettingCard(context, context.getString(R.string.my_card_security), "shield", v -> host.openSecuritySettings()));
            page.addView(mySettingCard(context, context.getString(R.string.my_card_more), "more", v -> host.openMoreSettings()));
        } else {
            page.addView(themedProfileHeader(context));
            page.addView(themedMySettingCard(context, context.getString(R.string.my_card_schedule), "schedule", v -> host.openScheduleSettings()));
            page.addView(themedMySettingCard(context, context.getString(R.string.my_card_global), "settings", v -> host.openGlobalSettings()));
            page.addView(themedMySettingCard(context, context.getString(R.string.my_card_security), "shield", v -> host.openSecuritySettings()));
            page.addView(themedMySettingCard(context, context.getString(R.string.my_card_more), "more", v -> host.openMoreSettings()));
        }
        TextView versionLine = new TextView(context);
        versionLine.setText(host.versionText());
        versionLine.setTextColor(host.mutedColor());
        versionLine.setTextSize(13);
        versionLine.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        versionParams.topMargin = dp(context, 24);
        page.addView(versionLine, versionParams);
        return scrollView;
    }

    private View themedProfileHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
        header.setBackground(host.roundedCardBackground(host.cardColorHex(), 26));
        host.applyThemeElevation(header, 4);

        CircleAvatarView avatar = new CircleAvatarView(context);
        avatar.setProfile(host.accountName(), host.avatarImageUri(), host.avatarImageCrop());
        // 头像底色：按账户名哈希取稳定随机色（1.27.7），主题切换不改变头像身份色。
        avatar.setPlaceholderColor(CircleAvatarView.placeholderColorFor(host.accountName()));
        avatar.setContentDescription(context.getString(R.string.my_cd_edit_account));
        avatar.setClickable(true);
        avatar.setFocusable(true);
        avatar.setOnClickListener(v -> host.editAccountProfile());
        header.addView(avatar, new LinearLayout.LayoutParams(dp(context, 78), dp(context, 78)));

        LinearLayout identity = new LinearLayout(context);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        identityParams.leftMargin = dp(context, 18);
        header.addView(identity, identityParams);

        TextView name = new TextView(context);
        name.setText(host.accountName());
        name.setTextColor(host.inkColor());
        name.setTextSize(22);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        identity.addView(name);

        TextView school = themedProfileLine(context, host.schoolDisplayName());
        LinearLayout.LayoutParams schoolParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        schoolParams.topMargin = dp(context, 6);
        identity.addView(school, schoolParams);

        TextView semester = themedProfileLine(context, host.semesterDisplayName());
        LinearLayout.LayoutParams semesterParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        semesterParams.topMargin = dp(context, 3);
        identity.addView(semester, semesterParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.round(host.menuCardWidthPx(0.88f)),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, dp(context, 8), 0, dp(context, 18));
        header.setLayoutParams(params);
        return header;
    }

    private TextView themedProfileLine(Context context, String value) {
        TextView line = new TextView(context);
        line.setText(value);
        line.setTextColor(host.mutedColor());
        line.setTextSize(13);
        line.setSingleLine(true);
        line.setEllipsize(TextUtils.TruncateAt.END);
        return line;
    }

    private View themedMySettingCard(Context context, String titleText, String iconType,
                                     View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 10), dp(context, 14), dp(context, 10));
        card.setBackground(host.roundedCardBackground(host.cardColorHex(), 22));
        host.applyThemeElevation(card, 3);
        card.setOnClickListener(listener);
        host.attachCardPressFeedback(card, 22);

        FrameLayout iconSurface = new FrameLayout(context);
        iconSurface.setBackground(host.roundedCardBackground(PolarisVisualTheme.hex(
                PolarisVisualTheme.accentSurfaceColor(host.visualTheme(), host.isDarkModeActive())), 16));
        host.applyThemeElevation(iconSurface, 2);
        MySettingIconView icon = new MySettingIconView(context, iconType,
                themedIconColor(iconType), host.mutedColor());
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                dp(context, 27), dp(context, 27), Gravity.CENTER);
        iconSurface.addView(icon, iconParams);
        card.addView(iconSurface, new LinearLayout.LayoutParams(dp(context, 46), dp(context, 46)));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(context, 16);
        card.addView(copy, copyParams);

        TextView label = new TextView(context);
        label.setText(titleText);
        label.setTextColor(host.inkColor());
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(label);

        TextView description = new TextView(context);
        description.setText(themedSettingDescription(context, iconType));
        description.setTextColor(host.mutedColor());
        description.setTextSize(12);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(context, 3);
        copy.addView(description, descriptionParams);

        TextView arrow = new TextView(context);
        arrow.setText("›");
        arrow.setTextColor(host.mutedColor());
        arrow.setTextSize(26);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(context, 30), dp(context, 48)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.round(host.menuCardWidthPx(0.88f)), dp(context, 78));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, 0, 0, dp(context, 12));
        card.setLayoutParams(params);
        return card;
    }

    private String themedSettingDescription(Context context, String iconType) {
        if ("schedule".equals(iconType)) {
            return context.getString(R.string.settings_themed_desc_schedule);
        }
        if ("settings".equals(iconType)) {
            return context.getString(R.string.settings_themed_desc_global);
        }
        if ("more".equals(iconType)) {
            return context.getString(R.string.settings_themed_desc_more);
        }
        return context.getString(R.string.settings_themed_desc_security);
    }

    private int themedIconColor(String iconType) {
        if ("settings".equals(iconType)) {
            return host.colorValue(host.isDarkModeActive() ? "#70D8F0" : "#238AB4");
        }
        if ("shield".equals(iconType)) {
            return host.colorValue(host.isDarkModeActive() ? "#7FE0B5" : "#2E8F68");
        }
        return PolarisVisualTheme.accentColor(host.visualTheme(), host.isDarkModeActive());
    }

    private View profileHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, dp(context, 14), 0, dp(context, 20));

        CircleAvatarView avatar = new CircleAvatarView(context);
        avatar.setProfile(host.accountName(), host.avatarImageUri(), host.avatarImageCrop());
        // 头像底色：按账户名哈希取稳定随机色（1.27.7），主题切换不改变头像身份色。
        avatar.setPlaceholderColor(CircleAvatarView.placeholderColorFor(host.accountName()));
        avatar.setContentDescription(context.getString(R.string.my_cd_edit_account));
        avatar.setClickable(true);
        avatar.setFocusable(true);
        avatar.setOnClickListener(v -> host.editAccountProfile());
        header.addView(avatar, new LinearLayout.LayoutParams(dp(context, 72), dp(context, 72)));

        TextView name = new TextView(context);
        name.setText(host.accountName());
        name.setTextColor(host.inkColor());
        name.setTextSize(20);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                Math.round(host.menuCardWidthPx(0.82f)),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(context, 10);
        header.addView(name, nameParams);

        TextView school = profileInfoLine(context, host.schoolDisplayName());
        LinearLayout.LayoutParams schoolParams = new LinearLayout.LayoutParams(
                Math.round(host.menuCardWidthPx(0.82f)),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        schoolParams.topMargin = dp(context, 5);
        header.addView(school, schoolParams);

        TextView semester = profileInfoLine(context, host.semesterDisplayName());
        LinearLayout.LayoutParams semesterParams = new LinearLayout.LayoutParams(
                Math.round(host.menuCardWidthPx(0.82f)),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        semesterParams.topMargin = dp(context, 2);
        header.addView(semester, semesterParams);
        return header;
    }

    private TextView profileInfoLine(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(host.mutedColor());
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private View mySettingCard(Context context, String titleText, String iconType,
                               View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        card.setBackground(host.roundedCardBackground(host.cardColorHex(), 18));
        card.setOnClickListener(listener);
        host.attachCardPressFeedback(card, 18);

        MySettingIconView icon = new MySettingIconView(context, iconType,
                host.inkColor(), host.mutedColor());
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(context, 28), dp(context, 28));
        iconParams.leftMargin = dp(context, 6);
        iconParams.rightMargin = dp(context, 18);
        card.addView(icon, iconParams);

        TextView label = new TextView(context);
        label.setText(titleText);
        label.setTextColor(host.inkColor());
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        card.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.round(host.menuCardWidthPx(0.86f)),
                dp(context, 58));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, 0, 0, dp(context, 10));
        card.setLayoutParams(params);
        return card;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** 设置入口手绘图标,纯搬自 MainActivity 内部类,视觉零变化。 */
    private static class MySettingIconView extends View {
        private final String type;
        private final int primary;
        private final int secondary;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        MySettingIconView(Context context, String type, int primary, int secondary) {
            super(context);
            this.type = type;
            this.primary = primary;
            this.secondary = secondary;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(primary);
            if ("schedule".equals(type)) {
                drawScheduleIcon(canvas, width, height);
            } else if ("settings".equals(type)) {
                drawSettingsIcon(canvas, width, height);
            } else if ("more".equals(type)) {
                drawMoreIcon(canvas, width, height);
            } else if ("plan".equals(type)) {
                drawPlanIcon(canvas, width, height);
            } else {
                drawShieldIcon(canvas, width, height);
            }
        }

        private void drawScheduleIcon(Canvas canvas, float width, float height) {
            float left = width * 0.18f;
            float top = height * 0.2f;
            float right = width * 0.82f;
            float bottom = height * 0.8f;
            canvas.drawRoundRect(left, top, right, bottom, dp(getContext(), 4), dp(getContext(), 4), paint);
            canvas.drawLine(left, height * 0.38f, right, height * 0.38f, paint);
            canvas.drawLine(width * 0.39f, height * 0.38f, width * 0.39f, bottom, paint);
            canvas.drawLine(width * 0.61f, height * 0.38f, width * 0.61f, bottom, paint);
            canvas.drawLine(width * 0.3f, top - dp(getContext(), 3), width * 0.3f, top + dp(getContext(), 5), paint);
            canvas.drawLine(width * 0.7f, top - dp(getContext(), 3), width * 0.7f, top + dp(getContext(), 5), paint);
        }

        private void drawSettingsIcon(Canvas canvas, float width, float height) {
            float centerX = width / 2f;
            float centerY = height / 2f;
            float outer = Math.min(width, height) * 0.34f;
            float inner = Math.min(width, height) * 0.13f;
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2d * i / 8d;
                float startX = centerX + (float) Math.cos(angle) * outer * 0.78f;
                float startY = centerY + (float) Math.sin(angle) * outer * 0.78f;
                float endX = centerX + (float) Math.cos(angle) * outer;
                float endY = centerY + (float) Math.sin(angle) * outer;
                canvas.drawLine(startX, startY, endX, endY, paint);
            }
            canvas.drawCircle(centerX, centerY, outer * 0.66f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(secondary);
            canvas.drawCircle(centerX, centerY, inner, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(primary);
        }

        private void drawMoreIcon(Canvas canvas, float width, float height) {
            float centerY = height / 2f;
            float radius = Math.min(width, height) * 0.08f;
            float spacing = Math.min(width, height) * 0.2f;
            float centerX = width / 2f;
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(centerX - spacing, centerY, radius, paint);
            canvas.drawCircle(centerX, centerY, radius, paint);
            canvas.drawCircle(centerX + spacing, centerY, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
        }

        private void drawShieldIcon(Canvas canvas, float width, float height) {
            path.reset();
            path.moveTo(width * 0.5f, height * 0.14f);
            path.lineTo(width * 0.78f, height * 0.25f);
            path.lineTo(width * 0.74f, height * 0.56f);
            path.quadTo(width * 0.68f, height * 0.76f, width * 0.5f, height * 0.88f);
            path.quadTo(width * 0.32f, height * 0.76f, width * 0.26f, height * 0.56f);
            path.lineTo(width * 0.22f, height * 0.25f);
            path.close();
            canvas.drawPath(path, paint);
            canvas.drawLine(width * 0.39f, height * 0.5f, width * 0.47f, height * 0.6f, paint);
            canvas.drawLine(width * 0.47f, height * 0.6f, width * 0.63f, height * 0.42f, paint);
        }

        private void drawPlanIcon(Canvas canvas, float width, float height) {
            // 清单：三条横线 + 首行行首对勾
            float left = width * 0.3f;
            float right = width * 0.82f;
            for (int i = 0; i < 3; i++) {
                float y = height * (0.28f + 0.22f * i);
                canvas.drawLine(left, y, right, y, paint);
            }
            float checkX = width * 0.16f;
            float checkY = height * 0.28f;
            canvas.drawLine(checkX, checkY, checkX + dp(getContext(), 4), checkY + dp(getContext(), 4), paint);
            canvas.drawLine(checkX + dp(getContext(), 4), checkY + dp(getContext(), 4),
                    checkX + dp(getContext(), 10), checkY - dp(getContext(), 5), paint);
        }
    }
}
