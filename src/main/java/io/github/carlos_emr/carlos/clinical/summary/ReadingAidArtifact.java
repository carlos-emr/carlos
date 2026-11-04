/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The reading-aid artifact of one clinician-approved summary: the canonical JSON a device shows next
 * to the original, and the manifest claims the sender signs for it. Pure and side-effect free: no
 * record access, no clock, no key material. The shapes follow the portal delivery contract
 * (carlos-portal docs/architecture/mycarlos-delivery.md, "Reading aids: a paired second delivery"):
 * canonical JSON per RFC 8785, at most 64 KiB, 50 points, 5 evidence strings each; manifest claims
 * exactly as the contract lists them, with an opaque approval id and a role, never a reviewer name
 * and never a model name. Signing and sealing stay with the delivery sender: {@link #compactManifest}
 * takes a {@link ClinicSigner} so the sender's key inventory produces the same compact form it uses
 * for originals.
 */
public final class ReadingAidArtifact {
    public static final long VERSION = 1L;
    public static final String TYPE = "mycarlos-reading-aid";
    public static final String MANIFEST_TYPE = "mycarlos-reading-aid-manifest";
    public static final String MEDIA_TYPE = "application/vnd.carlos.reading-aid+json";
    public static final String REVIEWER_ROLE = "clinician";
    public static final int MAX_BYTES = 64 * 1024;
    public static final int MAX_POINTS = 50;
    public static final int MAX_EVIDENCE_PER_POINT = 5;
    private static final int MAX_LABEL = 240;
    private static final Pattern UUID_V4 = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*");
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    /** Signs exact payload bytes with the clinic key named by {@code kid}: ES256 as 64 bytes r ‖ s. */
    @FunctionalInterface
    public interface ClinicSigner {
        byte[] signEs256(byte[] exactPayload, String kid) throws java.io.IOException;
    }

    /** The two copies' shared identity and the original this aid reads. */
    public record Pair(String pairId, String originalDeliveryId) {
        public Pair {
            if (!UUID_V4.matcher(Objects.requireNonNull(pairId)).matches()
                    || !UUID_V4.matcher(Objects.requireNonNull(originalDeliveryId)).matches()) throw invalid();
        }
    }

    /** What the sender knows about the copy's route and the chart; the same values as the original's manifest. */
    public record Routing(String deliveryId, String recipientId, String deviceKeyId, String clinicId,
            String clinicName, String providerName, String patientName, String birthDate) {
        public Routing {
            if (!UUID_V4.matcher(Objects.requireNonNull(deliveryId)).matches()) throw invalid();
            for (String value : List.of(recipientId, deviceKeyId, clinicId, clinicName, providerName, patientName, birthDate)) {
                label(value, MAX_LABEL);
            }
        }
    }

    private final byte[] json;
    private final String sha256;
    private final String language;
    private final String summaryVersion;
    private final String originalSha256;
    private final long generatedAt;
    private final Long revisedAt;
    private final String approvalId;
    private final long reviewedAt;

    private ReadingAidArtifact(byte[] json, String language, DocumentSummaryReviewBinding.Approval approval, String approvalId) {
        this.json = json;
        this.sha256 = sha256Hex(json);
        this.language = language;
        this.summaryVersion = approval.summaryVersion();
        this.originalSha256 = approval.sourceSha256();
        this.generatedAt = seconds(approval.generatedAt());
        this.revisedAt = approval.revisedAt() == null ? null : seconds(approval.revisedAt());
        this.approvalId = approvalId;
        this.reviewedAt = seconds(approval.reviewedAt());
    }

    /**
     * Builds the artifact of {@code approval} for the exact {@code originalBytes} it was approved
     * against; {@code approvalId} is the sender's opaque reference to the persisted approval.
     *
     * @throws IllegalStateException when there is no approval, or the original is not the approved one
     * @throws IllegalArgumentException when the approved content does not fit the contract's limits
     */
    public static ReadingAidArtifact of(DocumentSummaryReviewBinding.Approval approval, byte[] originalBytes, String approvalId) {
        if (approval == null) throw new IllegalStateException("A recorded clinician approval is required.");
        if (originalBytes == null || !approval.sourceSha256().equals(sha256Hex(originalBytes))) {
            throw new IllegalStateException("The original is not the document this summary was approved against.");
        }
        label(approvalId, 64);
        String language = approval.language();
        if (language == null || !LANGUAGE.matcher(language).matches()) throw invalid();
        DocumentSummary summary = approval.summary();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("v", VERSION);
        value.put("typ", TYPE);
        value.put("language", language);
        Map<String, Object> notice = new LinkedHashMap<>();
        notice.put("version", DocumentSummaryReviewBinding.NOTICE_VERSION);
        notice.put("heading", DocumentSummaryReviewBinding.noticeHeading());
        notice.put("body", DocumentSummaryReviewBinding.noticeBody());
        value.put("notice", notice);
        value.put("overview", text(summary.overview(), 4000));
        List<Map<String, Object>> points = summary.points();
        if (points.isEmpty() || points.size() > MAX_POINTS) throw invalid();
        List<Object> out = new ArrayList<>(points.size());
        for (Map<String, Object> point : points) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("text", text(point.get("text"), 2000));
            if (!(point.get("evidence") instanceof List<?> evidence) || evidence.isEmpty()
                    || evidence.size() > MAX_EVIDENCE_PER_POINT) throw invalid();
            List<Object> excerpts = new ArrayList<>(evidence.size());
            for (Object excerpt : evidence) excerpts.add(text(excerpt, 800));
            item.put("evidence", excerpts);
            out.add(item);
        }
        value.put("points", out);
        byte[] json = canonical(value);
        if (json.length > MAX_BYTES) throw invalid();
        return new ReadingAidArtifact(json, language, approval, approvalId);
    }

    /** The exact canonical JSON bytes the device receives (a copy). */
    public byte[] json() { return json.clone(); }
    public int size() { return json.length; }
    public String sha256() { return sha256; }
    public String language() { return language; }
    public String summaryVersion() { return summaryVersion; }
    public String originalSha256() { return originalSha256; }

    /**
     * The aid's manifest claims, exactly the contract's fifteen, in canonical key order. The sender
     * supplies the pair, the route and chart values it used for the original's manifest, the pair's
     * shared lifetime, and the signing key id.
     */
    public Map<String, Object> manifestClaims(Pair pair, Routing routing, long createdAt, long expiresAt, String kid) {
        Objects.requireNonNull(pair); Objects.requireNonNull(routing); label(kid, MAX_LABEL);
        if (createdAt < 0 || expiresAt <= createdAt || createdAt < reviewedAt) throw invalid();
        if (pair.originalDeliveryId().equals(routing.deliveryId())) throw invalid();
        Map<String, Object> claims = new TreeMap<>();
        claims.put("v", VERSION);
        claims.put("typ", MANIFEST_TYPE);
        claims.put("delivery_id", routing.deliveryId());
        claims.put("recipient_id", routing.recipientId());
        claims.put("device_key_id", routing.deviceKeyId());
        claims.put("clinic_id", routing.clinicId());
        claims.put("clinic_name", routing.clinicName());
        claims.put("provider_name", routing.providerName());
        claims.put("patient", sorted(Map.of("name", routing.patientName(), "birth_date", routing.birthDate())));
        claims.put("pair", sorted(Map.of("pair_id", pair.pairId(), "original_delivery_id", pair.originalDeliveryId(),
                "original_document_sha256", originalSha256)));
        Map<String, Object> aid = new TreeMap<>();
        aid.put("media_type", MEDIA_TYPE);
        aid.put("size", (long) json.length);
        aid.put("sha256", sha256);
        aid.put("language", language);
        aid.put("summary_version", summaryVersion);
        aid.put("generated_at", generatedAt);
        aid.put("revised_at", revisedAt);
        aid.put("notice_version", DocumentSummaryReviewBinding.NOTICE_VERSION);
        claims.put("reading_aid", aid);
        claims.put("approval", sorted(Map.of("id", approvalId, "reviewer_role", REVIEWER_ROLE, "reviewed_at", reviewedAt)));
        claims.put("created_at", createdAt);
        claims.put("expires_at", expiresAt);
        claims.put("kid", kid);
        return claims;
    }

    /** The manifest payload the clinic key signs: the claims as canonical JSON. */
    public byte[] manifestPayload(Pair pair, Routing routing, long createdAt, long expiresAt, String kid) {
        return canonical(manifestClaims(pair, routing, createdAt, expiresAt, kid));
    }

    /**
     * The compact signed manifest, {@code b64url(payload) "." b64url(signature)}, the form the
     * delivery sender uses for an original's manifest. The signer must return 64 bytes; the sender
     * verifies them against its published key as it does for originals.
     */
    public String compactManifest(Pair pair, Routing routing, long createdAt, long expiresAt, String kid, ClinicSigner signer)
            throws java.io.IOException {
        byte[] payload = manifestPayload(pair, routing, createdAt, expiresAt, kid);
        byte[] signature = Objects.requireNonNull(signer).signEs256(payload.clone(), kid);
        if (signature == null || signature.length != 64) throw invalid();
        return B64URL.encodeToString(payload) + "." + B64URL.encodeToString(signature);
    }

    /** The plaintext frame parts the sender seals: label, compact manifest, aid; the sealing is the original's. */
    public static final String PLAINTEXT_LABEL = "mycarlos-reading-aid/1";

    // --- Canonical JSON (RFC 8785) for this artifact's value kinds -----------------------------------

    /**
     * RFC 8785 for objects, arrays, strings, exact non-negative integers and null. Keys sort by UTF-16
     * code units; strings escape only what JSON requires, with the short forms, and are written as
     * UTF-8. The same subset the delivery sender canonicalizes, plus arrays and null, which this
     * artifact needs ({@code points}, {@code evidence}, {@code revised_at}).
     */
    static byte[] canonical(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw invalid();
                sorted.put(key, entry.getValue());
            }
            out.append('{');
            boolean first = true;
            for (var entry : sorted.entrySet()) {
                if (!first) out.append(',');
                first = false;
                string(out, entry.getKey());
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) out.append(',');
                first = false;
                write(out, item);
            }
            out.append(']');
        } else if (value instanceof String string) {
            string(out, string);
        } else if ((value instanceof Long || value instanceof Integer) && ((Number) value).longValue() >= 0
                && ((Number) value).longValue() <= 9_007_199_254_740_991L) {
            out.append(((Number) value).longValue());
        } else {
            throw invalid();
        }
    }

    private static void string(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isSurrogate(c)) {
                if (!Character.isHighSurrogate(c) || i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw invalid();
                }
                out.append(c).append(value.charAt(++i));
                continue;
            }
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00").append("0123456789abcdef".charAt(c >>> 4)).append("0123456789abcdef".charAt(c & 15));
                    } else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private static Map<String, Object> sorted(Map<String, Object> values) { return new TreeMap<>(values); }

    private static String text(Object value, int maximum) {
        if (!(value instanceof String string) || string.isBlank() || string.length() > maximum
                || string.indexOf('\r') >= 0) throw invalid();
        return string;
    }

    private static void label(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum
                || value.codePoints().anyMatch(Character::isISOControl)) throw invalid();
    }

    private static long seconds(Instant instant) {
        long value = Objects.requireNonNull(instant).getEpochSecond();
        if (value < 0) throw invalid();
        return value;
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Reading-aid artifact data is invalid."); }
}
