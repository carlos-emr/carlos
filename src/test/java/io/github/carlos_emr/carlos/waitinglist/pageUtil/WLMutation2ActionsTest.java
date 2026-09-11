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

    @Nested
    @DisplayName("WLSetupDisplayWaitingList2Action row selectors")
    class SetupDisplayWaitingListSelectors {

        @Test
        @DisplayName("should accept only the page's own indexed row field names as selectors")
        void shouldAcceptIndexedRowFields_asSelectors() {
            assertThat(WLSetupDisplayWaitingList2Action.isRowSelector("waitingListBean[0].note")).isTrue();
            assertThat(WLSetupDisplayWaitingList2Action.isRowSelector("waitingListBean[12].demographicNo")).isTrue();
            assertThat(WLSetupDisplayWaitingList2Action.isRowSelector("waitingListBean[3].onListSince")).isTrue();
            // Names the packaged WAF exempts by pattern for other pages, and near misses.
            assertThat(WLSetupDisplayWaitingList2Action.isRowSelector("comments-1")).isFalse();
            assertThat(WLSetupDisplayWaitingList2Action.isRowSelector("test_1.labnotes")).isFalse();
            assertThat(WLSetupDisplayWaitingList2Action.isRowSelector("contact_1.note")).isFalse();
            assertThat(WLSetupDisplayWaitingList2Action.isRowSelector("waitingListBean[0].notes")).isFalse();
            assertThat(WLSetupDisplayWaitingList2Action.isRowSelector("xwaitingListBean[0].note")).isFalse();
            assertThat(WLSetupDisplayWaitingList2Action.isRowSelector(null)).isFalse();
        }

        @Test
        @DisplayName("should answer 400 and persist nothing when a selector points outside the row")
        void shouldReject400_whenSelectorPointsOutsideRow() throws Exception {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull()))
                .thenReturn(true);
            mockRequest.setMethod("POST");
            mockRequest.setParameter("update", "Y");
            mockRequest.setParameter("waitingListId", "7");
            mockRequest.setParameter("demographicNumSelected", "waitingListBean[0].demographicNo");
            mockRequest.setParameter("wlNoteSelected", "comments-1");
            mockRequest.setParameter("onListSinceSelected", "waitingListBean[0].onListSince");
            mockRequest.setParameter("comments-1", "smuggled");

            String result = new WLSetupDisplayWaitingList2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(400);
            waitingListUtilMock.verifyNoInteractions();
        }

        @Test
        @DisplayName("should update the record with the selector-named values when the selectors share one row")
        void shouldUpdateRecord_whenSelectorsShareOneRow() throws Exception {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull()))
                .thenReturn(true);
            mockRequest.setMethod("POST");
            mockRequest.setParameter("update", "Y");
            mockRequest.setParameter("waitingListId", "7");
            mockRequest.setParameter("demographicNumSelected", "waitingListBean[0].demographicNo");
            mockRequest.setParameter("wlNoteSelected", "waitingListBean[0].note");
            mockRequest.setParameter("onListSinceSelected", "waitingListBean[0].onListSince");
            mockRequest.setParameter("waitingListBean[0].demographicNo", "42");
            mockRequest.setParameter("waitingListBean[0].note", "reviewed & ready");
            mockRequest.setParameter("waitingListBean[0].onListSince", "2026-06-01");

            // The mutation runs first; the page render that follows needs a full session and DB
            // that a unit test does not provide, so it is allowed to throw after the update under
            // test. This is also the path the page's own indexed field names take, so it is the
            // regression guard that a normal waiting-list save is NOT rejected by the selector check.
            try {
                new WLSetupDisplayWaitingList2Action().execute();
            } catch (Exception pageRenderNeedsSession) {
                // intentional: only the mutation is under test here
            }

            waitingListUtilMock.verify(() ->
                WLWaitingListUtil.updateWaitingListRecord("7", "reviewed & ready", "42", "2026-06-01"));
            assertThat(mockResponse.getStatus()).isNotEqualTo(400);
        }

        @Test
        @DisplayName("should answer 400 and persist nothing when the selectors span more than one row")
        void shouldReject400_whenSelectorsSpanMultipleRows() throws Exception {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull()))
                .thenReturn(true);
            mockRequest.setMethod("POST");
            mockRequest.setParameter("update", "Y");
            mockRequest.setParameter("waitingListId", "7");
            // Each selector is well-formed, but demographicNo names row 0 while note names row 1.
            mockRequest.setParameter("demographicNumSelected", "waitingListBean[0].demographicNo");
            mockRequest.setParameter("wlNoteSelected", "waitingListBean[1].note");
            mockRequest.setParameter("onListSinceSelected", "waitingListBean[0].onListSince");
            mockRequest.setParameter("waitingListBean[0].demographicNo", "42");
            mockRequest.setParameter("waitingListBean[1].note", "smuggled from another row");
            mockRequest.setParameter("waitingListBean[0].onListSince", "2026-06-01");

            String result = new WLSetupDisplayWaitingList2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(400);
            waitingListUtilMock.verifyNoInteractions();
        }

        @Test
        @DisplayName("should read the row index only from a well-formed selector")
        void shouldReadRowIndex_onlyFromWellFormedSelector() {
            assertThat(WLSetupDisplayWaitingList2Action.rowIndexOf("waitingListBean[0].note")).isEqualTo("0");
            assertThat(WLSetupDisplayWaitingList2Action.rowIndexOf("waitingListBean[12].demographicNo")).isEqualTo("12");
            assertThat(WLSetupDisplayWaitingList2Action.rowIndexOf("comments-1")).isNull();
            assertThat(WLSetupDisplayWaitingList2Action.rowIndexOf(null)).isNull();
        }
    }
}
