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
package io.github.carlos_emr.carlos.status.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.utility.SessionConstants;

/**
 * The session heartbeat the encounter's Sign &amp; Save preflight polls before it submits. A
 * signed-in session that still has to choose a facility is reported separately
 * ({@code facilitySelectionPending}), because LoginFilter redirects every other route to
 * {@code /select_facility} and the submitted note would be lost there.
 *
 * @since 2026-11-26
 */
@Tag("unit")
@Tag("security")
@DisplayName("SessionHeartbeat2Action")
class SessionHeartbeat2ActionUnitTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/status/SessionHeartbeat");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private MockedStatic<ServletActionContext> servletActionContext;

    @BeforeEach
    void setUp() {
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        servletActionContext.close();
    }

    private MockHttpSession signedInSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", "999998");
        request.setSession(session);
        return session;
    }

    @Test
    @DisplayName("should report a pending facility selection for a signed-in session that has not chosen one")
    void shouldReportFacilitySelectionPending_whenSignedInSessionAwaitsFacility() throws Exception {
        signedInSession().setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);

        String result = new SessionHeartbeat2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString()).isEqualTo("{\"valid\":true,\"facilitySelectionPending\":true}");
    }

    @Test
    @DisplayName("should report only validity for a signed-in session with its facility chosen")
    void shouldReportValidOnly_whenSignedInSessionHasNoPendingFacility() throws Exception {
        signedInSession();

        new SessionHeartbeat2Action().execute();

        assertThat(response.getContentAsString()).isEqualTo("{\"valid\":true}");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, no-cache, must-revalidate");
        assertThat(response.getContentType()).startsWith("application/json");
    }

    @Test
    @DisplayName("should not report a pending facility when the flag is set but is not Boolean.TRUE")
    void shouldReportValidOnly_whenPendingFlagIsNotTrue() throws Exception {
        signedInSession().setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.FALSE);

        new SessionHeartbeat2Action().execute();

        assertThat(response.getContentAsString()).isEqualTo("{\"valid\":true}");
    }

    @Test
    @DisplayName("should report invalid without the facility flag when the session has no signed-in user")
    void shouldReportInvalid_whenSessionHasNoUser() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);
        request.setSession(session);

        new SessionHeartbeat2Action().execute();

        assertThat(response.getContentAsString()).isEqualTo("{\"valid\":false}");
    }

    @Test
    @DisplayName("should report invalid without creating a session when the request has none")
    void shouldReportInvalidWithoutCreatingSession_whenRequestHasNoSession() throws Exception {
        new SessionHeartbeat2Action().execute();

        assertThat(response.getContentAsString()).isEqualTo("{\"valid\":false}");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("should refuse a POST with 405 and an Allow header")
    void shouldRefuseWith405_forPost() throws Exception {
        request.setMethod("POST");

        new SessionHeartbeat2Action().execute();

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD");
    }
}
