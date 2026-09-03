package com.polaris.timetable.widget;

import android.content.Context;
import android.content.Intent;
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
        return new CourseListFactory(getApplicationContext(), intent);
    }

    private static final class CourseListFactory implements RemoteViewsFactory {
        private final Context context;
        private final int dayOffset;
        private List<ScheduleWidgetEntry> entries = new ArrayList<>();

        CourseListFactory(Context context, Intent intent) {
            this.context = context;
            dayOffset = intent.getIntExtra(ScheduleWidgetProvider.EXTRA_DAY_OFFSET, 0);
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
            String scheduleId = repository.activeScheduleId();
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
}
