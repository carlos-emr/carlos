package io.github.carlos_emr.carlos.sms.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SmsAuditRedactor {
    private SmsAuditRedactor() {
    }

    public static String redactPhoneNumber(String phoneNumber) {
        String digits = onlyDigits(phoneNumber);
        if (digits.length() < 4) {
            return "[redacted-phone]";
        }
        return "[redacted-phone:last4=" + digits.substring(digits.length() - 4) + "]";
    }

    public static String safeMessagePreview(String body) {
        String value = body == null ? "" : body;
        return "[redacted-message length=" + value.length() + " sha256=" + digest(value, 12) + "]";
    }

    public static String safeProviderPayloadPreview(String payload) {
        String value = payload == null ? "" : payload;
        return "[redacted-provider-payload length=" + value.length() + " sha256=" + digest(value, 12) + "]";
    }

    public static String digest(String value, int length) {
        int safeLength = Math.clamp(length, 1, 64);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, safeLength);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String onlyDigits(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        return digits.toString();
    }
}
