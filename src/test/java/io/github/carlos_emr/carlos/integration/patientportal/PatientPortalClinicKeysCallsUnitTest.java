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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FAKE in-memory contract proof only: no socket, persisted private key, scheduler or publication. */
@Tag("unit")
@Tag("patient-portal")
class PatientPortalClinicKeysCallsUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final KeyPair FIRST = generate("secp256r1");
    private static final KeyPair SECOND = generate("secp256r1");
    private static final PortalJobAssertionSigner PUBLISH = signer(PortalJob.KEY_PUBLISH);
    private static final PortalJobAssertionSigner BOOKING = signer(PortalJob.BOOKING_SYNC);

    private static KeyPair generate(String curve) {
        try {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(curve));
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new AssertionError("FAKE key generation failed", exception);
        }
    }

    private static String encoded(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String kid(byte[] bytes) throws Exception {
        return encoded(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static PatientPortalClinicKeysPublishRequest.Key key(KeyPair pair, String status) throws Exception {
        byte[] spki = pair.getPublic().getEncoded();
        return new PatientPortalClinicKeysPublishRequest.Key(kid(spki), encoded(spki), status, null, null);
    }

    private static PatientPortalClinicKeysPublishRequest inventory() throws Exception {
        var old = key(SECOND, "revoked");
        var active = key(FIRST, "active");
        return new PatientPortalClinicKeysPublishRequest(List.of(
                new PatientPortalClinicKeysPublishRequest.Key(active.kid(), active.spki(), "active",
                        old.kid(), encoded(new byte[64])), old));
    }

    private static PortalJobAssertionSigner signer(PortalJob job) {
        return PortalJobAssertionSigner.from(new PortalJobKey(job,
                PortalSecret.of(PortalTestKeys.JOB_PRIVATE_KEY), job.jobName() + "-FAKE-key"));
    }

    private static PatientPortalSettings settings(String origin) {
        return PatientPortalSettings.fromProperties(Map.of(
                PatientPortalSettings.BASE_URL_KEY, origin,
                PatientPortalSettings.CLINIC_ID_KEY, "FAKE-clinic",
                PatientPortalSettings.SERVICE_TOKEN_KEY, "FAKE-service-token-only-00000001",
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY,
                PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "FAKE-staff-key",
                PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN));
    }

    private static final class Exchange implements PatientPortalHttpExchange {
        private final List<ClassicHttpRequest> sent = new ArrayList<>();
        private final List<PatientPortalHttpResponse> replies = new ArrayList<>();
        Exchange reply(int status, String body) {
            replies.add(new PatientPortalHttpResponse(status, body));
            return this;
        }
        @Override
        public PatientPortalHttpResponse send(ClassicHttpRequest request) {
            sent.add(request);
            if (sent.size() > replies.size()) {
                throw new AssertionError("unexpected FAKE transport call");
            }
            return replies.get(sent.size() - 1);
        }
    }

    private static PatientPortalService service(Exchange exchange, BooleanSupplier enabled) {
        var settings = settings("https://portal.example.test");
        return new PatientPortalService(settings, exchange,
                PortalStaffAssertionSigner.from(settings.staffAssertionPrivateKey()), enabled);
    }

    private static ObjectNode acknowledgment(PatientPortalClinicKeysPublishRequest request, boolean changed) {
        ObjectNode body = JSON.createObjectNode();
        body.put("clinic_id", "FAKE-clinic");
        body.put("changed", changed);
        var keys = body.putArray("keys");
        for (var key : request.keys()) {
            var entry = keys.addObject();
            entry.put("kid", key.kid());
            entry.put("status", key.status());
            entry.put("published_at", "2026-10-23T12:00:00Z");
            if (key.status().equals("revoked")) {
                entry.put("revoked_at", "2026-10-23T13:00:00Z");
            } else {
                entry.putNull("revoked_at");
            }
            entry.put("replaces_kid", key.replacesKid());
        }
        return body;
    }

    @Test
    void shouldRefusePublication_whenDisabledOrSignedByAnotherCaller() throws Exception {
        var request = inventory();
        Exchange exchange = new Exchange();
        assertThatThrownBy(() -> service(exchange, () -> false).publishClinicKeys(request, PUBLISH))
                .isInstanceOf(PortalRequestPreparationException.class).hasMessageContaining("disabled");
        assertThatThrownBy(() -> service(exchange, () -> true).publishClinicKeys(request, BOOKING))
                .isInstanceOf(PortalRequestPreparationException.class).hasMessageContaining("key-publish");
        assertThatThrownBy(() -> service(exchange, () -> true).publishClinicKeys(request, null))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> service(exchange, () -> true).publishClinicKeys(null, PUBLISH))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalStaffContext("999998", "FAKE Provider",
                Set.of(PortalJob.PERMISSION_CLINIC_KEYS_PUBLISH)))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThat(exchange.sent).isEmpty();
    }

    @Test
    void shouldDefaultOff_withStrictDocumentedFeatureFlag() throws Exception {
        for (String value : List.of("", " ", "false", " false ")) {
            assertThat(PortalMyCarlosDeliverySettings.isEnabled(ignored -> value)).isFalse();
        }
        assertThat(PortalMyCarlosDeliverySettings.isEnabled(ignored -> null)).isFalse();
        assertThat(PortalMyCarlosDeliverySettings.isEnabled(ignored -> "true")).isTrue();
        Exchange exchange = new Exchange();
        var request = inventory();
        assertThatThrownBy(() -> service(exchange,
                () -> PortalMyCarlosDeliverySettings.isEnabled(ignored -> "FAKE-bad-flag"))
                .publishClinicKeys(request, PUBLISH))
                .isInstanceOf(PatientPortalConfigurationException.class)
                .hasMessage(PortalMyCarlosDeliverySettings.ENABLED_KEY + " must be true or false");
        assertThat(exchange.sent).isEmpty();
    }

    @Test
    void shouldPublishCompleteInventory_withBoundJobAssertionAndIdempotentReply() throws Exception {
        var request = inventory();
        var original = new ArrayList<>(request.keys());
        var immutable = new PatientPortalClinicKeysPublishRequest(original);
        original.clear();
        Exchange exchange = new Exchange().reply(200, acknowledgment(request, true).toString())
                .reply(200, acknowledgment(request, false).toString());
        var service = service(exchange, () -> true);
        assertThat(service.publishClinicKeys(immutable, PUBLISH).changed()).isTrue();
        assertThat(service.publishClinicKeys(immutable, PUBLISH).changed()).isFalse();
        ClassicHttpRequest sent = exchange.sent.get(0);
        byte[] body = sent.getEntity().getContent().readAllBytes();
        assertThat(sent.getMethod()).isEqualTo("PUT");
        assertThat(sent.getRequestUri()).isEqualTo("/internal/carlos/mycarlos/clinic-keys");
        assertThat(JSON.readTree(body)).isEqualTo(request.toJson(JSON));
        assertThat(JSON.readTree(body).get("keys")).hasSize(2);
        assertThat(sent.getFirstHeader(PortalStaffAssertionSigner.HEADER)).isNull();
        String signed = sent.getFirstHeader(PortalJobAssertionSigner.HEADER).getValue();
        assertThat(PortalTestKeys.verifies(signed, PortalTestKeys.JOB_PUBLIC_KEY)).isTrue();
        assertThat(PortalTestKeys.verifies(signed, PortalTestKeys.PUBLIC_KEY)).isFalse();
        JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(signed.split("\\.")[0]));
        assertThat(claims.get("job").asText()).isEqualTo("key-publish");
        assertThat(claims.get("permission").asText()).isEqualTo("portal.mycarlos.clinic_keys.publish");
        assertThat(claims.get("clinic_id").asText()).isEqualTo("FAKE-clinic");
        assertThat(claims.has("provider_id")).isFalse();
        assertThat(claims.get("request_hash").asText()).isEqualTo(PortalStaffAssertionSigner.requestHash(
                "PUT", URI.create("https://portal.example.test/internal/carlos/mycarlos/clinic-keys"), body));
        assertThat(new String(body, StandardCharsets.UTF_8)).doesNotContain("PRIVATE", "private_key");
        assertThatThrownBy(() -> settings("https://portal.example.test/patient-prefix"))
                .isInstanceOf(PatientPortalConfigurationException.class);
    }

    @Test
    void shouldRejectMalformedPublicKeys_withCanonicalCurveAndInventoryBounds() throws Exception {
        var valid = key(FIRST, "active");
        assertThatThrownBy(() -> new PatientPortalClinicKeysPublishRequest(List.of()))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalClinicKeysPublishRequest(Collections.nCopies(17, valid)))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalClinicKeysPublishRequest(List.of(valid, valid)))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalClinicKeysPublishRequest.Key(
                valid.kid(), valid.spki() + "=", "active", null, null))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalClinicKeysPublishRequest.Key(
                "A".repeat(43), valid.spki(), "active", null, null))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalClinicKeysPublishRequest.Key(
                valid.kid(), encoded(FIRST.getPrivate().getEncoded()), "active", null, null))
                .isInstanceOf(PortalRequestPreparationException.class);
        byte[] otherCurve = generate("secp384r1").getPublic().getEncoded();
        String otherKid = kid(otherCurve);
        assertThatThrownBy(() -> new PatientPortalClinicKeysPublishRequest.Key(
                otherKid, encoded(otherCurve), "active", null, null))
                .isInstanceOf(PortalRequestPreparationException.class);
        byte[] offCurve = FIRST.getPublic().getEncoded().clone();
        java.util.Arrays.fill(offCurve, 27, offCurve.length, (byte) 0);
        String offKid = kid(offCurve);
        assertThatThrownBy(() -> new PatientPortalClinicKeysPublishRequest.Key(
                offKid, encoded(offCurve), "active", null, null))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalClinicKeysPublishRequest.Key(
                valid.kid(), valid.spki(), "active", valid.kid(), encoded(new byte[64])))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalClinicKeysPublishRequest.Key(
                valid.kid(), valid.spki(), "active", key(SECOND, "active").kid(), null))
                .isInstanceOf(PortalRequestPreparationException.class);
    }

    @Test
    void shouldRejectAcknowledgments_forAnotherClinicOrIncompleteInventory() throws Exception {
        var request = inventory();
        var valid = acknowledgment(request, true);
        List<ObjectNode> malformed = new ArrayList<>();
        malformed.add(valid.deepCopy().put("clinic_id", "FAKE-other-clinic"));
        var missing = valid.deepCopy();
        missing.withArray("keys").remove(1);
        malformed.add(missing);
        var duplicate = valid.deepCopy();
        duplicate.withArray("keys").set(1, duplicate.withArray("keys").get(0).deepCopy());
        malformed.add(duplicate);
        var status = valid.deepCopy();
        ((ObjectNode) status.withArray("keys").get(0)).put("status", "revoked");
        malformed.add(status);
        var replacement = valid.deepCopy();
        ((ObjectNode) replacement.withArray("keys").get(0)).putNull("replaces_kid");
        malformed.add(replacement);
        var omitted = valid.deepCopy();
        omitted.remove("changed");
        malformed.add(omitted);
        malformed.add(valid.deepCopy().put("changed", "false"));
        malformed.add(valid.deepCopy().put("unexpected", "FAKE"));
        for (ObjectNode reply : malformed) {
            Exchange exchange = new Exchange().reply(200, reply.toString());
            assertThatThrownBy(() -> service(exchange, () -> true).publishClinicKeys(request, PUBLISH))
                    .isInstanceOfSatisfying(PatientPortalException.class,
                            error -> assertThat(error.kind()).isEqualTo(PatientPortalException.Kind.MALFORMED_RESPONSE));
        }
        String duplicateIdentity = "{\"clinic_id\":\"FAKE-other-clinic\"," + valid.toString().substring(1);
        Exchange duplicateExchange = new Exchange().reply(200, duplicateIdentity);
        assertThatThrownBy(() -> service(duplicateExchange, () -> true).publishClinicKeys(request, PUBLISH))
                .isInstanceOfSatisfying(PatientPortalException.class, error -> {
                    assertThat(error.kind()).isEqualTo(PatientPortalException.Kind.MALFORMED_RESPONSE);
                    assertThat(error.getCause().getMessage()).isEqualTo("clinic key acknowledgment is not valid JSON");
                });
    }

    @Test
    void shouldPreserveKnownConflicts_withoutLeakingUnknownPortalDetails() throws Exception {
        var request = inventory();
        for (String detail : List.of("key_missing", "key_revoked", "key_changed", "FAKE-untrusted-detail")) {
            Exchange exchange = new Exchange().reply(409, JSON.createObjectNode().put("detail", detail).toString());
            assertThatThrownBy(() -> service(exchange, () -> true).publishClinicKeys(request, PUBLISH))
                    .isInstanceOfSatisfying(PatientPortalException.class, error -> {
                        assertThat(error.kind()).isEqualTo(PatientPortalException.Kind.CONFLICT);
                        if (detail.startsWith("FAKE")) {
                            assertThat(error.detail()).isNull();
                            assertThat(error.getMessage()).doesNotContain(detail);
                        } else {
                            assertThat(error.detail()).isEqualTo(detail);
                        }
                    });
        }
    }

    @Test
    void shouldKeepBookingCallsIndependent_whenMyCarlosFlagIsUnreadable() {
        Exchange exchange = new Exchange().reply(200, "{\"items\":[],\"has_more\":false}");
        var result = service(exchange, () -> { throw new AssertionError("booking read the myCarlos switch"); })
                .listPendingBookingChoices(1, BOOKING);
        assertThat(result.items()).isEmpty();
        assertThat(exchange.sent).hasSize(1);
    }
}
