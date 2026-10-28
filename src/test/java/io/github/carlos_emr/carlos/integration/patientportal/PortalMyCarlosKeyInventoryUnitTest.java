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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FAKE in-memory public keys only; no database, provision, private-key persistence or network. */
@Tag("unit")
@Tag("patient-portal")
class PortalMyCarlosKeyInventoryUnitTest {
    private static final String CLINIC = "fake-clinic";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<KeyPair> PAIRS =
            IntStream.range(0, 17).mapToObj(ignored -> generatePair()).toList();
    private static final List<PatientPortalClinicKeysPublishRequest.Key> KEYS =
            PAIRS.stream().map(PortalMyCarlosKeyInventoryUnitTest::key).toList();

    private static KeyPair generatePair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new AssertionError("FAKE key fixture generation failed", exception);
        }
    }

    private static PatientPortalClinicKeysPublishRequest.Key key(KeyPair pair) {
        try {
            byte[] spki = pair.getPublic().getEncoded();
            var encoder = Base64.getUrlEncoder().withoutPadding();
            return new PatientPortalClinicKeysPublishRequest.Key(
                    encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(spki)),
                    encoder.encodeToString(spki), "active", null, null);
        } catch (Exception exception) {
            throw new AssertionError("FAKE public-key fixture generation failed", exception);
        }
    }

    private static PatientPortalClinicKeysPublishRequest.Key revoked(
            PatientPortalClinicKeysPublishRequest.Key key) {
        return new PatientPortalClinicKeysPublishRequest.Key(
                key.kid(), key.spki(), "revoked", key.replacesKid(), key.replacementSignature());
    }

    private static ObjectNode storedNode(PortalMyCarlosKeyInventory inventory) throws Exception {
        return (ObjectNode) JSON.readTree(inventory.toStoredJson());
    }

    @Test
    void shouldRoundTripAndSort_withoutMutatingCallerInput() {
        var supplied = new ArrayList<>(List.of(KEYS.get(1), KEYS.get(0)));
        var catalogue = PortalMyCarlosKeyInventory.create(CLINIC, supplied);
        supplied.clear();
        var restored = PortalMyCarlosKeyInventory.fromStoredJson(catalogue.toStoredJson());

        assertThat(catalogue.publication().keys()).hasSize(2)
                .isSortedAccordingTo(java.util.Comparator.comparing(
                        PatientPortalClinicKeysPublishRequest.Key::kid));
        assertThat(restored.toStoredJson()).isEqualTo(catalogue.toStoredJson());
        assertThat(restored.contentDigest()).isEqualTo(catalogue.contentDigest());
        assertThat(restored.generation()).isEqualTo(1);
        assertThatThrownBy(() -> restored.publication().keys().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldAllowUnsignedNewKeys_withExplicitCompleteHistory() {
        var original = PortalMyCarlosKeyInventory.create(CLINIC, List.of(KEYS.get(0)));
        var next = original.replaceComplete(1, List.of(KEYS.get(0), KEYS.get(1)));

        assertThat(next.generation()).isEqualTo(2);
        assertThat(next.publication().keys()).containsExactlyInAnyOrder(KEYS.get(0), KEYS.get(1));
        assertThat(next.publication().keys()).allSatisfy(
                key -> assertThat(key.replacementSignature()).isNull());
        assertThat(original.generation()).isEqualTo(1);
        assertThat(original.publication().keys()).containsExactly(KEYS.get(0));
    }

    @Test
    void shouldKeepRevocationMonotonic_andPreserveHistoricalEntries() {
        var original = PortalMyCarlosKeyInventory.create(CLINIC, List.of(KEYS.get(0)));
        var retired = original.replaceComplete(1, List.of(revoked(KEYS.get(0)), KEYS.get(1)));
        String preserved = retired.toStoredJson();

        assertThatThrownBy(() -> retired.replaceComplete(2, List.of(KEYS.get(0), KEYS.get(1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retired.replaceComplete(2, List.of(KEYS.get(1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(retired.toStoredJson()).isEqualTo(preserved);
        // A wholly revoked catalogue is still publishable through the independent job key.
        var allRevoked = retired.replaceComplete(2, List.of(revoked(KEYS.get(0)), revoked(KEYS.get(1))));
        assertThat(allRevoked.publication().keys()).allSatisfy(
                key -> assertThat(key.status()).isEqualTo("revoked"));
    }

    @Test
    void shouldPreserveValidatedReplacementMetadata_withoutMintingNewProofOnRetry() {
        var replacement = PortalMyCarlosKeyReplacementProofs.mintOnce(
                CLINIC, KEYS.get(0), KEYS.get(1), PAIRS.get(0).getPrivate());
        var original = PortalMyCarlosKeyInventory.create(CLINIC, List.of(KEYS.get(0), replacement));
        var restored = PortalMyCarlosKeyInventory.fromStoredJson(original.toStoredJson());

        assertThat(restored.publication().keys()).contains(replacement);
        assertThatThrownBy(() -> PortalMyCarlosKeyInventory.create(CLINIC, List.of(replacement)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restored.replaceComplete(1, List.of(KEYS.get(0), KEYS.get(1))))
                .isInstanceOf(IllegalArgumentException.class);
        var differentProof = PortalMyCarlosKeyReplacementProofs.mintOnce(
                CLINIC, KEYS.get(2), KEYS.get(1), PAIRS.get(2).getPrivate());
        assertThatThrownBy(() -> restored.replaceComplete(
                1, List.of(KEYS.get(0), KEYS.get(2), differentProof)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(restored.toStoredJson()).isEqualTo(original.toStoredJson());
    }

    @Test
    void shouldRejectSeventeenthKey_withoutEvictingHistory() {
        var full = PortalMyCarlosKeyInventory.create(CLINIC, KEYS.subList(0, 16));
        String preserved = full.toStoredJson();

        assertThatThrownBy(() -> full.replaceComplete(1, KEYS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(full.publication().keys()).hasSize(16);
        assertThat(full.toStoredJson()).isEqualTo(preserved);
        assertThatThrownBy(() -> PortalMyCarlosKeyInventory.create(CLINIC, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectStaleOrExhaustedGenerations_withoutRewritingTheCatalogue() throws Exception {
        var original = PortalMyCarlosKeyInventory.create(CLINIC, List.of(KEYS.get(0)));
        assertThatThrownBy(() -> original.replaceComplete(0, List.of(KEYS.get(0), KEYS.get(1))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(original.replaceComplete(1, List.of(KEYS.get(0)))).isSameAs(original);

        ObjectNode exhaustedRecord = storedNode(original).put("generation", Long.MAX_VALUE);
        var exhausted = PortalMyCarlosKeyInventory.fromStoredJson(exhaustedRecord.toString());
        assertThat(exhausted.replaceComplete(Long.MAX_VALUE, List.of(KEYS.get(0)))).isSameAs(exhausted);
        assertThatThrownBy(() ->
                exhausted.replaceComplete(Long.MAX_VALUE, List.of(KEYS.get(0), KEYS.get(1))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(exhausted.toStoredJson()).isEqualTo(exhaustedRecord.toString());
    }

    @Test
    void shouldBindDigestToContent_andKeepItIndependentOfOrderingAndGeneration() throws Exception {
        var forward = PortalMyCarlosKeyInventory.create(CLINIC, List.of(KEYS.get(0), KEYS.get(1)));
        var reversedInput = new ArrayList<>(forward.publication().keys());
        Collections.reverse(reversedInput);
        var reversed = PortalMyCarlosKeyInventory.create(CLINIC, reversedInput);
        var laterGeneration = PortalMyCarlosKeyInventory.fromStoredJson(
                storedNode(forward).put("generation", 42).toString());
        var anotherClinic = PortalMyCarlosKeyInventory.create("another-fake-clinic", reversedInput);

        assertThat(forward.contentDigest()).isEqualTo(reversed.contentDigest())
                .isEqualTo(laterGeneration.contentDigest());
        assertThat(forward.contentDigest()).isNotEqualTo(anotherClinic.contentDigest());
        assertThat(forward.replaceComplete(1, List.of(revoked(KEYS.get(0)), KEYS.get(1))).contentDigest())
                .isNotEqualTo(forward.contentDigest());
    }

    @Test
    void shouldRejectMalformedPersistedRecords_insteadOfTreatingThemAsEmpty() throws Exception {
        var original = PortalMyCarlosKeyInventory.create(CLINIC, List.of(KEYS.get(0)));
        String stored = original.toStoredJson();
        List<String> malformed = new ArrayList<>(List.of(
                stored.replace("\"generation\":1", "\"generation\":1,\"generation\":1"),
                stored + "{}",
                storedNode(original).put("schema_version", 2).toString(),
                storedNode(original).put("generation", "1").toString(),
                storedNode(original).put("generation", 0).toString(),
                storedNode(original).put("unexpected", true).toString(),
                " ".repeat(PortalMyCarlosKeyInventory.MAX_STORED_BYTES + 1)));
        ObjectNode missingNullable = storedNode(original);
        ((ObjectNode) missingNullable.withArray("keys").get(0)).remove("replacement_signature");
        malformed.add(missingNullable.toString());
        ObjectNode tamperedSpki = storedNode(original);
        ((ObjectNode) tamperedSpki.withArray("keys").get(0)).put("spki", KEYS.get(1).spki());
        malformed.add(tamperedSpki.toString());

        for (String input : malformed) {
            assertThatThrownBy(() -> PortalMyCarlosKeyInventory.fromStoredJson(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("clinic public key catalogue is invalid");
        }
        assertThat(original.toStoredJson()).isEqualTo(stored);
    }
}
