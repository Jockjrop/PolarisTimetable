package com.polaris.timetable.sharing;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 分享链接的对称加密（AES-256-GCM）。
 *
 * <p>密钥由应用内置种子 + 每条链接的随机盐派生，所以同一条课表每次生成的密文都不同；
 * GCM 自带完整性校验，链接被改动任何一段都会解密失败，不会导入半截数据。
 *
 * <p>定位说明：这是**防窥探**，不是**防逆向**。目标是让别人随手复制到链接时
 * 看不到课程内容、也挡不住中间人直接读明文；但拿到 APK 的人理论上能还原种子。
 * 需要更高强度时应改用口令派生，不在本类的职责范围内。
 */
public final class LinkCipher {

    private static final int SALT_BYTES = 8;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String SEED = "polaris.share.v2/2026-11";

    private LinkCipher() {
    }

    /** 加密并返回 {@code salt || iv || ciphertext+tag}。 */
    public static byte[] encrypt(byte[] plain) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(salt), new GCMParameterSpec(TAG_BITS, iv));
        byte[] body = cipher.doFinal(plain);

        byte[] packed = new byte[salt.length + iv.length + body.length];
        System.arraycopy(salt, 0, packed, 0, salt.length);
        System.arraycopy(iv, 0, packed, salt.length, iv.length);
        System.arraycopy(body, 0, packed, salt.length + iv.length, body.length);
        return packed;
    }

    /** 解密；数据被篡改或种子不匹配时抛 {@link GeneralSecurityException}。 */
    public static byte[] decrypt(byte[] packed) throws GeneralSecurityException {
        if (packed == null || packed.length <= SALT_BYTES + IV_BYTES) {
            throw new GeneralSecurityException("分享数据长度不足");
        }
        byte[] salt = Arrays.copyOfRange(packed, 0, SALT_BYTES);
        byte[] iv = Arrays.copyOfRange(packed, SALT_BYTES, SALT_BYTES + IV_BYTES);
        byte[] body = Arrays.copyOfRange(packed, SALT_BYTES + IV_BYTES, packed.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(salt), new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(body);
    }

    private static SecretKeySpec key(byte[] salt) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(SEED.getBytes(StandardCharsets.UTF_8));
        digest.update(salt);
        return new SecretKeySpec(digest.digest(), "AES");
    }
}
