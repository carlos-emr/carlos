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
package io.github.carlos_emr.carlos.prescript.web;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RxReorder2Action}: swapping two drugs' positions is a chart mutation, so it is POST-only,
 * validates its ids, authorises the named patient before loading that patient's drugs, and swaps
 * only among that patient's prescriptions (#3908).
 */
@Tag("unit")
@Tag("prescription")
@Tag("security")
@DisplayName("RxReorder2Action")
class RxReorder2ActionUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 1001;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private LoggedInInfo mockLoggedInInfo;
    @Mock
    private DrugDao mockDrugDao;
    @Mock
    private CaseManagementManager mockCaseManagementManager;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Drug first;
    private Drug second;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(DrugDao.class, mockDrugDao);
        registerMock(CaseManagementManager.class, mockCaseManagementManager);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), anyString(), isNull())).thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("drugId", "77");
        request.setParameter("swapDrugId", "78");

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        first = drug(77, 1);
        second = drug(78, 2);
        when(mockCaseManagementManager.getPrescriptions(String.valueOf(DEMOGRAPHIC_NO), true))
                .thenReturn(List.of(first, second));
    }

    @AfterEach
    void tearDown() throws Exception {
        servletActionContextMock.close();
        loggedInInfoMock.close();
        mocks.close();
    }

    private static Drug drug(int id, int position) {
        Drug drug = new Drug();
        drug.setId(id);
        drug.setDemographicId(DEMOGRAPHIC_NO);
        drug.setPosition(position);
        return drug;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should answer 405 before any privilege check or drug lookup on a non-POST request")
    void shouldRefuseNonPost_beforeAnySideEffect(String httpMethod) throws Exception {
        request.setMethod(httpMethod);

        String result = new RxReorder2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(mockDrugDao, mockCaseManagementManager, mockSecurityInfoManager);
    }

    @ParameterizedTest(name = "demographicNo={0} drugId={1} swapDrugId={2}")
    @CsvSource(delimiter = '|', value = {
            "abc|77|78",
            "|77|78",
            "1001|-1|78",
            "1001|77|7.5",
            "1001|1234567890|78",
            "1001|77|"
    })
    @DisplayName("should answer 400 before authorising or loading when any id is malformed")
    void shouldAnswerBadRequest_whenAnyIdMalformed(String demographicNo, String drugId, String swapDrugId) throws Exception {
        request.setParameter("demographicNo", demographicNo == null ? "" : demographicNo);
        request.setParameter("drugId", drugId == null ? "" : drugId);
        request.setParameter("swapDrugId", swapDrugId == null ? "" : swapDrugId);

        String result = new RxReorder2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(mockDrugDao, mockCaseManagementManager);
        verify(mockSecurityInfoManager, never()).isAllowedAccessToPatientRecord(any(), any());
    }

    @Test
    @DisplayName("should refuse the swap before loading the patient's drugs when record access is denied")
    void shouldThrowSecurityException_whenPatientRecordAccessDenied() {
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(), eq(DEMOGRAPHIC_NO))).thenReturn(false);

        RxReorder2Action action = new RxReorder2Action();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
        verifyNoInteractions(mockDrugDao, mockCaseManagementManager);
    }

    @Test
    @DisplayName("should swap the two drugs' positions for the named patient and answer ok")
    void shouldSwapPositions_whenBothDrugsBelongToPatient() throws Exception {
        String result = new RxReorder2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString().trim()).isEqualTo("ok");
        assertThat(first.getPosition()).isEqualTo(2);
        assertThat(second.getPosition()).isEqualTo(1);
        verify(mockDrugDao).merge(first);
        verify(mockDrugDao).merge(second);
    }

    @Test
    @DisplayName("should change nothing when a drug id is not among the patient's prescriptions")
    void shouldMergeNothing_whenDrugNotOwnedByPatient() throws Exception {
        // Another patient's drug id: it is never looked up outside this patient's list.
        request.setParameter("swapDrugId", "99");

        String result = new RxReorder2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(first.getPosition()).isEqualTo(1);
        assertThat(second.getPosition()).isEqualTo(2);
        verify(mockDrugDao, never()).merge(any(Drug.class));
    }
}
