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

import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SecurityDelete2Action} covering privilege checks,
 * POST enforcement, successful delete, not-found, invalid ID, and DAO exception handling.
 *
 * @since 2026-04-11
 */
@DisplayName("SecurityDelete2Action")
@Tag("unit")
@Tag("admin")
@Tag("security")
class SecurityDelete2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private SecurityDao mockSecurityDao;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(SecurityDao.class, mockSecurityDao);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
            .thenReturn(mockLoggedInInfo);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("doc1");
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    private SecurityDelete2Action createActionWithPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull()))
            .thenReturn(true);
        mockRequest.setMethod("POST");
        return new SecurityDelete2Action();
    }

    @Nested
    @DisplayName("Privilege checks")
    class PrivilegeChecks {

        @Test
        @DisplayName("should throw SecurityException when both privileges are denied")
        void shouldThrowSecurityException_whenBothPrivilegesDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull()))
                .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.userAdmin"), eq("w"), isNull()))
                .thenReturn(false);

            SecurityDelete2Action action = new SecurityDelete2Action();
            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should pass when _admin w privilege is granted")
        void shouldPass_whenAdminPrivilegeGranted() throws Exception {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull()))
                .thenReturn(true);
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.userAdmin"), eq("w"), isNull()))
                .thenReturn(false);
            mockRequest.setMethod("POST");

            SecurityDelete2Action action = new SecurityDelete2Action();
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should pass when _admin.userAdmin w privilege is granted")
        void shouldPass_whenUserAdminPrivilegeGranted() throws Exception {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull()))
                .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.userAdmin"), eq("w"), isNull()))
                .thenReturn(true);
            mockRequest.setMethod("POST");

            SecurityDelete2Action action = new SecurityDelete2Action();
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Nested
    @DisplayName("POST enforcement")
    class PostEnforcement {

        @Test
        @DisplayName("should return NONE with 405 error on GET request")
        void shouldReturnNone_whenGetRequest() throws Exception {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull()))
                .thenReturn(true);
            mockRequest.setMethod("GET");

            SecurityDelete2Action action = new SecurityDelete2Action();
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(405);
        }
    }

    @Nested
    @DisplayName("Delete operations")
    class DeleteOperations {

        @Test
        @DisplayName("should delete entity and set success message when valid ID provided")
        void shouldDeleteEntity_whenValidIdProvided() throws Exception {
            SecurityDelete2Action action = createActionWithPrivilege();
            mockRequest.setParameter("keyword", "42");

            Security entity = mock(Security.class);
            when(entity.getUserName()).thenReturn("testuser");
            when(mockSecurityDao.find(42)).thenReturn(entity);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(mockSecurityDao).remove(entity);
            assertThat((String) mockRequest.getAttribute("msg"))
                .contains("Security entry deleted for user: testuser");
        }

        @Test
        @DisplayName("should set not-found message when entity does not exist")
        void shouldSetNotFoundMessage_whenEntityDoesNotExist() throws Exception {
            SecurityDelete2Action action = createActionWithPrivilege();
            mockRequest.setParameter("keyword", "999");
            when(mockSecurityDao.find(999)).thenReturn(null);

            action.execute();

            assertThat((String) mockRequest.getAttribute("msg"))
                .isEqualTo("Security entry not found.");
            verify(mockSecurityDao, never()).remove(any());
        }

        @Test
        @DisplayName("should set error message when keyword is invalid integer")
        void shouldSetErrorMessage_whenKeywordIsInvalidInteger() throws Exception {
            SecurityDelete2Action action = createActionWithPrivilege();
            mockRequest.setParameter("keyword", "abc");

            action.execute();

            assertThat((String) mockRequest.getAttribute("msg"))
                .isEqualTo("Invalid security identifier.");
            verify(mockSecurityDao, never()).remove(any());
        }

        @Test
        @DisplayName("should set message when no keyword is provided")
        void shouldSetMessage_whenNoKeywordProvided() throws Exception {
            SecurityDelete2Action action = createActionWithPrivilege();

            action.execute();

            assertThat((String) mockRequest.getAttribute("msg"))
                .isEqualTo("No security identifier was provided.");
        }

        @Test
        @DisplayName("should set error message when DAO remove throws RuntimeException")
        void shouldSetErrorMessage_whenDaoRemoveThrows() throws Exception {
            SecurityDelete2Action action = createActionWithPrivilege();
            mockRequest.setParameter("keyword", "42");

            Security entity = mock(Security.class);
            when(entity.getUserName()).thenReturn("testuser");
            when(mockSecurityDao.find(42)).thenReturn(entity);
            doThrow(new RuntimeException("DB error")).when(mockSecurityDao).remove(entity);

            action.execute();

            assertThat((String) mockRequest.getAttribute("msg"))
                .isEqualTo("Failed to delete security entry.");
        }

        @Test
        @DisplayName("should not include HTML encoding in msg attribute (JSP handles encoding)")
        void shouldNotIncludeHtmlEncoding_inMsgAttribute() throws Exception {
            SecurityDelete2Action action = createActionWithPrivilege();
            mockRequest.setParameter("keyword", "42");

            Security entity = mock(Security.class);
            when(entity.getUserName()).thenReturn("O'Brien & <Co>");
            when(mockSecurityDao.find(42)).thenReturn(entity);

            action.execute();

            String msg = (String) mockRequest.getAttribute("msg");
            assertThat(msg)
                .as("msg should contain raw username — the JSP is responsible for OWASP encoding")
                .contains("O'Brien & <Co>")
                .doesNotContain("&amp;")
                .doesNotContain("&lt;");
        }
    }
}
