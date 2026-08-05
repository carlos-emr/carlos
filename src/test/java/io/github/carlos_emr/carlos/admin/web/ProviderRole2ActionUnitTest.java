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
package io.github.carlos_emr.carlos.admin.web;

import java.util.stream.Stream;

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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for authorization and HTTP method enforcement on provider-role administration.
 *
 * @since 2026-08-05
 */
@DisplayName("Provider role action")
@Tag("unit")
@Tag("admin")
@Tag("security")
class ProviderRole2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        securityInfoManager = mock(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin"), eq("r"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    static Stream<Arguments> writeIntentParameters() {
        return Stream.of(
                Arguments.of("submit", "Ajouter"),
                Arguments.of("buttonUpdate", "Update"),
                Arguments.of("buttonSetPrimaryRole", "Update Primary EMR Role"));
    }

    /*
     * The Allow assertions below pin what the action sets on the response object. They are not
     * a claim about the wire: CARLOS' catch-all <error-page> makes sendError forward to
     * errorpage.jsp, and that reset strips the header before the client sees the 405 (verified
     * against Tomcat). Restoring it end-to-end is a container/filter concern, not this action's.
     */
    @ParameterizedTest(name = "GET with {0} is rejected")
    @MethodSource("writeIntentParameters")
    @DisplayName("should reject GET for every write intent")
    void shouldRejectGet_forEveryWriteIntent(String parameterName, String parameterValue) throws Exception {
        request.setMethod("GET");
        request.setParameter(parameterName, parameterValue);

        String result = new ProviderRole2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
    }

    @ParameterizedTest(name = "POST with {0} is allowed")
    @MethodSource("writeIntentParameters")
    @DisplayName("should allow POST for every write intent")
    void shouldAllowPost_forEveryWriteIntent(String parameterName, String parameterValue) throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull()))
                .thenReturn(true);
        request.setMethod("POST");
        request.setParameter(parameterName, parameterValue);

        String result = new ProviderRole2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @ParameterizedTest(name = "read-only admin cannot submit {0}")
    @MethodSource("writeIntentParameters")
    @DisplayName("should reject write intent when only read privilege is held")
    void shouldRejectWriteIntent_whenOnlyReadPrivilegeHeld(String parameterName, String parameterValue) {
        // hasPrivilege("r") is satisfied by r|u|w|x, so a read-only administrator clears the
        // page gate. Role assignment is a privilege change and must still be refused.
        request.setMethod("POST");
        request.setParameter(parameterName, parameterValue);

        ProviderRole2Action action = new ProviderRole2Action();

        assertThatExceptionOfType(SecurityException.class)
                .isThrownBy(action::execute)
                .withMessageContaining("missing required sec object (_admin or _admin.userAdmin)");
    }

    @Test
    @DisplayName("should accept write intent when userAdmin write privilege is held")
    void shouldAcceptWriteIntent_whenUserAdminWritePrivilegeHeld() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.userAdmin"), eq("w"), isNull()))
                .thenReturn(true);
        request.setMethod("POST");
        request.setParameter("buttonSetPrimaryRole", "Update Primary EMR Role");

        String result = new ProviderRole2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }

    @ParameterizedTest(name = "HEAD with {0} is rejected")
    @MethodSource("writeIntentParameters")
    @DisplayName("should reject HEAD for every write intent")
    void shouldRejectHead_forEveryWriteIntent(String parameterName, String parameterValue) throws Exception {
        request.setMethod("HEAD");
        request.setParameter(parameterName, parameterValue);

        String result = new ProviderRole2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    @DisplayName("should allow GET when request is read only")
    void shouldAllowGet_whenRequestIsReadOnly() throws Exception {
        request.setMethod("GET");
        request.setParameter("search", "Filter");

        String result = new ProviderRole2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("should accept either admin security object")
    void shouldAcceptEitherAdminSecurityObject_forUserAdminPrivilege() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("r"), isNull()))
                .thenReturn(false);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.userAdmin"), eq("r"), isNull()))
                .thenReturn(true);
        request.setMethod("GET");

        String result = new ProviderRole2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should reject request when neither admin privilege is held")
    void shouldRejectRequest_whenAdminPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), eq("r"), isNull()))
                .thenReturn(false);
        request.setMethod("POST");
        request.setParameter("buttonSetPrimaryRole", "Update Primary EMR Role");

        ProviderRole2Action action = new ProviderRole2Action();

        assertThatExceptionOfType(SecurityException.class)
                .isThrownBy(action::execute)
                .withMessageContaining("missing required sec object (_admin or _admin.userAdmin)");
    }
}
