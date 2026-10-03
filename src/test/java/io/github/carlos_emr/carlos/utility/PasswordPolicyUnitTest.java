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
class PasswordPolicyUnitTest {
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
    void shouldHonorConfiguredCharacters_whenWhitespaceIsExplicitlyInSpecialGroup() {
        assertThat(PasswordPolicy.countGroups("abcdefG ", "abcdef", "G", "123", "! ")).isEqualTo(3);
        assertThat(PasswordPolicy.countGroups("abcdefG ", "abcdef", "G", "123", "!")).isEqualTo(2);
    }
}
