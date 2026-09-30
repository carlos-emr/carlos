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

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * Seals outbound email archive artifacts in a versioned AES-256-GCM envelope before they reach the
 * document store, and opens them again on the authorized read path (#3448).
 *
 * <h2>Stored layout, format version 1</h2>
 * <pre>
 * offset  bytes  field
 *      0      8  magic 89 43 45 41 0D 0A 1A 0A  ("\x89CEA\r\n\x1a\n")
 *      8      1  format version, 0x01
 *      9      1  algorithm, 0x01 = AES-256-GCM, 96-bit nonce, 128-bit tag
 *     10      4  key id, big-endian, 1..2147483647
 *     14     12  nonce, fresh from SecureRandom for every artifact
 *     26      n  ciphertext, n = plaintext length
 *   26+n     16  GCM authentication tag
 * </pre>
 *
 * <p>The magic starts with a byte above 0x7F, so it cannot begin an RFC 822 message (header field
 * names are printable ASCII) or a JSON document. That is what lets the read path treat a stored
 * artifact without the magic as a legacy plaintext artifact written before #3448, with no database
 * column recording which is which.</p>
 *
 * <h2>What the tag authenticates</h2>
 * <p>The associated data is the whole 26-byte header followed by the archive context: the
 * {@code EmailLog} id, the demographic number, the plaintext size, the plaintext SHA-256 and the
 * content type, each fixed-width or length-prefixed so no two contexts encode alike. Changing the
 * header (a different key id or version), moving the ciphertext to another archive row, or editing
 * the row's recorded hash, size or content type all fail authentication. The archive row id is not
 * bound: the bytes are written through the eDoc store before that row exists. The plaintext hash
 * stands in for it: two rows whose ciphertexts could be swapped undetected would have to hold the
 * same plaintext.</p>
 *
 * <p>Integrity order is the ticket's: the caller hashes the plaintext, this class stores the
 * ciphertext, and the caller verifies size and SHA-256 against the plaintext this class returns.</p>
 *
 * <p>Nonces are random rather than counters, so no state has to survive restarts or be shared.
 * NIST SP 800-38D bounds random 96-bit nonces at 2<sup>32</sup> encryptions per key, far beyond
 * one archive key's lifetime; rotating the key resets the count.</p>
 *
 * <p>Not {@code EncryptionUtils}: that helper is for short {@code {ENC}}-prefixed secrets under the
 * application key (#3132), and this key domain is deliberately separate from it.</p>
 *
 * @since 2026-09-30
 */
public final class OutboundEmailArchiveEnvelope {

    private static final byte[] MAGIC = {(byte) 0x89, 'C', 'E', 'A', '\r', '\n', 0x1A, '\n'};
    static final byte FORMAT_VERSION_1 = 1;
    static final byte ALGORITHM_AES_256_GCM = 1;
    static final int NONCE_BYTES = 12;
    static final int TAG_BYTES = 16;
    static final int VERSION_OFFSET = MAGIC.length;
    static final int ALGORITHM_OFFSET = VERSION_OFFSET + 1;
    static final int KEY_ID_OFFSET = ALGORITHM_OFFSET + 1;
    static final int NONCE_OFFSET = KEY_ID_OFFSET + Integer.BYTES;

    /** Bytes before the ciphertext. */
    public static final int HEADER_BYTES = NONCE_OFFSET + NONCE_BYTES;

    /** Stored size minus plaintext size: the header plus the GCM tag. */
    public static final int OVERHEAD_BYTES = HEADER_BYTES + TAG_BYTES;

    /** Largest plaintext whose envelope still fits in one Java array. */
    public static final int MAX_PLAINTEXT_BYTES = Integer.MAX_VALUE - OVERHEAD_BYTES - 8;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final SecureRandom RANDOM = new SecureRandom();

    private OutboundEmailArchiveEnvelope() {
    }

    /**
     * The archive row facts an envelope is bound to. All of them are already stored on the
     * {@code outboundEmailArchive} row, so the read path can rebuild this without extra storage.
     *
     * @param emailLogId    the archive's {@code EmailLog} id
     * @param demographicNo the archive's patient
     * @param contentType   the media type stored on the archive row
     * @param sha256Hex     lowercase hex SHA-256 of the plaintext artifact
     * @param byteSize      plaintext artifact size in bytes
     */
    public record ArtifactContext(long emailLogId, int demographicNo, String contentType, String sha256Hex,
                                  long byteSize) {
        public ArtifactContext {
            Objects.requireNonNull(contentType, "contentType");
            Objects.requireNonNull(sha256Hex, "sha256Hex");
            if (byteSize < 0) {
                throw new IllegalArgumentException("Artifact byte size must not be negative");
            }
        }
    }

    /**
     * @param stored stored artifact bytes, possibly a legacy plaintext artifact
     * @return true when the bytes start with the envelope marker. A legacy artifact never does.
     */
    public static boolean hasEnvelopeMagic(byte[] stored) {
        return stored != null && stored.length >= MAGIC.length
                && Arrays.equals(stored, 0, MAGIC.length, MAGIC, 0, MAGIC.length);
    }

    /**
     * Encrypts a plaintext artifact under the keyring's current key.
     *
     * @param keyring   archive keyring; its current key seals the artifact
     * @param context   archive facts bound into the tag; its hash and size must describe {@code plaintext}
     * @param plaintext the exact artifact that was sent
     * @return the envelope bytes to store in place of the plaintext
     * @throws OutboundEmailArchiveEnvelopeException when the plaintext is too large or the JCE fails
     */
    public static byte[] seal(OutboundEmailArchiveKeyring keyring, ArtifactContext context, byte[] plaintext)
            throws OutboundEmailArchiveEnvelopeException {
        Objects.requireNonNull(keyring, "keyring");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(plaintext, "plaintext");
        if (plaintext.length > MAX_PLAINTEXT_BYTES || plaintext.length != context.byteSize()) {
            throw new OutboundEmailArchiveEnvelopeException(OutboundEmailArchiveEnvelopeException.Reason.MALFORMED,
                    "Archive artifact size does not match its context or is too large to encrypt");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        byte[] header = header(keyring.currentKeyId(), nonce);
        byte[] stored = new byte[OVERHEAD_BYTES + plaintext.length];
        System.arraycopy(header, 0, stored, 0, HEADER_BYTES);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keyring.currentKey(), new GCMParameterSpec(TAG_BYTES * 8, nonce));
            cipher.updateAAD(associatedData(header, context));
            // Sized exactly: a provider that needed more room would throw ShortBufferException.
            cipher.doFinal(plaintext, 0, plaintext.length, stored, HEADER_BYTES);
        } catch (GeneralSecurityException e) {
            throw new OutboundEmailArchiveEnvelopeException(
                    OutboundEmailArchiveEnvelopeException.Reason.ENCRYPTION_FAILED,
                    "Archive artifact encryption failed", e);
        }
        return stored;
    }

    /**
     * Authenticates and decrypts an envelope. Nothing is returned unless the tag verifies, so a
     * caller never sees partially decrypted or unauthenticated bytes.
     *
     * @param keyring archive keyring; the key named in the header opens the artifact
     * @param context the archive row's facts; must equal those the artifact was sealed with
     * @param stored  envelope bytes, starting with the marker
     * @return the plaintext artifact. The caller still verifies its size and SHA-256.
     * @throws OutboundEmailArchiveEnvelopeException when the envelope is malformed or of an unknown
     *         version or algorithm, its key is not in the keyring, or authentication fails
     */
    public static byte[] open(OutboundEmailArchiveKeyring keyring, ArtifactContext context, byte[] stored)
            throws OutboundEmailArchiveEnvelopeException {
        Objects.requireNonNull(keyring, "keyring");
        Objects.requireNonNull(context, "context");
        int keyId = readKeyId(stored);
        if (stored.length < OVERHEAD_BYTES) {
            throw new OutboundEmailArchiveEnvelopeException(OutboundEmailArchiveEnvelopeException.Reason.MALFORMED,
                    "Archive artifact envelope is truncated");
        }
        SecretKey key = keyring.key(keyId).orElseThrow(() -> new OutboundEmailArchiveEnvelopeException(
                OutboundEmailArchiveEnvelopeException.Reason.KEY_UNAVAILABLE,
                "Archive artifact key " + keyId + " is not in the archive keyring"));
        byte[] header = Arrays.copyOfRange(stored, 0, HEADER_BYTES);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BYTES * 8, stored, NONCE_OFFSET, NONCE_BYTES));
            cipher.updateAAD(associatedData(header, context));
            return cipher.doFinal(stored, HEADER_BYTES, stored.length - HEADER_BYTES);
        } catch (AEADBadTagException e) {
            // No cause attached: the provider's text adds nothing an operator can act on.
            throw new OutboundEmailArchiveEnvelopeException(
                    OutboundEmailArchiveEnvelopeException.Reason.AUTHENTICATION_FAILED,
                    "Archive artifact failed authentication");
        } catch (GeneralSecurityException e) {
            throw new OutboundEmailArchiveEnvelopeException(
                    OutboundEmailArchiveEnvelopeException.Reason.AUTHENTICATION_FAILED,
                    "Archive artifact could not be decrypted", e);
        }
    }

    /**
     * Validates the header and returns the key id it names. Length-bounded and fail-closed: an
     * unknown version or algorithm is refused rather than interpreted. Reads only the first
     * {@link #HEADER_BYTES}, so it also works on a file prefix.
     *
     * @param stored envelope bytes, or at least their first {@link #HEADER_BYTES}
     * @return the key id that sealed the artifact
     * @throws OutboundEmailArchiveEnvelopeException when the header is malformed or unsupported
     */
    public static int readKeyId(byte[] stored) throws OutboundEmailArchiveEnvelopeException {
        if (stored == null || stored.length < HEADER_BYTES || !hasEnvelopeMagic(stored)) {
            throw new OutboundEmailArchiveEnvelopeException(OutboundEmailArchiveEnvelopeException.Reason.MALFORMED,
                    "Archive artifact envelope is malformed");
        }
        if (stored[VERSION_OFFSET] != FORMAT_VERSION_1) {
            throw new OutboundEmailArchiveEnvelopeException(OutboundEmailArchiveEnvelopeException.Reason.UNSUPPORTED,
                    "Archive artifact envelope version is not supported");
        }
        if (stored[ALGORITHM_OFFSET] != ALGORITHM_AES_256_GCM) {
            throw new OutboundEmailArchiveEnvelopeException(OutboundEmailArchiveEnvelopeException.Reason.UNSUPPORTED,
                    "Archive artifact envelope algorithm is not supported");
        }
        int keyId = ByteBuffer.wrap(stored, KEY_ID_OFFSET, Integer.BYTES).getInt();
        if (keyId <= 0) {
            throw new OutboundEmailArchiveEnvelopeException(OutboundEmailArchiveEnvelopeException.Reason.MALFORMED,
                    "Archive artifact envelope key id is invalid");
        }
        return keyId;
    }

    private static byte[] header(int keyId, byte[] nonce) {
        return ByteBuffer.allocate(HEADER_BYTES)
                .put(MAGIC)
                .put(FORMAT_VERSION_1)
                .put(ALGORITHM_AES_256_GCM)
                .putInt(keyId)
                .put(nonce)
                .array();
    }

    /** Header, then the context: fixed-width numbers and length-prefixed strings, never ambiguous. */
    private static byte[] associatedData(byte[] header, ArtifactContext context) {
        byte[] sha256 = context.sha256Hex().getBytes(StandardCharsets.US_ASCII);
        byte[] contentType = context.contentType().getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(header.length + Long.BYTES + Integer.BYTES + Long.BYTES
                        + Integer.BYTES + sha256.length + Integer.BYTES + contentType.length)
                .put(header)
                .putLong(context.emailLogId())
                .putInt(context.demographicNo())
                .putLong(context.byteSize())
                .putInt(sha256.length).put(sha256)
                .putInt(contentType.length).put(contentType)
                .array();
    }
}
