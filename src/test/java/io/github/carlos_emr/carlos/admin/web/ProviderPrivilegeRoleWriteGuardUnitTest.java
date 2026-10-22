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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Provider Privilege and Provider Role write from their JSPs on parameter names ({@code submit},
 * {@code buttonUpdate}, {@code buttonSetPrimaryRole}) rather than on {@code method}, so their gates
 * must refuse any of those parameters unless the request is a POST. A GET carries no CSRF token: before
 * this guard, a link could rewrite privilege bits or a provider's role (issues #3394 and #2862).
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("security")
@Tag("admin")
@DisplayName("Provider Privilege and Provider Role refuse writes that are not posted")
class ProviderPrivilegeRoleWriteGuardUnitTest extends CarlosUnitTestBase {

    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private void grant(String object, String level) {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq(object), eq(level), isNull())).thenReturn(true);
    }

    private void assertRefusedWith405(String result) {
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
    }

    @Nested
    @DisplayName("Provider Privilege")
    class ProviderPrivilege {

        @BeforeEach
        void grantAdminWrite() {
            grant("_admin", "w");
        }

        @Test
        @DisplayName("should open the page on a bare GET")
        void shouldOpenPage_whenBareGet() throws Exception {
            request.setMethod("GET");

            assertThat(new ProviderPrivilege2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @ParameterizedTest(name = "{0} {1}={2}")
        @CsvSource({
                "GET, buttonUpdate, Update",
                "HEAD, buttonUpdate, Update",
                "GET, submit, Add",
                "GET, submit, Delete",
                "GET, buttonUpdate, ''",
                "PUT, buttonUpdate, Update"
        })
        @DisplayName("should refuse a write parameter that is not posted")
        void shouldRefuse_whenWriteParameterIsNotPosted(String httpMethod, String param, String value)
                throws Exception {
            request.setMethod(httpMethod);
            request.addParameter(param, value);

            assertRefusedWith405(new ProviderPrivilege2Action().execute());
        }

        @ParameterizedTest
        @ValueSource(strings = {"buttonUpdate", "submit"})
        @DisplayName("should let a posted write through to the page")
        void shouldAllow_whenWriteIsPosted(String param) throws Exception {
            request.setMethod("POST");
            request.addParameter(param, "Update");

            assertThat(new ProviderPrivilege2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should refuse a caller without write on either admin object")
        void shouldThrow_whenCallerLacksWrite() {
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin"), eq("w"), isNull()))
                    .thenReturn(false);
            request.setMethod("GET");

            ProviderPrivilege2Action action = new ProviderPrivilege2Action();
            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_admin or _admin.userAdmin)");
        }
    }

    @Nested
    @DisplayName("Provider Role")
    class ProviderRole {

        @Test
        @DisplayName("should open the page and filter its list by GET with read only")
        void shouldOpenAndFilter_whenGetWithReadOnly() throws Exception {
            grant("_admin.userAdmin", "r");
            request.setMethod("GET");
            request.addParameter("keyword", "FAKE");
            request.addParameter("search", "Filter");

            assertThat(new ProviderRole2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @ParameterizedTest(name = "{0} {1}={2}")
        @CsvSource({
                "GET, submit, Add",
                "GET, submit, Ajouter",
                "GET, submit, Supprimer",
                "GET, submit, Borrar",
                "HEAD, submit, Delete",
                "GET, buttonUpdate, Update",
                "GET, buttonSetPrimaryRole, Update Primary EMR Role",
                "GET, submit, ''"
        })
        @DisplayName("should refuse every write parameter, in any language, that is not posted")
        void shouldRefuse_whenWriteParameterIsNotPosted(String httpMethod, String param, String value)
                throws Exception {
            grant("_admin", "w");
            grant("_admin", "r");
            request.setMethod(httpMethod);
            request.addParameter(param, value);

            assertRefusedWith405(new ProviderRole2Action().execute());
        }

        @ParameterizedTest
        @ValueSource(strings = {"submit", "buttonUpdate", "buttonSetPrimaryRole"})
        @DisplayName("should refuse a posted write from a caller who may only read")
        void shouldThrow_whenPostedWriteHasReadOnly(String param) {
            grant("_admin", "r");
            request.setMethod("POST");
            request.addParameter(param, "Add");

            ProviderRole2Action action = new ProviderRole2Action();
            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_admin or _admin.userAdmin)");
        }

        @ParameterizedTest
        @CsvSource({"_admin, submit", "_admin.userAdmin, buttonUpdate", "_admin, buttonSetPrimaryRole"})
        @DisplayName("should let a posted write through for a caller with write")
        void shouldAllow_whenPostedWriteHasWrite(String object, String param) throws Exception {
            grant(object, "r");
            grant(object, "w");
            request.setMethod("POST");
            request.addParameter(param, "Add");

            assertThat(new ProviderRole2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should refuse a caller without read before looking at the parameters")
        void shouldThrow_whenCallerLacksRead() {
            request.setMethod("GET");
            request.addParameter("submit", "Add");

            ProviderRole2Action action = new ProviderRole2Action();
            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
