package com.polaris.timetable.update;

/**
 * 下载状态机。状态转换单向且可观察；同一时刻只允许一个下载任务。
 */
public enum UpdateDownloadState {
    IDLE,
    PREPARING,
    DOWNLOADING,
    VERIFYING_HASH,
    VERIFYING_APK,
    READY_TO_INSTALL,
    CANCELLED,
    FAILED,
}
