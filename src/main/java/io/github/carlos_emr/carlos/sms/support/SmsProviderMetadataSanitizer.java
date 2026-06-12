package io.github.carlos_emr.carlos.sms.support;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class SmsProviderMetadataSanitizer {
    static final int MAX_ENTRIES = 25;
    static final int MAX_KEY_LENGTH = 64;
    static final int MAX_VALUE_LENGTH = 512;

    private SmsProviderMetadataSanitizer() {
    }

    public static Map<String, String> sanitize(Map<String, String> providerMetadata) {
        if (providerMetadata == null || providerMetadata.isEmpty()) {
            return Map.of();
        }

        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : providerMetadata.entrySet()) {
            if (sanitized.size() >= MAX_ENTRIES) {
                break;
            }

            String key = safeKey(entry.getKey());
            if (key == null || isSensitiveKey(key)) {
                continue;
            }

            sanitized.put(trimTo(key, MAX_KEY_LENGTH), trimTo(entry.getValue(), MAX_VALUE_LENGTH));
        }

        if (sanitized.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(sanitized);
    }

    private static String safeKey(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    // FindSecBugs IMPROPER_UNICODE: Unicode normalization is intentional before ASCII sensitive-key matching; this is redaction defense-in-depth, not authorization.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Unicode normalization is intentional before ASCII sensitive-key matching; this is redaction defense-in-depth, not authorization")
    private static boolean isSensitiveKey(String key) {
        String normalized = Normalizer.normalize(key, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]", "");
        return normalized.contains("authorization")
                || normalized.contains("apikey")
                || normalized.contains("authtoken")
                || normalized.contains("bearer")
                || normalized.contains("credential")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("signature")
                || normalized.contains("token");
    }

    private static String trimTo(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
