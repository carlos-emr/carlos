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
package io.github.carlos_emr.carlos.facility;

import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Security regression coverage for the {@code /FacilityManager} admin screen.
 *
 * <p>Pins three things the action is responsible for: the {@code _admin} privilege gate (read for
 * the renders, write for the two mutations), the POST-only contract on {@code delete}/{@code save},
 * and the fact that {@code add} — which persists nothing — stays reachable by GET so the add-new
 * form still loads.
 *
 * @since 2026-05-28
 */
@DisplayName("FacilityManager2Action")
@Tag("unit")
@Tag("facility")
@Tag("security")
class FacilityManager2ActionUnitTest extends CarlosUnitTestBase {

    private static final String ADMIN_SECURITY_OBJECT = "_admin";

    @Mock private FacilityDao facilityDao;
    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private LoggedInInfo loggedInInfo;

    private AutoCloseable mocks;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest("GET", "/FacilityManager");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should deny the facility list when _admin read rights are missing")
    void shouldDenyList_whenAdminReadMissing() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, ADMIN_SECURITY_OBJECT, "r", null))
                .thenReturn(false);

        assertThatThrownBy(action()::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining(ADMIN_SECURITY_OBJECT);
        verifyNoInteractions(facilityDao);
    }

    /**
     * A user with read-only admin rights may browse the roster but must not be able to POST a
     * mutation through: the gate escalates to a write check as soon as {@code method} names one.
     */
    @ParameterizedTest
    @ValueSource(strings = {"delete", "save"})
    @DisplayName("should deny a mutation when only _admin read rights are held")
    void shouldDenyMutation_whenAdminWriteMissing(String method) {
        when(securityInfoManager.hasPrivilege(loggedInInfo, ADMIN_SECURITY_OBJECT, "w", null))
                .thenReturn(false);
        request.setMethod("POST");
        request.addParameter("method", method);
        request.addParameter("id", "7");

        assertThatThrownBy(action()::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining(ADMIN_SECURITY_OBJECT);
        verifyNoInteractions(facilityDao);
    }

    @ParameterizedTest(name = "{1} {0} is rejected with 405")
    @CsvSource({"delete,GET", "delete,HEAD", "save,GET", "save,HEAD"})
    @DisplayName("should reject a mutating method when the request is not a POST")
    void shouldRejectMutation_whenHttpMethodIsNotPost(String method, String httpMethod) throws Exception {
        allowAdminWrite();
        request.setMethod(httpMethod);
        request.addParameter("method", method);
        request.addParameter("id", "7");

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(facilityDao);
    }

    /**
     * {@code method=add} only builds a transient {@link Facility} for the edit form, so it is not
     * gated on POST — the admin screen links to it with a plain anchor, and the global
     * {@code HttpMethodGuardFilter} excludes it for the same reason.
     */
    @Test
    @DisplayName("should render the add form when requested by GET")
    void shouldRenderAddForm_whenRequestedByGet() throws Exception {
        allowAdminRead();
        request.setMethod("GET");
        request.addParameter("method", "add");

        String result = action().execute();

        assertThat(result).isEqualTo("edit");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verifyNoInteractions(facilityDao);
    }

    @Test
    @DisplayName("should render the facility list for a bare GET with _admin read rights")
    void shouldRenderList_forBareGet() throws Exception {
        allowAdminRead();
        request.setMethod("GET");

        String result = action().execute();

        assertThat(result).isEqualTo("list");
        verify(facilityDao).findAll(true);
    }

    @Test
    @DisplayName("should disable the facility on an authorized delete POST")
    void shouldDisableFacility_onAuthorizedDeletePost() throws Exception {
        allowAdminWrite();
        request.setMethod("POST");
        request.addParameter("method", "delete");
        request.addParameter("id", "7");
        Facility facility = new Facility();
        facility.setId(7);
        // delete() calls find(Integer.valueOf(id)), which binds to AbstractDao.find(Object) rather
        // than the find(int) overload — stub through an Integer so the same method is matched.
        Integer facilityId = 7;
        when(facilityDao.find(facilityId)).thenReturn(facility);

        String result = action().execute();

        assertThat(result).isEqualTo("list");
        assertThat(facility.isDisabled()).isTrue();
        verify(facilityDao).merge(facility);
        verify(facilityDao).findAll(true);
    }

    private void allowAdminRead() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, ADMIN_SECURITY_OBJECT, "r", null))
                .thenReturn(true);
    }

    private void allowAdminWrite() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, ADMIN_SECURITY_OBJECT, "w", null))
                .thenReturn(true);
    }

    private FacilityManager2Action action() {
        return new FacilityManager2Action(facilityDao, securityInfoManager);
    }
}
