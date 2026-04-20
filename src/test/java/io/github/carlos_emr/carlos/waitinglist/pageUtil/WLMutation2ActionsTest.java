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
package io.github.carlos_emr.carlos.waitinglist.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.waitinglist.util.WLWaitingListUtil;

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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Focused regression coverage for the POST-only waiting-list mutation 2Actions
 * introduced during the JSP migration.
 *
 * @since 2026-04-14
 */
@DisplayName("Waiting-list mutation 2Actions")
@Tag("unit")
@Tag("waitinglist")
@Tag("security")
class WLMutation2ActionsTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<WLWaitingListUtil> waitingListUtilMock;
    private AutoCloseable mockitoMocks;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoMocks = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockRequest.setContextPath("/carlos");
        mockResponse = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), isNull()))
            .thenReturn(true);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
            .thenReturn(mockLoggedInInfo);

        waitingListUtilMock = mockStatic(WLWaitingListUtil.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (waitingListUtilMock != null) waitingListUtilMock.close();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoMocks != null) mockitoMocks.close();
    }

    @Nested
    @DisplayName("WLAdd2WaitingList2Action")
    class AddAction {

        @Test
        @DisplayName("should reject GET with 405 before any privilege check")
        void shouldRejectGetWith405_beforeAnyPrivilegeCheck() throws Exception {
            mockRequest.setMethod("GET");

            String result = new WLAdd2WaitingList2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(405);
            verifyNoInteractions(mockSecurityInfoManager);
            waitingListUtilMock.verifyNoInteractions();
        }

        @Test
        @DisplayName("should throw SecurityException when demographic write privilege is denied")
        void shouldThrowSecurityException_whenPrivilegeDenied() {
            mockRequest.setMethod("POST");
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), isNull()))
                .thenReturn(false);

            WLAdd2WaitingList2Action action = new WLAdd2WaitingList2Action();

            assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_demographic w");

            waitingListUtilMock.verifyNoInteractions();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "0", "-1", "abc", "1 OR 1=1"})
        @DisplayName("should return 400 when listId is invalid")
        void shouldReturn400_whenListIdIsInvalid(String invalidListId) throws Exception {
            mockRequest.setMethod("POST");
            mockRequest.setParameter("listId", invalidListId);
            mockRequest.setParameter("demographicNo", "123");

            String result = new WLAdd2WaitingList2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(400);
            waitingListUtilMock.verifyNoInteractions();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "0", "-1", "abc", "123<script>"})
        @DisplayName("should return 400 when demographicNo is invalid")
        void shouldReturn400_whenDemographicNoIsInvalid(String invalidDemographicNo) throws Exception {
            mockRequest.setMethod("POST");
            mockRequest.setParameter("listId", "7");
            mockRequest.setParameter("demographicNo", invalidDemographicNo);

            String result = new WLAdd2WaitingList2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(400);
            waitingListUtilMock.verifyNoInteractions();
        }

        @Test
        @DisplayName("should add to waiting list and redirect to demographic edit when valid POST")
        void shouldAddToWaitingListAndRedirect_whenValidPost() throws Exception {
            mockRequest.setMethod("POST");
            mockRequest.setParameter("listId", "7");
            mockRequest.setParameter("demographicNo", "123");
            mockRequest.setParameter("waitingListNote", "Needs evening slot");
            mockRequest.setParameter("onListSince", "2026-04-14");

            String result = new WLAdd2WaitingList2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getRedirectedUrl())
                .isEqualTo("/carlos/demographic/DemographicEdit?demographic_no=123");
            waitingListUtilMock.verify(
                () -> WLWaitingListUtil.add2WaitingList("7", "Needs evening slot", "123", "2026-04-14"));
            verify(mockSecurityInfoManager)
                .hasPrivilege(mockLoggedInInfo, "_demographic", "w", null);
        }
    }

    @Nested
    @DisplayName("WLRemoveFromWaitingList2Action")
    class RemoveAction {

        @Test
        @DisplayName("should reject GET with 405 before any privilege check")
        void shouldRejectGetWith405_beforeAnyPrivilegeCheck() throws Exception {
            mockRequest.setMethod("GET");

            String result = new WLRemoveFromWaitingList2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(405);
            verifyNoInteractions(mockSecurityInfoManager);
            waitingListUtilMock.verifyNoInteractions();
        }

        @Test
        @DisplayName("should throw SecurityException when demographic write privilege is denied")
        void shouldThrowSecurityException_whenPrivilegeDenied() {
            mockRequest.setMethod("POST");
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), isNull()))
                .thenReturn(false);

            WLRemoveFromWaitingList2Action action = new WLRemoveFromWaitingList2Action();

            assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_demographic w");

            waitingListUtilMock.verifyNoInteractions();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "0", "-1", "abc", "1 OR 1=1"})
        @DisplayName("should return 400 when listId is invalid")
        void shouldReturn400_whenListIdIsInvalid(String invalidListId) throws Exception {
            mockRequest.setMethod("POST");
            mockRequest.setParameter("listId", invalidListId);
            mockRequest.setParameter("demographicNo", "123");

            String result = new WLRemoveFromWaitingList2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(400);
            waitingListUtilMock.verifyNoInteractions();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "0", "-1", "abc", "123<script>"})
        @DisplayName("should return 400 when demographicNo is invalid")
        void shouldReturn400_whenDemographicNoIsInvalid(String invalidDemographicNo) throws Exception {
            mockRequest.setMethod("POST");
            mockRequest.setParameter("listId", "7");
            mockRequest.setParameter("demographicNo", invalidDemographicNo);

            String result = new WLRemoveFromWaitingList2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(400);
            waitingListUtilMock.verifyNoInteractions();
        }

        @Test
        @DisplayName("should remove from waiting list when valid POST")
        void shouldRemoveFromWaitingList_whenValidPost() throws Exception {
            mockRequest.setMethod("POST");
            mockRequest.setParameter("listId", "9");
            mockRequest.setParameter("demographicNo", "321");

            String result = new WLRemoveFromWaitingList2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            waitingListUtilMock.verify(() -> WLWaitingListUtil.removeFromWaitingList("9", "321"));
            verify(mockSecurityInfoManager)
                .hasPrivilege(mockLoggedInInfo, "_demographic", "w", null);
        }
    }
}
