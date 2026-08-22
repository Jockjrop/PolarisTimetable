package com.polaris.timetable.reminder;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.polaris.timetable.MainActivity;

/** 计划提醒接收器：到点发通知；系统事件后重排提醒。 */
public final class PlanReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (PlanReminderScheduler.ACTION_PLAN_REMINDER.equals(action)) {
            notifyPlan(context, intent);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
                .equals(action)) {
            PlanReminderScheduler.reschedule(context);
        }
    }

    private void notifyPlan(Context context, Intent intent) {
        if (!PlanReminderScheduler.hasPermission(context)) {
            return;
        }
        PlanReminderScheduler.createNotificationChannel(context);
        String title = intent.getStringExtra(PlanReminderScheduler.EXTRA_TITLE);
        if (title == null || title.length() == 0) {
            title = "未命名计划";
        }
        int week = intent.getIntExtra(PlanReminderScheduler.EXTRA_WEEK, 1);
        int day = intent.getIntExtra(PlanReminderScheduler.EXTRA_DAY, 0);
        String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        String dayText = day >= 0 && day < days.length ? days[day] : "";
        String body = "第 " + week + " 周" + (dayText.length() > 0 ? " · " + dayText : "")
                + " · 记得完成学习计划";

        int notificationId = title.hashCode() & 0x7fffffff;
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, notificationId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, PlanReminderScheduler.CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }
        builder.setSmallIcon(com.polaris.timetable.R.drawable.ic_notification_course)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(contentIntent);
        Notification notification = builder.build();
        android.app.NotificationManager manager = (android.app.NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, notification);
        }
    }
}
