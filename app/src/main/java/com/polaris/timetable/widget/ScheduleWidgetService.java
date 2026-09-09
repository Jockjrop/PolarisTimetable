package com.polaris.timetable.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.polaris.timetable.R;
import com.polaris.timetable.storage.ScheduleRepository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class ScheduleWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        if (WeekScheduleWidgetProvider.FORM_WEEK.equals(
                intent.getStringExtra(WeekScheduleWidgetProvider.EXTRA_FORM))) {
            return new WeekGridFactory(getApplicationContext(), intent);
        }
        return new CourseListFactory(getApplicationContext(), intent);
    }

    /** 今日/明日课程列表工厂：每行一门课程（含进行中态）。 */
    private static final class CourseListFactory implements RemoteViewsFactory {
        private final Context context;
        private final int dayOffset;
        private final int appWidgetId;
        private List<ScheduleWidgetEntry> entries = new ArrayList<>();

        CourseListFactory(Context context, Intent intent) {
            this.context = context;
            this.dayOffset = intent.getIntExtra(ScheduleWidgetProvider.EXTRA_DAY_OFFSET, 0);
            this.appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        @Override
        public void onCreate() {
            reload();
        }

        @Override
        public void onDataSetChanged() {
            reload();
        }

        private void reload() {
            ScheduleRepository repository = new ScheduleRepository(context);
            // 每个 widget 实例可绑定独立课表（未配置时回退激活课表）。
            String scheduleId = ScheduleWidgetConfigActivity.boundScheduleId(context, appWidgetId);
            ScheduleRepository.Config config = repository.loadConfig(scheduleId);
            Calendar now = Calendar.getInstance();
            Calendar target = (Calendar) now.clone();
            target.add(Calendar.DATE, dayOffset);
            entries = ScheduleWidgetData.forDate(
                    repository.loadCourseView(scheduleId), config, target, now);
        }

        @Override
        public void onDestroy() {
            entries = new ArrayList<>();
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= entries.size()) {
                return null;
            }
            ScheduleWidgetEntry entry = entries.get(position);
            // 进行中条目用独立布局（高亮底色 + 时间行主文本色），两布局 ID 一致，
            // 绑定代码完全复用；viewTypeCount 同步为 2。
            RemoteViews views = new RemoteViews(context.getPackageName(), entry.ongoing
                    ? R.layout.widget_course_item_ongoing : R.layout.widget_course_item);
            views.setTextViewText(R.id.widget_course_name, entry.name);
            views.setTextViewText(R.id.widget_course_time, entry.time);
            views.setTextViewText(R.id.widget_course_location, entry.location);
            views.setInt(R.id.widget_course_accent, "setColorFilter", entry.color);
            views.setContentDescription(R.id.widget_course_item,
                    entry.name + "，" + entry.time + "，" + entry.location
                            + (entry.ongoing
                                    ? context.getString(R.string.widget_cd_ongoing) : ""));
            views.setOnClickFillInIntent(R.id.widget_course_item, new Intent());
            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public long getItemId(int position) {
            return position >= 0 && position < entries.size() ? entries.get(position).stableId : position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }

    /**
     * 整周课表网格工厂：每行一个节次（时间列 + 天列格子），行数=节次数，
     * ListView 天然支持在小组件内上下滑动。格子底图经 setColorFilter 染色
     * 呈现课程色圆角块；课程首节展示地点行，跨节续行只重复课程名。
     */
    private static final class WeekGridFactory implements RemoteViewsFactory {
        private static final int[] CELL_IDS = {
                R.id.widget_week_cell_0,
                R.id.widget_week_cell_1,
                R.id.widget_week_cell_2,
                R.id.widget_week_cell_3,
                R.id.widget_week_cell_4,
                R.id.widget_week_cell_5,
                R.id.widget_week_cell_6,
        };
        private static final int[] CELL_BG_IDS = {
                R.id.widget_week_cell_bg_0,
                R.id.widget_week_cell_bg_1,
                R.id.widget_week_cell_bg_2,
                R.id.widget_week_cell_bg_3,
                R.id.widget_week_cell_bg_4,
                R.id.widget_week_cell_bg_5,
                R.id.widget_week_cell_bg_6,
        };
        private static final int[] CELL_TEXT_IDS = {
                R.id.widget_week_cell_text_0,
                R.id.widget_week_cell_text_1,
                R.id.widget_week_cell_text_2,
                R.id.widget_week_cell_text_3,
                R.id.widget_week_cell_text_4,
                R.id.widget_week_cell_text_5,
                R.id.widget_week_cell_text_6,
        };

        private final Context context;
        private final int appWidgetId;
        private final int dayCount;
        private List<ScheduleWidgetWeekData.Row> rows = new ArrayList<>();

        WeekGridFactory(Context context, Intent intent) {
            this.context = context;
            this.appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            this.dayCount = intent.getIntExtra(
                    WeekScheduleWidgetProvider.EXTRA_DAY_COUNT,
                    ScheduleWidgetConfigActivity.DEFAULT_DAY_COUNT);
        }

        @Override
        public void onCreate() {
            reload();
        }

        @Override
        public void onDataSetChanged() {
            reload();
        }

        private void reload() {
            ScheduleRepository repository = new ScheduleRepository(context);
            String scheduleId = ScheduleWidgetConfigActivity.boundScheduleId(context, appWidgetId);
            rows = ScheduleWidgetWeekData.buildRows(
                    repository.loadCourseView(scheduleId),
                    repository.loadConfig(scheduleId),
                    Calendar.getInstance(),
                    ScheduleWidgetConfigActivity.widgetDayCount(context, appWidgetId));
        }

        @Override
        public void onDestroy() {
            rows = new ArrayList<>();
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= rows.size()) {
                return null;
            }
            ScheduleWidgetWeekData.Row row = rows.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_week_row);
            views.setTextViewText(R.id.widget_week_section_num, String.valueOf(row.section));
            views.setTextViewText(R.id.widget_week_section_time, row.sectionTime);
            for (int day = 0; day < CELL_IDS.length; day++) {
                ScheduleWidgetWeekData.Cell cell = row.cells[day];
                if (day >= dayCount) {
                    views.setViewVisibility(CELL_IDS[day], View.GONE);
                    continue;
                }
                views.setViewVisibility(CELL_IDS[day], View.VISIBLE);
                if (cell.color == 0) {
                    // 空格：隐藏圆角底图，文字与无障碍描述一并留空。
                    views.setViewVisibility(CELL_BG_IDS[day], View.GONE);
                    views.setTextViewText(CELL_TEXT_IDS[day], "");
                    views.setTextColor(CELL_TEXT_IDS[day],
                            context.getColor(R.color.widget_text_main));
                    views.setContentDescription(CELL_IDS[day], "");
                    continue;
                }
                // 课程格 / 今天列淡底格：白底圆角经染色呈现课程色，文字叠于其上。
                views.setViewVisibility(CELL_BG_IDS[day], View.VISIBLE);
                views.setInt(CELL_BG_IDS[day], "setColorFilter", cell.color);
                boolean showLocation = cell.startOfCourse && cell.location.length() > 0;
                views.setTextViewText(CELL_TEXT_IDS[day],
                        showLocation ? cell.text + " @" + cell.location : cell.text);
                views.setTextColor(CELL_TEXT_IDS[day], Color.WHITE);
                views.setContentDescription(CELL_IDS[day], cell.text.length() == 0 ? ""
                        : ScheduleWidgetWeekData.DAY_NAMES[day] + " 第" + row.section + "节 "
                                + cell.text
                                + (cell.location.length() > 0 ? " " + cell.location : ""));
            }
            views.setOnClickFillInIntent(R.id.widget_week_row, new Intent());
            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position >= 0 && position < rows.size() ? rows.get(position).section : position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
