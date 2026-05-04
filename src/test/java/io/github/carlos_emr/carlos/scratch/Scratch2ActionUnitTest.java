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
package io.github.carlos_emr.carlos.scratch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for scratchpad request ownership checks.
 *
 * @since 2026-05-04
 */
@Tag("unit")
@DisplayName("Scratch2Action")
class Scratch2ActionUnitTest {

    @Test
    @DisplayName("should allow save when providerNo request parameter is absent")
    void shouldAllowSave_whenProviderNoRequestParameterIsAbsent() {
        assertThat(Scratch2Action.isRequestForSessionProvider("999998", null)).isTrue();
    }

    @Test
    @DisplayName("should allow save when providerNo request parameter is blank")
    void shouldAllowSave_whenProviderNoRequestParameterIsBlank() {
        assertThat(Scratch2Action.isRequestForSessionProvider("999998", " ")).isTrue();
    }

    @Test
    @DisplayName("should allow save when providerNo matches session user")
    void shouldAllowSave_whenProviderNoMatchesSessionUser() {
        assertThat(Scratch2Action.isRequestForSessionProvider("999998", "999998")).isTrue();
    }

    @Test
    @DisplayName("should reject save when providerNo differs from session user")
    void shouldRejectSave_whenProviderNoDiffersFromSessionUser() {
        assertThat(Scratch2Action.isRequestForSessionProvider("999998", "123456")).isFalse();
    }

    @Test
    @DisplayName("should reject save when session user is absent")
    void shouldRejectSave_whenSessionUserIsAbsent() {
        assertThat(Scratch2Action.isRequestForSessionProvider(null, null)).isFalse();
        assertThat(Scratch2Action.isRequestForSessionProvider(" ", null)).isFalse();
    }
}
