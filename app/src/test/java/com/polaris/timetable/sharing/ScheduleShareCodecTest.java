package com.polaris.timetable.sharing;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ScheduleShareCodecTest {

    private static Course course(int day, int start, int end, String name,
                                 String weeks, String location, String teacher) {
        return new Course(day, start, end, name, weeks, location, teacher, "",
                "3", "#4FA4F3", CourseType.LECTURE);
    }

    private static List<Course> sample() {
        return new ArrayList<>(Arrays.asList(
                course(0, 1, 2, "高等数学", "1-16周", "A101", "张老师"),
                course(2, 3, 4, "大学物理", "1-16周(单)", "B202", "李老师"),
                course(4, 5, 6, "操作系统", "1-17周", "B122", "梁琛")));
    }

    @Test
    public void packAndUnpackRoundTripsEveryField() {
        List<Course> restored = ScheduleShareCodec.unpack(
                ScheduleShareCodec.pack(sample()));

        assertEquals(3, restored.size());
        Course first = restored.get(0);
        assertEquals(0, first.day);
        assertEquals(1, first.startSection);
        assertEquals(2, first.endSection);
        assertEquals("高等数学", first.name);
        assertEquals("1-16周", first.weeks);
        assertEquals("A101", first.location);
        assertEquals("张老师", first.teacher);
        assertEquals("3", first.credit);
        assertEquals("#4FA4F3", first.color);
        assertEquals(CourseType.LECTURE, first.courseType);
        assertEquals(CourseTimeMode.SECTION, first.timeMode);
    }

    @Test
    public void packKeepsClockModeCourses() {
        Course clock = new Course(1, 1, 1, "英语听力", "1-2周", "机房", "李老师", "",
                "2", "", CourseType.LECTURE, "", "",
                CourseTimeMode.CLOCK, 10 * 60, 11 * 60 + 30);

        List<Course> restored = ScheduleShareCodec.unpack(
                ScheduleShareCodec.pack(new ArrayList<>(Arrays.asList(clock))));

        assertEquals(1, restored.size());
        assertEquals(CourseTimeMode.CLOCK, restored.get(0).timeMode);
        assertEquals(600, restored.get(0).startMinuteOfDay);
        assertEquals(690, restored.get(0).endMinuteOfDay);
    }

    @Test
    public void encryptedPayloadDoesNotContainPlaintext() throws Exception {
        byte[] encrypted = LinkCipher.encrypt(ScheduleShareCodec.pack(sample()));
        String asText = new String(encrypted, StandardCharsets.ISO_8859_1);

        assertFalse(asText.contains("高等数学"));
        assertFalse(asText.contains("张老师"));
        assertFalse(asText.contains("A101"));
    }

    @Test
    public void encryptionRoundTrips() throws Exception {
        byte[] packed = ScheduleShareCodec.pack(sample());

        byte[] restored = LinkCipher.decrypt(LinkCipher.encrypt(packed));

        assertTrue(Arrays.equals(packed, restored));
    }

    @Test
    public void tamperedCiphertextIsRejected() throws Exception {
        byte[] encrypted = LinkCipher.encrypt(ScheduleShareCodec.pack(sample()));
        encrypted[encrypted.length - 3] ^= 0x01;

        try {
            LinkCipher.decrypt(encrypted);
            fail("篡改后的数据应当解密失败");
        } catch (GeneralSecurityException expected) {
            // GCM 完整性校验生效。
        }
    }

    @Test
    public void sameScheduleProducesDifferentCiphertext() throws Exception {
        byte[] packed = ScheduleShareCodec.pack(sample());

        byte[] first = LinkCipher.encrypt(packed);
        byte[] second = LinkCipher.encrypt(packed);

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    public void packedSizeStaysCompact() throws Exception {
        byte[] packed = ScheduleShareCodec.pack(sample());
        byte[] encrypted = LinkCipher.encrypt(packed);
        // Base64URL 无填充后约为 4/3 倍；这里给出上限，防止字段悄悄膨胀。
        int base64Length = (encrypted.length * 4 + 2) / 3;

        assertTrue("packed=" + packed.length + " base64=" + base64Length,
                base64Length < 420);
    }

    @Test
    public void unpackRejectsUnknownVersion() {
        try {
            ScheduleShareCodec.unpack(new byte[]{9, 0});
            fail("未知版本应当被拒绝");
        } catch (IllegalArgumentException expected) {
            assertNotEquals("", expected.getMessage());
        }
    }

    @Test
    public void unpackSkipsInvalidCourses() {
        Course invalid = new Course(-1, 0, 0, "无时间课程", "", "", "", "");

        List<Course> restored = ScheduleShareCodec.unpack(
                ScheduleShareCodec.pack(new ArrayList<>(Arrays.asList(invalid))));

        assertTrue(restored.isEmpty());
    }

    /**
     * 相册导入的核心路径：图片像素 → RGBLuminanceSource → 解码。
     * 这里用 zxing 自己生成位图再解回来，等价于「选了张二维码截图」。
     */
    @Test
    public void decodesQrCodeFromPixelArray() throws Exception {
        String payload = "polaris://s?d=eTESTPAYLOAD123";
        com.google.zxing.common.BitMatrix matrix =
                new com.google.zxing.qrcode.QRCodeWriter().encode(payload,
                        com.google.zxing.BarcodeFormat.QR_CODE, 300, 300);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        com.google.zxing.BinaryBitmap binary = new com.google.zxing.BinaryBitmap(
                new com.google.zxing.common.HybridBinarizer(
                        new com.google.zxing.RGBLuminanceSource(width, height, pixels)));

        com.google.zxing.Result result =
                new com.google.zxing.MultiFormatReader().decode(binary);

        assertEquals(payload, result.getText());
    }
}
