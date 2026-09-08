package com.polaris.timetable.storage;

/**
 * 备份恢复用例控制器：执行已确认的备份覆盖，并返回供界面展示的恢复统计。
 * 文件选择、确认对话框和恢复后的页面重载仍由 Activity 负责，避免控制器持有 UI。
 */
public final class BackupRestoreController {

    private final ScheduleRepository scheduleRepository;

    public BackupRestoreController(ScheduleRepository scheduleRepository) {
        if (scheduleRepository == null) {
            throw new IllegalArgumentException("无法访问本机存储");
        }
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * 执行恢复。底层写入失败时原样抛出异常，由宿主决定错误提示；成功时返回统计。
     */
    public RestoreResult restore(ScheduleBackupManager.BackupBundle bundle) {
        ScheduleBackupManager.restoreTo(scheduleRepository, bundle);
        int courseCount = 0;
        int studyPlanCount = 0;
        int academicEventCount = 0;
        for (ScheduleBackupManager.ScheduleBackup backup : bundle.schedules) {
            courseCount += backup.structuredCourses.size();
            studyPlanCount += backup.studyPlans.size();
            academicEventCount += backup.academicEvents.size();
        }
        return new RestoreResult(bundle.schedules.size(), courseCount,
                studyPlanCount, academicEventCount);
    }

    /** 恢复成功后供宿主更新提示文案的统计快照。 */
    public static final class RestoreResult {
        public final int scheduleCount;
        public final int courseCount;
        public final int studyPlanCount;
        public final int academicEventCount;

        private RestoreResult(int scheduleCount, int courseCount,
                              int studyPlanCount, int academicEventCount) {
            this.scheduleCount = scheduleCount;
            this.courseCount = courseCount;
            this.studyPlanCount = studyPlanCount;
            this.academicEventCount = academicEventCount;
        }
    }
}
