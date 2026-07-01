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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RxAddAllergy2Action Tests")
@Tag("unit")
@Tag("prescript")
class RxAddAllergy2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private AllergyDao mockAllergyDao;
    @Mock
    private PartialDateDao mockPartialDateDao;
    @Mock
    private LoggedInInfo mockLoggedInInfo;
    @Mock
    private HttpServletRequest mockRequest;
    @Mock
    private HttpServletResponse mockResponse;
    @Mock
    private HttpSession mockSession;

    private RxAddAllergy2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(AllergyDao.class, mockAllergyDao);
        registerMock(PartialDateDao.class, mockPartialDateDao);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(mockRequest))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_allergy"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockRequest.getSession()).thenReturn(mockSession);
        when(mockRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        action = new RxAddAllergy2Action();
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
    void shouldSendBadRequest_whenTypeParameterMissing() throws Exception {
        when(mockRequest.getParameter("ID")).thenReturn("");
        when(mockRequest.getParameter("type")).thenReturn(null);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or empty type parameter");
        verify(mockSession, never()).getAttribute("Patient");
    }

    @Test
    void shouldSendBadRequest_whenTypeParameterNonNumeric() throws Exception {
        when(mockRequest.getParameter("ID")).thenReturn("");
        when(mockRequest.getParameter("type")).thenReturn("abc");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid type parameter");
        verify(mockSession, never()).getAttribute("Patient");
    }

    @Test
    void shouldPersistAllergy_whenStartDateIsNull() throws Exception {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(123);
        RxPatientData.Patient patient = new RxPatientData.Patient(demographic);

        when(mockSession.getAttribute("Patient")).thenReturn(patient);
        when(mockRequest.getParameter("ID")).thenReturn("");
        when(mockRequest.getParameter("name")).thenReturn("Peanuts");
        when(mockRequest.getParameter("type")).thenReturn("0");
        when(mockRequest.getParameter("reactionDescription")).thenReturn("Rash");
        when(mockRequest.getParameter("startDate")).thenReturn(null);

        String result = action.execute();

        ArgumentCaptor<Allergy> allergyCaptor = ArgumentCaptor.forClass(Allergy.class);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockAllergyDao).persist(allergyCaptor.capture());
        verify(mockPartialDateDao).setPartialDate(eq(PartialDate.ALLERGIES), isNull(),
                eq(PartialDate.ALLERGIES_STARTDATE), isNull());
        assertThat(allergyCaptor.getValue().getTypeCode()).isEqualTo(0);
        assertThat(allergyCaptor.getValue().getStartDate()).isNull();
    }
}
