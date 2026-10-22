/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The document manifest embedded in a signed PDF as {@code carlos-document-manifest.json}
 * (carlos-emr/carlos#4691, carlos-emr/mycarlos#55): which clinic issued the document, for which
 * patient, and which version it is. myCarlos reads it after it has checked the PDF signature, which
 * covers it; it carries no signature of its own.
 *
 * <p>Field names match the Send to myCarlos delivery manifest where the two overlap
 * (carlos-portal {@code docs/architecture/mycarlos-delivery.md}). The JSON is written in
 * RFC 8785 canonical form, so the same manifest always gives the same bytes. Values are strings,
 * integers, nested objects or {@code null}: {@code clinicId} and {@code recipientId} are
 * {@code null} when the clinic has no portal or the patient no portal link, and any other optional
 * value is {@code null} when unknown.</p>
 *
 * @param documentId opaque ID, stable for this CARLOS document; see {@link DocumentIdCalculator}
 * @param version 1 for the first issue; a correction raises it
 * @param issuedAt when the document was issued, in Unix seconds
 * @param clinicId the clinic's portal clinic ID, or {@code null}
 * @param clinicName the issuing clinic's name, or {@code null}
 * @param providerName the provider the document is from, or {@code null}
 * @param recipientId the patient's portal recipient ID, or {@code null}
 * @param patientName the patient's name as on the chart, or {@code null}
 * @param patientBirthDate the patient's birth date as {@code yyyy-MM-dd}, or {@code null}
 * @param documentTitle the document's title, or {@code null}
 * @param documentType a short type hint such as {@code lab_report}, or {@code null}
 * @param serviceDate the date of service as {@code yyyy-MM-dd}, or {@code null}
 * @since 2026-10-21
 */
public record DocumentManifest(
        String documentId,
        int version,
        long issuedAt,
        String clinicId,
        String clinicName,
        String providerName,
        String recipientId,
        String patientName,
        String patientBirthDate,
        String documentTitle,
        String documentType,
        String serviceDate) {

    /** The embedded file's name inside the PDF. */
    public static final String FILE_NAME = "carlos-document-manifest.json";
    /** The {@code typ} value. */
    public static final String TYPE = "carlos-document-manifest";
    static final int FORMAT_VERSION = 1;
    static final String MEDIA_TYPE = "application/pdf";

    public DocumentManifest {
        Objects.requireNonNull(documentId, "documentId");
        if (documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be 1 or more");
        }
    }

    /**
     * The manifest as RFC 8785 canonical JSON, with {@code kid} set to the ID of the key that signs
     * the PDF.
     *
     * @param kid {@code b64url(SHA-256(SubjectPublicKeyInfo DER))} of the signing key
     * @return UTF-8 bytes
     */
    public byte[] toCanonicalJson(String kid) {
        Objects.requireNonNull(kid, "kid");
        Map<String, Object> patient = new TreeMap<>();
        patient.put("name", patientName);
        patient.put("birth_date", patientBirthDate);
        Map<String, Object> document = new TreeMap<>();
        document.put("title", documentTitle);
        document.put("type", documentType);
        document.put("service_date", serviceDate);
        document.put("media_type", MEDIA_TYPE);
        Map<String, Object> claims = new TreeMap<>();
        claims.put("v", (long) FORMAT_VERSION);
        claims.put("typ", TYPE);
        claims.put("document_id", documentId);
        claims.put("version", (long) version);
        claims.put("issued_at", issuedAt);
        claims.put("clinic_id", clinicId);
        claims.put("clinic_name", clinicName);
        claims.put("provider_name", providerName);
        claims.put("kid", kid);
        claims.put("recipient_id", recipientId);
        claims.put("patient", patient);
        claims.put("document", document);
        StringBuilder json = new StringBuilder();
        write(json, claims);
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * RFC 8785 for the value kinds used here. Keys sort by UTF-16 code units, which is
     * {@link String#compareTo}; integers are plain decimals; strings escape only what JSON
     * requires, with the short escapes and a lowercase six-character escape for other control
     * characters.
     */
    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Long number) {
            out.append(number.longValue());
        } else if (value instanceof String text) {
            writeString(out, text);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, (String) entry.getKey());
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
        } else {
            throw new IllegalArgumentException("Unsupported manifest value: " + value.getClass());
        }
    }

    private static void writeString(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isSurrogate(c)) {
                // RFC 8785 takes I-JSON input, which has no unpaired surrogates; UTF-8 encoding
                // would silently turn one into '?'. A valid pair is copied as it is.
                if (Character.isHighSurrogate(c) && i + 1 < text.length()
                        && Character.isLowSurrogate(text.charAt(i + 1))) {
                    out.append(c).append(text.charAt(++i));
                    continue;
                }
                throw new IllegalArgumentException("Manifest text holds an unpaired surrogate");
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
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
