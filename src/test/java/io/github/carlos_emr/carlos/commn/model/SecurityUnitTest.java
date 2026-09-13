/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.commn.model;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.password.PasswordHashHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("model")
@DisplayName("Security password compatibility")
@SuppressWarnings("deprecation")
class SecurityUnitTest extends CarlosUnitTestBase {
    private static final String LEGACY_PASSWORD = "legacy-password";
    private static final String LEGACY_SHA1_HASH =
            "-4331-12498-123-53-58-47-3511810312215-43-120-56-3368-276";

    @Test
    @DisplayName("should match legacy SHA-1 password when stored password is a legacy hash")
    void shouldMatchLegacyShaPassword_whenStoredPasswordIsLegacyHash() {
        TestSecurity security = securityWithPassword(LEGACY_SHA1_HASH);

        assertThat(security.checkPassword(LEGACY_PASSWORD)).isTrue();
        assertThat(security.throttleCount).isZero();
    }

    @Test
    @DisplayName("should match BCrypt password when stored password uses current hash")
    void shouldMatchBcryptPassword_whenStoredPasswordUsesCurrentHash() {
        TestSecurity security = securityWithPassword(PasswordHashHelper.encodePassword(LEGACY_PASSWORD));

        assertThat(security.checkPassword(LEGACY_PASSWORD)).isTrue();
        assertThat(security.throttleCount).isZero();
    }

    @Test
    @DisplayName("should throttle and reject password when stored password does not match")
    void shouldThrottleAndRejectPassword_whenStoredPasswordDoesNotMatch() {
        TestSecurity security = securityWithPassword(LEGACY_SHA1_HASH);

        assertThat(security.checkPassword("wrong-password")).isFalse();
        assertThat(security.throttleCount).isOne();
    }

    @Test
    @DisplayName("should throttle and reject password when stored hash has an unknown prefix")
    void shouldThrottleAndRejectPassword_whenStoredHashHasUnknownPrefix() {
        TestSecurity security = securityWithPassword("{unknown}not-a-valid-hash");

        assertThat(security.checkPassword(LEGACY_PASSWORD)).isFalse();
        assertThat(security.throttleCount).isOne();
    }

    @Test
    @DisplayName("should throttle and reject password when input password is missing")
    void shouldThrottleAndRejectPassword_whenInputPasswordIsMissing() {
        TestSecurity security = securityWithPassword(LEGACY_SHA1_HASH);

        assertThat(security.checkPassword(null)).isFalse();
        assertThat(security.throttleCount).isOne();
    }

    @Test
    @DisplayName("should reject password when stored password is missing")
    void shouldRejectPassword_whenStoredPasswordIsMissing() {
        TestSecurity security = securityWithPassword(null);

        assertThat(security.checkPassword(LEGACY_PASSWORD)).isFalse();
        assertThat(security.throttleCount).isZero();
    }

    private static TestSecurity securityWithPassword(String password) {
        TestSecurity security = new TestSecurity();
        security.setPassword(password);
        return security;
    }

    private static class TestSecurity extends Security {
        private int throttleCount;

        @Override
        protected void throttleOnFailedLogin() {
            throttleCount++;
        }
    }
}
