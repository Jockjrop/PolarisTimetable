package com.polaris.timetable;

import android.content.Context;
import android.net.Uri;

import com.polaris.timetable.model.CourseStructureMapper;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.ParseError;
import com.polaris.timetable.model.ParseResult;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;
import com.polaris.timetable.parser.ParseDiagnostics;
import com.polaris.timetable.parser.SchoolParserModel;
import com.polaris.timetable.parser.SemesterTextExtractor;
import com.polaris.timetable.parser.WeekRuleParser;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScheduleParser {
    private static final String[] WEEKDAYS = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
    private static final String[] SHORT_WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final String[] SINGLE_WEEKDAYS = {"一", "二", "三", "四", "五", "六", "日"};
    private static final String[] LOCATION_LABELS = {"场地:", "场地：", "地点:", "地点：", "教室:", "教室：", "校区:", "校区：", "上课地点:", "上课地点："};
    private static final String[] TEACHER_LABELS = {"教师:", "教师：", "任课教师:", "任课教师：", "主讲教师:", "主讲教师：", "老师:", "老师："};
    private static final Pattern SECTION_PATTERN = Pattern.compile("[（(](\\d+)\\s*-\\s*(\\d+)\\s*节?[）)]\\s*([^/\\n]*)");
    private static final Pattern LOOSE_SECTION_PATTERN = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)\\s*节");
    private static final Pattern HDU_SECTION_PATTERN = Pattern.compile("[（(](\\d+)\\s*-\\s*(\\d+)\\s*节[）)]\\s*([^/\\n]*)");
    private static final Pattern XAUT_SECTION_PATTERN = Pattern.compile("(\\d+)(?:\\s*-\\s*(\\d+))*\\s*节");
    private static final Pattern XAUT_WEEK_SECTION_PATTERN = Pattern.compile("(\\d+(?:\\s*-\\s*\\d+)?)\\s*\\(\\[周\\]\\)\\s*\\[\\s*(\\d{1,2}(?:\\s*-\\s*\\d{1,2})*)");
    private static final Pattern WEEK_PATTERN = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)\\s*周?(?:\\s*[（(](单|双)[）)])?|全周|项目周");
    private static final Pattern XUPT_FOOTER_ITEM_PATTERN = Pattern.compile(
            "([^;；]+?)([◇●])\\s*([^()（）/;；]+?)\\s*[（(]\\s*共\\s*\\d+\\s*周\\s*[）)]\\s*/\\s*"
                    + "((?:\\d+\\s*-\\s*\\d+|\\d+)\\s*周(?:\\s*[（(](?:单|双)[）)])?|项目周)");
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile("(\\d{1,2})\\s*[:：]\\s*(\\d{2})\\s*-\\s*(\\d{1,2})\\s*[:：]\\s*(\\d{2})");
    private static final Pattern COURSE_META_PATTERN = Pattern.compile(".*(学时|总学时|学分|课程性质|课程属性|考核方式|课程编号|课程序号|容量|人数|起止周|上课班级)\\s*[:：]?.*");
    private static final Pattern ROOM_PATTERN = Pattern.compile("([A-Z]\\-?\\d{2,4}[A-Z]?|[A-Z]楼\\d{2,4}|[A-Z]座\\d{2,4}|\\d{1,2}号楼\\d{0,4}|第?\\d{1,2}教学楼\\d{0,4}|第?\\d{1,2}教研楼[东西南北中]?\\d{0,4}(?:-\\d{2,4})*|[一二三四五六七八九十]号楼\\d{0,4}|实验楼\\d{0,4}|教学楼\\d{0,4}|体育馆[^/\\s]*|图书馆\\d{0,4}|体育场|操场|机房\\d{0,4}|课外实践不在教室|未排地点)");
    private static final Pattern TEACHER_GUESS_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5·]{2,5}$");
    private final WeekRuleParser weekRuleParser = new WeekRuleParser();

    public List<Course> parse(Context context, Uri uri) throws Exception {
        return parseDetailed(context, uri).courses;
    }

    public ParseResult parseDetailed(Context context, Uri uri) throws Exception {
        return parseDetailed(context, uri, SchoolParserModel.XUPT);
    }

    public ParseResult parseDetailed(Context context, Uri uri, SchoolParserModel parserModel) throws Exception {
        ParseDiagnostics diagnostics = new ParseDiagnostics();
        PDFBoxResourceLoader.init(context);
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             PDDocument document = PDDocument.load(input)) {
            diagnostics.info("opened PDF pages=" + document.getNumberOfPages());
            PositionStripper stripper = new PositionStripper();
            stripper.setSortByPosition(true);
            stripper.getText(document);
            diagnostics.info("extracted text blocks=" + stripper.blocks.size());
            if (parserModel == SchoolParserModel.XAUT) {
                diagnostics.info("parser model=" + parserModel.label);
                return parseXautBlocks(stripper.blocks, diagnostics, document.getNumberOfPages());
            }
            if (parserModel == SchoolParserModel.HDU) {
                diagnostics.info("parser model=" + parserModel.label);
                return parseHduBlocks(stripper.blocks, diagnostics, document.getNumberOfPages());
            }
            diagnostics.info("parser model=" + SchoolParserModel.XUPT.label);
            return parseBlocks(stripper.blocks, diagnostics, document.getNumberOfPages());
        }
    }

    private ParseResult parseHduBlocks(List<TextBlock> sourceBlocks, ParseDiagnostics diagnostics, int pageCount) {
        List<TextBlock> blocks = normalizeBlocks(sourceBlocks);
        if (blocks.isEmpty()) {
            diagnostics.error(ParseError.Code.NO_TEXT_FOUND, "PDF 中没有可提取文字", 0, "");
            return new ParseResult(false, Collections.emptyList(), diagnostics.errors(), diagnostics.text(), pageCount);
        }

        Map<Integer, Float> dayCenters = findDayCenters(blocks, diagnostics);
        diagnostics.info("hdu day columns=" + dayCenters);

        List<CourseSeed> seeds = findHduCourseSeeds(blocks, dayCenters, diagnostics);
        diagnostics.info("hdu course seeds=" + seeds.size());
        if (seeds.isEmpty()) {
            diagnostics.error(ParseError.Code.SECTION_NOT_FOUND,
                    "没有识别到杭州电子科技大学课表的周次节次文本", 0, preview(blocks));
            return new ParseResult(false, Collections.emptyList(), diagnostics.errors(), diagnostics.text(), pageCount);
        }

        List<Course> courses = new ArrayList<>();
        for (CourseSeed seed : seeds) {
            String cellText = collectCellText(blocks, seeds, seed, dayCenters);
            ParsedCell parsed = parseHduCell(seed, cellText, blocks, dayCenters, diagnostics);
            courses.add(new Course(seed.day, parsed.start, parsed.end, parsed.name, parsed.weeks,
                    parsed.location, parsed.teacher, cellText, parsed.credit, "", parsed.courseType));
            diagnostics.info("hdu course day=" + seed.day + " sections=" + parsed.start + "-" + parsed.end
                    + " name=" + parsed.name + " room=" + parsed.location);
        }

        Collections.sort(courses, courseComparator());
        List<Course> merged = mergeDuplicates(courses);
        diagnostics.info("hdu courses beforeMerge=" + courses.size() + " afterMerge=" + merged.size());
        List<StructuredCourse> structuredCourses = new CourseStructureMapper().fromLegacyCourses(merged);
        diagnostics.info("hdu structured courses=" + structuredCourses.size());
        String classTimeConfig = extractClassTimeConfig(blocks, diagnostics);
        String semesterName = extractSemesterName(blocks, diagnostics);
        return new ParseResult(!merged.isEmpty(), merged, structuredCourses,
                diagnostics.errors(), diagnostics.text(), pageCount, classTimeConfig, semesterName);
    }

    private ParseResult parseXautBlocks(List<TextBlock> sourceBlocks, ParseDiagnostics diagnostics, int pageCount) {
        List<TextBlock> blocks = normalizeBlocks(sourceBlocks);
        if (blocks.isEmpty()) {
            diagnostics.error(ParseError.Code.NO_TEXT_FOUND, "PDF 中没有可提取文字", 0, "");
            return new ParseResult(false, Collections.emptyList(), diagnostics.errors(), diagnostics.text(), pageCount);
        }

        Map<Integer, Float> dayCenters = findDayCenters(blocks, diagnostics);
        List<CourseSeed> seeds = findXautCourseSeeds(blocks, dayCenters, diagnostics);
        diagnostics.info("xaut course seeds=" + seeds.size());
        if (seeds.isEmpty()) {
            diagnostics.error(ParseError.Code.SECTION_NOT_FOUND,
                    "没有识别到西安理工大学课表的周次节次文本", 0, preview(blocks));
            return new ParseResult(false, Collections.emptyList(), diagnostics.errors(), diagnostics.text(), pageCount);
        }

        List<Course> courses = new ArrayList<>();
        for (CourseSeed seed : seeds) {
            String cellText = collectCellText(blocks, seeds, seed, dayCenters);
            ParsedCell parsed = parseXautCell(seed, cellText, diagnostics);
            courses.add(new Course(seed.day, parsed.start, parsed.end, parsed.name, parsed.weeks,
                    parsed.location, parsed.teacher, cellText, parsed.credit, "", parsed.courseType));
            diagnostics.info("xaut course day=" + seed.day + " sections=" + parsed.start + "-" + parsed.end
                    + " name=" + parsed.name + " room=" + parsed.location);
        }

        Collections.sort(courses, courseComparator());
        List<Course> merged = mergeDuplicates(courses);
        List<StructuredCourse> structuredCourses = new CourseStructureMapper().fromLegacyCourses(merged);
        diagnostics.info("xaut courses beforeMerge=" + courses.size() + " afterMerge=" + merged.size());
        String classTimeConfig = extractClassTimeConfig(blocks, diagnostics);
        String semesterName = extractSemesterName(blocks, diagnostics);
        return new ParseResult(!merged.isEmpty(), merged, structuredCourses,
                diagnostics.errors(), diagnostics.text(), pageCount, classTimeConfig, semesterName);
    }

    private List<CourseSeed> findXautCourseSeeds(List<TextBlock> blocks, Map<Integer, Float> dayCenters,
                                                 ParseDiagnostics diagnostics) {
        List<CourseSeed> seeds = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            TextBlock block = blocks.get(i);
            Matcher matcher = XAUT_WEEK_SECTION_PATTERN.matcher(compact(block.text));
            if (!matcher.find()) {
                continue;
            }
            int day = nearestDay(block.centerX(), dayCenters);
            if (day < 0) {
                diagnostics.warning(ParseError.Code.DAY_COLUMNS_NOT_FOUND,
                        "周次节次块无法匹配到星期列", block.page, block.text);
                continue;
            }
            int[] sections = parseXautSections(block.text);
            String weeks = normalizeXautWeeks(matcher.group(1));
            seeds.add(new CourseSeed(i, block.page, day, sections[0], sections[1], weeks, block.y));
        }
        Collections.sort(seeds, courseSeedComparator());
        return seeds;
    }

    private List<CourseSeed> findHduCourseSeeds(List<TextBlock> blocks, Map<Integer, Float> dayCenters,
                                                ParseDiagnostics diagnostics) {
        List<CourseSeed> seeds = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            TextBlock block = blocks.get(i);
            Matcher matcher = HDU_SECTION_PATTERN.matcher(block.text);
            if (!matcher.find()) {
                continue;
            }
            int day = nearestDay(block.centerX(), dayCenters);
            if (day < 0) {
                diagnostics.warning(ParseError.Code.DAY_COLUMNS_NOT_FOUND,
                        "杭电节次块无法匹配到星期列", block.page, block.text);
                continue;
            }
            int start = parseInt(matcher.group(1), 1);
            int end = parseInt(matcher.group(2), start);
            String weeks = cleanupWeeks(matcher.group(3));
            seeds.add(new CourseSeed(i, block.page, day, Math.min(start, end), Math.max(start, end), weeks, block.y));
        }
        Collections.sort(seeds, courseSeedComparator());
        return seeds;
    }

    private ParsedCell parseXautCell(CourseSeed seed, String cellText, ParseDiagnostics diagnostics) {
        ParsedCell parsed = new ParsedCell();
        int[] sections = parseXautSections(compact(cellText));
        parsed.start = sections[0] > 0 ? sections[0] : seed.start;
        parsed.end = sections[1] > 0 ? sections[1] : seed.end;
        parsed.weeks = normalizeWeekText(seed.weeks.length() == 0 ? extractXautWeeks(cellText) : seed.weeks);

        List<String> parts = usefulXautParts(cellText);
        int weekIndex = xautWeekPartIndex(parts);
        if (weekIndex > 0) {
            parsed.teacher = cleanupMarker(parts.get(weekIndex - 1));
        }
        if (weekIndex > 1) {
            parsed.name = cleanupXautName(parts.get(weekIndex - 2));
            if (weekIndex > 2 && isXautContinuationName(parts.get(weekIndex - 3))) {
                parsed.name = cleanupXautName(parts.get(weekIndex - 3) + parts.get(weekIndex - 2));
            }
        }
        for (int i = weekIndex + 1; i < parts.size(); i++) {
            String candidate = cleanupMarker(parts.get(i));
            if (candidate.length() > 0 && !isXautWeekSectionText(candidate)) {
                parsed.location = joinXautLocation(candidate, i + 1 < parts.size() ? parts.get(i + 1) : "");
                break;
            }
        }
        String xautLocation = extractXautLocation(cellText);
        if (xautLocation.length() > 0) {
            parsed.location = xautLocation;
        }
        if (parsed.name.length() == 0) {
            parsed.name = "未命名课程";
            diagnostics.warning(ParseError.Code.COURSE_NAME_LOW_CONFIDENCE,
                    "课程名置信度较低", seed.page, cellText);
        }
        if (parsed.location.length() == 0) {
            diagnostics.warning(ParseError.Code.LOCATION_NOT_FOUND, "未识别到课程地点", seed.page, cellText);
        }
        if (parsed.teacher.length() == 0) {
            diagnostics.warning(ParseError.Code.TEACHER_NOT_FOUND, "未识别到任课教师", seed.page, cellText);
        }
        parsed.credit = extractCredit(cellText);
        return parsed;
    }

    private ParsedCell parseHduCell(CourseSeed seed, String cellText, List<TextBlock> blocks,
                                    Map<Integer, Float> dayCenters, ParseDiagnostics diagnostics) {
        ParsedCell parsed = parseCell(seed, cellText, blocks, dayCenters, diagnostics);
        String hduLocation = extractHduLocation(cellText);
        if (hduLocation.length() > 0) {
            parsed.location = hduLocation;
        } else {
            parsed.location = cleanupHduLocation(parsed.location);
        }
        String hduTeacher = extractHduTeacher(cellText);
        if (hduTeacher.length() > 0) {
            parsed.teacher = hduTeacher;
        } else {
            parsed.teacher = "";
        }
        if (parsed.weeks.length() == 0 || "周次见PDF".equals(parsed.weeks)) {
            parsed.weeks = normalizeWeekText(extractWeeks(cellText));
        }
        return parsed;
    }

    private ParseResult parseBlocks(List<TextBlock> sourceBlocks, ParseDiagnostics diagnostics, int pageCount) {
        List<TextBlock> blocks = normalizeBlocks(sourceBlocks);
        if (blocks.isEmpty()) {
            diagnostics.error(ParseError.Code.NO_TEXT_FOUND, "PDF 中没有可提取文字", 0, "");
            return new ParseResult(false, Collections.emptyList(), diagnostics.errors(), diagnostics.text(), pageCount);
        }

        Map<Integer, Float> dayCenters = findDayCenters(blocks, diagnostics);
        diagnostics.info("day columns=" + dayCenters);

        List<CourseSeed> seeds = findCourseSeeds(blocks, dayCenters, diagnostics);
        diagnostics.info("course seeds=" + seeds.size());
        List<Course> footerCourses = extractXuptFooterCourses(joinBlockText(blocks));
        diagnostics.info("xupt footer practice courses=" + footerCourses.size());
        if (seeds.isEmpty() && footerCourses.isEmpty()) {
            diagnostics.error(ParseError.Code.SECTION_NOT_FOUND, "没有识别到课程节次文本", 0, preview(blocks));
            return new ParseResult(false, Collections.emptyList(), diagnostics.errors(), diagnostics.text(), pageCount);
        }

        List<Course> courses = new ArrayList<>(footerCourses);
        for (CourseSeed seed : seeds) {
            String cellText = collectCellText(blocks, seeds, seed, dayCenters);
            ParsedCell parsed = parseCell(seed, cellText, blocks, dayCenters, diagnostics);
            courses.add(new Course(seed.day, parsed.start, parsed.end, parsed.name, parsed.weeks,
                    parsed.location, parsed.teacher, cellText, parsed.credit, "", parsed.courseType));
            diagnostics.info("course day=" + seed.day + " sections=" + parsed.start + "-" + parsed.end
                    + " name=" + parsed.name + " room=" + parsed.location);
        }

        Collections.sort(courses, courseComparator());
        List<Course> merged = mergeDuplicates(courses);
        diagnostics.info("courses beforeMerge=" + courses.size() + " afterMerge=" + merged.size());
        List<StructuredCourse> structuredCourses = new CourseStructureMapper().fromLegacyCourses(merged);
        diagnostics.info("structured courses=" + structuredCourses.size());
        String classTimeConfig = extractClassTimeConfig(blocks, diagnostics);
        String semesterName = extractSemesterName(blocks, diagnostics);
        return new ParseResult(!merged.isEmpty(), merged, structuredCourses,
                diagnostics.errors(), diagnostics.text(), pageCount, classTimeConfig, semesterName);
    }

    private String extractSemesterName(List<TextBlock> blocks, ParseDiagnostics diagnostics) {
        String semesterName = SemesterTextExtractor.extract(joinBlockText(blocks));
        diagnostics.info(semesterName.length() == 0
                ? "semester name not found in PDF"
                : "semester name=" + semesterName);
        return semesterName;
    }

    private String joinBlockText(List<TextBlock> blocks) {
        StringBuilder text = new StringBuilder();
        for (TextBlock block : blocks) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(block.text);
        }
        return text.toString();
    }

    private List<Course> extractXuptFooterCourses(String documentText) {
        List<Course> courses = new ArrayList<>();
        String text = clean(documentText);
        Matcher sectionStart = Pattern.compile("实践课程\\s*[:：]").matcher(text);
        if (!sectionStart.find()) {
            return courses;
        }
        String footer = text.substring(sectionStart.end());
        Matcher sectionEnd = Pattern.compile("○\\s*[:：]\\s*网络|打印时间").matcher(footer);
        if (sectionEnd.find()) {
            footer = footer.substring(0, sectionEnd.start());
        }

        Matcher item = XUPT_FOOTER_ITEM_PATTERN.matcher(footer);
        while (item.find()) {
            String name = cleanupMarker(clean(item.group(1)));
            String teacher = cleanupMarker(clean(item.group(3)));
            String weeks = normalizeWeekText(item.group(4));
            CourseType courseType = CourseType.fromMarker(item.group(2).charAt(0));
            if (name.length() < 2 || !courseType.supportsBannerOnly()) {
                continue;
            }
            courses.add(new Course(-1, 0, 0, name, weeks, "", teacher,
                    item.group(), "", "", courseType));
        }
        return courses;
    }

    private List<TextBlock> normalizeBlocks(List<TextBlock> sourceBlocks) {
        List<TextBlock> blocks = new ArrayList<>();
        for (TextBlock block : sourceBlocks) {
            String text = clean(block.text);
            if (text.length() > 0) {
                blocks.add(new TextBlock(text, block.page, block.x, block.y, block.width, block.height));
            }
        }
        Collections.sort(blocks, textBlockComparator());
        return blocks;
    }

    private Comparator<Course> courseComparator() {
        return new Comparator<Course>() {
            @Override
            public int compare(Course first, Course second) {
                int byDay = Integer.compare(first.day, second.day);
                if (byDay != 0) {
                    return byDay;
                }
                int bySection = Integer.compare(first.startSection, second.startSection);
                if (bySection != 0) {
                    return bySection;
                }
                return first.name.compareTo(second.name);
            }
        };
    }

    private Comparator<CourseSeed> courseSeedComparator() {
        return new Comparator<CourseSeed>() {
            @Override
            public int compare(CourseSeed first, CourseSeed second) {
                int byPage = Integer.compare(first.page, second.page);
                if (byPage != 0) {
                    return byPage;
                }
                int byDay = Integer.compare(first.day, second.day);
                return byDay != 0 ? byDay : Float.compare(first.y, second.y);
            }
        };
    }

    private Comparator<TextBlock> textBlockComparator() {
        return new Comparator<TextBlock>() {
            @Override
            public int compare(TextBlock first, TextBlock second) {
                int byPage = Integer.compare(first.page, second.page);
                if (byPage != 0) {
                    return byPage;
                }
                int byY = Float.compare(first.y, second.y);
                return byY != 0 ? byY : Float.compare(first.x, second.x);
            }
        };
    }

    private Comparator<TextBlock> textBlockPositionComparator() {
        return new Comparator<TextBlock>() {
            @Override
            public int compare(TextBlock first, TextBlock second) {
                int byY = Float.compare(first.y, second.y);
                return byY != 0 ? byY : Float.compare(first.x, second.x);
            }
        };
    }

    private List<CourseSeed> findCourseSeeds(List<TextBlock> blocks, Map<Integer, Float> dayCenters,
                                             ParseDiagnostics diagnostics) {
        List<CourseSeed> seeds = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            TextBlock block = blocks.get(i);
            Matcher matcher = SECTION_PATTERN.matcher(block.text);
            if (!matcher.find()) {
                matcher = LOOSE_SECTION_PATTERN.matcher(block.text);
                if (!matcher.find()) {
                    continue;
                }
            }
            int day = nearestDay(block.centerX(), dayCenters);
            if (day < 0) {
                diagnostics.warning(ParseError.Code.DAY_COLUMNS_NOT_FOUND,
                        "节次块无法匹配到星期列", block.page, block.text);
                continue;
            }
            int start = parseInt(matcher.group(1), 1);
            int end = parseInt(matcher.group(2), start);
            String weeks = matcher.groupCount() >= 3 ? cleanupWeeks(matcher.group(3)) : extractWeeks(block.text);
            seeds.add(new CourseSeed(i, block.page, day, Math.min(start, end), Math.max(start, end), weeks, block.y));
        }
        Collections.sort(seeds, courseSeedComparator());
        return seeds;
    }

    private ParsedCell parseCell(CourseSeed seed, String cellText, List<TextBlock> blocks,
                                 Map<Integer, Float> dayCenters, ParseDiagnostics diagnostics) {
        ParsedCell parsed = new ParsedCell();
        parsed.start = seed.start;
        parsed.end = seed.end;
        parsed.weeks = normalizeWeekText(seed.weeks.length() == 0 ? extractWeeks(cellText) : seed.weeks);
        parsed.courseType = extractCourseType(cellText);
        parsed.name = extractName(cellText);
        if (parsed.name.length() == 0) {
            parsed.name = fallbackName(blocks, seed, dayCenters);
            diagnostics.warning(ParseError.Code.COURSE_NAME_LOW_CONFIDENCE,
                    "课程名置信度较低，使用邻近文本", seed.page, cellText);
        }
        if (parsed.name.length() == 0) {
            parsed.name = "未命名课程";
        }

        parsed.location = extractLocation(cellText);
        parsed.teacher = extractTeacher(cellText, parsed.name, parsed.location);
        parsed.credit = extractCredit(cellText);
        if (parsed.location.length() == 0) {
            diagnostics.warning(ParseError.Code.LOCATION_NOT_FOUND, "未识别到课程地点", seed.page, cellText);
        }
        if (parsed.teacher.length() == 0) {
            diagnostics.warning(ParseError.Code.TEACHER_NOT_FOUND, "未识别到任课教师", seed.page, cellText);
        }
        return parsed;
    }

    private String collectCellText(List<TextBlock> blocks, List<CourseSeed> seeds, CourseSeed seed,
                                   Map<Integer, Float> dayCenters) {
        float top = seed.y - 42f;
        float bottom = seed.y + Math.max(120f, (seed.end - seed.start + 1) * 54f);
        for (CourseSeed other : seeds) {
            if (other == seed || other.page != seed.page || other.day != seed.day) {
                continue;
            }
            if (other.y < seed.y && other.y > top) {
                top = other.y + 8f;
            } else if (other.y > seed.y && other.y < bottom) {
                bottom = other.y - 8f;
            }
        }

        List<TextBlock> cellBlocks = new ArrayList<>();
        for (TextBlock block : blocks) {
            if (block.page != seed.page || block.y < top || block.y > bottom) {
                continue;
            }
            if (nearestDay(block.centerX(), dayCenters) == seed.day && !isTableChrome(block.text)) {
                cellBlocks.add(block);
            }
        }
        Collections.sort(cellBlocks, textBlockPositionComparator());

        StringBuilder builder = new StringBuilder();
        for (TextBlock block : cellBlocks) {
            String text = clean(block.text);
            if (text.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(text);
        }
        return clean(builder.toString());
    }

    private Map<Integer, Float> findDayCenters(List<TextBlock> blocks, ParseDiagnostics diagnostics) {
        Map<Integer, Float> centers = new LinkedHashMap<>();
        for (TextBlock block : blocks) {
            String text = compact(block.text);
            for (int i = 0; i < WEEKDAYS.length; i++) {
                if (text.contains(WEEKDAYS[i]) || text.contains(SHORT_WEEKDAYS[i])) {
                    centers.put(i, block.centerX());
                }
            }
        }
        if (centers.size() >= 2 && hasUsefulSpread(centers)) {
            return completeCenters(centers);
        }
        centers = findSingleCharacterDayCenters(blocks);
        if (centers.size() >= 5 && hasUsefulSpread(centers)) {
            diagnostics.info("day columns from single-character headers=" + centers);
            return completeCenters(centers);
        }
        diagnostics.warning(ParseError.Code.DAY_COLUMNS_NOT_FOUND,
                "星期标题识别不完整或跨度异常，使用页面宽度估算列中心", 0, centers.toString());

        float pageWidth = 595f;
        for (TextBlock block : blocks) {
            pageWidth = Math.max(pageWidth, block.x + block.width + 24f);
        }
        centers.clear();
        float start = pageWidth * 0.16f;
        float col = pageWidth * 0.115f;
        for (int i = 0; i < WEEKDAYS.length; i++) {
            centers.put(i, start + col * i);
        }
        return centers;
    }

    private Map<Integer, Float> findSingleCharacterDayCenters(List<TextBlock> blocks) {
        Map<Integer, Float> best = new LinkedHashMap<>();
        for (TextBlock anchor : blocks) {
            Map<Integer, Float> row = new LinkedHashMap<>();
            for (TextBlock block : blocks) {
                if (block.page != anchor.page || Math.abs(block.y - anchor.y) > 8f) {
                    continue;
                }
                String text = compact(block.text);
                for (int i = 0; i < SINGLE_WEEKDAYS.length; i++) {
                    if (SINGLE_WEEKDAYS[i].equals(text)) {
                        row.put(i, block.centerX());
                    }
                }
            }
            if (row.size() > best.size()) {
                best = row;
            }
        }
        return best;
    }

    private Map<Integer, Float> completeCenters(Map<Integer, Float> partial) {
        List<Integer> days = new ArrayList<>(partial.keySet());
        Collections.sort(days);
        float totalStep = 0f;
        int stepCount = 0;
        for (int i = 1; i < days.size(); i++) {
            int previous = days.get(i - 1);
            int current = days.get(i);
            totalStep += (partial.get(current) - partial.get(previous)) / (current - previous);
            stepCount++;
        }
        float step = stepCount == 0 ? 62f : totalStep / stepCount;
        int anchorDay = days.get(0);
        float anchor = partial.get(anchorDay);
        Map<Integer, Float> centers = new LinkedHashMap<>();
        for (int i = 0; i < WEEKDAYS.length; i++) {
            centers.put(i, partial.containsKey(i) ? partial.get(i) : anchor + (i - anchorDay) * step);
        }
        return centers;
    }

    private boolean hasUsefulSpread(Map<Integer, Float> centers) {
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        for (Float center : centers.values()) {
            min = Math.min(min, center);
            max = Math.max(max, center);
        }
        return max - min > 80f;
    }

    private int nearestDay(float x, Map<Integer, Float> centers) {
        int bestDay = -1;
        float bestDistance = Float.MAX_VALUE;
        float step = estimatedStep(centers);
        for (Map.Entry<Integer, Float> entry : centers.entrySet()) {
            float distance = Math.abs(x - entry.getValue());
            if (distance < bestDistance) {
                bestDistance = distance;
                bestDay = entry.getKey();
            }
        }
        return bestDistance <= Math.max(42f, step * 0.58f) ? bestDay : -1;
    }

    private float estimatedStep(Map<Integer, Float> centers) {
        List<Float> values = new ArrayList<>(centers.values());
        Collections.sort(values);
        float total = 0f;
        int count = 0;
        for (int i = 1; i < values.size(); i++) {
            float gap = values.get(i) - values.get(i - 1);
            if (gap > 0f) {
                total += gap;
                count++;
            }
        }
        return count == 0 ? 62f : total / count;
    }

    private String extractName(String cellText) {
        String beforeSection = cellText;
        Matcher matcher = SECTION_PATTERN.matcher(cellText);
        if (matcher.find()) {
            beforeSection = cellText.substring(0, matcher.start());
        } else {
            Matcher loose = LOOSE_SECTION_PATTERN.matcher(cellText);
            if (loose.find()) {
                beforeSection = cellText.substring(0, loose.start());
            }
        }
        String[] parts = beforeSection.split("/");
        StringBuilder merged = new StringBuilder();
        for (String part : parts) {
            String candidate = cleanupCourseName(part);
            if (isCourseName(candidate)) {
                merged.append(candidate);
            }
        }
        String mergedName = cleanupCourseName(merged.toString());
        if (isCourseName(mergedName)) {
            return mergedName;
        }
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = cleanupCourseName(parts[i]);
            if (isCourseName(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private CourseType extractCourseType(String cellText) {
        String beforeSection = cellText == null ? "" : cellText;
        Matcher section = SECTION_PATTERN.matcher(beforeSection);
        if (section.find()) {
            beforeSection = beforeSection.substring(0, section.start());
        } else {
            Matcher looseSection = LOOSE_SECTION_PATTERN.matcher(beforeSection);
            if (looseSection.find()) {
                beforeSection = beforeSection.substring(0, looseSection.start());
            }
        }
        for (int index = beforeSection.length() - 1; index >= 0; index--) {
            char marker = beforeSection.charAt(index);
            if (marker == '◆' || marker == '◇' || marker == '●' || marker == '○') {
                return CourseType.fromMarker(marker);
            }
        }
        return CourseType.LECTURE;
    }

    private String fallbackName(List<TextBlock> blocks, CourseSeed seed, Map<Integer, Float> centers) {
        TextBlock interval = blocks.get(seed.blockIndex);
        TextBlock best = null;
        float bestDistance = Float.MAX_VALUE;
        for (TextBlock candidate : blocks) {
            if (candidate.page != seed.page || nearestDay(candidate.centerX(), centers) != seed.day) {
                continue;
            }
            if (candidate.y >= interval.y || interval.y - candidate.y > 58f) {
                continue;
            }
            String name = cleanupCourseName(candidate.text);
            if (!isCourseName(name)) {
                continue;
            }
            float distance = interval.y - candidate.y;
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best == null ? "" : cleanupCourseName(best.text);
    }

    private String extractWeeks(String text) {
        Matcher matcher = WEEK_PATTERN.matcher(clean(text));
        return matcher.find() ? matcher.group() : "周次见PDF";
    }

    private int[] parseXautSections(String text) {
        String value = compact(text).replace("/", "");
        Matcher matcher = XAUT_WEEK_SECTION_PATTERN.matcher(value);
        if (matcher.find()) {
            return parseSectionChain(matcher.group(2));
        }
        matcher = XAUT_SECTION_PATTERN.matcher(value);
        if (matcher.find()) {
            return parseSectionChain(matcher.group());
        }
        return new int[]{0, 0};
    }

    private int[] parseSectionChain(String text) {
        Matcher matcher = Pattern.compile("\\d{1,2}").matcher(text == null ? "" : text);
        int min = Integer.MAX_VALUE;
        int max = 0;
        while (matcher.find()) {
            int value = parseInt(matcher.group(), 0);
            if (value > 0) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        return max == 0 ? new int[]{0, 0} : new int[]{min, max};
    }

    private String normalizeXautWeeks(String text) {
        String value = clean(text);
        if (value.matches("\\d+")) {
            return value + "周";
        }
        return value.contains("周") ? value : value + "周";
    }

    private String extractXautWeeks(String text) {
        Matcher matcher = XAUT_WEEK_SECTION_PATTERN.matcher(compact(text));
        return matcher.find() ? normalizeXautWeeks(matcher.group(1)) : extractWeeks(text);
    }

    private List<String> usefulXautParts(String cellText) {
        String[] rawParts = clean(cellText).split("/");
        List<String> parts = new ArrayList<>();
        for (String part : rawParts) {
            String value = cleanupMarker(part);
            if (value.length() == 0 || isTableChrome(value) || TIME_RANGE_PATTERN.matcher(value).find()) {
                continue;
            }
            parts.add(value);
        }
        return parts;
    }

    private int xautWeekPartIndex(List<String> parts) {
        for (int i = 0; i < parts.size(); i++) {
            if (isXautWeekSectionText(parts.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isXautWeekSectionText(String text) {
        return XAUT_WEEK_SECTION_PATTERN.matcher(compact(text).replace("/", "")).find();
    }

    private boolean isXautContinuationName(String text) {
        String value = cleanupMarker(text);
        return value.length() > 0
                && !TEACHER_GUESS_PATTERN.matcher(value).matches()
                && !isXautWeekSectionText(value)
                && extractSpecificRoom(value).length() == 0;
    }

    private String cleanupXautName(String text) {
        String value = cleanupCourseName(text);
        value = value.replaceAll("^[:：]+", "");
        return cleanupMarker(value);
    }

    private String joinXautLocation(String first, String second) {
        String value = cleanupMarker(first);
        String next = cleanupMarker(second);
        if (isXautSectionFragment(value)) {
            return "";
        }
        if (value.endsWith("-") && next.matches("\\d{2,4}")) {
            return value + next;
        }
        return value;
    }

    private String extractXautLocation(String cellText) {
        String compactText = compact(cellText).replace("/", "");
        Matcher matcher = Pattern
                .compile("(曲江\\d{1,2}-\\d{3,4}|曲江乒乓球场|三电实验中心教\\d+-?\\d{3,4}|工程训练基地|物理实验室[\\u4e00-\\u9fa5\\d]*)")
                .matcher(compactText);
        if (matcher.find()) {
            return cleanupMarker(matcher.group(1));
        }
        return "";
    }

    private boolean isXautSectionFragment(String text) {
        String value = compact(text).replace("[", "").replace("]", "");
        return value.matches("\\d{1,2}(?:-\\d{1,2})*节?");
    }

    private String extractClassTimeConfig(List<TextBlock> blocks, ParseDiagnostics diagnostics) {
        Map<Integer, String> times = new LinkedHashMap<>();
        for (TextBlock block : blocks) {
            Matcher timeMatcher = TIME_RANGE_PATTERN.matcher(block.text);
            if (!timeMatcher.find()) {
                continue;
            }
            int[] sections = nearestSectionsForTime(blocks, block);
            if (sections[0] <= 0) {
                continue;
            }
            String time = twoDigitTime(timeMatcher.group(1), timeMatcher.group(2))
                    + "-" + twoDigitTime(timeMatcher.group(3), timeMatcher.group(4));
            times.put(sections[0], time);
        }
        if (times.isEmpty()) {
            diagnostics.info("class times not found in PDF");
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<Integer, String> entry : times.entrySet()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(entry.getKey()).append(' ').append(entry.getValue());
        }
        diagnostics.info("class times=" + builder.toString().replace('\n', ';'));
        return builder.toString();
    }

    private int[] nearestSectionsForTime(List<TextBlock> blocks, TextBlock timeBlock) {
        TextBlock best = null;
        float bestDistance = Float.MAX_VALUE;
        for (TextBlock candidate : blocks) {
            if (candidate.page != timeBlock.page) {
                continue;
            }
            String text = clean(candidate.text);
            if (!text.matches("[（(]\\s*\\d{1,2}(?:\\s*[,，]\\s*\\d{1,2})+\\s*[）)]")
                    && !SECTION_PATTERN.matcher(text).find()) {
                continue;
            }
            float distance = Math.abs(candidate.y - timeBlock.y) + Math.abs(candidate.x - timeBlock.x) * 0.18f;
            if (distance < bestDistance && distance < 80f) {
                best = candidate;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return new int[]{0, 0};
        }
        return parseSectionNumbers(best.text);
    }

    private int[] parseSectionNumbers(String text) {
        Matcher matcher = Pattern.compile("\\d{1,2}").matcher(text == null ? "" : text);
        int min = Integer.MAX_VALUE;
        int max = 0;
        while (matcher.find()) {
            int value = parseInt(matcher.group(), 0);
            if (value > 0) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        return max == 0 ? new int[]{0, 0} : new int[]{min, max};
    }

    private String twoDigitTime(String hour, String minute) {
        int parsedHour = parseInt(hour, 0);
        return (parsedHour < 10 ? "0" : "") + parsedHour + ":" + minute;
    }

    private String normalizeWeekText(String text) {
        String clean = cleanupWeeks(text);
        WeekRule weekRule = weekRuleParser.parse(clean);
        return weekRule.displayText();
    }

    private String extractLocation(String details) {
        String room = extractSpecificRoom(details);
        if (room.length() > 0) {
            String campus = cleanupCampus(details);
            return campus.length() > 0 && !room.startsWith(campus) ? campus + room : room;
        }

        String tagged = extractFirstAfter(details, LOCATION_LABELS);
        if (tagged.length() > 0) {
            room = extractSpecificRoom(tagged);
            String campus = cleanupCampus(tagged);
            if (campus.length() == 0) {
                campus = cleanupCampus(details);
            }
            return room.length() > 0 ? campus + room : campus;
        }
        return cleanupCampus(details);
    }

    private String cleanupHduLocation(String location) {
        String value = cleanupMarker(location)
                .replace("校区:下沙", "下沙")
                .replace("校区：下沙", "下沙")
                .replace("校区:文一", "文一")
                .replace("校区：文一", "文一");
        return cleanupMarker(value);
    }

    private String extractHduLocation(String details) {
        String text = clean(details).replace("/", "").replace(" ", "");
        String campus = extractHduField(text, "校区", "场地", "地点", "教室", "教师", "教学班");
        String room = extractHduField(text, "场地", "教师", "教学班", "考核方式", "选课备注");
        if (room.length() == 0) {
            room = extractHduField(text, "地点", "教师", "教学班", "考核方式", "选课备注");
        }
        if (room.length() == 0) {
            room = extractHduField(text, "教室", "教师", "教学班", "考核方式", "选课备注");
        }
        if (room.length() == 0) {
            room = extractSpecificRoom(text);
        }
        String value = cleanupHduLocation(campus + room);
        return value.replace("场地:", "").replace("场地：", "")
                .replace("地点:", "").replace("地点：", "")
                .replace("教室:", "").replace("教室：", "");
    }

    private String extractHduTeacher(String details) {
        String text = clean(details).replace("/", "").replace(" ", "");
        String value = extractHduField(text, "教师", "教学班", "考核方式", "选课备注", "课程学时组成");
        if (value.length() == 0) {
            Matcher matcher = Pattern.compile("教师[:：]?([\\u4e00-\\u9fa5·]{2,5})").matcher(text);
            if (matcher.find()) {
                value = matcher.group(1);
            }
        }
        value = cleanupMarker(value);
        return value.matches("[\\u4e00-\\u9fa5·,，、]{2,20}") ? value : "";
    }

    private String extractHduField(String text, String label, String... endLabels) {
        int start = text.indexOf(label + ":");
        int labelLength = label.length() + 1;
        if (start < 0) {
            start = text.indexOf(label + "：");
        }
        if (start < 0) {
            return "";
        }
        start += labelLength;
        int end = text.length();
        for (String endLabel : endLabels) {
            int index = text.indexOf(endLabel, start);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        return cleanupMarker(text.substring(start, end));
    }

    private int firstIndexAfterAnyLabel(String text, String[] labels) {
        for (String label : labels) {
            int index = text.indexOf(label);
            if (index >= 0) {
                return index + label.length();
            }
        }
        return -1;
    }

    private int firstPositiveIndex(String text, int fromIndex, String... tokens) {
        int best = -1;
        for (String token : tokens) {
            int index = text.indexOf(token, fromIndex);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private String extractSpecificRoom(String text) {
        String normalized = normalizeRoomText(text);
        Matcher matcher = ROOM_PATTERN.matcher(normalized);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String cleanupCampus(String text) {
        String value = cleanupMarker(text);
        if (value.contains("长安校区西区")) {
            return "长安校区西区";
        }
        if (value.contains("长安校区东区")) {
            return "长安校区东区";
        }
        if (value.contains("雁塔校区")) {
            return "雁塔校区";
        }
        if (value.contains("太白校区")) {
            return "太白校区";
        }
        if (value.contains("校区")) {
            String[] parts = value.split("/");
            for (String part : parts) {
                if (part.contains("校区")) {
                    return cleanupMarker(part);
                }
            }
        }
        return "";
    }

    private String extractTeacher(String details, String courseName, String location) {
        String tagged = extractFirstAfter(details, TEACHER_LABELS);
        if (tagged.length() > 0) {
            return tagged;
        }
        String[] parts = clean(details).split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = cleanupMarker(parts[i]);
            if (candidate.equals(courseName) || candidate.equals(location) || candidate.contains("周")
                    || SECTION_PATTERN.matcher(candidate).find() || LOOSE_SECTION_PATTERN.matcher(candidate).find()) {
                continue;
            }
            if (extractLocation(candidate).length() > 0 || isTableChrome(candidate)) {
                continue;
            }
            if (TEACHER_GUESS_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return "";
    }

    private String extractCredit(String details) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("学分\\s*[:：]\\s*(\\d+(?:\\.\\d+)?)")
                .matcher(clean(details));
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractFirstAfter(String details, String[] labels) {
        String text = clean(details);
        for (String label : labels) {
            int start = text.indexOf(label);
            if (start < 0) {
                continue;
            }
            start += label.length();
            int end = text.indexOf('/', start);
            if (end < 0) {
                end = text.length();
            }
            String value = clean(text.substring(start, end));
            for (String nextLabel : LOCATION_LABELS) {
                value = trimBefore(value, nextLabel);
            }
            for (String nextLabel : TEACHER_LABELS) {
                value = trimBefore(value, nextLabel);
            }
            return cleanupMarker(value);
        }
        return "";
    }

    private List<Course> mergeDuplicates(List<Course> courses) {
        Map<String, Course> merged = new LinkedHashMap<>();
        for (Course course : courses) {
            String key = course.day + "|" + course.startSection + "|" + course.endSection + "|"
                    + course.name + "|" + course.weeks + "|" + course.location + "|"
                    + course.courseType.name();
            if (!merged.containsKey(key)) {
                merged.put(key, course);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private boolean isCourseName(String text) {
        return text.length() >= 2
                && !isCourseMeta(text)
                && !text.contains("节")
                && !text.contains("周")
                && !text.contains("星期")
                && !text.contains("时间")
                && !text.contains("教师")
                && !text.contains("地点")
                && !text.contains("场地")
                && !text.matches("[:：]?\\d+(\\.\\d+)?");
    }

    private boolean isTableChrome(String text) {
        String clean = compact(text);
        if (clean.length() == 0) {
            return true;
        }
        if (isCourseMeta(clean)) {
            return true;
        }
        if ("时间".equals(clean) || "课程".equals(clean) || "节次".equals(clean)) {
            return true;
        }
        for (String weekday : WEEKDAYS) {
            if (clean.equals(weekday)) {
                return true;
            }
        }
        for (String weekday : SHORT_WEEKDAYS) {
            if (clean.equals(weekday)) {
                return true;
            }
        }
        return clean.matches("\\d+") || clean.matches("\\d{1,2}:\\d{2}.*");
    }

    private String cleanupCourseName(String text) {
        String value = cleanupMarker(text);
        value = SECTION_PATTERN.matcher(value).replaceAll("");
        value = LOOSE_SECTION_PATTERN.matcher(value).replaceAll("");
        value = WEEK_PATTERN.matcher(value).replaceAll("");
        value = removeCourseMeta(value);
        return cleanupMarker(value);
    }

    private boolean isCourseMeta(String text) {
        String value = compact(text);
        return COURSE_META_PATTERN.matcher(value).matches()
                || value.matches("[:：]?\\d+(\\.\\d+)?")
                || value.matches(".*学时\\s*[:：]?\\s*\\d+.*")
                || value.matches(".*学分\\s*[:：]?\\s*\\d+(\\.\\d+)?.*");
    }

    private String removeCourseMeta(String text) {
        String value = clean(text);
        // XUPT PDFs can split the "学分" label at a timetable column edge, leaving
        // an orphaned prefix such as "分:3.0无线定位技术" in the course-name block.
        // Only strip it at the beginning so legitimate names such as "微积分" remain intact.
        value = value.replaceFirst("^分\\s*[:：]\\s*\\d+(?:\\.\\d+)?\\s*", "");
        value = value.replaceAll("[:：]?\\d+(\\.\\d+)?\\s*总?学时\\s*[:：]?\\s*\\d+(\\.\\d+)?", "");
        value = value.replaceAll("总?学时\\s*[:：]?\\s*\\d+(\\.\\d+)?", "");
        value = value.replaceAll("学分\\s*[:：]?\\s*\\d+(\\.\\d+)?", "");
        value = value.replaceAll("课程性质\\s*[:：]?[^/]*", "");
        value = value.replaceAll("课程属性\\s*[:：]?[^/]*", "");
        value = value.replaceAll("考核方式\\s*[:：]?[^/]*", "");
        value = value.replaceAll("课程编号\\s*[:：]?[^/]*", "");
        value = value.replaceAll("课程序号\\s*[:：]?[^/]*", "");
        return value;
    }

    private String cleanupWeeks(String value) {
        String text = clean(value);
        if (text.startsWith("/")) {
            text = text.substring(1);
        }
        if (weekRuleParser.isSupportedExpression(text)) {
            return weekRuleParser.parse(text).displayText();
        }
        String extracted = extractWeeks(text);
        if (!"周次见PDF".equals(extracted)) {
            return extracted;
        }
        return text.length() == 0 ? "周次见PDF" : text;
    }

    private String normalizeRoomText(String text) {
        return cleanupMarker(text)
                .replace('Ａ', 'A').replace('Ｂ', 'B').replace('Ｃ', 'C').replace('Ｄ', 'D')
                .replace('Ｅ', 'E').replace('Ｆ', 'F').replace('Ｇ', 'G')
                .replace('ａ', 'A').replace('ｂ', 'B').replace('ｃ', 'C').replace('ｄ', 'D')
                .replace("０", "0").replace("１", "1").replace("２", "2").replace("３", "3")
                .replace("４", "4").replace("５", "5").replace("６", "6").replace("７", "7")
                .replace("８", "8").replace("９", "9")
                .replaceAll("\\s+", "")
                .toUpperCase();
    }

    private String trimBefore(String text, String token) {
        int index = text.indexOf(token);
        return index < 0 ? text : text.substring(0, index).trim();
    }

    private String cleanupMarker(String text) {
        return clean(text)
                .replace("◆", "")
                .replace("◇", "")
                .replace("●", "")
                .replace("○", "")
                .replace("♦", "")
                .trim();
    }

    private String compact(String text) {
        return clean(text).replaceAll("\\s+", "");
    }

    private String clean(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String preview(List<TextBlock> blocks) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(30, blocks.size());
        for (int i = 0; i < count; i++) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(blocks.get(i).text);
        }
        return builder.toString();
    }

    private static class ParsedCell {
        int start;
        int end;
        String name = "";
        String weeks = "周次见PDF";
        String location = "";
        String teacher = "";
        String credit = "";
        CourseType courseType = CourseType.LECTURE;
    }

    private static class CourseSeed {
        final int blockIndex;
        final int page;
        final int day;
        final int start;
        final int end;
        final String weeks;
        final float y;

        CourseSeed(int blockIndex, int page, int day, int start, int end, String weeks, float y) {
            this.blockIndex = blockIndex;
            this.page = page;
            this.day = day;
            this.start = start;
            this.end = end;
            this.weeks = weeks == null ? "" : weeks;
            this.y = y;
        }
    }

    private static class TextBlock {
        final String text;
        final int page;
        final float x;
        final float y;
        final float width;
        final float height;

        TextBlock(String text, int page, float x, float y, float width, float height) {
            this.text = text;
            this.page = page;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        float centerX() {
            return x + width / 2f;
        }
    }

    private static class PositionStripper extends PDFTextStripper {
        final List<TextBlock> blocks = new ArrayList<>();

        PositionStripper() throws java.io.IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            if (textPositions == null || textPositions.isEmpty()) {
                return;
            }
            List<TextPosition> positions = new ArrayList<>(textPositions);
            Collections.sort(positions, new Comparator<TextPosition>() {
                @Override
                public int compare(TextPosition first, TextPosition second) {
                    return Float.compare(first.getXDirAdj(), second.getXDirAdj());
                }
            });
            StringBuilder chunk = new StringBuilder();
            float minX = Float.MAX_VALUE;
            float maxX = Float.MIN_VALUE;
            float minY = Float.MAX_VALUE;
            float maxY = Float.MIN_VALUE;
            int count = 0;
            float previousRight = -1f;
            for (TextPosition position : positions) {
                float gap = previousRight < 0f ? 0f : position.getXDirAdj() - previousRight;
                if (chunk.length() > 0 && gap > 14f) {
                    addChunk(chunk, minX, maxX, minY, maxY, count);
                    chunk.setLength(0);
                    minX = Float.MAX_VALUE;
                    maxX = Float.MIN_VALUE;
                    minY = Float.MAX_VALUE;
                    maxY = Float.MIN_VALUE;
                    count = 0;
                } else if (chunk.length() > 0 && gap > 3.5f) {
                    chunk.append(' ');
                }
                chunk.append(position.getUnicode());
                minX = Math.min(minX, position.getXDirAdj());
                maxX = Math.max(maxX, position.getXDirAdj() + position.getWidthDirAdj());
                minY = Math.min(minY, position.getYDirAdj());
                maxY = Math.max(maxY, position.getYDirAdj() + position.getHeightDir());
                count++;
                previousRight = position.getXDirAdj() + position.getWidthDirAdj();
            }
            if (chunk.length() > 0 && count > 0) {
                addChunk(chunk, minX, maxX, minY, maxY, count);
            }
        }

        private void addChunk(StringBuilder chunk, float minX, float maxX, float minY, float maxY, int count) {
            if (count <= 0) {
                return;
            }
            blocks.add(new TextBlock(chunk.toString(), getCurrentPageNo(), minX, minY, maxX - minX, maxY - minY));
        }
    }
}
