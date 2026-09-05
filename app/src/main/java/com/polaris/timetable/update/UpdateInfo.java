package com.polaris.timetable.update;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 更新清单 latest.json 的不可变数据模型。
 * 构造完成后字段不再允许 UI 修改。
 */
public final class UpdateInfo {
    private final int schemaVersion;
    private final String channel;
    private final String packageName;
    private final int versionCode;
    private final String versionName;
    private final int minSdk;
    private final int minSupportedVersionCode;
    private final String publishedAt;
    private final String apkFileName;
    private final String apkUrl;
    private final long apkSize;
    private final String apkSha256;
    private final List<String> releaseNotes;
    private final String releaseNotesUrl;
    private final boolean required;

    public UpdateInfo(int schemaVersion, String channel, String packageName, int versionCode,
                      String versionName, int minSdk, int minSupportedVersionCode,
                      String publishedAt, String apkFileName, String apkUrl, long apkSize,
                      String apkSha256, List<String> releaseNotes, String releaseNotesUrl,
                      boolean required) {
        this.schemaVersion = schemaVersion;
        this.channel = channel;
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.minSdk = minSdk;
        this.minSupportedVersionCode = minSupportedVersionCode;
        this.publishedAt = publishedAt;
        this.apkFileName = apkFileName;
        this.apkUrl = apkUrl;
        this.apkSize = apkSize;
        this.apkSha256 = apkSha256;
        this.releaseNotes = Collections.unmodifiableList(new ArrayList<>(releaseNotes));
        this.releaseNotesUrl = releaseNotesUrl;
        this.required = required;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String channel() {
        return channel;
    }

    public String packageName() {
        return packageName;
    }

    public int versionCode() {
        return versionCode;
    }

    public String versionName() {
        return versionName;
    }

    public int minSdk() {
        return minSdk;
    }

    public int minSupportedVersionCode() {
        return minSupportedVersionCode;
    }

    public String publishedAt() {
        return publishedAt;
    }

    public String apkFileName() {
        return apkFileName;
    }

    public String apkUrl() {
        return apkUrl;
    }

    public long apkSize() {
        return apkSize;
    }

    public String apkSha256() {
        return apkSha256;
    }

    public List<String> releaseNotes() {
        return releaseNotes;
    }

    public String releaseNotesUrl() {
        return releaseNotesUrl;
    }

    public boolean required() {
        return required;
    }

    /** 两个发布源是否描述同一个 APK；下载地址和说明页地址允许不同。 */
    public boolean hasSameApkIdentity(UpdateInfo other) {
        return other != null
                && versionCode == other.versionCode
                && apkSize == other.apkSize
                && stringEquals(packageName, other.packageName)
                && stringEquals(versionName, other.versionName)
                && stringEquals(apkFileName, other.apkFileName)
                && stringEquals(apkSha256, other.apkSha256);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UpdateInfo)) {
            return false;
        }
        UpdateInfo that = (UpdateInfo) o;
        return schemaVersion == that.schemaVersion
                && versionCode == that.versionCode
                && minSdk == that.minSdk
                && minSupportedVersionCode == that.minSupportedVersionCode
                && apkSize == that.apkSize
                && required == that.required
                && stringEquals(channel, that.channel)
                && stringEquals(packageName, that.packageName)
                && stringEquals(versionName, that.versionName)
                && stringEquals(publishedAt, that.publishedAt)
                && stringEquals(apkFileName, that.apkFileName)
                && stringEquals(apkUrl, that.apkUrl)
                && stringEquals(apkSha256, that.apkSha256)
                && stringEquals(releaseNotesUrl, that.releaseNotesUrl)
                && releaseNotes.equals(that.releaseNotes);
    }

    @Override
    public int hashCode() {
        int result = schemaVersion;
        result = 31 * result + versionCode;
        result = 31 * result + (int) (apkSize ^ (apkSize >>> 32));
        result = 31 * result + releaseNotes.hashCode();
        return result;
    }

    private static boolean stringEquals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
