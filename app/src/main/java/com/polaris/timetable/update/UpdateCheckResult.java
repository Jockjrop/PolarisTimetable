package com.polaris.timetable.update;

/**
 * 检查更新的明确结果类型。禁止用 null 同时表示“无更新 / 失败 / 取消”。
 * kind 之外的字段按 kind 语义填充：可用/最新/被忽略/不兼容时携带清单信息，
 * 失败类携带 {@link UpdateError}。
 */
public final class UpdateCheckResult {

    public enum Kind {
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        DEVICE_UNSUPPORTED,
        IGNORED_VERSION,
        NETWORK_ERROR,
        HTTP_ERROR,
        INVALID_METADATA,
        UNSUPPORTED_SCHEMA,
        CANCELLED,
    }

    private final Kind kind;
    private final UpdateInfo update;
    private final UpdateError error;

    private UpdateCheckResult(Kind kind, UpdateInfo update, UpdateError error) {
        this.kind = kind;
        this.update = update;
        this.error = error;
    }

    public static UpdateCheckResult available(UpdateInfo info) {
        return new UpdateCheckResult(Kind.UPDATE_AVAILABLE, info, null);
    }

    public static UpdateCheckResult upToDate(UpdateInfo info) {
        return new UpdateCheckResult(Kind.UP_TO_DATE, info, null);
    }

    public static UpdateCheckResult deviceUnsupported(UpdateInfo info) {
        return new UpdateCheckResult(Kind.DEVICE_UNSUPPORTED, info, null);
    }

    public static UpdateCheckResult ignoredVersion(UpdateInfo info) {
        return new UpdateCheckResult(Kind.IGNORED_VERSION, info, null);
    }

    public static UpdateCheckResult networkError(UpdateError error) {
        return new UpdateCheckResult(Kind.NETWORK_ERROR, null,
                error == null ? UpdateError.NETWORK : error);
    }

    public static UpdateCheckResult httpError() {
        return new UpdateCheckResult(Kind.HTTP_ERROR, null, UpdateError.HTTP);
    }

    public static UpdateCheckResult invalidMetadata(UpdateError error) {
        return new UpdateCheckResult(Kind.INVALID_METADATA, null,
                error == null ? UpdateError.INVALID_METADATA : error);
    }

    public static UpdateCheckResult unsupportedSchema() {
        return new UpdateCheckResult(Kind.UNSUPPORTED_SCHEMA, null, UpdateError.UNSUPPORTED_SCHEMA);
    }

    public static UpdateCheckResult cancelled() {
        return new UpdateCheckResult(Kind.CANCELLED, null, null);
    }

    public Kind kind() {
        return kind;
    }

    /** 仅可用/最新/被忽略/不兼容时非空。 */
    public UpdateInfo update() {
        return update;
    }

    /** 仅失败类非空。 */
    public UpdateError error() {
        return error;
    }
}
