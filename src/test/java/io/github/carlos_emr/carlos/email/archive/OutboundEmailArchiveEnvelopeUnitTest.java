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

package io.github.carlos_emr.carlos.email.archive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveTestKeyrings.keyring;
import static io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveTestKeyrings.syntheticKey;
import static io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveTestKeyrings.syntheticKeyBase64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("OutboundEmailArchiveEnvelope (#3448)")
class OutboundEmailArchiveEnvelopeUnitTest {

    private static final byte[] PLAINTEXT = "Subject: synthetic archive test\r\n\r\nNot a real patient."
            .getBytes(StandardCharsets.US_ASCII);
    private static final OutboundEmailArchiveEnvelope.ArtifactContext CONTEXT = context(PLAINTEXT);

    @Test
    void shouldRoundTrip_withTheCurrentKey() throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);

        assertThat(OutboundEmailArchiveEnvelope.open(keyring(1, 1), CONTEXT, stored)).isEqualTo(PLAINTEXT);
    }

    @Test
    void shouldWriteTheDocumentedHeader_forFormatVersionOne() throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(7, 7), CONTEXT, PLAINTEXT);

        assertThat(stored).hasSize(PLAINTEXT.length + OutboundEmailArchiveEnvelope.OVERHEAD_BYTES);
        assertThat(OutboundEmailArchiveEnvelope.OVERHEAD_BYTES).isEqualTo(42);
        assertThat(java.util.Arrays.copyOfRange(stored, 0, 8))
                .isEqualTo(new byte[] {(byte) 0x89, 'C', 'E', 'A', '\r', '\n', 0x1A, '\n'});
        assertThat(stored[8]).isEqualTo((byte) 1);
        assertThat(stored[9]).isEqualTo((byte) 1);
        assertThat(ByteBuffer.wrap(stored, 10, 4).getInt()).isEqualTo(7);
        assertThat(OutboundEmailArchiveEnvelope.readKeyId(stored)).isEqualTo(7);
    }

    @Test
    void shouldNotStorePlaintext_inTheEnvelope() throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);

        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("synthetic archive test");
    }

    @Test
    void shouldUseAFreshNonce_forEveryEncryption() throws Exception {
        byte[] first = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);
        byte[] second = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);

        assertThat(java.util.Arrays.copyOfRange(first, 14, 26)).isNotEqualTo(java.util.Arrays.copyOfRange(second, 14, 26));
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldOpenOldArtifacts_afterRotationWhileSealingNewOnesWithTheNewKey() throws Exception {
        byte[] writtenUnderKeyOne = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);
        OutboundEmailArchiveKeyring rotated = keyring(2, 1, 2);

        byte[] writtenUnderKeyTwo = OutboundEmailArchiveEnvelope.seal(rotated, CONTEXT, PLAINTEXT);

        assertThat(OutboundEmailArchiveEnvelope.open(rotated, CONTEXT, writtenUnderKeyOne)).isEqualTo(PLAINTEXT);
        assertThat(OutboundEmailArchiveEnvelope.readKeyId(writtenUnderKeyTwo)).isEqualTo(2);
        assertThat(OutboundEmailArchiveEnvelope.open(rotated, CONTEXT, writtenUnderKeyTwo)).isEqualTo(PLAINTEXT);
    }

    @Test
    void shouldFailCleanly_whenTheKeyIdIsNotInTheKeyring() throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(2, 1, 2), CONTEXT, PLAINTEXT);
        OutboundEmailArchiveKeyring withoutKeyTwo = keyring(1, 1);

        assertThatThrownBy(() -> OutboundEmailArchiveEnvelope.open(withoutKeyTwo, CONTEXT, stored))
                .isInstanceOfSatisfying(OutboundEmailArchiveEnvelopeException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(OutboundEmailArchiveEnvelopeException.Reason.KEY_UNAVAILABLE);
                    assertThat(e.getMessage()).isEqualTo("Archive artifact key 2 is not in the archive keyring");
                });
    }

    /** Offsets: 14 = nonce, 26 = first ciphertext byte, -1 = last tag byte. */
    @ParameterizedTest
    @ValueSource(ints = {14, 25, 26, 30, -1})
    void shouldFailAuthentication_whenANonceCiphertextOrTagByteIsChanged(int offset) throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);
        int index = offset >= 0 ? offset : stored.length + offset;
        stored[index] ^= 0x01;

        assertAuthenticationFails(keyring(1, 1), CONTEXT, stored);
    }

    @Test
    void shouldFailAuthentication_whenTheHeaderIsMovedToAnotherKey() throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(1, 1, 2), CONTEXT, PLAINTEXT);
        ByteBuffer.wrap(stored).putInt(10, 2);

        assertAuthenticationFails(keyring(1, 1, 2), CONTEXT, stored);
    }

    @Test
    void shouldRefuseUnknownVersions_insteadOfGuessing() throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);
        stored[8] = 2;

        assertReason(keyring(1, 1), stored, OutboundEmailArchiveEnvelopeException.Reason.UNSUPPORTED);
    }

    @Test
    void shouldRefuseUnknownAlgorithms_insteadOfGuessing() throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);
        stored[9] = 9;

        assertReason(keyring(1, 1), stored, OutboundEmailArchiveEnvelopeException.Reason.UNSUPPORTED);
    }

    @Test
    void shouldRefuseTruncatedEnvelopes_asMalformed() throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);
        byte[] truncated = java.util.Arrays.copyOf(stored, OutboundEmailArchiveEnvelope.OVERHEAD_BYTES - 1);

        assertReason(keyring(1, 1), truncated, OutboundEmailArchiveEnvelopeException.Reason.MALFORMED);
    }

    @Test
    void shouldRefuseANonPositiveKeyId_asMalformed() throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);
        ByteBuffer.wrap(stored).putInt(10, 0);

        assertReason(keyring(1, 1), stored, OutboundEmailArchiveEnvelopeException.Reason.MALFORMED);
    }

    /** Swapping ciphertext between archive rows, or editing a row's recorded facts, must not decrypt. */
    @Test
    void shouldFailAuthentication_whenAnyBoundArchiveFactDiffers() throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);
        String otherHash = sha256Hex("different".getBytes(StandardCharsets.US_ASCII));

        assertAuthenticationFails(keyring(1, 1), new OutboundEmailArchiveEnvelope.ArtifactContext(
                45, CONTEXT.demographicNo(), CONTEXT.contentType(), CONTEXT.sha256Hex(), CONTEXT.byteSize()), stored);
        assertAuthenticationFails(keyring(1, 1), new OutboundEmailArchiveEnvelope.ArtifactContext(
                CONTEXT.emailLogId(), 124, CONTEXT.contentType(), CONTEXT.sha256Hex(), CONTEXT.byteSize()), stored);
        assertAuthenticationFails(keyring(1, 1), new OutboundEmailArchiveEnvelope.ArtifactContext(
                CONTEXT.emailLogId(), CONTEXT.demographicNo(), "application/json", CONTEXT.sha256Hex(),
                CONTEXT.byteSize()), stored);
        assertAuthenticationFails(keyring(1, 1), new OutboundEmailArchiveEnvelope.ArtifactContext(
                CONTEXT.emailLogId(), CONTEXT.demographicNo(), CONTEXT.contentType(), otherHash,
                CONTEXT.byteSize()), stored);
        assertAuthenticationFails(keyring(1, 1), new OutboundEmailArchiveEnvelope.ArtifactContext(
                CONTEXT.emailLogId(), CONTEXT.demographicNo(), CONTEXT.contentType(), CONTEXT.sha256Hex(),
                CONTEXT.byteSize() + 1), stored);
    }

    @Test
    void shouldFailAuthentication_whenTheKeyUnderTheSameIdIsDifferent() throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);
        // Key 1 of a different keyring: what a wrong keyring restored from backup looks like.
        OutboundEmailArchiveKeyring impostor = new OutboundEmailArchiveKeyring(1,
                java.util.Map.of(1, syntheticKey(99)));

        assertAuthenticationFails(impostor, CONTEXT, stored);
    }

    @Test
    void shouldRecogniseOnlyEnvelopes_byTheMarker() throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);

        assertThat(OutboundEmailArchiveEnvelope.hasEnvelopeMagic(stored)).isTrue();
        assertThat(OutboundEmailArchiveEnvelope.hasEnvelopeMagic(PLAINTEXT)).isFalse();
        assertThat(OutboundEmailArchiveEnvelope.hasEnvelopeMagic("{\"personalizations\":[]}"
                .getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(OutboundEmailArchiveEnvelope.hasEnvelopeMagic(new byte[0])).isFalse();
        assertThat(OutboundEmailArchiveEnvelope.hasEnvelopeMagic(null)).isFalse();
    }

    @Test
    void shouldRefuseToSeal_whenTheContextDoesNotDescribeThePlaintext() {
        OutboundEmailArchiveEnvelope.ArtifactContext wrongSize = new OutboundEmailArchiveEnvelope.ArtifactContext(
                44, 123, "message/rfc822", CONTEXT.sha256Hex(), PLAINTEXT.length + 1L);
        OutboundEmailArchiveKeyring keyring = keyring(1, 1);

        assertThatThrownBy(() -> OutboundEmailArchiveEnvelope.seal(keyring, wrongSize, PLAINTEXT))
                .isInstanceOf(OutboundEmailArchiveEnvelopeException.class);
    }

    @Test
    void shouldKeepKeyMaterialOut_ofKeyringTextAndErrors() throws Exception {
        byte[] stored = OutboundEmailArchiveEnvelope.seal(keyring(1, 1), CONTEXT, PLAINTEXT);
        stored[30] ^= 0x01;
        OutboundEmailArchiveKeyring keyring = keyring(1, 1);

        assertThat(keyring.toString()).isEqualTo("OutboundEmailArchiveKeyring[currentKeyId=1, keyIds=[1]]");
        assertThatThrownBy(() -> OutboundEmailArchiveEnvelope.open(keyring, CONTEXT, stored))
                .satisfies(e -> assertThat(e.toString())
                        .doesNotContain(syntheticKeyBase64(1))
                        .doesNotContain(new String(syntheticKey(1), StandardCharsets.US_ASCII))
                        .doesNotContain("synthetic archive test"));
    }

    private static void assertAuthenticationFails(OutboundEmailArchiveKeyring keyring,
                                                  OutboundEmailArchiveEnvelope.ArtifactContext context, byte[] stored) {
        assertThatThrownBy(() -> OutboundEmailArchiveEnvelope.open(keyring, context, stored))
                .isInstanceOfSatisfying(OutboundEmailArchiveEnvelopeException.class, e -> assertThat(e.getReason())
                        .isEqualTo(OutboundEmailArchiveEnvelopeException.Reason.AUTHENTICATION_FAILED));
    }

    private static void assertReason(OutboundEmailArchiveKeyring keyring, byte[] stored,
                                     OutboundEmailArchiveEnvelopeException.Reason reason) {
        assertThatThrownBy(() -> OutboundEmailArchiveEnvelope.open(keyring, CONTEXT, stored))
                .isInstanceOfSatisfying(OutboundEmailArchiveEnvelopeException.class,
                        e -> assertThat(e.getReason()).isEqualTo(reason));
    }

    private static OutboundEmailArchiveEnvelope.ArtifactContext context(byte[] plaintext) {
        return new OutboundEmailArchiveEnvelope.ArtifactContext(44, 123, "message/rfc822", sha256Hex(plaintext),
                plaintext.length);
    }

    private static String sha256Hex(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
