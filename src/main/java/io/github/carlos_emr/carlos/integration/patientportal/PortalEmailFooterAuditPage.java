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
import io.github.carlos_emr.carlos.email.core.EmailFooterHtml;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded saved Portal email attempts; never reads current footer settings. */
public record PortalEmailFooterAuditPage(LocalDate date, List<Attempt> attempts, String nextBefore) {
    static final int MAX_PAGE_BYTES = 512 * 1024;
    private static final Set<String> PAGE_FIELDS = Set.of("date", "attempts", "next_before");
    private static final Set<String> ATTEMPT_FIELDS = Set.of("attempt_id", "kind", "prepared_at",
            "status", "status_at", "clinic_id", "revision", "footer_text", "logo_sha256");
    private static final Set<String> KINDS = Set.of("mfa", "password_reset", "contact_change",
            "email_change_confirmation", "email_change_requested", "booking_prompt", "booking_prompt_update");
    private static final Set<String> STATUSES = Set.of("prepared", "accepted", "failed", "unknown");

    public PortalEmailFooterAuditPage {
        attempts = List.copyOf(attempts);
    }
    public LocalDate getDate() { return date; }
    public List<Attempt> getAttempts() { return attempts; }
    public String getNextBefore() { return nextBefore; }
    @Override public String toString() {
        return "PortalEmailFooterAuditPage[attemptCount=" + attempts.size() + "]";
    }

    /** Display-only plaintext and truthful outcome; no recipient, body, HTML or logo bytes. */
    public record Attempt(String attemptId, String kind, Instant preparedAt, String status,
            Instant statusAt, String revision, String footerText, String logoSha256) {
        public String getAttemptId() { return attemptId; }
        public String getKind() { return kind; }
        public Instant getPreparedAt() { return preparedAt; }
        public String getStatus() { return status; }
        public Instant getStatusAt() { return statusAt; }
        public String getRevision() { return revision; }
        public String getFooterText() { return footerText; }
        public String getLogoSha256() { return logoSha256; }
        @Override public String toString() { return "PortalEmailFooterAuditAttempt[status=" + status + "]"; }
    }

    static boolean validCursor(String value) {
        return value != null && value.matches("[0-9]{20}-[0-9a-f]{32}");
    }

    static PortalEmailFooterAuditPage fromJson(JsonNode node, LocalDate date, int limit,
            String before, String clinicId) {
        exactFields(node, PAGE_FIELDS);
        if (!date.toString().equals(PortalJson.requiredText(node, "date"))) throw invalid("date");
        JsonNode rows = node.get("attempts");
        if (!rows.isArray() || rows.size() > limit) throw invalid("attempts");
        List<Attempt> attempts = new ArrayList<>();
        String previous = before;
        for (JsonNode row : rows) {
            exactFields(row, ATTEMPT_FIELDS);
            String id = PortalJson.requiredText(row, "attempt_id");
            if (!validCursor(id) || (previous != null && id.compareTo(previous) >= 0)) throw invalid("attempt_id");
            previous = id;
            if (!clinicId.equals(PortalJson.requiredText(row, "clinic_id"))) throw invalid("clinic_id");
            String kind = PortalJson.requiredText(row, "kind");
            String status = PortalJson.requiredText(row, "status");
            if (!KINDS.contains(kind) || !STATUSES.contains(status)) throw invalid("kind/status");
            Instant prepared = timestamp(PortalJson.requiredText(row, "prepared_at"));
            if (!prepared.atOffset(ZoneOffset.UTC).toLocalDate().equals(date)) throw invalid("prepared_at");
            String statusText = PortalJson.nullableText(row, "status_at");
            if ("prepared".equals(status) != (statusText == null)) throw invalid("status_at");
            Instant statusAt = statusText == null ? null : timestamp(statusText);
            // These saved wall-clock readings can move backwards after a clock correction.
            // Preserve both; the producer contract promises UTC syntax, not monotonic time.
            String revision = PortalJson.requiredText(row, "revision");
            String logoHash = PortalJson.nullableText(row, "logo_sha256");
            if (!hash(revision) || (logoHash != null && !hash(logoHash))) throw invalid("revision/logo_sha256");
            String text = PortalJson.requiredText(row, "footer_text");
            if (text.length() > 2000 || !EmailFooterHtml.hasVisibleText(text)
                    || malformedUnicode(text)) throw invalid("footer_text");
            attempts.add(new Attempt(id, kind, prepared, status, statusAt, revision, text, logoHash));
        }
        String next = PortalJson.nullableText(node, "next_before");
        if (next != null && (attempts.isEmpty() || !validCursor(next)
                || !next.equals(attempts.get(attempts.size() - 1).attemptId()))) throw invalid("next_before");
        return new PortalEmailFooterAuditPage(date, attempts, next);
    }

    private static boolean hash(String value) { return value.matches("[0-9a-f]{64}"); }
    private static boolean malformedUnicode(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) return true;
            } else if (Character.isLowSurrogate(c)) return true;
        }
        return false;
    }
    private static Instant timestamp(String value) {
        if (!value.endsWith("Z") || value.length() > 30) throw invalid("timestamp");
        try { return Instant.parse(value); }
        catch (DateTimeParseException e) { throw invalid("timestamp"); }
    }
    private static void exactFields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) throw invalid("object");
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw invalid("fields");
    }
    private static PortalContractException invalid(String field) {
        return new PortalContractException("portal footer history has invalid " + field);
    }
}
