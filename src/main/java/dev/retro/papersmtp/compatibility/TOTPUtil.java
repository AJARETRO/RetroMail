package dev.retro.papersmtp.compatibility;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public class TOTPUtil {
    private static final int TIME_STEP = 30; // seconds
    private static final int CODE_DIGITS = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateSecretKey() {
        byte[] buffer = new byte[10]; // 80 bits
        RANDOM.nextBytes(buffer);
        return Base32.encode(buffer);
    }

    public static boolean verifyCode(String secret, int code, int window) {
        try {
            byte[] decodedKey = Base32.decode(secret);
            long currentInterval = System.currentTimeMillis() / 1000 / TIME_STEP;
            for (int i = -window; i <= window; i++) {
                if (getTOTP(decodedKey, currentInterval + i) == code) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static int getTOTP(byte[] key, long time) throws GeneralSecurityException {
        byte[] data = new byte[8];
        long value = time;
        for (int i = 8; i-- > 0; value >>>= 8) {
            data[i] = (byte) value;
        }

        SecretKeySpec signKey = new SecretKeySpec(key, "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(signKey);
        byte[] hash = mac.doFinal(data);

        int offset = hash[hash.length - 1] & 0xF;
        int truncatedHash = 0;
        for (int i = 0; i < 4; ++i) {
            truncatedHash <<= 8;
            truncatedHash |= (hash[offset + i] & 0xFF);
        }
        truncatedHash &= 0x7FFFFFFF;
        truncatedHash %= Math.pow(10, CODE_DIGITS);
        return truncatedHash;
    }

    // A tiny Base32 encoder/decoder to keep the build zero-dependency
    public static class Base32 {
        private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        private static final int[] DECODE_TABLE = new int[128];

        static {
            for (int i = 0; i < DECODE_TABLE.length; i++) {
                DECODE_TABLE[i] = -1;
            }
            for (int i = 0; i < ALPHABET.length(); i++) {
                DECODE_TABLE[ALPHABET.charAt(i)] = i;
            }
        }

        public static String encode(byte[] data) {
            StringBuilder sb = new StringBuilder();
            int i = 0, index = 0, digit = 0;
            int currByte, nextByte;
            while (i < data.length) {
                currByte = (data[i] >= 0) ? data[i] : (data[i] + 256);
                if (index > 3) {
                    if (i + 1 < data.length) {
                        nextByte = (data[i + 1] >= 0) ? data[i + 1] : (data[i + 1] + 256);
                    } else {
                        nextByte = 0;
                    }
                    digit = currByte & (0xFF >> index);
                    index = (index + 5) % 8;
                    digit <<= index;
                    digit |= nextByte >> (8 - index);
                    i++;
                } else {
                    digit = (currByte >> (8 - (index + 5))) & 0x1F;
                    index = (index + 5) % 8;
                    if (index == 0) i++;
                }
                sb.append(ALPHABET.charAt(digit));
            }
            return sb.toString();
        }

        public static byte[] decode(String base32) {
            base32 = base32.toUpperCase();
            int numBytes = base32.length() * 5 / 8;
            byte[] bytes = new byte[numBytes];
            int i = 0, index = 0, lookup = 0, offset = 0;
            for (int charIndex = 0; charIndex < base32.length(); charIndex++) {
                lookup = base32.charAt(charIndex) - '0';
                if (lookup < 0 || lookup >= DECODE_TABLE.length) {
                    continue;
                }
                int val = DECODE_TABLE[base32.charAt(charIndex)];
                if (val == -1) continue;
                if (index <= 3) {
                    index = (index + 5) % 8;
                    if (index == 0) {
                        bytes[offset] |= val;
                        offset++;
                        if (offset >= bytes.length) break;
                    } else {
                        bytes[offset] |= val << (8 - index);
                    }
                } else {
                    index = (index + 5) % 8;
                    bytes[offset] |= val >> index;
                    offset++;
                    if (offset >= bytes.length) break;
                    bytes[offset] |= val << (8 - index);
                }
            }
            return bytes;
        }
    }
}
