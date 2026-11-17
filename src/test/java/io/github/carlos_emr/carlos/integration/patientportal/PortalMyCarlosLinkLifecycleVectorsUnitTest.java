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
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Actual Java production proof/graph/producer-transition checks against the unchanged shared bytes.
 * Device recovery authorization, notice durability and bootstrap consent are portal/native owners;
 * their presence in the fixture is not claimed as Java producer implementation or test coverage.
 */
@Tag("unit")
@Tag("patient-portal")
class PortalMyCarlosLinkLifecycleVectorsUnitTest {
    private static final String FIXTURE_SHA =
            "4a4ef7b6ae5e0531b1c377aeb5aee5c603c3919b6fce909a20a0f80e8e7b8879";
    private static final String FROZEN_SHA =
            "87c2cc6446fb5884ecad0c77c714178288934f4525f17722a6bc4966a75eaafa";
    private static final JsonNode FIXTURE = fixture();

    private static JsonNode fixture() {
        try {
            byte[] bytes = resource("link-lifecycle-v1.json");
            assertThat(sha(bytes)).isEqualTo(FIXTURE_SHA);
            JsonNode root = new ObjectMapper().readTree(bytes);
            assertThat(root.get("frozen_contract_sha256").textValue()).isEqualTo(FROZEN_SHA);
            assertThat(sha(resource("delivery-contract.json")))
                    .isEqualTo(root.get("existing_delivery_fixture_sha256").textValue());
            return root;
        } catch (Exception failure) {
            throw new AssertionError("shared lifecycle fixture provenance is invalid", failure);
        }
    }

    private static byte[] resource(String file) throws IOException {
        try (var input = PortalMyCarlosLinkLifecycleVectorsUnitTest.class
                .getResourceAsStream("/patientportal/" + file)) {
            if (input == null) {
                throw new IOException("shared fixture is not installed");
            }
            return input.readAllBytes();
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static Stream<Arguments> proofVectors() {
        return vectors("proof_vectors");
    }

    static Stream<Arguments> refreshVectors() {
        return vectors("inventory_vectors")
                .filter(arguments -> "refresh".equals(((JsonNode) arguments.get()[1]).get("mode").textValue()));
    }

    static Stream<Arguments> bootstrapVectors() {
        return vectors("inventory_vectors")
                .filter(arguments -> "bootstrap".equals(((JsonNode) arguments.get()[1]).get("mode").textValue()));
    }

    private static Stream<Arguments> vectors(String field) {
        return StreamSupport.stream(FIXTURE.get(field).spliterator(), false)
                .map(node -> Arguments.of(node.get("name").textValue(), node));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("proofVectors")
    void shouldMatchSharedProofBytes_usingTheProductionJcaVerifier(String name, JsonNode vector) throws Exception {
        boolean expected = vector.get("expect_valid").booleanValue();
        try {
            byte[] oldSpki = Base64.getUrlDecoder().decode(vector.get("predecessor_spki").textValue());
            var old = new PatientPortalClinicKeysPublishRequest.Key(
                    Base64.getUrlEncoder().withoutPadding().encodeToString(
                            MessageDigest.getInstance("SHA-256").digest(oldSpki)),
                    vector.get("predecessor_spki").textValue(), "active", null, null);
            var next = new PatientPortalClinicKeysPublishRequest.Key(
                    vector.get("kid").textValue(), vector.get("spki").textValue(), "active",
                    vector.get("replaces_kid").textValue(), vector.get("signature").textValue());
            String clinic = vector.get("clinic_id").textValue();
            PortalMyCarlosKeyReplacementProofs.validateAll(
                    clinic, new PatientPortalClinicKeysPublishRequest(List.of(old, next)));
            assertThat(expected).as(name).isTrue();
            assertThat(HexFormat.of().formatHex(
                    PortalMyCarlosKeyReplacementProofs.statement(clinic, old, next)))
                    .as(name + " exact frozen statement").isEqualTo(vector.get("statement_hex").textValue());
        } catch (IllegalArgumentException refusal) {
            assertThat(expected).as(name).isFalse();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refreshVectors")
    void shouldMatchSharedCompleteInventoryTransitions_preservingRefusedPreviousState(
            String name, JsonNode vector) {
        String clinic = FIXTURE.get("keys").get("A").get("clinic_id").textValue();
        var previous = PortalMyCarlosKeyInventory.create(clinic, keys(vector.get("previous")));
        String preserved = previous.toStoredJson();
        if (vector.get("expect_accept").booleanValue()) {
            var next = previous.replaceComplete(previous.generation(), keys(vector.get("incoming")));
            assertThat(next.clinicId()).as(name).isEqualTo(clinic);
        } else {
            assertThatThrownBy(() -> previous.replaceComplete(
                    previous.generation(), keys(vector.get("incoming")))).as(name)
                    .isInstanceOf(RuntimeException.class);
        }
        assertThat(previous.toStoredJson()).as(name + " preserved previous snapshot").isEqualTo(preserved);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bootstrapVectors")
    void shouldValidateSharedBootstrapGraphs_preservingTheAllRevokedProducerDistinction(
            String name, JsonNode vector) {
        String clinic = FIXTURE.get("keys").get("A").get("clinic_id").textValue();
        if ("bootstrap-all-revoked".equals(name)) {
            // Initial linking requires an active pin; a producer must still publish revocation-only history.
            assertThat(vector.get("expect_accept").booleanValue()).isFalse();
            var catalogue = PortalMyCarlosKeyInventory.create(clinic, keys(vector.get("incoming")));
            assertThat(catalogue.publication().keys()).allSatisfy(
                    key -> assertThat(key.status()).isEqualTo("revoked"));
        } else if (vector.get("expect_accept").booleanValue()) {
            assertThat(PortalMyCarlosKeyInventory.create(clinic, keys(vector.get("incoming"))).publication().keys())
                    .as(name).isNotEmpty();
        } else {
            assertThatThrownBy(() -> PortalMyCarlosKeyInventory.create(clinic, keys(vector.get("incoming"))))
                    .as(name).isInstanceOf(RuntimeException.class);
        }
    }

    private static List<PatientPortalClinicKeysPublishRequest.Key> keys(JsonNode entries) {
        if (!entries.isArray()) {
            throw new IllegalArgumentException("fixture inventory is not an array");
        }
        var keys = new ArrayList<PatientPortalClinicKeysPublishRequest.Key>();
        for (JsonNode entry : entries) {
            Set<String> fields = new HashSet<>();
            entry.fieldNames().forEachRemaining(fields::add);
            boolean replacement = entry.has("replaces_kid");
            Set<String> expected = replacement
                    ? Set.of("kid", "spki", "status", "replaces_kid", "replacement_signature")
                    : Set.of("kid", "spki", "status");
            if (!entry.isObject() || !fields.equals(expected)) {
                throw new IllegalArgumentException("fixture key fields are not exact");
            }
            keys.add(new PatientPortalClinicKeysPublishRequest.Key(
                    PortalJson.requiredText(entry, "kid"), PortalJson.requiredText(entry, "spki"),
                    PortalJson.requiredText(entry, "status"), replacement ? PortalJson.nullableText(entry, "replaces_kid") : null,
                    replacement ? PortalJson.nullableText(entry, "replacement_signature") : null));
        }
        return List.copyOf(keys);
    }
}
