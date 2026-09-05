package com.polaris.timetable.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 默认账户名与头像首字母规则（1.27.7）：
 * 默认名 = 「用户」+ 6 位随机字母数字码，且码首字符必须为字母；
 * 头像首字母取码首字符（而非所有默认账户共用的「用」字），
 * 自定义名取首字符，空名回退「用」。
 */
public class ScheduleAccountDefaultNameTest {

    private static boolean isUppercaseLetter(char candidate) {
        return candidate >= 'A' && candidate <= 'Z';
    }

    private static boolean isAlphanumeric(char candidate) {
        return isUppercaseLetter(candidate)
                || (candidate >= 'a' && candidate <= 'z')
                || (candidate >= '0' && candidate <= '9');
    }

    @Test
    public void defaultAccountName_isUserPrefixPlusSixCharCode() {
        for (int round = 0; round < 300; round++) {
            String name = ScheduleRepository.defaultAccountName();
            assertEquals(8, name.length());
            assertTrue(name.startsWith("用户"));
            String code = name.substring(2);
            assertTrue("码首字符必须是字母: " + name, isUppercaseLetter(code.charAt(0)));
            for (int index = 1; index < code.length(); index++) {
                assertTrue("码必须是字母数字组合: " + name, isAlphanumeric(code.charAt(index)));
            }
        }
    }

    @Test
    public void defaultAccountName_coversLettersAndDigitsAcrossSamples() {
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int round = 0; round < 300 && !(hasLetter && hasDigit); round++) {
            String code = ScheduleRepository.defaultAccountName().substring(2);
            for (int index = 0; index < code.length(); index++) {
                char candidate = code.charAt(index);
                hasLetter |= candidate >= 'A' && candidate <= 'Z';
                hasDigit |= candidate >= '0' && candidate <= '9';
            }
        }
        assertTrue("随机码应能抽到字母", hasLetter);
        assertTrue("随机码应能抽到数字", hasDigit);
    }

    @Test
    public void isDefaultAccountName_matchesOnlyUserPlusSixAlphanumericShape() {
        assertTrue(ScheduleRepository.isDefaultAccountName("用户A12bZ9"));
        assertTrue(ScheduleRepository.isDefaultAccountName("  用户A12bZ9  "));
        assertTrue(ScheduleRepository.isDefaultAccountName(ScheduleRepository.defaultAccountName()));
        // 历史版本可能生成过数字开头的码，形状一致仍视为默认名。
        assertTrue(ScheduleRepository.isDefaultAccountName("用户5XK2Q9"));
        assertFalse(ScheduleRepository.isDefaultAccountName(null));
        assertFalse(ScheduleRepository.isDefaultAccountName(""));
        assertFalse(ScheduleRepository.isDefaultAccountName("管理员"));
        assertFalse(ScheduleRepository.isDefaultAccountName("用户12345"));
        assertFalse(ScheduleRepository.isDefaultAccountName("用户1234567"));
        assertFalse(ScheduleRepository.isDefaultAccountName("用户ABC12!"));
        assertFalse(ScheduleRepository.isDefaultAccountName("用户ABC12中"));
        assertFalse(ScheduleRepository.isDefaultAccountName("张三"));
    }

    @Test
    public void avatarInitial_usesFirstCodeCharForDefaultNames() {
        String name = ScheduleRepository.defaultAccountName();
        char firstCodeChar = name.charAt(2);
        assertTrue(isUppercaseLetter(firstCodeChar));
        assertEquals(String.valueOf(firstCodeChar), ScheduleRepository.avatarInitial(name));
        assertEquals(String.valueOf(firstCodeChar),
                ScheduleRepository.avatarInitial("  " + name + "  "));
    }

    @Test
    public void avatarInitial_showsLegacyDigitFirstCodeAsIs() {
        assertEquals("5", ScheduleRepository.avatarInitial("用户5XK2Q9"));
    }

    @Test
    public void avatarInitial_usesFirstCharForCustomNames() {
        assertEquals("张", ScheduleRepository.avatarInitial("张三"));
        assertEquals("A", ScheduleRepository.avatarInitial("Alex"));
        assertEquals("管", ScheduleRepository.avatarInitial("管理员"));
    }

    @Test
    public void avatarInitial_emptyNameFallsBackToYong() {
        assertEquals("用", ScheduleRepository.avatarInitial(""));
        assertEquals("用", ScheduleRepository.avatarInitial(null));
        assertEquals("用", ScheduleRepository.avatarInitial("   "));
    }

    @Test
    public void avatarInitial_handlesSupplementaryCodePoints() {
        assertEquals("\uD83C\uDF1F", ScheduleRepository.avatarInitial("\uD83C\uDF1F星"));
    }
}
