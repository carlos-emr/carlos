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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Captured fake-only responses from the authenticated production Python history route. */
@Tag("unit")
class PortalEmailFooterAuditPythonFixtureUnitTest {
    private static final String PAGE_HASH = "06f3b9e743e8337d7f78d1dc11400e4252fdc8d2e334c6717d8043a46a86645e";
    private static final String CONTINUATION_HASH = "0c3284fa3017523a5face0c1a4402a28edcd3ab8c7833cbe5ccb2a5fac5ae836";
    /**
     * Captured 29 Oct from carlos-portal #27 (16e36f0752) through its authenticated history route:
     * FAKE data written by its own audit store with a fixed clock, one row per skip reason.
     */
    private static final String SKIPPED_HASH = "46a6a150e29c1beee0e93dcd65bf97184174f3cafa10b551bd33a4bfe625faf1";
    private static final String CURSOR = "01791561601000000000-fc66b481d6574e43a1c4146de61807b7";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 9);

    private String fixture(String name, String expectedHash) throws Exception {
        try (var input = getClass().getResourceAsStream("/patientportal/" + name)) {
            assertThat(input).as("captured production Python fixture").isNotNull();
            byte[] bytes = input.readAllBytes();
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))
                    .isEqualTo(expectedHash);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    @Test void shouldReadActualPythonPagesThroughSignedJavaClient_andPreserveSavedValues() throws Exception {
        String first = fixture("email-footer-audit-python-page.json", PAGE_HASH);
        String continuation = fixture("email-footer-audit-python-continuation.json", CONTINUATION_HASH);
        List<String> queries = new ArrayList<>();
        var client = client(List.of(first, continuation), queries);
        var staff = new PatientPortalStaffContext("999998", "Dr FAKE",
                Set.of(PatientPortalStaffContext.PERMISSION_EMAIL_AUDIT_READ));
        var page = client.listEmailFooterAttempts(DATE, 7, null, staff);
        assertThat(page.date()).isEqualTo(DATE);
        assertThat(page.nextBefore()).isEqualTo(CURSOR);
        assertThat(page.attempts()).extracting(PortalEmailFooterAuditPage.Attempt::kind).containsExactly(
                "booking_prompt_update", "booking_prompt", "email_change_requested",
                "email_change_confirmation", "contact_change", "password_reset", "mfa");
        assertThat(page.attempts()).extracting(PortalEmailFooterAuditPage.Attempt::status).containsExactly(
                "unknown", "accepted", "prepared", "unknown", "failed", "accepted", "prepared");
        var newest = page.attempts().get(0);
        assertThat(newest.preparedAt()).isEqualTo(Instant.parse("2026-10-09T16:00:07.000000Z"));
        assertThat(newest.statusAt()).isEqualTo(Instant.parse("2026-10-09T16:00:07.500000Z"));
        assertThat(newest.footerText()).isEqualTo("FAKE saved Clinic <script>display as text</script> & booking_prompt_update");
        assertThat(newest.revision()).isEqualTo("74fd714a12ea07b703d9b9a9d66ccf009452009a0eb5ea2077c8294689d8d302");
        assertThat(newest.logoSha256()).isEqualTo("ead3c0141832c5d7d054ba59949e768e23aab2ded5690bb2cb51706f2ebb04ed");
        assertThat(page.attempts().get(1).logoSha256()).isNull();
        assertThat(page.attempts().get(2).statusAt()).isNull();
        assertThat(page.attempts().get(6).statusAt()).isNull();
        var older = client.listEmailFooterAttempts(DATE, 7, page.nextBefore(), staff);
        assertThat(older.nextBefore()).isNull();
        assertThat(older.attempts()).hasSize(1);
        assertThat(older.attempts().get(0).attemptId()).isLessThan(CURSOR);
        assertThat(older.attempts().get(0).footerText())
                .isEqualTo("FAKE saved Clinic <script>display as text</script> & mfa");
        assertThat(queries).containsExactly("/internal/carlos/email-footer-attempts?date=2026-10-09&limit=7",
                "/internal/carlos/email-footer-attempts?date=2026-10-09&limit=7&before=" + CURSOR);
    }

    /** The signed Java client, answered by the captured Portal responses in turn. */
    private static PatientPortalService client(List<String> responses, List<String> queries) {
        return new PatientPortalService(PatientPortalSettings.fromProperties(Map.of(
                PatientPortalSettings.BASE_URL_KEY, "https://portal.clinic.example",
                PatientPortalSettings.CLINIC_ID_KEY, "clinic-a",
                PatientPortalSettings.SERVICE_TOKEN_KEY, "FAKE-portal-service-token-value-000001",
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY,
                PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "primary",
                PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN)), request -> {
            assertThat(request.getFirstHeader(PortalStaffAssertionSigner.HEADER)).isNotNull();
            queries.add(request.getRequestUri());
            return new PatientPortalHttpResponse(200, responses.get(queries.size() - 1));
        });
    }

    @Test void shouldReadPortalAttemptsWithoutFooter_inTheShapeThePortalWrites() throws Exception {
        // Sign-in codes and password resets the Portal prepared without the clinic footer (D138),
        // one per reason it writes, in every outcome state, after an ordinary attempt.
        String body = fixture("email-footer-audit-python-skipped.json", SKIPPED_HASH);
        var rows = new ObjectMapper().readTree(body).get("attempts");
        // The Portal writes footer_skipped only on a skipped attempt; an ordinary one keeps its fields.
        assertThat(rows.get(9).has("footer_skipped")).isFalse();
        for (int i = 0; i < 9; i++) assertThat(rows.get(i).get("footer_skipped").isTextual()).isTrue();
        List<String> queries = new ArrayList<>();
        var staff = new PatientPortalStaffContext("999998", "Dr FAKE",
                Set.of(PatientPortalStaffContext.PERMISSION_EMAIL_AUDIT_READ));
        var page = client(List.of(body), queries).listEmailFooterAttempts(DATE, 20, null, staff);
        assertThat(page.nextBefore()).isNull();
        var skipped = page.attempts().subList(0, 9);
        // Every reason the Portal can write is one CARLOS reads (and labels), and no other.
        assertThat(skipped).extracting(PortalEmailFooterAuditPage.Attempt::footerSkipped)
                .containsExactlyInAnyOrderElementsOf(PortalEmailFooterAuditPage.SKIP_REASONS);
        assertThat(skipped).extracting(PortalEmailFooterAuditPage.Attempt::kind).containsOnly("mfa", "password_reset");
        assertThat(skipped).extracting(PortalEmailFooterAuditPage.Attempt::status)
                .contains("prepared", "accepted", "failed", "unknown");
        for (var attempt : skipped) {
            assertThat(attempt.revision()).isNull();
            assertThat(attempt.footerText()).isNull();
            assertThat(attempt.logoSha256()).isNull();
            assertThat(attempt.statusAt() == null).isEqualTo("prepared".equals(attempt.status()));
        }
        var ordinary = page.attempts().get(9);
        assertThat(ordinary.footerSkipped()).isNull();
        assertThat(ordinary.footerText()).isEqualTo("FAKE saved Clinic footer");
        assertThat(ordinary.revision()).matches("[0-9a-f]{64}");
        assertThat(queries).containsExactly("/internal/carlos/email-footer-attempts?date=2026-10-09&limit=20");
    }
}
