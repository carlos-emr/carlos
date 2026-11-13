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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.CarlosProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Configuration boundaries and explicit clinic character-set compatibility. */
@Tag("unit")
class PasswordPolicyUnitTest extends CarlosUnitTestBase {
    private static final String EIGHT_CHARACTERS_THREE_GROUPS =
            new String(new char[] {'A', 'b', 'c', 'd', 'e', 'f', 'g', '1'});
    private CarlosProperties properties() {
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        return properties;
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "5", "2147483647", "bad"})
    void shouldUseDefaultGroups_whenConfigurationIsInvalid(String value) {
        CarlosProperties properties = properties();
        when(properties.getProperty("password_min_groups")).thenReturn(value);
        String twoGroups = new String(new char[] {'A', 'b', 'c', 'd', 'e', 'f', 'g', 'h'});
        assertThat(PasswordPolicy.validate(twoGroups, properties).isValid()).isFalse();
        assertThat(PasswordPolicy.validate(EIGHT_CHARACTERS_THREE_GROUPS, properties).isValid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "bad", "2147483648"})
    void shouldUseDefaultLength_whenConfigurationIsInvalid(String value) {
        CarlosProperties properties = properties();
        when(properties.getProperty("password_min_length")).thenReturn(value);
        // Synthetic boundary input: seven characters must fail despite all four character groups.
        String shortCandidate = new String(new char[] {'A', 'b', '1', '!', 'x', 'y', 'z'});
        assertThat(PasswordPolicy.validate(shortCandidate, properties).isValid()).isFalse();
        assertThat(PasswordPolicy.validate(EIGHT_CHARACTERS_THREE_GROUPS, properties).isValid()).isTrue();
    }

    @Test
    void shouldRefuseWithLengthRule_whenPasswordIsShorterThanConfiguredMinimum() {
        CarlosProperties properties = properties();
        when(properties.getProperty("password_min_length")).thenReturn("10");
        String nineCharacters = new String(new char[] {'A', 'b', 'c', 'd', 'e', 'f', 'g', '1', '!'});

        PasswordPolicy.Validation refused = PasswordPolicy.validate(nineCharacters, properties);

        assertThat(refused.isValid()).isFalse();
        assertThat(refused.auditReason()).isEqualTo("password_policy_min_length");
        assertThat(refused.message(key -> "[" + key + "]")).isEqualTo(
                "[password.policy.violation.msgPasswordLengthError] 10 [password.policy.violation.msgSymbols]");
        assertThat(PasswordPolicy.validate(nineCharacters + "x", properties).isValid()).isTrue();
    }

    @Test
    void shouldRefuseWithGroupRule_whenPasswordUsesFewerGroupsThanConfigured() {
        CarlosProperties properties = properties();
        when(properties.getProperty("password_min_groups")).thenReturn("4");

        PasswordPolicy.Validation refused = PasswordPolicy.validate(EIGHT_CHARACTERS_THREE_GROUPS, properties);

        assertThat(refused.isValid()).isFalse();
        assertThat(refused.auditReason()).isEqualTo("password_policy_min_groups");
        assertThat(refused.message(key -> "[" + key + "]")).isEqualTo(
                "[password.policy.violation.msgPasswordStrengthError] 4 [password.policy.violation.msgPasswordGroups]");
        assertThat(PasswordPolicy.validate(EIGHT_CHARACTERS_THREE_GROUPS + "!", properties).isValid()).isTrue();
    }

    @Test
    void shouldAcceptAnyPassword_whenRequirementsAreIgnored() {
        CarlosProperties properties = properties();
        when(properties.getProperty("IGNORE_PASSWORD_REQUIREMENTS")).thenReturn("true");

        assertThat(PasswordPolicy.validate("a", properties).isValid()).isTrue();
    }

    @Test
    void shouldRefusePin_whenShorterThanConfiguredMinimum() {
        CarlosProperties properties = properties();
        when(properties.getProperty("password_pin_min_length")).thenReturn("6");

        PasswordPolicy.Validation refused = PasswordPolicy.validatePinLength("12345", properties);

        assertThat(refused.isValid()).isFalse();
        assertThat(refused.auditReason()).isEqualTo("pin_policy_min_length");
        assertThat(refused.message(key -> "[" + key + "]")).isEqualTo(
                "[password.policy.violation.msgPinLengthError] 6 [password.policy.violation.msgDigits]");
        assertThat(PasswordPolicy.validatePinLength("123456", properties).isValid()).isTrue();
        assertThat(PasswordPolicy.validatePinLength(null, properties).isValid()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "bad", "2147483648"})
    void shouldUseDefaultPinLength_whenConfigurationIsInvalid(String value) {
        CarlosProperties properties = properties();
        when(properties.getProperty("password_pin_min_length")).thenReturn(value);

        assertThat(PasswordPolicy.validatePinLength("123", properties).isValid()).isFalse();
        assertThat(PasswordPolicy.validatePinLength("1234", properties).isValid()).isTrue();
    }

    @Test
    void shouldKeepPinRule_whenPasswordRequirementsAreIgnored() {
        CarlosProperties properties = properties();
        when(properties.getProperty("IGNORE_PASSWORD_REQUIREMENTS")).thenReturn("true");

        assertThat(PasswordPolicy.validatePinLength("12", properties).isValid()).isFalse();
    }

    @Test
    void shouldHonorConfiguredCharacters_whenWhitespaceIsExplicitlyInSpecialGroup() {
        assertThat(PasswordPolicy.countGroups("abcdefG ", "abcdef", "G", "123", "! ")).isEqualTo(3);
        assertThat(PasswordPolicy.countGroups("abcdefG ", "abcdef", "G", "123", "!")).isEqualTo(2);
    }
}
