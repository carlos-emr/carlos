/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PasswordHash} PBKDF2 password hashing security utility.
 *
 * <p>Tests hash creation, verification, format validation, and constant-time comparison.</p>
 *
 * @since 2026-03-31
 */
@DisplayName("PasswordHash Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
@Tag("security")
class PasswordHashUnitTest {

    @Nested
    @DisplayName("createHash")
    class CreateHash {

        @Test
        @DisplayName("should produce non-null hash")
        void shouldProduceNonNullHash() throws Exception {
            String hash = PasswordHash.createHash("testPassword");
            assertThat(hash).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should produce hash with 5 colon-separated sections")
        void shouldProduceHash_withFiveSections() throws Exception {
            String hash = PasswordHash.createHash("testPassword");
            String[] parts = hash.split(":");
            assertThat(parts).hasSize(PasswordHash.HASH_SECTIONS);
        }

        @Test
        @DisplayName("should start with sha1 algorithm identifier")
        void shouldStartWithSha1() throws Exception {
            String hash = PasswordHash.createHash("testPassword");
            assertThat(hash).startsWith("sha1:");
        }

        @Test
        @DisplayName("should include iteration count in hash")
        void shouldIncludeIterationCount() throws Exception {
            String hash = PasswordHash.createHash("testPassword");
            String[] parts = hash.split(":");
            int iterations = Integer.parseInt(parts[PasswordHash.ITERATION_INDEX]);
            assertThat(iterations).isEqualTo(PasswordHash.PBKDF2_ITERATIONS);
        }

        @Test
        @DisplayName("should produce different hashes for same password (salted)")
        void shouldProduceDifferentHashes_forSamePassword() throws Exception {
            String hash1 = PasswordHash.createHash("samePassword");
            String hash2 = PasswordHash.createHash("samePassword");
            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("verifyPassword")
    class VerifyPassword {

        @Test
        @DisplayName("should verify correct password")
        void shouldVerify_correctPassword() throws Exception {
            String hash = PasswordHash.createHash("correctPassword");
            assertThat(PasswordHash.verifyPassword("correctPassword", hash)).isTrue();
        }

        @Test
        @DisplayName("should reject incorrect password")
        void shouldReject_incorrectPassword() throws Exception {
            String hash = PasswordHash.createHash("correctPassword");
            assertThat(PasswordHash.verifyPassword("wrongPassword", hash)).isFalse();
        }

        @Test
        @DisplayName("should reject empty password against valid hash")
        void shouldReject_emptyPassword() throws Exception {
            String hash = PasswordHash.createHash("password123");
            assertThat(PasswordHash.verifyPassword("", hash)).isFalse();
        }

        @Test
        @DisplayName("should throw InvalidHashException for malformed hash")
        void shouldThrow_forMalformedHash() {
            assertThatThrownBy(() -> PasswordHash.verifyPassword("test", "not:a:valid:hash"))
                    .isInstanceOf(PasswordHash.InvalidHashException.class)
                    .hasMessageContaining("Fields are missing");
        }

        @Test
        @DisplayName("should throw CannotPerformOperationException for unsupported algorithm")
        void shouldThrow_forUnsupportedAlgorithm() {
            assertThatThrownBy(() -> PasswordHash.verifyPassword("test", "sha256:1000:18:c2FsdA==:aGFzaA=="))
                    .isInstanceOf(PasswordHash.CannotPerformOperationException.class)
                    .hasMessageContaining("Unsupported hash type");
        }

        @Test
        @DisplayName("should throw InvalidHashException for invalid iteration count")
        void shouldThrow_forInvalidIterations() {
            assertThatThrownBy(() -> PasswordHash.verifyPassword("test", "sha1:notanumber:18:c2FsdA==:aGFzaA=="))
                    .isInstanceOf(PasswordHash.InvalidHashException.class)
                    .hasMessageContaining("iteration count");
        }

        @Test
        @DisplayName("should throw InvalidHashException for negative iterations")
        void shouldThrow_forNegativeIterations() {
            assertThatThrownBy(() -> PasswordHash.verifyPassword("test", "sha1:-1:18:c2FsdA==:aGFzaA=="))
                    .isInstanceOf(PasswordHash.InvalidHashException.class)
                    .hasMessageContaining("Invalid number of iterations");
        }

        @Test
        @DisplayName("should throw InvalidHashException for corrupted Base64 salt")
        void shouldThrow_forCorruptedSalt() {
            assertThatThrownBy(() -> PasswordHash.verifyPassword("test", "sha1:1000:18:!!!invalid!!!:aGFzaA=="))
                    .isInstanceOf(PasswordHash.InvalidHashException.class)
                    .hasMessageContaining("Base64 decoding of salt");
        }
    }

    @Nested
    @DisplayName("constants")
    class Constants {

        @Test
        @DisplayName("should have expected salt size")
        void shouldHaveExpectedSaltSize() {
            assertThat(PasswordHash.SALT_BYTE_SIZE).isEqualTo(24);
        }

        @Test
        @DisplayName("should have expected hash size")
        void shouldHaveExpectedHashSize() {
            assertThat(PasswordHash.HASH_BYTE_SIZE).isEqualTo(18);
        }

        @Test
        @DisplayName("should have expected iteration count")
        void shouldHaveExpectedIterations() {
            assertThat(PasswordHash.PBKDF2_ITERATIONS).isEqualTo(64000);
        }
    }
}
