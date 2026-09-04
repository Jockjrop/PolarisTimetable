package com.polaris.timetable.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 安装前强制校验（协议第 10.1 节）：长度 → SHA-256 → 归档信息 → 包名 → 版本 →
 * minSdk → 签名连续性。任一步失败返回具体原因，不负责展示弹窗。
 */
public final class ApkVerifier {

    private ApkVerifier() {
    }

    public static final class Result {
        public final boolean ok;
        public final UpdateError error;

        private Result(boolean ok, UpdateError error) {
            this.ok = ok;
            this.error = error;
        }

        static Result success() {
            return new Result(true, null);
        }

        static Result failure(UpdateError error) {
            return new Result(false, error);
        }
    }

    /** 全量校验：用于下载完成后的首次验收。 */
    public static Result verify(Context context, File apk, UpdateInfo info,
                                 int installedVersionCode, int deviceSdk) {
        if (context == null || apk == null || info == null) {
            return Result.failure(UpdateError.APK_UNREADABLE);
        }
        if (!apk.isFile() || apk.length() != info.apkSize()) {
            return Result.failure(UpdateError.FILE_SIZE_MISMATCH);
        }
        String digest = sha256Hex(apk);
        if (digest == null || !digest.equals(info.apkSha256())) {
            return Result.failure(UpdateError.HASH_MISMATCH);
        }
        return verifyArchive(context, apk, info, installedVersionCode, deviceSdk);
    }

    /** 复验：进程重启后恢复的待安装包缺少清单上下文，校验归档与本地一致性（无清单哈希对照）。 */
    public static Result verifyWithoutManifest(Context context, File apk,
                                               int expectedVersionCode,
                                               int installedVersionCode, int deviceSdk) {
        if (context == null || apk == null || !apk.isFile() || apk.length() <= 0) {
            return Result.failure(UpdateError.APK_UNREADABLE);
        }
        UpdateInfo surrogate = new UpdateInfo(1, "stable", context.getPackageName(),
                expectedVersionCode, "", 0, 0, "", apk.getName(), "", apk.length(), "",
                java.util.Collections.singletonList(""), "", false);
        return verifyArchive(context, apk, surrogate, installedVersionCode, deviceSdk);
    }

    private static Result verifyArchive(Context context, File apk, UpdateInfo info,
                                        int installedVersionCode, int deviceSdk) {
        PackageManager packageManager = context.getPackageManager();
        // GET_SIGNATURES 对归档 APK 在所有支持版本上仍可读取签名；28+ 的安装包侧签名
        // 用 GET_SIGNING_CERTIFICATES 读取（见 readInstalledSignatures）。
        @SuppressWarnings("deprecation")
        PackageInfo archive = packageManager.getPackageArchiveInfo(
                apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        if (archive == null || archive.signatures == null || archive.signatures.length == 0) {
            return Result.failure(UpdateError.APK_UNREADABLE);
        }
        if (!context.getPackageName().equals(archive.packageName)) {
            return Result.failure(UpdateError.PACKAGE_MISMATCH);
        }
        if (archive.versionCode != info.versionCode()) {
            return Result.failure(UpdateError.APK_VERSION_MISMATCH);
        }
        if (info.versionName() != null && !info.versionName().isEmpty()
                && !info.versionName().equals(archive.versionName)) {
            return Result.failure(UpdateError.APK_VERSION_MISMATCH);
        }
        if (info.versionCode() <= installedVersionCode) {
            // 降级攻击防护：远端与 APK versionCode 都必须大于本地。
            return Result.failure(UpdateError.APK_VERSION_MISMATCH);
        }
        // ApplicationInfo.minSdkVersion 自 API 24 起才对归档信息可靠填充；
        // API 23 上读不到（0 = 未知）时跳过该层检查，交由系统安装器最终把关。
        int archiveMinSdk = 0;
        if (archive.applicationInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                archiveMinSdk = archive.applicationInfo.minSdkVersion;
            }
        }
        if (archiveMinSdk > 0 && archiveMinSdk > deviceSdk) {
            return Result.failure(UpdateError.DEVICE_UNSUPPORTED);
        }
        Set<String> archiveDigests = digestSet(archive.signatures);
        Set<String> installedDigests = readInstalledSignatureDigests(packageManager,
                context.getPackageName());
        if (installedDigests == null || installedDigests.isEmpty()
                || !installedDigests.equals(archiveDigests)) {
            return Result.failure(UpdateError.SIGNATURE_MISMATCH);
        }
        return Result.success();
    }

    private static Set<String> readInstalledSignatureDigests(PackageManager packageManager,
                                                             String packageName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo installed = packageManager.getPackageInfo(packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES);
                SigningInfo signingInfo = installed.signingInfo;
                if (signingInfo == null) {
                    return null;
                }
                return digestSet(signingInfo.getApkContentsSigners());
            }
            @SuppressWarnings("deprecation")
            PackageInfo installed = packageManager.getPackageInfo(packageName,
                    PackageManager.GET_SIGNATURES);
            return installed.signatures == null ? null : digestSet(installed.signatures);
        } catch (PackageManager.NameNotFoundException exception) {
            return null;
        }
    }

    private static Set<String> digestSet(Signature[] signatures) {
        Set<String> digests = new HashSet<>();
        if (signatures == null) {
            return digests;
        }
        for (Signature signature : signatures) {
            String digest = sha256Hex(signature.toByteArray());
            if (digest != null) {
                digests.add(digest);
            }
        }
        return digests;
    }

    public static String sha256Hex(File file) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            FileInputStream stream = new FileInputStream(file);
            try {
                byte[] chunk = new byte[65536];
                int read;
                while ((read = stream.read(chunk)) != -1) {
                    digest.update(chunk, 0, read);
                }
            } finally {
                try {
                    stream.close();
                } catch (java.io.IOException ignored) {
                    // 关闭失败不影响哈希结果。
                }
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format(Locale.US, "%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            return null;
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder builder = new StringBuilder();
            for (byte item : digest.digest(data)) {
                builder.append(String.format(Locale.US, "%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            return null;
        }
    }
}
