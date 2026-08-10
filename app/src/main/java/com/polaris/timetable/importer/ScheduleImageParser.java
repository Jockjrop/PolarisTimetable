package com.polaris.timetable.importer;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;
import com.polaris.timetable.parser.WeekRuleParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java first-stage parser for upright grid timetable screenshots. */
public final class ScheduleImageParser {
    private static final Pattern SECTION_PATTERN =
            Pattern.compile("(?:第)?([1-9]|1[0-9]|20)(?:节)?");
    private static final String RANGE_CONNECTOR = "[-‐‑‒–—﹘﹣－~～〜∼]|至";
    private static final String LIST_CONNECTOR = "[、,，;；]";
    private static final Pattern WEEK_EXPRESSION = Pattern.compile(
            "(\\d+(?:\\s*(?:" + RANGE_CONNECTOR + ")\\s*\\d+)?"
                    + "(?:\\s*" + LIST_CONNECTOR + "\\s*\\d+"
                    + "(?:\\s*(?:" + RANGE_CONNECTOR + ")\\s*\\d+)?)*"
                    + "\\s*周(?:\\s*(?:单周|双周|[（(]\\s*[单双]\\s*[）)]))?)");
    private static final Pattern PREFIXED_TEACHER =
            Pattern.compile("(?:教师|老师)\\s*[:：]\\s*(.+)");
    private static final Pattern TEACHER =
            Pattern.compile("([\\p{IsHan}A-Za-z·.]{1,16}(?:老师|教师))");
    private static final Pattern PREFIXED_LOCATION =
            Pattern.compile("(?:地点|教室|位置)\\s*[:：]\\s*(.+)");
    private static final Pattern ROOM_CODE =
            Pattern.compile("(?i)([A-Z]{1,4}\\s*-?\\s*\\d{2,4})");
    private static final String[] COLORS = {
            "#4FA4F3", "#36B889", "#956BD6", "#F29D49", "#E66F83"
    };

    private final WeekRuleParser weekRuleParser;

    public ScheduleImageParser() {
        this(new WeekRuleParser());
    }

    ScheduleImageParser(WeekRuleParser weekRuleParser) {
        this.weekRuleParser = weekRuleParser;
    }

    public ImageScheduleRecognitionResult parse(
            List<OcrTextBlock> source, int imageWidth, int imageHeight) {
        List<OcrTextBlock> blocks = cleanBlocks(source);
        if (blocks.isEmpty()) {
            return failure(blocks, "OCR 未识别到任何文字");
        }
        Layout layout = analyzeLayout(blocks, imageWidth, imageHeight);
        if (!layout.valid) {
            return new ImageScheduleRecognitionResult(
                    ImageScheduleRecognitionResult.Status.FAILURE,
                    Collections.emptyList(), blocks, layout.warnings,
                    layout.confidence, "未能可靠识别课表网格结构");
        }

        List<String> warnings = new ArrayList<>(layout.warnings);
        List<ParsedCell> cells = new ArrayList<>();
        int ignoredContentBlocks = 0;
        for (OcrTextBlock block : blocks) {
            if (isLayoutLabel(block, layout)) {
                continue;
            }
            int day = layout.dayAt(block.centerX());
            List<Integer> sections = layout.sectionsOverlapping(block.top, block.bottom);
            if (day < 0 || sections.isEmpty()) {
                continue;
            }
            ParsedCell cell = parseCell(block, day, sections.get(0),
                    sections.get(sections.size() - 1), warnings);
            if (cell == null) {
                ignoredContentBlocks++;
            } else {
                cells.add(cell);
            }
        }
        if (cells.isEmpty()) {
            warnings.add("已识别课表网格，但没有找到可确认的课程格");
            return new ImageScheduleRecognitionResult(
                    ImageScheduleRecognitionResult.Status.FAILURE,
                    Collections.emptyList(), blocks, warnings,
                    Math.min(layout.confidence, 0.49f), "未识别到可导入课程");
        }
        if (ignoredContentBlocks > 0) {
            warnings.add("有 " + ignoredContentBlocks + " 个课表区域无法判断课程名称，请检查原图");
        }

        List<StructuredCourse> courses = mergeCells(cells);
        float parsedRatio = cells.size() / (float) Math.max(1, cells.size() + ignoredContentBlocks);
        float ocrConfidence = averageConfidence(blocks);
        float confidence = clamp(
                layout.confidence * 0.55f
                        + parsedRatio * 0.30f
                        + ocrConfidence * 0.15f
                        - Math.min(0.18f, warnings.size() * 0.025f));
        ImageScheduleRecognitionResult.Status status = warnings.isEmpty()
                ? ImageScheduleRecognitionResult.Status.SUCCESS
                : ImageScheduleRecognitionResult.Status.PARTIAL;
        if (confidence < ImageScheduleRecognitionResult.MIN_IMPORT_CONFIDENCE) {
            warnings.add("整体识别置信度偏低，已禁止直接导入");
        }
        return new ImageScheduleRecognitionResult(
                status, courses, blocks, warnings, confidence,
                "本地 OCR 识别；请在确认前检查课程、教师、地点和周次");
    }

    public Layout analyzeLayout(List<OcrTextBlock> source,
                                int imageWidth, int imageHeight) {
        List<OcrTextBlock> blocks = cleanBlocks(source);
        List<String> warnings = new ArrayList<>();
        if (imageWidth <= 0 || imageHeight <= 0) {
            warnings.add("图片尺寸无效");
            return Layout.invalid(warnings);
        }

        Map<Integer, OcrTextBlock> dayHeaders = new LinkedHashMap<>();
        for (OcrTextBlock block : blocks) {
            int day = weekdayIndex(block.text);
            if (day < 0) {
                continue;
            }
            OcrTextBlock previous = dayHeaders.get(day);
            if (previous == null || block.top < previous.top) {
                dayHeaders.put(day, block);
            }
        }
        boolean hasWeekdays = true;
        for (int day = 0; day < 5; day++) {
            hasWeekdays &= dayHeaders.containsKey(day);
        }
        if (!hasWeekdays) {
            warnings.add("未完整识别周一到周五标题");
            return Layout.invalid(warnings);
        }

        int dayCount = dayHeaders.containsKey(5) && dayHeaders.containsKey(6) ? 7 : 5;
        List<Float> centers = new ArrayList<>();
        int headerBottom = 0;
        for (int day = 0; day < dayCount; day++) {
            OcrTextBlock header = dayHeaders.get(day);
            if (header == null) {
                warnings.add("星期标题不连续");
                return Layout.invalid(warnings);
            }
            centers.add(header.centerX());
            headerBottom = Math.max(headerBottom, header.bottom);
            if (day > 0 && centers.get(day) <= centers.get(day - 1)) {
                warnings.add("星期标题横向顺序异常");
                return Layout.invalid(warnings);
            }
        }
        List<ColumnRange> columns = new ArrayList<>();
        for (int day = 0; day < dayCount; day++) {
            float left = day == 0
                    ? centers.get(0) - (centers.get(1) - centers.get(0)) / 2f
                    : (centers.get(day - 1) + centers.get(day)) / 2f;
            float right = day == dayCount - 1
                    ? centers.get(day) + (centers.get(day) - centers.get(day - 1)) / 2f
                    : (centers.get(day) + centers.get(day + 1)) / 2f;
            columns.add(new ColumnRange(day, Math.max(0f, left),
                    Math.min(imageWidth, right)));
        }

        float firstCourseLeft = columns.get(0).left;
        Map<Integer, OcrTextBlock> sectionLabels = new LinkedHashMap<>();
        for (OcrTextBlock block : blocks) {
            if (block.centerX() >= firstCourseLeft || block.top <= headerBottom) {
                continue;
            }
            int section = sectionNumber(block.text);
            if (section <= 0) {
                continue;
            }
            OcrTextBlock previous = sectionLabels.get(section);
            if (previous == null || block.left < previous.left) {
                sectionLabels.put(section, block);
            }
        }
        if (sectionLabels.size() < 2) {
            warnings.add("未识别到足够的节次纵轴");
            return Layout.invalid(warnings);
        }
        List<Map.Entry<Integer, OcrTextBlock>> orderedSections =
                new ArrayList<>(sectionLabels.entrySet());
        orderedSections.sort(Comparator.comparingDouble(entry -> entry.getValue().centerY()));
        for (int index = 1; index < orderedSections.size(); index++) {
            if (orderedSections.get(index).getKey() <= orderedSections.get(index - 1).getKey()) {
                warnings.add("节次纵向顺序异常");
                return Layout.invalid(warnings);
            }
        }
        List<SectionRange> sections = new ArrayList<>();
        for (int index = 0; index < orderedSections.size(); index++) {
            float center = orderedSections.get(index).getValue().centerY();
            float top = index == 0
                    ? center - (orderedSections.get(1).getValue().centerY() - center) / 2f
                    : (orderedSections.get(index - 1).getValue().centerY() + center) / 2f;
            float bottom = index == orderedSections.size() - 1
                    ? center + (center - orderedSections.get(index - 1).getValue().centerY()) / 2f
                    : (center + orderedSections.get(index + 1).getValue().centerY()) / 2f;
            sections.add(new SectionRange(
                    orderedSections.get(index).getKey(),
                    Math.max(headerBottom, top), Math.min(imageHeight, bottom)));
        }
        float dayScore = dayCount == 7 ? 1f : 0.9f;
        float sectionScore = Math.min(1f, sections.size() / 6f);
        return new Layout(true, columns, sections, warnings,
                clamp(dayScore * 0.60f + sectionScore * 0.40f), headerBottom);
    }

    static int weekdayIndex(String text) {
        String value = firstLine(text).replaceAll("\\s+", "");
        String[] numbers = {"一", "二", "三", "四", "五", "六", "日"};
        for (int day = 0; day < numbers.length; day++) {
            String number = numbers[day];
            if (value.equals("周" + number)
                    || value.equals("星期" + number)
                    || value.equals("礼拜" + number)) {
                return day;
            }
        }
        return value.equals("周天") || value.equals("星期天") || value.equals("礼拜天")
                ? 6 : -1;
    }

    private ParsedCell parseCell(OcrTextBlock block, int day, int startSection,
                                 int endSection, List<String> warnings) {
        List<String> lines = nonEmptyLines(block.text);
        String weekText = extractWeekExpression(block.text);
        String teacher = extractTeacher(lines);
        String location = extractLocation(lines);
        String name = extractCourseName(lines, weekText, teacher, location);
        if (name.isEmpty()) {
            return null;
        }
        WeekRule weekRule = weekRuleParser.parse(weekText);
        if (weekText.isEmpty() || weekRule.type == WeekRule.Type.UNKNOWN) {
            warnings.add("“" + name + "”的周次无法确定，已标记为待检查");
        }
        if (teacher.isEmpty()) {
            warnings.add("“" + name + "”未识别到教师");
        }
        if (location.isEmpty()) {
            warnings.add("“" + name + "”未识别到地点");
        }
        return new ParsedCell(name, teacher, location, day, startSection,
                endSection, weekRule, block.text);
    }

    private List<StructuredCourse> mergeCells(List<ParsedCell> cells) {
        Map<String, MutableCourse> grouped = new LinkedHashMap<>();
        for (ParsedCell cell : cells) {
            String key = normalizeIdentity(cell.name) + "|" + normalizeIdentity(cell.teacher);
            if (cell.teacher.isEmpty()) {
                key += "|" + normalizeIdentity(cell.location);
            }
            MutableCourse course = grouped.get(key);
            if (course == null) {
                course = new MutableCourse(cell.name, cell.teacher, cell.location,
                        COLORS[grouped.size() % COLORS.length]);
                grouped.put(key, course);
            }
            course.meetings.add(new CourseMeeting(
                    cell.day, cell.startSection, cell.endSection, cell.weekRule,
                    cell.location, cell.teacher, cell.rawText));
        }
        List<StructuredCourse> courses = new ArrayList<>();
        for (MutableCourse course : grouped.values()) {
            courses.add(new StructuredCourse(
                    StableCourseId.create(), course.name, course.teacher,
                    course.location, course.meetings, "本地 OCR 图片识别",
                    "", course.color, CourseType.LECTURE));
        }
        return courses;
    }

    private boolean isLayoutLabel(OcrTextBlock block, Layout layout) {
        if (weekdayIndex(block.text) >= 0) {
            return true;
        }
        return block.centerX() < layout.columns.get(0).left
                && sectionNumber(block.text) > 0;
    }

    private int sectionNumber(String text) {
        String value = firstLine(text).replaceAll("\\s+", "");
        Matcher matcher = SECTION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String extractWeekExpression(String text) {
        Matcher matcher = WEEK_EXPRESSION.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return "";
        }
        String value = matcher.group(1).replaceAll("\\s+", " ").trim();
        return weekRuleParser.isSupportedExpression(value) ? value : "";
    }

    private String extractTeacher(List<String> lines) {
        for (String line : lines) {
            Matcher prefixed = PREFIXED_TEACHER.matcher(line);
            if (prefixed.find()) {
                return prefixed.group(1).trim();
            }
            Matcher teacher = TEACHER.matcher(line);
            if (teacher.find()) {
                return teacher.group(1).trim();
            }
        }
        return "";
    }

    private String extractLocation(List<String> lines) {
        for (String line : lines) {
            Matcher prefixed = PREFIXED_LOCATION.matcher(line);
            if (prefixed.find()) {
                return prefixed.group(1).trim();
            }
            Matcher room = ROOM_CODE.matcher(line);
            if (room.find()) {
                return room.group(1).replaceAll("\\s+", "").trim();
            }
            if (!line.contains("老师")
                    && (line.endsWith("教室") || line.endsWith("校区")
                    || line.matches(".*[楼室]$"))) {
                return line.trim();
            }
        }
        return "";
    }

    private String extractCourseName(List<String> lines, String weekText,
                                     String teacher, String location) {
        for (String line : lines) {
            String value = line.trim();
            if (value.isEmpty() || value.equals(weekText) || value.contains("周次")
                    || value.equals(teacher) || value.contains(teacher + "老师")
                    || value.equals(location) || PREFIXED_TEACHER.matcher(value).find()
                    || PREFIXED_LOCATION.matcher(value).find()
                    || WEEK_EXPRESSION.matcher(value).matches()
                    || ROOM_CODE.matcher(value).matches()) {
                continue;
            }
            return value;
        }
        return "";
    }

    private List<OcrTextBlock> cleanBlocks(List<OcrTextBlock> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<OcrTextBlock> blocks = new ArrayList<>();
        for (OcrTextBlock block : source) {
            if (block != null && !block.text.isEmpty() && block.width() > 0 && block.height() > 0) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    private float averageConfidence(List<OcrTextBlock> blocks) {
        float total = 0f;
        int count = 0;
        for (OcrTextBlock block : blocks) {
            if (block.confidence >= 0f) {
                total += block.confidence;
                count++;
            }
        }
        return count == 0 ? 0.75f : total / count;
    }

    private static List<String> nonEmptyLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : (text == null ? "" : text).split("[\\r\\n]+")) {
            String value = line.trim();
            if (!value.isEmpty()) {
                lines.add(value);
            }
        }
        return lines;
    }

    private static String firstLine(String text) {
        List<String> lines = nonEmptyLines(text);
        return lines.isEmpty() ? "" : lines.get(0);
    }

    private static String normalizeIdentity(String text) {
        return (text == null ? "" : text).replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private ImageScheduleRecognitionResult failure(List<OcrTextBlock> blocks, String warning) {
        return new ImageScheduleRecognitionResult(
                ImageScheduleRecognitionResult.Status.FAILURE,
                Collections.emptyList(), blocks, Collections.singletonList(warning),
                0f, warning);
    }

    public static final class ColumnRange {
        public final int day;
        public final float left;
        public final float right;

        ColumnRange(int day, float left, float right) {
            this.day = day;
            this.left = left;
            this.right = right;
        }

        public boolean contains(float x) {
            return x >= left && x < right;
        }
    }

    public static final class SectionRange {
        public final int section;
        public final float top;
        public final float bottom;

        SectionRange(int section, float top, float bottom) {
            this.section = section;
            this.top = top;
            this.bottom = bottom;
        }
    }

    public static final class Layout {
        public final boolean valid;
        public final List<ColumnRange> columns;
        public final List<SectionRange> sections;
        public final List<String> warnings;
        public final float confidence;
        public final int headerBottom;

        Layout(boolean valid, List<ColumnRange> columns,
               List<SectionRange> sections, List<String> warnings,
               float confidence, int headerBottom) {
            this.valid = valid;
            this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
            this.sections = Collections.unmodifiableList(new ArrayList<>(sections));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
            this.confidence = confidence;
            this.headerBottom = headerBottom;
        }

        static Layout invalid(List<String> warnings) {
            return new Layout(false, Collections.emptyList(), Collections.emptyList(),
                    warnings, 0f, 0);
        }

        public int dayAt(float centerX) {
            for (ColumnRange column : columns) {
                if (column.contains(centerX)) {
                    return column.day;
                }
            }
            return -1;
        }

        public List<Integer> sectionsOverlapping(int top, int bottom) {
            if (bottom <= top) {
                return Collections.emptyList();
            }
            Set<Integer> matched = new LinkedHashSet<>();
            for (SectionRange section : sections) {
                float overlap = Math.min(bottom, section.bottom) - Math.max(top, section.top);
                float rowHeight = Math.max(1f, section.bottom - section.top);
                float blockHeight = Math.max(1f, bottom - top);
                if (overlap > 0f
                        && (overlap / rowHeight >= 0.20f
                        || overlap / blockHeight >= 0.45f)) {
                    matched.add(section.section);
                }
            }
            return new ArrayList<>(matched);
        }
    }

    private static final class ParsedCell {
        final String name;
        final String teacher;
        final String location;
        final int day;
        final int startSection;
        final int endSection;
        final WeekRule weekRule;
        final String rawText;

        ParsedCell(String name, String teacher, String location, int day,
                   int startSection, int endSection, WeekRule weekRule, String rawText) {
            this.name = name;
            this.teacher = teacher;
            this.location = location;
            this.day = day;
            this.startSection = startSection;
            this.endSection = endSection;
            this.weekRule = weekRule;
            this.rawText = rawText;
        }
    }

    private static final class MutableCourse {
        final String name;
        final String teacher;
        final String location;
        final String color;
        final List<CourseMeeting> meetings = new ArrayList<>();

        MutableCourse(String name, String teacher, String location, String color) {
            this.name = name;
            this.teacher = teacher;
            this.location = location;
            this.color = color;
        }
    }
}
