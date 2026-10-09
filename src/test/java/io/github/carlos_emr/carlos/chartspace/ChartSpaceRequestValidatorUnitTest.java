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
package io.github.carlos_emr.carlos.chartspace;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChartSpaceRequestValidator}. They pin the check order
 * shared by every ChartSpace action: method, session, parameter, privilege.
 *
 * @since 2026-10-08
 */
@Tag("unit")
class ChartSpaceRequestValidatorUnitTest {

    private static final String SESSION_KEY = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockHttpSession session;
    private LoggedInInfo info;
    private SecurityInfoManager securityInfoManager;
    private ChartSpaceRequestValidator validator;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        session = new MockHttpSession();
        request.setSession(session);
        request.setMethod("GET");
        info = mock(LoggedInInfo.class);
        session.setAttribute(SESSION_KEY, info);
        securityInfoManager = mock(SecurityInfoManager.class);
        validator = new ChartSpaceRequestValidator(securityInfoManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void shouldWrite405_forNonGetMethods(String method) throws Exception {
        request.setMethod(method);
        request.setParameter("demographicNo", "2");

        Optional<ChartSpaceRequestValidator.Validated> result = validator.validate(request, response);

        assertThat(result).isEmpty();
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD");
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    void shouldThrowSecurityException_whenNoLoggedInInfo() {
        session.removeAttribute(SESSION_KEY);
        request.setParameter("demographicNo", "2");

        assertThatThrownBy(() -> validator.validate(request, response))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing session");
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    void shouldWrite400_whenDemographicNoInvalid() throws Exception {
        request.setParameter("demographicNo", "abc");

        Optional<ChartSpaceRequestValidator.Validated> result = validator.validate(request, response);

        assertThat(result).isEmpty();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    void shouldThrowSecurityException_whenEChartReadMissing() {
        request.setParameter("demographicNo", "2");

        assertThatThrownBy(() -> validator.validate(request, response))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_eChart)");
    }

    @Test
    void shouldReturnValidated_whenEverythingChecksOut() throws Exception {
        request.setParameter("demographicNo", "2");
        when(securityInfoManager.hasPrivilege(info, "_eChart", "r", "2")).thenReturn(true);

        Optional<ChartSpaceRequestValidator.Validated> result = validator.validate(request, response);

        assertThat(result).isPresent();
        assertThat(result.get().demographicNo()).isEqualTo(2);
        assertThat(result.get().loggedInInfo()).isSameAs(info);
        verify(securityInfoManager).hasPrivilege(info, "_eChart", "r", "2");
    }

    @Test
    void shouldAccept_forHead() throws Exception {
        request.setMethod("HEAD");
        request.setParameter("demographicNo", "2");
        when(securityInfoManager.hasPrivilege(info, "_eChart", "r", "2")).thenReturn(true);

        assertThat(validator.validate(request, response)).isPresent();
    }
}
