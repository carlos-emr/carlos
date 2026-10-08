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
import org.apache.struts2.ActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViewChartSpace2Action}; no Spring context.
 *
 * @since 2026-10-08
 */
@Tag("unit")
class ViewChartSpace2ActionUnitTest {

    private static final String SESSION_KEY = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockHttpSession session;
    private SecurityInfoManager securityInfoManager;
    private ViewChartSpace2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        session = new MockHttpSession();
        request.setSession(session);
        request.setMethod("GET");
        session.setAttribute(SESSION_KEY, mock(LoggedInInfo.class));
        ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
        securityInfoManager = mock(SecurityInfoManager.class);
        action = new ViewChartSpace2Action(securityInfoManager);
    }

    @AfterEach
    void tearDown() {
        ActionContext.clear();
    }

    @Test
    void shouldReturnSuccess_whenEChartReadGrantedForPatient() throws Exception {
        request.setParameter("demographicNo", "2");
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), eq("2"))).thenReturn(true);

        assertThat(action.execute()).isEqualTo("success");
        assertThat(request.getAttribute("demographicNo")).isEqualTo(2);
    }

    @Test
    void shouldThrowSecurityException_whenEChartReadMissing() {
        request.setParameter("demographicNo", "2");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_eChart)");
    }

    @Test
    void shouldThrowSecurityException_whenNoLoggedInInfo() {
        session.removeAttribute(SESSION_KEY);
        request.setParameter("demographicNo", "2");

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    void shouldReturn400_whenDemographicNoInvalid() throws Exception {
        request.setParameter("demographicNo", "abc");

        assertThat(action.execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(securityInfoManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void shouldReturn405_forNonGetMethods(String method) throws Exception {
        request.setMethod(method);
        request.setParameter("demographicNo", "2");

        assertThat(action.execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD");
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    void shouldReturnSuccess_forHead() throws Exception {
        request.setMethod("HEAD");
        request.setParameter("demographicNo", "2");
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), eq("2"))).thenReturn(true);

        assertThat(action.execute()).isEqualTo("success");
    }
}
