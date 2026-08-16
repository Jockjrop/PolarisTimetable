package com.polaris.timetable.reminder;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.polaris.timetable.MainActivity;
import com.polaris.timetable.R;

public final class CourseReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (CourseReminderScheduler.ACTION_COURSE_REMINDER.equals(intent.getAction())) {
            if (CourseReminderScheduler.hasOverlayPermission(context)) {
                CourseReminderPopup.show(context, intent);
            } else {
                showNotification(context, intent);
            }
        }
        CourseReminderScheduler.reschedule(context);
    }

    private void showNotification(Context context, Intent source) {
        if (!CourseReminderScheduler.hasNotificationPermission(context)) {
            return;
        }
        CourseReminderScheduler.createNotificationChannel(context);
        String courseName = source.getStringExtra(CourseReminderScheduler.EXTRA_COURSE_NAME);
        String location = source.getStringExtra(CourseReminderScheduler.EXTRA_LOCATION);
        String timeText = source.getStringExtra(CourseReminderScheduler.EXTRA_TIME_TEXT);
        int week = source.getIntExtra(CourseReminderScheduler.EXTRA_WEEK, 0);
        int minutesBefore = source.getIntExtra(
                CourseReminderScheduler.EXTRA_MINUTES_BEFORE, 0);
        StringBuilder details = new StringBuilder();
        details.append(minutesBefore > 0 ? minutesBefore + " 分钟后上课" : "课程即将开始");
        if (timeText != null && timeText.length() > 0) {
            details.append(" · ").append(timeText);
        }
        if (location != null && location.length() > 0) {
            details.append(" · ").append(location);
        }
        Intent openApp = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CourseReminderScheduler.CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_notification_course)
                .setContentTitle(courseName == null || courseName.length() == 0
                        ? "课程提醒" : courseName)
                .setContentText(details.toString())
                .setStyle(new Notification.BigTextStyle().bigText(details.toString()))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setWhen(System.currentTimeMillis());
        if (week > 0) {
            builder.setSubText("第 " + week + " 周");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            int notificationId = source.getDataString() == null
                    ? (int) System.currentTimeMillis()
                    : source.getDataString().hashCode();
            manager.notify(notificationId, builder.build());
        }
    }
}
