/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * You can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Contract tests for the assertion verified by carlos-portal's {@code staff_identity.py}. */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PortalStaffAssertionSigner")
class PortalStaffAssertionSignerUnitTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
    private static final UUID ASSERTION_ID =
            UUID.fromString("7dfabfa5-9e46-4a0b-88e7-c5c0dc1dfda2");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("should emit the portal's exact claims with a bounded lifetime")
    void shouldCreateExactClaims_forAuthenticatedStaff() throws Exception {
        PortalStaffAssertionSigner signer = signer();
        PatientPortalStaffContext staff =
                new PatientPortalStaffContext(
                        "999998",
                        "Dr Łukasz 李",
                        Set.of(
                                PatientPortalStaffContext.PERMISSION_INVITE_MANAGE,
                                PatientPortalStaffContext.PERMISSION_ACCOUNT_UNLOCK));

        String assertion = signer.sign(staff, "maplecreek");
        JsonNode payload = payload(assertion);

        assertThat(payload.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "iss",
                        "aud",
                        "iat",
                        "exp",
                        "jti",
                        "provider_id",
                        "provider_name",
                        "clinic_id",
                        "permissions");
        assertThat(payload.get("iss").asText()).isEqualTo("carlos");
        assertThat(payload.get("aud").asText())
                .isEqualTo("carlos-patient-portal-internal-api");
        assertThat(payload.get("iat").asLong()).isEqualTo(NOW.getEpochSecond());
        assertThat(payload.get("exp").asLong() - payload.get("iat").asLong()).isEqualTo(60L);
        assertThat(payload.get("jti").asText()).isEqualTo(ASSERTION_ID.toString());
        assertThat(payload.get("provider_id").asText()).isEqualTo("999998");
        assertThat(payload.get("provider_name").asText()).isEqualTo("Dr Łukasz 李");
        assertThat(payload.get("clinic_id").asText()).isEqualTo("maplecreek");
        assertThat(payload.get("permissions").toString())
                .isEqualTo("[\"portal.account.unlock\",\"portal.invite.manage\"]");
    }

    @Test
    @DisplayName("should produce a signature accepted by the configured portal public key")
    void shouldSignPayload_withMatchingEd25519Key() throws Exception {
        String assertion =
                signer()
                        .sign(
                                new PatientPortalStaffContext(
                                        "999998",
                                        "Dr Example",
                                        Set.of(
                                                PatientPortalStaffContext.PERMISSION_INVITE_MANAGE)),
                                "maplecreek");
        String[] parts = assertion.split("\\.", -1);

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey());
        verifier.update(Base64.getUrlDecoder().decode(parts[0]));

        assertThat(parts).hasSize(2);
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[1]))).isTrue();
        assertThat(assertion).doesNotContain("=");
    }

    @Test
    @DisplayName("should never render private key material")
    void shouldRedactSigner_whenRendered() throws Exception {
        assertThat(signer().toString())
                .contains("REDACTED")
                .doesNotContain(PortalTestKeys.PRIVATE_KEY);
    }

    private PortalStaffAssertionSigner signer() throws Exception {
        return new PortalStaffAssertionSigner(
                privateKey(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> ASSERTION_ID,
                MAPPER);
    }

    private JsonNode payload(String assertion) throws Exception {
        String[] parts = assertion.split("\\.", -1);
        assertThat(parts).hasSize(2);
        return MAPPER.readTree(Base64.getUrlDecoder().decode(parts[0]));
    }

    private PrivateKey privateKey() throws Exception {
        byte[] encoded = Base64.getUrlDecoder().decode(PortalTestKeys.PRIVATE_KEY);
        return KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private PublicKey publicKey() throws Exception {
        byte[] prefix = HexFormat.of().parseHex("302a300506032b6570032100");
        byte[] raw = Base64.getUrlDecoder().decode(PortalTestKeys.PUBLIC_KEY);
        byte[] encoded = new byte[prefix.length + raw.length];
        System.arraycopy(prefix, 0, encoded, 0, prefix.length);
        System.arraycopy(raw, 0, encoded, prefix.length, raw.length);
        return KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(encoded));
    }
}
