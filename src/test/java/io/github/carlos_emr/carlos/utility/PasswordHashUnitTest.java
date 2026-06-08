/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.utility.password.PasswordHashHelper;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PasswordHashHelper} password hashing security utility.
 *
 * <p>Tests hash creation, verification, and input validation through the Spring
 * Security delegating password encoder used by CARLOS EMR.</p>
 *
 * @since 2026-03-31
 */
@DisplayName("PasswordHashHelper Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
@Tag("security")
class PasswordHashUnitTest {

    @Nested
    @DisplayName("encodePassword")
    class EncodePassword {

        @Test
        @DisplayName("should produce non-null bcrypt hash")
        void shouldProduceHash_withBcryptPrefix() {
            String hash = PasswordHashHelper.encodePassword("testPassword");

            assertThat(hash).isNotNull().isNotEmpty().startsWith("{bcrypt}");
        }

        @Test
        @DisplayName("should produce different hashes for same password")
        void shouldProduceDifferentHashes_forSamePassword() {
            String hash1 = PasswordHashHelper.encodePassword("samePassword");
            String hash2 = PasswordHashHelper.encodePassword("samePassword");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("should throw for null password")
        void shouldThrow_forNullPassword() {
            assertThatThrownBy(() -> PasswordHashHelper.encodePassword(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("matches")
    class Matches {

        @Test
        @DisplayName("should verify correct password")
        void shouldVerifyPassword_whenPasswordMatches() {
            String hash = PasswordHashHelper.encodePassword("correctPassword");

            assertThat(PasswordHashHelper.matches("correctPassword", hash)).isTrue();
        }

        @Test
        @DisplayName("should reject incorrect password")
        void shouldRejectPassword_whenPasswordDoesNotMatch() {
            String hash = PasswordHashHelper.encodePassword("correctPassword");

            assertThat(PasswordHashHelper.matches("wrongPassword", hash)).isFalse();
        }

        @Test
        @DisplayName("should throw for null encoded password")
        void shouldThrow_forNullEncodedPassword() {
            assertThatThrownBy(() -> PasswordHashHelper.matches("password", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("upgradeEncoding")
    class UpgradeEncoding {

        @Test
        @DisplayName("should not require upgrade for newly encoded password")
        void shouldNotRequireUpgrade_forNewHash() {
            String hash = PasswordHashHelper.encodePassword("password123");

            assertThat(PasswordHashHelper.upgradeEncoding(hash)).isFalse();
        }
    }
}
