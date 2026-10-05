/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.www.admin;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.password.PasswordHashHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Boundaries for administrator password edits and unchanged-credential submissions. */
@Tag("unit")
class SecurityUpdatePasswordValidatorUnitTest extends CarlosUnitTestBase {
    private CarlosProperties properties() {
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        return properties;
    }

    private String candidate(int length) {
        return new String(new char[] {'A', 'b', '1', '!'}) + "x".repeat(length - 4);
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 20, 28, 32})
    void shouldPreserveEntirePassword_whenLongerThanOldEditLimit(int length) {
        String value = candidate(length);
        assertThat(SecurityUpdatePasswordValidator.validate(value, value, properties())).isNull();
        String encoded = PasswordHashHelper.encodePassword(value);
        assertThat(PasswordHashHelper.matches(value, encoded)).isTrue();
        assertThat(PasswordHashHelper.matches(value.substring(0, 15), encoded)).isFalse();
    }

    @Test
    void shouldRejectOverlongPassword_whenComplexityChecksAreDisabled() {
        CarlosProperties properties = properties();
        when(properties.getProperty("IGNORE_PASSWORD_REQUIREMENTS")).thenReturn("true");
        String value = candidate(33);
        assertThat(SecurityUpdatePasswordValidator.validate(value, value, properties))
                .isEqualTo("admin.securityupdate.msgPasswordTooLong");
    }

    @Test
    void shouldEnforceEncoderByteLimit_whenMultibytePasswordFitsCharacterLimit() {
        String boundary = candidate(4) + "\u20ac".repeat(22) + "xx";
        assertThat(SecurityUpdatePasswordValidator.validate(boundary, boundary, properties())).isNull();
        String overlong = boundary + "x";
        assertThat(SecurityUpdatePasswordValidator.validate(overlong, overlong, properties()))
                .isEqualTo("admin.securityupdate.msgPasswordEncodingTooLong");
    }

    @Test
    void shouldRequireConfirmation_whenChangingOrRetainingPassword() {
        String value = candidate(20);
        for (String confirmation : new String[] {null, "", candidate(19), value + " "}) {
            assertThat(SecurityUpdatePasswordValidator.validate(value, confirmation, properties()))
                    .isEqualTo("admin.securityrecord.msgPasswordNotConfirmed");
        }
        assertThat(SecurityUpdatePasswordValidator.validate("*********", null, properties()))
                .isEqualTo("admin.securityrecord.msgPasswordNotConfirmed");
    }

    @Test
    void shouldRejectMissingOrWeakPassword_whenNotUsingUnchangedSentinel() {
        for (String value : new String[] {null, "", "short", "lowercaseonly"}) {
            assertThat(SecurityUpdatePasswordValidator.validate(value, value, properties()))
                    .isEqualTo("admin.securityaddsecurity.msgPasswordInvalid");
        }
    }

    @Test
    void shouldRetainExistingPassword_whenConfirmedSentinelDoesNotMeetCurrentPolicy() {
        CarlosProperties properties = properties();
        when(properties.getProperty("password_min_length")).thenReturn("20");
        assertThat(SecurityUpdatePasswordValidator.validate("*********", "*********", properties)).isNull();
    }
}
