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
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("LoggedInInfo unit tests")
@Tag("unit")
@Tag("web")
class LoggedInInfoUnitTest {

    @Test
    @DisplayName("should return session LoggedInInfo when present")
    void shouldReturnLoggedInInfo_whenSessionValuePresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute(new LoggedInInfo().getLoggedInInfoKey())).thenReturn(loggedInInfo);

        LoggedInInfo actual = LoggedInInfo.requireLoggedInInfoFromSession(request);

        assertThat(actual).isSameAs(loggedInInfo);
    }

    @Test
    @DisplayName("should throw SecurityException when session LoggedInInfo is missing")
    void shouldThrowSecurityException_whenSessionValueMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);

        assertThatThrownBy(() -> LoggedInInfo.requireLoggedInInfoFromSession(request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required session");
    }
}
