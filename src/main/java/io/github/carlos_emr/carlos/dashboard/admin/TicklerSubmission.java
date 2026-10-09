/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.dashboard.admin;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.springframework.web.util.WebUtils;

/** Session-bound, at-most-once dashboard saves, including uncertain/partially failed saves. */
public final class TicklerSubmission {
    private static final String SESSION_KEY = TicklerSubmission.class.getName();
    private static final int LIMIT = 64;
    private static final String[] FIELDS = {"demographics", "serviceDate", "serviceTime",
            "ticklerCategoryId", "priority", "message", "messageAppend", "taskAssignedTo"};

    private TicklerSubmission() { }

    private static final class Receipt implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String patients;
        private String payload;
        private boolean success;

        private Receipt(String patients) { this.patients = patients; }

        private synchronized Boolean execute(Map<String, String[]> parameters, BooleanSupplier save) {
            if (!patients.equals(fingerprint(parameters, "demographics"))) return null;
            String submitted = fingerprint(parameters, FIELDS);
            if (payload != null) return payload.equals(submitted) ? success : null;
            // Claim before writing: exceptions or partial writes must never enable another attempt.
            payload = submitted;
            success = save.getAsBoolean();
            return success;
        }
    }

    private record Pending(LinkedHashMap<String, Receipt> receipts) implements Serializable { }

    /** Each newly opened form is a distinct operation; copied forms retain the same key. */
    public static String issue(HttpSession session, String demographics) {
        String token = UUID.randomUUID().toString();
        Receipt receipt = new Receipt(fingerprint(Map.of("demographics", new String[]{demographics}), "demographics"));
        synchronized (WebUtils.getSessionMutex(session)) {
            var receipts = copy(session);
            if (receipts.size() >= LIMIT) receipts.pollFirstEntry();
            receipts.put(token, receipt);
            session.setAttribute(SESSION_KEY, new Pending(receipts));
        }
        return token;
    }

    /** Unknown/evicted keys and altered payloads are refused; retries reuse the recorded result. */
    public static Boolean execute(HttpSession session, String token,
                                  Map<String, String[]> parameters, BooleanSupplier save) {
        if (session == null || token == null) return null;
        Receipt receipt;
        synchronized (WebUtils.getSessionMutex(session)) {
            receipt = copy(session).get(token);
        }
        return receipt == null ? null : receipt.execute(parameters, save);
    }

    private static LinkedHashMap<String, Receipt> copy(HttpSession session) {
        Object value = session.getAttribute(SESSION_KEY);
        return value instanceof Pending(var receipts) ? new LinkedHashMap<>(receipts) : new LinkedHashMap<>();
    }

    private static String fingerprint(Map<String, String[]> parameters, String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                String[] values = parameters.get(field);
                updateLength(digest, values == null ? -1 : values.length);
                if (values == null) continue;
                for (String value : values) {
                    byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
                    updateLength(digest, bytes == null ? -1 : bytes.length);
                    if (bytes != null) digest.update(bytes);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void updateLength(MessageDigest digest, int length) {
        digest.update(new byte[]{(byte) (length >>> 24), (byte) (length >>> 16),
                (byte) (length >>> 8), (byte) length});
    }
}
