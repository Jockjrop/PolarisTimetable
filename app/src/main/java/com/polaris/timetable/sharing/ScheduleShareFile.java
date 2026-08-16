package com.polaris.timetable.sharing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Lightweight file wrapper for a Polaris schedule share link. */
public final class ScheduleShareFile {
    public static final String MIME_TYPE = "application/vnd.polaris.schedule";
    public static final String EXTENSION = ".polaris";

    private static final String HEADER = "POLARIS_SCHEDULE_SHARE_FILE_V1\n";
    private static final int MAX_FILE_BYTES = 1_000_000;

    private ScheduleShareFile() {
    }

    public static byte[] encode(String shareLink) {
        String link = shareLink == null ? "" : shareLink.trim();
        if (!link.startsWith("polaris://")) {
            throw new IllegalArgumentException("课表分享数据无效");
        }
        byte[] bytes = (HEADER + link).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("课表分享文件过大");
        }
        return bytes;
    }

    public static String decode(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0
                || fileBytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("课表分享文件为空或过大");
        }
        String text = new String(fileBytes, StandardCharsets.UTF_8);
        if (!text.startsWith(HEADER)) {
            throw new IllegalArgumentException("不是有效的 Polaris 课表文件");
        }
        String link = text.substring(HEADER.length()).trim();
        if (!link.startsWith("polaris://")) {
            throw new IllegalArgumentException("课表分享文件缺少有效数据");
        }
        return link;
    }

    public static String read(InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("无法读取课表分享文件");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count == 0) {
                continue;
            }
            if (output.size() + count > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("课表分享文件过大");
            }
            output.write(buffer, 0, count);
        }
        return decode(output.toByteArray());
    }
}
