/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link EncryptionUtils} cryptographic operations.
 *
 * <p>Tests password hashing/verification, AES-GCM string encryption/decryption,
 * key generation, and encrypted string detection.</p>
 *
 * @since 2026-03-31
 */
@DisplayName("EncryptionUtils Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
@Tag("security")
class EncryptionUtilsUnitTest {

    // -----------------------------------------------------------------------
    // Password hashing (delegates to PasswordHashHelper)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("password hashing")
    class PasswordHashing {

        @Test
        @DisplayName("should hash password to non-null result")
        void shouldHashPassword_toNonNullResult() {
            String hash = EncryptionUtils.hash("testPassword123");
            assertThat(hash).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should produce different hashes for same password (salted)")
        void shouldProduceDifferentHashes_forSamePassword() {
            String hash1 = EncryptionUtils.hash("samePassword");
            String hash2 = EncryptionUtils.hash("samePassword");
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("should verify correct password against hash")
        void shouldVerifyCorrectPassword_againstHash() {
            String hash = EncryptionUtils.hash("mySecurePass");
            assertThat(EncryptionUtils.verify("mySecurePass", hash)).isTrue();
        }

        @Test
        @DisplayName("should reject incorrect password against hash")
        void shouldRejectIncorrectPassword_againstHash() {
            String hash = EncryptionUtils.hash("mySecurePass");
            assertThat(EncryptionUtils.verify("wrongPassword", hash)).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // isEncrypted
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("isEncrypted")
    class IsEncrypted {

        @Test
        @DisplayName("should return true for encrypted string with prefix")
        void shouldReturnTrue_forEncryptedString() {
            assertThat(EncryptionUtils.isEncrypted("{ENC}someBase64data")).isTrue();
        }

        @Test
        @DisplayName("should return false for plain text string")
        void shouldReturnFalse_forPlainText() {
            assertThat(EncryptionUtils.isEncrypted("plaintext")).isFalse();
        }

        @Test
        @DisplayName("should return true for null input")
        void shouldReturnTrue_forNull() {
            assertThat(EncryptionUtils.isEncrypted(null)).isTrue();
        }

        @Test
        @DisplayName("should return true for empty string")
        void shouldReturnTrue_forEmptyString() {
            assertThat(EncryptionUtils.isEncrypted("")).isTrue();
        }

        @Test
        @DisplayName("should return false for string starting with partial prefix")
        void shouldReturnFalse_forPartialPrefix() {
            assertThat(EncryptionUtils.isEncrypted("{EN}data")).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Key generation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("key generation")
    class KeyGeneration {

        @Test
        @DisplayName("should generate non-null AES secret key")
        void shouldGenerateNonNullSecretKey() throws NoSuchAlgorithmException {
            SecretKey key = EncryptionUtils.generateEncryptionKey();
            assertThat(key).isNotNull();
            assertThat(key.getAlgorithm()).isEqualTo("AES");
        }

        @Test
        @DisplayName("should generate SecretKeySpec from seed string")
        void shouldGenerateSecretKeySpec_fromSeed() {
            SecretKeySpec keySpec = EncryptionUtils.generateEncryptionKey("mySeedPhrase");
            assertThat(keySpec).isNotNull();
            assertThat(keySpec.getAlgorithm()).isEqualTo("AES");
        }

        @Test
        @DisplayName("should generate same key from same seed")
        void shouldGenerateSameKey_fromSameSeed() {
            SecretKeySpec key1 = EncryptionUtils.generateEncryptionKey("sameSeed");
            SecretKeySpec key2 = EncryptionUtils.generateEncryptionKey("sameSeed");
            assertThat(key1.getEncoded()).isEqualTo(key2.getEncoded());
        }

        @Test
        @DisplayName("should generate Base64 encoded 256-bit key string")
        void shouldGenerateBase64Key() throws NoSuchAlgorithmException {
            String keyString = EncryptionUtils.generateSecretKey();
            assertThat(keyString).isNotNull().isNotEmpty();
            byte[] decoded = Base64.getDecoder().decode(keyString);
            assertThat(decoded).hasSize(32); // 256 bits = 32 bytes
        }
    }

    // -----------------------------------------------------------------------
    // AES-GCM string encrypt/decrypt (requires SECRET_KEY_SPEC)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AES-GCM string encryption")
    class AesGcmStringEncryption {

        @BeforeEach
        void setUpSecretKey() throws Exception {
            // Generate and set SECRET_KEY_SPEC via reflection
            String base64Key = EncryptionUtils.generateSecretKey();
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            Field field = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
            field.setAccessible(true);
            field.set(null, keySpec);
        }

        @AfterEach
        void clearSecretKey() throws Exception {
            Field field = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
            field.setAccessible(true);
            field.set(null, null);
        }

        @Test
        @DisplayName("should encrypt string with {ENC} prefix")
        void shouldEncryptString_withPrefix() throws Exception {
            String encrypted = EncryptionUtils.encrypt("Hello World");
            assertThat(encrypted).startsWith("{ENC}");
            assertThat(encrypted).isNotEqualTo("Hello World");
        }

        @Test
        @DisplayName("should decrypt encrypted string back to original")
        void shouldDecryptEncryptedString_backToOriginal() throws Exception {
            String original = "Sensitive patient data";
            String encrypted = EncryptionUtils.encrypt(original);
            String decrypted = EncryptionUtils.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(original);
        }

        @Test
        @DisplayName("should return null for null encrypt input")
        void shouldReturnNull_forNullEncryptInput() throws Exception {
            assertThat(EncryptionUtils.encrypt((String) null)).isNull();
        }

        @Test
        @DisplayName("should return empty for empty encrypt input")
        void shouldReturnEmpty_forEmptyEncryptInput() throws Exception {
            assertThat(EncryptionUtils.encrypt("")).isEmpty();
        }

        @Test
        @DisplayName("should return non-encrypted text as-is from decrypt")
        void shouldReturnPlainText_asIsFromDecrypt() throws Exception {
            assertThat(EncryptionUtils.decrypt("not encrypted")).isEqualTo("not encrypted");
        }

        @Test
        @DisplayName("should return null from decrypt for null input")
        void shouldReturnNull_fromDecryptForNull() throws Exception {
            assertThat(EncryptionUtils.decrypt((String) null)).isNull();
        }

        @Test
        @DisplayName("should return empty from decrypt for empty input")
        void shouldReturnEmpty_fromDecryptForEmpty() throws Exception {
            assertThat(EncryptionUtils.decrypt("")).isEmpty();
        }

        @Test
        @DisplayName("should produce different ciphertexts for same plaintext (random IV)")
        void shouldProduceDifferentCiphertexts_forSamePlaintext() throws Exception {
            String encrypted1 = EncryptionUtils.encrypt("same text");
            String encrypted2 = EncryptionUtils.encrypt("same text");
            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }
    }

    // -----------------------------------------------------------------------
    // AES-GCM without key set (should throw)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AES-GCM without secret key")
    class AesGcmWithoutKey {

        @BeforeEach
        void clearSecretKey() throws Exception {
            Field field = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
            field.setAccessible(true);
            field.set(null, null);
        }

        @Test
        @DisplayName("should throw when encrypting bytes without key")
        void shouldThrow_whenEncryptingBytesWithoutKey() {
            assertThatThrownBy(() -> EncryptionUtils.encrypt("test".getBytes()))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("Secret key not found");
        }
    }

    // -----------------------------------------------------------------------
    // Legacy SHA-1 (deprecated but still used)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("legacy SHA-1")
    class LegacySha1 {

        @Test
        @DisplayName("should return consistent hash for same input")
        @SuppressWarnings("deprecation")
        void shouldReturnConsistentHash_forSameInput() {
            byte[] hash1 = EncryptionUtils.getSha1("test");
            byte[] hash2 = EncryptionUtils.getSha1("test");
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("should return null for null input")
        @SuppressWarnings("deprecation")
        void shouldReturnNull_forNullInput() {
            assertThat(EncryptionUtils.getSha1(null)).isNull();
        }

        @Test
        @DisplayName("should return 20-byte SHA-1 hash")
        @SuppressWarnings("deprecation")
        void shouldReturn20ByteHash() {
            byte[] hash = EncryptionUtils.getSha1("hello");
            assertThat(hash).hasSize(20);
        }
    }
}
