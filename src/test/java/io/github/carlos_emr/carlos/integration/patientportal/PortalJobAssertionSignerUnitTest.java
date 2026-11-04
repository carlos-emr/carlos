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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the job assertion verified by carlos-portal's {@code job_identity.py}. The
 * shared vectors in {@code job-assertion-contract.json} are a byte-for-byte copy of the portal's
 * {@code tests/fixtures/job-assertion-contract.json}.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PortalJobAssertionSigner")
class PortalJobAssertionSignerUnitTest extends CarlosUnitTestBase {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final UUID ASSERTION_ID = UUID.fromString("5b0f8c7e-2d41-4a9b-8f3e-6c1d2a7b9e40");
    private static final String KID = "booking-sync-20261020-a1b2c3";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final URI CHOICES =
            URI.create("https://portal.example/internal/carlos/booking-prompts/choices?state=pending&limit=100");

    @Test
    @DisplayName("should emit exactly the job claims, one permission and a 60 second life")
    void shouldEmitExactClaims_forTheBookingSyncJob() throws Exception {
        JsonNode payload = payload(signer(PortalTestKeys.JOB_PRIVATE_KEY, KID, ASSERTION_ID.toString())
                .sign(PortalJob.PERMISSION_BOOKING_PROMPT_SYNC, "clinic-a", "GET", CHOICES, new byte[0]));

        // Sorted, so CARLOS writes the same bytes the portal's fixture was signed over.
        assertThat(payload.fieldNames()).toIterable().containsExactly(
                "aud", "clinic_id", "exp", "iat", "iss", "job", "jti", "kid", "permission", "request_hash");
        assertThat(payload.get("aud").asText()).isEqualTo("carlos-patient-portal-job-api");
        assertThat(payload.get("iss").asText()).isEqualTo("carlos");
        assertThat(payload.get("clinic_id").asText()).isEqualTo("clinic-a");
        assertThat(payload.get("iat").asLong()).isEqualTo(NOW.getEpochSecond());
        assertThat(payload.get("exp").asLong() - payload.get("iat").asLong()).isEqualTo(60L);
        assertThat(payload.get("job").asText()).isEqualTo("booking-sync");
        assertThat(payload.get("jti").asText()).isEqualTo(ASSERTION_ID.toString());
        assertThat(payload.get("kid").asText()).isEqualTo(KID);
        // One string, never a list: the portal refuses a list of permissions from a job.
        assertThat(payload.get("permission").isTextual()).isTrue();
        assertThat(payload.get("permission").asText()).isEqualTo("portal.booking_prompt.sync");
        assertThat(payload.get("request_hash").asText())
                .isEqualTo(PortalStaffAssertionSigner.requestHash("GET", CHOICES, new byte[0]));
        assertThat(payload.has("provider_id")).isFalse();
        assertThat(payload.has("permissions")).isFalse();
    }

    @Test
    @DisplayName("should refuse to sign a permission the job does not hold")
    void shouldRefuseToSign_whenTheJobDoesNotHoldThePermission() throws Exception {
        PortalJobAssertionSigner signer = signer(PortalTestKeys.JOB_PRIVATE_KEY, KID, ASSERTION_ID.toString());
        for (String permission : new String[] {
                PatientPortalStaffContext.PERMISSION_INVITE_MANAGE, PatientPortalStaffContext.PERMISSION_BOOKING_PROMPT_MANAGE,
                "portal.signup.manage", "", null}) {
            assertThatThrownBy(() -> signer.sign(permission, "clinic-a", "GET", CHOICES, new byte[0]))
                    .isInstanceOf(PortalRequestPreparationException.class)
                    .hasMessageContaining("does not hold");
        }
    }

    @Test
    @DisplayName("should compute every shared request hash and signed assertion byte for byte")
    void shouldMatchSharedVectors_forExactWireBytes() throws Exception {
        JsonNode fixture = fixture();
        // The fixture's key is RFC 8032 TEST 1, the same published test key as PortalTestKeys.PRIVATE_KEY.
        assertThat(fixture.get("public_key").asText()).isEqualTo(PortalTestKeys.PUBLIC_KEY);
        assertThat(fixture.get("assertion_header").asText()).isEqualTo(PortalJobAssertionSigner.HEADER);
        assertThat(fixture.get("audience").asText()).isEqualTo(PortalJobAssertionSigner.AUDIENCE);
        assertThat(fixture.get("issuer").asText()).isEqualTo(PortalJobAssertionSigner.ISSUER);
        assertThat(fixture.get("job").asText()).isEqualTo(PortalJob.BOOKING_SYNC.jobName());
        assertThat(fixture.get("expires_at").asLong() - fixture.get("issued_at").asLong())
                .isEqualTo(PortalJobAssertionSigner.LIFETIME.toSeconds());
        assertThat(fixture.get("vectors")).hasSize(2);
        for (JsonNode vector : fixture.get("vectors")) {
            URI uri = URI.create(vector.get("uri").asText());
            byte[] body = vector.get("body").asText().getBytes(StandardCharsets.UTF_8);
            String method = vector.get("method").asText();
            assertThat(PortalStaffAssertionSigner.requestHash(method, uri, body))
                    .as(vector.get("name").asText())
                    .isEqualTo(vector.get("request_hash").asText());

            PortalJobAssertionSigner signer = new PortalJobAssertionSigner(PortalJob.BOOKING_SYNC,
                    fixture.get("kid").asText(), privateKey(PortalTestKeys.PRIVATE_KEY),
                    Clock.fixed(Instant.ofEpochSecond(fixture.get("issued_at").asLong()), ZoneOffset.UTC),
                    () -> UUID.fromString(vector.get("jti").asText()), MAPPER);
            String signed = signer.sign(vector.get("permission").asText(), fixture.get("clinic_id").asText(),
                    method, uri, body);

            assertThat(new String(Base64.getUrlDecoder().decode(signed.split("\\.")[0]), StandardCharsets.UTF_8))
                    .isEqualTo(vector.get("payload").asText());
            assertThat(signed).isEqualTo(vector.get("assertion").asText());
            assertThat(PortalTestKeys.verifies(signed, fixture.get("public_key").asText())).isTrue();
        }
    }

    @Test
    @DisplayName("should sign with the job's own key, which the staff public key does not verify")
    void shouldSignWithTheJobsOwnKey_neverTheStaffKey() throws Exception {
        PortalJobKey key = PortalJobKey.fromProperties(PortalJob.BOOKING_SYNC, Map.of(
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY,
                PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "primary",
                PortalJob.BOOKING_SYNC.privateKeyProperty(), PortalTestKeys.JOB_PRIVATE_KEY,
                PortalJob.BOOKING_SYNC.keyIdProperty(), KID)::get).orElseThrow();
        PortalJobAssertionSigner signer = PortalJobAssertionSigner.from(key);

        String signed = signer.sign(PortalJob.PERMISSION_BOOKING_PROMPT_SYNC, "clinic-a", "GET", CHOICES, new byte[0]);

        assertThat(signer.job()).isEqualTo(PortalJob.BOOKING_SYNC);
        assertThat(signer.keyId()).isEqualTo(KID);
        assertThat(PortalTestKeys.verifies(signed, PortalTestKeys.JOB_PUBLIC_KEY)).isTrue();
        assertThat(PortalTestKeys.verifies(signed, PortalTestKeys.PUBLIC_KEY)).isFalse();
        assertThat(signed).doesNotContain("=");
    }

    @Test
    @DisplayName("should use a fresh assertion id for every call")
    void shouldIssueFreshNonce_forEachCall() throws Exception {
        PortalJobAssertionSigner signer = PortalJobAssertionSigner.from(new PortalJobKey(
                PortalJob.BOOKING_SYNC, PortalSecret.of(PortalTestKeys.JOB_PRIVATE_KEY), KID));
        JsonNode first = payload(signer.sign(PortalJob.PERMISSION_BOOKING_PROMPT_SYNC, "clinic-a", "GET", CHOICES,
                new byte[0]));
        JsonNode second = payload(signer.sign(PortalJob.PERMISSION_BOOKING_PROMPT_SYNC, "clinic-a", "GET", CHOICES,
                new byte[0]));
        assertThat(first.get("jti").asText()).isNotEqualTo(second.get("jti").asText());
        assertThat(UUID.fromString(first.get("jti").asText()).toString()).isEqualTo(first.get("jti").asText());
    }

    @Test
    @DisplayName("should never render private key material")
    void shouldRedactTheKey_whenRendered() throws Exception {
        PortalJobKey key = new PortalJobKey(PortalJob.BOOKING_SYNC, PortalSecret.of(PortalTestKeys.JOB_PRIVATE_KEY), KID);
        assertThat(PortalJobAssertionSigner.from(key).toString())
                .contains("REDACTED", "booking-sync", KID)
                .doesNotContain(PortalTestKeys.JOB_PRIVATE_KEY);
        assertThat(key.toString()).contains("REDACTED").doesNotContain(PortalTestKeys.JOB_PRIVATE_KEY);
    }

    private PortalJobAssertionSigner signer(String encodedKey, String kid, String jti) throws Exception {
        return new PortalJobAssertionSigner(PortalJob.BOOKING_SYNC, kid, privateKey(encodedKey),
                Clock.fixed(NOW, ZoneOffset.UTC), () -> UUID.fromString(jti), MAPPER);
    }

    private static JsonNode fixture() throws Exception {
        try (var input = PortalJobAssertionSignerUnitTest.class
                .getResourceAsStream("/patientportal/job-assertion-contract.json")) {
            assertThat(input).isNotNull();
            return MAPPER.readTree(input);
        }
    }

    private static JsonNode payload(String assertion) throws Exception {
        String[] parts = assertion.split("\\.", -1);
        assertThat(parts).hasSize(2);
        return MAPPER.readTree(Base64.getUrlDecoder().decode(parts[0]));
    }

    private static PrivateKey privateKey(String encoded) throws Exception {
        return KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getUrlDecoder().decode(encoded)));
    }
}
