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

import io.github.carlos_emr.carlos.commn.dao.PreventionsLotNrsDao;
import io.github.carlos_emr.carlos.commn.model.PreventionsLotNrs;
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
 * Unit tests for {@link LotNrAddRecord2Action} covering privilege checks,
 * POST enforcement, soft-delete restore, duplicate detection, new record creation,
 * and DAO exception handling.
 *
 * @since 2026-04-11
 */
@DisplayName("LotNrAddRecord2Action")
@Tag("unit")
@Tag("admin")
class LotNrAddRecord2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private PreventionsLotNrsDao mockPreventionsLotNrsDao;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(PreventionsLotNrsDao.class, mockPreventionsLotNrsDao);

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

    private LotNrAddRecord2Action createActionWithPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull()))
            .thenReturn(true);
        mockRequest.setMethod("POST");
        return new LotNrAddRecord2Action();
    }

    @Nested
    @DisplayName("Privilege checks")
    class PrivilegeChecks {

        @Test
        @DisplayName("should throw SecurityException when privilege is denied")
        void shouldThrowSecurityException_whenPrivilegeDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull()))
                .thenReturn(false);

            LotNrAddRecord2Action action = new LotNrAddRecord2Action();
            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
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

            LotNrAddRecord2Action action = new LotNrAddRecord2Action();
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(405);
        }
    }

    @Nested
    @DisplayName("Add operations")
    class AddOperations {

        @Test
        @DisplayName("should restore soft-deleted record when one exists")
        void shouldRestoreSoftDeletedRecord_whenOneExists() throws Exception {
            LotNrAddRecord2Action action = createActionWithPrivilege();
            mockRequest.setParameter("prevention", "Flu");
            mockRequest.setParameter("lotnr", "LOT123");

            PreventionsLotNrs deletedRecord = mock(PreventionsLotNrs.class);
            when(mockPreventionsLotNrsDao.findByName("Flu", "LOT123", true)).thenReturn(deletedRecord);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(deletedRecord).setDeleted(false);
            verify(mockPreventionsLotNrsDao).merge(deletedRecord);
            assertThat((String) mockRequest.getAttribute("resultMsg"))
                .contains("restored");
        }

        @Test
        @DisplayName("should reject duplicate when active record already exists")
        void shouldRejectDuplicate_whenActiveRecordExists() throws Exception {
            LotNrAddRecord2Action action = createActionWithPrivilege();
            mockRequest.setParameter("prevention", "Flu");
            mockRequest.setParameter("lotnr", "LOT123");

            when(mockPreventionsLotNrsDao.findByName("Flu", "LOT123", true)).thenReturn(null);
            PreventionsLotNrs activeRecord = mock(PreventionsLotNrs.class);
            when(mockPreventionsLotNrsDao.findByName("Flu", "LOT123", false)).thenReturn(activeRecord);

            action.execute();

            verify(mockPreventionsLotNrsDao, never()).persist(any());
            assertThat((String) mockRequest.getAttribute("resultMsg"))
                .contains("Duplicate");
        }

        @Test
        @DisplayName("should create new record when no existing record found")
        void shouldCreateNewRecord_whenNoExistingRecordFound() throws Exception {
            LotNrAddRecord2Action action = createActionWithPrivilege();
            mockRequest.setParameter("prevention", "Flu");
            mockRequest.setParameter("lotnr", "LOT456");

            when(mockPreventionsLotNrsDao.findByName("Flu", "LOT456", true)).thenReturn(null);
            when(mockPreventionsLotNrsDao.findByName("Flu", "LOT456", false)).thenReturn(null);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(mockPreventionsLotNrsDao).persist(any(PreventionsLotNrs.class));
            assertThat((String) mockRequest.getAttribute("resultMsg"))
                .contains("added");
        }

        @Test
        @DisplayName("should set error message when both parameters are null")
        void shouldSetErrorMessage_whenParametersAreNull() throws Exception {
            LotNrAddRecord2Action action = createActionWithPrivilege();

            action.execute();

            assertThat((String) mockRequest.getAttribute("resultMsg"))
                .contains("required");
            verify(mockPreventionsLotNrsDao, never()).persist(any());
            verify(mockPreventionsLotNrsDao, never()).merge(any());
        }

        @Test
        @DisplayName("should set error message when DAO throws RuntimeException")
        void shouldSetErrorMessage_whenDaoThrows() throws Exception {
            LotNrAddRecord2Action action = createActionWithPrivilege();
            mockRequest.setParameter("prevention", "Flu");
            mockRequest.setParameter("lotnr", "LOT789");

            when(mockPreventionsLotNrsDao.findByName("Flu", "LOT789", true))
                .thenThrow(new RuntimeException("DB error"));

            action.execute();

            assertThat((String) mockRequest.getAttribute("resultMsg"))
                .isEqualTo("Failed to add lot number record.");
        }

        @Test
        @DisplayName("should always set prevention attribute for the JSP")
        void shouldAlwaysSetPreventionAttribute() throws Exception {
            LotNrAddRecord2Action action = createActionWithPrivilege();
            mockRequest.setParameter("prevention", "Flu");
            mockRequest.setParameter("lotnr", "LOT123");

            when(mockPreventionsLotNrsDao.findByName(any(), any(), anyBoolean())).thenReturn(null);

            action.execute();

            assertThat(mockRequest.getAttribute("prevention")).isEqualTo("Flu");
        }
    }
}
