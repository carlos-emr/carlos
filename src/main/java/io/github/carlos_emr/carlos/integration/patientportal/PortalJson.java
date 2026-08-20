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
package io.github.carlos_emr.carlos.integration.patientportal;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Field readers shared by the portal response types.
 *
 * <p>Two kinds of reader live here, and the distinction is the point:
 *
 * <ul>
 *   <li><b>Optional readers</b> ({@link #text}, {@link #optionalLong}, {@link #optionalInt}) return
 *       {@code null} when a field is absent or JSON null. Use these only where the portal genuinely
 *       may omit the field.
 *   <li><b>Required readers</b> ({@link #requiredLong}, {@link #requiredInt}, {@link #requiredBool})
 *       throw when a field is absent, null, or of the wrong JSON type. Use these for identifiers and
 *       safety-relevant flags, where Jackson's convenience accessors would coerce a missing field to
 *       {@code 0} or {@code false} and turn a portal contract change into confidently wrong data.
 * </ul>
 *
 * <p>These readers do not attempt to be strict about coercion beyond what is stated: {@link #text}
 * renders any non-null node through {@code asText()}, so a field that changed from string to number
 * yields its text form rather than an error.
 *
 * @since 2026-08-19
 */
final class PortalJson {

    private static final String MISSING_FIELD = "portal response is missing required field '%s'";
    private static final String OUT_OF_RANGE =
            "the portal sent %s as a number too large for the identifier CARLOS expects";
    private static final String WRONG_TYPE = "portal field '%s' is not of the expected JSON type";

    private PortalJson() {}

    static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    static Long optionalLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    static Integer optionalInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    /**
     * Reads an identifier that the portal must supply.
     *
     * @throws PortalContractException if the field is absent, null, or not a number
     */
    static long requiredLong(JsonNode node, String field) {
        JsonNode value = present(node, field);
        if (!value.isNumber()) {
            throw new PortalContractException(String.format(Locale.ROOT, WRONG_TYPE, field));
        }
        return value.asLong();
    }

    /**
     * Reads an identifier that the portal must supply and that CARLOS models as an {@code int}.
     *
     * <p>Out-of-range is a contract violation like any other, not an arithmetic accident.
     * {@code Math.toIntExact} was used here and throws {@link ArithmeticException}, which is not a
     * {@link PortalContractException} and so escapes the funnel in {@code PatientPortalService}
     * that turns contract violations into {@code MALFORMED_RESPONSE} — landing on a generic CARLOS
     * error page instead, the exact outcome this exception type exists to prevent.
     *
     * @throws PortalContractException if the field is absent, null, not a number, or too large to
     *     be the identifier CARLOS expects
     */
    static int requiredInt(JsonNode node, String field) {
        long value = requiredLong(node, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new PortalContractException(String.format(Locale.ROOT, OUT_OF_RANGE, field));
        }
        return (int) value;
    }

    /**
     * Reads a flag that the portal must supply.
     *
     * <p>Required rather than optional because every boolean in this package defaults to the
     * reassuring answer when absent — {@code force_password_reset=false} reads as "the patient can
     * sign in", {@code created=false} reads as "no second passphrase was minted". A portal that
     * stopped emitting one of these must fail loudly, not quietly agree.
     *
     * @throws PortalContractException if the field is absent, null, or not a boolean
     */
    static boolean requiredBool(JsonNode node, String field) {
        JsonNode value = present(node, field);
        if (!value.isBoolean()) {
            throw new PortalContractException(String.format(Locale.ROOT, WRONG_TYPE, field));
        }
        return value.asBoolean();
    }

    private static JsonNode present(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new PortalContractException(String.format(Locale.ROOT, MISSING_FIELD, field));
        }
        return value;
    }

    /**
     * Parses a portal timestamp, with or without an offset.
     *
     * <p>The portal emits the same field two different ways, which is not obvious and is not
     * documented anywhere on that side. Verified against a live portal:
     *
     * <pre>
     *   create  last_issued_at: 2026-08-19T19:18:56.278540Z
     *   resend  last_issued_at: 2026-08-19T19:18:56.394257Z
     *   list    last_issued_at: 2026-08-19T19:18:56.278540     &lt;- no offset
     *   revoke  last_issued_at: 2026-08-19T19:18:27.892796     &lt;- no offset
     * </pre>
     *
     * <p>The split is by provenance, not by endpoint: a value freshly constructed in the portal
     * process is timezone-aware and serializes with {@code Z}, while a value that has round-tripped
     * through the database comes back naive, because SQLite does not persist {@code tzinfo}. A
     * PostgreSQL deployment using {@code timestamptz} would not lose it — so a parser that accepted
     * only the offset form worked in unit tests, worked against PostgreSQL, and failed every list
     * and revoke call on the SQLite demo and development path.
     *
     * <p>A naive value is therefore read as UTC rather than rejected. That is not a guess: the
     * portal's models timestamp with {@code utc_now()}, so the offset is lost in storage rather than
     * being unknown. Reading it as local time would silently shift every date by the server's
     * offset, which for a seven-day invite expiry is the difference between valid and expired.
     *
     * @throws PortalContractException if the value is neither form of ISO-8601 date-time
     */
    static Instant timestamp(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException withoutOffset) {
            try {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException notATimestamp) {
                // The cause is deliberately dropped. DateTimeParseException embeds the value it
                // choked on — "Text 'patient@example.com' could not be parsed" — and this exception
                // is logged with its full cause chain when it reaches the web layer, so chaining it
                // writes portal data straight into the CARLOS log. The field name is already in the
                // message, and the value is the one thing that must not be kept.
                throw new PortalContractException(String.format(Locale.ROOT, WRONG_TYPE, field));
            }
        }
    }
}
