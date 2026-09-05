package com.polaris.timetable.update;

/**
 * 更新系统错误种类。UI 层按此映射用户可读文案（strings.xml 的 update_error_*），
 * 不得向用户展示异常堆栈、缓存绝对路径或内部类名。
 */
public enum UpdateError {
    /** 网络不可用或请求失败（含重定向目标非法）。 */
    NETWORK,
    /** 连接或读取超时。 */
    TIMEOUT,
    /** 更新服务返回非 200（GitHub 暂时不可用等）。 */
    HTTP,
    /** 清单缺失必填字段、类型错误或数值越界。 */
    INVALID_METADATA,
    /** 清单 schemaVersion 高于客户端支持值。 */
    UNSUPPORTED_SCHEMA,
    /** 两个官方来源对同一 versionCode 给出的 APK 身份不一致。 */
    SOURCE_MISMATCH,
    /** 两个官方更新来源均不可用或均返回无效数据。 */
    UPDATE_SERVICE_UNAVAILABLE,
    /** 设备 API 低于远端 APK minSdk。 */
    DEVICE_UNSUPPORTED,
    /** 可用空间不足（APK 大小 + 50 MiB 余量）。 */
    INSUFFICIENT_STORAGE,
    /** 用户取消下载。 */
    CANCELLED,
    /** 文件实际长度与清单声明不符。 */
    FILE_SIZE_MISMATCH,
    /** SHA-256 校验失败。 */
    HASH_MISMATCH,
    /** 归档 APK 无法读取（损坏或非 APK）。 */
    APK_UNREADABLE,
    /** APK 包名与当前应用不一致。 */
    PACKAGE_MISMATCH,
    /** APK 版本与清单不一致或构成降级。 */
    APK_VERSION_MISMATCH,
    /** APK 签名与已安装应用不一致。 */
    SIGNATURE_MISMATCH,
    /** 系统没有可处理安装 Intent 的组件。 */
    NO_INSTALLER,
    /** 更新文件写入/重命名失败。 */
    STORAGE_WRITE_FAILED,
    /** 系统在安装确认中阻止了本次安装（PackageInstaller STATUS_FAILURE_BLOCKED 等）。 */
    INSTALL_BLOCKED,
}
