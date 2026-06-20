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
package io.github.carlos_emr.carlos.utility.password;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Deprecated SHA password encoder")
@Tag("unit")
@Tag("utility")
class DeprecatedShaPasswordEncoderUnitTest {

    private static final String LEGACY_PASSWORD = "legacy-password";
    private static final String LEGACY_SHA1_HASH =
            "-4331-12498-123-53-58-47-3511810312215-43-120-56-3368-276";

    @Test
    void shouldVerifyLegacyShaPassword_whenExistingHashProvided() {
        Deprecated_SHA_PasswordEncoder encoder = new Deprecated_SHA_PasswordEncoder();

        assertThat(encoder.matches(LEGACY_PASSWORD, LEGACY_SHA1_HASH)).isTrue();
        assertThat(encoder.matches("wrong-password", LEGACY_SHA1_HASH)).isFalse();
    }

    @Test
    void shouldThrowException_whenEncodingLegacyShaPassword() {
        Deprecated_SHA_PasswordEncoder encoder = new Deprecated_SHA_PasswordEncoder();

        assertThatThrownBy(() -> encoder.encode(LEGACY_PASSWORD))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("verification-only");
    }

    @Test
    void shouldUseBcrypt_forNewPasswordHashes() {
        String hash = PasswordHashHelper.encodePassword(LEGACY_PASSWORD);

        assertThat(hash).startsWith("{bcrypt}");
        assertThat(PasswordHashHelper.matches(LEGACY_PASSWORD, hash)).isTrue();
        assertThat(PasswordHashHelper.matches(LEGACY_PASSWORD, LEGACY_SHA1_HASH)).isTrue();
        assertThat(PasswordHashHelper.upgradeEncoding(LEGACY_SHA1_HASH)).isTrue();
    }
}
