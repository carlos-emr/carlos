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

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosKeyTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Generated ephemeral branches supplement, and do not replace, the one shared cross-language fixture. */
@Tag("unit")
@Tag("patient-portal")
class PortalMyCarlosKeyReplacementProofsUnitTest {
    @Test
    void shouldBindProofToClinicAndNewKey_withoutDowngradingInvalidProofsToUnsigned() {
        var proof = PortalMyCarlosKeyReplacementProofs.mintOnce(
                CLINIC, FIRST, SECOND, FIRST_PAIR.getPrivate());
        var valid = new PatientPortalClinicKeysPublishRequest(List.of(FIRST, proof));
        PortalMyCarlosKeyReplacementProofs.validateAll(CLINIC, valid);
        assertThat(Base64.getUrlDecoder().decode(proof.replacementSignature())).hasSize(64);
        assertThatThrownBy(() -> PortalMyCarlosKeyReplacementProofs.validateAll("other-clinic", valid))
                .isInstanceOf(IllegalArgumentException.class);
        var third = key(pair());
        var substituted = new PatientPortalClinicKeysPublishRequest.Key(
                third.kid(), third.spki(), "active", FIRST.kid(), proof.replacementSignature());
        assertThatThrownBy(() -> PortalMyCarlosKeyReplacementProofs.validateAll(CLINIC,
                new PatientPortalClinicKeysPublishRequest(List.of(FIRST, substituted))))
                .isInstanceOf(IllegalArgumentException.class);
        var zero = new PatientPortalClinicKeysPublishRequest.Key(SECOND.kid(), SECOND.spki(), "active",
                FIRST.kid(), Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[64]));
        assertThatThrownBy(() -> PortalMyCarlosKeyInventory.create(CLINIC, List.of(FIRST, zero)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectCyclesAndForks_evenWhenEveryIndividualSignatureIsValid() {
        var firstReplacedBySecond = PortalMyCarlosKeyReplacementProofs.mintOnce(
                CLINIC, FIRST, SECOND, FIRST_PAIR.getPrivate());
        var secondReplacedByFirst = PortalMyCarlosKeyReplacementProofs.mintOnce(
                CLINIC, SECOND, FIRST, SECOND_PAIR.getPrivate());
        assertThatThrownBy(() -> PortalMyCarlosKeyInventory.create(
                CLINIC, List.of(firstReplacedBySecond, secondReplacedByFirst)))
                .isInstanceOf(IllegalArgumentException.class);
        var third = key(pair());
        var competingSuccessor = PortalMyCarlosKeyReplacementProofs.mintOnce(
                CLINIC, FIRST, third, FIRST_PAIR.getPrivate());
        assertThatThrownBy(() -> PortalMyCarlosKeyInventory.create(
                CLINIC, List.of(FIRST, firstReplacedBySecond, competingSuccessor)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldImportCompleteHistoricalChains_withoutAnyPreviousActiveAnchor() {
        var thirdPair = pair();
        var third = key(thirdPair);
        var second = PortalMyCarlosKeyReplacementProofs.mintOnce(
                CLINIC, FIRST, SECOND, FIRST_PAIR.getPrivate());
        var latest = PortalMyCarlosKeyReplacementProofs.mintOnce(
                CLINIC, SECOND, third, SECOND_PAIR.getPrivate());
        var imported = PortalMyCarlosKeyInventory.create(
                CLINIC, List.of(revoked(FIRST), revoked(second), latest));
        assertThat(imported.publication().keys()).hasSize(3);
        assertThat(PortalMyCarlosKeyInventory.fromStoredJson(imported.toStoredJson()).toStoredJson())
                .isEqualTo(imported.toStoredJson());
    }

    @Test
    void shouldPreserveOldEdgesAfterRevocation_butRefuseNewEdgesFromPreviouslyRevokedKeys() {
        var signedSecond = PortalMyCarlosKeyReplacementProofs.mintOnce(
                CLINIC, FIRST, SECOND, FIRST_PAIR.getPrivate());
        var original = PortalMyCarlosKeyInventory.create(CLINIC, List.of(FIRST, signedSecond));
        var revokedFirst = original.replaceComplete(1, List.of(revoked(FIRST), signedSecond));
        var third = key(pair());
        var signedThird = PortalMyCarlosKeyReplacementProofs.mintOnce(
                CLINIC, SECOND, third, SECOND_PAIR.getPrivate());
        assertThat(revokedFirst.replaceComplete(
                2, List.of(revoked(FIRST), revoked(signedSecond), signedThird)).generation()).isEqualTo(3);
        var priorRevokedSecond = original.replaceComplete(1, List.of(FIRST, revoked(signedSecond)));
        assertThatThrownBy(() -> priorRevokedSecond.replaceComplete(
                2, List.of(FIRST, revoked(signedSecond), signedThird)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptOfflineUnsignedRootsAndSignedDescendants_evenWhenTheNewRootIsAlreadyRevoked() {
        var previous = catalogue(); // Device/producer previously knew only A.
        var third = key(pair());
        var signedThird = PortalMyCarlosKeyReplacementProofs.mintOnce(
                CLINIC, SECOND, third, SECOND_PAIR.getPrivate());
        // B is a new unsigned root. The client must persist its plain notice/history atomically.
        var next = previous.replaceComplete(1, List.of(FIRST, revoked(SECOND), signedThird));
        assertThat(next.publication().keys()).containsExactlyInAnyOrder(FIRST, revoked(SECOND), signedThird);
    }

    @Test
    void shouldAcceptTheSixteenKeyChain_andRefuseAnyAdditionalHistoricalKey() {
        var pairs = new ArrayList<java.security.KeyPair>();
        for (int i = 0; i < 16; i++) {
            pairs.add(pair());
        }
        var keys = new ArrayList<PatientPortalClinicKeysPublishRequest.Key>();
        keys.add(key(pairs.getFirst()));
        for (int i = 1; i < pairs.size(); i++) {
            keys.add(PortalMyCarlosKeyReplacementProofs.mintOnce(
                    CLINIC, key(pairs.get(i - 1)), key(pairs.get(i)), pairs.get(i - 1).getPrivate()));
        }
        var full = PortalMyCarlosKeyInventory.create(CLINIC, keys);
        assertThat(full.publication().keys()).hasSize(16);
        keys.add(key(pair()));
        assertThatThrownBy(() -> full.replaceComplete(1, keys)).isInstanceOf(IllegalArgumentException.class);
        assertThat(full.publication().keys()).hasSize(16);
    }

    @Test
    void shouldRefuseWrongOrRevokedPrivatePredecessors_withoutProducingAUsableProof() {
        assertThatThrownBy(() -> PortalMyCarlosKeyReplacementProofs.mintOnce(
                CLINIC, FIRST, SECOND, SECOND_PAIR.getPrivate()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortalMyCarlosKeyReplacementProofs.mintOnce(
                CLINIC, revoked(FIRST), SECOND, FIRST_PAIR.getPrivate()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
