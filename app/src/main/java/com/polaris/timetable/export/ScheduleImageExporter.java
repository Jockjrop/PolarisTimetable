package com.polaris.timetable.export;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import com.polaris.timetable.Course;
import com.polaris.timetable.time.CourseTimeResolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** Renders a shareable, screen-size-independent image of one schedule week. */
public final class ScheduleImageExporter {
    private static final String[] DAY_NAMES = {
            "周一", "周二", "周三", "周四", "周五", "周六", "周日"
    };
    private static final int PAGE_MARGIN = 58;
    private static final int HEADER_HEIGHT = 224;
    private static final int BANNER_HEIGHT = 86;
    private static final int DAY_HEADER_HEIGHT = 92;
    private static final int TIME_COLUMN_WIDTH = 172;
    private static final int SECTION_HEIGHT = 208;
    private static final int FOOTER_HEIGHT = 74;
    private static final int COURSE_GAP = 5;
    private static final int[] COURSE_PALETTE = {
            Color.rgb(220, 234, 255),
            Color.rgb(219, 243, 232),
            Color.rgb(255, 238, 199),
            Color.rgb(235, 224, 255),
            Color.rgb(255, 224, 227),
            Color.rgb(215, 241, 247)
    };

    private ScheduleImageExporter() {
    }

    public static final class Request {
        public final List<Course> courses;
        public final CourseTimeResolver.Settings timeSettings;
        public final String scheduleName;
        public final String semesterName;
        public final String schoolName;
        public final int week;
        public final int sectionCount;
        public final boolean showSaturday;
        public final boolean showSunday;
        public final boolean showPracticeBanner;
        public final long firstWeekStartMillis;

        public Request(List<Course> courses, CourseTimeResolver.Settings timeSettings,
                       String scheduleName, String semesterName, String schoolName,
                       int week, int sectionCount, boolean showSaturday, boolean showSunday,
                       boolean showPracticeBanner, long firstWeekStartMillis) {
            this.courses = courses == null
                    ? Collections.<Course>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(courses));
            this.timeSettings = timeSettings;
            this.scheduleName = safe(scheduleName);
            this.semesterName = safe(semesterName);
            this.schoolName = safe(schoolName);
            this.week = Math.max(1, week);
            this.sectionCount = Math.max(1, Math.min(20, sectionCount));
            this.showSaturday = showSaturday;
            this.showSunday = showSunday;
            this.showPracticeBanner = showPracticeBanner;
            this.firstWeekStartMillis = firstWeekStartMillis;
        }
    }

    public static boolean hasExportableContent(Request request) {
        if (request == null) {
            return false;
        }
        ScheduleExportLayout.Result layout = ScheduleExportLayout.create(
                request.courses, request.week, request.showSaturday,
                request.showSunday, request.sectionCount);
        return !layout.items.isEmpty()
                || (request.showPracticeBanner && !layout.bannerCourses.isEmpty());
    }

    public static File exportToCache(Context context, Request request) throws IOException {
        if (context == null) {
            throw new IOException("导出参数不完整");
        }
        Bitmap bitmap = render(request);
        File directory = new File(context.getCacheDir(), ExportFileProvider.EXPORT_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            bitmap.recycle();
            throw new IOException("无法创建导出缓存目录");
        }
        File output = new File(directory, "Polaris-week-" + request.week + ".png");
        try {
            try (FileOutputStream stream = new FileOutputStream(output, false)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw new IOException("课表图片编码失败");
                }
            }
            return output;
        } catch (IOException exception) {
            if (output.exists()) {
                output.delete();
            }
            throw exception;
        } catch (RuntimeException exception) {
            if (output.exists()) {
                output.delete();
            }
            throw new IOException("课表图片生成失败", exception);
        } finally {
            bitmap.recycle();
        }
    }

    static Bitmap render(Request request) throws IOException {
        return render(request, false, request == null ? 1 : request.week);
    }

    static Bitmap renderSemester(Request request, int semesterWeeks) throws IOException {
        return render(request, true, Math.max(1, Math.min(20, semesterWeeks)));
    }

    private static Bitmap render(Request request, boolean semesterMode,
                                 int semesterWeeks) throws IOException {
        if (request == null || request.timeSettings == null) {
            throw new IOException("导出参数不完整");
        }
        ScheduleExportLayout.Result layout = semesterMode
                ? ScheduleExportLayout.createAllWeeks(request.courses,
                        request.showSaturday, request.showSunday, request.sectionCount)
                : ScheduleExportLayout.create(request.courses, request.week,
                        request.showSaturday, request.showSunday, request.sectionCount);
        List<Course> bannerCourses = request.showPracticeBanner
                ? layout.bannerCourses : Collections.<Course>emptyList();
        if (layout.items.isEmpty() && bannerCourses.isEmpty()) {
            throw new IOException(semesterMode
                    ? "当前课表没有可导出的课程" : "本周没有可导出的课程");
        }
        int dayCount = layout.visibleDays.size();
        int width = 600 + dayCount * 200;
        int bannerAreaHeight = bannerCourses.isEmpty() ? 0 : BANNER_HEIGHT;
        int gridTop = HEADER_HEIGHT + bannerAreaHeight;
        int gridBottom = gridTop + DAY_HEADER_HEIGHT
                + request.sectionCount * SECTION_HEIGHT;
        int height = gridBottom + FOOTER_HEIGHT;
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError error) {
            throw new IOException("设备内存不足，无法生成课表图片", error);
        }
        try {
            Canvas canvas = new Canvas(bitmap);
            drawPage(canvas, width, height, gridTop,
                    request, layout, bannerCourses, semesterMode, semesterWeeks);
            return bitmap;
        } catch (RuntimeException exception) {
            bitmap.recycle();
            throw new IOException("课表图片生成失败", exception);
        }
    }

    private static void drawPage(Canvas canvas, int width, int height, int gridTop,
                                 Request request, ScheduleExportLayout.Result layout,
                                 List<Course> bannerCourses, boolean semesterMode,
                                 int semesterWeeks) {
        canvas.drawColor(Color.rgb(246, 249, 253));
        drawHeader(canvas, width, request, layout.visibleDays,
                semesterMode, semesterWeeks);
        if (!bannerCourses.isEmpty()) {
            drawPracticeBanner(canvas, width, bannerCourses, semesterMode);
        }
        drawGrid(canvas, width, gridTop, request, layout, semesterMode);
        Paint footer = textPaint(22, Color.rgb(102, 112, 133), false);
        footer.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("由 Polaris课程表生成", width - PAGE_MARGIN,
                height - 28, footer);
    }

    private static void drawHeader(Canvas canvas, int width, Request request,
                                   List<Integer> visibleDays, boolean semesterMode,
                                   int semesterWeeks) {
        Paint title = textPaint(56, Color.rgb(23, 32, 51), true);
        float titleWidth = width - PAGE_MARGIN * 2f - 230f;
        CharSequence titleText = TextUtils.ellipsize(
                request.scheduleName.length() == 0 ? "我的课表" : request.scheduleName,
                new TextPaint(title), titleWidth, TextUtils.TruncateAt.END);
        canvas.drawText(titleText.toString(), PAGE_MARGIN, 82, title);

        StringBuilder metadata = new StringBuilder();
        if (request.semesterName.length() > 0) {
            metadata.append(request.semesterName);
        }
        if (request.schoolName.length() > 0) {
            if (metadata.length() > 0) {
                metadata.append(" · ");
            }
            metadata.append(request.schoolName);
        }
        Paint subtitle = textPaint(27, Color.rgb(102, 112, 133), false);
        CharSequence subtitleText = TextUtils.ellipsize(
                metadata.length() == 0 ? "Polaris课程表" : metadata.toString(),
                new TextPaint(subtitle), width - PAGE_MARGIN * 2f,
                TextUtils.TruncateAt.END);
        canvas.drawText(subtitleText.toString(), PAGE_MARGIN, 129, subtitle);
        canvas.drawText(semesterMode
                        ? "第 1–" + semesterWeeks + " 周 · 合并课表"
                        : weekDateRange(request, visibleDays),
                PAGE_MARGIN, 171, subtitle);

        Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        pill.setColor(Color.rgb(37, 99, 235));
        RectF pillBounds = new RectF(width - PAGE_MARGIN - 206, 42,
                width - PAGE_MARGIN, 104);
        canvas.drawRoundRect(pillBounds, 31, 31, pill);
        Paint weekText = textPaint(28, Color.WHITE, true);
        weekText.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(semesterMode ? "整学期" : "第 " + request.week + " 周",
                pillBounds.centerX(), 83, weekText);
    }

    private static void drawPracticeBanner(Canvas canvas, int width,
                                           List<Course> bannerCourses,
                                           boolean semesterMode) {
        RectF bounds = new RectF(PAGE_MARGIN, HEADER_HEIGHT + 4,
                width - PAGE_MARGIN, HEADER_HEIGHT + BANNER_HEIGHT - 8);
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(Color.rgb(231, 239, 255));
        canvas.drawRoundRect(bounds, 20, 20, background);
        Paint label = textPaint(26, Color.rgb(29, 78, 216), true);
        canvas.drawText(semesterMode ? "学期实践" : "本周实践",
                bounds.left + 22, bounds.centerY() + 9, label);
        StringBuilder names = new StringBuilder();
        int visibleNameCount = Math.min(8, bannerCourses.size());
        for (int index = 0; index < visibleNameCount; index++) {
            if (names.length() > 0) {
                names.append(" · ");
            }
            Course course = bannerCourses.get(index);
            names.append(displayName(course));
            if (semesterMode && safe(course.weeks).trim().length() > 0) {
                names.append("（").append(course.weeks.trim()).append("）");
            }
        }
        if (bannerCourses.size() > visibleNameCount) {
            names.append(" 等 ").append(bannerCourses.size()).append(" 项");
        }
        Paint namesPaint = textPaint(25, Color.rgb(51, 65, 85), false);
        float namesLeft = bounds.left + 154;
        CharSequence namesText = TextUtils.ellipsize(names.toString(),
                new TextPaint(namesPaint), bounds.right - namesLeft - 22,
                TextUtils.TruncateAt.END);
        canvas.drawText(namesText.toString(), namesLeft, bounds.centerY() + 9, namesPaint);
    }

    private static void drawGrid(Canvas canvas, int width, int gridTop, Request request,
                                 ScheduleExportLayout.Result layout,
                                 boolean semesterMode) {
        int gridLeft = PAGE_MARGIN;
        int gridRight = width - PAGE_MARGIN;
        int courseLeft = gridLeft + TIME_COLUMN_WIDTH;
        float dayWidth = (gridRight - courseLeft) / (float) layout.visibleDays.size();
        int bodyTop = gridTop + DAY_HEADER_HEIGHT;
        int bodyBottom = bodyTop + request.sectionCount * SECTION_HEIGHT;
        Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
        surface.setColor(Color.WHITE);
        canvas.drawRoundRect(new RectF(gridLeft, gridTop, gridRight, bodyBottom),
                22, 22, surface);

        Paint headerBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerBackground.setColor(Color.rgb(236, 243, 252));
        canvas.drawRect(gridLeft, gridTop, gridRight, bodyTop, headerBackground);
        Paint rowAlternate = new Paint(Paint.ANTI_ALIAS_FLAG);
        rowAlternate.setColor(Color.rgb(249, 251, 254));
        for (int section = 1; section <= request.sectionCount; section++) {
            if (section % 2 == 0) {
                float top = bodyTop + (section - 1) * SECTION_HEIGHT;
                canvas.drawRect(gridLeft, top, gridRight, top + SECTION_HEIGHT, rowAlternate);
            }
        }

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(Color.rgb(217, 226, 236));
        line.setStrokeWidth(2f);
        canvas.drawLine(courseLeft, gridTop, courseLeft, bodyBottom, line);
        for (int day = 1; day < layout.visibleDays.size(); day++) {
            float x = courseLeft + day * dayWidth;
            canvas.drawLine(x, gridTop, x, bodyBottom, line);
        }
        for (int section = 0; section <= request.sectionCount; section++) {
            float y = bodyTop + section * SECTION_HEIGHT;
            canvas.drawLine(gridLeft, y, gridRight, y, line);
        }

        Paint sectionHeader = textPaint(24, Color.rgb(102, 112, 133), true);
        sectionHeader.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("节次", gridLeft + TIME_COLUMN_WIDTH / 2f,
                gridTop + 56, sectionHeader);
        drawDayHeaders(canvas, courseLeft, gridTop, dayWidth, request,
                layout.visibleDays, semesterMode);
        drawSectionLabels(canvas, gridLeft, bodyTop, request);
        drawCourses(canvas, courseLeft, bodyTop, dayWidth, layout.items, semesterMode);
    }

    private static void drawDayHeaders(Canvas canvas, int courseLeft, int gridTop,
                                       float dayWidth, Request request,
                                       List<Integer> visibleDays,
                                       boolean semesterMode) {
        Calendar weekStart = Calendar.getInstance();
        weekStart.setTimeInMillis(request.firstWeekStartMillis);
        weekStart.add(Calendar.DATE, (request.week - 1) * 7);
        Paint dayName = textPaint(28, Color.rgb(23, 32, 51), true);
        dayName.setTextAlign(Paint.Align.CENTER);
        Paint date = textPaint(22, Color.rgb(102, 112, 133), false);
        date.setTextAlign(Paint.Align.CENTER);
        for (int index = 0; index < visibleDays.size(); index++) {
            int day = visibleDays.get(index);
            Calendar value = (Calendar) weekStart.clone();
            value.add(Calendar.DATE, day);
            float centerX = courseLeft + index * dayWidth + dayWidth / 2f;
            canvas.drawText(DAY_NAMES[day], centerX, gridTop + 39, dayName);
            canvas.drawText(semesterMode ? "全学期"
                            : (value.get(Calendar.MONTH) + 1) + "/"
                            + value.get(Calendar.DAY_OF_MONTH),
                    centerX, gridTop + 70, date);
        }
    }

    private static void drawSectionLabels(Canvas canvas, int gridLeft, int bodyTop,
                                          Request request) {
        String[] times = CourseTimeResolver.sectionTimeLabels(
                request.timeSettings, request.sectionCount);
        Paint sectionNumber = textPaint(30, Color.rgb(23, 32, 51), true);
        sectionNumber.setTextAlign(Paint.Align.CENTER);
        Paint time = textPaint(20, Color.rgb(102, 112, 133), false);
        time.setTextAlign(Paint.Align.CENTER);
        float centerX = gridLeft + TIME_COLUMN_WIDTH / 2f;
        for (int index = 0; index < request.sectionCount; index++) {
            float top = bodyTop + index * SECTION_HEIGHT;
            canvas.drawText(String.valueOf(index + 1), centerX, top + 43, sectionNumber);
            String[] range = times[index].split("\\n", 2);
            canvas.drawText(range.length > 0 ? range[0] : "", centerX, top + 76, time);
            canvas.drawText(range.length > 1 ? range[1] : "", centerX, top + 103, time);
        }
    }

    private static void drawCourses(Canvas canvas, int courseLeft, int bodyTop,
                                    float dayWidth, List<ScheduleExportLayout.Item> items,
                                    boolean semesterMode) {
        for (ScheduleExportLayout.Item item : items) {
            float laneWidth = dayWidth / item.laneCount;
            float laneGap = Math.min(COURSE_GAP, Math.max(1f, laneWidth * 0.08f));
            float left = courseLeft + item.visibleDayIndex * dayWidth
                    + item.lane * laneWidth + laneGap;
            float right = courseLeft + item.visibleDayIndex * dayWidth
                    + (item.lane + 1) * laneWidth - laneGap;
            right = Math.max(left + 1f, right);
            float top = bodyTop + (item.startSection - 1) * SECTION_HEIGHT + COURSE_GAP;
            float bottom = bodyTop + item.endSection * SECTION_HEIGHT - COURSE_GAP;
            int backgroundColor = courseColor(item.course);
            Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
            background.setColor(backgroundColor);
            RectF bounds = new RectF(left, top, right, bottom);
            canvas.drawRoundRect(bounds, 18, 18, background);
            drawCourseText(canvas, bounds, item.course,
                    readableTextColor(backgroundColor), semesterMode);
        }
    }

    private static void drawCourseText(Canvas canvas, RectF bounds, Course course,
                                       int textColor, boolean semesterMode) {
        int contentWidth = Math.max(1, (int) bounds.width() - 24);
        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(textColor);
        titlePaint.setTextSize(34);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        boolean singleSection = bounds.height() < SECTION_HEIGHT * 1.5f;
        int titleLines = singleSection ? 1 : 3;
        StaticLayout titleLayout = textLayout(
                displayName(course), titlePaint, contentWidth, titleLines, true);
        float textLeft = bounds.left + 12;
        float nextTop = bounds.top + 13;
        drawTextLayout(canvas, titleLayout, textLeft, nextTop);
        nextTop += titleLayout.getHeight() + 8;

        float remaining = bounds.bottom - 12 - nextTop;
        if (remaining < 30) {
            return;
        }
        TextPaint detailsPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        detailsPaint.setColor(withAlpha(textColor, 210));
        detailsPaint.setTextSize(26);
        if (semesterMode) {
            StaticLayout weeksLayout = textLayout(
                    "周次：" + valueOrMissing(course.weeks),
                    detailsPaint, contentWidth, 1, true);
            if (nextTop + weeksLayout.getHeight() + 6 < bounds.bottom - 43) {
                drawTextLayout(canvas, weeksLayout, textLeft, nextTop);
                nextTop += weeksLayout.getHeight() + 6;
            }
        }
        String teacherLine = "教师：" + valueOrMissing(course.teacher);
        StaticLayout teacherLayout = textLayout(
                teacherLine, detailsPaint, contentWidth, 1, true);
        float teacherTop = bounds.bottom - 12 - teacherLayout.getHeight();
        float locationHeight = teacherTop - nextTop - 6;
        if (locationHeight >= 30) {
            int locationLines = Math.max(1, Math.min(singleSection ? 2 : 4,
                    (int) Math.floor(locationHeight / 31f)));
            StaticLayout locationLayout = textLayout(
                    "地点：" + valueOrMissing(course.location),
                    detailsPaint, contentWidth, locationLines, true);
            drawTextLayout(canvas, locationLayout, textLeft, nextTop);
        }
        drawTextLayout(canvas, teacherLayout, textLeft, teacherTop);
    }

    private static StaticLayout textLayout(String text, TextPaint paint, int width,
                                           int maxLines, boolean ellipsize) {
        String value = safe(text);
        StaticLayout.Builder builder = StaticLayout.Builder.obtain(
                        value, 0, value.length(), paint, Math.max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(2f, 1f);
        if (maxLines != Integer.MAX_VALUE) {
            builder.setMaxLines(Math.max(1, maxLines));
        }
        if (ellipsize) {
            builder.setEllipsize(TextUtils.TruncateAt.END);
        }
        return builder.build();
    }

    private static void drawTextLayout(Canvas canvas, StaticLayout layout,
                                       float left, float top) {
        canvas.save();
        canvas.translate(left, top);
        layout.draw(canvas);
        canvas.restore();
    }

    private static String valueOrMissing(String value) {
        String text = safe(value).trim();
        return text.length() == 0 ? "未填写" : text;
    }

    private static String weekDateRange(Request request, List<Integer> visibleDays) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(request.firstWeekStartMillis);
        start.add(Calendar.DATE, (request.week - 1) * 7);
        Calendar end = (Calendar) start.clone();
        int lastVisibleDay = visibleDays.isEmpty() ? 4 : visibleDays.get(visibleDays.size() - 1);
        end.add(Calendar.DATE, lastVisibleDay);
        return start.get(Calendar.YEAR) + "/" + (start.get(Calendar.MONTH) + 1)
                + "/" + start.get(Calendar.DAY_OF_MONTH) + " — "
                + (end.get(Calendar.MONTH) + 1) + "/" + end.get(Calendar.DAY_OF_MONTH);
    }

    private static Paint textPaint(float size, int color, boolean bold) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT,
                bold ? Typeface.BOLD : Typeface.NORMAL));
        return paint;
    }

    private static int courseColor(Course course) {
        String value = safe(course.color).trim();
        if (value.length() > 0) {
            try {
                return Color.rgb(Color.red(Color.parseColor(value)),
                        Color.green(Color.parseColor(value)),
                        Color.blue(Color.parseColor(value)));
            } catch (IllegalArgumentException ignored) {
                // Fall back to a stable course-name color.
            }
        }
        int index = Math.abs(displayName(course).hashCode() % COURSE_PALETTE.length);
        return COURSE_PALETTE[index];
    }

    private static int readableTextColor(int background) {
        double luminance = 0.2126 * Color.red(background)
                + 0.7152 * Color.green(background)
                + 0.0722 * Color.blue(background);
        return luminance < 145 ? Color.WHITE : Color.rgb(23, 50, 77);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static String displayName(Course course) {
        String name = course == null ? "" : safe(course.name).trim();
        return name.length() == 0 ? "未命名课程" : name;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
